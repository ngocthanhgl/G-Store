package dev.gstore.data

import dev.gstore.common.Outcome
import dev.gstore.database.AssetDao
import dev.gstore.database.AssetEntity
import dev.gstore.database.CheckpointDao
import dev.gstore.database.ReleaseDao
import dev.gstore.database.ReleaseEntity
import dev.gstore.database.RepositoryDao
import dev.gstore.database.RepositoryEntity
import dev.gstore.database.SyncCheckpoint
import dev.gstore.network.ApiResponse
import dev.gstore.network.ContentsDto
import dev.gstore.network.GitHubException
import dev.gstore.network.GitHubService
import dev.gstore.network.ReadmeFetch
import dev.gstore.network.ReleaseDto
import dev.gstore.network.RepoDto
import dev.gstore.network.SearchResponse
import dev.gstore.testing.GitHubFixtures
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory DAO fakes — no Robolectric needed for engine logic (TEST_PLAN §1). */
private class FakeRepos : RepositoryDao {
    val rows = mutableMapOf<Long, RepositoryEntity>()
    override fun observeAll(): Flow<List<RepositoryEntity>> = MutableStateFlow(rows.values.toList())
    override suspend fun getById(id: Long): RepositoryEntity? = rows[id]
    override suspend fun getByFullName(fullName: String): RepositoryEntity? =
        rows.values.find { it.fullName == fullName }
    override fun observeByIds(ids: List<Long>): Flow<List<RepositoryEntity>> =
        MutableStateFlow(rows.values.filter { it.id in ids })
    override fun searchLocal(query: String): Flow<List<RepositoryEntity>> =
        MutableStateFlow(
            rows.values.filter {
                it.fullName.contains(query, ignoreCase = true) ||
                    (it.description?.contains(query, ignoreCase = true) == true) ||
                    it.owner.contains(query, ignoreCase = true)
            },
        )
    override suspend fun upsert(repo: RepositoryEntity) { rows[repo.id] = repo }
}

private class FakeReleases : ReleaseDao {
    val rows = mutableMapOf<Long, ReleaseEntity>()
    override fun observeByRepo(repoId: Long): Flow<List<ReleaseEntity>> =
        MutableStateFlow(rows.values.filter { it.repoId == repoId })
    override suspend fun listByRepo(repoId: Long): List<ReleaseEntity> =
        rows.values.filter { it.repoId == repoId }
    override suspend fun countEligibleReleases(repoId: Long): Int = 0 // unused by engine
    override suspend fun sumDownloadsSince(repoId: Long, since: String): Long = 0 // unused
    override suspend fun firstEligibleReleaseAt(repoId: Long): String? = null // unused
    override suspend fun upsertAll(releases: List<ReleaseEntity>) {
        releases.forEach { rows[it.id] = it }
    }
    override suspend fun pruneExcept(repoId: Long, keepIds: List<Long>) {
        rows.entries.removeIf { it.value.repoId == repoId && it.key !in keepIds }
    }
}

private class FakeAssets : AssetDao {
    val rows = mutableMapOf<Long, AssetEntity>()
    override suspend fun listByRelease(releaseId: Long): List<AssetEntity> =
        rows.values.filter { it.releaseId == releaseId }
    override suspend fun listEligibleByRelease(releaseId: Long): List<AssetEntity> =
        listByRelease(releaseId).filter { it.eligibleApk }
    override suspend fun releaseIdsWithApk(releaseIds: List<Long>): List<Long> =
        rows.values.filter { it.eligibleApk && it.releaseId in releaseIds }.map { it.releaseId }.distinct()
    override suspend fun upsertAll(assets: List<AssetEntity>) {
        assets.forEach { rows[it.id] = it }
    }
}

private class FakeCheckpoints(var row: SyncCheckpoint? = null) : CheckpointDao {
    override suspend fun get(fullName: String): SyncCheckpoint? = row
    override suspend fun put(checkpoint: SyncCheckpoint) { row = checkpoint }
    override suspend fun clear(fullName: String) { row = null }
}

private class FakeApi(
    var repo: RepoDto = GitHubFixtures.repo(),
    var pages: Map<Int, List<ReleaseDto>> = mapOf(1 to GitHubFixtures.releasesPage1()),
    var failures: ArrayDeque<GitHubException> = ArrayDeque(),
    var etag: String? = "etag-1",
    val calls: MutableList<String> = mutableListOf(),
) : GitHubService {
    override suspend fun getRepo(owner: String, repo: String, etag: String?): ApiResponse<RepoDto> {
        calls += "repo"
        failures.removeFirstOrNull()?.let { throw it }
        return ApiResponse.Fresh(this.repo, "repo-etag")
    }
    override suspend fun listReleases(
        owner: String,
        repo: String,
        page: Int,
        etag: String?,
    ): ApiResponse<List<ReleaseDto>> {
        calls += "releases:$page"
        failures.removeFirstOrNull()?.let { throw it }
        if (etag != null && etag == this.etag && page == 1) return ApiResponse.NotModified
        return ApiResponse.Fresh(pages[page] ?: emptyList(), this.etag)
    }
    override suspend fun searchRepos(query: String, page: Int): ApiResponse<SearchResponse> =
        ApiResponse.Fresh(SearchResponse(0, emptyList()), null)
    override suspend fun fetchReadme(
        owner: String,
        repo: String,
        etag: String?,
    ): ApiResponse<ReadmeFetch> = ApiResponse.Fresh(ReadmeFetch("# test", null), null)
    override suspend fun fetchContents(
        owner: String,
        repo: String,
        ref: String,
        etag: String?,
    ): ApiResponse<List<ContentsDto>> = ApiResponse.Fresh(emptyList(), null)
}

