package dev.gstore.home

import dev.gstore.ui.AuroraSectionHeader
import dev.gstore.ui.safeTop
import dev.gstore.ui.CountPill
import dev.gstore.ui.EmptyContent
import dev.gstore.ui.GStoreSpace
import dev.gstore.ui.Fluent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gstore.data.AppCategory
import dev.gstore.data.categoryLabel
import dev.gstore.model.RepoSignals
import dev.gstore.model.Repository
import dev.gstore.ui.AdaptiveCard
import dev.gstore.ui.ApkBadge
import dev.gstore.ui.RailCard
import dev.gstore.ui.ShimmerRows

internal fun apkBadgeOf(signals: RepoSignals): ApkBadge =
    if (signals.eligibleReleaseCount > 0) ApkBadge.HAS_APK else ApkBadge.UNKNOWN

/**
 * Aurora-style shelf: one horizontal rail of the top 5 apps per category.
 * Only categories present in the catalog render — never empty shells.
 */
@Composable
fun CategoryRail(
    category: AppCategory,
    count: Int,
    top: List<RepoSignals>,
    favorites: Set<Long>,
    cardRadiusDp: Int,
    onOpenCategory: (AppCategory) -> Unit,
    onOpen: (Repository) -> Unit,
    onToggleFavorite: (Repository) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GStoreSpace.Block)) {
        // The shared header: same titleLarge + count pill + 48dp trailing
        // control as every other section in the app.
        AuroraSectionHeader(
            title = categoryLabel(category),
            count = count,
            onClick = { onOpenCategory(category) },
            trailing = {
                Text(
                    "See all",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            },
            modifier = Modifier.padding(horizontal = GStoreSpace.Gutter),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(GStoreSpace.Small),
            contentPadding = PaddingValues(horizontal = GStoreSpace.Gutter),
        ) {
            items(top, key = { it.repo.id }) { signals ->
                RailCard(
                    repo = signals.repo,
                    onOpen = { onOpen(signals.repo) },
                )
            }
        }
    }
}

/**
 * Full category panel: every app in the category, stars-first, honoring the
 * persisted Card/List layout. Genuine loads show skeletons, never blank.
 */
@Composable
fun CategoryFullScreen(
    category: AppCategory,
    items: List<RepoSignals>,
    favorites: Set<Long>,
    syncing: Boolean,
    grid: Boolean,
    compact: Boolean,
    cardRadiusDp: Int,
    reduceMotion: Boolean,
    onBack: () -> Unit,
    onOpen: (Repository) -> Unit,
    onToggleFavorite: (Repository) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().safeTop()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = GStoreSpace.Gutter),
        ) {
            IconButton(onClick = onBack) {
                Icon(Fluent.ArrowLeft, contentDescription = "Back")
            }
            Text(
                text = categoryLabel(category),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            CountPill(items.size)
        }
        if (items.isEmpty() && syncing) {
            ShimmerRows(
                count = 8,
                reduceMotion = reduceMotion,
                modifier = Modifier.padding(horizontal = GStoreSpace.Gutter),
            )
        } else if (items.isEmpty()) {
            EmptyContent(
                message = "No apps in this category yet.",
                icon = Fluent.Add,
            )
        } else if (grid) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(GStoreSpace.Gutter),
                horizontalArrangement = Arrangement.spacedBy(GStoreSpace.Gutter),
                contentPadding = PaddingValues(
                    start = GStoreSpace.Gutter,
                    end = GStoreSpace.Gutter,
                    top = GStoreSpace.Block,
                    bottom = GStoreSpace.Block,
                ),
            ) {
                items(items, key = { it.repo.id }) { signals ->
                    AdaptiveCard(
                        repo = signals.repo,
                        isFavorite = signals.repo.id in favorites,
                        apkBadge = apkBadgeOf(signals),
                        grid = true,
                        onOpen = { onOpen(signals.repo) },
                        onToggleFavorite = { onToggleFavorite(signals.repo) },
                        cardRadiusDp = cardRadiusDp,
                        compact = compact,
                    )
                }
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                verticalArrangement = Arrangement.spacedBy(GStoreSpace.Small),
                contentPadding = PaddingValues(
                    start = GStoreSpace.Gutter,
                    end = GStoreSpace.Gutter,
                    top = GStoreSpace.Block,
                    bottom = GStoreSpace.Block,
                ),
            ) {
                items(items, key = { it.repo.id }) { signals ->
                    AdaptiveCard(
                        repo = signals.repo,
                        isFavorite = signals.repo.id in favorites,
                        apkBadge = apkBadgeOf(signals),
                        grid = false,
                        onOpen = { onOpen(signals.repo) },
                        onToggleFavorite = { onToggleFavorite(signals.repo) },
                        cardRadiusDp = cardRadiusDp,
                        compact = compact,
                    )
                }
            }
        }
    }
}
