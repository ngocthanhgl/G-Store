package dev.gstore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gstore.designsystem.cardShapeFor
import dev.gstore.model.Repository

/**
 * Vertical grid card — the GRID counterpart to [RepoCard]'s horizontal row.
 * Fixed text slots (2-line name, 1-line ledger, fixed status slot) keep every
 * card in a row the same height; nothing wraps or pushes its neighbor.
 *
 * Shares its radius, padding and icon size with every other card through
 * [cardShapeFor] and [GStoreSpace], so switching a card between list, grid and
 * rail never changes its metrics. Touch target 48dp on the action.
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
) {
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = cardShapeFor(cardRadiusDp),
    ) {
        Column(
            modifier = Modifier.padding(GStoreSpace.Card),
            verticalArrangement = Arrangement.spacedBy(GStoreSpace.Block),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(
                    avatarUrl = repo.avatarUrl,
                    owner = repo.owner,
                    size = GStoreSpace.Tile,
                    modifier = Modifier.weight(1f, fill = false),
                )
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(GStoreSpace.TouchTarget),
                ) {
                    Icon(
                        Fluent.Heart,
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
            LedgerLine(repo, apkBadge)
        }
    }
}

/**
 * Status as a tonal pill rather than a raw hue on the surface. An empty string
 * still renders the slot so card heights stay equal across a row.
 */
@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier) {
    if (text.isEmpty()) {
        Box(modifier = modifier.defaultMinSize(minHeight = 24.dp))
        return
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .defaultMinSize(minHeight = 24.dp)
            .padding(horizontal = GStoreSpace.Small, vertical = 2.dp),
    )
}