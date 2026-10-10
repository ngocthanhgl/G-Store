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
private val MonogramSize = 44.dp

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
            Surface(
                modifier = Modifier.size(MonogramSize),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                androidx.compose.foundation.layout.Box(
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = repo.owner.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
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
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
                    )
                    Text(
                        text = "· ${formatCount(repo.forks)} forks",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private fun formatCount(n: Int): String = when {    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fk".format(n / 1_000.0)
    else -> n.toString()
}
