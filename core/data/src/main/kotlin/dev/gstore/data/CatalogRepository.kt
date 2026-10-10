package dev.gstore.data

import dev.gstore.common.Outcome
import dev.gstore.database.AssetDao
import dev.gstore.database.CheckpointDao
import dev.gstore.database.FavoriteDao
import dev.gstore.database.FavoriteEntity
import dev.gstore.database.GStoreDatabase
import dev.gstore.database.ReleaseDao
import dev.gstore.database.RepositoryDao
import dev.gstore.model.Release
import dev.gstore.model.ReleaseAsset
import dev.gstore.model.RepoSignals
import dev.gstore.model.Repository
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
) {
    fun observeRepositories(): Flow<List<Repository>> =
        repos.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getRepository(id: Long): Repository? = repos.getById(id)?.toDomain()

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

    suspend fun sync(fullName: String): Outcome<Unit> = engine.syncRepository(fullName)

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
                api, engine, db.repositories(), db.releases(), db.assets(), db.favorites(),
            )
        }
    }
}
