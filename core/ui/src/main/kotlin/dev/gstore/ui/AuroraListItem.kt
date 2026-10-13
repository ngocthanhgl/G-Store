// SPDX-FileCopyrightText: 2026 Aurora OSS
// SPDX-FileCopyrightText: 2026 G Store contributors
// SPDX-License-Identifier: GPL-3.0-or-later
//
// Adapted from Aurora Store's AuroraListItem
// (app/src/main/java/com/aurora/store/compose/composable/AuroraListItem.kt):
// same slot contract (leading / headline / supporting / tertiary /
// trailing), same no-card row, same 16dp/4dp rhythm. Changes for G Store:
// resource dimens inlined as dp (no R.dimen), headline/supporting/tertiary
// truncate with ellipsis (GitHub names, owners, and digests must never
// push their row wider — see DECISIONS.md), no @Preview (no preview
// tooling dep in this module).
package dev.gstore.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Single shared layout for every list-row in the app. A leading slot (icon,
 * image), a stacked headline + supporting + tertiary text column, and a
 * trailing slot (checkbox, button, chip). [headlineStyle] toggles between
 * data-row and settings-row typography while keeping every other dimension
 * consistent.
 */
@Composable
fun AuroraListItem(
    modifier: Modifier = Modifier,
    headline: String,
    supporting: String? = null,
    tertiary: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    headlineStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) leading()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = headline,
                style = headlineStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!supporting.isNullOrBlank()) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!tertiary.isNullOrBlank()) {
                Text(
                    text = tertiary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) trailing()
    }
}
