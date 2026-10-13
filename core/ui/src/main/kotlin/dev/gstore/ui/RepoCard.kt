// SPDX-FileCopyrightText: 2026 G Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Repository row built on Aurora Store's AuroraListItem contract (leading /
// headline / supporting / tertiary / trailing, no card wrapper).
package dev.gstore.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gstore.model.Repository

/** APK availability as known from cached catalog metadata — never a guarantee. */
enum class ApkBadge { HAS_APK, NO_APK, UNKNOWN }

private val TouchTarget = 48.dp

/**
 * Shared repository row (home, search, favorites): squircle icon, short
 * name, owner, one ledger line, one 48dp favorite action. Same shape in
 * list and search; grid mode uses [GridCard] instead.
 * Touch target >= 48dp; contentDescription on every action (UI_UX_SPEC §6).
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
    AuroraListItem(
        modifier = modifier,
        headline = repo.fullName.substringAfter('/'),
        supporting = repo.owner,
        tertiary = ledgerOf(repo, apkBadge),
        headlineStyle = MaterialTheme.typography.bodyMedium,
        onClick = onOpen,
        leading = {
            AppIcon(
                avatarUrl = repo.avatarUrl,
                owner = repo.owner,
                size = 56.dp,
            )
        },
        trailing = {
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
        },
    )
}

/** One ledger line: status first, then popularity. Plain text, no chips. */
private fun ledgerOf(repo: Repository, apkBadge: ApkBadge): String {
    val status = when {
        repo.archived -> "Archived"
        apkBadge == ApkBadge.HAS_APK -> "APK available"
        apkBadge == ApkBadge.NO_APK -> "No APK"
        else -> null
    }
    // No description here: it never fits beside popularity and always
    // degrades into a dangling ellipsis. Full text lives on Details.
    val popularity = "★ ${formatCount(repo.stars)} · ${formatCount(repo.forks)} forks"
    return if (status == null) popularity else "$status · $popularity"
}
// formatCount lives in RailCard.kt — shared by both card styles.
