package dev.gstore.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gstore.database.DownloadRecord
import dev.gstore.model.VerificationResult
import dev.gstore.ui.EmptyContent

/**
 * Download queue + history (flow C). Progress survives Activity recreation
 * (Room-backed); cancellation and retry act on the record, never on a lost
 * callback. A COMPLETED row offers installation handoff via [onInstall].
 */
@Composable
fun DownloadsScreen(
    state: DownloadsUiState,
    onCancel: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onForget: (Long) -> Unit,
    onInstall: (DownloadRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.active.isEmpty() && state.history.isEmpty()) {
        EmptyContent("No downloads yet. Pick an APK on an app's details screen.")
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun ActiveRow(record: DownloadRecord, onCancel: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(record.filename, style = MaterialTheme.typography.titleSmall)
            Text(
                "${record.repoFullName} · ${record.releaseTag}",
                style = MaterialTheme.typography.bodySmall,
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
            Text(record.filename, style = MaterialTheme.typography.titleSmall)
            Text(
                "${record.repoFullName} · ${record.releaseTag}",
                style = MaterialTheme.typography.bodySmall,
            )
            when (record.status) {
                "COMPLETED" -> {
                    Text(
                        verificationLine(record.verification),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    record.packageName?.let {
                        Text("Package $it${record.versionName?.let { v -> " · $v" } ?: ""}", style = MaterialTheme.typography.bodySmall)
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
