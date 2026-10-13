package dev.gstore.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gstore.data.CatalogRepository
import dev.gstore.database.DownloadRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    val installedRefreshing: Boolean = false,
    val sort: InstalledSort = InstalledSort.NAME,
    val notice: String? = null,
)

class DownloadsViewModel(
    private val repository: DownloadRepository,
    private val installedApps: InstalledAppsGateway,
    private val catalog: CatalogRepository,
) : ViewModel() {
    private val installed = MutableStateFlow<List<InstalledRow>>(emptyList())
    private val installedRefreshing = MutableStateFlow(false)
    private val sort = MutableStateFlow(InstalledSort.NAME)
    private val notice = MutableStateFlow<String?>(null)

    val state: StateFlow<DownloadsUiState> = combine(
        repository.observeAll(),
        installed,
        installedRefreshing,
        sort,
        notice,
    ) { records, rows, refreshing, order, message ->
        val (running, done) = records.partition {
            it.status == "QUEUED" || it.status == "ACTIVE"
        }
        // COMPLETED rows with a package live on the Installed shelf now —
        // history keeps failures, cancellations, and package-less files.
        val history = done.filter { it.status != "COMPLETED" || it.packageName == null }
        DownloadsUiState(
            active = running,
            history = history,
            installed = sortRows(rows, order),
            installedRefreshing = refreshing,
            sort = order,
            notice = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

    fun setSort(order: InstalledSort) {
        sort.value = order
    }

    fun dismissNotice() {
        notice.value = null
    }

    /**
     * Re-read PackageManager for every tracked package and re-check the
     * catalog for newer tags on the tracked repos. User-triggered and
     * bounded by the installed count (usually a handful) — never a scan.
     */
    fun refreshInstalled() {
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
        catalog.observeReleases(repo.id).first().firstOrNull()?.tag
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

    fun recordFor(assetId: Long) = repository.observe(assetId)
}

private fun sortRows(rows: List<InstalledRow>, order: InstalledSort): List<InstalledRow> =
    when (order) {
        InstalledSort.NAME -> rows.sortedBy { it.label.lowercase() }
        InstalledSort.RECENT -> rows.sortedByDescending { it.lastUpdateTime }
        InstalledSort.SIZE -> rows.sortedByDescending { it.record.bytesTotal }
    }
