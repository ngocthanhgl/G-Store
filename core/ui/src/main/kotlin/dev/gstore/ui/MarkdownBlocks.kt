package dev.gstore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.size.Size

import dev.gstore.designsystem.cardShapeFor
import dev.gstore.designsystem.imageShapeFor
import dev.gstore.designsystem.pillShape

/**
 * Shared Markdown block renderer (moved from :feature:readme so the details
 * screen can embed the same README rendering — one parser, one renderer,
 * no duplication, no feature-to-feature dependency).
 */
/**
 * Hard cap on block nesting. Markdown arrives from public repositories, so a
 * release body can legitimately contain a thousand-deep list/quote chain.
 * Every recursive edge funnels through this function, so one guard here bounds
 * the whole renderer instead of trusting each call site to remember a limit.
 * `shortcut:` rooms beyond 6 render nothing; raise this only with a measured
 * reason, since each level costs real composable frames.
 */
private const val MAX_BLOCK_NESTING = 6

@Composable
fun BlockView(block: Block, owner: String, repo: String, branch: String = "main", depth: Int = 0) {
    if (depth > MAX_BLOCK_NESTING) return
    when (block) {
        is Block.Heading -> {
            // Body copy is bodyMedium so every heading step stays visible above it.
            val (style, weight, topGap) = when (block.level) {
                1 -> Triple(MaterialTheme.typography.headlineSmall, FontWeight.Bold, GStoreSpace.BeforeHeader)
                2 -> Triple(MaterialTheme.typography.titleLarge, FontWeight.Bold, GStoreSpace.BeforeHeader)
                3 -> Triple(MaterialTheme.typography.titleMedium, FontWeight.Bold, GStoreSpace.Block)
                else -> Triple(MaterialTheme.typography.bodyMedium, FontWeight.Bold, GStoreSpace.Block)
            }
            Text(
                block.text,
                style = style,
                fontWeight = weight,
                modifier = Modifier.padding(top = topGap, bottom = GStoreSpace.AfterHeader),
            )
            if (block.level <= 2) HorizontalDivider()
        }
        is Block.Paragraph -> SpanText(block.spans, owner, repo, branch, MaterialTheme.typography.bodyMedium)
        is Block.CodeBlock -> CodeBlockView(block)
        is Block.ListBlock -> ListBlockView(block, block.ordered, owner, repo, branch, depth)
        is Block.Quote -> Surface(
            tonalElevation = 0.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = imageShapeFor(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = GStoreSpace.Gutter, vertical = GStoreSpace.Medium), verticalArrangement = Arrangement.spacedBy(GStoreSpace.Small)) {
                block.children.forEach { BlockView(it, owner, repo, branch, depth) }
            }
        }
        is Block.Table -> TableView(block)
        Block.Rule -> HorizontalDivider(Modifier.padding(vertical = 16.dp))
        is Block.RawText -> Text(block.text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ListBlockView(
    block: Block.ListBlock,
    ordered: Boolean,
    owner: String,
    repo: String,
    branch: String,
    depth: Int,
) {
    val indent = (depth.coerceIn(0, 4) * 16).dp
    Column(
        modifier = Modifier.padding(start = indent, top = GStoreSpace.Block),
        verticalArrangement = Arrangement.spacedBy(GStoreSpace.Small),
    ) {
        block.items.forEachIndexed { index, item ->
            ListItemView(item, ordered, index + 1, owner, repo, branch, depth)
        }
    }
}

@Composable
private fun ListItemView(
    item: MdListItem,
    ordered: Boolean,
    ordinal: Int,
    owner: String,
    repo: String,
    branch: String,
    depth: Int,
) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(GStoreSpace.Small)) {
        if (item.checked != null) {
            // READMEs are data, not a to-do app: the box stays inert but keeps
            // the row rhythm even so task lists line up with plain bullets.
            Box(
                Modifier.width(GStoreSpace.TouchTarget).height(GStoreSpace.TouchTarget),
                contentAlignment = Alignment.Center,
            ) {
                Checkbox(
                    checked = item.checked,
                    onCheckedChange = null,
                    enabled = false,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Text(
                text = if (ordered) "$ordinal." else "\u2022",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(20.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(GStoreSpace.Small)) {
            if (item.spans.isNotEmpty()) SpanText(item.spans, owner, repo, branch, MaterialTheme.typography.bodyMedium)
            item.children.forEach { BlockView(it, owner, repo, branch, depth = depth + 1) }
        }
    }
}

@Composable
private fun TableView(table: Block.Table) {
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
        Column {
            TableRowView(table.headers, header = true)
            HorizontalDivider()
            table.rows.forEach { TableRowView(it, header = false) }
        }
    }
}

@Composable
private fun TableRowView(cells: List<String>, header: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(GStoreSpace.Small),
        modifier = Modifier.padding(vertical = GStoreSpace.Block),
    ) {
        cells.forEach { cell ->
            Text(
                cell,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (header) FontWeight.Bold else null,
                maxLines = 4,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                // Rows are measured independently, so a min width is the only
                // way columns line up between header and body.
                modifier = Modifier
                    .weight(1f, fill = false)
                    .widthIn(min = 96.dp)
                    .padding(end = GStoreSpace.Small),
            )
        }
    }
}

@Composable
private fun CodeBlockView(block: Block.CodeBlock) {
    val clipboard = LocalClipboardManager.current
    Surface(
        tonalElevation = 0.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = imageShapeFor(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = GStoreSpace.Gutter, vertical = GStoreSpace.Medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (block.language != null) {
                    Text(
                        block.language,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = { clipboard.setText(AnnotatedString(block.code)) }) {
                    Text("Copy")
                }
            }
            // Long lines scroll instead of wrapping into a ragged block.
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Text(
                    block.code.trimEnd(),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun SpanText(
    spans: List<Span>,
    owner: String,
    repo: String,
    branch: String,
    style: androidx.compose.ui.text.TextStyle,
) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val codeColor = MaterialTheme.colorScheme.onSurface
    val annotated = buildAnnotatedString {
        fun append(span: Span) {
            when (span) {
                is Span.Text -> append(span.text)
                is Span.Code -> withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        // A SpanStyle cannot draw padding, but the tonal
                        // background alone makes inline code legible.
                        background = codeBackground,
                        color = codeColor,
                    ),
                ) { append(span.code) }
                is Span.Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    span.children.forEach(::append)
                }
                is Span.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    span.children.forEach(::append)
                }
                is Span.Strike -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    span.children.forEach(::append)
                }
                is Span.Hyperlink -> {
                    val start = length
                    span.children.forEach(::append)
                    if (span.safe) {
                        addStyle(
                            SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                            ),
                            start, length,
                        )
                        addStringAnnotation("url", span.href, start, length)
                    }
                }
                // The image renders below the paragraph; no placeholder text.
                is Span.Picture -> Unit
            }
        }
        spans.forEach(::append)
    }
    // Image spans render beneath the text line that references them.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ClickableText(
            text = annotated,
            style = style.copy(color = MaterialTheme.colorScheme.onSurface),
            onClick = { offset ->
                annotated.getStringAnnotations("url", offset, offset).firstOrNull()?.let { link ->
                    uriHandler.openUri(resolveRelative(link.item, owner, repo, forImage = false, branch = branch))
                }
            },
        )
        spans.filterIsInstance<Span.Picture>().forEach { picture ->
            val url = resolveRelative(picture.src, owner, repo, forImage = true, branch = branch)
            // Auto-fetch images only over HTTPS. Anything else renders as an
            // explicit tap-to-open link so the app never silently fetches
            // cleartext or non-web content on the user's behalf.
if (url.startsWith("https://")) {
                // Cap decoded size: README screenshots are often 4K and
                // would otherwise sit in memory at full resolution.
                // The aspect box reserves layout space so decoding never
                // shifts the document while it is being read.
                //
                // Loading and error states are painted UNDER the image and
                // cleared by its load callbacks: a broken README image must
                // say so rather than leaving a silent empty rectangle.
                var failed by remember(url) { mutableStateOf(false) }
                var loaded by remember(url) { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 10f)
            // Was pillShape (percent = 50) on a full-text-width box: a real
            // lens that cut into the image content. Images cap at 12dp.
                        .clip(imageShapeFor())
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!loaded) {
                        Text(
                            if (failed) "Image unavailable" else "Loading image…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(GStoreSpace.Medium),
                        )
                    }
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(url)
                            .size(Size(1600, 1600))
                            .build(),
                        contentDescription = picture.alt.ifEmpty { "README image" },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        onSuccess = { loaded = true },
                        onError = { failed = true },
                    )
                }
            } else {
                TextButton(onClick = { uriHandler.openUri(url) }) {
                    Text(
                        "Open image: ${picture.alt.ifEmpty { picture.src }}",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
