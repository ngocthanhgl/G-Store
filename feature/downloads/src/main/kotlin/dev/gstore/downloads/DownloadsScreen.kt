package dev.gstore.downloads

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.gstore.data.UpdateState
import dev.gstore.database.DownloadRecord
import dev.gstore.model.VerificationResult
import dev.gstore.ui.AppIcon
import dev.gstore.ui.EmptyContent

/**
 * Download queue + history (flow C) and the Installed shelf: every APK
 * fetched through G Store with its on-device state (flow: PackageManager
 * truth, catalog tags for updates). Progress survives Activity recreation
 * (Room-backed); cancellation and retry act on the record, never on a lost
 * callback. A COMPLETED row offers installation handoff via [onInstall].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    state: DownloadsUiState,
    onCancel: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onForget: (Long) -> Unit,
    onInstall: (DownloadRecord) -> Unit,
    onBrowseRepo: (String) -> Unit,
    onLaunch: (String) -> Unit,
    onRefreshInstalled: () -> Unit,
    onSort: (InstalledSort) -> Unit,
    onOpenLogin: () -> Unit,
    onDismissLoginPrompt: () -> Unit,
    snackbar: SnackbarHostState,
    onDismissNotice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notice = state.notice
    LaunchedEffect(notice) {
        if (notice != null) {
            snackbar.showSnackbar(notice)
            onDismissNotice()
        }
    }
    LaunchedEffect(Unit) { onRefreshInstalled() }
    if (state.showLoginPrompt) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismissLoginPrompt,
            title = { Text("Slow down") },
            text = {
                Text(
                    "One update check per minute — the anonymous GitHub quota " +
                        "is 60 requests/hour. Log in to lift it to 5,000.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDismissLoginPrompt()
                        onOpenLogin()
                    },
                ) { Text("Log in to GitHub") }
            },
            dismissButton = {
                TextButton(onClick = onDismissLoginPrompt) { Text("Later") }
            },
        )
    }
    PullToRefreshBox(
        isRefreshing = state.installedRefreshing,
        onRefresh = onRefreshInstalled,
        modifier = modifier.fillMaxSize().statusBarsPadding(),
    ) {
        val updates = state.installed.filter {
            it.updateState == UpdateState.UPDATE_AVAILABLE ||
                it.updateState == UpdateState.READY_TO_INSTALL
        }
        val current = state.installed.filter { it.updateState == UpdateState.UP_TO_DATE }
        val missing = state.installed.filter { it.updateState == UpdateState.NOT_INSTALLED }
        if (state.installed.isEmpty() && state.active.isEmpty() && state.history.isEmpty()) {
            EmptyContent(
                message = "Nothing here yet. Pick an APK on an app's details screen.",
                icon = Icons.Filled.KeyboardArrowDown,
            )
            return@PullToRefreshBox
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Installed",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onRefreshInstalled) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Check for updates")
                    }
                }
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    InstalledSort.entries.forEach { option ->
                        FilterChip(
                            selected = state.sort == option,
                            onClick = { onSort(option) },
                            label = {
                                Text(
                                    when (option) {
                                        InstalledSort.NAME -> "Name"
                                        InstalledSort.RECENT -> "Recent"
                                        InstalledSort.SIZE -> "Size"
                                    },
                                )
                            },
                        )
                    }
                }
            }
            if (updates.isNotEmpty()) {
                item { SectionTitle("Updates available (${updates.size})") }
                items(updates, key = { "u-${it.record.assetId}" }) { row ->
                    InstalledRow(
                        row = row,
                        onBrowseRepo = onBrowseRepo,
                        onLaunch = onLaunch,
                        onInstall = onInstall,
                    )
                }
            }
            if (current.isNotEmpty()) {
                item { SectionTitle("Installed via G Store (${current.size})") }
                items(current, key = { "i-${it.record.assetId}" }) { row ->
                    InstalledRow(
                        row = row,
                        onBrowseRepo = onBrowseRepo,
                        onLaunch = onLaunch,
                        onInstall = onInstall,
                    )
                }
            }
            if (missing.isNotEmpty()) {
                item { SectionTitle("Not on this device (${missing.size})") }
                items(missing, key = { "m-${it.record.assetId}" }) { row ->
                    InstalledRow(
                        row = row,
                        onBrowseRepo = onBrowseRepo,
                        onLaunch = onLaunch,
                        onInstall = onInstall,
                    )
                }
            }
            if (state.active.isNotEmpty()) {
                item { SectionTitle("Active") }
                items(state.active, key = { it.assetId }) { record ->
                    ActiveRow(record = record, onCancel = { onCancel(record.assetId) })
                }
            }
            if (state.history.isNotEmpty()) {
                item { SectionTitle("History") }
                items(state.history, key = { it.assetId }) { record ->
                    HistoryRow(
                        record = record,
                        onRetry = { onRetry(record.assetId) },
                        onForget = { onForget(record.assetId) },
                        onInstall = { onInstall(record) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InstalledRow(
    row: InstalledRow,
    onBrowseRepo: (String) -> Unit,
    onLaunch: (String) -> Unit,
    onInstall: (DownloadRecord) -> Unit,
) {
    val record = row.record
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PackageIcon(
                packageName = record.packageName ?: record.repoFullName,
                label = row.label,
            )
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = record.packageName ?: record.repoFullName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                val status = when (row.updateState) {
                    UpdateState.UPDATE_AVAILABLE ->
                        "Update available${row.latestTag?.let { " · $it" } ?: ""}"
                    UpdateState.READY_TO_INSTALL -> "Downloaded update ready to install"
                    UpdateState.UP_TO_DATE ->
                        "Installed${row.installedVersionName?.let { " · $it" } ?: ""}"
                    UpdateState.NOT_INSTALLED -> "Not installed on this device"
                }
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (row.updateState == UpdateState.UP_TO_DATE) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (row.updateState) {
                    UpdateState.UPDATE_AVAILABLE -> Button(
                        onClick = { onBrowseRepo(record.repoFullName) },
                    ) { Text("Update") }
                    UpdateState.READY_TO_INSTALL -> Button(
                        onClick = { onInstall(record) },
                    ) { Text("Install") }
                    UpdateState.NOT_INSTALLED -> Button(
                        onClick = { onInstall(record) },
                    ) { Text("Install") }
                    UpdateState.UP_TO_DATE -> Unit
                }
                if (row.installedVersionCode != null) {
                    TextButton(onClick = { onLaunch(record.packageName ?: "") }) {
                        Text("Open")
                    }
                }
            }
        }
    }
}

/** Real app icon from PackageManager, monogram tile while it resolves. */
@Composable
private fun PackageIcon(packageName: String, label: String) {
    val context = LocalContext.current
    val drawable = remember(packageName) {
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
    }
    Box(contentAlignment = Alignment.Center) {
        AppIcon(avatarUrl = null, owner = label, size = 56.dp)
        if (drawable != null) {
            AsyncImage(
                model = drawable,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun ActiveRow(record: DownloadRecord, onCancel: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                record.filename,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                "${record.repoFullName} · ${record.releaseTag}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            val fraction = if (record.bytesTotal > 0) {
                (record.bytesDone.toFloat() / record.bytesTotal).coerceIn(0f, 1f)
            } else {
                null
            }
            if (fraction != null) {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                Text(
                    "${formatBytes(record.bytesDone)} of ${formatBytes(record.bytesTotal)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("${formatBytes(record.bytesDone)} downloaded", style = MaterialTheme.typography.bodySmall)
            }
            Row {
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    record: DownloadRecord,
    onRetry: () -> Unit,
    onForget: () -> Unit,
    onInstall: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                record.filename,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                "${record.repoFullName} · ${record.releaseTag}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            when (record.status) {
                "COMPLETED" -> {
                    Text(
                        verificationLine(record.verification),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    record.packageName?.let {
                        Text(
                            "Package $it${record.versionName?.let { v -> " · $v" } ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onInstall) { Text("Install") }
                        TextButton(onClick = onForget) { Text("Remove") }
                    }
                }
                "FAILED" -> {
                    Text(
                        record.error ?: "Download failed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onRetry) { Text("Retry") }
                        TextButton(onClick = onForget) { Text("Remove") }
                    }
                }
                else -> {
                    Text(record.status.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = onForget) { Text("Remove") }
                    }
                }
            }
        }
    }
}

/**
 * Honest checksum wording: a matching digest proves the bytes equal what the
 * release published — never authorship or safety (SECURITY_MODEL §3).
 */
private fun verificationLine(verification: String?): String = when (verification) {
    VerificationResult.CHECKED_OK.name ->
        "Checksum matches the published digest (proves equality with the release file, not authorship)."
    VerificationResult.MISMATCH.name -> "Checksum mismatch — file was discarded."
    else -> "No checksum published — validated by size and APK structure only."
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}
