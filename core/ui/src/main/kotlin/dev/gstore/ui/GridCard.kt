package dev.gstore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gstore.model.Repository

/**
 * Vertical grid card — the GRID counterpart to [RepoCard]'s horizontal row.
 * Fixed text slots (2-line name, 1-line ledger, fixed badge slot) keep every
 * card in a row the same height; nothing wraps or pushes its neighbor.
 * Touch target >= 48dp; contentDescription on the action (UI_UX_SPEC §6).
 * Radius comes from persisted appearance — never hardcoded here.
 */
@Composable
fun GridCard(
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
        Column(
            modifier = Modifier.padding(if (compact) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(
                    avatarUrl = repo.avatarUrl,
                    owner = repo.owner,
                    size = 56.dp,
                    modifier = Modifier.weight(1f, fill = false),
                )
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        if (isFavorite) Fluent.Heart else Fluent.Heart,
                        contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = repo.fullName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Description slot is always present (possibly empty) so cards
            // with and without descriptions stay the same height.
            Text(
                text = repo.description ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Fluent.Star,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatCount(repo.stars),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Status slot is always one line (possibly empty) for alignment.
            val status = when {
                repo.archived -> "Archived" to MaterialTheme.colorScheme.error
                apkBadge == ApkBadge.HAS_APK -> "APK available" to MaterialTheme.colorScheme.primary
                else -> "" to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = status.first,
                style = MaterialTheme.typography.labelSmall,
                color = status.second,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
