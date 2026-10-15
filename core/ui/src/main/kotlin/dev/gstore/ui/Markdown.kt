package dev.gstore.ui

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
    data class Picture(val alt: String, val src: String) : Span
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
fun resolveRelative(href: String, owner: String, repo: String, forImage: Boolean): String {
    val trimmed = href.trim()
    if (trimmed.startsWith("#")) return "https://github.com/$owner/$repo$trimmed"
    val lower = trimmed.lowercase()
    if (lower.startsWith("http://") || lower.startsWith("https://")) return trimmed
    if (trimmed.startsWith("/")) {
        val path = trimmed.trimStart('/')
        return if (forImage) "https://raw.githubusercontent.com/$owner/$repo/HEAD/$path"
        else "https://github.com/$path"
    }
    val base = if (forImage) "https://raw.githubusercontent.com/$owner/$repo/HEAD/"
    else "https://github.com/$owner/$repo/blob/HEAD/"
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

private fun spansOf(node: Node): List<Span> {
    val out = mutableListOf<Span>()
    for (child in childrenOf(node)) {
        when (child) {
            is Text -> out += Span.Text(child.literal)
            is Code -> out += Span.Code(child.literal)
            is Emphasis -> out += Span.Emphasis(spansOf(child))
            is StrongEmphasis -> out += Span.Strong(spansOf(child))
            is Strikethrough -> out += Span.Strike(spansOf(child))
            is SoftLineBreak -> out += Span.Text(" ")
            is HardLineBreak -> out += Span.Text("\n")
            is HtmlInline -> out += Span.Text(child.literal)
            is Link -> {
                val href = child.destination
                val inner = spansOf(child)
                out += if (isSafeHref(href)) Span.Hyperlink(href, inner, safe = true)
                else Span.Text(plainText(child))
            }
            is Image -> out += Span.Picture(alt = plainText(child), src = child.destination)
            else -> out += Span.Text(plainText(child))
        }
    }
    return out
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
            is HtmlBlock -> out += Block.RawText(child.literal.trim())
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
