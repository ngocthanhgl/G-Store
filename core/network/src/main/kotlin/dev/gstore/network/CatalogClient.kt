package dev.gstore.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer

/**
 * Daily APK catalog produced by tools/scan.py and committed to catalog/.
 * Fetched from raw.githubusercontent.com — no auth, CDN-cached, ETag
 * supported. Schema 1; unknown keys ignored so the scanner can evolve.
 *
 * Single place that names the catalog repo: after a repo rename, update
 * CATALOG_REPO here (and tools/scan.py needs no change — it commits
 * locally) and the app pulls from the new location.
 */
const val CATALOG_OWNER = "ngocthanhgl"
const val CATALOG_REPO = "g-store-android"
/**
 * Slim launch index (repo metadata + latest-APK-release pointer, no notes,
 * no per-asset rows). The full apps.json outgrew a safe single pull
 * (2.8 MiB and growing with merge-accumulation), so the app pulls this.
 * Full asset lists load lazily per repo via the GitHub API on demand.
 */
const val CATALOG_INDEX_URL =
    "https://raw.githubusercontent.com/$CATALOG_OWNER/$CATALOG_REPO/main/catalog/index.json"

/** 4 MiB cap — the slim index stays near 1 MiB; anything bigger is a bug. */
const val CATALOG_MAX_BYTES = 4L * 1024 * 1024

@Serializable
data class CatalogRepo(
    val id: Long,
    val full_name: String,
    val owner: String,
    val avatar_url: String? = null,
    val description: String? = null,
    val stars: Int = 0,
    val forks: Int = 0,
    val topics: List<String> = emptyList(),
    val archived: Boolean = false,
    val default_branch: String = "main",
    val updated_at: String? = null,
    val created_at: String? = null,
    val license: String? = null,
    val language: String? = null,
    val openIssues: Int? = null,
    val watchers: Int? = null,
    val homepage: String? = null,
    val pushedAt: String? = null,
)

@Serializable
data class CatalogRelease(
    val id: Long,
    val tag: String,
    val name: String? = null,
    val notes: String? = null,
    val published_at: String? = null,
    val html_url: String? = null,
    val prerelease: Boolean = false,
)

@Serializable
data class CatalogAsset(
    val id: Long,
    val name: String,
    val content_type: String? = null,
    val size: Long = 0,
    val download_url: String? = null,
    val digest: String? = null,
    val download_count: Long = 0,
)

@Serializable
data class CatalogEntry(
    val schema: Int = 1,
    val repo: CatalogRepo,
    val release: CatalogRelease,
    val assets: List<CatalogAsset> = emptyList(),
)

/**
 * Slim index entry (catalog/index.json): repo metadata plus the latest
 * APK release pointer. No notes, no per-asset rows — those arrive lazily
 * through a per-repo GitHub sync when the user opens the details screen.
 */
@Serializable
data class CatalogIndexRelease(
    val id: Long,
    val tag: String,
    val published_at: String? = null,
    val prerelease: Boolean = false,
    val apk_count: Int = 0,
)

@Serializable
data class CatalogIndexEntry(
    val schema: Int = 1,
    val last_verified: String? = null,
    val repo: CatalogRepo,
    val release: CatalogIndexRelease? = null,
)

private val catalogJson = Json { ignoreUnknownKeys = true; explicitNulls = true }

/** Pure parse — unit-tested without network (CatalogParseTest). */
fun parseCatalog(raw: String): List<CatalogEntry> =
    catalogJson.decodeFromString<List<CatalogEntry>>(raw)

/** Pure parse of the slim launch index. */
fun parseIndex(raw: String): List<CatalogIndexEntry> =
    catalogJson.decodeFromString<List<CatalogIndexEntry>>(raw)

class CatalogClient(private val http: OkHttpClient = OkHttpClient.Builder().build()) {
    suspend fun fetch(etag: String?): ApiResponse<List<CatalogIndexEntry>> =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url(CATALOG_INDEX_URL)
                .header("User-Agent", "gstore-app")
            if (etag != null) builder.header("If-None-Match", etag)
            val call = try {
                http.newCall(builder.build()).execute()
            } catch (e: Exception) {
                throw GitHubException.Network(e)
            }
            call.use { resp ->
                when (resp.code) {
                    304 -> ApiResponse.NotModified
                    404 -> throw GitHubException.NotFound(CATALOG_INDEX_URL)
                    in 500..599 -> throw GitHubException.Server(resp.code, CATALOG_INDEX_URL)
                    else -> {
                        if (!resp.isSuccessful) throw GitHubException.Server(resp.code, CATALOG_INDEX_URL)
                        ApiResponse.Fresh(
                            parseIndex(readBounded(resp)),
                            resp.header("ETag"),
                        )
                    }
                }
            }
        }

    private fun readBounded(resp: okhttp3.Response): String {
        val body = resp.body ?: throw GitHubException.Server(resp.code, CATALOG_INDEX_URL)
        if (body.contentLength() > CATALOG_MAX_BYTES) throw GitHubException.TooLarge(CATALOG_INDEX_URL)
        val out = Buffer()
        val source = body.source()
        var total = 0L
        while (!source.exhausted()) {
            val read = source.read(out, 8_192)
            if (read == -1L) break
            total += read
            if (total > CATALOG_MAX_BYTES) throw GitHubException.TooLarge(CATALOG_INDEX_URL)
        }
        return out.readUtf8()
    }
}
