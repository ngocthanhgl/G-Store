package dev.forkly.ui

import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import java.net.URI

/** Inline content inside paragraphs, headings and table cells. */
sealed interface Span {
    data class Text(val text: String) : Span
    data class Code(val code: String) : Span
    data class Emphasis(val children: List<Span>) : Span
    data class Strong(val children: List<Span>) : Span
    data class Strike(val children: List<Span>) : Span

    /**
     * [safe]=false for javascript:/data:/vbscript:/file: hrefs — rendered as
     * plain text, never opened (SECURITY_MODEL §3).
     */
    data class Hyperlink(val href: String, val children: List<Span>, val safe: Boolean) : Span
    data class Picture(val alt: String, val src: String, val href: String? = null) : Span
}

/** Block-level rendering model. Ordering mirrors the source document. */
sealed interface Block {
    data class Heading(val level: Int, val text: String, val anchor: String) : Block
    data class Paragraph(val spans: List<Span>) : Block
    data class CodeBlock(val language: String?, val code: String) : Block
    data class ListBlock(val ordered: Boolean, val items: List<MdListItem>) : Block
    data class Quote(val children: List<Block>) : Block
    data class Table(val headers: List<String>, val rows: List<List<String>>) : Block
    data object Rule : Block

    /** Unsupported/unsafe raw HTML is kept as inert text — never rendered as markup. */
    data class RawText(val text: String) : Block
}

data class MdListItem(
    val spans: List<Span>,
    val checked: Boolean?,
    val children: List<Block> = emptyList(),
)

private val PARSER: Parser by lazy {
    Parser.builder().extensions(
        listOf(TablesExtension.create(), StrikethroughExtension.create(), TaskListItemsExtension.create()),
    ).build()
}

/** Parses Markdown source into the sanitized [Block] model. Pure and thread-safe. */
fun parseMarkdown(source: String): List<Block> = blocksOf(PARSER.parse(source))

/** GitHub-style heading slug: lowercase, strip punctuation, spaces become hyphens. */
fun slugify(text: String): String =
    text.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}\\s\\-_]"), "")
        .trim()
        .replace(Regex("\\s+"), "-")

private val UNSAFE_SCHEMES = listOf("javascript:", "data:", "vbscript:", "file:")

/**
 * Absolute http(s) links and relative links are safe (resolved later against the
 * repository context). In-page anchors are safe. Anything else is inert text.
 */
fun isSafeHref(href: String): Boolean {
    val lower = href.trim().lowercase()
    if (lower.isEmpty()) return false
    if (lower.startsWith("#")) return true
    if (lower.startsWith("http://") || lower.startsWith("https://")) return true
    if (lower.startsWith("/") || lower.startsWith("./") || lower.startsWith("../")) return true
    if (UNSAFE_SCHEMES.any { lower.startsWith(it) }) return false
    // Bare relative path (no scheme): safe once resolved against the repo.
    if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(lower)) return false
    return true
}

/**
 * Resolves a relative link/image against the repository context.
 * [forImage]=true resolves against raw.githubusercontent.com (actual file bytes).
 */
fun resolveRelative(href: String, owner: String, repo: String, forImage: Boolean, branch: String = "main"): String {
    val trimmed = href.trim()
    if (trimmed.startsWith("#")) return "https://github.com/$owner/$repo$trimmed"
    val lower = trimmed.lowercase()
    if (lower.startsWith("http://") || lower.startsWith("https://")) return trimmed
    val ref = branch.ifBlank { "main" }
    if (trimmed.startsWith("/")) {
        val path = trimmed.trimStart('/')
        return if (forImage) "https://raw.githubusercontent.com/$owner/$repo/$ref/$path"
        else "https://github.com/$path"
    }
    val base = if (forImage) "https://raw.githubusercontent.com/$owner/$repo/$ref/"
    else "https://github.com/$owner/$repo/blob/$ref/"
    return base + normalizePath(trimmed.removePrefix("./"))
}

