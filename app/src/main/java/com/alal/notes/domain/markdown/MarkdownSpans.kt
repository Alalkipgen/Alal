package com.alal.notes.domain.markdown

/**
 * Pure-Kotlin scanner that turns the raw Markdown of a note into a flat list of styled ranges.
 *
 * It is used by the live editor to hide `**`, `*`, `++`, `~~`, `==`, `` ` `` and link syntax and to
 * colour headings, quotes and list markers. The scanner never modifies the text, so every range
 * maps 1:1 onto the stored note. It works line by line and needs no substring allocations, so it
 * is cheap enough to run on notes of several hundred thousand characters, which is why the editor
 * no longer needs the old 8 000 / 20 000 character styling cut-offs that left raw symbols visible
 * in long notes.
 *
 * Kinds are small ints (see the constants) so the UI layer can map them to styles with an array.
 */
object MarkdownSpans {

    // Inline kinds. Ranges of these kinds cover the *content* between the markers; the markers
    // themselves are emitted as SYNTAX so the UI can collapse them.
    const val SYNTAX = 0
    const val BOLD = 1
    const val ITALIC = 2
    const val UNDERLINE = 3
    const val STRIKE = 4
    const val HIGHLIGHT = 5
    const val CODE = 6
    const val LINK = 7

    // Block kinds.
    const val H1 = 8
    const val H2 = 9
    const val H3 = 10
    const val H1_MARK = 11
    const val H2_MARK = 12
    const val H3_MARK = 13
    const val QUOTE_MARK = 14
    const val QUOTE_TEXT = 15
    const val LIST_MARK = 16
    const val ORDERED_MARK = 17
    const val CHECK_MARK = 18
    const val CHECK_DONE = 19
    const val RULE = 20

    const val KIND_COUNT = 21

    // Bit flags for [inlineStateAt].
    const val FLAG_BOLD = 1
    const val FLAG_ITALIC = 1 shl 1
    const val FLAG_UNDERLINE = 1 shl 2
    const val FLAG_STRIKE = 1 shl 3
    const val FLAG_HIGHLIGHT = 1 shl 4
    const val FLAG_CODE = 1 shl 5

    /** Growable, allocation-light list of (kind, start, end) triples. */
    class SpanList(initialCapacity: Int = 64) {
        private var data = IntArray(maxOf(3, initialCapacity * 3))
        var size: Int = 0
            private set

        fun clear() { size = 0 }

        fun add(kind: Int, start: Int, end: Int) {
            if (end <= start) return
            val at = size * 3
            if (at + 3 > data.size) data = data.copyOf(data.size * 2)
            data[at] = kind
            data[at + 1] = start
            data[at + 2] = end
            size++
        }

        fun kind(i: Int): Int = data[i * 3]
        fun start(i: Int): Int = data[i * 3 + 1]
        fun end(i: Int): Int = data[i * 3 + 2]

        /** Test helper: all ranges of [kind] as start..end pairs. */
        fun ranges(kind: Int): List<IntRange> {
            val out = ArrayList<IntRange>()
            for (i in 0 until size) if (kind(i) == kind) out.add(start(i) until end(i))
            return out
        }
    }

    /** Scans the whole [text]. The result is appended to [out] (cleared first). */
    fun scan(text: CharSequence, out: SpanList = SpanList(maxOf(64, text.length / 40))): SpanList {
        out.clear()
        val n = text.length
        var lineStart = 0
        while (lineStart <= n) {
            var lineEnd = lineStart
            while (lineEnd < n && text[lineEnd] != '\n') lineEnd++
            if (lineEnd > lineStart) scanLine(text, lineStart, lineEnd, out)
            lineStart = lineEnd + 1
        }
        return out
    }

