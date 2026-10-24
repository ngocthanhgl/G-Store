package dev.forkly.readme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.forkly.ui.safeTop
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.forkly.ui.BlockView
import dev.forkly.ui.ForklySpace
import dev.forkly.ui.ShimmerBlock

@Composable
fun ReadmeScreen(
    state: ReadmeUiState,
    reduceMotion: Boolean = false,
    onRefresh: (Boolean) -> Unit,
    onDismissNotice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().safeTop()) {
        when (state.status) {
            ReadmeStatus.LOADING -> ReadmeSkeleton(reduceMotion)
            ReadmeStatus.MISSING -> EmptyReadme { onRefresh(true) }
            ReadmeStatus.ERROR -> ErrorReadme(state.notice) { onRefresh(true) }
            ReadmeStatus.READY -> BlocksList(state, Modifier.fillMaxSize())
        }
        if (state.notice != null && state.status == ReadmeStatus.READY) {
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(ForklySpace.Gutter),
                action = { Button(onClick = onDismissNotice) { Text("Dismiss") } },
            ) {
                Text(
                    state.notice,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ReadmeSkeleton(reduceMotion: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = ForklySpace.Gutter, vertical = ForklySpace.Block),
        verticalArrangement = Arrangement.spacedBy(ForklySpace.Block),
    ) {
        ShimmerBlock(Modifier.fillMaxWidth(0.58f).height(28.dp), reduceMotion = reduceMotion)
        repeat(3) { section ->
            Column(verticalArrangement = Arrangement.spacedBy(ForklySpace.Small)) {
                ShimmerBlock(
                    Modifier.fillMaxWidth(if (section == 0) 0.72f else 0.48f).height(20.dp),
                    reduceMotion = reduceMotion,
                )
                repeat(3) { line ->
                    ShimmerBlock(
                        Modifier.fillMaxWidth(if (line == 2) 0.64f else 1f).height(12.dp),
                        radiusDp = 6,
                        reduceMotion = reduceMotion,
                    )
                }
            }
        }
        ShimmerBlock(
            Modifier.fillMaxWidth().height(200.dp),
            radiusDp = 12,
            reduceMotion = reduceMotion,
        )
    }
}

@Composable
private fun EmptyReadme(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(ForklySpace.Gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "No README on the default branch",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(ForklySpace.Medium))
        Text(
            "This repository does not publish a README where Forkly looks for one.",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(ForklySpace.Medium))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun ErrorReadme(notice: String?, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(ForklySpace.Gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "README unavailable",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(ForklySpace.Medium))
        if (notice != null) {
            Text(
                notice,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(ForklySpace.Medium))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun BlocksList(state: ReadmeUiState, modifier: Modifier = Modifier) {
    // The stale banner and the list were both Box children, so the list drew
    // over the banner. A Column gives the banner its own row.
    Column(modifier) {
        if (state.stale) {
            Text(
                "Offline copy — may be outdated",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(
                    horizontal = ForklySpace.Gutter,
                    vertical = ForklySpace.Medium,
                ),
            )
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = ForklySpace.Gutter,
                end = ForklySpace.Gutter,
                top = ForklySpace.Block,
                bottom = ForklySpace.Block,
            ),
        ) {
            itemsIndexed(state.blocks, key = { index, _ -> index }) { _, block ->
                // Cap the measure: full-width body text is unreadable on
                // tablets and expanded windows.
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 680.dp)) {
                        BlockView(block, state.owner, state.repo, state.branch)
                        Spacer(Modifier.height(ForklySpace.Block))
                    }
                }
            }
        }
    }
}
