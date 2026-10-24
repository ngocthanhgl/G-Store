package dev.forkly.details

import dev.forkly.ui.Block
import dev.forkly.ui.Span
import dev.forkly.ui.canAutoLoadMarkdownImage
import dev.forkly.ui.parseMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadmeMarkdownTest {
    @Test
    fun `bare external urls use compact clickable host labels`() {
        val paragraph = parseMarkdown(
            "See https://fmhy.net and https://trendshift.io.",
        ).filterIsInstance<Block.Paragraph>().single()

        assertEquals(
            listOf("fmhy.net", "trendshift.io"),
            paragraph.spans.filterIsInstance<Span.Hyperlink>().map {
                (it.children.single() as Span.Text).text
            },
        )
    }

    @Test
    fun `linked GitHub achievement badges keep image and destination`() {
        val blocks = parseMarkdown(
            """<p align="center"><a href="https://github.com/owner/repo"><img src="https://camo.githubusercontent.com/badge.svg" alt="Top repository of the week"></a></p>""",
        )
        val badge = blocks.filterIsInstance<Block.Paragraph>().single()
            .spans.filterIsInstance<Span.Picture>().single()

        assertEquals("Top repository of the week", badge.alt)
        assertEquals("https://github.com/owner/repo", badge.href)
        assertTrue(canAutoLoadMarkdownImage(badge.src))
    }
}
