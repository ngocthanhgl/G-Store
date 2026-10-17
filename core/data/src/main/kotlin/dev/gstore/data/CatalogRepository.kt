package dev.gstore.data

import androidx.room.withTransaction
import dev.gstore.common.Outcome
import dev.gstore.database.AssetDao
import dev.gstore.database.CheckpointDao
import dev.gstore.database.FavoriteDao
import dev.gstore.database.FavoriteEntity
import dev.gstore.database.GStoreDatabase
import dev.gstore.database.ReleaseDao
import dev.gstore.database.RepositoryDao
import dev.gstore.database.AssetEntity
import dev.gstore.database.ReleaseEntity
import dev.gstore.database.RepositoryEntity
import dev.gstore.model.Release
import dev.gstore.model.ReleaseAsset
import dev.gstore.model.RepoSignals
import dev.gstore.model.Repository
import dev.gstore.network.ApiResponse
import dev.gstore.network.CatalogClient
import dev.gstore.network.CatalogEntry
import dev.gstore.network.CatalogIndexEntry
import dev.gstore.network.ContentsDto
import dev.gstore.network.GitHubException
import dev.gstore.network.GitHubService
import dev.gstore.network.toDomain as dtoToDomain
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository facade over the sync engine + Room (ARCHITECTURE.md: data
 * implementations provide repository interfaces to the domain layer).
 */