private fun normalizePath(path: String): String {
    val out = ArrayDeque<String>()
    for (segment in path.split('/')) {
        when (segment) {
            "", "." -> Unit
            ".." -> if (out.isNotEmpty()) out.removeLast()
            else -> out.addLast(segment)
        }
    }
    return out.joinToString("/")
}

private fun childrenOf(node: Node): List<Node> {
    val out = mutableListOf<Node>()
    var child = node.firstChild
    while (child != null) {
        out += child
        child = child.next
    }
    return out
}

private fun plainText(node: Node): String {
    val sb = StringBuilder()
    fun visit(n: Node) {
        when (n) {
            is Text -> sb.append(n.literal)
            is Code -> sb.append(n.literal)
            is SoftLineBreak, is HardLineBreak -> sb.append(' ')
            else -> childrenOf(n).forEach(::visit)
        }
    }
    visit(node)
    return sb.toString()
}

private fun spansOf(node: Node, compactUrls: Boolean = true): List<Span> {
    val out = mutableListOf<Span>()
    for (child in childrenOf(node)) {
        when (child) {
            is Text -> if (compactUrls) out.addAll(textSpans(child.literal))
            else out += Span.Text(child.literal)
            is Code -> out += Span.Code(child.literal)
            is Emphasis -> out += Span.Emphasis(spansOf(child, compactUrls))
            is StrongEmphasis -> out += Span.Strong(spansOf(child, compactUrls))
            is Strikethrough -> out += Span.Strike(spansOf(child, compactUrls))
            is SoftLineBreak -> out += Span.Text(" ")
            is HardLineBreak -> out += Span.Text("\n")
            is HtmlInline -> out += htmlSpans(child.literal)
            is Link -> {
                val href = child.destination
                val inner = spansOf(child, compactUrls = false)
                val visible = if (plainText(child).trim() == href) {
                    listOf(Span.Text(compactUrlLabel(href)))
                } else {
                    inner
                }
                out += linkedSpans(href, visible)
            }
            is Image -> out += Span.Picture(alt = plainText(child), src = child.destination)
            else -> out += Span.Text(plainText(child))
        }
    }
    return out
}

private val PLAIN_URL = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE)
private val URL_TRAILING_PUNCTUATION = setOf('.', ',', ';', ':', '!', '?', ')', ']', '}')

private fun textSpans(text: String): List<Span> {
    val spans = mutableListOf<Span>()
    var cursor = 0
    for (match in PLAIN_URL.findAll(text)) {
        var urlEnd = match.range.last + 1
        while (urlEnd > match.range.first && text[urlEnd - 1] in URL_TRAILING_PUNCTUATION) {
            urlEnd--
        }
        val url = text.substring(match.range.first, urlEnd)
        if (url.isBlank() || !isSafeHref(url)) continue
        if (match.range.first > cursor) spans += Span.Text(text.substring(cursor, match.range.first))
        spans += Span.Hyperlink(url, listOf(Span.Text(compactUrlLabel(url))), safe = true)
        spans += Span.Text(text.substring(urlEnd, match.range.last + 1))
        cursor = match.range.last + 1
    }
    if (cursor < text.length) spans += Span.Text(text.substring(cursor))
    return spans.ifEmpty { listOf(Span.Text(text)) }
}

private fun compactUrlLabel(href: String): String =
    runCatching { URI(href).host?.removePrefix("www.")?.takeIf { it.isNotBlank() } }
        .getOrNull() ?: href

private fun linkedSpans(href: String, children: List<Span>): List<Span> {
    if (!isSafeHref(href)) return children
    if (children.isEmpty()) {
        return listOf(Span.Hyperlink(href, listOf(Span.Text(compactUrlLabel(href))), safe = true))
    }
    return children.flatMap { child ->
        when (child) {
            is Span.Picture -> listOf(child.copy(href = href))
            is Span.Hyperlink -> listOf(child)
            else -> listOf(Span.Hyperlink(href, listOf(child), safe = true))
        }
    }
}

