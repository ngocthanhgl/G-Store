package dev.gstore.home

import dev.gstore.ui.AuroraSectionHeader
import dev.gstore.ui.safeTop
import dev.gstore.ui.CountPill
import dev.gstore.ui.EmptyContent
import dev.gstore.ui.GStoreSpace
import dev.gstore.ui.Fluent

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gstore.data.AppCategory
import dev.gstore.data.CatalogFilter
import dev.gstore.data.categoriesPresent
import dev.gstore.data.categoryOf
import dev.gstore.datastore.GitHubIdentity
import dev.gstore.model.RepoSignals
import dev.gstore.model.Repository
import dev.gstore.ui.AdaptiveCard
import dev.gstore.ui.ApkBadge
import dev.gstore.ui.AppIcon
import dev.gstore.ui.RepoCard
import dev.gstore.ui.RepoRows
import dev.gstore.ui.ShimmerRows
import dev.gstore.designsystem.LocalCardRadius
import dev.gstore.designsystem.panelShape

private const val COLLAPSED_COUNT = 5

/**
 * Home is the scanner catalog and nothing else: the filter chip rail on
 * top, tapping a chip expands its list. All curated sections (Trending,
 * Famous, Top charts, categories, ...) were removed â€” with the slim
 * launch index they rendered as permanent empty shells.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onOpenRepo: (Repository) -> Unit,
    onOpenDiscoveryRepo: (Repository) -> Unit,
    onOpenCategory: (AppCategory) -> Unit,
    onOpenLogin: () -> Unit,
    onLogout: () -> Unit,
    snackbar: SnackbarHostState,
    grid: Boolean = false,
    compact: Boolean = false,
    cardRadiusDp: Int = LocalCardRadius.current,
    reduceMotion: Boolean = false,
) {
    val state by vm.state.collectAsState()
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showDiscover by rememberSaveable { mutableStateOf(false) }
    // Scroll + panel state survive tab switches (saved with the back stack).
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    LaunchedEffect(state.banner) {
        state.banner?.let {
            snackbar.showSnackbar(it)
            vm.dismissBanner()
        }
    }

    // Aurora-style shelves: one horizontal rail of the top 5 per category,
    // only for categories present in the catalog. Computed once per catalog
    // emission in composable scope (never inside the LazyList DSL), not per
    // recomposition or per row.
    val shelves = remember(state.catalog) {
        categoriesPresent(state.catalog).map { (category, count) ->
            Triple(
                category,
                count,
                state.catalog
                    .filter { categoryOf(it.repo.topics) == category }
                    .sortedByDescending { it.repo.stars }
                    .take(5),
            )
        }
    }
    PullToRefreshBox(
        isRefreshing = state.syncing,
        onRefresh = vm::refreshAll,
        modifier = Modifier.fillMaxSize().safeTop(),
    ) {
        if (state.isEmpty && !state.syncing && !state.signalsLoading) {
            EmptyCatalog(
                onAdd = { showAdd = true },
                onDiscover = { showDiscover = true },
                discovering = state.discovering,
                onSyncCatalog = { vm.syncCatalog() },
                syncingCatalog = state.syncingCatalog,
            )
        } else if (state.isEmpty) {
            // Rows exist but signals are still assembling (or a sync runs):
            // skeleton placeholders, never a blank screen or a false empty.
            Column(modifier = Modifier.fillMaxSize()) {
                HomeHeader(
                    identity = state.identity,
                    count = state.catalog.size,
                    syncing = state.syncingCatalog,
                    onRefresh = { vm.syncCatalog() },
                    onAdd = { showAdd = true },
                    onOpenLogin = onOpenLogin,
                    onLogout = onLogout,
                )
                ShimmerRows(count = 8, reduceMotion = reduceMotion)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = GStoreSpace.Block),
                ) {
                item {
                    HomeHeader(
                        identity = state.identity,
                        count = state.catalog.size,
                        syncing = state.syncingCatalog,
                        onRefresh = { vm.syncCatalog() },
                        onAdd = { showAdd = true },
                        onOpenLogin = onOpenLogin,
                        onLogout = onLogout,
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
                        onOpen = onOpenRepo,
                        onToggleFavorite = vm::toggleFavorite,
                    )
                }
                items(shelves, key = { it.first }) { (category, count, top) ->
                    CategoryRail(
                        category = category,
                        count = count,
                        top = top,
                        favorites = state.favoriteIds,
                        cardRadiusDp = cardRadiusDp,
                        onOpenCategory = onOpenCategory,
                        onOpen = onOpenRepo,
                        onToggleFavorite = vm::toggleFavorite,
                    )
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
            onOpen = onOpenDiscoveryRepo,
            onToggleFavorite = vm::toggleFavorite,
            onDismiss = { showDiscover = false },
        )
    }
}

/**
 * Accepts owner/name or a full GitHub link:
 * https://github.com/owner/repo, with optional .git or sub-path suffix.
 * Returns the normalized owner/name, or null when unparseable.
 */
fun parseRepoLink(input: String): String? {
    val t = input.trim().trimEnd('/')
    if (t.isEmpty()) return null
    val m = Regex("""github\.com/([^/\s]+)/([^/\s?#]+)""").find(t)
    if (m != null) {
        val owner = m.groupValues[1]
        val name = m.groupValues[2].removeSuffix(".git")
        if (owner.isNotEmpty() && name.isNotEmpty()) return "$owner/$name"
        return null
    }
    if ("/" in t && " " !in t) return t.trim('/')
    return null
}

