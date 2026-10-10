package dev.gstore.readme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gstore.common.Outcome
import dev.gstore.data.ReadmeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ReadmeStatus { LOADING, READY, MISSING, ERROR }

data class ReadmeUiState(
    val status: ReadmeStatus = ReadmeStatus.LOADING,
    val blocks: List<Block> = emptyList(),
    /** Cached copy is older than TTL or a refresh failed while a copy exists. */
    val stale: Boolean = false,
    val notice: String? = null,
    val owner: String = "",
    val repo: String = "",
)

/**
 * Offline-first README state: cached body renders immediately, a background
 * refresh revalidates with ETag, failures surface as notices — never blank screens.
 */
class ReadmeViewModel(
    owner: String,
    name: String,
    private val repository: ReadmeRepository,
) : ViewModel() {
    private val fullName = "$owner/$name"

    private val _state = MutableStateFlow(ReadmeUiState(owner = owner, repo = name))
    val state: StateFlow<ReadmeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observe(fullName).collectLatest { entity ->
                if (entity == null) {
                    refresh(force = false)
                } else {
                    val blocks = withContext(Dispatchers.Default) { parseMarkdown(entity.markdown) }
                    val current = _state.value
                    _state.value = current.copy(
                        status = ReadmeStatus.READY,
                        blocks = blocks,
                        stale = System.currentTimeMillis() - entity.fetchedAt > ReadmeRepository.TTL_MS,
                    )
                    // Revalidate stale copies in the background.
                    if (System.currentTimeMillis() - entity.fetchedAt > ReadmeRepository.TTL_MS) {
                        refresh(force = false)
                    }
                }
            }
        }
    }

    fun refresh(force: Boolean) {
        viewModelScope.launch {
            val parts = fullName.split('/')
            when (val result = repository.refresh(parts[0], parts[1], force)) {
                is Outcome.Ok -> Unit // Observer above publishes the new body.
                is Outcome.Err -> {
                    val hasBody = _state.value.blocks.isNotEmpty()
                    _state.value = _state.value.copy(
                        status = if (hasBody) ReadmeStatus.READY else {
                            if (result.failure.startsWith("No README")) ReadmeStatus.MISSING
                            else ReadmeStatus.ERROR
                        },
                        stale = hasBody,
                        notice = result.failure,
                    )
                }
            }
        }
    }

    fun dismissNotice() {
        _state.value = _state.value.copy(notice = null)
    }
}
