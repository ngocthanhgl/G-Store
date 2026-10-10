package dev.gstore.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gstore.data.CatalogFilter
import dev.gstore.model.HomeSection
import dev.gstore.model.RepoSignals
import dev.gstore.model.Repository
import dev.gstore.model.SectionConfig
import dev.gstore.ui.ApkBadge
import dev.gstore.ui.RepoCard
import dev.gstore.ui.RepoRows

private const val COLLAPSED_COUNT = 5

private fun titleOf(section: HomeSection): String = when (section) {
    HomeSection.NEW -> "New apps"
    HomeSection.TRENDING -> "Trending"
    HomeSection.FAMOUS -> "Famous"
    HomeSection.UPDATED -> "Recently updated"
    HomeSection.RECOMMENDED -> "Recommended"
    HomeSection.BROWSING -> "Continue browsing"
}

private fun emptyOf(section: HomeSection): String = when (section) {
    HomeSection.NEW -> "No apps first released in the last 30 days yet."
    HomeSection.TRENDING -> "No recent release activity yet — sync a repository to begin."
    HomeSection.FAMOUS -> "No established projects yet (1,000+ stars, 3+ APK releases, 1+ year)."
    HomeSection.UPDATED -> "Nothing released in the last 30 days."
    HomeSection.RECOMMENDED -> "Favorite the famous projects you like; the rest appear here."
    HomeSection.BROWSING -> "Open a repository and it will wait for you here."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onOpenRepo: (Repository) -> Unit,
    snackbar: SnackbarHostState,
    grid: Boolean = false,
    compact: Boolean = false,
    cardRadiusDp: Int = 12,
    reduceMotion: Boolean = false,
) {
    val state by vm.state.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var showCustomize by remember { mutableStateOf(false) }
    var showDiscover by remember { mutableStateOf(false) }

    LaunchedEffect(state.banner) {
        state.banner?.let {
            snackbar.showSnackbar(it)
            vm.dismissBanner()
        }
    }

    PullToRefreshBox(
        isRefreshing = state.syncing,
        onRefresh = vm::refreshAll,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (state.isEmpty && !state.syncing) {
            EmptyCatalog(
                onAdd = { showAdd = true },
                onDiscover = { showDiscover = true },
                discovering = state.discovering,
                onSyncCatalog = { vm.syncCatalog() },
                syncingCatalog = state.syncingCatalog,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    HomeHeader(
                        onAdd = { showAdd = true },
                        onCustomize = { showCustomize = true },
                    )
                }
                item(key = "catalog") {
                    CatalogBlock(
                        items = state.catalog,
                        filter = state.catalogFilter,
                        onFilter = vm::setCatalogFilter,
                        favorites = state.favoriteIds,
                        syncing = state.syncingCatalog,
                        grid = grid,
                        compact = compact,
                        cardRadiusDp = cardRadiusDp,
                        reduceMotion = reduceMotion,
                        onRefresh = { vm.syncCatalog() },
                        onOpen = onOpenRepo,
                        onToggleFavorite = vm::toggleFavorite,
                    )
                }
                for (config in state.configs.filter { it.visible }) {                    item(key = config.section.name) {
                        when (config.section) {
                            HomeSection.BROWSING -> BrowsingBlock(
                                repos = state.browsing,
                                favorites = state.favoriteIds,
                                syncing = state.syncing,
                                grid = grid,
                                compact = compact,
                                cardRadiusDp = cardRadiusDp,
                                reduceMotion = reduceMotion,
                                onRefresh = { vm.refreshSection(state.browsing) },
                                onHide = { vm.setSectionVisible(config.section, false) },
                                onOpen = onOpenRepo,
                                onToggleFavorite = vm::toggleFavorite,
                            )
                            else -> SectionBlock(
                                config = config,
                                items = itemsOf(config.section, state),
                                favorites = state.favoriteIds,
                                syncing = state.syncing,
                                grid = grid,
                                compact = compact,
                                cardRadiusDp = cardRadiusDp,
                                reduceMotion = reduceMotion,
                                onRefresh = {
                                    vm.refreshSection(
                                        itemsOf(config.section, state).map { it.repo },
                                    )
                                },
                                onHide = { vm.setSectionVisible(config.section, false) },
                                onOpen = onOpenRepo,
                                onToggleFavorite = vm::toggleFavorite,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddRepoDialog(
            onDismiss = { showAdd = false },
            onConfirm = {
                vm.addRepository(it)
                showAdd = false
            },
        )
    }
    if (showDiscover) {
        DiscoverDialog(
            results = state.discoverResults,
            discovering = state.discovering,
            favorites = state.favoriteIds,
            onSearch = vm::discover,
            onAdd = vm::addRepository,
            onOpen = onOpenRepo,
            onToggleFavorite = vm::toggleFavorite,
            onDismiss = { showDiscover = false },
        )
    }
    if (showCustomize) {
        CustomizeDialog(
            configs = state.configs,
            onToggle = vm::setSectionVisible,
            onMove = vm::moveSection,
            onDismiss = { showCustomize = false },
        )
    }
}

private fun itemsOf(section: HomeSection, state: HomeUiState): List<RepoSignals> =
    when (section) {
        HomeSection.NEW -> state.sections.new
        HomeSection.TRENDING -> state.sections.trending
        HomeSection.FAMOUS -> state.sections.famous
        HomeSection.UPDATED -> state.sections.updated
        HomeSection.RECOMMENDED -> state.sections.recommended
        HomeSection.BROWSING -> emptyList() // rendered by BrowsingBlock
    }

private fun apkBadgeOf(signals: RepoSignals): ApkBadge =
    if (signals.eligibleReleaseCount > 0) ApkBadge.HAS_APK else ApkBadge.NO_APK

@Composable
private fun HomeHeader(onAdd: () -> Unit, onCustomize: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Home",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = "Add repository")
        }
        IconButton(onClick = onCustomize) {
            Icon(Icons.Filled.Settings, contentDescription = "Customize sections")
        }
    }
}

/**
 * Shared section header — DESIGN.md "Tonal Shelf": one titleLarge heading
 * with a tonal count pill, never an eyebrow above it.
 */
@Composable
private fun SectionHeader(
    title: String,
    countText: String?,
    subline: String? = null,
    syncing: Boolean,
    onRefresh: () -> Unit,
    refreshDescription: String,
    onHide: (() -> Unit)?,
    hideDescription: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
            if (countText != null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            if (syncing) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = refreshDescription)
                }
            }
            if (onHide != null) {
                IconButton(onClick = onHide) {
                    Icon(Icons.Filled.Close, contentDescription = hideDescription)
                }
            }
        }
        if (subline != null) {
            Text(
                text = subline,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionBlock(
    config: SectionConfig,
    items: List<RepoSignals>,
    favorites: Set<Long>,
    syncing: Boolean,
    grid: Boolean,
    compact: Boolean,
    cardRadiusDp: Int,
    reduceMotion: Boolean,
    onRefresh: () -> Unit,
    onHide: () -> Unit,
    onOpen: (Repository) -> Unit,
    onToggleFavorite: (Repository) -> Unit,
) {
    val section = config.section
    var expanded by remember(section) { mutableStateOf(false) }
    val shown = if (expanded) items else items.take(COLLAPSED_COUNT)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            // The show-more expand animates — unless the user asked for reduced
            // motion, in which case the size change snaps instantly (M7).
            .animateContentSize(
                animationSpec = if (reduceMotion) {
                    androidx.compose.animation.core.snap()
                } else {
                    androidx.compose.animation.core.spring()
                },
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(
            title = titleOf(section),
            countText = "${items.size}",
            syncing = syncing,
            onRefresh = onRefresh,
            refreshDescription = "Refresh ${titleOf(section)}",
            onHide = onHide,
            hideDescription = "Hide ${titleOf(section)}",
        )
        if (items.isEmpty()) {
            Text(
                emptyOf(section),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            RepoRows(
                grid = grid,
                count = shown.size,
                keyOf = { "repo-${shown[it].repo.id}" },
                card = { index ->
                    val signals = shown[index]
                    RepoCard(
                        repo = signals.repo,
                        isFavorite = signals.repo.id in favorites,
                        apkBadge = apkBadgeOf(signals),
                        onOpen = { onOpen(signals.repo) },
                        onToggleFavorite = { onToggleFavorite(signals.repo) },
                        cardRadiusDp = cardRadiusDp,
                        compact = compact,
                    )
                },
            )
            if (items.size > COLLAPSED_COUNT) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Show less" else "Show all ${items.size}")
                }
            }
        }
    }
}

@Composable
private fun BrowsingBlock(
    repos: List<Repository>,
    favorites: Set<Long>,
    syncing: Boolean,
    grid: Boolean,
    compact: Boolean,
    cardRadiusDp: Int,
    reduceMotion: Boolean,
    onRefresh: () -> Unit,
    onHide: () -> Unit,
    onOpen: (Repository) -> Unit,
    onToggleFavorite: (Repository) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val shown = if (expanded) repos else repos.take(COLLAPSED_COUNT)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .animateContentSize(
                animationSpec = if (reduceMotion) {
                    androidx.compose.animation.core.snap()
                } else {
                    androidx.compose.animation.core.spring()
                },
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(
            title = "Continue browsing",
            countText = null,
            syncing = syncing,
            onRefresh = onRefresh,
            refreshDescription = "Refresh Continue browsing",
            onHide = onHide,
            hideDescription = "Hide Continue browsing",
        )
        if (repos.isEmpty()) {
            Text(
                emptyOf(HomeSection.BROWSING),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            RepoRows(
                grid = grid,
                count = shown.size,
                keyOf = { "browse-${shown[it].id}" },
                card = { index ->
                    val repo = shown[index]
                    RepoCard(
                        repo = repo,
                        isFavorite = repo.id in favorites,
                        onOpen = { onOpen(repo) },
                        onToggleFavorite = { onToggleFavorite(repo) },
                        cardRadiusDp = cardRadiusDp,
                        compact = compact,
                    )
                },
            )
            if (repos.size > COLLAPSED_COUNT) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Show less" else "Show all ${repos.size}")
                }
            }
        }
    }
}

@Composable
private fun EmptyCatalog(
    onAdd: () -> Unit,
    onDiscover: () -> Unit,
    discovering: Boolean,
    onSyncCatalog: () -> Unit,
    syncingCatalog: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text("No repositories yet", style = MaterialTheme.typography.titleLarge)
        Text(
            "Add a GitHub repository by owner and name, or discover Android projects. " +
                "Nothing is fetched until you ask.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Button(onClick = onAdd) { Text("Add repository") }
            OutlinedButton(onClick = onDiscover, enabled = !discovering) {                Text(if (discovering) "Searching…" else "Discover")
            }
        }
        TextButton(onClick = onSyncCatalog, enabled = !syncingCatalog) {
            Text(if (syncingCatalog) "Loading catalog…" else "Or load the daily app catalog")
        }
    }
}

