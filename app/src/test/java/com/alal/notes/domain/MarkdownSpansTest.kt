package com.alal.notes.domain

import com.alal.notes.domain.markdown.MarkdownSpans
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownSpansTest {

    private fun scan(text: String) = MarkdownSpans.scan(text)

    @Test
    fun boldItalicUnderlineAreDetected() {
        val text = "a **bold** and *it* and ++u++"
        val spans = scan(text)
        assertEquals(listOf(4 until 8), spans.ranges(MarkdownSpans.BOLD))
        assertEquals("bold", text.substring(spans.ranges(MarkdownSpans.BOLD).single()))
        assertEquals("it", text.substring(spans.ranges(MarkdownSpans.ITALIC).single()))
        assertEquals("u", text.substring(spans.ranges(MarkdownSpans.UNDERLINE).single()))
        // Every marker is hidden as syntax.
        val syntax = spans.ranges(MarkdownSpans.SYNTAX).map { text.substring(it) }
        assertEquals(listOf("**", "**", "++", "++", "*", "*"), syntax)
    }

    @Test
    fun strikeHighlightCodeAndLinks() {
        val text = "~~gone~~ ==hi== `x*y` [Alal](https://example.com) tail"
        val spans = scan(text)
        assertEquals("gone", text.substring(spans.ranges(MarkdownSpans.STRIKE).single()))
        assertEquals("hi", text.substring(spans.ranges(MarkdownSpans.HIGHLIGHT).single()))
        assertEquals("x*y", text.substring(spans.ranges(MarkdownSpans.CODE).single()))
        assertEquals("Alal", text.substring(spans.ranges(MarkdownSpans.LINK).single()))
        // The star inside the code span must not start an italic run.
        assertTrue(spans.ranges(MarkdownSpans.ITALIC).isEmpty())
        // The URL part is hidden.
        assertTrue(spans.ranges(MarkdownSpans.SYNTAX).any { text.substring(it) == "](https://example.com)" })
    }

    @Test
    fun markersNeverCrossLines() {
        val text = "**open\nclose** and *a\nb*"
        val spans = scan(text)
        assertTrue(spans.ranges(MarkdownSpans.BOLD).isEmpty())
        assertTrue(spans.ranges(MarkdownSpans.ITALIC).isEmpty())
    }

    @Test
    fun blockMarkersAreDetected() {
        val text = "# Title\n## Sub\n### Small\n> quote\n- [ ] todo\n- [x] done\n- bullet\n12. ordered\n---"
        val spans = scan(text)
        assertEquals("# Title", text.substring(spans.ranges(MarkdownSpans.H1).single()))
        assertEquals("# ", text.substring(spans.ranges(MarkdownSpans.H1_MARK).single()))
        assertEquals("## Sub", text.substring(spans.ranges(MarkdownSpans.H2).single()))
        assertEquals("### Small", text.substring(spans.ranges(MarkdownSpans.H3).single()))
        assertEquals("quote", text.substring(spans.ranges(MarkdownSpans.QUOTE_TEXT).single()))
        assertEquals(2, spans.ranges(MarkdownSpans.CHECK_MARK).size)
        assertEquals("done", text.substring(spans.ranges(MarkdownSpans.CHECK_DONE).single()))
        assertEquals("-", text.substring(spans.ranges(MarkdownSpans.LIST_MARK).single()))
        assertEquals("12.", text.substring(spans.ranges(MarkdownSpans.ORDERED_MARK).single()))
        assertEquals("---", text.substring(spans.ranges(MarkdownSpans.RULE).single()))
    }

    @Test
    fun inlineStylingWorksInsideHeadingsAndLists() {
        val text = "# A **bold** title\n- item with *em*"
        val spans = scan(text)
        assertEquals("bold", text.substring(spans.ranges(MarkdownSpans.BOLD).single()))
        assertEquals("em", text.substring(spans.ranges(MarkdownSpans.ITALIC).single()))
    }

    @Test
    fun longNotesAreStyledCompletely() {
        // Regression: inline styling used to stop after 8 000 characters, leaving raw ** and ++
        // markers visible in long notes. Build a ~60k-character note and make sure the very
        // last paragraph is still styled.
        val paragraph = "Lorem ipsum **dolor** sit amet, ++consectetur++ adipiscing *elit* sed do eiusmod tempor.\n\n"
        val text = buildString { repeat(700) { append(paragraph) } }
        assertTrue(text.length > 50_000)
        val spans = scan(text)
        assertEquals(700, spans.ranges(MarkdownSpans.BOLD).size)
        assertEquals(700, spans.ranges(MarkdownSpans.UNDERLINE).size)
        assertEquals(700, spans.ranges(MarkdownSpans.ITALIC).size)
        val lastBold = spans.ranges(MarkdownSpans.BOLD).last()
        assertEquals("dolor", text.substring(lastBold))
        assertTrue(lastBold.first > text.length - paragraph.length)
    }

    @Test
    fun scanIsCheapEnoughForVeryLongNotes() {
        val text = buildString { repeat(4000) { append("Line $it with **b** and *i* and [l](u) and `c`\n") } }
        assertTrue(text.length > 150_000)
        val start = System.nanoTime()
        val spans = scan(text)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(4000, spans.ranges(MarkdownSpans.BOLD).size)
        // Generous bound for CI machines; the scanner is linear in the text length.
        assertTrue("scan took $ms ms", ms < 2_000)
    }

    @Test
    fun inlineStateAtReportsSurroundingStyles() {
        val text = "plain **bo|ld** ++u++".replace("|", "")
        val insideBold = text.indexOf("bold") + 2
        val state = MarkdownSpans.inlineStateAt(text, insideBold)
        assertTrue(state and MarkdownSpans.FLAG_BOLD != 0)
        assertTrue(state and MarkdownSpans.FLAG_UNDERLINE == 0)
        assertEquals(0, MarkdownSpans.inlineStateAt(text, 2))
        val insideUnderline = text.indexOf("++u") + 3
        assertTrue(MarkdownSpans.inlineStateAt(text, insideUnderline) and MarkdownSpans.FLAG_UNDERLINE != 0)
    }

    @Test
    fun unbalancedMarkersStayPlain() {
        val text = "2 ** 3 = 6 and 4 * 5"
        val spans = scan(text)
        assertTrue(spans.ranges(MarkdownSpans.BOLD).isEmpty())
        assertTrue(spans.ranges(MarkdownSpans.ITALIC).isEmpty())
    }
}
