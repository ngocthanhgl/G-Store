package dev.gstore.data

import dev.gstore.database.StarCheckpoint
import dev.gstore.network.ApiResponse
import dev.gstore.network.GitHubApi
import dev.gstore.network.GitHubException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sweeps the authenticated user's GitHub stars into local Favourites.
 *
 * Design constraints the user set:
 * - APK-bearing repos only. A star on a repo with no installable artifact is
 *   noise in a store, and it is exactly what made the Installed shelf unusable.
 * - One request per candidate: `GET /repos/{o}/{r}/releases/latest` returns the
 *   release WITH its assets, so a single call answers "does it ship an APK?".
 *   Re-probing per release page would cost an order of magnitude more.
 * - Resumable. Progress lives in `star_checkpoints`, so process death, an
 *   expired quota, or a cancelled run resumes instead of restarting.
 *
 * [maxChecksPerRun] bounds one run against the authenticated quota (5,000/hr);
 * the remainder is picked up next time.
 */
class StarSyncEngine(
    private val api: GitHubApi,
    private val catalog: CatalogRepository,
    private val checkpoints: dev.gstore.database.StarCheckpointDao,
    private val now: () -> Long = System::currentTimeMillis,
) {

    data class Progress(
        val checked: Int = 0,
        val withApk: Int = 0,
        val total: Int = 0,
        val done: Boolean = false,
    )

    data class Result(
        val checked: Int,
        val withApk: Int,
        val exhausted: Boolean,
        val message: String? = null,
    ) {
        val progress: Progress
            get() = Progress(checked, withApk, total = checked, done = !exhausted)
    }

    /**
     * @param budget how many starred repos to probe in this run.
     * @param onProgress called after each repo so the UI can show movement.
     */
    suspend fun run(
        budget: Int = DefaultBudget,
        onProgress: (Progress) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
        var page = 1
        var checked = 0
        var withApk = 0
        var exhausted = false
        var failure: String? = null
        val seen = checkpoints.all().associateBy { it.repoFullName }

        outer@ while (checked < budget) {
            val starred = try {
                when (val page1 = api.listStarred(page)) {
                    is ApiResponse.Fresh -> page1.data
                    ApiResponse.NotModified -> emptyList()
                }
            } catch (e: GitHubException.Auth) {
                failure = "GitHub rejected the token"
                break@outer
            } catch (e: GitHubException.RateLimited) {
                failure = "GitHub rate limit reached - progress saved"
                break@outer
            } catch (e: GitHubException) {
                failure = "Could not read your stars: ${e.message}"
                break@outer
            }

            if (starred.isEmpty()) break@outer

            for (dto in starred) {
                if (checked >= budget) {
                    exhausted = true
                    break@outer
                }
                val fullName = dto.fullName
                val prior = seen[fullName]
                // Re-probing costs a request, so a recent verdict is final.
                if (prior != null && now() - prior.checkedAt < RecheckAfterMillis) continue

                val hasApk = runCatching { releasesLatestHasApk(dto.owner.login, dto.fullName.substringAfter('/')) }
                    .getOrDefault(false)
                checkpoints.upsert(StarCheckpoint(fullName, now(), hasApk))
                checked++
                if (hasApk) {
                    withApk++
                    // Only the winners pay for a full sync (all release pages +
                    // assets), which is what puts real data behind the heart.
                    runCatching { catalog.sync(fullName, catalogVisible = false) }
                        .onSuccess { addFavorite(dto.fullName) }
                }
                onProgress(Progress(checked, withApk, total = checked))
            }
            page++
        }

        Result(checked, withApk, exhausted, failure)
    }

    /** One request: the latest release response carries its own assets. */
    private suspend fun releasesLatestHasApk(owner: String, name: String): Boolean = try {
        when (val latest = api.latestRelease(owner, name)) {
            is ApiResponse.Fresh -> latest.data.assets.any { asset ->
                val verdict = classifyAsset(asset.name, asset.contentType, asset.size)
                verdict.eligible && verdict.kind == AssetKind.APK
            }
            ApiResponse.NotModified -> false
        }
    } catch (e: GitHubException.NotFound) {
        // No published release at all - a normal outcome, not an error.
        false
    }

    private suspend fun addFavorite(fullName: String) {
        runCatching {
            val id = catalog.getByFullName(fullName)?.id ?: return@runCatching
            catalog.addFavorite(id, fullName)
        }
    }

    /** Star a repo on GitHub. Best-effort: a local favourite must still apply. */
    suspend fun starOnGitHub(owner: String, name: String): Boolean = try {
        api.starRepo(owner, name)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: GitHubException) {
        false
    }

    companion object {
        /** Keeps one run well inside the authenticated hourly quota. */
        const val DefaultBudget = 400

        /** A verdict older than this is re-probed, so new releases get noticed. */
        const val RecheckAfterMillis = 30L * 24 * 60 * 60 * 1000
    }
}
