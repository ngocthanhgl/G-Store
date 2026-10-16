package dev.gstore.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gstore.data.CatalogRepository
import dev.gstore.data.InstalledAppsGateway
import dev.gstore.data.UpdateState
import dev.gstore.data.classifyAsset
import dev.gstore.data.resolveUpdateState
import dev.gstore.database.DownloadRecord
import dev.gstore.datastore.PreferencesRepository
import dev.gstore.datastore.TokenStore
import dev.gstore.model.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class InstalledSort { NAME, RECENT, SIZE }

data class InstalledRow(
    val record: DownloadRecord,
    val label: String,
    val installedVersionName: String?,
    val installedVersionCode: Long?,
    val latestTag: String?,
    val updateState: UpdateState,
    val lastUpdateTime: Long,
)

data class DownloadsUiState(
    val active: List<DownloadRecord> = emptyList(),
    val history: List<DownloadRecord> = emptyList(),
    val installed: List<InstalledRow> = emptyList(),
    val favorites: List<Repository> = emptyList(),
    val manual: List<Repository> = emptyList(),
    val installedRefreshing: Boolean = false,
    val sort: InstalledSort = InstalledSort.NAME,
    val showLoginPrompt: Boolean = false,
    val notice: String? = null,
)

class DownloadsViewModel(
    private val repository: DownloadRepository,
    private val installedApps: InstalledAppsGateway,
    private val catalog: CatalogRepository,
    private val tokens: TokenStore,
    private val prefs: PreferencesRepository,
) : ViewModel() {
    private val installed = MutableStateFlow<List<InstalledRow>>(emptyList())
    private val installedRefreshing = MutableStateFlow(false)
    private val sort = MutableStateFlow(InstalledSort.NAME)
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
    }

    private val shelves = combine(favorites, manual) { f, m -> f to m }

    val state: StateFlow<DownloadsUiState> = combine(
        repository.observeAll(),
        installed,
        sort,
        refreshUi,
        shelves,
    ) { records, rows, order, ui, shelfPair ->
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
            installed = sortRows(rows, order),
            favorites = shelfPair.first,
            manual = shelfPair.second,
            installedRefreshing = ui.refreshing,
            sort = order,
            showLoginPrompt = ui.prompt,
            notice = ui.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

    fun setSort(order: InstalledSort) {
        sort.value = order
    }

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
     * Logged-in users are never throttled (5,000/hr quota): the 60 s gate
     * and its prompt apply to anonymous taps only. Automatic calls (app
     * resume) pass [auto] and return silently instead of prompting.
     */
    fun refreshInstalled(auto: Boolean = false) {
        val now = System.currentTimeMillis()
        if (tokens.token.value.isNullOrBlank() && now - lastRefreshMillis < 60_000) {
            if (!auto) showLoginPrompt.value = true
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
                installed.value = records.map { record ->
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
        viewModelScope.launch { repository.forget(assetId) }
    }

    fun removeManualRepo(fullName: String) {
        viewModelScope.launch { prefs.removeManualRepo(fullName) }
    }

    /**
     * One-tap APK for a hearted repo: latest stable release carrying an
     * eligible APK wins, prereleases are the fallback. Surfaces a notice
     * when nothing downloadable exists — never a silent no-op.
     */
    fun downloadFavorite(repo: Repository) {
        viewModelScope.launch {
            // Release walk off the main thread; only the notice lands on it.
            val started = withContext(Dispatchers.Default) {
                runCatching {
                    val releases = catalog.observeReleases(repo.id).first()
                    val includePre = prefs.appearance.first().includePrereleases
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
            if (!started) notice.value = "No downloadable APK found for ${repo.fullName}"
        }
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

private fun sortRows(rows: List<InstalledRow>, order: InstalledSort): List<InstalledRow> =
    when (order) {
        InstalledSort.NAME -> rows.sortedBy { it.label.lowercase() }
        InstalledSort.RECENT -> rows.sortedByDescending { it.lastUpdateTime }
        InstalledSort.SIZE -> rows.sortedByDescending { it.record.bytesTotal }
    }
