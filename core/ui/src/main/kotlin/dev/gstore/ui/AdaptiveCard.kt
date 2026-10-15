package dev.gstore.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.gstore.model.Repository

/**
 * List/grid adaptive card: horizontal [RepoCard] in list mode; in grid mode
 * the persisted density decides — COMFORTABLE keeps the big [GridCard],
 * COMPACT shows the Aurora-style icon+name [RailCard] tile. Same data, same
 * navigation — density changes the shape, never the content.
 */
@Composable
fun AdaptiveCard(
    repo: Repository,
    isFavorite: Boolean,
    apkBadge: ApkBadge = ApkBadge.UNKNOWN,
    grid: Boolean,
    compact: Boolean,
    cardRadiusDp: Int,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (grid) {
        if (compact) {
            Box(
                modifier = modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                RailCard(repo = repo, onOpen = onOpen)
            }
        } else {
            GridCard(
                repo = repo,
                isFavorite = isFavorite,
                apkBadge = apkBadge,
                onOpen = onOpen,
                onToggleFavorite = onToggleFavorite,
                modifier = modifier,
                cardRadiusDp = cardRadiusDp,
                compact = compact,
            )
        }
    } else {
        RepoCard(
            repo = repo,
            isFavorite = isFavorite,
            apkBadge = apkBadge,
            onOpen = onOpen,
            onToggleFavorite = onToggleFavorite,
            modifier = modifier,
            cardRadiusDp = cardRadiusDp,
            compact = compact,
        )
    }
}
