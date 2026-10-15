package dev.gstore.network

import dev.gstore.model.Release
import dev.gstore.model.ReleaseAsset
import dev.gstore.model.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer

private const val BASE_URL = "https://api.github.com"
private const val API_VERSION = "2026-03-10"

/** Raw GitHub DTOs — mapped to domain models at the data boundary. */
@Serializable
data class RepoDto(
    val id: Long,
    @SerialName("full_name") val fullName: String,
    val owner: OwnerDto,
    val description: String?,
    @SerialName("stargazers_count") val stars: Int = 0,
    @SerialName("forks_count") val forks: Int = 0,
    val topics: List<String> = emptyList(),
    val archived: Boolean = false,
    @SerialName("default_branch") val defaultBranch: String = "main",
    @SerialName("updated_at") val updatedAt: String?,
    @SerialName("created_at") val createdAt: String? = null,
    val license: LicenseDto? = null,
    val language: String? = null,
    @SerialName("open_issues_count") val openIssues: Int? = null,
    @SerialName("subscribers_count") val watchers: Int? = null,
    val homepage: String? = null,
    @SerialName("pushed_at") val pushedAt: String? = null,
)

@Serializable
data class LicenseDto(val name: String? = null, @SerialName("spdx_id") val spdxId: String? = null)

@Serializable
data class OwnerDto(val login: String, @SerialName("avatar_url") val avatarUrl: String?)

@Serializable
data class ReleaseDto(
    val id: Long,
    @SerialName("tag_name") val tag: String,
    val name: String?,
    @SerialName("body") val notes: String?,
    @SerialName("published_at") val publishedAt: String?,
    @SerialName("html_url") val htmlUrl: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<AssetDto> = emptyList(),
)

@Serializable
data class AssetDto(
    val id: Long,
    val name: String,
    val label: String?,
    @SerialName("content_type") val contentType: String?,
    val size: Long,
    /** Nullable per API — absent digest must surface as UNAVAILABLE, not error. */
    val digest: String?,
    @SerialName("download_count") val downloadCount: Long = 0,
    @SerialName("browser_download_url") val downloadUrl: String,
)

@Serializable
data class SearchResponse(
    @SerialName("total_count") val totalCount: Int,
    val items: List<RepoDto> = emptyList(),
)

