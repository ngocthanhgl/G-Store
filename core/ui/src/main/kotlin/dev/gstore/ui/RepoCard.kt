// SPDX-FileCopyrightText: 2026 G Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Repository row built on Aurora Store's AuroraListItem contract (leading /
// headline / supporting / tertiary / trailing, no card wrapper).
package dev.gstore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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

/** APK availability as known from cached catalog metadata — never a guarantee. */
enum class ApkBadge { HAS_APK, UNKNOWN }

/**
 * Shared repository row (home, search, favorites, downloads shelves): squircle
 * icon, short name, owner, one ledger line, one 48dp favorite action. Same
 * shape in list and search; grid mode uses [AdaptiveCard] instead.
 * Icon size comes from [GStoreSpace] so the row matches every other card.
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
) {
    AuroraListItem(
        modifier = modifier,
        headline = repo.fullName.substringAfter('/'),
        supporting = repo.owner,
        tertiaryContent = { LedgerLine(repo, apkBadge) },
        headlineStyle = MaterialTheme.typography.bodyMedium,
        onClick = onOpen,
        leading = {
            AppIcon(
                avatarUrl = repo.avatarUrl,
                owner = repo.owner,
                size = GStoreSpace.Tile,
            )
        },
        trailing = {
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
        },
    )
}

/**
 * The one ledger line every card shows: status pill, star icon + count,
 * fork count. Both [RepoCard] and [GridCard] render this so the same app
 * reads identically in list and grid. The star is the real Fluent icon,
 * never a unicode glyph.
 */
@Composable
fun LedgerLine(repo: Repository, apkBadge: ApkBadge, modifier: Modifier = Modifier) {
    val status = when {
        repo.archived -> "Archived"
        apkBadge == ApkBadge.HAS_APK -> "APK available"
        else -> ""
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(GStoreSpace.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (status.isNotEmpty()) {
            StatusPill(status)
        }
        Icon(
            Fluent.Star,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "${formatCount(repo.stars)} stars",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}