    /** Scans a single line `[lineStart, lineEnd)` (no newline inside) and appends to [out]. */
    fun scanLine(text: CharSequence, lineStart: Int, lineEnd: Int, out: SpanList) {
        val len = lineEnd - lineStart
        if (len <= 0) return
        var contentStart = lineStart
        when {
            text.has(lineStart, lineEnd, "### ") -> {
                out.add(H3_MARK, lineStart, lineStart + 4)
                out.add(H3, lineStart, lineEnd)
                contentStart = lineStart + 4
            }
            text.has(lineStart, lineEnd, "## ") -> {
                out.add(H2_MARK, lineStart, lineStart + 3)
                out.add(H2, lineStart, lineEnd)
                contentStart = lineStart + 3
            }
            text.has(lineStart, lineEnd, "# ") -> {
                out.add(H1_MARK, lineStart, lineStart + 2)
                out.add(H1, lineStart, lineEnd)
                contentStart = lineStart + 2
            }
            text.has(lineStart, lineEnd, "> ") -> {
                out.add(QUOTE_MARK, lineStart, lineStart + 1)
                out.add(QUOTE_TEXT, lineStart + 2, lineEnd)
                contentStart = lineStart + 2
            }
            text.has(lineStart, lineEnd, "- [") && len >= 6 && text[lineStart + 4] == ']' && text[lineStart + 5] == ' ' &&
                (text[lineStart + 3] == ' ' || text[lineStart + 3] == 'x' || text[lineStart + 3] == 'X') -> {
                out.add(CHECK_MARK, lineStart, lineStart + 5)
                if (text[lineStart + 3] != ' ') out.add(CHECK_DONE, lineStart + 6, lineEnd)
                contentStart = lineStart + 6
            }
            text.has(lineStart, lineEnd, "- ") || text.has(lineStart, lineEnd, "* ") -> {
                out.add(LIST_MARK, lineStart, lineStart + 1)
                contentStart = lineStart + 2
            }
            len == 3 && (text.has(lineStart, lineEnd, "---") || text.has(lineStart, lineEnd, "***")) -> {
                out.add(RULE, lineStart, lineEnd)
                return
            }
            else -> {
                // "1. ", "12. ", "123. " ordered-list markers
                var d = 0
                while (d < 3 && lineStart + d < lineEnd && text[lineStart + d].isDigit()) d++
                if (d in 1..3 && lineStart + d + 1 < lineEnd && text[lineStart + d] == '.' && text[lineStart + d + 1] == ' ') {
                    out.add(ORDERED_MARK, lineStart, lineStart + d + 1)
                    contentStart = lineStart + d + 2
                }
            }
        }
        if (contentStart < lineEnd) inlineLine(text, contentStart, lineEnd, out)
    }

    /**
     * Bit set of inline styles (FLAG_*) active at [offset], e.g. to highlight toolbar buttons.
     * Only the caret's line is scanned, so this is safe to call on every caret move.
     */
    fun inlineStateAt(text: CharSequence, offset: Int): Int {
        val n = text.length
        if (n == 0) return 0
        val o = offset.coerceIn(0, n)
        var s = o
        while (s > 0 && text[s - 1] != '\n') s--
        var e = o
        while (e < n && text[e] != '\n') e++
        if (e <= s) return 0
        val spans = SpanList(8)
        scanLine(text, s, e, spans)
        var flags = 0
        for (i in 0 until spans.size) {
            if (o < spans.start(i) || o > spans.end(i)) continue
            flags = flags or when (spans.kind(i)) {
                BOLD -> FLAG_BOLD
                ITALIC -> FLAG_ITALIC
                UNDERLINE -> FLAG_UNDERLINE
                STRIKE -> FLAG_STRIKE
                HIGHLIGHT -> FLAG_HIGHLIGHT
                CODE -> FLAG_CODE
                else -> 0
            }
        }
        return flags
    }

    // ------------------------------------------------------------------ inline

    private fun inlineLine(text: CharSequence, s: Int, e: Int, out: SpanList) {
        // 1) Inline code first: anything inside back-ticks is protected from the other markers.
        var protectedRanges: IntArray? = null
        var codeCount = 0
        var i = s
        while (i < e) {
            if (text[i] != '`') { i++; continue }
            val close = indexOf(text, '`', i + 1, e)
            if (close < 0) break
            if (close > i + 1) {
                out.add(SYNTAX, i, i + 1)
                out.add(CODE, i + 1, close)
                out.add(SYNTAX, close, close + 1)
                var ranges = protectedRanges ?: IntArray(8)
                if (codeCount * 2 + 2 > ranges.size) ranges = ranges.copyOf(ranges.size * 2)
                ranges[codeCount * 2] = i
                ranges[codeCount * 2 + 1] = close + 1
                protectedRanges = ranges
                codeCount++
            }
            i = close + 1
        }

        // 2) Double-character markers.
        pair(text, s, e, '*', BOLD, protectedRanges, codeCount, out)
        pair(text, s, e, '=', HIGHLIGHT, protectedRanges, codeCount, out)
        pair(text, s, e, '+', UNDERLINE, protectedRanges, codeCount, out)
        pair(text, s, e, '~', STRIKE, protectedRanges, codeCount, out)

        // 3) Single `*` italic that is not part of `**`.
        singleStar(text, s, e, protectedRanges, codeCount, out)

        // 4) [label](url) links.
        links(text, s, e, protectedRanges, codeCount, out)
    }

