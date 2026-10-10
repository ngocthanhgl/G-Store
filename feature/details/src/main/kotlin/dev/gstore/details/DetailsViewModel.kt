package dev.gstore.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gstore.common.Outcome
import dev.gstore.data.CatalogRepository
import dev.gstore.data.Eligibility
import dev.gstore.data.artifactOf
import dev.gstore.data.classifyAsset
import dev.gstore.datastore.PreferencesRepository
import dev.gstore.model.ApkArtifact
import dev.gstore.model.Release
import dev.gstore.model.ReleaseAsset
import dev.gstore.model.Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** One row of the artifact chooser — non-APKs labeled, never hidden. */
data class AssetRow(
    val asset: ReleaseAsset,
    val eligibility: Eligibility,
    val artifact: ApkArtifact,
)

data class DetailsUiState(
    val repo: Repository? = null,
    val loadingRepo: Boolean = true,
    val releases: List<Release> = emptyList(),
    val selectedReleaseId: Long? = null,
    val assetRows: List<AssetRow> = emptyList(),
    val loadingAssets: Boolean = false,
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
    private val prefs: PreferencesRepository,
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
        _state.value = _state.value.copy(repo = repo, loadingRepo = false)
        if (repo != null) prefs.recordView(repo.fullName)
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
        }
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
