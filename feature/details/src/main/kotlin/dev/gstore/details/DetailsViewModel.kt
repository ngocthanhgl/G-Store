package dev.gstore.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gstore.common.Outcome
import dev.gstore.data.AssetKind
import dev.gstore.data.CatalogRepository
import dev.gstore.data.Eligibility
import dev.gstore.data.InstalledAppsGateway
import dev.gstore.data.ReadmeRepository
import dev.gstore.data.artifactOf
import dev.gstore.data.classifyAsset
import dev.gstore.database.DownloadRecord
import dev.gstore.model.ApkArtifact
import dev.gstore.model.Release
import dev.gstore.model.ReleaseAsset
import dev.gstore.model.Repository
import dev.gstore.ui.Block
import dev.gstore.ui.parseMarkdown
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
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

data class DetailsUiState(
    val repo: Repository? = null,
    val loadingRepo: Boolean = true,
    val releases: List<Release> = emptyList(),
    /** Releases with at least one eligible APK — the only ones shown. */
    val apkReleaseIds: Set<Long> = emptySet(),
    val selectedReleaseId: Long? = null,
    val assetRows: List<AssetRow> = emptyList(),
    val hero: HeroDlState = HeroDlState.IdleDownload,
    /** Parsed README blocks for the inline expandable preview. */
    val readmeBlocks: List<Block> = emptyList(),
    val loadingAssets: Boolean = false,
    /** True while the APK scan over cached releases is still running. */
    val scanningApks: Boolean = false,
    val isFavorite: Boolean = false,
    val syncing: Boolean = false,
    val notice: String? = null,
)

/**
 * Latest stable release wins by default; prereleases stay inspectable via
 * [selectRelease]. Older releases remain listed — never silently replaced.
 */
fun selectDefaultRelease(releases: List<Release>): Long? =
    (releases.firstOrNull { !it.prerelease } ?: releases.firstOrNull())?.id

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
    private val readme: ReadmeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DetailsUiState())
    val state: StateFlow<DetailsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch {
            // GitHub repository IDs are stable across search and sync
            // (DATA_MODEL.md), so the passed id stays valid after on-demand sync.
            catalog.observeReleases(repoId).collectLatest { releases ->
                val current = _state.value
                val selected = current.selectedReleaseId
                val next = if (selected != null && releases.any { it.id == selected }) selected
                else selectDefaultRelease(releases)
                _state.value = current.copy(releases = releases, selectedReleaseId = next)
                if (next != null && next != current.selectedReleaseId) loadAssets(next)
                scanApkReleases(releases)
            }
        }
        viewModelScope.launch {
            catalog.observeFavoriteIds().collectLatest { ids ->
                _state.value = _state.value.copy(isFavorite = repoId in ids)
            }
        }
    }

    /**
     * Load the repo from cache, syncing on demand when opened from a
     * network-only result (search/discover) that was never persisted.
     * Previously this left repo null and the screen spun forever.
     */
    private suspend fun load() {
        _state.value = _state.value.copy(loadingRepo = true, notice = null)
        var repo = catalog.getRepository(repoId)
        if (repo == null && fullName.isNotBlank()) {
            _state.value = _state.value.copy(syncing = true)
            when (val result = catalog.sync(fullName)) {
                is Outcome.Ok -> repo = catalog.getByFullName(fullName)
                is Outcome.Err -> _state.value = _state.value.copy(notice = result.failure)
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
                is Outcome.Ok -> repo = catalog.getByFullName(resolvedName)
                is Outcome.Err -> _state.value = _state.value.copy(notice = result.failure)
            }
            _state.value = _state.value.copy(syncing = false)
        }
        _state.value = _state.value.copy(repo = repo, loadingRepo = false)
        val resolved = repo
        if (resolved != null) {
            val parts = resolved.fullName.split('/')
            if (parts.size == 2) loadReadme(parts[0], parts[1], resolved.fullName)
        }
    }

    /** Inline README preview: refresh, then parse off the main thread. */
    private fun loadReadme(owner: String, name: String, fullName: String) {
        viewModelScope.launch {
            runCatching { readme.refresh(owner, name) }
            val markdown = readme.observe(fullName).first()?.markdown ?: return@launch
            val blocks = withContext(Dispatchers.Default) { parseMarkdown(markdown) }
            _state.value = _state.value.copy(readmeBlocks = blocks)
        }
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

    private fun scanApkReleases(releases: List<Release>) {
        val gen = ++scanGen
        _state.value = _state.value.copy(scanningApks = true)
        viewModelScope.launch {
            val ids = releases.filter { rel ->
                runCatching {
                    catalog.allAssets(rel.id).any {
                        val e = classifyAsset(it.filename, it.contentType, it.size)
                        e.eligible && e.kind == AssetKind.APK
                    }
                }.getOrDefault(false)
            }.map { it.id }.toSet()
            if (gen != scanGen) return@launch // superseded by a newer scan
            val current = _state.value
            if (current.releases != releases) {
                _state.value = current.copy(scanningApks = gen == scanGen)
                return@launch // superseded
            }
            val selected = current.selectedReleaseId
            val next = if (selected != null && selected in ids) selected
            else releases.firstOrNull { it.id in ids }?.id
            var updated = current.copy(
                apkReleaseIds = ids,
                selectedReleaseId = next,
                scanningApks = false,
            )
            if (next == null) updated = updated.copy(assetRows = emptyList(), loadingAssets = false)
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
            _state.value = _state.value.copy(syncing = true, notice = null)
            when (val result = catalog.sync(repo.fullName)) {
                is Outcome.Ok -> Unit
                is Outcome.Err -> _state.value = _state.value.copy(notice = result.failure)
            }
            _state.value = _state.value.copy(syncing = false)
        }
    }

    fun dismissNotice() {
        _state.value = _state.value.copy(notice = null)
    }
}
