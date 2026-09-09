package com.alal.notes.domain

import com.alal.notes.domain.markdown.AutoTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoTitleTest {
    @Test
    fun explicitTitleWins() {
        assertEquals("My title", AutoTitle.from("  My title ", "# body heading"))
    }

    @Test
    fun firstNonEmptyLineIsUsed() {
        assertEquals("Second line", AutoTitle.from("", "\n\n  \nSecond line\nThird"))
    }

    @Test
    fun markdownIsStripped() {
        assertEquals("Headline here", AutoTitle.from("", "# **Headline** *here*"))
    }

    @Test
    fun truncatedToSixtyCharacters() {
        val long = "a".repeat(100)
        val t = AutoTitle.from("", long)
        assertEquals(AutoTitle.MAX, t.length)
    }

    @Test
    fun emptyBodyGivesEmptyTitle() {
        assertTrue(AutoTitle.from("", "   \n\n").isEmpty())
    }

    @Test
    fun myanmarLineIsPreserved() {
        val line = "\u1021\u1000\u103A\u1005\u102C\u1005\u102C\u1001\u1036\u1038"
        assertEquals(line, AutoTitle.from("", "## $line\nmore"))
    }
}
