package dev.gstore.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gstore.model.Repository
import dev.gstore.ui.ApkBadge
import dev.gstore.ui.RepoCard

@Composable
fun SearchScreen(
    vm: SearchViewModel,
    onOpenRepo: (Long) -> Unit,
    snackbar: SnackbarHostState,
    grid: Boolean = false,
    compact: Boolean = false,
    cardRadiusDp: Int = 12,
) {
    val state by vm.state.collectAsState()
    val netResults by vm.networkResults.collectAsState()

    LaunchedEffect(state.banner) {
        state.banner?.let {
            snackbar.showSnackbar(it)
            vm.dismissBanner()
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Search", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            label = { Text("Name, description, or owner") },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = vm::searchGitHub) {
                    Icon(Icons.Filled.Search, contentDescription = "Search GitHub")
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
        LazyColumn(
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
                    RepoCard(
                        repo = repo,
                        isFavorite = repo.id in state.favoriteIds,
                        apkBadge = if (repo.id in state.apkOnlyIds) ApkBadge.HAS_APK else ApkBadge.UNKNOWN,
                        onOpen = { onOpenRepo(repo.id) },
                        onToggleFavorite = { vm.toggleFavorite(repo) },
                        cardRadiusDp = cardRadiusDp,
                        compact = compact,
                    )
                }
            } else {
                items(state.results.chunked(2), key = { it.first().id }) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { repo ->
                            RepoCard(
                                repo = repo,
                                isFavorite = repo.id in state.favoriteIds,
                                apkBadge = if (repo.id in state.apkOnlyIds) ApkBadge.HAS_APK else ApkBadge.UNKNOWN,
                                onOpen = { onOpenRepo(repo.id) },
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
                item { Text("Searching GitHub…") }
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
                        onOpen = { onOpenRepo(repo.id) },
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
            Text(repo.fullName, style = MaterialTheme.typography.titleSmall)
            Text(
                "★ ${repo.stars}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = "Add ${repo.fullName}")
        }
    }
}
