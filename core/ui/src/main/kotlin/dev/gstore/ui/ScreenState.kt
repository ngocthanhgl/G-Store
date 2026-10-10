package dev.gstore.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gstore.model.CatalogFailure

/** Shared screen states — every async surface uses one of these. */
sealed interface ScreenState<out T> {
    data object Loading : ScreenState<Nothing>
    data class Content<T>(val data: T) : ScreenState<T>
    data object Empty : ScreenState<Nothing>
    data class Error(val failure: CatalogFailure, val retry: () -> Unit) : ScreenState<Nothing>
}

@Composable
fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorContent(failure: CatalogFailure, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                when (failure) {
                    CatalogFailure.Network -> "No connection. Cached content stays available."
                    is CatalogFailure.RateLimited -> "GitHub rate limit reached. Try again shortly."
                    CatalogFailure.Auth -> "Authentication failed."
                    CatalogFailure.NotFound -> "Not found."
                    CatalogFailure.Malformed -> "Unexpected response from GitHub."
                    CatalogFailure.Storage -> "Storage unavailable."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                Text("Retry")
            }
        }
    }
}

@Composable
fun EmptyContent(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}
