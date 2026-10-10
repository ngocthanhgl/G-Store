package dev.gstore.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gstore.ui.RepoCard

@Composable
fun FavoritesScreen(
    vm: FavoritesViewModel,
    onOpenRepo: (Long) -> Unit,
    grid: Boolean = false,
    compact: Boolean = false,
    cardRadiusDp: Int = 12,
) {
    val favorites by vm.favorites.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Favorites", style = MaterialTheme.typography.headlineSmall)
        if (favorites.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No favorites yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tap the heart on any repository. Favorites work fully offline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                if (!grid) {
                    items(favorites, key = { it.id }) { repo ->
                        RepoCard(
                            repo = repo,
                            isFavorite = true,
                            onOpen = { onOpenRepo(repo.id) },
                            onToggleFavorite = { vm.toggleFavorite(repo) },
                            cardRadiusDp = cardRadiusDp,
                            compact = compact,
                        )
                    }
                } else {
                    items(favorites.chunked(2), key = { it.first().id }) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { repo ->
                                RepoCard(
                                    repo = repo,
                                    isFavorite = true,
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
            }
        }
    }
}