private fun blocksOf(node: Node): List<Block> {
    val out = mutableListOf<Block>()
    for (child in childrenOf(node)) {
        when (child) {
            is Heading -> {
                val text = plainText(child)
                out += Block.Heading(child.level.coerceIn(1, 6), text, slugify(text))
            }
            is Paragraph -> {
                val spans = spansOf(child)
                if (spans.isNotEmpty()) out += Block.Paragraph(spans)
            }
            is FencedCodeBlock -> out += Block.CodeBlock(
                child.info?.trim()?.takeIf { it.isNotEmpty() },
                child.literal,
            )
            is IndentedCodeBlock -> out += Block.CodeBlock(null, child.literal)
            is BulletList -> out += Block.ListBlock(ordered = false, itemsOf(child))
            is OrderedList -> out += Block.ListBlock(ordered = true, itemsOf(child))
            is BlockQuote -> out += Block.Quote(blocksOf(child))
            is org.commonmark.ext.gfm.tables.TableBlock -> out += tableOf(child)
            is ThematicBreak -> out += Block.Rule
            is HtmlBlock -> out += htmlBlocks(child.literal.trim())
            else -> {
                val text = plainText(child)
                if (text.isNotBlank()) out += Block.RawText(text)
            }
        }
    }
    return out
}

private fun itemsOf(list: Node): List<MdListItem> {
    val items = mutableListOf<MdListItem>()
    for (child in childrenOf(list)) {
        if (child !is ListItem) continue
        var checked: Boolean? = null
        val spans = mutableListOf<Span>()
        val nested = mutableListOf<Block>()
        for (grand in childrenOf(child)) {
            when (grand) {
                is TaskListItemMarker -> checked = grand.isChecked
                is Paragraph -> spans += spansOf(grand)
                is BulletList -> nested += Block.ListBlock(false, itemsOf(grand))
                is OrderedList -> nested += Block.ListBlock(true, itemsOf(grand))
                else -> nested += blocksOf(grand)
            }
        }
        items += MdListItem(spans, checked, nested)
    }
    return items
}

private fun tableOf(table: Node): Block {
    val headers = mutableListOf<String>()
    val rows = mutableListOf<List<String>>()
    for (section in childrenOf(table)) {
        when (section) {
            is TableHead -> {
                for (row in childrenOf(section)) {
                    if (row is TableRow) {
                        headers += childrenOf(row).filterIsInstance<TableCell>().map { plainText(it) }
                    }
                }
            }
            is TableBody -> {
                for (row in childrenOf(section)) {
                    if (row is TableRow) {
                        rows += listOf(
                            childrenOf(row).filterIsInstance<TableCell>().map { plainText(it) },
                        )
                    }
                }
            }
            else -> Unit
        }
    }
    return Block.Table(headers, rows)
}

