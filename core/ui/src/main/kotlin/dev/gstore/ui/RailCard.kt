// SPDX-FileCopyrightText: 2025 The Calyx Institute
// SPDX-FileCopyrightText: 2026 G Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Adapted from Aurora Store's AppListItem
// (app/src/main/java/com/aurora/store/compose/composable/app/AppListItem.kt):
// 80dp tile, full-width 1:1 squircle artwork, two-line label. Changes for
// G Store: resource dimens inlined (80dp tile, 12dp radius, 4dp rhythm),
// AppIcon (avatar-over-monogram) instead of raw AsyncImage since catalog
// entries may carry no URL, no @Preview.
package dev.gstore.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gstore.model.Repository

/**
 * Minimal app tile for horizontal rails: artwork plus display name.
 */
@Composable
fun RailCard(
    repo: Repository,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(80.dp)
            .clickable(onClick = onOpen)
            .padding(all = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        AppIcon(
            avatarUrl = repo.avatarUrl,
            owner = repo.owner,
            size = 72.dp,
            modifier = Modifier,
        )
        Text(
            modifier = Modifier,
            text = repo.fullName.substringAfter('/'),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Shared compact count — matches list/grid ledger formatting. */
internal fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fk".format(n / 1_000.0)
    else -> n.toString()
}