class SyncEngineTest {

    @Test fun `full sync persists repo releases and eligible assets, drops drafts`() = runBlocking {
        val api = FakeApi()
        val repos = FakeRepos(); val releases = FakeReleases()
        val assets = FakeAssets(); val checkpoints = FakeCheckpoints()
        val delays = mutableListOf<Long>()
        val engine = SyncEngine(api, repos, releases, assets, checkpoints) { delays += it }

        val result = engine.syncRepository("example/coolapp")

        assertTrue(result is Outcome.Ok)
        assertEquals("example/coolapp", repos.rows[123L]?.fullName)
        assertEquals("FRESH", repos.rows[123L]?.syncState)
        // Stable + prerelease kept, draft excluded.
        assertEquals(setOf(1001L, 1002L), releases.rows.keys)
        // arm64 apk + universal apk + beta apk eligible; exe/zip/apks not.
        assertEquals(setOf(2001L, 2002L, 2006L), assets.rows.values.filter { it.eligibleApk }.map { it.id }.toSet())
        assertEquals(6, assets.rows.size)
        // Finished: checkpoint reset to page 1.
        assertEquals(1, checkpoints.row?.nextPage)
        assertTrue(delays.isEmpty())
    }

    @Test fun `not-modified page one finishes without writes`() = runBlocking {
        val api = FakeApi()
        val repos = FakeRepos(); val releases = FakeReleases()
        val assets = FakeAssets()
        // Stored etag matches the server's → 304.
        val checkpoints = FakeCheckpoints(SyncCheckpoint("example/coolapp", 1, "etag-1", null))
        val engine = SyncEngine(api, repos, releases, assets, checkpoints) {}

        val result = engine.syncRepository("example/coolapp")

        assertTrue(result is Outcome.Ok)
        assertTrue(releases.rows.isEmpty())
        assertEquals("FRESH", repos.rows[123L]?.syncState)
    }

    @Test fun `rate limit is retried with server-directed wait then succeeds`() = runBlocking {
        val api = FakeApi(failures = ArrayDeque(listOf(GitHubException.RateLimited(30))))
        val repos = FakeRepos(); val releases = FakeReleases()
        val assets = FakeAssets(); val checkpoints = FakeCheckpoints()
        val delays = mutableListOf<Long>()
        val engine = SyncEngine(api, repos, releases, assets, checkpoints) { delays += it }

        val result = engine.syncRepository("example/coolapp")

        assertTrue(result is Outcome.Ok)
        // Server-directed 30s wait, ±20% jitter applied by the engine.
        assertEquals(1, delays.size)
        assertTrue(delays[0] in 24_000L..36_000L)
        assertEquals(2, api.calls.count { it == "repo" })
    }

    @Test fun `persistent rate limit fails and marks repo`() = runBlocking {
        val api = FakeApi(
            failures = ArrayDeque(List(10) { GitHubException.RateLimited(null) }),
        )
        val repos = FakeRepos()
        repos.upsert(GitHubFixtures.repo().toEntity("FRESH", 0L))
        val engine = SyncEngine(
            api, repos, FakeReleases(), FakeAssets(), FakeCheckpoints(), maxAttempts = 2,
        ) {}

        val result = engine.syncRepository("example/coolapp")

        assertTrue(result is Outcome.Err)
        assertTrue(repos.rows[123L]?.syncState?.startsWith("FAILED") == true)
    }

    @Test fun `missing repo clears checkpoint and reports error`() = runBlocking {
        val api = FakeApi(failures = ArrayDeque(listOf(GitHubException.NotFound("x"))))
        val checkpoints = FakeCheckpoints(SyncCheckpoint("example/gone", 3, null, null))
        val engine = SyncEngine(api, FakeRepos(), FakeReleases(), FakeAssets(), checkpoints) {}

        val result = engine.syncRepository("example/gone")

        assertTrue(result is Outcome.Err)
        assertNull(checkpoints.row)
    }

    @Test fun `invalid name is rejected without network`() = runBlocking {
        val api = FakeApi()
        val engine = SyncEngine(api, FakeRepos(), FakeReleases(), FakeAssets(), FakeCheckpoints()) {}

        val result = engine.syncRepository("not-a-repo")

        assertTrue(result is Outcome.Err)
        assertTrue(api.calls.isEmpty())
    }

    @Test fun `duplicate syncs share one execution`() = runBlocking {
        val api = FakeApi()
        val engine = SyncEngine(api, FakeRepos(), FakeReleases(), FakeAssets(), FakeCheckpoints()) {}

        val first = engine.syncRepository("example/coolapp")
        val second = engine.syncRepository("example/coolapp")

        assertTrue(first is Outcome.Ok && second is Outcome.Ok)
        // Two sequential syncs, each doing repo + 1 page (page 1 returns 3 items < 100 → done).
        assertEquals(4, api.calls.size)
    }

    @Test fun `resumes from stored checkpoint page`() = runBlocking {
        val page2 = GitHubFixtures.releasesPage1().take(1)
        val api = FakeApi(pages = mapOf(2 to page2), etag = null)
        val checkpoints = FakeCheckpoints(SyncCheckpoint("example/coolapp", 2, null, null))
        val releases = FakeReleases()
        val engine = SyncEngine(api, FakeRepos(), releases, FakeAssets(), checkpoints) {}

        val result = engine.syncRepository("example/coolapp")

        assertTrue(result is Outcome.Ok)
        assertTrue(api.calls.contains("releases:2"))
        assertTrue(releases.rows.containsKey(1001L))
    }
}
