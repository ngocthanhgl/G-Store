package dev.gstore.details

import androidx.compose.runtime.Immutable
import android.util.Log
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
import dev.gstore.network.ApiResponse
import dev.gstore.network.ContentsDto
import dev.gstore.ui.Block
import dev.gstore.ui.Span
import dev.gstore.ui.parseMarkdown
import dev.gstore.ui.resolveRelative
import java.io.File
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** One row of the artifact chooser — non-APKs labeled, never hidden. */
data class AssetRow(
    val asset: ReleaseAsset,
    val eligibility: Eligibility,
    val artifact: ApkArtifact,
)

/** One repository image, sourced from the root listing or README. */
data class RepositoryImage(val url: String, val name: String)

private val excludedImageTerms =
    listOf(
        "badge", "shield", "icon", "favicon", "logo", "avatar", "tracking", "pixel", "spacer",
        "banner", "cover", "header", "social", "feature", "sponsor", "background",
    )

internal fun meetsMinimumImageSize(width: Float, height: Float): Boolean =
    width >= MIN_IMAGE_DIMENSION || height >= MIN_IMAGE_DIMENSION

private const val MIN_IMAGE_DIMENSION = 500f

// shortcut: README-referenced files plus repo-root images; add bounded Git tree traversal if docs not linked by README.
internal fun selectRepositoryImages(
    contents: List<ContentsDto>,
    markdown: String,
    owner: String,
    repo: String,
    branch: String,
): List<RepositoryImage> {
    val rootImages = contents.mapNotNull { item ->
        if (item.type != "file") return@mapNotNull null
        val name = item.name.lowercase()
        if (name.substringAfterLast('.', "") !in setOf("png", "jpg", "jpeg", "webp")) {
            return@mapNotNull null
        }
        if (excludedImageTerms.any(name::contains)) return@mapNotNull null
        val url = item.download_url ?: return@mapNotNull null
        repositoryImage(url, item.name)?.let { image ->
            (if (screenshotNameTerms.any(name::contains)) 12 else 0) to image
        }
    }
    val readmeImages = imageSources(parseMarkdown(markdown))
        .mapNotNull { source ->
            val url = resolveRelative(source, owner, repo, forImage = true, branch = branch)
            repositoryImage(url, source.substringAfterLast('/').substringBefore('?').ifBlank { "README image" })
                ?.let { 20 to it }
        }
    return (readmeImages + rootImages)
        .distinctBy { it.second.url }
        .sortedWith(compareByDescending<Pair<Int, RepositoryImage>> { it.first }.thenBy { it.second.name })
        .take(24)
        .map { it.second }
}

private val screenshotNameTerms = listOf("screenshot", "screen", "shot", "preview", "demo", "phone", "tablet")

private fun repositoryImage(url: String, name: String): RepositoryImage? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    if (uri.scheme != "https" || uri.host != "raw.githubusercontent.com") return null
    if (excludedImageTerms.any(name.lowercase()::contains)) return null
    return RepositoryImage(url, name)
}

private fun imageSources(blocks: List<Block>): List<String> = buildList {
    fun addSpans(spans: List<Span>) {
        spans.forEach { span ->
            when (span) {
                is Span.Picture -> add(span.src)
                is Span.Emphasis -> addSpans(span.children)
                is Span.Strong -> addSpans(span.children)
                is Span.Strike -> addSpans(span.children)
                is Span.Hyperlink -> addSpans(span.children)
                else -> Unit
            }
        }
    }
    fun addBlocks(items: List<Block>) {
        items.forEach { block ->
            when (block) {
                is Block.Paragraph -> addSpans(block.spans)
                is Block.Quote -> addBlocks(block.children)
                is Block.ListBlock -> block.items.forEach { item ->
                    addSpans(item.spans)
                    addBlocks(item.children)
                }
                else -> Unit
            }
        }
    }
    addBlocks(blocks)
}

/** Hero download button state for the recommended APK of the selection. */
sealed interface HeroDlState {
    data object IdleDownload : HeroDlState
    data class Downloading(val done: Long, val total: Long) : HeroDlState
    data class ReadyInstall(val record: DownloadRecord) : HeroDlState
    data class Installed(val packageName: String, val versionName: String?) : HeroDlState
    data object FailedRetry : HeroDlState
}

