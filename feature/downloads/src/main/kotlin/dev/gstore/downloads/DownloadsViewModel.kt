package dev.gstore.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gstore.data.CatalogRepository
import dev.gstore.data.InstalledAppsGateway
import dev.gstore.data.StarSyncEngine
import dev.gstore.data.UpdateState
import dev.gstore.data.classifyAsset
import dev.gstore.data.resolveUpdateState
import dev.gstore.database.DownloadRecord
import dev.gstore.datastore.PreferencesRepository
import dev.gstore.datastore.TokenStore
import dev.gstore.model.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledRow(
    val record: DownloadRecord,
    val label: String,
    val installedVersionName: String?,
    val installedVersionCode: Long?,
    val latestTag: String?,
    val updateState: UpdateState,
    val lastUpdateTime: Long,
)

internal fun completedAfterActive(
    previousActiveIds: Set<Long>,
    candidates: List<DownloadRecord>,
): DownloadRecord? = candidates.firstOrNull {
    it.assetId in previousActiveIds && it.status == "COMPLETED"
}

/** Grouped star-sync flags, so the 5-arg combine stays under the arity cap. */
private data class StarSyncUi(
    val running: Boolean,
    val progress: StarSyncEngine.Progress?,
    val message: String?,
)

data class DownloadsUiState(
    val active: List<DownloadRecord> = emptyList(),
    val history: List<DownloadRecord> = emptyList(),
    val installed: List<InstalledRow> = emptyList(),
    val favorites: List<Repository> = emptyList(),
    val manual: List<Repository> = emptyList(),
    /**
     * Package name of an installed copy, keyed by repo full name. The
     * favourites shelf reads this so it can show Open/Uninstall instead of
     * offering to download a copy the user already has. Empty until the first
     * installed refresh, so a shelf can briefly under-report.
     */
    val installedByRepo: Map<String, String> = emptyMap(),
    val starSyncRunning: Boolean = false,
    val starSyncProgress: StarSyncEngine.Progress? = null,
    val starSyncNotice: String? = null,
    val installedRefreshing: Boolean = false,
    val showLoginPrompt: Boolean = false,
    val notice: String? = null,
)

