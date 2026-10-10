package dev.gstore.data

import dev.gstore.common.Outcome
import dev.gstore.database.ReadmeDao
import dev.gstore.database.ReadmeEntity
import dev.gstore.network.ApiResponse
import dev.gstore.network.GitHubException
import dev.gstore.network.GitHubService
import dev.gstore.network.ReadmeFetch
import dev.gstore.network.ReleaseDto
import dev.gstore.network.RepoDto
import dev.gstore.network.SearchResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeReadmes : ReadmeDao {
    val rows = mutableMapOf<String, ReadmeEntity>()
    var prunes = 0
    private val flow = MutableStateFlow<Map<String, ReadmeEntity>>(emptyMap())

    private fun publish() {
        flow.value = rows.toMap()
    }

    override fun observe(fullName: String): Flow<ReadmeEntity?> = flow.map { it[fullName] }

    override suspend fun get(fullName: String): ReadmeEntity? = rows[fullName]

    override suspend fun put(readme: ReadmeEntity) {
        rows[readme.repoFullName] = readme
        publish()
    }

    override suspend fun clear(fullName: String) {
        rows.remove(fullName)
        publish()
    }

    override suspend fun prune(keep: Int) {
        prunes++
    }
}

private class FakeReadmeApi(
    var fetch: ApiResponse<ReadmeFetch> = ApiResponse.Fresh(ReadmeFetch("# hi", "e1"), "e1"),
    var failure: GitHubException? = null,
) : GitHubService {
    var calls = 0
    override suspend fun getRepo(owner: String, repo: String, etag: String?): ApiResponse<RepoDto> =
        throw UnsupportedOperationException()
    override suspend fun listReleases(
        owner: String,
        repo: String,
        page: Int,
        etag: String?,
    ): ApiResponse<List<ReleaseDto>> = throw UnsupportedOperationException()
    override suspend fun searchRepos(query: String, page: Int): ApiResponse<SearchResponse> =
        throw UnsupportedOperationException()
    override suspend fun fetchReadme(
        owner: String,
        repo: String,
        etag: String?,
    ): ApiResponse<ReadmeFetch> {
        calls++
        failure?.let { throw it }
        return fetch
    }
}

class ReadmeRepositoryTest {

    @Test fun `fresh cache skips network`() = runBlocking {
        val api = FakeReadmeApi()
        val dao = FakeReadmes()
        val now = 1_000_000L
        dao.put(ReadmeEntity("o/r", "# cached", "e0", now))
        val repo = ReadmeRepository(api, dao) { now + 3_600_000L }

        assertTrue(repo.refresh("o", "r") is Outcome.Ok)
        assertEquals(0, api.calls)
    }

    @Test fun `stale cache revalidates and stores etag`() = runBlocking {
        val api = FakeReadmeApi(ApiResponse.Fresh(ReadmeFetch("# new", "e2"), "e2"))
        val dao = FakeReadmes()
        dao.put(ReadmeEntity("o/r", "# old", "e0", 0L))
        val repo = ReadmeRepository(api, dao) { ReadmeRepository.TTL_MS + 1 }

        assertTrue(repo.refresh("o", "r") is Outcome.Ok)
        assertEquals(1, api.calls)
        assertEquals("# new", dao.rows["o/r"]?.markdown)
        assertEquals("e2", dao.rows["o/r"]?.etag)
        assertEquals(1, dao.prunes)
    }

    @Test fun `not-modified keeps body and touches timestamp`() = runBlocking {
        val api = FakeReadmeApi(ApiResponse.NotModified)
        val dao = FakeReadmes()
        dao.put(ReadmeEntity("o/r", "# body", "e0", 0L))
        val repo = ReadmeRepository(api, dao) { ReadmeRepository.TTL_MS + 1 }

        assertTrue(repo.refresh("o", "r") is Outcome.Ok)
        assertEquals("# body", dao.rows["o/r"]?.markdown)
        assertEquals(ReadmeRepository.TTL_MS + 1, dao.rows["o/r"]?.fetchedAt)
    }

    @Test fun `missing readme reported without throwing`() = runBlocking {
        val api = FakeReadmeApi(failure = GitHubException.NotFound("gone"))
        val repo = ReadmeRepository(api, FakeReadmes()) { 0L }

        val result = repo.refresh("o", "gone")
        assertTrue(result is Outcome.Err)
        assertTrue((result as Outcome.Err).failure.contains("No README"))
    }

    @Test fun `rate limit reported without throwing`() = runBlocking {
        val api = FakeReadmeApi(failure = GitHubException.RateLimited(null))
        val repo = ReadmeRepository(api, FakeReadmes()) { 0L }

        val result = repo.refresh("o", "r")
        assertTrue(result is Outcome.Err)
    }
}