internal fun shouldAutoInstallHero(previous: HeroDlState?, current: HeroDlState): Boolean =
    previous is HeroDlState.Downloading && current is HeroDlState.ReadyInstall

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
    /** Package name of an already-installed copy of this app, if any. */
    val installedPackageName: String? = null,
    /** Repository images from root contents and the rendered README. */
    val images: List<RepositoryImage> = emptyList(),
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
    private val searchResult: Boolean = false,
) : ViewModel() {
    private val _state = MutableStateFlow(DetailsUiState())
    val state: StateFlow<DetailsUiState> = _state.asStateFlow()

    /**
     * The id actually observed. Entry points may synthesize id 0 (Downloads
     * "browse repo"); after on-demand sync the real GitHub id is known and
     * the collectors must follow it, or the release list stays empty forever.
     */
    private val resolvedId = MutableStateFlow(repoId)
    private val initialLoad = CompletableDeferred<Unit>()
    private val refreshMutex = Mutex()
    private var initialSyncSucceeded = false
    private var searchOnly = false

    init {
        viewModelScope.launch {
            try {
                load()
            } finally {
                initialLoad.complete(Unit)
            }
        }
        viewModelScope.launch {
            // GitHub repository IDs are stable across search and sync
            // (DATA_MODEL.md), so the passed id stays valid after on-demand sync.
            resolvedId.flatMapLatest { id -> catalog.observeReleases(id) }.collectLatest { releases ->
                val current = _state.value
                val selected = current.selectedReleaseId
                val includePre = prefs.appearance.firstOrNull()?.includePrereleases ?: false
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
        searchOnly = searchResult && (repo == null || catalog.isSearchOnly(repoId))
        if (repo == null && fullName.isNotBlank()) {
            _state.value = _state.value.copy(syncing = true)
            when (val result = catalog.sync(fullName, catalogVisible = !searchOnly)) {
                is Outcome.Ok -> {
                    initialSyncSucceeded = true
                    repo = catalog.getByFullName(fullName)
                    if (searchOnly) repo?.let { catalog.markSearchOnly(it.id) }
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
        // Paint whatever is cached BEFORE the full sync runs. That sync walks
        // every release page and used to hold the screen on a bare spinner for
        // its whole duration, so a cached repo looked like it was loading.
        _state.value = _state.value.copy(repo = repo, loadingRepo = false)
        viewModelScope.launch { loadRepositoryImages(repo) }

    }

    /** Refresh repository/release metadata whenever this screen is entered. */
    fun refreshLatestReleaseInfo() {
        viewModelScope.launch {
            initialLoad.await()
            if (initialSyncSucceeded) {
                initialSyncSucceeded = false
                return@launch
            }
            val name = _state.value.repo?.fullName?.takeIf { it.isNotBlank() } ?: fullName
            if (name.isNotBlank()) refreshInBackground(name)
        }
    }

    /** Background full sync: updates state in place, never blanks the screen. */
    private suspend fun refreshInBackground(name: String) {
        if (!refreshMutex.tryLock()) return
        _state.value = _state.value.copy(syncing = true)
        try {
            when (val result = catalog.sync(name, catalogVisible = !searchOnly)) {
                is Outcome.Ok -> {
                    val fresh = catalog.getByFullName(name)
                    if (searchOnly) fresh?.let { catalog.markSearchOnly(it.id) }
                    val syncedRepo = fresh ?: _state.value.repo
                    _state.value = _state.value.copy(
                        repo = syncedRepo,
                        syncError = null,
                    )
                    syncedRepo?.let { refreshReleaseSnapshot(it.id) }
                }
                is Outcome.Err -> _state.value = _state.value.copy(
                    notice = result.failure,
                    syncError = result.failure,
                )
            }
        } finally {
            _state.value = _state.value.copy(syncing = false)
            refreshMutex.unlock()
        }
    }

    /** Asset rows are written after release rows, so scan once more after sync completes. */
    private suspend fun refreshReleaseSnapshot(repoId: Long) {
        val releases = catalog.observeReleases(repoId).firstOrNull().orEmpty()
        val includePre = prefs.appearance.firstOrNull()?.includePrereleases ?: false
        val current = _state.value
        val selected = current.selectedReleaseId?.takeIf { id -> releases.any { it.id == id } }
            ?: defaultRelease(releases, includePre)
            ?: releases.firstOrNull()?.id
        _state.value = current.copy(releases = releases, selectedReleaseId = selected)
        scanApkReleases(releases, includePre)
    }

    /** README and root images are best-effort presentation data, never catalog eligibility. */
    private suspend fun loadRepositoryImages(repo: Repository?) {
        if (repo == null || repo.defaultBranch.isBlank()) return
        try {
            val name = repo.fullName.substringAfter('/')
            val contents = (catalog.fetchContents(repo.owner, name, repo.defaultBranch) as? ApiResponse.Fresh)
                ?.data.orEmpty()
            val markdown = (catalog.fetchReadme(repo.owner, name) as? ApiResponse.Fresh)
                ?.data?.markdown.orEmpty()
            _state.value = _state.value.copy(
                images = selectRepositoryImages(contents, markdown, repo.owner, name, repo.defaultBranch),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("DetailsViewModel", "Could not load repository images", e)
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

    private fun scanApkReleases(releases: List<Release>, includePre: Boolean) {
        val gen = ++scanGen
        _state.value = _state.value.copy(scanningApks = true)
        viewModelScope.launch {
            // Per-release asset scan off the collector; the generation guard
            // below keeps overlapping scans from clearing each other's flag.
            val ids = withContext(Dispatchers.Default) {
                catalog.releaseIdsWithApk(releases.map { it.id })
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
            if (next != null) loadAssets(next)
        }
    }

    private var assetLoadJob: Job? = null

    fun selectRelease(releaseId: Long) {
        _state.value = _state.value.copy(selectedReleaseId = releaseId)
        loadAssets(releaseId)
    }

    fun refreshInstalledState() {
        val asset = _state.value.assetRows.firstOrNull {
            it.eligibility.eligible && it.eligibility.kind == AssetKind.APK
        }?.asset ?: return
        viewModelScope.launch {
            try {
                val record = tracker.observe(asset.id).firstOrNull() ?: return@launch
                val packageName = record.packageName ?: return@launch
                if (record.status != "COMPLETED" ||
                    record.filePath?.let { File(it).exists() } != true
                ) return@launch
                val info = withContext(Dispatchers.IO) {
                    installed.installedPackages(setOf(packageName))[packageName]
                }
                val downloadedVersion = record.versionCode
                val hero = if (info == null ||
                    (downloadedVersion != null && info.versionCode < downloadedVersion)
                ) {
                    HeroDlState.ReadyInstall(record)
                } else {
                    HeroDlState.Installed(packageName, info.versionName)
                }
                _state.value = _state.value.copy(
                    hero = hero,
                    installedPackageName = (hero as? HeroDlState.Installed)?.packageName,
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (e: Exception) {
                Log.w("DetailsViewModel", "Could not refresh installed app state", e)
            }
        }
    }

    private fun loadAssets(releaseId: Long) {
        assetLoadJob?.cancel()
        _state.value = _state.value.copy(
            assetRows = emptyList(),
            loadingAssets = true,
            hero = HeroDlState.IdleDownload,
            installedPackageName = null,
        )
        assetLoadJob = viewModelScope.launch {
            try {
                loadAssetsInner(releaseId)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (t: Throwable) {
                // Asset rows and the installed-state probe both read storage and
                // PackageManager. A failure here is a degraded hero button, never
                // a reason to take the process down with an uncaught coroutine.
                heroJob?.cancel()
                _state.value = _state.value.copy(loadingAssets = false, hero = HeroDlState.IdleDownload)
            }
        }
    }

    private suspend fun loadAssetsInner(releaseId: Long) {
        val rows = catalog.allAssets(releaseId).map { asset ->
            val verdict = classifyAsset(asset.filename, asset.contentType, asset.size)
            AssetRow(asset, verdict, artifactOf(asset.id, asset.filename, null, verdict.kind))
        }
        if (_state.value.selectedReleaseId != releaseId) return
        _state.value = _state.value.copy(assetRows = rows, loadingAssets = false)
        val best = rows.firstOrNull {
            it.eligibility.eligible && it.eligibility.kind == AssetKind.APK
        }?.asset
        if (best != null) {
            // Initial check: is this APK already installed (via a previous
            // G Store download)? If so, show Open/Uninstall immediately
            // instead of "Download". firstOrNull, not first: an empty flow
            // here used to throw NoSuchElementException out of the scope.
            val existing = tracker.observe(best.id).firstOrNull()
            if (existing != null && existing.status == "COMPLETED") {
                val pkg = existing.packageName
                if (pkg != null) {
                    val info = withContext(Dispatchers.IO) {
                        installed.installedPackages(setOf(pkg))[pkg]
                    }
                    if (info != null) {
                        _state.value = _state.value.copy(
                            hero = HeroDlState.Installed(pkg, existing.versionName),
                            installedPackageName = pkg,
                        )
                    } else {
                        watchHero(best)
                    }
                } else {
                    watchHero(best)
                }
            } else {
                watchHero(best)
            }
        } else {
            heroJob?.cancel()
            _state.value = _state.value.copy(hero = HeroDlState.IdleDownload)
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
                if (hero is HeroDlState.Installed) {
                    _state.value = _state.value.copy(installedPackageName = hero.packageName)
                } else if (hero !is HeroDlState.Installed) {
                    _state.value = _state.value.copy(installedPackageName = null)
                }
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
            when (val result = catalog.sync(repo.fullName, catalogVisible = !searchOnly)) {
                is Outcome.Ok -> {
                    if (searchOnly) catalog.markSearchOnly(repo.id)
                    refreshReleaseSnapshot(repo.id)
                }
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
