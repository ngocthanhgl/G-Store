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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SortOrder { STARS, UPDATED }

@Immutable
data class SearchUiState(
    val query: String = "",
    val results: List<Repository> = emptyList(),
    val favoriteIds: Set<Long> = emptySet(),
    val apkOnlyIds: Set<Long> = emptySet(),
    val sort: SortOrder = SortOrder.STARS,
    val apkOnly: Boolean = false,
    val searching: Boolean = false,
    val banner: String? = null,
)

@OptIn(FlowPreview::class)
class SearchViewModel(private val catalog: CatalogRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(SortOrder.STARS)
    private val apkOnly = MutableStateFlow(false)
    private val searching = MutableStateFlow(false)
    private val banner = MutableStateFlow<String?>(null)

    private val localMatches = combine(
        query.debounce(300),
        catalog.observeRepositories(),
    ) { q, repos -> searchFilter(repos, q) }

    /** APK filter needs signal lookups — computed off the render path, capped. */
    private val apkIds = MutableStateFlow<Set<Long>>(emptySet())

    private data class SearchList(
        val results: List<Repository>,
        val apkIds: Set<Long>,
        val sort: SortOrder,
        val apkOnly: Boolean,
    )

    private val listInputs = combine(
        localMatches,
        apkIds,
        sort,
        apkOnly,
    ) { matches, apk, s, only ->
        val filtered = if (only) matches.filter { it.id in apk } else matches
        val sorted = when (s) {
            SortOrder.STARS -> filtered.sortedByDescending { it.stars }
            SortOrder.UPDATED -> filtered.sortedByDescending { it.updatedAt ?: "" }
        }
        SearchList(sorted, apk, s, only)
    }

    val state: StateFlow<SearchUiState> = combine(
        query,
        listInputs,
        catalog.observeFavoriteIds(),
        searching,
        banner,
    ) { q, list, favs, net, message ->
        SearchUiState(q, list.results, favs, list.apkIds, list.sort, list.apkOnly, net, message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    init {
        viewModelScope.launch {
            combine(localMatches, apkOnly) { matches, only -> matches to only }
                .collect { (matches, only) ->
                    if (only) {
                        // DB fan-out off the collector: 4 queries per repo.
                        val ids = withContext(Dispatchers.Default) {
                            matches.take(50)
                                .mapNotNull { repo ->
                                    if (catalog.signals(repo).eligibleReleaseCount > 0) repo.id
                                    else null
                                }.toSet()
                        }
                        apkIds.value = ids
                    } else {
                        apkIds.value = emptySet()
                    }
                }
        }
    }

    fun setQuery(q: String) {
        query.value = q
    }

    fun setSort(s: SortOrder) {
        sort.value = s
    }

    fun setApkOnly(only: Boolean) {
        apkOnly.value = only
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
