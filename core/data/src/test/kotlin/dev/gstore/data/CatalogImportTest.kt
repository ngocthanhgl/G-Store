package dev.gstore.data

import dev.gstore.database.AssetDao
import dev.gstore.database.AssetEntity
import dev.gstore.database.CheckpointDao
import dev.gstore.database.FavoriteDao
import dev.gstore.database.FavoriteEntity
import dev.gstore.database.ReleaseDao
import dev.gstore.database.ReleaseEntity
import dev.gstore.database.RepositoryDao
import dev.gstore.database.RepositoryEntity
import dev.gstore.database.SyncCheckpoint
import dev.gstore.network.ApiResponse
import dev.gstore.network.CatalogAsset
import dev.gstore.network.CatalogEntry
import dev.gstore.network.CatalogIndexRow
import dev.gstore.network.CatalogRelease
import dev.gstore.network.CatalogRepo
import dev.gstore.network.GitHubService
import dev.gstore.network.ReadmeFetch
import dev.gstore.network.ReleaseDto
import dev.gstore.network.RepoDto
import dev.gstore.network.SearchResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * importCatalog merge rules: FRESH rows are never downgraded, history only
 * grows, assets without a download URL are skipped. Unused doubles are TODO
 * stubs — importCatalog never calls them.
 */
class CatalogImportTest {
    private class Repos : RepositoryDao {
        val rows = mutableMapOf<Long, RepositoryEntity>()
        override fun observeAll(): Flow<List<RepositoryEntity>> = flowOf(rows.values.toList())
        override suspend fun getById(id: Long): RepositoryEntity? = rows[id]
        override suspend fun getByFullName(fullName: String): RepositoryEntity? =
            rows.values.firstOrNull { it.fullName == fullName }
        override suspend fun upsert(repo: RepositoryEntity) { rows[repo.id] = repo }
        override fun searchLocal(q: String): Flow<List<RepositoryEntity>> = TODO()
        override fun observeByIds(ids: List<Long>): Flow<List<RepositoryEntity>> = TODO()
    }

    private class Releases : ReleaseDao {
        val rows = mutableMapOf<Long, ReleaseEntity>()
        override fun observeByRepo(repoId: Long): Flow<List<ReleaseEntity>> = TODO()
        override suspend fun upsertAll(rs: List<ReleaseEntity>) { rs.forEach { rows[it.id] = it } }
        override suspend fun pruneExcept(repoId: Long, keepIds: List<Long>) = TODO()
        override suspend fun listByRepo(repoId: Long): List<ReleaseEntity> = TODO()
        override suspend fun countEligibleReleases(repoId: Long): Int = TODO()
        override suspend fun sumDownloadsSince(repoId: Long, cutoff: String): Long = TODO()
        override suspend fun firstEligibleReleaseAt(repoId: Long): String? = TODO()
    }

    private class Assets : AssetDao {
        val rows = mutableMapOf<Long, AssetEntity>()
        override suspend fun listByRelease(releaseId: Long): List<AssetEntity> = TODO()
        override suspend fun listEligibleByRelease(releaseId: Long): List<AssetEntity> = TODO()
        override suspend fun upsertAll(a: List<AssetEntity>) { a.forEach { rows[it.id] = it } }
    }

    private class UnusedService : GitHubService {
        override suspend fun getRepo(o: String, r: String, e: String?): ApiResponse<RepoDto> = TODO()
        override suspend fun listReleases(
            o: String,
            r: String,
            p: Int,
            e: String?,
        ): ApiResponse<List<ReleaseDto>> = TODO()
        override suspend fun searchRepos(q: String, p: Int): ApiResponse<SearchResponse> = TODO()
        override suspend fun fetchReadme(o: String, r: String, e: String?): ApiResponse<ReadmeFetch> = TODO()
    }

    private class UnusedCheckpoints : CheckpointDao {
        override suspend fun get(fullName: String): SyncCheckpoint? = TODO()
        override suspend fun put(checkpoint: SyncCheckpoint) = TODO()
        override suspend fun clear(fullName: String) = TODO()
    }

