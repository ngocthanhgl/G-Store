package dev.forkly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableFloatStateOf
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
import java.net.URI
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.size.Size

import dev.forkly.designsystem.cardShapeFor
import dev.forkly.designsystem.imageShapeFor
import dev.forkly.designsystem.pillShape

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
                1 -> Triple(MaterialTheme.typography.headlineSmall, FontWeight.Bold, ForklySpace.BeforeHeader)
                2 -> Triple(MaterialTheme.typography.titleLarge, FontWeight.Bold, ForklySpace.BeforeHeader)
                3 -> Triple(MaterialTheme.typography.titleMedium, FontWeight.Bold, ForklySpace.Block)
                else -> Triple(MaterialTheme.typography.bodyMedium, FontWeight.Bold, ForklySpace.Block)
            }
            Text(
                block.text,
                style = style,
                fontWeight = weight,
                modifier = Modifier.padding(top = topGap, bottom = ForklySpace.AfterHeader),
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
            Column(Modifier.padding(horizontal = ForklySpace.Gutter, vertical = ForklySpace.Medium), verticalArrangement = Arrangement.spacedBy(ForklySpace.Small)) {
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
        modifier = Modifier.padding(start = indent, top = ForklySpace.Block),
        verticalArrangement = Arrangement.spacedBy(ForklySpace.Small),
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
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(ForklySpace.Small)) {
        if (item.checked != null) {
            // READMEs are data, not a to-do app: the box stays inert but keeps
            // the row rhythm even so task lists line up with plain bullets.
            Box(
                Modifier.width(ForklySpace.TouchTarget).height(ForklySpace.TouchTarget),
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
        Column(verticalArrangement = Arrangement.spacedBy(ForklySpace.Small)) {
            if (item.spans.isNotEmpty()) SpanText(item.spans, owner, repo, branch, MaterialTheme.typography.bodyMedium)
            item.children.forEach { BlockView(it, owner, repo, branch, depth = depth + 1) }
        }
    }
}

@Composable
private fun TableView(table: Block.Table) {
    val scroll = rememberScrollState()
    val columnCount = maxOf(table.headers.size, table.rows.maxOfOrNull { it.size } ?: 0)
    val widths = (0 until columnCount).map { column ->
        val contentLength = sequenceOf(table.headers.getOrNull(column))
            .plus(table.rows.asSequence().map { it.getOrNull(column) })
            .filterNotNull()
            .maxOfOrNull { it.length.coerceAtMost(32) }
            ?: 12
        (contentLength * 8 + 24).dp.coerceIn(112.dp, 280.dp)
    }
    Box(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
        Column {
            TableRowView(table.headers, widths, header = true)
            HorizontalDivider()
            table.rows.forEach { TableRowView(it, widths, header = false) }
        }
    }
}

@Composable
private fun TableRowView(cells: List<String>, widths: List<androidx.compose.ui.unit.Dp>, header: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ForklySpace.Small),
        modifier = Modifier.padding(vertical = ForklySpace.Block),
    ) {
        cells.forEachIndexed { index, cell ->
            Text(
                cell,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (header) FontWeight.Bold else null,
                maxLines = 4,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.width(widths.getOrElse(index) { 160.dp }),
            )
        }
    }
}

fun canAutoLoadMarkdownImage(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    val host = uri.host?.lowercase() ?: return false
    return uri.scheme.equals("https", ignoreCase = true) &&
        uri.userInfo == null &&
        (uri.port == -1 || uri.port == 443) &&
        !isPrivateImageHost(host)
}

private fun isPrivateImageHost(host: String): Boolean {
    val normalized = host.removePrefix("[").removeSuffix("]").removeSuffix(".")
    if (normalized == "localhost" || normalized.endsWith(".localhost") ||
        normalized.endsWith(".local") || normalized.endsWith(".internal") ||
        normalized.endsWith(".lan") || normalized.endsWith(".home") ||
        normalized.endsWith(".home.arpa") || normalized.endsWith(".test")
    ) return true

    if (':' in normalized) {
        val address = runCatching { java.net.InetAddress.getByName(normalized) }.getOrNull()
            ?: return true
        val bytes = address.address
        return address.isAnyLocalAddress || address.isLoopbackAddress ||
            address.isLinkLocalAddress || address.isSiteLocalAddress ||
            address.isMulticastAddress ||
            (bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC)
    }

    if (normalized.any { it !in '0'..'9' && it != '.' }) {
        if (Regex("(?i)(?:0x[0-9a-f]+|[0-9]+)(?:\\.(?:0x[0-9a-f]+|[0-9]+))*").matches(normalized)) {
            return true
        }
        return '.' !in normalized
    }
    val parts = normalized.split('.')
    val octets = parts.mapNotNull { it.toIntOrNull() }
    if (octets.size != 4 || octets.any { it !in 0..255 } ||
        parts.any { it.length > 1 && it.startsWith('0') }
    ) return true
    val (a, b) = octets
    return a == 0 || a == 10 || a == 127 || a >= 224 ||
        (a == 100 && b in 64..127) ||
        (a == 169 && b == 254) ||
        (a == 172 && b in 16..31) ||
        (a == 192 && b == 168) ||
        (a == 198 && b in 18..19)
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
        Column(Modifier.padding(horizontal = ForklySpace.Gutter, vertical = ForklySpace.Medium)) {
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
            val imageModifier = Modifier.fillMaxSize().then(
                picture.href?.let { href ->
                    Modifier.clickable {
                        uriHandler.openUri(
                            resolveRelative(href, owner, repo, forImage = false, branch = branch),
                        )
                    }
                } ?: Modifier,
            )
            // Public HTTPS images display inline; local/private addresses are blocked.
            if (canAutoLoadMarkdownImage(url)) {
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
                var aspectRatio by remember(url) { mutableFloatStateOf(16f / 10f) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspectRatio)
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
                            modifier = Modifier.padding(ForklySpace.Medium),
                        )
                    }
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(url)
                            .size(Size(1600, 1600))
                            .build(),
                        contentDescription = picture.alt.ifEmpty { "README image" },
                        contentScale = ContentScale.Fit,
                        modifier = imageModifier,
                        onSuccess = { result ->
                            loaded = true
                            val imageSize = result.painter.intrinsicSize
                            if (imageSize.width.isFinite() && imageSize.height.isFinite() &&
                                imageSize.width > 0f && imageSize.height > 0f
                            ) {
                                aspectRatio = (imageSize.width / imageSize.height).coerceIn(0.2f, 5f)
                            }
                        },
                        onError = { failed = true },
                    )
                }
            }
        }
    }
}