/**
 * The scanner-catalog list: every app GitHub-wide with an APK release,
 * ordered by the selected filter. Sections below stay personalized views
 * over the same data (PRODUCT_SPEC §3).
 */
@Composable
private fun CatalogBlock(
    items: List<RepoSignals>,
    filter: CatalogFilter,
    onFilter: (CatalogFilter) -> Unit,
    favorites: Set<Long>,
    syncing: Boolean,
    grid: Boolean,
    compact: Boolean,
    cardRadiusDp: Int,
    reduceMotion: Boolean,
    onRefresh: () -> Unit,
    onOpen: (Repository) -> Unit,
    onToggleFavorite: (Repository) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val shown = if (expanded) items else items.take(COLLAPSED_COUNT)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .animateContentSize(
                animationSpec = if (reduceMotion) {
                    androidx.compose.animation.core.snap()
                } else {
                    androidx.compose.animation.core.spring()
                },
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(
            title = "All apps",
            countText = "${items.size}",
            subline = "Daily catalog · ${filterLabel(filter).lowercase()} first",
            syncing = syncing,
            onRefresh = onRefresh,
            refreshDescription = "Refresh app catalog",
            onHide = null,
            hideDescription = "",
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            CatalogFilter.entries.forEach { option ->
                val selected = option == filter
                FilterChip(
                    selected = selected,
                    onClick = { onFilter(option) },
                    label = { Text(filterLabel(option)) },
                    leadingIcon = if (selected) {
                        {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }
        if (items.isEmpty()) {
            Text(
                if (syncing) "Loading the daily catalog…" else "No catalog yet — pull to refresh.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            RepoRows(
                grid = grid,
                count = shown.size,
                keyOf = { "catalog-${shown[it].repo.id}" },
                card = { index ->
                    val signals = shown[index]
                    RepoCard(
                        repo = signals.repo,
                        isFavorite = signals.repo.id in favorites,
                        apkBadge = apkBadgeOf(signals),
                        onOpen = { onOpen(signals.repo) },
                        onToggleFavorite = { onToggleFavorite(signals.repo) },
                        cardRadiusDp = cardRadiusDp,
                        compact = compact,
                    )
                },
            )
            if (items.size > COLLAPSED_COUNT) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Show less" else "Show all ${items.size}")
                }
            }
        }
    }
}

private fun filterLabel(filter: CatalogFilter): String = when (filter) {
    CatalogFilter.TRENDING -> "Trending"
    CatalogFilter.STARS -> "Top stars"
    CatalogFilter.NEW -> "New"
    CatalogFilter.UPDATED -> "Recently updated"
}

@Composable
private fun AddRepoDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add repository") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Enter owner/name, e.g. owner/app. Only this repository is fetched.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("owner/name") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.contains("/")) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DiscoverDialog(
    results: List<Repository>,
    discovering: Boolean,
    favorites: Set<Long>,
    onSearch: () -> Unit,
    onAdd: (String) -> Unit,
    onOpen: (Repository) -> Unit,
    onToggleFavorite: (Repository) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Discover Android projects") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                // Up to 10 cards must scroll inside the dialog on compact screens.
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "One search for topic:android. Tapping Add fetches that repository.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onSearch, enabled = !discovering) {
                    Text(if (discovering) "Searching…" else "Search GitHub")
                }
                results.take(10).forEach { repo ->
                    RepoCard(
                        repo = repo,
                        isFavorite = repo.id in favorites,
                        onOpen = { onOpen(repo) },
                        onToggleFavorite = { onToggleFavorite(repo) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = null,
    )
}

@Composable
private fun CustomizeDialog(
    configs: List<SectionConfig>,
    onToggle: (HomeSection, Boolean) -> Unit,
    onMove: (HomeSection, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sections") },
        text = {
            Column {
                configs.forEachIndexed { index, config ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = config.visible,
                            onCheckedChange = { onToggle(config.section, it) },
                        )
                        Text(
                            titleOf(config.section),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { onMove(config.section, index - 1) },
                            enabled = index > 0,
                        ) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                        }
                        IconButton(
                            onClick = { onMove(config.section, index + 1) },
                            enabled = index < configs.size - 1,
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = null,
    )
}