/** Authenticated identity (`GET /user`) — login validation + profile chip. */
@Serializable
data class GitHubUser(
    val login: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

/** A GET result: fresh body, or NOT_MODIFIED when the ETag still matches. */
sealed interface ApiResponse<out T> {
    data class Fresh<T>(val data: T, val etag: String?) : ApiResponse<T>
    data object NotModified : ApiResponse<Nothing>
}

/** Failure taxonomy mirrors CatalogFailure (DATAFLOW.md flow A). */
sealed class GitHubException(message: String) : IllegalStateException(message) {
    /** 403/429 — honor retryAfterSeconds, then exponential backoff. */
    class RateLimited(val retryAfterSeconds: Long?) :
        GitHubException("rate limited (retryAfter=$retryAfterSeconds)")

    /** 404/451 — mark repo inaccessible, keep stale cache. */
    class NotFound(val url: String) : GitHubException("not found: $url")
    class Auth(val url: String) : GitHubException("auth failed: $url")
    class Server(val code: Int, val url: String) : GitHubException("server $code: $url")
    class Network(cause: Throwable) : GitHubException("network: ${cause.message}")
    /** Client-side bound tripped (README cap) — never presented as a server error. */
    class TooLarge(val url: String) : GitHubException("response too large: $url")
}

/** Raw README fetch — bounded, never proxied (SECURITY_MODEL.md). */
data class ReadmeFetch(val markdown: String, val etag: String?)

/** Interface so the sync engine is unit-testable with a fake (TEST_PLAN §1). */
interface GitHubService {
    suspend fun getRepo(owner: String, repo: String, etag: String? = null): ApiResponse<RepoDto>
    suspend fun listReleases(
        owner: String,
        repo: String,
        page: Int = 1,
        etag: String? = null,
    ): ApiResponse<List<ReleaseDto>>

    suspend fun searchRepos(query: String, page: Int = 1): ApiResponse<SearchResponse>

    suspend fun fetchReadme(owner: String, repo: String, etag: String? = null): ApiResponse<ReadmeFetch>
}

/**
 * Minimal client on OkHttp + kotlinx.serialization. Raw header access is
 * deliberate: ETag, X-RateLimit-*, Retry-After drive the sync engine.
 * No token is ever shipped (see SECURITY_MODEL.md).
 */
class GitHubApi : GitHubService {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = true }
    private val http = OkHttpClient.Builder().build()

    /**
     * User-supplied Personal Access Token (Settings → login). Never shipped
     * in the repo/APK — it lives encrypted on-device (TokenStore) and is
     * only ever sent to api.github.com. Null = anonymous (60 req/hour).
     */
    @Volatile var token: String? = null

    private fun request(url: String, etag: String?): Request {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", API_VERSION)
            .header("User-Agent", "gstore-app/0.2.0")
        if (etag != null) builder.header("If-None-Match", etag)
        token?.takeIf { it.isNotBlank() }?.let {
            builder.header("Authorization", "Bearer $it")
        }
        return builder.build()
    }

    private suspend inline fun <reified T> get(url: String, etag: String?): ApiResponse<T> =
        withContext(Dispatchers.IO) {
            val call =
                try {
                    http.newCall(request(url, etag)).execute()
                } catch (e: Exception) {
                    throw GitHubException.Network(e)
                }
            call.use { resp ->
                when (resp.code) {
                    304 -> ApiResponse.NotModified
                    401 -> throw GitHubException.Auth(url)
                    403, 429 -> throw GitHubException.RateLimited(
                        resp.header("Retry-After")?.toLongOrNull()
                            ?: resp.header("X-RateLimit-Reset")?.toLongOrNull()?.let { reset ->
                                (reset - System.currentTimeMillis() / 1000).coerceAtLeast(0)
                            },
                    )
                    404, 451 -> throw GitHubException.NotFound(url)
                    in 500..599 -> throw GitHubException.Server(resp.code, url)
                    else -> {
                        if (!resp.isSuccessful) throw GitHubException.Server(resp.code, url)
                        val body = resp.body.string()
                        ApiResponse.Fresh(json.decodeFromString<T>(body), resp.header("ETag"))
                    }
                }
            }
        }

    override suspend fun getRepo(owner: String, repo: String, etag: String?): ApiResponse<RepoDto> =
        get("$BASE_URL/repos/$owner/$repo", etag)

    override suspend fun listReleases(
        owner: String,
        repo: String,
        page: Int,
        etag: String?,
    ): ApiResponse<List<ReleaseDto>> =
        get("$BASE_URL/repos/$owner/$repo/releases?per_page=100&page=$page", etag)

    override suspend fun searchRepos(query: String, page: Int): ApiResponse<SearchResponse> =
        get("$BASE_URL/search/repositories?q=$query&per_page=100&page=$page", etag = null)

    /**
     * Validate a user-supplied token and read its identity. NOT on the
     * [GitHubService] interface (sync engine never needs it); throws
     * [GitHubException.Auth] for bad tokens — the login screen maps that to
     * "check the token", never a crash.
     */
    suspend fun fetchUser(): GitHubUser =
        when (val response = get<GitHubUser>("$BASE_URL/user", etag = null)) {
            is ApiResponse.Fresh -> response.data
            ApiResponse.NotModified -> throw GitHubException.Network(
                IllegalStateException("unexpected 304 without etag"),
            )
        }

    override suspend fun fetchReadme(
        owner: String,
        repo: String,
        etag: String?,
    ): ApiResponse<ReadmeFetch> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/repos/$owner/$repo/readme"
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.raw")
            .header("X-GitHub-Api-Version", API_VERSION)
            .header("User-Agent", "gstore-app/0.2.0")
        if (etag != null) builder.header("If-None-Match", etag)
        val call = try {
            http.newCall(builder.build()).execute()
        } catch (e: Exception) {
            throw GitHubException.Network(e)
        }
        call.use { resp ->
            when (resp.code) {
                304 -> ApiResponse.NotModified
                401 -> throw GitHubException.Auth(url)
                403, 429 -> throw GitHubException.RateLimited(
                    resp.header("Retry-After")?.toLongOrNull()
                        ?: resp.header("X-RateLimit-Reset")?.toLongOrNull()?.let { reset ->
                            (reset - System.currentTimeMillis() / 1000).coerceAtLeast(0)
                        },
                )
                404, 451 -> throw GitHubException.NotFound(url)
                in 500..599 -> throw GitHubException.Server(resp.code, url)
                else -> {
                    if (!resp.isSuccessful) throw GitHubException.Server(resp.code, url)
                    ApiResponse.Fresh(readBounded(resp, url), resp.header("ETag"))
                }
            }
        }
    }

    /** 1 MiB cap — a bigger README is treated as hostile, never parsed. */
    private fun readBounded(resp: okhttp3.Response, url: String): ReadmeFetch {
        val body = resp.body ?: throw GitHubException.Server(resp.code, url)
        if (body.contentLength() > README_MAX_BYTES) throw GitHubException.TooLarge(url)
        val out = Buffer()
        val source = body.source()
        var total = 0L
        while (!source.exhausted()) {
            val read = source.read(out, 8_192)
            if (read == -1L) break
            total += read
            if (total > README_MAX_BYTES) throw GitHubException.TooLarge(url)
        }
        return ReadmeFetch(out.readUtf8(), resp.header("ETag"))
    }
}

private const val README_MAX_BYTES = 1_048_576L

fun RepoDto.toDomain(): Repository = Repository(
    id = id,
    fullName = fullName,
    owner = owner.login,
    avatarUrl = owner.avatarUrl,
    description = description,
    stars = stars,
    forks = forks,
    topics = topics,
    archived = archived,
    defaultBranch = defaultBranch,
    updatedAt = updatedAt,
    createdAt = createdAt,
    license = license?.name ?: license?.spdxId,
)

fun ReleaseDto.toDomain(repoId: Long): Release = Release(
    id = id,
    repoId = repoId,
    tag = tag,
    name = name,
    notes = notes,
    publishedAt = publishedAt,
    htmlUrl = htmlUrl,
    prerelease = prerelease,
)

fun AssetDto.toDomain(releaseId: Long): ReleaseAsset = ReleaseAsset(
    id = id,
    releaseId = releaseId,
    filename = name,
    contentType = contentType,
    size = size,
    downloadUrl = downloadUrl,
    digest = digest,
    downloadCount = downloadCount,
)
