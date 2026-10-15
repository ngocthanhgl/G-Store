package dev.gstore.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gstore.common.Outcome
import dev.gstore.data.CatalogFilter
import dev.gstore.data.CatalogRepository
import dev.gstore.data.HomeSections
import dev.gstore.data.computeSections
import dev.gstore.data.sortedCatalog
import dev.gstore.datastore.GitHubIdentity
import dev.gstore.datastore.PreferencesRepository
import dev.gstore.datastore.TokenStore
import dev.gstore.model.HomeSection
import dev.gstore.model.RepoSignals
import dev.gstore.model.Repository
import dev.gstore.model.SectionConfig
import dev.gstore.model.TrendingWeights
import dev.gstore.network.ApiResponse
import dev.gstore.network.CatalogClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Catalog counts as stale after 24h — matches the scanner schedule. */
private const val STALE_AFTER_MILLIS = 24L * 60 * 60 * 1000

data class HomeUiState(
    val sections: HomeSections = HomeSections(),
    val configs: List<SectionConfig> = emptyList(),
    val favoriteIds: Set<Long> = emptySet(),
    val identity: GitHubIdentity = GitHubIdentity(),
    val favoriteRepos: List<Repository> = emptyList(),
    val catalog: List<RepoSignals> = emptyList(),
    val catalogFilter: CatalogFilter = CatalogFilter.TRENDING,
    val syncingCatalog: Boolean = false,
    val isEmpty: Boolean = true,
    val syncing: Boolean = false,
    val banner: String? = null,
    val discoverResults: List<Repository> = emptyList(),
    val discovering: Boolean = false,
)

