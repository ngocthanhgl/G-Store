package dev.gstore.search

import dev.gstore.ui.Fluent
import dev.gstore.ui.safeTop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gstore.model.Repository
import dev.gstore.ui.AdaptiveCard
import dev.gstore.ui.ApkBadge
import dev.gstore.ui.AppIcon
import dev.gstore.ui.GStoreSpace
import dev.gstore.designsystem.LocalCardRadius

@Composable
fun SearchScreen(
    vm: SearchViewModel,
    onOpenRepo: (Repository) -> Unit,
    snackbar: SnackbarHostState,
    grid: Boolean = false,
    compact: Boolean = false,
    cardRadiusDp: Int = LocalCardRadius.current,
) {
    val state by vm.state.collectAsState()
    val netResults by vm.networkResults.collectAsState()
    val focus = LocalFocusManager.current

    LaunchedEffect(state.banner) {
        state.banner?.let {
            snackbar.showSnackbar(it)
            vm.dismissBanner()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = GStoreSpace.Gutter)
            .safeTop()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = GStoreSpace.TouchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Search",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            label = {
                Text(
                    "Name, description, or owner",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            singleLine = true,
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    focus.clearFocus()
                    vm.searchGitHub()
                },
            ),
            trailingIcon = {
                IconButton(onClick = vm::searchGitHub) {
                    Icon(Fluent.Search, contentDescription = "Search GitHub")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = GStoreSpace.TouchTarget)
                .padding(vertical = GStoreSpace.Block),
        )
        val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
        val recentQueries by vm.recentSearches.collectAsState()
        // Chunk once per result set, not on every recomposition.
        val rows = remember(state.results) { state.results.chunked(2) }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = GStoreSpace.Block),
            verticalArrangement = Arrangement.spacedBy(GStoreSpace.Small),
        ) {
            if (state.query.isBlank()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(GStoreSpace.Small)) {
                        Text(
                            "Searches your synced catalog. Use the magnifier for GitHub-wide search — " +
                                "results are never saved without your tap.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (recentQueries.isNotEmpty()) {
                            Text(
                                "Recent searches",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            recentQueries.forEach { recent ->
                                Text(
                                    text = recent,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { vm.searchGitHub(recent) },
                                )
                            }
                        }
                    }
                }
            } else if (state.results.isEmpty()) {
                item {
                    Text(
                        "No cached matches. Try GitHub search, or add owner/name from Home.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!grid) {
                items(state.results, key = { it.id }) { repo ->
                    AdaptiveCard(
                        repo = repo,
                        isFavorite = repo.id in state.favoriteIds,
                        apkBadge = ApkBadge.UNKNOWN,
                        grid = false,
                        onOpen = { onOpenRepo(repo) },
                        onToggleFavorite = { vm.toggleFavorite(repo) },
                        cardRadiusDp = cardRadiusDp,
                        compact = compact,
                    )
                }
            } else {
                items(rows, key = { it.first().id }) { row ->
                    Column(verticalArrangement = Arrangement.spacedBy(GStoreSpace.Gutter)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(GStoreSpace.Gutter),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            row.forEach { repo ->
                                AdaptiveCard(
                                    repo = repo,
                                    isFavorite = repo.id in state.favoriteIds,
                                    apkBadge = ApkBadge.UNKNOWN,
                                    grid = true,
                                    onOpen = { onOpenRepo(repo) },
                                    onToggleFavorite = { vm.toggleFavorite(repo) },
                                    modifier = Modifier.weight(1f),
                                    cardRadiusDp = cardRadiusDp,
                                    compact = compact,
                                )
                            }
                            if (row.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            if (state.searching) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GStoreSpace.Small),
                        modifier = Modifier.padding(vertical = GStoreSpace.Small),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(GStoreSpace.TouchTarget),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                        Text(
                            "Searching GitHub...",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            if (netResults.isNotEmpty()) {
                item {
                    Text(
                        "GitHub results (not saved)",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = GStoreSpace.BeforeHeader),
                    )
                }
                items(netResults, key = { "net-${it.id}" }) { repo ->
                    NetworkRow(
                        repo = repo,
                        onAdd = { vm.addRepository(repo.fullName) },
                        onOpen = { onOpenRepo(repo) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkRow(repo: Repository, onAdd: () -> Unit, onOpen: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GStoreSpace.Medium),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 4.dp),
    ) {
        AppIcon(
            avatarUrl = repo.avatarUrl,
            owner = repo.owner,
            size = GStoreSpace.Tile,
        )
        Column(Modifier.weight(1f)) {
            Text(
                repo.fullName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                repo.description?.takeIf { it.isNotBlank() } ?: "by ${repo.owner}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${repo.stars} stars",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onAdd) {
            Icon(Fluent.Add, contentDescription = "Add ${repo.fullName}")
        }
    }
}
