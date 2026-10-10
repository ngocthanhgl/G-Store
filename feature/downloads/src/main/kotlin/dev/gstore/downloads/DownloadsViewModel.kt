package dev.gstore.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gstore.database.DownloadRecord
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DownloadsUiState(
    val active: List<DownloadRecord> = emptyList(),
    val history: List<DownloadRecord> = emptyList(),
    val notice: String? = null,
)

class DownloadsViewModel(
    private val repository: DownloadRepository,
) : ViewModel() {
    val state: StateFlow<DownloadsUiState> = repository.observeAll()
        .map { records ->
            val (running, done) = records.partition {
                it.status == "QUEUED" || it.status == "ACTIVE"
            }
            DownloadsUiState(active = running, history = done)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

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
