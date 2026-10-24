package dev.forkly.details

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import dev.forkly.ui.safeTop
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import dev.forkly.ui.AuroraSectionHeader
import dev.forkly.ui.ForklySpace
import dev.forkly.ui.ShimmerBlock
import coil3.request.ImageRequest
import coil3.size.Size
import dev.forkly.ui.Fluent

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.forkly.data.AssetKind
import dev.forkly.database.DownloadRecord
import dev.forkly.designsystem.imageShapeFor
import dev.forkly.designsystem.panelShape
import dev.forkly.model.Release
import dev.forkly.model.ReleaseAsset
import dev.forkly.model.Repository
import dev.forkly.ui.Block
import dev.forkly.ui.BlockView
import dev.forkly.ui.ShimmerRows
import dev.forkly.ui.parseMarkdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.layout.ContentScale
import dev.forkly.designsystem.LocalCardRadius

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
    onResumeInstalledState: () -> Unit,
    canOpenApp: (String) -> Boolean,
    onOpenApp: (String) -> Unit,
    onOpenReadme: () -> Unit,
    cardRadiusDp: Int = LocalCardRadius.current,
    reduceMotion: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var screenResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner, onResumeInstalledState) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    screenResumed = true
                    onResumeInstalledState()
                }
                Lifecycle.Event.ON_PAUSE -> screenResumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notice = state.notice
    LaunchedEffect(notice) {
        if (notice != null) snackbar.showSnackbar(notice)
    }
    val context = LocalContext.current
    val repo = state.repo
    if (repo == null) {
        Box(
            modifier.fillMaxSize().safeTop(),
            contentAlignment = Alignment.Center,
        ) {
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
    // Owned by the screen, keyed by release id. Lazy saveable state is keyed
    // by item INDEX, so holding this inside the changelog item made the
    // registry throw whenever the release list shifted under it mid-load.
    var changelogExpanded by rememberSaveable { mutableStateOf(false) }
    var changelogReleaseId by rememberSaveable { mutableStateOf<Long?>(null) }
    val showChangelog =
        changelogExpanded && changelogReleaseId == state.selectedReleaseId
    // Full-screen gallery: index into the same screenshot list the strip shows,
    // so the viewer always opens on the tile that was tapped.
    var viewerIndex by rememberSaveable { mutableStateOf(-1) }
    val imageDimensionsOk = remember(repo.id) { mutableStateMapOf<String, Boolean>() }
    val visibleImages = state.images.filter { imageDimensionsOk[it.url] != false }
    var previousHero by remember(repo.id) { mutableStateOf<HeroDlState?>(null) }
    LaunchedEffect(state.hero, screenResumed) {
        if (screenResumed && shouldAutoInstallHero(previousHero, state.hero)) {
            onHeroInstall((state.hero as HeroDlState.ReadyInstall).record)
        }
        if (screenResumed) previousHero = state.hero
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ForklySpace.Block),
        // The navigation bar owns the system inset, so this is just a reading
        // gap; the top inset comes from the TopBar's safeTop().
        contentPadding = PaddingValues(
            start = ForklySpace.Gutter,
            end = ForklySpace.Gutter,
            bottom = ForklySpace.Block,
        ),
    ) {
        // Back / share / favorite / refresh live in one top row; the hero
        // below it sits under safeTop() padding from the TopBar.
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
                modifier = Modifier.safeTop(),
            )
        }
        item {
            val latestRelease = state.releases.maxByOrNull { it.publishedAt.orEmpty() }
            HeaderRow(repo, latestRelease?.publishedAt ?: repo.pushedAt)
        }
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
                findingApk = state.syncing || state.scanningApks,
                loadingReleaseAssets = state.loadingAssets,
            )
        }
        val description = repo.description
        if (description != null) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(
                        top = ForklySpace.BeforeHeader - ForklySpace.Block,
                    ),
                    verticalArrangement = Arrangement.spacedBy(ForklySpace.AfterHeader),
                ) {
                    AuroraSectionHeader(
                        title = "About this app",
                        topGap = false,
                        bottomGap = false,
                    )
                    DescriptionText(description)
                }
            }
        }
        if (state.images.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(
                        top = ForklySpace.BeforeHeader - ForklySpace.Block,
                    ),
                    verticalArrangement = Arrangement.spacedBy(ForklySpace.AfterHeader),
                ) {
                    AuroraSectionHeader(
                        title = "Images",
                        count = visibleImages.size,
                        topGap = false,
                        bottomGap = false,
                    )
                    ImagesRow(visibleImages, reduceMotion, onImageSizeChecked = { url, accepted ->
                        imageDimensionsOk[url] = accepted
                    }) { index -> viewerIndex = index }
                }
            }
        }
        val busy = state.syncing || state.loadingRepo || state.scanningApks
        val syncError = state.syncError
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(
                    top = ForklySpace.BeforeHeader - ForklySpace.Block,
                ),
                verticalArrangement = Arrangement.spacedBy(ForklySpace.AfterHeader),
            ) {
                AuroraSectionHeader(
                    title = "What's new",
                    count = apkReleases.size.takeIf { it > 0 },
                    topGap = false,
                    bottomGap = false,
                )
                if (apkReleases.isEmpty() && busy && syncError == null) {
                    ShimmerRows(count = 2, reduceMotion = reduceMotion)
                } else {
                    ReleasePicker(
                        apkReleases,
                        state.selectedReleaseId,
                        changelogReleaseId.takeIf { changelogExpanded },
                        onClickRelease = { releaseId ->
                            if (changelogExpanded && changelogReleaseId == releaseId) {
                                changelogExpanded = false
                            } else {
                                onSelectRelease(releaseId)
                                changelogReleaseId = releaseId
                                changelogExpanded = true
                            }
                        },
                    )
                }
                if (syncError != null) {
                    SyncErrorCard(syncError, onRefresh, cardRadiusDp)
                }
            }
        }
        if (showChangelog) selectedRelease(state)?.let { release ->
            item {
                ChangelogFull(
                    release = release,
                    reduceMotion = reduceMotion,
                )
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(
                    top = ForklySpace.BeforeHeader - ForklySpace.Block,
                ),
                verticalArrangement = Arrangement.spacedBy(ForklySpace.AfterHeader),
            ) {
                MetaFooter(repo, onOpenReadme)
            }
        }
    }
    if (viewerIndex >= 0) {
        ScreenshotViewer(
            images = visibleImages,
            startIndex = viewerIndex,
            onDismiss = { viewerIndex = -1 },
        )
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
    // All controls keep identical 48dp targets and share the same row spacing.
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ForklySpace.Small),
    ) {
        IconButton(
            onClick = onBack,
        ) { Icon(Fluent.ArrowLeft, contentDescription = "Back") }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onShare) { Icon(Fluent.Share, contentDescription = "Share") }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (isFavorite) Fluent.HeartFilled else Fluent.Heart,
                contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (syncing) {
            Box(
                modifier = Modifier.size(ForklySpace.TouchTarget),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(Modifier.size(ForklySpace.Icon))
            }
        } else IconButton(
            onClick = onRefresh,
        ) { Icon(Fluent.ArrowSync, contentDescription = "Re-sync") }
    }
}

