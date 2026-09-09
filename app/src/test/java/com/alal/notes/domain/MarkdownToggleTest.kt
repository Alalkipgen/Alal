package com.alal.notes.domain

import com.alal.notes.domain.markdown.MarkdownToggle
import com.alal.notes.domain.markdown.PrefixKind
import com.alal.notes.domain.markdown.TextSelection
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownToggleTest {
    @Test
    fun wrapSelectionWithBold() {
        val r = MarkdownToggle.wrap("hello world", TextSelection(0, 5), "**")
        assertEquals("**hello** world", r.text)
        assertEquals(TextSelection(2, 7), r.selection)
    }

    @Test
    fun wrapCollapsedSelectionPlacesCursorInside() {
        val r = MarkdownToggle.wrap("ab", TextSelection(1), "*")
        assertEquals("a**b", r.text)
        assertEquals(TextSelection(2), r.selection)
    }

    @Test
    fun unwrapWhenSelectionIncludesMarkers() {
        val r = MarkdownToggle.wrap("**hello** world", TextSelection(0, 9), "**")
        assertEquals("hello world", r.text)
        assertEquals(TextSelection(0, 5), r.selection)
    }

    @Test
    fun unwrapWhenMarkersSurroundSelection() {
        val r = MarkdownToggle.wrap("**hello** world", TextSelection(2, 7), "**")
        assertEquals("hello world", r.text)
    }

    @Test
    fun asymmetricMarkersForHighlightAndUnderline() {
        assertEquals("==hi==", MarkdownToggle.wrap("hi", TextSelection(0, 2), "==").text)
        assertEquals("++hi++", MarkdownToggle.wrap("hi", TextSelection(0, 2), "++").text)
    }

    @Test
    fun headingPrefixIsAdded() {
        val r = MarkdownToggle.linePrefix("Title\nbody", TextSelection(0), "# ", PrefixKind.HEADING)
        assertEquals("# Title\nbody", r.text)
    }

    @Test
    fun sameHeadingPrefixIsRemoved() {
        val r = MarkdownToggle.linePrefix("# Title", TextSelection(3), "# ", PrefixKind.HEADING)
        assertEquals("Title", r.text)
    }

    @Test
    fun differentHeadingLevelReplaces() {
        val r = MarkdownToggle.linePrefix("# Title", TextSelection(3), "## ", PrefixKind.HEADING)
        assertEquals("## Title", r.text)
    }

    @Test
    fun listPrefixAppliesToEveryLineInSelection() {
        val text = "one\ntwo\nthree"
        val r = MarkdownToggle.linePrefix(text, TextSelection(0, text.length), "- ", PrefixKind.LIST)
        assertEquals("- one\n- two\n- three", r.text)
    }

    @Test
    fun checklistReplacesBullet() {
        val r = MarkdownToggle.linePrefix("- task", TextSelection(2), "- [ ] ", PrefixKind.LIST)
        assertEquals("- [ ] task", r.text)
    }

    @Test
    fun quoteToggle() {
        val on = MarkdownToggle.linePrefix("say", TextSelection(0), "> ", PrefixKind.QUOTE)
        assertEquals("> say", on.text)
        val off = MarkdownToggle.linePrefix(on.text, TextSelection(2), "> ", PrefixKind.QUOTE)
        assertEquals("say", off.text)
    }

    @Test
    fun continueBulletListOnEnter() {
        val old = "- item"
        val new = "- item\n"
        val r = MarkdownToggle.continueList(old, new, new.length)
        assertEquals("- item\n- ", r?.text)
    }

    @Test
    fun continueNumberedListIncrements() {
        val old = "1. a"
        val new = "1. a\n"
        val r = MarkdownToggle.continueList(old, new, new.length)
        assertEquals("1. a\n2. ", r?.text)
    }

    @Test
    fun enterOnEmptyListItemEndsList() {
        val old = "- a\n- "
        val new = "- a\n- \n"
        val r = MarkdownToggle.continueList(old, new, new.length)
        assertEquals("- a\n", r?.text)
    }
}
