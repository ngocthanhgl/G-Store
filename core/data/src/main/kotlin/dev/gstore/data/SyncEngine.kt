package dev.gstore.data

import dev.gstore.common.Outcome
import dev.gstore.database.AssetDao
import dev.gstore.database.CheckpointDao
import dev.gstore.database.ReleaseDao
import dev.gstore.database.RepositoryDao
import dev.gstore.database.SyncCheckpoint
import dev.gstore.network.ApiResponse
import dev.gstore.network.GitHubException
import dev.gstore.network.GitHubService
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

/**
 * Flow A implementation: repo metadata → paginated releases → eligibility →
 * persistence → checkpoint. Resumable, idempotent per repo, deduplicated
 * in-flight (DATAFLOW.md, GITHUB_DISCOVERY §3/§5).
 */
class SyncEngine(
    private val api: GitHubService,
    private val repos: RepositoryDao,
    private val releases: ReleaseDao,
    private val assets: AssetDao,
    private val checkpoints: CheckpointDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val maxAttempts: Int = 5,
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
) {
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Outcome<Unit>>>()

    suspend fun syncRepository(fullName: String, catalogVisible: Boolean = true): Outcome<Unit> {
        val key = "$fullName:${if (catalogVisible) "catalog" else "details"}"
        inFlight[key]?.let { return it.await() }
        val gate = CompletableDeferred<Outcome<Unit>>()
        inFlight[key] = gate
        return try {
            val result = doSync(fullName, catalogVisible)
            gate.complete(result)
            result
        } catch (e: Exception) {
            val out = Outcome.Err("sync failed: ${e.message}")
            gate.complete(out)
            out
        } finally {
            inFlight.remove(key)
        }
    }

    private suspend fun doSync(fullName: String, catalogVisible: Boolean): Outcome<Unit> {
        val parts = fullName.split('/')
        if (parts.size != 2 || parts.any { it.isBlank() }) {
            return Outcome.Err("invalid repository name: $fullName")
        }
        val (owner, name) = parts
        val checkpoint = checkpoints.get(fullName)
        val now = clock()

        val repoDto = try {
            when (val resp = withRetry { api.getRepo(owner, name) }) {
                is ApiResponse.Fresh -> resp.data
                // getRepo never sends ETag, so NotModified cannot happen; be exhaustive anyway.
                ApiResponse.NotModified -> return Outcome.Ok(Unit)
            }
        } catch (e: GitHubException.NotFound) {
            markState(fullName, "FAILED:NotFound", now)
            checkpoints.clear(fullName)
            return Outcome.Err("repository not accessible: $fullName")
        } catch (e: GitHubException.Auth) {
            return Outcome.Err("authentication failed")
        } catch (e: GitHubException) {
            markState(fullName, "FAILED:${e.javaClass.simpleName}", now)
            return Outcome.Err("sync failed: ${e.message}")
        }
        val syncState = if (catalogVisible) "FRESH" else "SEARCH_ONLY"
        repos.upsert(repoDto.toEntity(syncState, now))

        var page = checkpoint?.nextPage ?: 1
        var etag = if (page == 1) checkpoint?.etag else null
        // Prune only with a complete in-run id set: a resumed run (page > 1)
        // must not delete releases synced on earlier runs when it hits an
        // empty page. A resumed run that finishes resets the checkpoint to
        // page 1, so the next sync is full and prunes then.
        val pruneAllowed = page == 1
        val seenReleaseIds = mutableListOf<Long>()
        while (true) {
            val resp = try {
                withRetry { api.listReleases(owner, name, page, etag) }
            } catch (e: GitHubException.NotFound) {
                markState(fullName, "FAILED:NotFound", now)
                checkpoints.clear(fullName)
                return Outcome.Err("repository not accessible: $fullName")
            } catch (e: GitHubException.Auth) {
                return Outcome.Err("authentication failed")
            } catch (e: GitHubException) {
                markState(fullName, "FAILED:${e.javaClass.simpleName}", now)
                return Outcome.Err("sync failed: ${e.message}")
            }
            when (resp) {
                ApiResponse.NotModified -> {
                    // Page 1 unchanged → catalog is current. Guard the degenerate
                    // case: a checkpoint with an etag but zero local releases
                    // (interrupted first sync) must restart full instead of
                    // reporting an empty catalog as success.
                    if (releases.listByRepo(repoDto.id).isEmpty()) {
                        checkpoints.clear(fullName)
                    } else {
                        repos.upsert(repoDto.toEntity(syncState, now))
                        checkpoints.put(SyncCheckpoint(fullName, 1, etag, now))
                    }
                    return Outcome.Ok(Unit)
                }
                is ApiResponse.Fresh -> {
                    // Drafts never enter the catalog (GITHUB_DISCOVERY §2).
                    val visible = resp.data.filter { !it.draft }
                    releases.upsertAll(visible.map { it.toEntity(repoDto.id) })
                    assets.upsertAll(visible.flatMap { r -> r.assets.map { it.toEntity(r.id) } })
                    seenReleaseIds += visible.map { it.id }
                    if (resp.data.size < PAGE_SIZE) {
                        if (pruneAllowed) releases.pruneExcept(repoDto.id, seenReleaseIds)
                        repos.upsert(repoDto.toEntity(syncState, now))
                        checkpoints.put(SyncCheckpoint(fullName, 1, resp.etag, now))
                        return Outcome.Ok(Unit)
                    }
                    if (page == 1) etag = resp.etag
                    page += 1
                    checkpoints.put(SyncCheckpoint(fullName, page, etag, null))
                }
            }
        }
    }

    private suspend fun markState(fullName: String, state: String, now: Long) {
        // Only meaningful when we already cache this repo; otherwise nothing to mark.
        val existing = repos.getByFullName(fullName)
        if (existing != null) {
            repos.upsert(
                existing.copy(
                    syncState = if (existing.syncState == "SEARCH_ONLY") "SEARCH_ONLY" else state,
                    lastSyncedAt = now,
                ),
            )
        }
    }

    /** Bounded retries: server-directed wait first, else exponential backoff + jitter. */
    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: GitHubException.RateLimited) {
                if (attempt >= maxAttempts) throw e
                sleeper(jittered(rateLimitDelayMillis(e.retryAfterSeconds) ?: baseDelayMillis(attempt)))
                attempt++
            } catch (e: GitHubException.Server) {
                if (attempt >= maxAttempts) throw e
                sleeper(jittered(baseDelayMillis(attempt)))
                attempt++
            } catch (e: GitHubException.Network) {
                if (attempt >= maxAttempts) throw e
                sleeper(jittered(baseDelayMillis(attempt)))
                attempt++
            }
        }
    }

    companion object {
        const val PAGE_SIZE = 100
    }
}
