package dev.gstore.downloads
import dev.gstore.ui.Fluent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.gstore.data.UpdateState
import dev.gstore.database.DownloadRecord
import dev.gstore.model.Repository
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
    onUninstall: (String) -> Unit,
    onDownloadFavorite: (Repository) -> Unit,
    onRemoveManual: (String) -> Unit,
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
    // Manual-only refresh: entering this screen never fires a check on its
    // own. Pull-to-refresh, the header button, or the empty-state button
    // below are the only triggers (throttled when anonymous, free when
    // logged in).
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
        if (state.installed.isEmpty() && state.active.isEmpty() && state.history.isEmpty() &&
            state.favorites.isEmpty() && state.manual.isEmpty()
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                EmptyContent(
                    message = "Nothing here yet. Pick an APK on an app's details screen.",
                    icon = Fluent.ArrowDownload,
                )
                OutlinedButton(
                    onClick = onRefreshInstalled,
                    enabled = !state.installedRefreshing,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text(if (state.installedRefreshing) "Checking..." else "Check now")
                }
            }
            return@PullToRefreshBox
        }
        val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Downloads",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onRefreshInstalled) {
                        Icon(Fluent.ArrowSync, contentDescription = "Check for updates")
                    }
                }
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
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
            if (state.favorites.isNotEmpty()) {
                item { SectionTitle("Favourites (${state.favorites.size})") }
                items(state.favorites, key = { "f-${it.id}" }) { repo ->
                    FavoriteRow(
                        repo = repo,
                        onBrowse = { onBrowseRepo(repo.fullName) },
                        onDownload = { onDownloadFavorite(repo) },
                    )
                }
            }
            if (state.manual.isNotEmpty()) {
                item { SectionTitle("Added by you (${state.manual.size})") }
                items(state.manual, key = { "a-${it.id}" }) { repo ->
                    ManualRow(
                        repo = repo,
                        onBrowse = { onBrowseRepo(repo.fullName) },
                        onRemove = { onRemoveManual(repo.fullName) },
                    )
                }
            }
            if (updates.isNotEmpty()) {
                item { SectionTitle("Updates available (${updates.size})") }
                items(updates, key = { "u-${it.record.assetId}" }) { row ->
                    InstalledRow(
                        row = row,
                        onBrowseRepo = onBrowseRepo,
                        onLaunch = onLaunch,
                        onUninstall = onUninstall,
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
                        onUninstall = onUninstall,
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
                        onUninstall = onUninstall,
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
    onUninstall: (String) -> Unit,
    onInstall: (DownloadRecord) -> Unit,
) {
    val record = row.record
    // Cardless row: the list already insets 16dp, so the row only pads vertically.
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = { onBrowseRepo(record.repoFullName) })
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PackageIcon(
            packageName = record.packageName ?: record.repoFullName,
            label = row.label,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
                val versionLine = if (row.installedVersionName != null) {
                    val latest = row.latestTag?.let { " → $it" } ?: ""
                    "${row.installedVersionName}${row.installedVersionCode?.let { " ($it)" } ?: ""}$latest"
                } else {
                    "Not installed on this device"
                }
                Text(
                    text = versionLine,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                val sizeBytes = record.bytesTotal.takeIf { it > 0 }
                    ?: record.expectedSize.takeIf { it > 0 }
                val metaParts = buildList {
                    sizeBytes?.let { add(formatBytes(it)) }
                    if (row.lastUpdateTime > 0) {
                        add(
                            java.text.DateFormat.getDateInstance()
                                .format(java.util.Date(row.lastUpdateTime)),
                        )
                    }
                    if (record.releaseTag.isNotBlank()) add(record.releaseTag)
                }
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = record.repoFullName,
                    style = MaterialTheme.typography.labelSmall,
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
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.End,
            ) {
                when (row.updateState) {
                    UpdateState.UPDATE_AVAILABLE -> Button(
                        onClick = { onBrowseRepo(record.repoFullName) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Update") }
                    UpdateState.READY_TO_INSTALL -> Button(
                        onClick = { onInstall(record) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Install") }
                    UpdateState.NOT_INSTALLED -> Button(
                        onClick = { onInstall(record) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Install") }
                    UpdateState.UP_TO_DATE -> Unit
                }
                if (row.installedVersionCode != null) {
                    TextButton(
                        onClick = { onLaunch(record.packageName ?: "") },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("Open")
                    }
                    TextButton(
                        onClick = { onUninstall(record.packageName ?: "") },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("Uninstall")
                    }
                }
            }
        }
}

/** Hearted repo with a one-tap APK download (latest stable eligible asset wins). */
@Composable
private fun FavoriteRow(
    repo: Repository,
    onBrowse: () -> Unit,
    onDownload: () -> Unit,
) {
    // Cardless row (see InstalledRow): vertical padding only.
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onBrowse)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(avatarUrl = repo.avatarUrl, owner = repo.owner, size = 56.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
                Text(
                    text = repo.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = "${repo.stars} stars",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Button(
                onClick = onDownload,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Download") }
        }
}

/** Hand-added repo (link or owner/name): browseable, removable, no download yet. */
@Composable
private fun ManualRow(
    repo: Repository,
    onBrowse: () -> Unit,
    onRemove: () -> Unit,
) {
    // Cardless row (see InstalledRow): vertical padding only.
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onBrowse)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(avatarUrl = repo.avatarUrl, owner = repo.owner, size = 56.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
                Text(
                    text = repo.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = if (repo.archived) "Archived on GitHub" else "${repo.stars} stars",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            TextButton(
                onClick = onRemove,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Remove") }
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
    // No own top gap: the list separates items with a uniform 16dp gap.
    Text(text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun ActiveRow(record: DownloadRecord, onCancel: () -> Unit) {
    // Cardless status block: vertical padding only, 16dp rhythm inside.
    Column(
        Modifier.fillMaxWidth().padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Cancel") }
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
    // Cardless status block (see ActiveRow): vertical padding only.
    Column(
        Modifier.fillMaxWidth().padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = onInstall,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Install") }
                        TextButton(
                            onClick = onForget,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Remove") }
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
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedButton(
                            onClick = onRetry,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Retry") }
                        TextButton(
                            onClick = onForget,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Remove") }
                    }
                }
                else -> {
                    Text(record.status.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = onForget,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Remove") }
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


