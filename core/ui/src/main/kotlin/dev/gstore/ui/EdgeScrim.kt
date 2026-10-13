package dev.gstore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Legibility scrim behind the status bar (top) or navigation bar (bottom).
 * A short surface-to-transparent gradient, not a true blur: RenderEffect
 * blurs cost a full-screen offscreen pass per frame and need API 31+,
 * while the scrim lifts bar icons off scrolled content on every level.
 * No click handling — touches pass straight through to content below.
 */
@Composable
fun EdgeScrim(top: Boolean, modifier: Modifier = Modifier) {
    val surface = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    if (top) {
        Column(modifier.fillMaxWidth()) {
            SpacerBars(WindowInsets.statusBars, top = true)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(
                        Brush.verticalGradient(listOf(surface, Color.Transparent)),
                    ),
            )
        }
    } else {
        Column(modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, surface)),
                    ),
            )
            SpacerBars(WindowInsets.navigationBars, top = false)
        }
    }
}

@Composable
private fun SpacerBars(insets: WindowInsets, top: Boolean) {
    Box(
        Modifier.fillMaxWidth().then(
            if (top) Modifier.windowInsetsTopHeight(insets)
            else Modifier.windowInsetsBottomHeight(insets),
        ),
    )
}
