// SPDX-FileCopyrightText: 2026 Aurora OSS
// SPDX-FileCopyrightText: 2026 G Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Adapted from Aurora Store's SectionHeader
// (app/src/main/java/com/aurora/store/compose/composable/SectionHeader.kt):
// title with optional subtitle, optional trailing slot, whole row clickable
// with a default primary-tinted chevron. Changes for G Store: resource
// dimens/drawables inlined (16dp/4dp/8dp rhythm, Material core chevron),
// no @Preview.
package dev.gstore.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Section header row used throughout the app. Title with optional subtitle,
 * an optional trailing slot, and the whole row becomes clickable when
 * [onClick] is non-null. When [trailing] is null and [onClick] is set, a
 * default right-chevron is shown.
 */
@Composable
fun AuroraSectionHeader(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(
                painter = Fluent.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