@Composable
private fun HomeHeader(
    identity: GitHubIdentity,
    count: Int,
    syncing: Boolean,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onOpenLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    var showProfile by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GStoreSpace.Gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GStoreSpace.Small),
    ) {
        Text(
            "Home",
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        CountPill(count)
        // Refresh, add and profile share one 8dp gap and one 48dp target, and
        // all three sit inside the 16dp gutter: no per-button optical offset,
        // which used to push the trailing target past the gutter to the bezel.
        if (syncing) {
            Box(modifier = Modifier.size(GStoreSpace.TouchTarget), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(GStoreSpace.Icon))
            }
        } else IconButton(onClick = onRefresh) {
            Icon(Fluent.ArrowSync, contentDescription = "Refresh app catalog")
        }
        IconButton(onClick = onAdd) {
            Icon(Fluent.Add, contentDescription = "Add repository")
        }
        Box {
            IconButton(onClick = { showProfile = true }) {
                if (identity.login.isBlank()) {
                    Icon(Fluent.Person, contentDescription = "Open account and settings")
                } else {
                    AppIcon(
                        avatarUrl = identity.avatarUrl,
                        owner = identity.login,
                        size = 28.dp,
                        circular = true,
                    )
                }
            }
            DropdownMenu(
                expanded = showProfile,
                onDismissRequest = { showProfile = false },
                shape = panelShape(),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                if (identity.login.isNotBlank()) {
                    DropdownMenuItem(
                        text = { Text("${identity.login} · refresh every 10s") },
                        enabled = false,
                        onClick = {},
                    )
                }
                DropdownMenuItem(
                    text = { Text("Settings") },
                    onClick = {
                        showProfile = false
                        onOpenLogin()
                    },
                )
                if (identity.login.isNotBlank()) {
                    DropdownMenuItem(
                        text = { Text("Log out") },
                        onClick = {
                            showProfile = false
                            onLogout()
                        },
                    )
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
            Fluent.Add,
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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Button(onClick = onAdd) { Text("Add repository") }
            OutlinedButton(onClick = onDiscover, enabled = !discovering) {
                Text(if (discovering) "Searching..." else "Discover")
            }
        }
        if (syncingCatalog) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
        }
        TextButton(onClick = onSyncCatalog, enabled = !syncingCatalog) {
            Text(if (syncingCatalog) "Loading catalog..." else "Or load the daily app catalog")
        }
    }
}

/**
 * The scanner-catalog list: every app GitHub-wide with an APK release,
 * ordered by the selected filter.
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
    onOpen: (Repository) -> Unit,
    onToggleFavorite: (Repository) -> Unit,
) {
    // The chips always lead to a visible list. An earlier version gated the
    // results behind tapping a chip, so the screen opened with nothing but a
    // row of filters — the home of an app store should show apps.
    val current = filter
    val shown = items.take(12)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GStoreSpace.Gutter)
            .animateContentSize(
                animationSpec = if (reduceMotion) {
                    androidx.compose.animation.core.snap()
                } else {
                    androidx.compose.animation.core.spring()
                },
            ),
        verticalArrangement = Arrangement.spacedBy(GStoreSpace.Block),
    ) {
        // No section header: the count lives in the Home title above and the
        // refresh action moved to the header row, so the filter chips lead.
        Row(
            horizontalArrangement = Arrangement.spacedBy(GStoreSpace.Small),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            CatalogFilter.entries.forEach { option ->
                val open = current == option
                FilterChip(
                    selected = open,
                    onClick = { onFilter(option) },
                    label = { Text(filterLabel(option), maxLines = 1) },
                    leadingIcon = if (open) {
                        {
                            Icon(
                                Fluent.Checkmark,
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
                    shape = CircleShape,
                    // M3 chips are 32dp tall; every other control in the app
                    // answers to a 48dp target, and a chip row is a primary
                    // navigation surface, so it gets the same floor.
                    modifier = Modifier.heightIn(min = GStoreSpace.TouchTarget),
                )
            }
        }
        if (items.isEmpty() && syncing) {
            // Genuine load in flight: Aurora sweep placeholders, never blank.
            ShimmerRows(count = 5, reduceMotion = reduceMotion)
        } else if (items.isEmpty()) {
            Text(
                "No catalog yet - pull to refresh.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "${filterLabel(current)} - ${shown.size} of ${items.size} apps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RepoRows(
                grid = grid,
                count = shown.size,
                keyOf = { "catalog-${shown[it].repo.id}" },
                card = { index ->
                    val signals = shown[index]
                    AdaptiveCard(
                        repo = signals.repo,
                        isFavorite = signals.repo.id in favorites,
                        apkBadge = apkBadgeOf(signals),
                        grid = grid,
                        onOpen = { onOpen(signals.repo) },
                        onToggleFavorite = { onToggleFavorite(signals.repo) },
                        cardRadiusDp = cardRadiusDp,
                        compact = compact,
                    )
                },
            )
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
    var text by rememberSaveable { mutableStateOf("") }
    val parsed = parseRepoLink(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add repository") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Enter owner/name or paste a GitHub link. Only this repository is fetched.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("owner/name or link") },
                    singleLine = true,
                    shape = CircleShape,
                )
                if (text.isNotBlank() && parsed != null && parsed != text.trim()) {
                    Text(
                        "Adds $parsed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(parsed ?: text) },
                enabled = parsed != null,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Cancel") }
        },
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
                // Up to 10 cards must scroll inside the dialog on compact screens.
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "One search for topic:android. Tapping Add fetches that repository.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onSearch, enabled = !discovering) {
                    Text(if (discovering) "Searching..." else "Search GitHub")
                }
                if (!discovering && results.isEmpty()) {
                    Text(
                        "No matches â€” try a different query.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
