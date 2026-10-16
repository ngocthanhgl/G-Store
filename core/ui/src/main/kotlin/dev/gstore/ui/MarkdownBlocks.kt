package dev.gstore.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size

/**
 * Shared Markdown block renderer (moved from :feature:readme so the details
 * screen can embed the same README rendering — one parser, one renderer,
 * no duplication, no feature-to-feature dependency).
 */
@Composable
fun BlockView(block: Block, owner: String, repo: String, branch: String = "main") {
    when (block) {
        is Block.Heading -> {
            val style = when (block.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleLarge
                3 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            }
            Text(block.text, style = style, fontWeight = FontWeight.Bold)
            if (block.level <= 2) HorizontalDivider(Modifier.padding(top = 4.dp))
        }
        is Block.Paragraph -> SpanText(block.spans, owner, repo, branch, MaterialTheme.typography.bodyLarge)
        is Block.CodeBlock -> CodeBlockView(block)
        is Block.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEach { item -> ListItemView(item, block.ordered, owner, repo, branch) }
        }
        is Block.Quote -> Surface(
            tonalElevation = 1.dp,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                block.children.forEach { BlockView(it, owner, repo, branch) }
            }
        }
        is Block.Table -> TableView(block)
        Block.Rule -> HorizontalDivider(Modifier.padding(vertical = 4.dp))
        is Block.RawText -> Text(block.text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ListItemView(item: MdListItem, ordered: Boolean, owner: String, repo: String, branch: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (item.checked != null) {
            Checkbox(checked = item.checked, onCheckedChange = null, enabled = false)
        } else {
            Text(if (ordered) "•" else "•", style = MaterialTheme.typography.bodyLarge)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (item.spans.isNotEmpty()) SpanText(item.spans, owner, repo, branch, MaterialTheme.typography.bodyLarge)
            item.children.forEach { BlockView(it, owner, repo, branch) }
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
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        cells.forEach { cell ->
            Text(
                cell,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (header) FontWeight.Bold else null,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

@Composable
private fun CodeBlockView(block: Block.CodeBlock) {
    val clipboard = LocalClipboardManager.current
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
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
            Text(block.code.trimEnd(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
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
    val annotated = buildAnnotatedString {
        fun append(span: Span) {
            when (span) {
                is Span.Text -> append(span.text)
                is Span.Code -> withStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace),
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
                is Span.Picture -> append("[image: ${span.alt.ifEmpty { span.src }}]")
            }
        }
        spans.forEach(::append)
    }
    // Image spans render beneath the text line that references them.
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url)
                        .size(Size(1600, 1600))
                        .build(),
                    contentDescription = picture.alt,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                TextButton(onClick = { uriHandler.openUri(url) }) {
                    Text("Open image: ${picture.alt.ifEmpty { picture.src }}")
                }
            }
        }
    }
}
