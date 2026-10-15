package dev.gstore.readme

import dev.gstore.ui.Block
import dev.gstore.ui.MdListItem
import dev.gstore.ui.Span
import dev.gstore.ui.isSafeHref
import dev.gstore.ui.parseMarkdown
import dev.gstore.ui.resolveRelative
import dev.gstore.ui.slugify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {

    @Test fun `headings carry level text and github-style anchor`() {
        val blocks = parseMarkdown("# Hello, World!\n### Sub_section 2\n")
        assertEquals(
            listOf(
                Block.Heading(1, "Hello, World!", "hello-world"),
                Block.Heading(3, "Sub_section 2", "sub_section-2"),
            ),
            blocks,
        )
    }

    @Test fun `inline styles nest correctly`() {
        val blocks = parseMarkdown("A *b* **c** `d` ~~e~~.\n")
        val para = blocks.single() as Block.Paragraph
        assertEquals(9, para.spans.size)
        assertTrue(para.spans[1] is Span.Emphasis)
        assertTrue(para.spans[3] is Span.Strong)
        assertEquals(Span.Code("d"), para.spans[5])
        assertTrue(para.spans[7] is Span.Strike)
    }

    @Test fun `https link stays clickable`() {
        val blocks = parseMarkdown("[site](https://example.com/x)\n")
        val link = (blocks.single() as Block.Paragraph).spans.single() as Span.Hyperlink
        assertTrue(link.safe)
        assertEquals("https://example.com/x", link.href)
    }

    @Test fun `javascript href degrades to inert text`() {
        val blocks = parseMarkdown("[click](javascript:alert(1))\n")
        val para = blocks.single() as Block.Paragraph
        assertFalse(para.spans.any { it is Span.Hyperlink })
        assertEquals("click", (para.spans.single() as Span.Text).text)
    }

    @Test fun `data href degrades to inert text`() {
        assertFalse(isSafeHref("data:text/html,<b>x</b>"))
        assertFalse(isSafeHref("VbScript:msgbox(1)"))
        assertTrue(isSafeHref("docs/guide.md"))
        assertTrue(isSafeHref("#install"))
    }

    @Test fun `relative urls resolve against repo context`() {
        assertEquals(
            "https://github.com/o/r/blob/main/docs/a.md",
            resolveRelative("docs/a.md", "o", "r", forImage = false),
        )
        assertEquals(
            "https://raw.githubusercontent.com/o/r/main/img/a.png",
            resolveRelative("img/a.png", "o", "r", forImage = true),
        )
        assertEquals(
            "https://github.com/o/r#install",
            resolveRelative("#install", "o", "r", forImage = false),
        )
        assertEquals(
            "https://github.com/o/r/blob/main/a.md",
            resolveRelative("docs/../a.md", "o", "r", forImage = false),
        )
        assertEquals(
            "https://example.com/x",
            resolveRelative("https://example.com/x", "o", "r", forImage = true),
        )
    }

    @Test fun `fenced code keeps language and body`() {
        val blocks = parseMarkdown("```kotlin\nval x = 1\n```\n")
        assertEquals(Block.CodeBlock("kotlin", "val x = 1\n"), blocks.single())
    }

    @Test fun `task lists record checked state`() {
        val blocks = parseMarkdown("- [x] done\n- [ ] todo\n")
        val items = (blocks.single() as Block.ListBlock).items
        assertEquals(listOf(true, false), items.map { it.checked })
        assertEquals("done", ((items[0].spans.single()) as Span.Text).text)
    }

    @Test fun `nested lists become children`() {
        val blocks = parseMarkdown("- a\n  - b\n")
        val top = (blocks.single() as Block.ListBlock).items.single()
        assertEquals(1, top.children.size)
        val nested = top.children.single() as Block.ListBlock
        assertEquals("b", ((nested.items.single().spans.single()) as Span.Text).text)
    }

    @Test fun `tables keep header and row order`() {
        val blocks = parseMarkdown("| A | B |\n|---|---|\n| 1 | 2 |\n| 3 | 4 |\n")
        assertEquals(
            Block.Table(listOf("A", "B"), listOf(listOf("1", "2"), listOf("3", "4"))),
            blocks.single(),
        )
    }

    @Test fun `quotes rules and raw html`() {
        val blocks = parseMarkdown("> note\n\n---\n\n<div>hi</div>\n")
        assertTrue(blocks[0] is Block.Quote)
        assertEquals(Block.Rule, blocks[1])
        // Inline HTML renders as text, never raw tags.
        val para = blocks[2] as Block.Paragraph
        assertTrue(para.spans.any { it is Span.Text && it.text.contains("hi") })
    }

    @Test fun `images keep alt and relative src`() {
        val blocks = parseMarkdown("![logo](img/logo.png)\n")
        val para = blocks.single() as Block.Paragraph
        assertEquals(Span.Picture("logo", "img/logo.png"), para.spans.single())
    }

    @Test fun `empty document parses to nothing`() {
        assertTrue(parseMarkdown("").isEmpty())
        assertTrue(parseMarkdown("\n\n").isEmpty())
    }
}