class CatalogRepository(
    private val api: GitHubService,
    private val engine: SyncEngine,
    private val repos: RepositoryDao,
    private val releases: ReleaseDao,
    private val assets: AssetDao,
    private val favorites: FavoriteDao,
    private val database: GStoreDatabase? = null,
) {
    fun observeRepositories(): Flow<List<Repository>> =
        repos.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getRepository(id: Long): Repository? = repos.getById(id)?.toDomain()

    suspend fun getByFullName(fullName: String): Repository? =
        repos.getByFullName(fullName)?.toDomain()

    fun searchLocal(query: String): Flow<List<Repository>> =
        repos.searchLocal(query).map { list -> list.map { it.toDomain() } }

    fun observeByIds(ids: List<Long>): Flow<List<Repository>> =
        if (ids.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList())
        else repos.observeByIds(ids).map { list -> list.map { it.toDomain() } }

    fun observeFavoriteIds(): Flow<Set<Long>> =
        favorites.observeAll().map { list -> list.map { it.repoId }.toSet() }

    /** Returns true when the repo is now favorited. */
    suspend fun toggleFavorite(repo: Repository): Boolean =
        if (repo.id in favorites.idList().toSet()) {
            favorites.delete(repo.id)
            false
        } else {
            favorites.upsert(
                FavoriteEntity(repo.id, repo.fullName, System.currentTimeMillis()),
            )
            true
        }

    /**
     * Add to Favourites without ever removing. Used by the GitHub-starred
     * sweep, which must be idempotent: re-running it over an already-hearted
     * repo has to leave the favourite in place, not toggle it off.
     */
    suspend fun addFavorite(repoId: Long, fullName: String) {
        if (favorites.idList().toSet().contains(repoId)) return
        favorites.upsert(FavoriteEntity(repoId, fullName, System.currentTimeMillis()))
    }

    /**
     * Activity snapshot for one repo — section-membership input (PRODUCT_SPEC §3).
     * Three indexed queries + one in-memory scan; catalog-scale only (see DECISIONS.md).
     */
    suspend fun signals(repo: Repository, now: Instant = Instant.now()): RepoSignals {
        val cutoff = now.minus(30, ChronoUnit.DAYS).toString()
        val all = releases.listByRepo(repo.id)
        return RepoSignals(
            repo = repo,
            releases30d = all.count { (it.publishedAt ?: "") >= cutoff },
            downloads30d = releases.sumDownloadsSince(repo.id, cutoff),
            eligibleReleaseCount = releases.countEligibleReleases(repo.id),
            firstEligibleReleaseAt = releases.firstEligibleReleaseAt(repo.id),
            latestReleaseAt = all.mapNotNull { it.publishedAt }.maxOrNull(),
        )
    }

    fun observeReleases(repoId: Long): Flow<List<Release>> =
        releases.observeByRepo(repoId).map { list -> list.map { it.toDomain() } }

    suspend fun eligibleAssets(releaseId: Long): List<ReleaseAsset> =
        assets.listEligibleByRelease(releaseId).map { it.toDomain() }

    /** Every asset of a release — the chooser labels non-APKs, never hides them. */
    suspend fun allAssets(releaseId: Long): List<ReleaseAsset> =
        assets.listByRelease(releaseId).map { it.toDomain() }

    /**
     * Which of [releaseIds] ship an installable APK, in one query. Batched on
     * purpose: a repo with hundreds of releases used to cost one asset query
     * each, which is what made the details screen feel like it was loading.
     */
    suspend fun releaseIdsWithApk(releaseIds: List<Long>): Set<Long> =
        if (releaseIds.isEmpty()) emptySet() else assets.releaseIdsWithApk(releaseIds).toSet()

    /** Repo root directory listing — used for screenshots (gallery at bottom of details). */
    suspend fun fetchContents(
        owner: String,
        repo: String,
        ref: String,
    ): ApiResponse<List<ContentsDto>> = api.fetchContents(owner, repo, ref, null)

    suspend fun sync(fullName: String): Outcome<Unit> = engine.syncRepository(fullName)

    /**
     * Slim scanner rows carry metadata but no assets — details must full-sync
     * before it may claim "no APK". True when the repo is unknown or its row
     * was never fully synced (STALE from the index import).
     */
    suspend fun needsFullSync(fullName: String): Boolean {
        val existing = repos.getByFullName(fullName) ?: return true
        return existing.syncState != "FRESH"
    }

    /**
     * Merge scanner-catalog entries into Room (flow A entry point for the bot).
     * Repo metadata is always refreshed, but a FRESH fully-synced row is never
     * downgraded to STALE, and release history is only ever added to — the
     * single catalog release never replaces what a full sync already stored.
     * Returns the number of entries merged. Skips assets without a download URL.
     */
    suspend fun importCatalog(entries: List<CatalogEntry>, nowMillis: Long): Int {
        var merged = 0
        for (e in entries) {
            if (e.schema != 1) continue
            val r = e.repo
            val existing = repos.getByFullName(r.full_name)
            repos.upsert(
                RepositoryEntity(
                    id = r.id,
                    fullName = r.full_name,
                    owner = r.owner,
                    avatarUrl = r.avatar_url,
                    description = r.description,
                    stars = r.stars,
                    forks = r.forks,
                    topicsJson = topicsJson(r.topics),
                    archived = r.archived,
                    defaultBranch = r.default_branch,
                    updatedAt = r.updated_at,
                    createdAt = r.created_at,
                    license = r.license,
                    // Extended facts: entry value wins, existing kept on null — never wiped.
                    language = r.language ?: existing?.language,
                    openIssues = r.openIssues ?: existing?.openIssues,
                    watchers = r.watchers ?: existing?.watchers,
                    homepage = r.homepage ?: existing?.homepage,
                    pushedAt = r.pushedAt ?: existing?.pushedAt,
                    lastSyncedAt = existing?.lastSyncedAt ?: 0L,
                    syncState = if (existing?.syncState == "FRESH") "FRESH" else "STALE",
                ),
            )
            val rel = e.release
            releases.upsertAll(
                listOf(
                    ReleaseEntity(
                        id = rel.id,
                        repoId = r.id,
                        tag = rel.tag,
                        name = rel.name,
                        notes = rel.notes,
                        publishedAt = rel.published_at,
                        htmlUrl = rel.html_url,
                        prerelease = rel.prerelease,
                    ),
                ),
            )
            assets.upsertAll(
                e.assets.mapNotNull { a ->
                    val url = a.download_url
                    if (url.isNullOrBlank()) return@mapNotNull null
                    AssetEntity(
                        id = a.id,
                        releaseId = rel.id,
                        filename = a.name,
                        contentType = a.content_type,
                        size = a.size,
                        downloadUrl = url,
                        digest = a.digest,
                        downloadCount = a.download_count,
                        eligibleApk = classifyAsset(a.name, a.content_type, a.size).eligible,
                    )
                },
            )
            merged++
        }
        return merged
    }

    /**
     * Merge slim launch-index entries (catalog/index.json) into Room.
     * Only repo metadata plus one release row per repo — no assets. Full
     * asset lists arrive later through a per-repo GitHub sync when the
     * user opens the details screen, so sections that need release dates
     * (New, Updated) work immediately while Downloads waits for real data.
     * A FRESH fully-synced row keeps its full description/topics (the
     * index truncates descriptions); stars, forks, avatar and flags are
     * always refreshed.
     */
    suspend fun importIndex(entries: List<CatalogIndexEntry>, nowMillis: Long): Int =
        database?.withTransaction { mergeIndex(entries) } ?: mergeIndex(entries)

    private suspend fun mergeIndex(entries: List<CatalogIndexEntry>): Int {
        var merged = 0
        for (e in entries) {
            if (e.schema != 1) continue
            val r = e.repo
            val existing = repos.getByFullName(r.full_name)
            val keepFull = existing?.syncState == "FRESH"
            repos.upsert(
                RepositoryEntity(
                    id = r.id,
                    fullName = r.full_name,
                    owner = r.owner,
                    avatarUrl = r.avatar_url,
                    description = if (keepFull) existing.description else r.description,
                    stars = r.stars,
                    forks = r.forks,
                    topicsJson = if (keepFull) existing.topicsJson else topicsJson(r.topics),
                    archived = r.archived,
                    defaultBranch = r.default_branch,
                    updatedAt = r.updated_at,
                    createdAt = r.created_at,
                    license = r.license,
                    // Slim rows never carry extended facts — always keep what a full sync stored.
                    language = existing?.language,
                    openIssues = existing?.openIssues,
                    watchers = existing?.watchers,
                    homepage = existing?.homepage,
                    pushedAt = existing?.pushedAt,
                    lastSyncedAt = existing?.lastSyncedAt ?: 0L,
                    syncState = if (keepFull) "FRESH" else "STALE",
                ),
            )
            val rel = e.release
            if (rel != null) {
                releases.upsertAll(
                    listOf(
                        ReleaseEntity(
                            id = rel.id,
                            repoId = r.id,
                            tag = rel.tag,
                            name = null,
                            notes = null,
                            publishedAt = rel.published_at,
                            htmlUrl = null,
                            prerelease = rel.prerelease,
                        ),
                    ),
                )
            }
            merged++
        }
        return merged
    }

    /** Seed search — mapped but NOT persisted until the user syncs a repo (M4). */
    suspend fun search(query: String): Outcome<List<Repository>> = try {
        when (val resp = api.searchRepos(query)) {
            is dev.gstore.network.ApiResponse.Fresh ->
                Outcome.Ok(resp.data.items.map { it.dtoToDomain() })
            dev.gstore.network.ApiResponse.NotModified -> Outcome.Ok(emptyList())
        }
    } catch (e: GitHubException.RateLimited) {
        Outcome.Err("rate limited, retry after ${e.retryAfterSeconds}s")
    } catch (e: GitHubException) {
        Outcome.Err("search failed: ${e.message}")
    }

    companion object {
        fun create(api: GitHubService, db: GStoreDatabase): CatalogRepository {
            val engine = SyncEngine(api, db.repositories(), db.releases(), db.assets(), db.checkpoints())
            return CatalogRepository(
                api, engine, db.repositories(), db.releases(), db.assets(), db.favorites(), db,
            )
        }
    }
}

/** Minimal JSON string-list encoder — decoded by Mappers' kotlinx parser. */
private fun topicsJson(topics: List<String>): String = buildString {
    append('[')
    topics.take(20).forEachIndexed { i, t ->
        if (i > 0) append(',')
        append('"')
        append(t.replace("\\", "\\\\").replace("\"", "\\\""))
        append('"')
    }
    append(']')
}
