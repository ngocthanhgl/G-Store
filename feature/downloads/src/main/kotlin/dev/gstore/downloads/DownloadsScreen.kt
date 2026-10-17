package dev.gstore.downloads
import dev.gstore.ui.Fluent

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.gstore.data.UpdateState
import dev.gstore.database.DownloadRecord
import dev.gstore.model.Repository
import dev.gstore.model.VerificationResult
import dev.gstore.ui.AppIcon
import dev.gstore.ui.AuroraSectionHeader
import dev.gstore.ui.EmptyContent
import dev.gstore.ui.GStoreSpace
import dev.gstore.ui.ShimmerRows
import dev.gstore.ui.squircleDp

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
    reduceMotion: Boolean = false,
    onCancel: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onForget: (Long) -> Unit,
    onInstall: (DownloadRecord) -> Unit,
    onBrowseRepo: (String) -> Unit,
    onLaunch: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onDownloadUpdate: (DownloadRecord) -> Unit,
    onDownloadFavorite: (Repository) -> Unit,
    onRemoveManual: (String) -> Unit,
    onRefreshInstalled: () -> Unit,
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
                    modifier = Modifier.heightIn(min = GStoreSpace.TouchTarget),
                ) { Text("Log in to GitHub") }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissLoginPrompt,
                    modifier = Modifier.heightIn(min = GStoreSpace.TouchTarget),
                ) { Text("Later") }
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
            // A check is in flight: show the rows it is about to fill rather
            // than an empty shelf that reads as "you have nothing".
            if (state.installedRefreshing) {
                ShimmerRows(
                    count = 5,
                    reduceMotion = reduceMotion,
                    modifier = Modifier.padding(horizontal = GStoreSpace.Gutter),
                )
                return@PullToRefreshBox
            }
            EmptyContent(
                message = "Nothing here yet. Pick an APK on an app's details screen.",
                icon = Fluent.ArrowDownload,
                action = {
                    OutlinedButton(
                        onClick = onRefreshInstalled,
                        enabled = !state.installedRefreshing,
                        modifier = Modifier.heightIn(min = GStoreSpace.TouchTarget),
                    ) {
                        Text(if (state.installedRefreshing) "Checking..." else "Check now")
                    }
                },
            )
            return@PullToRefreshBox
        }
        val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = GStoreSpace.Gutter),
            verticalArrangement = Arrangement.spacedBy(GStoreSpace.Small),
        ) {
            item {
                AuroraSectionHeader(
                    title = "Downloads",
                    topGap = false,
                    trailing = {
                        IconButton(onClick = onRefreshInstalled) {
                            Icon(Fluent.ArrowSync, contentDescription = "Check for updates")
                        }
                    },
                )
            }
            if (state.favorites.isNotEmpty()) {
                item {
                    AuroraSectionHeader(
                        title = "Favourites",
                        count = state.favorites.size,
                    )
                }
                items(state.favorites, key = { "f-${it.id}" }) { repo ->
                    FavoriteRow(
                        repo = repo,
                        onBrowse = { onBrowseRepo(repo.fullName) },
                        onDownload = { onDownloadFavorite(repo) },
                    )
                }
            }
            if (state.manual.isNotEmpty()) {
                item {
                    AuroraSectionHeader(
                        title = "Added by you",
                        count = state.manual.size,
                    )
                }
                items(state.manual, key = { "a-${it.id}" }) { repo ->
                    ManualRow(
                        repo = repo,
                        onBrowse = { onBrowseRepo(repo.fullName) },
                        onRemove = { onRemoveManual(repo.fullName) },
                    )
                }
            }
            if (updates.isNotEmpty()) {
                item {
                    AuroraSectionHeader(
                        title = "Updates available",
                        count = updates.size,
                    )
                }
                items(updates, key = { "u-${it.record.assetId}" }) { row ->
                    InstalledRow(
                        row = row,
                        onBrowseRepo = onBrowseRepo,
                        onLaunch = onLaunch,
                        onUninstall = onUninstall,
                        onInstall = onInstall,
                        onDownloadUpdate = onDownloadUpdate,
                    )
                }
            }
            if (current.isNotEmpty()) {
                item {
                    AuroraSectionHeader(
                        title = "Installed via G Store",
                        count = current.size,
                    )
                }
                items(current, key = { "i-${it.record.assetId}" }) { row ->
                    InstalledRow(
                        row = row,
                        onBrowseRepo = onBrowseRepo,
                        onLaunch = onLaunch,
                        onUninstall = onUninstall,
                        onInstall = onInstall,
                        onDownloadUpdate = onDownloadUpdate,
                    )
                }
            }
            if (missing.isNotEmpty()) {
                item {
                    AuroraSectionHeader(
                        title = "Not on this device",
                        count = missing.size,
                    )
                }
                items(missing, key = { "m-${it.record.assetId}" }) { row ->
                    InstalledRow(
                        row = row,
                        onBrowseRepo = onBrowseRepo,
                        onLaunch = onLaunch,
                        onUninstall = onUninstall,
                        onInstall = onInstall,
                        onDownloadUpdate = onDownloadUpdate,
                    )
                }
            }
            if (state.active.isNotEmpty()) {
                item {
                    AuroraSectionHeader(
                        title = "Active",
                        count = state.active.size,
                    )
                }
                items(state.active, key = { it.assetId }) { record ->
                    ActiveRow(record = record, onCancel = { onCancel(record.assetId) })
                }
            }
            if (state.history.isNotEmpty()) {
                item {
                    AuroraSectionHeader(
                        title = "History",
                        count = state.history.size,
                    )
                }
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
    onDownloadUpdate: (DownloadRecord) -> Unit,
) {
    val record = row.record
    val installed = row.installedVersionCode != null
    // Cardless row: the list already insets the gutter, so the row only pads
    // vertically. Anatomy is shared with FavoriteRow / ManualRow — tile,
    // GStoreSpace.Medium to the text column, GStoreSpace.Small inside it.
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = { onBrowseRepo(record.repoFullName) })
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GStoreSpace.Medium),
    ) {
        PackageIcon(
            packageName = record.packageName ?: record.repoFullName,
            label = row.label,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(GStoreSpace.Small),
        ) {
            // Line 1: app label.
            Text(
                text = row.label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Line 2: status + version, single ellipsis line. The separator is
            // only rendered when a version actually exists — never "Installed · ".
            val statusLine = when (row.updateState) {
                UpdateState.UPDATE_AVAILABLE ->
                    "Update available${row.latestTag?.let { " · $it" } ?: ""}"
                UpdateState.READY_TO_INSTALL -> "Downloaded update ready to install"
                UpdateState.UP_TO_DATE -> {
                    val version = row.installedVersionName ?: row.installedVersionCode?.toString()
                    if (version != null) "Installed · $version" else "Installed"
                }
                UpdateState.NOT_INSTALLED -> "Not installed on this device"
            }
            Text(
                text = statusLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Trailing column: at most TWO controls, so a row never towers over its
        // neighbours (three stacked buttons made this column 104-160dp). The
        // primary action wins the first slot; "Uninstall" is the third control
        // that gets dropped when a primary is present.
        Column(
            verticalArrangement = Arrangement.spacedBy(GStoreSpace.Small),
            horizontalAlignment = Alignment.End,
        ) {
            val primaryShown = when (row.updateState) {
                UpdateState.UPDATE_AVAILABLE -> {
                    OutlinedButton(
                        onClick = { onDownloadUpdate(record) },
                        modifier = Modifier.heightIn(min = GStoreSpace.TouchTarget),
                    ) { Text("Update") }
                    true
                }
                UpdateState.READY_TO_INSTALL, UpdateState.NOT_INSTALLED -> {
                    // Filled, 48dp — identical to HistoryRow's Install.
                    Button(
                        onClick = { onInstall(record) },
                        modifier = Modifier.heightIn(min = GStoreSpace.TouchTarget),
                    ) { Text("Install") }
                    true
                }
                UpdateState.UP_TO_DATE -> false
            }
            if (installed && !primaryShown) {
                TextButton(
                    onClick = { onLaunch(record.packageName ?: "") },
                    modifier = Modifier.heightIn(min = GStoreSpace.RowAction),
                ) {
                    Text("Open")
                }
            }
            if (installed) {
                TextButton(
                    onClick = { onUninstall(record.packageName ?: "") },
                    modifier = Modifier.heightIn(min = GStoreSpace.RowAction),
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
    // Cardless row (see InstalledRow): vertical padding only, shared anatomy.
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onBrowse)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GStoreSpace.Medium),
    ) {
        AppIcon(avatarUrl = repo.avatarUrl, owner = repo.owner, size = GStoreSpace.Tile)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(GStoreSpace.Small),
        ) {
            Text(
                text = repo.fullName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${repo.stars} stars",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // One-tap primary: 48dp, like every other primary action on the screen.
        Button(
            onClick = onDownload,
            modifier = Modifier.heightIn(min = GStoreSpace.TouchTarget),
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
    // Cardless row (see InstalledRow): vertical padding only, shared anatomy.
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onBrowse)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GStoreSpace.Medium),
    ) {
        AppIcon(avatarUrl = repo.avatarUrl, owner = repo.owner, size = GStoreSpace.Tile)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(GStoreSpace.Small),
        ) {
            Text(
                text = repo.fullName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (repo.archived) "Archived on GitHub" else "${repo.stars} stars",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Tertiary text action: stays compact at GStoreSpace.RowAction.
        TextButton(
            onClick = onRemove,
            modifier = Modifier.heightIn(min = GStoreSpace.RowAction),
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
        AppIcon(avatarUrl = null, owner = label, size = GStoreSpace.Tile)
        if (drawable != null) {
            // Forced to the common squircle: the overlay must carry the same
            // clip as the AppIcon well beneath it, or square corners show.
            AsyncImage(
                model = drawable,
                contentDescription = null,
                modifier = Modifier.size(GStoreSpace.Tile).clip(
                    RoundedCornerShape(squircleDp(GStoreSpace.Tile)),
                ),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/**
 * Tile corner radius, snapped to the 4dp grid by core:ui's shared
 * [squircleDp] so the PackageManager icon overlay always matches the
 * monogram well beneath it.
 */

@Composable
private fun ActiveRow(record: DownloadRecord, onCancel: () -> Unit) {
    // Cardless row like every other row here: vertical padding only, and the
    // same 4dp text rhythm. No leading icon — the filename is the anchor.
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(GStoreSpace.Small),
    ) {
            Text(
                record.filename,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${record.repoFullName} · ${record.releaseTag}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    "${formatBytes(record.bytesDone)} downloaded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.heightIn(min = GStoreSpace.RowAction),
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
    // Cardless row (see ActiveRow): vertical padding only, shared rhythm.
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(GStoreSpace.Small),
    ) {
            Text(
                record.filename,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${record.repoFullName} · ${record.releaseTag}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when (record.status) {
                "COMPLETED" -> {
                    Text(
                        verificationLine(record.verification),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    record.packageName?.let {
                        Text(
                            "Package $it${record.versionName?.let { v -> " · $v" } ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(GStoreSpace.Small)) {
                        // Filled, 48dp — the one "Install" look on this screen.
                        Button(
                            onClick = onInstall,
                            modifier = Modifier.heightIn(min = GStoreSpace.TouchTarget),
                        ) { Text("Install") }
                        TextButton(
                            onClick = onForget,
                            modifier = Modifier.heightIn(min = GStoreSpace.RowAction),
                        ) { Text("Remove") }
                    }
                }
                "FAILED" -> {
                    Text(
                        record.error ?: "Download failed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(GStoreSpace.Small)) {
                        OutlinedButton(
                            onClick = onRetry,
                            modifier = Modifier.heightIn(min = GStoreSpace.RowAction),
                        ) { Text("Retry") }
                        TextButton(
                            onClick = onForget,
                            modifier = Modifier.heightIn(min = GStoreSpace.RowAction),
                        ) { Text("Remove") }
                    }
                }
                else -> {
                    Text(
                        text = record.status.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = onForget,
                            modifier = Modifier.heightIn(min = GStoreSpace.RowAction),
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