class HomeViewModel(
    private val catalog: CatalogRepository,
    private val prefs: PreferencesRepository,
    private val catalogClient: CatalogClient,
    tokens: TokenStore,
    cachedSignals: StateFlow<List<RepoSignals>>,
) : ViewModel() {
    private val allRepos = MutableStateFlow<List<Repository>>(emptyList())
    private val syncing = MutableStateFlow(false)
    private val banner = MutableStateFlow<String?>(null)
    private val discoverResults = MutableStateFlow<List<Repository>>(emptyList())
    private val discovering = MutableStateFlow(false)
    private val catalogFilter = MutableStateFlow(CatalogFilter.TRENDING)
    private val syncingCatalog = MutableStateFlow(false)
    private var catalogAutoTried = false

    /** Favorites shelf: favorited repos, stars first, fully offline. */
    private val favoriteRepos = combine(
        catalog.observeFavoriteIds(),
        catalog.observeRepositories(),
    ) { favs, repos ->
        repos.filter { it.id in favs }.sortedByDescending { it.stars }
    }

    /** Four section inputs combined separately — keeps each combine arity small. */
    private val sectionInputs = combine(
        cachedSignals,
        catalog.observeFavoriteIds(),
        prefs.sectionConfigs,
        prefs.trendingWeights,
        tokens.identity,
    ) { list, favs, configs, weights, identity ->
        SectionInputs(list, favs, configs, weights, identity)
    }

    private data class SectionInputs(
        val signals: List<RepoSignals>,
        val favorites: Set<Long>,
        val configs: List<SectionConfig>,
        val weights: TrendingWeights,
        val identity: GitHubIdentity,
    )

    private data class RefreshState(
        val syncing: Boolean,
        val banner: String?,
        val filter: CatalogFilter,
        val syncingCatalog: Boolean,
    )

    private val refreshState = combine(
        syncing, banner, catalogFilter, syncingCatalog,
    ) { s, b, f, cs ->
        RefreshState(s, b, f, cs)
    }

    val state: StateFlow<HomeUiState> = combine(
        sectionInputs,
        favoriteRepos,
        refreshState,
        discoverResults,
        discovering,
    ) { inputs, favorites, refresh, discovered, isDiscovering ->
        HomeUiState(
            sections = computeSections(inputs.signals, inputs.favorites, inputs.weights),
            configs = inputs.configs,
            favoriteIds = inputs.favorites,
            identity = inputs.identity,
            favoriteRepos = favorites,
            catalog = sortedCatalog(inputs.signals, refresh.filter, inputs.weights),
            catalogFilter = refresh.filter,
            syncingCatalog = refresh.syncingCatalog,
            isEmpty = inputs.signals.isEmpty(),
            syncing = refresh.syncing,
            banner = refresh.banner,
            discoverResults = discovered,
            discovering = isDiscovering,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch {
            catalog.observeRepositories().collect { repos ->
                allRepos.value = repos
                // First open pulls the catalog when the DB is empty OR the
                // last import is older than the scanner schedule — no manual
                // button needed, offline just keeps showing cache.
                if (!catalogAutoTried) {
                    catalogAutoTried = true
                    val last = prefs.catalogImportedAt.first()
                    if (repos.isEmpty() || last == null ||
                        System.currentTimeMillis() - last > STALE_AFTER_MILLIS
                    ) {
                        syncCatalog(quiet = true)
                    }
                }
            }
        }
    }

    fun dismissBanner() {
        banner.value = null
    }

    fun toggleFavorite(repo: Repository) {
        viewModelScope.launch { catalog.toggleFavorite(repo) }
    }

    fun setSectionVisible(section: HomeSection, visible: Boolean) {
        // Categories are locked: always browsable, never hiding.
        if (section == HomeSection.CATEGORIES) return
        viewModelScope.launch { prefs.setSectionVisible(section, visible) }
    }

    fun moveSection(section: HomeSection, toRank: Int) {
        viewModelScope.launch { prefs.moveSection(section, toRank) }
    }

    fun setCatalogFilter(filter: CatalogFilter) {
        catalogFilter.value = filter
    }

    /**
     * Pull the daily scanner index and merge it into Room. Runs on first
     * open with an empty DB, on pull-to-refresh when older than 24h, and from
     * the empty-state button. ETag-kept: a 304 touches imported-at only.
     */
    fun syncCatalog(quiet: Boolean = false) {
        viewModelScope.launch {
            syncingCatalog.value = true
            val now = System.currentTimeMillis()
            try {
                when (val resp = catalogClient.fetch(prefs.catalogEtag.first())) {
                    is ApiResponse.Fresh -> {
                        val merged = catalog.importIndex(resp.data, now)
                        prefs.setCatalogState(resp.etag, now)
                        if (!quiet) banner.value = "Loaded $merged apps from the daily catalog"
                    }
                    ApiResponse.NotModified -> {
                        prefs.setCatalogState(prefs.catalogEtag.first(), now)
                        if (!quiet) banner.value = "Catalog is up to date"
                    }
                }
            } catch (e: Exception) {
                if (!quiet) banner.value = "Catalog pull failed: ${e.message}"
            }
            syncingCatalog.value = false
        }
    }

    /** Full refresh: catalog first when stale, then every cached repo. */
    fun refreshAll() = refreshAll(allRepos.value, withCatalog = true)

    /** Section-scoped refresh — only repos currently in the section. */
    fun refreshSection(repos: List<Repository>) = refreshAll(repos)

    private fun refreshAll(repos: List<Repository>, withCatalog: Boolean = false) {
        viewModelScope.launch {
            syncing.value = true
            if (withCatalog) {
                val last = prefs.catalogImportedAt.first()
                if (last == null || System.currentTimeMillis() - last > STALE_AFTER_MILLIS) {
                    syncCatalog(quiet = true)
                }
            }
            var ok = 0
            var failed = 0
            for (repo in repos) {
                when (catalog.sync(repo.fullName)) {
                    is Outcome.Ok -> ok++
                    is Outcome.Err -> failed++
                }
            }
            syncing.value = false
            banner.value = when {
                failed > 0 -> "$ok synced, $failed failed — retry from the section"
                else -> null
            }
        }
    }

    /** Explicit discovery mechanism: user adds owner/name (PRODUCT_SPEC §4). */
    fun addRepository(rawInput: String) {
        val input = rawInput.trim().removePrefix("https://github.com/").removeSuffix("/")
        viewModelScope.launch {
            prefs.addManualRepo(input)
            when (val result = catalog.sync(input)) {
                is Outcome.Ok -> banner.value = "Added $input"
                is Outcome.Err -> banner.value = "Could not add $input: ${result.failure}"
            }
        }
    }

    /** Trending seeds: one user-triggered search, results added only on tap. */
    fun discover() {
        viewModelScope.launch {
            discovering.value = true
            when (val result = catalog.search("topic:android")) {
                is Outcome.Ok -> discoverResults.value = result.value
                is Outcome.Err -> banner.value = result.failure
            }
            discovering.value = false
        }
    }
}
