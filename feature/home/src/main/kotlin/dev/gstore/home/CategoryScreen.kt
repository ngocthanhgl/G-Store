package dev.gstore.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gstore.data.AppCategory
import dev.gstore.data.categoryLabel
import dev.gstore.data.categoryOf
import dev.gstore.model.Repository
import dev.gstore.ui.AdaptiveCard
import dev.gstore.ui.ApkBadge

/**
 * Full shelf page behind a category chevron: every catalog app in the
 * category, stars first. Same cards and actions as Home — a destination,
 * not a filter state, so back navigation works.
 */
@Composable
fun CategoryScreen(
    category: AppCategory,
    vm: HomeViewModel,
    onBack: () -> Unit,
    onOpenRepo: (Repository) -> Unit,
    grid: Boolean = false,
    compact: Boolean = false,
    cardRadiusDp: Int = 12,
) {
    val state by vm.state.collectAsState()
    val items = state.catalog.filter { categoryOf(it.repo.topics) == category }
        .sortedByDescending { it.repo.stars }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = categoryLabel(category),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${items.size} apps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (items.isEmpty()) {
            Text(
                "No apps in this category yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                items(items, key = { "cat-${it.repo.id}" }) { signals ->
                    AdaptiveCard(
                        repo = signals.repo,
                        isFavorite = signals.repo.id in state.favoriteIds,
                        apkBadge = if (signals.eligibleReleaseCount > 0) {
                            ApkBadge.HAS_APK
                        } else {
                            ApkBadge.NO_APK
                        },
                        grid = grid,
                        onOpen = { onOpenRepo(signals.repo) },
                        onToggleFavorite = { vm.toggleFavorite(signals.repo) },
                        cardRadiusDp = cardRadiusDp,
                        compact = compact,
                    )
                }
            }
        }
    }
}
