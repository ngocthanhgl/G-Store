package dev.gstore.readme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gstore.ui.BlockView

@Composable
fun ReadmeScreen(
    state: ReadmeUiState,
    onRefresh: (Boolean) -> Unit,
    onDismissNotice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().statusBarsPadding()) {
        when (state.status) {
            ReadmeStatus.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            ReadmeStatus.MISSING -> EmptyReadme { onRefresh(true) }
            ReadmeStatus.ERROR -> ErrorReadme(state.notice) { onRefresh(true) }
            ReadmeStatus.READY -> BlocksList(state, Modifier.fillMaxSize())
        }
        if (state.notice != null && state.status == ReadmeStatus.READY) {
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = { Button(onClick = onDismissNotice) { Text("Dismiss") } },
            ) { Text(state.notice, maxLines = 2) }
        }
    }
}

@Composable
private fun EmptyReadme(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("No README on the default branch", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "This repository does not publish a README where G Store looks for one.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun ErrorReadme(notice: String?, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("README unavailable", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (notice != null) Text(notice, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun BlocksList(state: ReadmeUiState, modifier: Modifier = Modifier) {
    if (state.stale) {
        Text(
            "Offline copy — may be outdated",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
    LazyColumn(modifier, contentPadding = PaddingValues(horizontal = 16.dp)) {
        itemsIndexed(state.blocks, key = { index, _ -> index }) { _, block ->
            BlockView(block, state.owner, state.repo, state.branch)
            Spacer(Modifier.height(8.dp))
        }
    }
}