@Composable
private fun HeaderRow(repo: Repository, lastUpdate: String?) {
    // One-screen header: icon left, texts stacked left. The stats row died
    // here — stars/forks/freshness compress into a single meta line whose
    // tail (least important) truncates first, by design.
    Row(verticalAlignment = Alignment.CenterVertically) {
        dev.forkly.ui.AppIcon(
            avatarUrl = repo.avatarUrl,
            owner = repo.owner,
            size = 72.dp,
        )
        Spacer(Modifier.width(ForklySpace.Medium))
        // SpaceEvenly, not Top: three short lines against a 72dp icon left
        // them bunched at the top with dead space underneath.
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
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
                    lastUpdate?.take(10)?.let { append(" · Last update $it") }
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
            .heightIn(min = ForklySpace.TouchTarget)
            .clickable { expanded = !expanded }
            .padding(vertical = ForklySpace.Small),
    )
}

/**
 * Quiet metadata footer. Play Store puts provenance last and keeps it
 * recessive, so this is plain bodySmall text plus link rows that look like
 * list items — not a card, not a row of primary buttons. Every row is a
 * 48dp touch target; the chevron is decorative.
 */
@Composable
private fun MetaFooter(repo: Repository, onOpenReadme: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val facts = listOfNotNull(
        repo.license?.takeIf { it.isNotBlank() }?.let { "License $it" },
        repo.language?.takeIf { it.isNotBlank() },
        repo.openIssues?.let { "$it open issues" },
        repo.homepage?.takeIf { it.isNotBlank() }?.let { "Homepage $it" },
    )
    if (facts.isNotEmpty()) {
        Text(
            facts.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = ForklySpace.Block),
        )
    }
    val links = buildList {
        add("Repository" to "https://github.com/${repo.fullName}")
        repo.homepage?.takeIf { it.isNotBlank() }?.let {
            add("Homepage" to if (it.startsWith("http")) it else "https://$it")
        }
    }
    Column(modifier = Modifier.padding(top = ForklySpace.AfterHeader)) {
        links.forEach { (label, url) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ForklySpace.TouchTarget)
                    .clickable { uriHandler.openUri(url) },
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Fluent.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(ForklySpace.Icon),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ForklySpace.TouchTarget)
                .clickable { onOpenReadme() },
        ) {
            Text(
                "Read more",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Fluent.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(ForklySpace.Icon),
            )
        }
    }
}

private fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fk".format(n / 1_000.0)
    else -> n.toString()
}

