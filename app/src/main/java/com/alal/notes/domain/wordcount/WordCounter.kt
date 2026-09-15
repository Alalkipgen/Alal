package com.alal.notes.domain.wordcount

import com.alal.notes.domain.model.WordCountMethod
import kotlin.math.ceil
import kotlin.math.max

/** Result of counting a piece of text. All fields are cheap to display. */
data class TextStats(
    val words: Int = 0,
    val myanmarWords: Int = 0,
    val latinWords: Int = 0,
    val chars: Int = 0,
    val charsNoSpaces: Int = 0,
    val sentences: Int = 0,
    val paragraphs: Int = 0,
    val readMinutes: Int = 0,
) {
    val avgWordsPerSentence: Float
        get() = if (sentences == 0) 0f else words.toFloat() / sentences

    companion object {
        val EMPTY = TextStats()
    }
}

/** Myanmar block + Myanmar Extended-A/B. */
fun isMyanmarChar(c: Char): Boolean =
    (c in '\u1000'..'\u109F') || (c in '\uA9E0'..'\uA9FF') || (c in '\uAA60'..'\uAA7F')

/**
 * One UAX #29 word segment produced by a [WordBreakEngine].
 *
 * [ruleStatus] follows ICU's `BreakIterator.getRuleStatus()` convention:
 *  - `0 ..  99` WORD_NONE   (spaces, punctuation)
 *  - `100..199` WORD_NUMBER (digits)
 *  - `200..299` WORD_LETTER (letters, incl. dictionary-segmented Myanmar words)
 *  - `300..399` WORD_KANA, `400..499` WORD_IDEO
 */
data class WordSegment(val start: Int, val end: Int, val ruleStatus: Int) {
    val isWordLike: Boolean get() = ruleStatus >= RULE_STATUS_NUMBER

    companion object {
        const val RULE_STATUS_NONE = 0
        const val RULE_STATUS_NUMBER = 100
        const val RULE_STATUS_LETTER = 200
    }
}

/**
 * Platform word-boundary provider (UAX #29 word boundaries for locale "my").
 *
 * Implementations must NOT split on Myanmar consonants or whitespace themselves;
 * they only report the boundaries and the rule status of each segment. The
 * production implementation is [AndroidIcuWordBreakEngine] (ICU dictionary
 * segmentation). Tests may plug in ICU4J or a fake.
 */
fun interface WordBreakEngine {
    fun segments(text: String): List<WordSegment>

    /** Allocation-free path used by the Android ICU implementation. */
    fun forEachSegment(text: String, action: (start: Int, end: Int, ruleStatus: Int) -> Unit) {
        for (segment in segments(text)) action(segment.start, segment.end, segment.ruleStatus)
    }
}

/**
 * Dependency-free fallback that treats every maximal run of letters/digits/marks
 * as one segment. It never splits inside a Myanmar run (no dictionary), so a
 * spaceless Burmese sentence counts as 1 word. Only used when no ICU engine is
 * available (plain-JVM unit tests); on device we always use ICU.
 */
object BasicWordBreakEngine : WordBreakEngine {
    override fun segments(text: String): List<WordSegment> {
        val out = ArrayList<WordSegment>()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            val kind = kindOf(c)
            var j = i + 1
            if (kind == WordSegment.RULE_STATUS_NONE) {
                while (j < n && kindOf(text[j]) == WordSegment.RULE_STATUS_NONE) j++
            } else {
                while (j < n) {
                    val k = kindOf(text[j])
                    val joiner = text[j] == '\'' || text[j] == '\u2019' || text[j] == '-' || text[j] == '_'
                    if (k != WordSegment.RULE_STATUS_NONE || (joiner && j + 1 < n && kindOf(text[j + 1]) != WordSegment.RULE_STATUS_NONE)) j++ else break
                }
            }
            out += WordSegment(i, j, kind)
            i = j
        }
        return out
    }

    private fun kindOf(c: Char): Int = when {
        Character.isDigit(c) -> WordSegment.RULE_STATUS_NUMBER
        Character.isLetter(c) -> WordSegment.RULE_STATUS_LETTER
        Character.getType(c) == Character.NON_SPACING_MARK.toInt() ||
            Character.getType(c) == Character.COMBINING_SPACING_MARK.toInt() ||
            Character.getType(c) == Character.ENCLOSING_MARK.toInt() -> WordSegment.RULE_STATUS_LETTER
        else -> WordSegment.RULE_STATUS_NONE
    }
}

/**
 * Counts words, characters, sentences and paragraphs.
 *
 * Word counting (default method) uses the platform's ICU dictionary-based word
 * segmentation (UAX #29, locale "my") via [WordBreakEngine]: every segment whose
 * rule status is `>= WORD_LETTER` is one word. Digit runs (`WORD_NUMBER`) are
 * counted as well so "3 apples" is two words, matching `Intl.Segmenter`'s
 * `isWordLike` and NSString `.byWords`. Nothing is split on Myanmar consonants
 * or on spaces by this class.
 */
class WordCounter(private val engine: WordBreakEngine = BasicWordBreakEngine) {

    fun count(text: CharSequence, method: WordCountMethod = WordCountMethod.MYANMAR_SYLLABLE): TextStats {
        if (text.isEmpty()) return TextStats.EMPTY
        val chars = text.length
        var charsNoSpaces = 0
        for (c in text) if (!c.isWhitespace()) charsNoSpaces++

        var myanmar = 0
        var latin = 0
        if (method == WordCountMethod.SPACE_SPLIT) {
            var inTok = false
            for (c in text) {
                if (c.isWhitespace()) inTok = false
                else if (!inTok) { inTok = true; latin++ }
            }
        } else {
            val s = text.toString()
            engine.forEachSegment(s) { start, end, ruleStatus ->
                if (ruleStatus >= WordSegment.RULE_STATUS_NUMBER) {
                    if (containsMyanmar(s, start, end)) myanmar++ else latin++
                }
            }
        }
        val words = myanmar + latin
        val sentences = countSentences(text)
        val paragraphs = countParagraphs(text)
        val minutes = if (words == 0) 0 else max(1, ceil(latin / 200.0 + myanmar / 150.0).toInt())
        return TextStats(
            words = words, myanmarWords = myanmar, latinWords = latin,
            chars = chars, charsNoSpaces = charsNoSpaces,
            sentences = sentences, paragraphs = paragraphs, readMinutes = minutes,
        )
    }

    private fun containsMyanmar(s: String, start: Int, end: Int): Boolean {
        for (k in start until end) if (isMyanmarChar(s[k])) return true
        return false
    }

    private fun countSentences(text: CharSequence): Int {
        var count = 0
        var content = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '.' || c == '!' || c == '?' || c == '\u104B') {
                if (content) count++
                content = false
                while (i + 1 < text.length && (text[i + 1] == '.' || text[i + 1] == '!' || text[i + 1] == '?' || text[i + 1] == '\u104B')) i++
            } else if (!c.isWhitespace()) content = true
            i++
        }
        if (content) count++
        return count
    }

    private fun countParagraphs(text: CharSequence): Int {
        var count = 0
        var inPara = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\n') {
                // blank line = a newline followed only by whitespace up to another newline
                var j = i + 1
                while (j < text.length && text[j] != '\n' && text[j].isWhitespace()) j++
                if (j < text.length && text[j] == '\n') { inPara = false; i = j; continue }
            } else if (!c.isWhitespace() && !inPara) {
                inPara = true; count++
            }
            i++
        }
        return count
    }
}
