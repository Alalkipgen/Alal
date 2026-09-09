package com.alal.notes.domain

import com.alal.notes.domain.markdown.Outline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutlineTest {

    @Test
    fun headingsAreExtractedWithLevelsAndOffsets() {
        val body = "# Intro\n\nSome text\n## Part **one**\nmore\n### Detail"
        val items = Outline.extract(body)
        assertEquals(3, items.size)
        assertEquals(1, items[0].level); assertEquals("Intro", items[0].text); assertEquals(0, items[0].offset)
        assertEquals(2, items[1].level); assertEquals("Part one", items[1].text); assertEquals(body.indexOf("## Part"), items[1].offset)
        assertEquals(3, items[2].level); assertEquals("Detail", items[2].text)
    }

    @Test
    fun paragraphsAreUsedWhenThereAreNoHeadings() {
        val body = "First paragraph line one\nline two\n\nSecond paragraph\n\n\nThird"
        val items = Outline.extract(body)
        assertEquals(3, items.size)
        assertTrue(items.all { it.level == 0 })
        assertEquals("First paragraph line one", items[0].text)
        assertEquals(body.indexOf("Second"), items[1].offset)
        assertEquals(body.indexOf("Third"), items[2].offset)
    }

    @Test
    fun longEntriesAreClipped() {
        val long = "a".repeat(100)
        val items = Outline.extract("# $long")
        assertEquals(61, items[0].text.length) // 60 chars + ellipsis
    }

    @Test
    fun blankBodyGivesNothing() {
        assertTrue(Outline.extract("").isEmpty())
        assertTrue(Outline.extract("  \n\n").isEmpty())
    }

    @Test
    fun myanmarHeadingsWork() {
        val heading = "\u1021\u1005\u1000\u103A\u1015\u102D\u102F\u1004\u103A\u1038" // အစက်ပိုင်း
        val bodyLine = "\u1005\u102C\u1000\u102D\u102F\u101A\u103A" // စာကိုယ်
        val items = Outline.extract("## $heading\n$bodyLine")
        assertEquals(heading, items[0].text)
    }
}
