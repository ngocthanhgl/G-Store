package dev.gstore.details

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gstore.data.AssetKind
import dev.gstore.database.DownloadRecord
import dev.gstore.model.Release
import dev.gstore.model.ReleaseAsset
import dev.gstore.model.Repository
import dev.gstore.ui.Block
import dev.gstore.ui.BlockView

@Composable
fun DetailsScreen(
    state: DetailsUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRefresh: () -> Unit,
    onSelectRelease: (Long) -> Unit,
    onHeroDownload: (ReleaseAsset) -> Unit,
    onHeroCancel: (Long) -> Unit,
    onHeroInstall: (DownloadRecord) -> Unit,
    onHeroUninstall: (String) -> Unit,
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
    // APK-only scope: releases and rows without an eligible APK are hidden.
    // Before the scan finishes apkReleaseIds is empty and everything shows.
    val apkReleases = if (state.apkReleaseIds.isEmpty()) state.releases
    else state.releases.filter { it.id in state.apkReleaseIds }
    val apkRows = state.assetRows.filter {
        it.eligibility.eligible && it.eligibility.kind == AssetKind.APK
    }
    Column(modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        // The back/favorite row sits below the status bar; the hero below
        // it bleeds edge-to-edge under the camera hole.
        item {
            TopBar(
                onBack,
                state.isFavorite,
                onToggleFavorite,
                state.syncing,
                onRefresh,
                modifier = Modifier.statusBarsPadding(),
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
        item { Header(repo) }
        item { Spacer(Modifier.height(12.dp)) }
        item { ActionsRow(repo, onOpenReadme) }
        item { Spacer(Modifier.height(12.dp)) }
        selectedRelease(state)?.let { release ->
            val asset = apkRows.firstOrNull()?.asset
            item {
                HeroDownloadBar(
                    hero = state.hero,
                    tag = release.tag,
                    asset = asset,
                    onDownload = onHeroDownload,
                    onCancel = onHeroCancel,
                    onInstall = onHeroInstall,
                    onUninstall = onHeroUninstall,
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
        val description = repo.description
        if (description != null) {
            item { InfoCard(description) }
            item { Spacer(Modifier.height(12.dp)) }
        }
        item { SectionTitle("Releases (${apkReleases.size})") }
        item { ReleasePicker(apkReleases, state.selectedReleaseId, onSelectRelease) }
        item { Spacer(Modifier.height(8.dp)) }
        selectedRelease(state)?.let { release ->
            item { ReleaseNotes(release) }
            item { Spacer(Modifier.height(12.dp)) }
            item { SectionTitle("APK artifacts (${apkRows.size})") }
            if (state.loadingAssets) {
                item { CircularProgressIndicator(Modifier.padding(8.dp)) }
            } else if (apkRows.isEmpty()) {
                item { Text("No APK artifacts on this release.") }
            } else {
                items(apkRows, key = { it.asset.id }) { row ->
                    AssetCard(row)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        if (state.readmeBlocks.isNotEmpty()) {
            item { Spacer(Modifier.height(12.dp)) }
            item {
                ReadmeCard(
                    blocks = state.readmeBlocks,
                    owner = repo.owner,
                    name = repo.fullName.substringAfter('/'),
                    onOpenFull = onOpenReadme,
                )
            }
        }
    }
}
}

private fun selectedRelease(state: DetailsUiState): Release? =
    state.releases.firstOrNull { it.id == state.selectedReleaseId }

/**
 * Tap-to-expand info: three lines collapsed, full text expanded. The whole
 * card is the toggle — no position, no button.
 */
@Composable
private fun InfoCard(description: String) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = if (expanded) {
                androidx.compose.ui.text.style.TextOverflow.Visible
            } else {
                androidx.compose.ui.text.style.TextOverflow.Ellipsis
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(12.dp),
        )
    }
}

/**
 * Inline README preview with the same renderer as the full screen: a short
 * window collapsed, up to 60 blocks expanded, full screen one tap away.
 * Tapping anywhere on the card toggles.
 */
@Composable
private fun ReadmeCard(
    blocks: List<Block>,
    owner: String,
    name: String,
    onOpenFull: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (expanded) "README — tap to collapse" else "README — tap to expand",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
            )
            val shown = if (expanded) blocks.take(60) else blocks.take(4)
            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    shown.forEach { BlockView(it, owner, name) }
                }
                TextButton(onClick = onOpenFull) { Text("Open full README") }
            } else {
                Box(Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        shown.forEach { BlockView(it, owner, name) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    onBack: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    syncing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
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
    // Store hero: big squircle icon, app name, owner in the theme accent,
    // full coordinates in gray — the Play-shelf reading order.
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        dev.gstore.ui.AppIcon(
            avatarUrl = repo.avatarUrl,
            owner = repo.owner,
            size = 72.dp,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(
                repo.fullName.substringAfter('/'),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                repo.owner,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                repo.fullName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            // Description lives in the expandable info card below, not here.
            // Divider-separated stats in the store idiom: popularity and
            // freshness at a glance, each cell truncating, never wrapping.
            StatsRow(
                stars = formatCount(repo.stars),
                forks = "${formatCount(repo.forks)} forks",
                updated = repo.updatedAt?.take(10),
                archived = repo.archived,
            )
            repo.license?.let {
                Text(
                    "License $it",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Store stats row: cells divided by accent bars, replacing the old ledger. */
@Composable
private fun StatsRow(stars: String, forks: String, updated: String?, archived: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp),
    ) {
        StatCell(
            text = "★ $stars",
            modifier = Modifier.weight(1f),
        )
        StatDivider()
        StatCell(
            text = forks,
            modifier = Modifier.weight(1f),
        )
        StatDivider()
        StatCell(
            text = when {
                archived -> "Archived"
                updated != null -> "Updated $updated"
                else -> "Undated"
            },
            modifier = Modifier.weight(1f),
            alert = archived,
        )
    }
}

@Composable
private fun StatCell(
    text: String,
    modifier: Modifier = Modifier,
    alert: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (alert) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .size(width = 1.dp, height = 16.dp)
            .background(MaterialTheme.colorScheme.primary),
    )
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
private fun AssetCard(row: AssetRow) {
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
                formatBytes(asset.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * Hero download bar for the recommended APK of the selected release.
 * Small text throughout; progress, install and uninstall stay on this
 * screen — nothing navigates away except the system installer itself.
 */
@Composable
private fun HeroDownloadBar(
    hero: HeroDlState,
    tag: String,
    asset: ReleaseAsset?,
    onDownload: (ReleaseAsset) -> Unit,
    onCancel: (Long) -> Unit,
    onInstall: (DownloadRecord) -> Unit,
    onUninstall: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Recommended APK · $tag",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            when (hero) {
                is HeroDlState.IdleDownload -> {
                    val target = asset
                    Button(
                        onClick = { if (target != null) onDownload(target) },
                        enabled = target != null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(
                            if (target != null) "Download ${formatBytes(target.size)}"
                            else "No APK on this release",
                            maxLines = 1,
                        )
                    }
                }
                is HeroDlState.Downloading -> {
                    val fraction = if (hero.total > 0) {
                        (hero.done.toFloat() / hero.total).coerceIn(0f, 1f)
                    } else null
                    if (fraction != null) {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${formatBytes(hero.done)} of ${formatBytes(hero.total)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        val target = asset
                        TextButton(onClick = { if (target != null) onCancel(target.id) }) {
                            Text("Cancel")
                        }
                    }
                }
                is HeroDlState.ReadyInstall -> {
                    Button(
                        onClick = { onInstall(hero.record) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text("Install", maxLines = 1)
                    }
                }
                is HeroDlState.Installed -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Installed" + (hero.versionName?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onUninstall(hero.packageName) }) {
                            Text("Uninstall")
                        }
                    }
                }
                is HeroDlState.FailedRetry -> {
                    val target = asset
                    OutlinedButton(
                        onClick = { if (target != null) onDownload(target) },
                        enabled = target != null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text("Retry download", maxLines = 1)
                    }
                }
            }
        }
    }
}
