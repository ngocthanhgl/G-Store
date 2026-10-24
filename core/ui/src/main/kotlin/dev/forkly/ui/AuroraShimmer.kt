/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS (original Shimmer design)
 * SPDX-FileCopyrightText: 2026 Forkly contributors (Compose port)
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.forkly.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

import dev.forkly.designsystem.LocalCardRadius
import dev.forkly.designsystem.cardShapeFor

/**
 * Aurora-style loading placeholders: a travelling diagonal highlight sweep
 * (not an alpha pulse) over faithful per-screen layouts — list rows, rail
 * tiles, category rows. Unlike Aurora, motion stops at a static tone when
 * [reduceMotion] is true.
 */
@Composable
fun shimmerBrush(reduceMotion: Boolean = false): Brush {
    val base = MaterialTheme.colorScheme.onSurface
    if (reduceMotion) {
        return Brush.linearGradient(listOf(base.copy(alpha = 0.12f), base.copy(alpha = 0.12f)))
    }
    val colors = listOf(base.copy(alpha = 0.08f), base.copy(alpha = 0.20f), base.copy(alpha = 0.08f))
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_offset",
    )
    val x = offset * 1200f
    return Brush.linearGradient(colors, start = Offset(x - 400f, 0f), end = Offset(x, 400f))
}

/** Text placeholders stay near-square: a pill bar reads as a control, not text. */
private const val TextBarRadius = 6

/** Image placeholders follow the image cap, never the card slider. */
private const val ImageBarRadius = 12

@Composable
fun ShimmerBlock(modifier: Modifier, radiusDp: Int = LocalCardRadius.current, reduceMotion: Boolean = false) {
    Box(
        modifier = modifier
            .clip(cardShapeFor(radiusDp))
            .background(shimmerBrush(reduceMotion)),
    )
}

@Composable
private fun ShimmerTextStack(
    modifier: Modifier = Modifier,
    fractions: List<Float>,
    reduceMotion: Boolean,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        fractions.forEach { fraction ->
            ShimmerBlock(
                modifier = Modifier.fillMaxWidth(fraction).height(16.dp),
                radiusDp = TextBarRadius,
                reduceMotion = reduceMotion,
            )
        }
    }
}

/** List-row shimmer: 56dp icon + 3 text bars + optional trailing button. */
@Composable
fun ShimmerRows(
    count: Int = 5,
    showTrailing: Boolean = false,
    reduceMotion: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(count.coerceIn(1, 10)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ShimmerBlock(modifier = Modifier.size(56.dp), radiusDp = ImageBarRadius, reduceMotion = reduceMotion)
                ShimmerTextStack(
                    modifier = Modifier.weight(1f),
                    fractions = listOf(0.7f, 0.5f, 0.5f),
                    reduceMotion = reduceMotion,
                )
                if (showTrailing) {
                    ShimmerBlock(
                        modifier = Modifier.width(80.dp).height(36.dp),
                        radiusDp = 16,
                        reduceMotion = reduceMotion,
                    )
                }
            }
        }
    }
}

/** Horizontal rail shimmer: 80dp tiles with two label bars, like a category rail. */
@Composable
fun ShimmerTiles(
    count: Int = 5,
    reduceMotion: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(count.coerceIn(1, 8)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ShimmerBlock(
                    modifier = Modifier.size(80.dp),
                    radiusDp = ImageBarRadius,
                    reduceMotion = reduceMotion,
                )
                ShimmerBlock(
                    modifier = Modifier.width(60.dp).height(12.dp),
                    radiusDp = TextBarRadius,
                    reduceMotion = reduceMotion,
                )
                ShimmerBlock(
                    modifier = Modifier.width(40.dp).height(12.dp),
                    radiusDp = TextBarRadius,
                    reduceMotion = reduceMotion,
                )
            }
        }
    }
}

/** Category-row shimmer: 32dp icon + full-width bar. */
@Composable
fun ShimmerCategoryRows(
    count: Int = 5,
    reduceMotion: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(count.coerceIn(1, 10)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ShimmerBlock(modifier = Modifier.size(32.dp), radiusDp = ImageBarRadius, reduceMotion = reduceMotion)
                ShimmerBlock(
                    modifier = Modifier.weight(1f).height(24.dp),
                    radiusDp = TextBarRadius,
                    reduceMotion = reduceMotion,
                )
            }
        }
    }
}
