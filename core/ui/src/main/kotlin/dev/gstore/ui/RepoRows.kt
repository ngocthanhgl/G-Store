package dev.gstore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * List or 2-column grid of cards. Grid reuses the same card at half width;
 * card count and order are identical in both layouts (M7).
 */
@Composable
fun RepoRows(
    grid: Boolean,
    count: Int,
    keyOf: (Int) -> Any,
    card: @Composable (Int) -> Unit,
) {
    if (!grid) {
        for (index in 0 until count) {
            key(keyOf(index)) { card(index) }
        }
        return
    }
    for (rowStart in (0 until count) step 2) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (index in rowStart until minOf(rowStart + 2, count)) {
                key(keyOf(index)) {
                    Box(Modifier.weight(1f)) { card(index) }
                }
            }
            if ((count - rowStart) == 1) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