@Composable
private fun ReleasePicker(
    releases: List<Release>,
    selectedId: Long?,
    expandedReleaseId: Long?,
    onClickRelease: (Long) -> Unit,
) {
    if (releases.isEmpty()) {
        Text(
            "No releases synced yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = ForklySpace.Small),
        )
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(ForklySpace.Small)) {
        items(releases, key = { it.id }) { release ->
            val selected = release.id == selectedId
            val expanded = release.id == expandedReleaseId
            FilterChip(
                selected = selected,
                onClick = { onClickRelease(release.id) },
                label = {
                    Text(
                        release.tag + if (release.prerelease) " · pre" else "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = if (selected) {
                    {
                        Icon(
                            Fluent.Checkmark,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else null,
                trailingIcon = if (selected) {
                    {
                        Icon(
                            if (expanded) Fluent.ChevronDown else Fluent.ChevronRight,
                            contentDescription = if (expanded) "Hide changelog" else "Show changelog",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else null,
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                // M3 chips are 32dp; this rail is a primary choice surface.
                modifier = Modifier.heightIn(min = ForklySpace.RowAction),
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
private fun ChangelogFull(
    release: Release,
    reduceMotion: Boolean,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(panelShape())
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(ForklySpace.Card),
        verticalArrangement = Arrangement.spacedBy(ForklySpace.Small),
    ) {
        val releaseName = release.name
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
            var blocks by remember(release.id) { mutableStateOf<List<Block>?>(null) }
            LaunchedEffect(release.id, notes) {
                blocks = withContext(Dispatchers.Default) { parseMarkdown(notes) }
            }
            val parsed = blocks
            if (parsed == null) {
                ShimmerRows(count = 2, reduceMotion = reduceMotion)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(ForklySpace.Small)) {
                    parsed.forEach { BlockView(it, "", "") }
                }
            }
        }
        release.htmlUrl?.let { url ->
            TextButton(
                onClick = { uriHandler.openUri(url) },
                modifier = Modifier.heightIn(min = ForklySpace.TouchTarget),
            ) { Text("Open release page") }
        }
    }
}
/**
 * Repo and README images, filtered after bounded decode to avoid badges and
 * tiny decorative assets.
 */
@Composable
private fun ImagesRow(
    items: List<RepositoryImage>,
    reduceMotion: Boolean,
    onImageSizeChecked: (String, Boolean) -> Unit,
    onOpen: (Int) -> Unit,
) {
    if (items.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ForklySpace.Small),
        contentPadding = PaddingValues(vertical = ForklySpace.Small),
    ) {
        items(items, key = { it.url }) { screenshot ->
            val imageUrl = screenshot.url
            var loaded by remember(screenshot.url) { mutableStateOf(false) }
            var failed by remember(screenshot.url) { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .size(ScreenshotSize)
                    .clip(imageShapeFor())
                    // Play Store behaviour: the strip is a window onto the
                    // full-screen gallery, not the gallery itself.
                    .clickable(
                        enabled = imageUrl.isNotBlank(),
                        onClick = { onOpen(items.indexOf(screenshot)) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (!loaded && !failed && imageUrl.isNotBlank()) {
                    ShimmerBlock(Modifier.fillMaxSize(), reduceMotion = reduceMotion)
                }
                if (failed) {
                    Text(
                        "Image unavailable",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUrl)
                            // Cap decode resolution instead of trusting the
                            // uploaded dimensions; a 140dp tile at xxhdpi is
                            // ~560px.
                            .size(Size(560, 560))
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(ScreenshotSize)
                            .clip(imageShapeFor()),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        onSuccess = { result ->
                            loaded = true
                            failed = false
                            val size = result.painter.intrinsicSize
                            onImageSizeChecked(
                                screenshot.url,
                                meetsMinimumImageSize(size.width, size.height),
                            )
                        },
                        onError = { _ ->
                            loaded = true
                            failed = true
                            onImageSizeChecked(screenshot.url, false)
                        },
                    )
                }
            }
        }
    }
}

/** Screenshot tile edge: 140dp reads as artwork, not as a thumbnail. */
private val ScreenshotSize = 140.dp

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
    loadingReleaseAssets: Boolean = false,
    /** Package name of an already-installed copy, if any. */
    installedPackageName: String? = null,
) {
    val tag = release?.tag
    val version = tag?.let { if (release?.prerelease == true) "$it · pre" else it }
    // The hero is a pill that hugs its label, not a full-width slab: at
    // fillMaxWidth it swallowed the whole first screen and left the download
    // state unreadable at a glance.
    val heroShape = dev.forkly.designsystem.pillShape
    val heroModifier = Modifier
        .padding(top = ForklySpace.Block)
        .heightIn(min = ForklySpace.TouchTarget)
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
                    Column(
                        modifier = heroModifier.fillMaxWidth().padding(top = ForklySpace.Small),
                        verticalArrangement = Arrangement.spacedBy(ForklySpace.Small),
                    ) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            "Finding APK...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                } else if (loadingReleaseAssets) {
                    Column(
                        modifier = heroModifier.fillMaxWidth().padding(top = ForklySpace.Small),
                        verticalArrangement = Arrangement.spacedBy(ForklySpace.Small),
                    ) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            "Loading selected version...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                } else {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = heroModifier,
                        shape = heroShape,
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
                modifier = heroModifier,
                shape = heroShape,
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
                modifier = heroModifier,
                shape = heroShape,
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
                modifier = heroModifier,
                shape = heroShape,
            ) {
                Text("Retry download", maxLines = 1)
            }
        }
    }
}
