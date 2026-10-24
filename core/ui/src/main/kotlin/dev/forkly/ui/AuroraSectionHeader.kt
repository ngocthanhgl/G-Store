// SPDX-FileCopyrightText: 2026 Aurora OSS
// SPDX-FileCopyrightText: 2026 Forkly contributors
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Adapted from Aurora Store's SectionHeader
// (app/src/main/java/com/aurora/store/compose/composable/SectionHeader.kt):
// title with optional subtitle, optional trailing slot, whole row clickable
// with a default primary-tinted chevron. Changes for Forkly: resource
// dimens/drawables inlined, Material core chevron, titleLarge + count pill
// per DESIGN.md, 24dp-above/8dp-below rhythm, no @Preview.
package dev.forkly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The app's only section header. Every screen uses this so titles, counts,
 * rhythm and trailing controls cannot drift apart.
 *
 * Rhythm (DESIGN.md): 24dp above the heading, 8dp below it — more space above
 * than below. [topGap] is zero for a header that opens a screen, where the
 * status bar already separates it from the edge.
 *
 * [count] renders as a tonal pill beside the title, matching the ledger
 * convention; it is omitted when null rather than shown as a bare number.
 */
@Composable
fun AuroraSectionHeader(
    modifier: Modifier = Modifier,
    title: String,
    count: Int? = null,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    topGap: Boolean = true,
    bottomGap: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(top = if (topGap) ForklySpace.BeforeHeader else 0.dp)
            .padding(bottom = if (bottomGap) ForklySpace.AfterHeader else 0.dp)
            .defaultMinSize(minHeight = ForklySpace.TouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (count != null) {
            CountPill(count)
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = ForklySpace.Medium),
            )
        }
        when {
            trailing != null -> Box(modifier = Modifier.padding(start = ForklySpace.Medium)) {
                trailing()
            }

            onClick != null -> Icon(
                painter = Fluent.ChevronRight,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = ForklySpace.Medium)
                    .size(ForklySpace.Icon),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Tonal count pill — `· 42`. The ledger answer to "how many". */
@Composable
fun CountPill(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(start = ForklySpace.Small)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .defaultMinSize(minWidth = 28.dp, minHeight = 28.dp)
            .padding(horizontal = ForklySpace.Small),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
        )
    }
}

/**
 * Vertical rhythm tokens. One source for the values DESIGN.md fixes, so a
 * screen cannot pick 12dp where another picked 16dp.
 */
object ForklySpace {
    /** Screen gutter, horizontal. */
    val Gutter = 16.dp

    /** Card inner padding. */
    val Card = 12.dp

    /** Space ABOVE a section header. More than below: the heading leads. */
    val BeforeHeader = 24.dp

    /** Space BELOW a section header. */
    val AfterHeader = 8.dp

    /** Gap between sibling blocks inside a list. */
    val Block = 12.dp

    /** Small inset, e.g. between an icon and its text. */
    val Small = 8.dp

    /** Medium inset. */
    val Medium = 12.dp

    /** Icon size, app-wide. */
    val Icon = 24.dp

    /** App tile / monogram size. */
    val Tile = 56.dp

    /** Minimum primary touch target. */
    val TouchTarget = 48.dp

    /** Minimum height for compact row actions. */
    val RowAction = 40.dp
}