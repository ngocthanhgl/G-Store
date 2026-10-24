package dev.forkly.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.forkly.model.CatalogFailure

/** Shared screen states — every async surface uses one of these. */
sealed interface ScreenState<out T> {
    data object Loading : ScreenState<Nothing>
    data class Content<T>(val data: T) : ScreenState<T>
    data object Empty : ScreenState<Nothing>
    data class Error(val failure: CatalogFailure, val retry: () -> Unit) : ScreenState<Nothing>
}

/**
 * Loading state. Skeleton rows, never a centred spinner: a spinner in an
 * empty frame is a dead pixel (DESIGN.md, "Loading = skeleton rows ...
 * never a dead spinner") because it tells the user nothing about what is
 * arriving. [reduceMotion] freezes the sweep into a static gradient.
 */
@Composable
fun LoadingContent(
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    rows: Int = 6,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(ForklySpace.Gutter),
        contentAlignment = Alignment.TopCenter,
    ) {
        ShimmerRows(count = rows, reduceMotion = reduceMotion)
    }
}

/** Problem + recovery. Never a dead end: there is always a Retry. */
@Composable
fun ErrorContent(failure: CatalogFailure, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val message = when (failure) {
        CatalogFailure.Network -> "No connection. Cached content stays available."
        is CatalogFailure.RateLimited -> "GitHub rate limit reached. Try again shortly."
        CatalogFailure.Auth -> "Authentication failed."
        CatalogFailure.NotFound -> "Not found."
        CatalogFailure.Malformed -> "Unexpected response from GitHub."
        CatalogFailure.Storage -> "Storage unavailable."
    }
    // The Box owns no horizontal padding: [EmptyContent] supplies the gutter,
    // so wrapping callers must not add a second one.
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ForklySpace.Block),
            modifier = Modifier.padding(horizontal = ForklySpace.Gutter),
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .defaultMinSize(minHeight = ForklySpace.TouchTarget),
            ) {
                Text("Retry")
            }
        }
    }
}

/** Icon + one sentence (+ optional action). Callers add their own action. */
@Composable
fun EmptyContent(
    message: String,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = ForklySpace.Gutter),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ForklySpace.Block),
        ) {
            if (icon != null) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (action != null) {
                Box(modifier = Modifier.heightIn(min = ForklySpace.TouchTarget)) { action() }
            }
        }
    }
}