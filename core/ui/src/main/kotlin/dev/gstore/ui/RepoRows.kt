package dev.gstore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * List or 2-column grid of cards. Grid reuses the same card at half width;
 * card count and order are identical in both layouts (M7).
 *
 * The grid gap is [GStoreSpace.Gutter] on BOTH axes: callers space their
 * own rows with the same token, so a grid never reads as 16dp across and
 * 12dp down.
 */
@Composable
fun RepoRows(
    grid: Boolean,
    count: Int,
    keyOf: (Int) -> Any,
    card: @Composable (Int) -> Unit,
    rowGap: Dp = GStoreSpace.Gutter,
) {
    if (!grid) {
        for (index in 0 until count) {
            key(keyOf(index)) { card(index) }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(rowGap)) {
        for (rowStart in (0 until count) step 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(GStoreSpace.Gutter)) {
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
}

