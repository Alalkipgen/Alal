package com.alal.notes.domain

import com.alal.notes.domain.model.WordCountMethod
import com.alal.notes.domain.wordcount.WordCounter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordCounterTest {
    private val counter = WordCounter()

    @Test
    fun emptyTextIsZero() {
        val s = counter.count("")
        assertEquals(0, s.words)
        assertEquals(0, s.chars)
        assertEquals(0, s.paragraphs)
    }

    @Test
    fun latinWordsAreCountedBySpaces() {
        val s = counter.count("The quick brown fox jumps over the lazy dog.")
        assertEquals(9, s.words)
        assertEquals(9, s.latinWords)
        assertEquals(0, s.myanmarWords)
        assertEquals(1, s.sentences)
        assertEquals(1, s.paragraphs)
    }

    @Test
    fun charactersWithAndWithoutSpaces() {
        val s = counter.count("ab cd")
        assertEquals(5, s.chars)
        assertEquals(4, s.charsNoSpaces)
    }

    @Test
    fun myanmarTextProducesWords() {
        // "Mingalaba" — a single run of Myanmar script with no spaces (default
        // engine has no dictionary, so it is 1 word; ICU cases live in MyanmarWordCountTest).
        val s = counter.count("\u1019\u1004\u103A\u1002\u101C\u102C\u1015\u102B")
        assertTrue("expected at least one Myanmar word", s.myanmarWords >= 1)
        assertEquals(s.myanmarWords, s.words)
        assertEquals(0, s.latinWords)
    }

    @Test
    fun myanmarSentenceTerminatorIsRecognised() {
        val s = counter.count("\u1000\u102D\u102F\u1015\u102B\u104B \u1000\u102D\u102F\u1015\u102B\u104B")
        assertEquals(2, s.sentences)
    }

    @Test
    fun mixedScriptCountsBoth() {
        val s = counter.count("Yangon \u101B\u1014\u103A\u1000\u102F\u1014\u103A city")
        assertEquals(2, s.latinWords)
        assertTrue(s.myanmarWords >= 1)
        assertEquals(s.latinWords + s.myanmarWords, s.words)
    }

    @Test
    fun spaceSplitMethodIgnoresScript() {
        val s = counter.count("\u1000\u102D\u102F hello \u1015\u102B", WordCountMethod.SPACE_SPLIT)
        assertEquals(3, s.words)
    }

    @Test
    fun paragraphsSplitOnBlankLines() {
        val s = counter.count("one two\n\nthree\n\n\nfour")
        assertEquals(3, s.paragraphs)
        assertEquals(4, s.words)
    }

    @Test
    fun readMinutesIsAtLeastOneForShortText() {
        assertTrue(counter.count("hello").readMinutes >= 1)
    }
}