    /** Index just past the protected range containing [i], or [i] itself when unprotected. */
    private fun skipProtected(i: Int, ranges: IntArray?, count: Int): Int {
        if (ranges == null) return i
        for (k in 0 until count) {
            val a = ranges[k * 2]
            val b = ranges[k * 2 + 1]
            if (i >= a && i < b) return b
        }
        return i
    }

    private fun isProtected(i: Int, ranges: IntArray?, count: Int): Boolean = skipProtected(i, ranges, count) != i

    /** Finds the next `cc` (two identical chars) at or after [from] that is not protected. */
    private fun findDouble(text: CharSequence, c: Char, from: Int, e: Int, ranges: IntArray?, count: Int): Int {
        var i = from
        while (i + 1 < e) {
            val j = skipProtected(i, ranges, count)
            if (j != i) { i = j; continue }
            if (text[i] == c && text[i + 1] == c) return i
            i++
        }
        return -1
    }

    private fun pair(text: CharSequence, s: Int, e: Int, c: Char, kind: Int, ranges: IntArray?, count: Int, out: SpanList) {
        var i = s
        while (i + 1 < e) {
            val open = findDouble(text, c, i, e, ranges, count)
            if (open < 0) return
            val close = findDouble(text, c, open + 2, e, ranges, count)
            if (close < 0) return
            if (close > open + 2) {
                out.add(SYNTAX, open, open + 2)
                out.add(kind, open + 2, close)
                out.add(SYNTAX, close, close + 2)
                i = close + 2
            } else {
                // "****" - treat the first pair as a stray marker and keep looking.
                i = open + 2
            }
        }
    }

    private fun singleStar(text: CharSequence, s: Int, e: Int, ranges: IntArray?, count: Int, out: SpanList) {
        var i = s
        var open = -1
        while (i < e) {
            val j = skipProtected(i, ranges, count)
            if (j != i) { i = j; continue }
            if (text[i] == '*') {
                if (i + 1 < e && text[i + 1] == '*') { i += 2; continue }
                if (open < 0) {
                    open = i
                } else if (i > open + 1) {
                    out.add(SYNTAX, open, open + 1)
                    out.add(ITALIC, open + 1, i)
                    out.add(SYNTAX, i, i + 1)
                    open = -1
                } else {
                    open = i
                }
            }
            i++
        }
    }

    private fun links(text: CharSequence, s: Int, e: Int, ranges: IntArray?, count: Int, out: SpanList) {
        var i = s
        while (i < e) {
            val lb = indexOf(text, '[', i, e)
            if (lb < 0) return
            if (isProtected(lb, ranges, count)) { i = skipProtected(lb, ranges, count); continue }
            val rb = indexOf(text, ']', lb + 1, e)
            if (rb < 0) return
            if (rb + 1 < e && text[rb + 1] == '(' && rb > lb + 1) {
                val close = indexOf(text, ')', rb + 2, e)
                if (close > rb + 2) {
                    out.add(SYNTAX, lb, lb + 1)
                    out.add(LINK, lb + 1, rb)
                    // Hide the closing bracket, parentheses and URL; only the linked label remains.
                    out.add(SYNTAX, rb, close + 1)
                    i = close + 1
                    continue
                }
            }
            i = rb + 1
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Allocation-free `startsWith` for the line that spans [start, end). */
    private fun CharSequence.has(start: Int, end: Int, prefix: String): Boolean {
        if (end - start < prefix.length) return false
        for (i in prefix.indices) if (this[start + i] != prefix[i]) return false
        return true
    }

    private fun indexOf(text: CharSequence, c: Char, from: Int, end: Int): Int {
        var i = from
        while (i < end) {
            if (text[i] == c) return i
            i++
        }
        return -1
    }
}
