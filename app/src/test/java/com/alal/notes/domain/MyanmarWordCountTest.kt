package com.alal.notes.domain

import com.alal.notes.domain.wordcount.WordBreakEngine
import com.alal.notes.domain.wordcount.WordCounter
import com.alal.notes.domain.wordcount.WordSegment
import com.ibm.icu.text.BreakIterator
import com.ibm.icu.util.ULocale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM twin of AndroidIcuWordBreakEngine, backed by ICU4J so the unit tests use
 * the same UAX #29 + Burmese dictionary segmentation the device uses.
 */
class Icu4jWordBreakEngine : WordBreakEngine {
    private val template: BreakIterator = BreakIterator.getWordInstance(ULocale("my"))

    override fun segments(text: String): List<WordSegment> {
        if (text.isEmpty()) return emptyList()
        val it = template.clone() as BreakIterator
        it.setText(text)
        val out = ArrayList<WordSegment>()
        var start = it.first()
        var end = it.next()
        while (end != BreakIterator.DONE) {
            out += WordSegment(start, end, it.ruleStatus)
            start = end
            end = it.next()
        }
        return out
    }
}

class MyanmarWordCountTest {
    private val counter = WordCounter(Icu4jWordBreakEngine())

    // မနက်တိုင်း ရွာက တိတ်ဆိတ်စွာ နိုးထလာသည်။
    private val sentence1 =
        "\u1019\u1014\u1000\u103A\u1010\u102D\u102F\u1004\u103A\u1038 " +
            "\u101B\u103D\u102C\u1000 " +
            "\u1010\u102D\u1010\u103A\u1006\u102D\u1010\u103A\u1005\u103D\u102C " +
            "\u1014\u102D\u102F\u1038\u1011\u101C\u102C\u101E\u100A\u103A\u104B"

    // ထမင်းစားပြီးပြီလား  (no spaces at all)
    private val sentence2 =
        "\u1011\u1019\u1004\u103A\u1038\u1005\u102C\u1038\u1015\u103C\u102E\u1038\u1015\u103C\u102E\u101C\u102C\u1038"

    @Test
    fun spacedBurmeseSentenceMatchesIcuDictionary() {
        val s = counter.count(sentence1)
        // ICU 76.1 yields 9 lexical words:
        // မနက် + တိုင်း, ရွာ + က, တိတ်ဆိတ်စွာ, နိုး + ထ + လာ + သည်.
        // The previous expected value (10) treated a syllable inside တိတ်ဆိတ်စွာ as a word,
        // contradicting the dictionary-based behavior this test is meant to verify.
        assertEquals("words in: $sentence1", 9, s.words)
        assertEquals(9, s.myanmarWords)
        assertEquals(0, s.latinWords)
        assertEquals(1, s.sentences)
    }

    @Test
    fun spacelessBurmeseIsSegmentedByDictionaryNotSyllables() {
        val s = counter.count(sentence2)
        // Six syllables; the dictionary should yield 4 or 5 words, never 6.
        assertTrue("expected 4 or 5 words, got ${s.words}", s.words == 4 || s.words == 5)
        assertEquals(s.words, s.myanmarWords)
    }

    @Test
    fun singleDictionaryWordIsNotSplitOnConsonants() {
        // မင်္ဂလာပါ = "mingalaba", 4 syllables but one lexical greeting.
        val s = counter.count("\u1019\u1004\u103A\u1039\u1002\u101C\u102C\u1015\u102B")
        assertTrue("got ${s.words}", s.words in 1..2)
    }

    @Test
    fun myanmarPunctuationAndSpacesAreNotWords() {
        // Only ၊ ။ and spaces.
        val s = counter.count("\u104A \u104B  \u104B")
        assertEquals(0, s.words)
    }

    @Test
    fun mixedBurmeseAndEnglishWithIcu() {
        val s = counter.count("Yangon \u101B\u1014\u103A\u1000\u102F\u1014\u103A city 2025")
        assertEquals(3, s.latinWords) // Yangon, city, 2025
        assertTrue(s.myanmarWords >= 1)
        assertEquals(s.latinWords + s.myanmarWords, s.words)
    }
}

/** Engine-independent checks of how WordCounter interprets segments. */
class WordCounterSegmentLogicTest {
    private fun engine(vararg segs: WordSegment) = WordBreakEngine { segs.toList() }

    @Test
    fun onlyLetterAndNumberSegmentsAreCounted() {
        val text = "ab, 12 \u1000\u102C"
        val counter = WordCounter(engine(
            WordSegment(0, 2, WordSegment.RULE_STATUS_LETTER),   // ab
            WordSegment(2, 3, WordSegment.RULE_STATUS_NONE),     // ,
            WordSegment(3, 4, WordSegment.RULE_STATUS_NONE),     // space
            WordSegment(4, 6, WordSegment.RULE_STATUS_NUMBER),   // 12
            WordSegment(6, 7, WordSegment.RULE_STATUS_NONE),     // space
            WordSegment(7, 9, WordSegment.RULE_STATUS_LETTER),   // ကာ
        ))
        val s = counter.count(text)
        assertEquals(3, s.words)
        assertEquals(1, s.myanmarWords)
        assertEquals(2, s.latinWords)
    }

    @Test
    fun spaceSplitMethodBypassesEngine() {
        val counter = WordCounter(engine()) // engine returns nothing
        val s = counter.count("\u1000\u102D\u102F hello \u1015\u102B", com.alal.notes.domain.model.WordCountMethod.SPACE_SPLIT)
        assertEquals(3, s.words)
    }
}
