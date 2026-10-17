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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
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
import androidx.compose.ui.layout.ContentScale

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
        item { HeaderRow(repo) }
        item { Spacer(Modifier.height(12.dp)) }
            item {
                HeroBare(
                    hero = state.hero,
                    release = selectedRelease(state),
                    asset = heroAsset,
                    installedPackageName = state.installedPackageName,
                    onDownload = onHeroDownload,
                    onCancel = onHeroCancel,
                    onInstall = onHeroInstall,
                    onUninstall = onHeroUninstall,
                    canOpenApp = canOpenApp,
                    onOpenApp = onOpenApp,
                    findingApk = state.syncing || state.loadingAssets || state.scanningApks,
                )
            }
        item { Spacer(Modifier.height(12.dp)) }
        val description = repo.description
        if (description != null) {
            item { DescriptionText(description) }
            item { Spacer(Modifier.height(8.dp)) }
        }
        item { FactsLine(repo) }
        item { Spacer(Modifier.height(8.dp)) }
        item { LinksRow(repo, onOpenReadme) }
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
            item { ChangelogFull(release, reduceMotion) }
            item { Spacer(Modifier.height(12.dp)) }
        }
        item { Spacer(Modifier.height(12.dp)) }
        item { ScreenshotsRow(state.screenshots, reduceMotion) }
    }
}
}

private fun selectedRelease(state: DetailsUiState): Release? =
    state.releases.firstOrNull { it.id == state.selectedReleaseId }

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
    // Optical 16dp alignment: an IconButton centers its 24dp glyph in a
    // 48dp box, so edge buttons shift 12dp outward to put the glyph edge
    // exactly on the list's 16dp inset. Offsets don't move siblings.
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.offset(x = (-12).dp),
        ) { Icon(Fluent.ArrowLeft, contentDescription = "Back") }
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
        } else IconButton(
            onClick = onRefresh,
            modifier = Modifier.offset(x = 12.dp),
        ) { Icon(Fluent.ArrowSync, contentDescription = "Re-sync") }
    }
}

@Composable
private fun HeaderRow(repo: Repository) {
    // One-screen header: icon left, texts stacked left. The stats row died
    // here — stars/forks/freshness compress into a single meta line whose
    // tail (least important) truncates first, by design.
    Row(verticalAlignment = Alignment.CenterVertically) {
        dev.gstore.ui.AppIcon(
            avatarUrl = repo.avatarUrl,
            owner = repo.owner,
            size = 56.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    if (repo.archived) append("Archived · ")
                    append("★ ${formatCount(repo.stars)} · ${formatCount(repo.forks)} forks")
                    repo.updatedAt?.take(10)?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

/** Description without card chrome: two lines, tap to expand. */
@Composable
private fun DescriptionText(description: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = if (expanded) Int.MAX_VALUE else 2,
        overflow = if (expanded) {
            androidx.compose.ui.text.style.TextOverflow.Visible
        } else {
            androidx.compose.ui.text.style.TextOverflow.Ellipsis
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
    )
}

/** Every fact on one ellipsis line; nothing when there is nothing to say. */
@Composable
private fun FactsLine(repo: Repository) {
    val facts = listOfNotNull(
        repo.language,
        repo.license,
        repo.openIssues?.let { "$it open issues" },
        "Branch ${repo.defaultBranch}",
    )
    if (facts.isNotEmpty()) {
        Text(
            facts.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Repository / homepage / README as one row of compact link buttons. */
@Composable
private fun LinksRow(repo: Repository, onOpenReadme: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = { uriHandler.openUri("https://github.com/${repo.fullName}") },
            modifier = Modifier.heightIn(min = 40.dp),
        ) { Text("Repository", maxLines = 1) }
        repo.homepage?.takeIf { it.isNotBlank() }?.let { home ->
            val url = if (home.startsWith("http")) home else "https://$home"
            TextButton(
                onClick = { uriHandler.openUri(url) },
                modifier = Modifier.heightIn(min = 40.dp),
            ) { Text("Homepage", maxLines = 1) }
        }
        TextButton(
            onClick = onOpenReadme,
            modifier = Modifier.heightIn(min = 40.dp),
        ) { Text("README", maxLines = 1) }
    }
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
 * Changelog: tag/date header always visible; full markdown shown on tap.
 * Parsed off the main thread with a shimmer until ready.
 */
@Composable
private fun ChangelogFull(release: Release, reduceMotion: Boolean) {
    val uriHandler = LocalUriHandler.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
        ) {
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
/**
 * Repo-root screenshot gallery: image files only, up to 8, in a horizontal
 * rail. Shimmer while the contents API is still resolving.
 */
@Composable
private fun ScreenshotsRow(items: List<ScreenshotItem>, reduceMotion: Boolean) {
    if (items.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Screenshots")
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.name }) { screenshot ->
                val imageUrl = screenshot.url
                var loaded by remember(screenshot.url) { mutableStateOf(false) }
                Box(
                    modifier = Modifier.size(104.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!loaded && imageUrl.isNotBlank()) {
                        ShimmerRows(count = 1, reduceMotion = reduceMotion)
                    }
                    if (imageUrl.isNotBlank()) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(104.dp)
                                .clip(panelShape()),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            onSuccess = { _ -> loaded = true },
                            onError = { _ -> loaded = false },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bare hero control, no card chrome: one action per state, always visible.
 * The button text carries version (prerelease marked, never silent) and
 * size, so no separate label row is needed. A finished scan with nothing
 * installable renders a disabled reason, never a void.
 */
@Composable
private fun HeroBare(
    hero: HeroDlState,
    release: Release?,
    asset: ReleaseAsset?,
    onDownload: (ReleaseAsset) -> Unit,
    onCancel: (Long) -> Unit,
    onInstall: (DownloadRecord) -> Unit,
    onUninstall: (String) -> Unit,
    canOpenApp: (String) -> Boolean,
    onOpenApp: (String) -> Unit,
    /** True while assets are still arriving — show progress, never blank. */
    findingApk: Boolean = false,
    /** Package name of an already-installed copy, if any. */
    installedPackageName: String? = null,
) {
    val tag = release?.tag
    val version = tag?.let { if (release?.prerelease == true) "$it · pre" else it }
    when (hero) {
        is HeroDlState.IdleDownload -> {
            if (installedPackageName != null) {
                // Already installed — hero is Open/Uninstall, never Download.
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val launchable = canOpenApp(installedPackageName)
                    OutlinedButton(
                        onClick = { if (launchable) onOpenApp(installedPackageName) },
                        enabled = launchable,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("Open", maxLines = 1) }
                    OutlinedButton(
                        onClick = { onUninstall(installedPackageName) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("Uninstall", maxLines = 1) }
                }
                return
            }
            val target = asset
            if (target == null) {
                if (findingApk) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        "Finding APK...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                } else {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(
                            if (tag != null) "No APK in $tag" else "No APK in any release",
                            maxLines = 1,
                        )
                    }
                }
                return
            }
            Button(
                onClick = { onDownload(target) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(
                    listOfNotNull("Download", version, formatBytes(target.size))
                        .joinToString(" · "),
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




