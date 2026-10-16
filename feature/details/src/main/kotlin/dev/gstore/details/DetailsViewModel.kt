package dev.gstore.details

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gstore.common.Outcome
import dev.gstore.data.AssetKind
import dev.gstore.data.CatalogRepository
import dev.gstore.data.Eligibility
import dev.gstore.data.InstalledAppsGateway
import dev.gstore.data.artifactOf
import dev.gstore.data.classifyAsset
import dev.gstore.database.DownloadRecord
import dev.gstore.datastore.PreferencesRepository
import dev.gstore.model.ApkArtifact
import dev.gstore.model.Release
import dev.gstore.model.ReleaseAsset
import dev.gstore.model.Repository
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One row of the artifact chooser — non-APKs labeled, never hidden. */
data class AssetRow(
    val asset: ReleaseAsset,
    val eligibility: Eligibility,
    val artifact: ApkArtifact,
)

/** Hero download button state for the recommended APK of the selection. */
sealed interface HeroDlState {
    data object IdleDownload : HeroDlState
    data class Downloading(val done: Long, val total: Long) : HeroDlState
    data class ReadyInstall(val record: DownloadRecord) : HeroDlState
    data class Installed(val packageName: String, val versionName: String?) : HeroDlState
    data object FailedRetry : HeroDlState
}

@Immutable
data class DetailsUiState(
    val repo: Repository? = null,
    val loadingRepo: Boolean = true,
    val releases: List<Release> = emptyList(),
    /** Releases with at least one eligible APK — the only ones shown. */
    val apkReleaseIds: Set<Long> = emptySet(),
    val selectedReleaseId: Long? = null,
    val assetRows: List<AssetRow> = emptyList(),
    val hero: HeroDlState = HeroDlState.IdleDownload,
    val loadingAssets: Boolean = false,
    /** True while the APK scan over cached releases is still running. */
    val scanningApks: Boolean = false,
    val isFavorite: Boolean = false,
    val syncing: Boolean = false,
    /** Last on-demand sync failure — shown inline, not just a snackbar. */
    val syncError: String? = null,
    val notice: String? = null,
)

/**
 * Latest stable release wins by default; prereleases stay inspectable via
 * [selectRelease]. Older releases remain listed — never silently replaced.
 */
fun selectDefaultRelease(releases: List<Release>): Long? =
    (releases.firstOrNull { !it.prerelease } ?: releases.firstOrNull())?.id

/**
 * Stable-aware default pick: with prereleases excluded and no stable
 * release present, there is no default (strict — never silently fall back
 * to a beta). Explicit chip taps stay unrestricted user intent.
 */
private fun DetailsViewModel.defaultRelease(releases: List<Release>, includePre: Boolean): Long? {
    val pool = if (includePre) releases else releases.filter { !it.prerelease }
    return pool.firstOrNull()?.id
}

/** 12.0 MB / 1.5 GB — binary units, one decimal. */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "$bytes B" else "%.1f %s".format(value, units[unit])
}