class DownloadsViewModel(
    private val repository: DownloadRepository,
    private val installedApps: InstalledAppsGateway,
    private val catalog: CatalogRepository,
    private val tokens: TokenStore,
    private val prefs: PreferencesRepository,
    private val starSync: StarSyncEngine,
    private val notifications: DownloadNotifications,
) : ViewModel() {
    private val installed = MutableStateFlow<List<InstalledRow>>(emptyList())
    private val installedRefreshing = MutableStateFlow(false)
    private val showLoginPrompt = MutableStateFlow(false)
    private val notice = MutableStateFlow<String?>(null)
    private var lastRefreshMillis = 0L

    // Typed combine caps at five flows: the three small UI flows merge
    // first so the state combine stays at four.
    private data class RefreshUi(
        val refreshing: Boolean,
        val prompt: Boolean,
        val message: String?,
    )

    private val refreshUi = combine(
        installedRefreshing,
        showLoginPrompt,
        notice,
    ) { refreshing, prompt, message -> RefreshUi(refreshing, prompt, message) }

    private val favorites = MutableStateFlow<List<Repository>>(emptyList())
    private val manual = MutableStateFlow<List<Repository>>(emptyList())
    private val starSyncRunning = MutableStateFlow(false)
    private val starSyncProgress = MutableStateFlow<StarSyncEngine.Progress?>(null)
    private val starSyncNotice = MutableStateFlow<String?>(null)

    /**
     * Pull the user's GitHub stars into Favourites. Runs once per app start
     * when a token exists; progress is checkpointed in Room, so a killed
     * process resumes instead of restarting.
     */
    fun syncStarred() {
        if (starSyncRunning.value) return
        if (tokens.token.value.isNullOrBlank()) {
            starSyncNotice.value = "Log in with a GitHub token to import your stars."
            return
        }
        viewModelScope.launch {
            starSyncRunning.value = true
            starSyncNotice.value = null
            try {
                val result = starSync.run { progress -> starSyncProgress.value = progress }
                starSyncNotice.value = result.message ?: when {
                    result.checked == 0 -> "No new stars to check."
                    result.withApk == 0 ->
                        "Checked ${result.checked} starred repos - none ship an APK."
                    result.exhausted ->
                        "Imported ${result.withApk} of ${result.checked} checked " +
                            "(${result.withApk} with an APK). More resumes next time."
                    else -> "Imported ${result.withApk} starred repos with an APK."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                starSyncNotice.value = "Star import failed: ${e.message}"
            }
            starSyncRunning.value = false
        }
    }

    fun dismissStarSyncNotice() {
        starSyncNotice.value = null
    }

    init {
        viewModelScope.launch {
            catalog.observeFavoriteIds().collectLatest { ids ->
                catalog.observeByIds(ids.toList()).collectLatest { favorites.value = it }
            }
        }
        viewModelScope.launch {
            prefs.manualRepos.collectLatest { names ->
                manual.value = names.mapNotNull { name ->
                    runCatching { catalog.getByFullName(name) }.getOrNull()
                }
            }
        }
        // Pull GitHub stars in the background on start. Checkpoints make this
        // cheap on the second launch, and a logged-out device skips it entirely.
        viewModelScope.launch {
            if (!tokens.token.value.isNullOrBlank()) syncStarred()
        }
        refreshInstalled(auto = true)
    }

    private val shelves = combine(favorites, manual) { f, m -> f to m }
    private val starSyncUi = combine(starSyncRunning, starSyncProgress, starSyncNotice) {
            running, progress, message ->
        StarSyncUi(running, progress, message)
    }

    val state: StateFlow<DownloadsUiState> = combine(
        repository.observeAll(),
        installed,
        refreshUi,
        shelves,
        starSyncUi,
    ) { records, rows, ui, shelfPair, star ->
        val (running, done) = records.partition {
            it.status == "QUEUED" || it.status == "ACTIVE"
        }
        // COMPLETED rows with a package live on the Installed shelf — but only
        // once the shelf actually has a row for them. Until the next installed
        // refresh, a fresh download stays visible in history instead of
        // vanishing into a shelf that has not caught up yet.
        val history = done.filter { record ->
            record.status != "COMPLETED" || record.packageName == null ||
                rows.none { it.record.assetId == record.assetId }
        }
        DownloadsUiState(
            active = running,
            history = history,
            // Single fixed order: alphabetical by label. The sort chips were
            // removed — one predictable order beats three forgotten ones.
            installed = rows.sortedBy { it.label.lowercase() },
            favorites = shelfPair.first,
            manual = shelfPair.second,
            // Derived from the same rows, so the shelf can never disagree with
            // what the Installed shelf reports.
            installedByRepo = rows.mapNotNull { row ->
                row.record.packageName?.let { row.record.repoFullName to it }
            }.toMap(),
            starSyncRunning = star.running,
            starSyncProgress = star.progress,
            starSyncNotice = star.message,
            installedRefreshing = ui.refreshing,
            showLoginPrompt = ui.prompt,
            notice = ui.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

    fun dismissNotice() {
        notice.value = null
    }

    fun dismissLoginPrompt() {
        showLoginPrompt.value = false
    }

    /**
     * Re-read PackageManager for every tracked package and re-check the
     * catalog for newer tags on the tracked repos. User-triggered and
     * bounded by the installed count (usually a handful) — never a scan.
     * Automatic calls are bounded to one per minute to avoid duplicate
     * startup/resume requests. Manual checks are throttled only for anonymous
     * users; logged-in users have the higher GitHub quota.
     */
    fun refreshInstalled(auto: Boolean = false, force: Boolean = false) {
        val now = System.currentTimeMillis()
        val anonymous = tokens.token.value.isNullOrBlank()
        if (!force && (auto || anonymous) && now - lastRefreshMillis < 60_000) {
            if (!auto && anonymous) showLoginPrompt.value = true
            return
        }
        lastRefreshMillis = now
        viewModelScope.launch {
            installedRefreshing.value = true
            try {
                val records = withContext(Dispatchers.IO) {
                    repository.observeAll().first()
                }.filter { it.status == "COMPLETED" && it.packageName != null }
                val infos = installedApps.installedPackages(
                    records.mapNotNull { it.packageName }.toSet(),
                )
                val refreshed = records.map { record ->
                    val info = infos[record.packageName]
                    val latestTag = latestTagFor(record.repoFullName)
                    InstalledRow(
                        record = record,
                        label = info?.label ?: record.filename,
                        installedVersionName = info?.versionName,
                        installedVersionCode = info?.versionCode,
                        latestTag = latestTag,
                        updateState = resolveUpdateState(
                            installedVc = info?.versionCode,
                            downloadedVc = record.versionCode,
                            latestTag = latestTag,
                            recordTag = record.releaseTag,
                            installedVn = info?.versionName,
                        ),
                        lastUpdateTime = info?.lastUpdateTime ?: 0L,
                    )
                }
                installed.value = refreshed
                refreshed.filter { it.updateState == UpdateState.UPDATE_AVAILABLE }
                    .forEach { row ->
                        val packageName = row.record.packageName ?: return@forEach
                        val releaseTag = row.latestTag ?: return@forEach
                        notifications.updateAvailable(packageName, row.label, releaseTag)
                    }
            } catch (e: Exception) {
                notice.value = "Could not refresh installed apps: ${e.message}"
            }
            installedRefreshing.value = false
        }
    }

    /** Latest cached release tag for a repo; syncs on miss (bounded, one repo). */
    private suspend fun latestTagFor(fullName: String): String? = runCatching {
        val repo = catalog.getByFullName(fullName) ?: catalog.sync(fullName).let {
            catalog.getByFullName(fullName)
        } ?: return null
        val includePre = prefs.appearance.first().includePrereleases
        catalog.observeReleases(repo.id).first()
            .firstOrNull { includePre || !it.prerelease }?.tag
    }.getOrNull()

    fun cancel(assetId: Long) {
        viewModelScope.launch { repository.cancel(assetId) }
    }

    fun retry(assetId: Long) {
        viewModelScope.launch { repository.retry(assetId) }
    }

    fun forget(assetId: Long) {
        viewModelScope.launch {
            try {
                repository.forget(assetId)
                refreshInstalled(auto = true, force = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice.value = "Could not remove downloaded APK: ${e.message}"
            }
        }
    }

    fun removeManualRepo(fullName: String) {
        viewModelScope.launch { prefs.removeManualRepo(fullName) }
    }

    /**
     * One-tap update from the Installed shelf: resolves the newest
     * stable-or-allowed release carrying an eligible APK and starts it
     * directly — no detour through details. Respects the prerelease toggle.
     */
    fun downloadUpdate(record: DownloadRecord) {
        viewModelScope.launch {
            val started = withContext(Dispatchers.Default) {
                runCatching {
                    val repo = catalog.getByFullName(record.repoFullName) ?: return@runCatching false
                    val includePre = prefs.appearance.first().includePrereleases
                    val releases = catalog.observeReleases(repo.id).first()
                    val ordered = if (includePre) {
                        releases.filter { !it.prerelease } + releases.filter { it.prerelease }
                    } else {
                        releases.filter { !it.prerelease }
                    }
                    for (rel in ordered) {
                        val apk = catalog.allAssets(rel.id).firstOrNull {
                            classifyAsset(it.filename, it.contentType, it.size).eligible
                        }
                        if (apk != null) {
                            return@runCatching repository.start(
                                assetId = apk.id,
                                repoFullName = repo.fullName,
                                releaseTag = rel.tag,
                                rawFilename = apk.filename,
                                url = apk.downloadUrl,
                                expectedSize = apk.size,
                                expectedDigest = apk.digest,
                            )
                        }
                    }
                    false
                }.getOrDefault(false)
            }
            if (!started) notice.value = "No downloadable APK found for ${record.repoFullName}"
        }
    }

    fun recordFor(assetId: Long) = repository.observe(assetId)
}
