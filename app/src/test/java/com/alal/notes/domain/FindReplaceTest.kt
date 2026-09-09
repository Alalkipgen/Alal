package com.alal.notes.domain

import com.alal.notes.domain.findreplace.FindOptions
import com.alal.notes.domain.findreplace.FindReplace
import com.alal.notes.domain.findreplace.FindResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FindReplaceTest {
    private val plain = FindOptions()

    private fun matches(r: FindResult) = (r as FindResult.Matches).matches

    @Test
    fun emptyQueryIsEmptyResult() {
        assertTrue(FindReplace.find("abc", "", plain) is FindResult.Empty)
    }

    @Test
    fun caseInsensitiveByDefault() {
        val m = matches(FindReplace.find("Cat cat CAT", "cat", plain))
        assertEquals(3, m.size)
        assertEquals(0, m[0].start)
        assertEquals(3, m[0].end)
    }

    @Test
    fun caseSensitiveOption() {
        val m = matches(FindReplace.find("Cat cat CAT", "cat", FindOptions(caseSensitive = true)))
        assertEquals(1, m.size)
        assertEquals(4, m[0].start)
    }

    @Test
    fun wholeWordOption() {
        val m = matches(FindReplace.find("cat category concat cat", "cat", FindOptions(wholeWord = true)))
        assertEquals(2, m.size)
    }

    @Test
    fun regexOption() {
        val m = matches(FindReplace.find("a1 b22 c333", "[a-z]\\d+", FindOptions(regex = true)))
        assertEquals(3, m.size)
    }

    @Test
    fun invalidRegexReportsError() {
        val r = FindReplace.find("abc", "(", FindOptions(regex = true))
        assertTrue(r is FindResult.Error)
    }

    @Test
    fun specialCharactersAreLiteralWithoutRegex() {
        val m = matches(FindReplace.find("1+1=2 (ok)", "(ok)", plain))
        assertEquals(1, m.size)
    }

    @Test
    fun myanmarTextIsSearchable() {
        val word = "\u101B\u1014\u103A\u1000\u102F\u1014\u103A"
        val text = "$word \u1019\u103E\u102C $word"
        val m = matches(FindReplace.find(text, word, plain))
        assertEquals(2, m.size)
    }

    @Test
    fun replaceOneReplacesOnlyThatMatch() {
        val text = "cat cat"
        val m = matches(FindReplace.find(text, "cat", plain))
        val out = FindReplace.replaceOne(text, m[1], "cat", "dog", plain).getOrThrow()
        assertEquals("cat dog", out)
    }

    @Test
    fun replaceAllReturnsCount() {
        val (out, n) = FindReplace.replaceAll("a-a-a", "a", "b", plain).getOrThrow()
        assertEquals("b-b-b", out)
        assertEquals(3, n)
    }

    @Test
    fun replaceAllWithRegexGroups() {
        val (out, _) = FindReplace.replaceAll("2026-09-09", "(\\d+)-(\\d+)-(\\d+)", "$3/$2/$1", FindOptions(regex = true)).getOrThrow()
        assertEquals("09/09/2026", out)
    }

    @Test
    fun replaceAllWithInvalidRegexFails() {
        assertTrue(FindReplace.replaceAll("x", "[", "y", FindOptions(regex = true)).isFailure)
    }
}
