package com.alal.notes.domain.wordcount

import android.icu.text.BreakIterator
import java.util.Locale

/**
 * Production [WordBreakEngine]: the platform ICU word break iterator for locale
 * "my" (UAX #29 word boundaries + ICU's Burmese dictionary, available on every
 * device since Android 7 / API 24; this app's minSdk is 26).
 *
 * Each boundary's `ruleStatus` is reported unchanged so [WordCounter] can keep
 * only `WORD_LETTER` (>= 200) / `WORD_NUMBER` (>= 100) segments. No splitting on
 * Myanmar consonants or on spaces happens here.
 */
class AndroidIcuWordBreakEngine(locale: Locale = Locale.forLanguageTag("my")) : WordBreakEngine {

    // BreakIterator is not thread-safe; keep one template and clone per call
    // (cloning is far cheaper than building a new dictionary iterator).
    private val template: BreakIterator by lazy { BreakIterator.getWordInstance(locale) }


    override fun forEachSegment(text: String, action: (start: Int, end: Int, ruleStatus: Int) -> Unit) {
        if (text.isEmpty()) return
        val it = template.clone() as BreakIterator
        it.setText(text)
        var start = it.first()
        var end = it.next()
        while (end != BreakIterator.DONE) {
            action(start, end, it.ruleStatus)
            start = end
            end = it.next()
        }
    }

    override fun segments(text: String): List<WordSegment> {
        if (text.isEmpty()) return emptyList()
        val it = template.clone() as BreakIterator
        it.setText(text)
        val out = ArrayList<WordSegment>(text.length / 3 + 1)
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
