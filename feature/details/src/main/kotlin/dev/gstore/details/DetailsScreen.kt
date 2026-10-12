package dev.gstore.details

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gstore.data.AssetKind
import dev.gstore.model.AbiConfidence
import dev.gstore.model.AbiType
import dev.gstore.model.Release
import dev.gstore.model.ReleaseAsset
import dev.gstore.model.Repository

@Composable
fun DetailsScreen(
    state: DetailsUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRefresh: () -> Unit,
    onSelectRelease: (Long) -> Unit,
    onDownload: (ReleaseAsset) -> Unit,
    onOpenReadme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notice = state.notice
    LaunchedEffect(notice) {
        if (notice != null) snackbar.showSnackbar(notice)
    }
    val repo = state.repo
    if (repo == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (state.loadingRepo || state.syncing) {
                CircularProgressIndicator()
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text(
                        "Could not load this repository.",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        state.notice ?: "It may have been removed or renamed on GitHub.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onBack) { Text("Back") }
                        Button(onClick = onRefresh) { Text("Retry") }
                    }
                }
            }
        }
        return
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item { TopBar(onBack, state.isFavorite, onToggleFavorite, state.syncing, onRefresh) }
        item { Spacer(Modifier.height(8.dp)) }
        item { Header(repo) }
        item { Spacer(Modifier.height(12.dp)) }
        item { ActionsRow(repo, onOpenReadme) }
        item { Spacer(Modifier.height(16.dp)) }
        item { SectionTitle("Releases (${state.releases.size})") }
        item { ReleasePicker(state.releases, state.selectedReleaseId, onSelectRelease) }
        item { Spacer(Modifier.height(8.dp)) }
        selectedRelease(state)?.let { release ->
            item { ReleaseNotes(release) }
            item { Spacer(Modifier.height(12.dp)) }
            item { SectionTitle("Artifacts (${state.assetRows.size})") }
            if (state.loadingAssets) {
                item { CircularProgressIndicator(Modifier.padding(8.dp)) }
            } else if (state.assetRows.isEmpty()) {
                item { Text("No downloadable artifacts on this release.") }
            } else {
                items(state.assetRows, key = { it.asset.id }) { row ->
                    AssetCard(row, onDownload)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
        item { SectionTitle("Release history") }
        items(state.releases, key = { it.id }) { release ->
            HistoryRow(release, selected = release.id == state.selectedReleaseId, onSelectRelease)
        }
    }
}

private fun selectedRelease(state: DetailsUiState): Release? =
    state.releases.firstOrNull { it.id == state.selectedReleaseId }

@Composable
private fun TopBar(
    onBack: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    syncing: Boolean,
    onRefresh: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (syncing) CircularProgressIndicator(Modifier.size(24.dp))
        else IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "Re-sync") }
    }
}

@Composable
private fun Header(repo: Repository) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        dev.gstore.ui.AppIcon(
            avatarUrl = repo.avatarUrl,
            owner = repo.owner,
            size = 56.dp,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text(
                repo.fullName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            val description = repo.description
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        formatCount(repo.stars),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
                Text(
                    "· ${formatCount(repo.forks)} forks",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (repo.archived) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            "Archived",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            val meta = buildList {
                repo.license?.let { add("License $it") }
                repo.updatedAt?.let { add("Updated ${it.take(10)}") }
            }
            if (meta.isNotEmpty()) {
                Text(
                    meta.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fk".format(n / 1_000.0)
    else -> n.toString()
}

@Composable
private fun ActionsRow(repo: Repository, onOpenReadme: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    // Equal weights + shared 48dp height: Repository / README / Share always
    // line up, whatever the label length.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { uriHandler.openUri("https://github.com/${repo.fullName}") },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) {
            Text("Repository", maxLines = 1)
        }
        OutlinedButton(
            onClick = onOpenReadme,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) {
            Text("README", maxLines = 1)
        }
        OutlinedButton(
            onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "https://github.com/${repo.fullName}")
                    },
                )
            },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) {
            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Share", maxLines = 1)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun ReleasePicker(
    releases: List<Release>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    if (releases.isEmpty()) {
        Text("No releases synced yet.")
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(releases, key = { it.id }) { release ->
            val selected = release.id == selectedId
            FilterChip(
                selected = selected,
                onClick = { onSelect(release.id) },
                label = {
                    Text(
                        release.tag + if (release.prerelease) " · pre" else "",
                        maxLines = 1,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun ReleaseNotes(release: Release) {
    val uriHandler = LocalUriHandler.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    release.tag,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                if (release.prerelease) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Prerelease",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            release.publishedAt?.let {
                Text("Published ${it.take(10)}", style = MaterialTheme.typography.labelMedium)
            }
            val releaseName = release.name
            if (releaseName != null) {
                Text(
                    releaseName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            val notes = release.notes
            if (!notes.isNullOrBlank()) {
                SelectionContainer {
                    Text(notes, style = MaterialTheme.typography.bodySmall)
                }
            }
            release.htmlUrl?.let { url ->
                TextButton(onClick = { uriHandler.openUri(url) }) { Text("Open release page") }
            }
        }
    }
}

@Composable
private fun AssetCard(row: AssetRow, onDownload: (ReleaseAsset) -> Unit) {
    val asset = row.asset
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Monospace filenames don't wrap mid-token — cap at two lines.
            Text(
                asset.filename,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                "${formatBytes(asset.size)} · ${asset.downloadCount} downloads",
                style = MaterialTheme.typography.labelMedium,
            )
            AbiLine(row)
            KindLine(row)
            ChecksumLine(asset)
            if (row.eligibility.eligible) {
                // Full-width 48dp action: every asset card ends on the same
                // aligned button, never a floating small one.
                Button(
                    onClick = { onDownload(asset) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Download", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun AbiLine(row: AssetRow) {
    val artifact = row.artifact
    if (!row.eligibility.eligible) return
    when (artifact.abi) {
        AbiType.UNKNOWN -> Text(
            "ABI unknown — compatibility with this device is not established",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
        )
        AbiType.UNIVERSAL -> Text(
            "Universal build (${artifact.confidence.name.lowercase()} confidence)",
            style = MaterialTheme.typography.labelMedium,
        )
        else -> Text(
            "${artifact.abi.name} (${artifact.confidence.name.lowercase()} confidence)",
            style = MaterialTheme.typography.labelMedium,
        )
    }
    if (artifact.confidence == AbiConfidence.LOW || artifact.confidence == AbiConfidence.UNKNOWN) {
        Text(
            "Filename-based guess only — inspect the APK before installing",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun KindLine(row: AssetRow) {
    val label = when (row.eligibility.kind) {
        AssetKind.APK -> return
        AssetKind.BUNDLE -> "Bundle format — needs split-APK installer support, not directly installable"
        AssetKind.ARCHIVE -> "Archive — not an installable artifact"
        AssetKind.OTHER -> "Not an installable artifact"
    }
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
}

@Composable
private fun ChecksumLine(asset: ReleaseAsset) {
    val digest = asset.digest
    if (digest == null) {
        Text("Checksum unavailable for this artifact", style = MaterialTheme.typography.labelSmall)
    } else {
        Text(
            "Published checksum (unverified): ${digest.take(24)}…",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun HistoryRow(release: Release, selected: Boolean, onSelect: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                release.tag,
                fontWeight = if (selected) FontWeight.Bold else null,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    release.publishedAt?.take(10),
                    if (release.prerelease) "prerelease" else null,
                ).joinToString(" · ").ifEmpty { "undated" },
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        if (!selected) {
            AssistChip(onClick = { onSelect(release.id) }, label = { Text("View") })
        }
    }
}
