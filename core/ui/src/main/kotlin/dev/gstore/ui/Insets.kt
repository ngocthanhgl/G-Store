package dev.gstore.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Top safe inset for a screen body.
 *
 * [androidx.compose.foundation.layout.statusBarsPadding] covers the status bar
 * only, so on a device whose camera is a punch-hole inside (or taller than) the
 * status bar, content slid under the lens. [safeDrawing] takes the larger of
 * the status bar and the display cutout, which is what every screen actually
 * needs.
 *
 * Horizontal insets are applied once by the shell, so this is Top only —
 * applying both would double the gutter.
 */
@Composable
fun Modifier.safeTop(): Modifier =
    windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))

/**
 * Bottom safe inset for screens that are NOT covered by the navigation bar
 * (the expanded-width rail layout, and full-screen viewers). The compact
 * navigation bar owns its own inset, so compact screens must not add this.
 */
@Composable
fun Modifier.safeBottom(): Modifier =
    windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))

/**
 * Horizontal safe inset, for content hosted outside the shell's padded area
 * (dialog-style permission gates drawn as a bare Card).
 */
@Composable
fun Modifier.safeHorizontal(): Modifier =
    windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))