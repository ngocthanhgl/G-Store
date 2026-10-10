package dev.gstore.data

import dev.gstore.common.Outcome
import dev.gstore.database.ReadmeDao
import dev.gstore.database.ReadmeEntity
import dev.gstore.network.ApiResponse
import dev.gstore.network.GitHubException
import dev.gstore.network.GitHubService
import java.io.IOException
import kotlinx.coroutines.flow.Flow

/** Offline-first README cache. Mirrors docs/DATAFLOW.md flow B. */
class ReadmeRepository(
    private val api: GitHubService,
    private val readmes: ReadmeDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    companion object {
        /** Revalidate cached READMEs older than this (DATA_MODEL §ReadmeDocument). */
        const val TTL_MS = 24L * 3600L * 1000L
        /** Bounded storage: keep only the most recently fetched entries. */
        const val MAX_CACHED = 50
    }

    fun observe(fullName: String): Flow<ReadmeEntity?> = readmes.observe(fullName)

    /**
     * Returns Ok immediately when the cache is fresh. Otherwise revalidates with
     * the stored ETag; a 304 only touches [ReadmeEntity.fetchedAt]. Never throws.
     */
    suspend fun refresh(owner: String, repo: String, force: Boolean = false): Outcome<Unit> {
        val fullName = "$owner/$repo"
        val cached = readmes.get(fullName)
        val fresh = cached != null && clock() - cached.fetchedAt < TTL_MS
        if (fresh && !force) return Outcome.Ok(Unit)
        return try {
            when (val response = api.fetchReadme(owner, repo, if (force) null else cached?.etag)) {
                is ApiResponse.Fresh -> {
                    readmes.put(ReadmeEntity(fullName, response.data.markdown, response.etag, clock()))
                    readmes.prune(MAX_CACHED)
                }
                is ApiResponse.NotModified -> {
                    // 304: keep the body, record the revalidation time.
                    if (cached != null) readmes.put(cached.copy(fetchedAt = clock()))
                }
            }
            Outcome.Ok(Unit)
        } catch (e: GitHubException.NotFound) {
            Outcome.Err("No README on the default branch")
        } catch (e: GitHubException.RateLimited) {
            Outcome.Err("GitHub rate limit reached — cached copy shown")
        } catch (e: GitHubException) {
            Outcome.Err(e.message ?: "GitHub request failed")
        } catch (e: IOException) {
            Outcome.Err("Network unavailable — cached copy shown")
        }
    }
}
