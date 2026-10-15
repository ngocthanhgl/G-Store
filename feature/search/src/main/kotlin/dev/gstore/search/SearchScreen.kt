package dev.gstore.search
import dev.gstore.ui.Fluent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.gstore.model.Repository
import dev.gstore.ui.AdaptiveCard
import dev.gstore.ui.ApkBadge

@Composable
fun SearchScreen(
    vm: SearchViewModel,
    onOpenRepo: (Repository) -> Unit,
    snackbar: SnackbarHostState,
    grid: Boolean = false,
    compact: Boolean = false,
    cardRadiusDp: Int = 12,
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

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).statusBarsPadding().imePadding()) {
        Text(
            "Search",
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            label = { Text("Name, description, or owner") },
            singleLine = true,
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
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.sort == SortOrder.STARS,
                onClick = { vm.setSort(SortOrder.STARS) },
                label = { Text("Stars") },
            )
            FilterChip(
                selected = state.sort == SortOrder.UPDATED,
                onClick = { vm.setSort(SortOrder.UPDATED) },
                label = { Text("Recently updated") },
            )
            FilterChip(
                selected = state.apkOnly,
                onClick = { vm.setApkOnly(!state.apkOnly) },
                label = { Text("Has APK") },
            )
        }
        val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            if (state.query.isBlank()) {
                item {
                    Text(
                        "Searches your synced catalog. Use the magnifier for GitHub-wide search — " +
                            "results are never saved without your tap.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (state.results.isEmpty()) {
                item {
                    Text(
                        "No cached matches. Try GitHub search, or add owner/name from Home.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!grid) {
                items(state.results, key = { it.id }) { repo ->
                    AdaptiveCard(
                        repo = repo,
                        isFavorite = repo.id in state.favoriteIds,
                        apkBadge = if (repo.id in state.apkOnlyIds) ApkBadge.HAS_APK else ApkBadge.UNKNOWN,
                        grid = false,
                        onOpen = { onOpenRepo(repo) },
                        onToggleFavorite = { vm.toggleFavorite(repo) },
                        cardRadiusDp = cardRadiusDp,
                        compact = compact,
                    )
                }
            } else {
                items(state.results.chunked(2), key = { it.first().id }) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { repo ->
                            AdaptiveCard(
                                repo = repo,
                                isFavorite = repo.id in state.favoriteIds,
                                apkBadge = if (repo.id in state.apkOnlyIds) ApkBadge.HAS_APK else ApkBadge.UNKNOWN,
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
            if (state.searching) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Searching GitHub...")
                    }
                }
            }
            if (netResults.isNotEmpty()) {
                item {
                    Text(
                        "GitHub results (not saved)",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                repo.fullName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                "★ ${repo.stars}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onAdd) {
            Icon(Fluent.Add, contentDescription = "Add ${repo.fullName}")
        }
    }
}



