package dev.gstore.details
import dev.gstore.ui.Fluent

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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.gstore.data.AssetKind
import dev.gstore.database.DownloadRecord
import dev.gstore.designsystem.panelShape
import dev.gstore.model.Release
import dev.gstore.model.ReleaseAsset
import dev.gstore.model.Repository
import dev.gstore.ui.Block
import dev.gstore.ui.BlockView
import dev.gstore.ui.ShimmerRows
import dev.gstore.ui.parseMarkdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    canOpenApp: (String) -> Boolean,
    onOpenApp: (String) -> Unit,
    onOpenReadme: () -> Unit,
    cardRadiusDp: Int = 12,
    reduceMotion: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val notice = state.notice
    LaunchedEffect(notice) {
        if (notice != null) snackbar.showSnackbar(notice)
    }
    val context = LocalContext.current
    val repo = state.repo
    if (repo == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (state.loadingRepo || state.syncing) {
                CircularProgressIndicator()
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TextButton(
                            onClick = onBack,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Back") }
                        Button(
                            onClick = onRefresh,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Retry") }
                    }
                }
            }
        }
        return
    }
    // APK-only scope: releases without an eligible APK are hidden. Before the
    // scan finishes apkReleaseIds is empty and everything shows.
    val apkReleases = if (state.apkReleaseIds.isEmpty()) state.releases
    else state.releases.filter { it.id in state.apkReleaseIds }
    val heroAsset = state.assetRows.firstOrNull {
        it.eligibility.eligible && it.eligibility.kind == AssetKind.APK
    }?.asset
    Column(modifier.fillMaxSize()) {
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        // Back / share / favorite / refresh live in one top row; the hero
        // below it bleeds edge-to-edge under the camera hole.
        item {
            TopBar(
                onBack = onBack,
                onShare = {
                    context.startActivity(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "https://github.com/${repo.fullName}")
                        },
                    )
                },
                isFavorite = state.isFavorite,
                onToggleFavorite = onToggleFavorite,
                syncing = state.syncing,
                onRefresh = onRefresh,
                // No horizontal padding: the list already insets 16dp.
                modifier = Modifier.statusBarsPadding(),
            )
        }
        item { Spacer(Modifier.height(12.dp)) }
        item { Header(repo) }
        item { Spacer(Modifier.height(12.dp)) }
        item {
            StatsRow(
                stars = formatCount(repo.stars),
                forks = "${formatCount(repo.forks)} forks",
                updated = repo.updatedAt?.take(10),
                archived = repo.archived,
            )
        }
        item { Spacer(Modifier.height(12.dp)) }
        selectedRelease(state)?.let { release ->
            item {
                HeroDownloadBar(
                    hero = state.hero,
                    tag = release.tag,
                    asset = heroAsset,
                    onDownload = onHeroDownload,
                    onCancel = onHeroCancel,
                    onInstall = onHeroInstall,
                    onUninstall = onHeroUninstall,
                    canOpenApp = canOpenApp,
                    onOpenApp = onOpenApp,
                    cardRadiusDp = cardRadiusDp,
                    findingApk = state.syncing || state.loadingAssets || state.scanningApks,
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
        val description = repo.description
        if (description != null) {
            item { InfoCard(description, cardRadiusDp) }
            item { Spacer(Modifier.height(12.dp)) }
        }
        item { InfoGrid(repo, cardRadiusDp) }
        item { Spacer(Modifier.height(12.dp)) }
        val busy = state.syncing || state.loadingRepo || state.scanningApks
        val syncError = state.syncError
        item { SectionTitle("Releases (${apkReleases.size})") }
        if (apkReleases.isEmpty() && busy && syncError == null) {
            // Still arriving: shimmer instead of "no releases" copy.
            item { ShimmerRows(count = 2, reduceMotion = reduceMotion) }
        } else {
            item { ReleasePicker(apkReleases, state.selectedReleaseId, onSelectRelease) }
        }
        if (syncError != null) {
            item { Spacer(Modifier.height(12.dp)) }
            item {
                SyncErrorCard(syncError, onRefresh, cardRadiusDp)
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
        selectedRelease(state)?.let { release ->
            item { ReleaseNotes(release, cardRadiusDp, reduceMotion) }
            item { Spacer(Modifier.height(12.dp)) }
        }
        item {
            FilterChip(
                selected = false,
                onClick = onOpenReadme,
                label = {
                    Text(
                        "README",
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                },
                trailingIcon = { Icon(Fluent.ChevronRight, contentDescription = null) },
                shape = CircleShape,
            )
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
private fun InfoCard(description: String, cardRadiusDp: Int) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(shape = panelShape(), modifier = Modifier.fillMaxWidth()) {
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

/**
 * Everything fetchable about the repo, next to the header icon visually:
 * language, issues, watchers, license, freshness, branch, plus the
 * repository and homepage links (the old round buttons live on as rows).
 */
@Composable
private fun InfoGrid(repo: Repository, cardRadiusDp: Int) {
    val uriHandler = LocalUriHandler.current
    val cells = listOfNotNull(
        repo.language?.let { "Language" to it },
        repo.openIssues?.let { "Open issues" to it.toString() },
        repo.watchers?.let { "Watchers" to it.toString() },
        repo.license?.let { "License" to it },
        "Branch" to repo.defaultBranch,
    )
    Card(shape = panelShape(), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            cells.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, value) ->
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            Text(
                                value,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            TextButton(
                onClick = { uriHandler.openUri("https://github.com/${repo.fullName}") },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Repository on GitHub", maxLines = 1)
            }
            repo.homepage?.takeIf { it.isNotBlank() }?.let { home ->
                val url = if (home.startsWith("http")) home else "https://$home"
                TextButton(
                    onClick = { uriHandler.openUri(url) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        home,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    onBack: () -> Unit,
    onShare: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    syncing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconButton(onClick = onBack) { Icon(Fluent.ArrowLeft, contentDescription = "Back") }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onShare) { Icon(Fluent.Share, contentDescription = "Share") }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (isFavorite) Fluent.Heart else Fluent.Heart,
                contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (syncing) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp))
            }
        } else IconButton(onClick = onRefresh) { Icon(Fluent.ArrowSync, contentDescription = "Re-sync") }
    }
}

@Composable
private fun Header(repo: Repository) {
    // Minimal centered hero: artwork, name, owner. Stats live in their own
    // padded block below so nothing crowds the title; the license row moved
    // to the info grid (it was duplicated here).
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        dev.gstore.ui.AppIcon(
            avatarUrl = repo.avatarUrl,
            owner = repo.owner,
            size = 72.dp,
        )
        Text(
            repo.fullName.substringAfter('/'),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            repo.owner,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Store stats row: cells divided by accent bars, replacing the old ledger. */
@Composable
private fun StatsRow(stars: String, forks: String, updated: String?, archived: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatCell(
            text = stars,
            modifier = Modifier.weight(1f),
            icon = Fluent.Star,
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
    icon: Painter? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (alert) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
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
private fun SectionTitle(text: String) {
    // Same top gap as the Downloads screen's SectionTitle: count labels
    // start at the same height for identical hierarchy on both screens.
    // No own top gap: every call site already separates with a 16dp spacer.
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
    )
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
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(releases, key = { it.id }) { release ->
            val selected = release.id == selectedId
            FilterChip(
                selected = selected,
                onClick = { onSelect(release.id) },
                label = {
                    Text(
                        release.tag + if (release.prerelease) " · pre" else "",
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

/**
 * A failed on-demand sync must never render as complete-but-empty data.
 * Inline card with the reason and a retry, next to the release list.
 */
@Composable
private fun SyncErrorCard(message: String, onRetry: () -> Unit, cardRadiusDp: Int) {
    Card(shape = panelShape(), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Could not refresh release data.",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Retry")
            }
        }
    }
}

/**
 * Changelog: collapsed to tag + date with a chevron, full markdown on tap.
 * The whole card is the toggle.
 */
@Composable
private fun ReleaseNotes(release: Release, cardRadiusDp: Int, reduceMotion: Boolean) {
    val uriHandler = LocalUriHandler.current
    var expanded by rememberSaveable(release.id) { mutableStateOf(false) }
    Card(shape = panelShape(), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildString {
                        append(release.tag)
                        if (release.prerelease) append(" · pre")
                        release.publishedAt?.take(10)?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Fluent.ChevronDown else Fluent.ChevronRight,
                    contentDescription = if (expanded) "Collapse changelog" else "Expand changelog",
                )
            }
            if (expanded) {
                val releaseName = release.name
                // The tag already heads this card: repeat the name only when
                // it actually says something different.
                if (releaseName != null && releaseName != release.tag) {
                    Text(
                        releaseName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                val notes = release.notes
                if (notes.isNullOrBlank()) {
                    Text(
                        "No changelog text on this release.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    // Parsed off the main thread; shimmer until ready.
                    var blocks by remember(release.id) { mutableStateOf<List<Block>?>(null) }
                    LaunchedEffect(release.id, notes) {
                        blocks = withContext(Dispatchers.Default) { parseMarkdown(notes) }
                    }
                    val parsed = blocks
                    if (parsed == null) {
                        ShimmerRows(count = 2, reduceMotion = reduceMotion)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            parsed.forEach { BlockView(it, "", "") }
                        }
                    }
                }
                release.htmlUrl?.let { url ->
                    TextButton(
                        onClick = { uriHandler.openUri(url) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Open release page") }
                }
            }
        }
    }
}

/**
 * Hero download bar for the recommended APK of the selected release.
 * Small text throughout; progress, install, open and uninstall stay on this
 * screen — nothing navigates away except the system installer itself.
 * Installed apps split into Open (grayed when the package exposes no
 * launcher intent) and Uninstall; uninstalled apps get a single Download.
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
    canOpenApp: (String) -> Boolean,
    onOpenApp: (String) -> Unit,
    cardRadiusDp: Int,
    /** True while assets are still arriving — show progress, never "no APK". */
    findingApk: Boolean = false,
) {
    // The "no APK" state was removed: when the scan finished with nothing
    // installable, the whole card stays hidden instead of a dead button.
    if (hero == HeroDlState.IdleDownload && asset == null && !findingApk) return
    Card(shape = panelShape(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                asset?.let { "Recommended APK · ${formatBytes(it.size)}" }
                    ?: "Recommended APK · $tag",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            when (hero) {
                is HeroDlState.IdleDownload -> {
                    val target = asset
                    if (target == null && findingApk) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            "Finding APK…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        return@Column
                    }
                    if (target == null) return@Column
                    Button(
                        onClick = { onDownload(target) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(
                            "Download ${formatBytes(target.size)}",
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
                        TextButton(
                            onClick = { if (target != null) onCancel(target.id) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
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
                    Text(
                        "Installed" + (hero.versionName?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val launchable = canOpenApp(hero.packageName)
                        OutlinedButton(
                            onClick = { onOpenApp(hero.packageName) },
                            enabled = launchable,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) {
                            Text("Open", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { onUninstall(hero.packageName) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) {
                            Text("Uninstall", maxLines = 1)
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