private val HTML_TAG = Regex("<(/?)([a-zA-Z][a-zA-Z0-9]*)((?:\"[^\"]*\"|'[^']*'|[^\"'>])*)>")
private val HTML_ATTR = Regex("([a-zA-Z-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
private val HTML_CLOSE = Regex("</%s\\s*>", RegexOption.IGNORE_CASE)

private fun htmlAttr(attrs: String, name: String): String? {
    for (m in HTML_ATTR.findAll(attrs)) {
        if (!m.groupValues[1].equals(name, ignoreCase = true)) continue
        return listOf(m.groupValues[3], m.groupValues[4], m.groupValues[5]).firstOrNull { it.isNotEmpty() }
    }
    return null
}

private fun decodeEntities(s: String): String =
    s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")

private fun stripTags(html: String): String = decodeEntities(HTML_TAG.replace(html, "")).trim()

/** Inline spans from an HTML fragment: links and images survive, the rest becomes text. */
private fun htmlSpans(html: String, compactUrls: Boolean = true): List<Span> {
    val out = mutableListOf<Span>()
    var i = 0
    while (i < html.length) {
        val m = HTML_TAG.find(html, i)
        if (m == null) {
            val t = decodeEntities(html.substring(i)).trim()
            if (t.isNotEmpty()) {
                if (compactUrls) out.addAll(textSpans(t)) else out += Span.Text(t)
            }
            break
        }
        if (m.range.first > i) {
            val t = decodeEntities(html.substring(i, m.range.first)).trim()
            if (t.isNotEmpty()) {
                if (compactUrls) out.addAll(textSpans(t)) else out += Span.Text(t)
            }
        }
        val closing = m.groupValues[1] == "/"
        val name = m.groupValues[2].lowercase()
        val attrs = m.groupValues[3]
        val after = m.range.last + 1
        when {
            !closing && (name == "script" || name == "style" || name == "iframe") -> {
                val close = Regex(HTML_CLOSE.pattern.format(name)).find(html, after)
                i = close?.range?.last?.plus(1) ?: html.length
            }
            !closing && name == "a" -> {
                val href = htmlAttr(attrs, "href")
                val closeAt = html.indexOf("</a>", after, ignoreCase = true)
                val inner = if (closeAt >= 0) html.substring(after, closeAt) else html.substring(after)
                i = if (closeAt >= 0) closeAt + 4 else html.length
                val children = htmlSpans(inner, compactUrls = false)
                out += if (href != null) linkedSpans(
                    href,
                    children.ifEmpty {
                        stripTags(inner).takeIf { it.isNotBlank() }?.let { listOf(Span.Text(it)) }.orEmpty()
                    },
                ) else children
            }
            !closing && name == "img" -> {
                val src = htmlAttr(attrs, "src")
                if (src != null) out += Span.Picture(htmlAttr(attrs, "alt") ?: "", src)
                i = after
            }
            !closing && name == "br" -> {
                out += Span.Text(" ")
                i = after
            }
            else -> i = after
        }
    }
    return out
}

/** Blocks from an HTML fragment: headings, lists, links/images survive as real blocks. */
private fun htmlBlocks(html: String): List<Block> {
    if (html.isBlank()) return emptyList()
    val trimmed = html.trim()
    val out = mutableListOf<Block>()
    val heading = Regex("^<h([1-6])\\b[^>]*>(.*)</h[1-6]>$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(trimmed)
    if (heading != null) {
        val text = stripTags(heading.groupValues[2])
        if (text.isNotEmpty()) out += Block.Heading(heading.groupValues[1].toInt(), text, slugify(text))
        return out
    }
    val list = Regex("^<(ul|ol)\\b[^>]*>(.*)</(ul|ol)>$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(trimmed)
    if (list != null) {
        val items = Regex("<li\\b[^>]*>(.*?)</li>", RegexOption.DOT_MATCHES_ALL).findAll(list.groupValues[2]).map { li ->
            val text = stripTags(li.groupValues[1])
            MdListItem(listOf(Span.Text(text)), null, emptyList())
        }.filter { it.spans.isNotEmpty() }.toList()
        if (items.isNotEmpty()) out += Block.ListBlock(ordered = list.groupValues[1].equals("ol", ignoreCase = true), items)
        return out
    }
    if (trimmed.startsWith("<pre", ignoreCase = true)) {
        val text = stripTags(trimmed)
        if (text.isNotEmpty()) out += Block.CodeBlock(null, text)
        return out
    }
    if (trimmed.contains("<img", ignoreCase = true) || trimmed.contains("<a", ignoreCase = true)) {
        val spans = htmlSpans(trimmed)
        if (spans.isNotEmpty()) out += Block.Paragraph(spans)
        return out
    }
    val text = stripTags(trimmed)
    if (text.isNotEmpty()) out += Block.Paragraph(listOf(Span.Text(text)))
    return out
}