class DetailsViewModel(
    private val repoId: Long,
    private val fullName: String,
    private val catalog: CatalogRepository,
    private val tracker: DownloadTracker,
    private val installed: InstalledAppsGateway,
    private val prefs: PreferencesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DetailsUiState())
    val state: StateFlow<DetailsUiState> = _state.asStateFlow()

    /**
     * The id actually observed. Entry points may synthesize id 0 (Downloads
     * "browse repo"); after on-demand sync the real GitHub id is known and
     * the collectors must follow it, or the release list stays empty forever.
     */
    private val resolvedId = MutableStateFlow(repoId)

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch {
            // GitHub repository IDs are stable across search and sync
            // (DATA_MODEL.md), so the passed id stays valid after on-demand sync.
            resolvedId.flatMapLatest { id -> catalog.observeReleases(id) }.collectLatest { releases ->
                val current = _state.value
                val selected = current.selectedReleaseId
                val includePre = prefs.appearance.first().includePrereleases
                val next = if (selected != null && releases.any { it.id == selected }) selected
                else defaultRelease(releases, includePre) ?: releases.firstOrNull()?.id
                _state.value = current.copy(releases = releases, selectedReleaseId = next)
                if (next != null && next != current.selectedReleaseId) loadAssets(next)
                scanApkReleases(releases, includePre)
            }
        }
        viewModelScope.launch {
            combine(resolvedId, catalog.observeFavoriteIds()) { id, ids -> id in ids }
                .collectLatest { fav ->
                    _state.value = _state.value.copy(isFavorite = fav)
                }
        }
    }

    /**
     * Load the repo from cache, syncing on demand when opened from a
     * network-only result (search/discover) that was never persisted.
     * Previously this left repo null and the screen spun forever.
     */
    private suspend fun load() {
        _state.value = _state.value.copy(loadingRepo = true, notice = null, syncError = null)
        var repo = catalog.getRepository(repoId)
        if (repo == null && fullName.isNotBlank()) {
            _state.value = _state.value.copy(syncing = true)
            when (val result = catalog.sync(fullName)) {
                is Outcome.Ok -> {
                    repo = catalog.getByFullName(fullName)
                    _state.value = _state.value.copy(syncError = null)
                }
                is Outcome.Err -> _state.value = _state.value.copy(
                    notice = result.failure,
                    syncError = result.failure,
                )
            }
            _state.value = _state.value.copy(syncing = false)
        }
        // Slim scanner rows have metadata but no assets: full-sync on entry
        // so the screen never reports "no APK" for data it never fetched.
        // A failed sync leaves the row STALE, so a later refresh retries.
        val resolvedName = repo?.fullName?.takeIf { it.isNotBlank() } ?: fullName
        if (repo != null && resolvedName.isNotBlank() && catalog.needsFullSync(resolvedName)) {
            _state.value = _state.value.copy(syncing = true)
            when (val result = catalog.sync(resolvedName)) {
                is Outcome.Ok -> {
                    repo = catalog.getByFullName(resolvedName)
                    _state.value = _state.value.copy(syncError = null)
                }
                is Outcome.Err -> _state.value = _state.value.copy(
                    notice = result.failure,
                    syncError = result.failure,
                )
            }
            _state.value = _state.value.copy(syncing = false)
        }
        if (repo != null) resolvedId.value = repo.id
        _state.value = _state.value.copy(repo = repo, loadingRepo = false)
    }

    /**
     * APK-only scope: find releases shipping at least one eligible APK and
     * prefer them for selection. Runs off the collector so a slow repo never
     * blocks the release list; stale results are discarded when releases
     * change underneath. [scanningApks] lets the hero show "finding" instead
     * of "no APK" while this runs — the generation guard keeps overlapping
     * scans from clearing each other's flag.
     */
    private var scanGen = 0

    private fun scanApkReleases(releases: List<Release>, includePre: Boolean) {
        val gen = ++scanGen
        _state.value = _state.value.copy(scanningApks = true)
        viewModelScope.launch {
            // Per-release asset scan off the collector; the generation guard
            // below keeps overlapping scans from clearing each other's flag.
            val ids = withContext(Dispatchers.Default) {
                releases.filter { rel ->
                    runCatching {
                        catalog.allAssets(rel.id).any {
                            val e = classifyAsset(it.filename, it.contentType, it.size)
                            e.eligible && e.kind == AssetKind.APK
                        }
                    }.getOrDefault(false)
                }.map { it.id }.toSet()
            }
            if (gen != scanGen) return@launch // superseded by a newer scan
            val current = _state.value
            if (current.releases != releases) {
                _state.value = current.copy(scanningApks = gen == scanGen)
                return@launch // superseded
            }
            val selected = current.selectedReleaseId
            // Never downgrade: an empty scan (or a scan racing a sync) must
            // not erase a valid selection and hide hero + notes with it.
            // The default pick honors the prerelease toggle; explicit taps
            // always win (kept by the first branch above).
            // Stable-with-APK first; prerelease-with-APK fallback (labeled
            // honestly in the UI) so the hero never idles on null while an
            // installable artifact exists. Explicit taps always win.
            val next = if (selected != null && selected in ids) selected
            else releases.firstOrNull {
                it.id in ids && (includePre || !it.prerelease)
            }?.id
                ?: releases.firstOrNull { it.id in ids }?.id
                ?: selected?.takeIf { s -> releases.any { it.id == s } }
            var updated = current.copy(
                apkReleaseIds = ids,
                selectedReleaseId = next,
                scanningApks = false,
            )
            if (releases.isEmpty()) {
                updated = updated.copy(assetRows = emptyList(), loadingAssets = false)
            }
            _state.value = updated
            if (next != null && next != selected) loadAssets(next)
        }
    }

    fun selectRelease(releaseId: Long) {
        _state.value = _state.value.copy(selectedReleaseId = releaseId)
        loadAssets(releaseId)
    }

    private fun loadAssets(releaseId: Long) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingAssets = true)
            val rows = catalog.allAssets(releaseId).map { asset ->
                val verdict = classifyAsset(asset.filename, asset.contentType, asset.size)
                AssetRow(asset, verdict, artifactOf(asset.id, asset.filename, null, verdict.kind))
            }
            _state.value = _state.value.copy(assetRows = rows, loadingAssets = false)
            val best = rows.firstOrNull {
                it.eligibility.eligible && it.eligibility.kind == AssetKind.APK
            }?.asset
            if (best != null) watchHero(best) else {
                heroJob?.cancel()
                _state.value = _state.value.copy(hero = HeroDlState.IdleDownload)
            }
        }
    }

    private var heroJob: Job? = null

    /** Track the recommended APK's record + install truth for the hero button. */
    private fun watchHero(asset: ReleaseAsset) {
        heroJob?.cancel()
        heroJob = viewModelScope.launch {
            tracker.observe(asset.id).collectLatest { record ->
                val hero = when {
                    record == null -> HeroDlState.IdleDownload
                    record.status == "ACTIVE" || record.status == "QUEUED" ->
                        HeroDlState.Downloading(record.bytesDone, record.bytesTotal)
                    record.status == "COMPLETED" &&
                        record.filePath?.let { File(it).exists() } == true -> {
                        val pkg = record.packageName
                        if (pkg == null) {
                            HeroDlState.ReadyInstall(record)
                        } else {
                            val info = installed.installedPackages(setOf(pkg))[pkg]
                            val downloadedVc = record.versionCode
                            when {
                                info == null -> HeroDlState.ReadyInstall(record)
                                downloadedVc != null &&
                                    info.versionCode < downloadedVc ->
                                    HeroDlState.ReadyInstall(record)
                                else -> HeroDlState.Installed(pkg, info.versionName)
                            }
                        }
                    }
                    else -> HeroDlState.FailedRetry
                }
                _state.value = _state.value.copy(hero = hero)
            }
        }
    }

    fun heroDownload(asset: ReleaseAsset) {
        val repo = _state.value.repo ?: return
        val tag = _state.value.releases
            .firstOrNull { it.id == _state.value.selectedReleaseId }?.tag ?: return
        // Watch the tapped asset, not just the scanned best: otherwise the
        // hero stays on "Download" for a row that is already downloading.
        watchHero(asset)
        viewModelScope.launch { tracker.start(asset, repo.fullName, tag) }
    }

    fun heroCancel(assetId: Long) {
        viewModelScope.launch { tracker.cancel(assetId) }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val repo = _state.value.repo ?: return@launch
            catalog.toggleFavorite(repo)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val repo = _state.value.repo
            if (repo == null) {
                load()
                return@launch
            }
            _state.value = _state.value.copy(syncing = true, notice = null, syncError = null)
            when (val result = catalog.sync(repo.fullName)) {
                is Outcome.Ok -> Unit
                is Outcome.Err -> _state.value = _state.value.copy(
                    notice = result.failure,
                    syncError = result.failure,
                )
            }
            _state.value = _state.value.copy(syncing = false)
        }
    }

    fun dismissNotice() {
        _state.value = _state.value.copy(notice = null)
    }
}
