package dev.gstore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gstore.model.Repository

/** APK availability as known from cached catalog metadata — never a guarantee. */
enum class ApkBadge { HAS_APK, NO_APK, UNKNOWN }

private val TouchTarget = 48.dp

/**
 * Shared repository card (home, search, favorites) — DESIGN.md "Tonal Shelf":
 * monogram tile, title, ledger row, one 48dp favorite action.
 * Touch target >= 48dp; contentDescription on every action (UI_UX_SPEC §6).
 * Radius/density come from persisted appearance (M7) — never hardcoded here.
 */
@Composable
fun RepoCard(
    repo: Repository,
    isFavorite: Boolean,
    apkBadge: ApkBadge = ApkBadge.UNKNOWN,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    cardRadiusDp: Int = 12,
    compact: Boolean = false,
) {
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = dev.gstore.designsystem.cardShapeFor(cardRadiusDp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(
                avatarUrl = repo.avatarUrl,
                owner = repo.owner,
                size = 48.dp,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = repo.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val description = repo.description
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // List rows stay one line: a second line reads as
                        // overflow, not information. Full text lives in Details.
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Ledger row truncates as a unit: forks takes the remaining
                // width and ellipsizes instead of pushing the card wider.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatCount(repo.stars),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Text(
                        text = "· ${formatCount(repo.forks)} forks",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // fill=true (default): the weight BOUNDS the text so the
                        // ellipsis can engage. fill=false leaves intrinsic width
                        // and overflows the card — the classic spill bug.
                        modifier = Modifier.weight(1f),
                    )
                }
                // Badges get their own line so they never squeeze the ledger
                // off narrow cards; each badge truncates instead of wrapping.
                val badgeRow = @Composable {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (repo.archived) {
                            Badge(
                                text = "Archived",
                                container = MaterialTheme.colorScheme.errorContainer,
                                content = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                        when (apkBadge) {
                            ApkBadge.HAS_APK -> Badge(
                                text = "APK",
                                container = MaterialTheme.colorScheme.primaryContainer,
                                content = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            ApkBadge.NO_APK -> Badge(text = "No APK")
                            ApkBadge.UNKNOWN -> Unit
                        }
                    }
                }
                if (repo.archived || apkBadge != ApkBadge.UNKNOWN) badgeRow()
            }
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(TouchTarget),
            ) {
                Icon(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Static status badge — deliberately not clickable. Tonal per DESIGN.md. */
@Composable
private fun Badge(
    text: String,
    container: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = container,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
// formatCount lives in RailCard.kt — shared by both card styles.
