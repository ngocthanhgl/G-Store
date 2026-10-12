package dev.gstore.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.gstore.model.Repository

/**
 * List/grid adaptive card: horizontal [RepoCard] in list mode, vertical
 * [GridCard] in grid mode. Same data, same actions — the layout setting
 * changes the shape, never the content. Used by home, search, favorites.
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