    private class UnusedFavs : FavoriteDao {
        override fun observeAll(): Flow<List<FavoriteEntity>> = TODO()
        override suspend fun idList(): List<Long> = TODO()
        override suspend fun upsert(favorite: FavoriteEntity) = TODO()
        override suspend fun delete(repoId: Long) = TODO()
    }

    private fun catalog(repos: Repos, releases: Releases, assets: Assets): CatalogRepository {
        val api = UnusedService()
        val engine = SyncEngine(api, repos, releases, assets, UnusedCheckpoints())
        return CatalogRepository(api, engine, repos, releases, assets, UnusedFavs())
    }

    private fun entry() = CatalogEntry(
        repo = CatalogRepo(
            id = 7, full_name = "o/app", owner = "o", stars = 10, forks = 1,
            updated_at = "2026-10-09T00:00:00Z", created_at = "2021-01-01T00:00:00Z",
        ),
        release = CatalogRelease(
            id = 9, tag = "v1", published_at = "2026-10-08T00:00:00Z",
        ),
        assets = listOf(
            CatalogAsset(id = 5, name = "app.apk", size = 100, download_url = "https://dl/x"),
            CatalogAsset(id = 6, name = "notes.txt", size = 10, download_url = null),
            CatalogAsset(id = 8, name = "ghost.apk", size = 10, download_url = " "),
        ),
    )

    private fun repoRow(state: String) = RepositoryEntity(
        id = 7, fullName = "o/app", owner = "o", avatarUrl = null, description = null,
        stars = 1, forks = 0, topicsJson = "[]", archived = false, defaultBranch = "main",
        updatedAt = null, createdAt = null, license = null,
        lastSyncedAt = 111L, syncState = state,
    )

    @Test fun `import stores repo release and eligible apk, skips url-less assets`() = runBlocking {
        val repos = Repos()
        val releases = Releases()
        val assets = Assets()
        assertEquals(1, catalog(repos, releases, assets).importCatalog(listOf(entry()), 0L))
        assertEquals("STALE", repos.rows[7L]?.syncState)
        assertEquals("v1", releases.rows[9L]?.tag)
        assertEquals(setOf(5L), assets.rows.keys) // url-less assets skipped
        assertTrue(assets.rows[5L]?.eligibleApk == true)
    }

    @Test fun `fresh rows keep fresh state and sync time`() = runBlocking {
        val repos = Repos()
        repos.upsert(repoRow("FRESH"))
        catalog(repos, Releases(), Assets()).importCatalog(listOf(entry()), 0L)
        assertEquals("FRESH", repos.rows[7L]?.syncState)
        assertEquals(111L, repos.rows[7L]?.lastSyncedAt)
        assertEquals(10, repos.rows[7L]?.stars) // metadata still refreshed
    }

    private fun indexRow() = CatalogIndexRow(
        id = 7, full_name = "o/app", owner = "o", stars = 10, forks = 1,
        updated_at = "2026-10-09T00:00:00Z", tag = "v1",
        published_at = "2026-10-08T00:00:00Z", prerelease = false, apk_count = 2,
    )

    @Test fun `importIndex stores slim rows without asset detail`() = runBlocking {
        val repos = Repos()
        val releases = Releases()
        val assets = Assets()
        assertEquals(1, catalog(repos, releases, assets).importIndex(listOf(indexRow()), 0L))
        assertEquals("STALE", repos.rows[7L]?.syncState)
        assertEquals("v1", releases.rows[-7L]?.tag) // synthetic id -(repoId)
        assertTrue(assets.rows.isEmpty()) // no asset detail in the slim index
    }

    @Test fun `importIndex never clobbers fresh descriptions`() = runBlocking {
        val repos = Repos()
        repos.upsert(repoRow("FRESH").copy(description = "keep"))
        catalog(repos, Releases(), Assets()).importIndex(listOf(indexRow()), 0L)
        assertEquals("FRESH", repos.rows[7L]?.syncState)
        assertEquals("keep", repos.rows[7L]?.description)
    }
}
