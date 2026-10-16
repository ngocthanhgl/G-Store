package dev.gstore.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gstore.common.Outcome
import dev.gstore.data.CatalogRepository
import dev.gstore.data.searchFilter
import dev.gstore.model.Repository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class SearchUiState(
    val query: String = "",
    val results: List<Repository> = emptyList(),
    val favoriteIds: Set<Long> = emptySet(),
    val searching: Boolean = false,
    val banner: String? = null,
)

@OptIn(FlowPreview::class)
class SearchViewModel(private val catalog: CatalogRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val searching = MutableStateFlow(false)
    private val banner = MutableStateFlow<String?>(null)

    private val localMatches = combine(
        query.debounce(300),
        catalog.observeRepositories(),
    ) { q, repos -> searchFilter(repos, q).sortedByDescending { it.stars } }

    val state: StateFlow<SearchUiState> = combine(
        query,
        localMatches,
        catalog.observeFavoriteIds(),
        searching,
        banner,
    ) { q, matches, favs, net, message ->
        SearchUiState(q, matches, favs, net, message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun setQuery(q: String) {
        query.value = q
    }

    fun dismissBanner() {
        banner.value = null
    }

    fun toggleFavorite(repo: Repository) {
        viewModelScope.launch { catalog.toggleFavorite(repo) }
    }

    fun addRepository(fullName: String) {
        viewModelScope.launch {
            when (val result = catalog.sync(fullName.trim())) {
                is Outcome.Ok -> banner.value = "Added $fullName"
                is Outcome.Err -> banner.value = "Could not add $fullName: ${result.failure}"
            }
        }
    }

    /** Network search — user-triggered only, results are NOT persisted. */
    fun searchGitHub() {
        val q = query.value.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            searching.value = true
            when (val result = catalog.search(q)) {
                is Outcome.Ok -> {
                    netResults.value = result.value
                    if (result.value.isEmpty()) banner.value = "No GitHub results for \"$q\""
                }
                is Outcome.Err -> banner.value = result.failure
            }
            searching.value = false
        }
    }

    private val netResults = MutableStateFlow<List<Repository>>(emptyList())
    val networkResults: StateFlow<List<Repository>> = netResults
}
