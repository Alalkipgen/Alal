package com.alal.notes.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.alal.notes.domain.markdown.MarkdownToggle
import kotlin.math.abs

/**
 * Continues Markdown lists when the user presses Enter (`- `, `1. `, `- [ ] `, `> `).
 * Detects a single inserted newline by comparing with the original buffer.
 */
@OptIn(ExperimentalFoundationApi::class)
object ListContinuationTransformation : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        if (length != originalText.length + 1) return
        val cursor = selection.start
        if (!selection.collapsed || cursor <= 0 || charAt(cursor - 1) != '\n') return
        val result = MarkdownToggle.continueList(originalText.toString(), asCharSequence().toString(), cursor) ?: return
        val newText = result.text
        // Replace only the tail after the cursor position where the change begins.
        val current = asCharSequence().toString()
        var prefix = 0
        val maxPrefix = minOf(current.length, newText.length)
        while (prefix < maxPrefix && current[prefix] == newText[prefix]) prefix++
        replace(prefix, current.length, newText.substring(prefix))
        selection = TextRange(result.selection.start.coerceIn(0, length), result.selection.end.coerceIn(0, length))
    }
}

/**
 * Two-finger pinch that runs in the Initial pointer pass so it wins over the text field's
 * own gestures. Reports cumulative zoom ratio steps; consumer maps them to font size.
 */
fun Modifier.pinchToZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(onZoom) {
    awaitEachGesture {
        var lastDistance = -1f
        var zoomAccum = 1f
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                val a = pressed[0].position
                val b = pressed[1].position
                val d = (a - b).getDistance()
                if (lastDistance > 0f && d > 0f) {
                    zoomAccum *= d / lastDistance
                    if (abs(zoomAccum - 1f) > 0.08f) {
                        onZoom(zoomAccum)
                        zoomAccum = 1f
                    }
                }
                lastDistance = d
                event.changes.forEach { it.consume() }
            } else {
                lastDistance = -1f
                zoomAccum = 1f
            }
            if (event.changes.none { it.pressed }) break
        }
    }
}

/**
 * Live inline Markdown styling (headings, bold, italic, underline, strikethrough, highlight,
 * inline code, quotes) drawn on top of the raw text, plus optional paragraph focus dimming.
 * The buffer text itself is never modified, so offsets stay identical to the stored note.
 */
@OptIn(ExperimentalFoundationApi::class)
class MarkdownOutputTransformation(
    private val accent: Color,
    private val onSurface: Color,
    private val muted: Color,
    private val highlight: Color,
    private val titleFont: FontFamily,
    private val bodySize: TextUnit,
    private val paragraphFocus: Boolean,
    initialCursor: Int = -1,
) : OutputTransformation {

    /**
     * Caret offset used by paragraph focus. It is snapshot state rather than a constructor
     * argument so that moving the caret does not force a brand-new transformation object (which
     * would make the text field throw away its whole layout on every cursor move).
     */
    var cursor: Int by mutableIntStateOf(initialCursor)

    private companion object {
        /** Keep focus/IME changes cheap on long notes by avoiding full-buffer span work. */
        const val STYLE_LIMIT = 20_000

        /**
         * Inline markers are the expensive half (five full scans plus a span per match), so they
         * stop earlier than block styling. Long notes keep heading / list / quote colouring and
         * stay responsive while typing.
         */
        const val INLINE_LIMIT = 8_000
    }

    override fun TextFieldBuffer.transformOutput() {
        val text = asCharSequence()
        val n = text.length
        if (n == 0 || n > STYLE_LIMIT) return
        val syntax = SpanStyle(color = muted.copy(alpha = 0.55f))

        // --- block level: iterate over lines
        var lineStart = 0
        var focusStart = -1
        var focusEnd = -1
        while (lineStart <= n) {
            var lineEnd = lineStart
            while (lineEnd < n && text[lineEnd] != '\n') lineEnd++
            // No substring/toString here: the old code allocated a String per line on every
            // output pass, which is what made typing in a long note stutter.
            val len = lineEnd - lineStart
            when {
                text.has(lineStart, lineEnd, "### ") -> heading(lineStart, lineEnd, 4, bodySize * 1.15f)
                text.has(lineStart, lineEnd, "## ") -> heading(lineStart, lineEnd, 3, bodySize * 1.3f)
                text.has(lineStart, lineEnd, "# ") -> heading(lineStart, lineEnd, 2, bodySize * 1.5f)
                text.has(lineStart, lineEnd, "> ") -> {
                    addStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold), lineStart, lineStart + 1)
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic, color = muted), lineStart + 2, lineEnd)
                }
                text.has(lineStart, lineEnd, "- [") && len >= 6 && text[lineStart + 4] == ']' && text[lineStart + 5] == ' ' -> {
                    val mark = text[lineStart + 3]
                    if (mark == ' ' || mark == 'x' || mark == 'X') {
                        addStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold), lineStart, lineStart + 5)
                        if (mark != ' ') {
                            addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = muted), lineStart + 6, lineEnd)
                        }
                    }
                }
                text.has(lineStart, lineEnd, "- ") || text.has(lineStart, lineEnd, "* ") ->
                    addStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold), lineStart, lineStart + 1)
                len == 3 && (text.has(lineStart, lineEnd, "---") || text.has(lineStart, lineEnd, "***")) ->
                    addStyle(SpanStyle(color = muted.copy(alpha = 0.5f), letterSpacing = 4.sp), lineStart, lineEnd)
                else -> {
                    // "1. ", "12. ", "123. " ordered-list markers
                    var d = 0
                    while (d < 3 && lineStart + d < lineEnd && text[lineStart + d].isDigit()) d++
                    if (d in 1..3 && lineStart + d + 1 < lineEnd && text[lineStart + d] == '.' && text[lineStart + d + 1] == ' ') {
                        addStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold), lineStart, lineStart + d + 1)
                    }
                }
            }
            if (paragraphFocus && focusStart < 0 && cursor >= lineStart && cursor <= lineEnd) {
                // Expand to paragraph boundaries (blank lines)
                var ps = lineStart
                while (ps > 0) {
                    val prevEnd = ps - 1
                    var prevStart = prevEnd
                    while (prevStart > 0 && text[prevStart - 1] != '\n') prevStart--
                    if (prevStart == prevEnd) break
                    ps = prevStart
                }
                var pe = lineEnd
                while (pe < n) {
                    var nextEnd = pe + 1
                    val nextStart = nextEnd
                    while (nextEnd < n && text[nextEnd] != '\n') nextEnd++
                    if (nextStart == nextEnd) break
                    pe = nextEnd
                }
                focusStart = ps; focusEnd = pe
            }
            lineStart = lineEnd + 1
        }

        // --- inline level (skipped for very large notes so scrolling stays smooth)
        if (n <= INLINE_LIMIT) {
            inline(text, "**", SpanStyle(fontWeight = FontWeight.Bold), syntax)
            inline(text, "==", SpanStyle(background = highlight), syntax)
            inline(text, "++", SpanStyle(textDecoration = TextDecoration.Underline), syntax)
            inline(text, "~~", SpanStyle(textDecoration = TextDecoration.LineThrough, color = muted), syntax)
            inline(text, "`", SpanStyle(fontFamily = FontFamily.Monospace, background = muted.copy(alpha = 0.12f)), syntax)
            singleStarItalic(text, syntax)
            links(text, syntax)
        }

        if (paragraphFocus && focusStart >= 0) {
            val dim = SpanStyle(color = onSurface.copy(alpha = 0.35f))
            if (focusStart > 0) addStyle(dim, 0, focusStart)
            if (focusEnd < n) addStyle(dim, focusEnd, n)
        }
    }

    private fun TextFieldBuffer.heading(start: Int, end: Int, prefixLen: Int, size: TextUnit) {
        addStyle(SpanStyle(color = muted.copy(alpha = 0.5f), fontSize = size * 0.8f), start, minOf(start + prefixLen, end))
        addStyle(SpanStyle(fontFamily = titleFont, fontWeight = FontWeight.Bold, fontSize = size), start, end)
    }

    private fun TextFieldBuffer.inline(text: CharSequence, marker: String, style: SpanStyle, syntax: SpanStyle) {
        var i = 0
        val m = marker.length
        val n = text.length
        while (i < n) {
            val open = indexOf(text, marker, i)
            if (open < 0) break
            val close = indexOf(text, marker, open + m)
            if (close < 0) break
            if (close > open + m && !text.subSequence(open + m, close).contains('\n')) {
                addStyle(syntax, open, open + m)
                addStyle(style, open + m, close)
                addStyle(syntax, close, close + m)
                i = close + m
            } else {
                i = open + m
            }
        }
    }

    /** Italic via single `*` that is not part of `**`. */
    private fun TextFieldBuffer.singleStarItalic(text: CharSequence, syntax: SpanStyle) {
        val n = text.length
        var i = 0
        var open = -1
        while (i < n) {
            val c = text[i]
            if (c == '*') {
                if (i + 1 < n && text[i + 1] == '*') { i += 2; continue }
                if (open < 0) open = i
                else if (i > open + 1) {
                    addStyle(syntax, open, open + 1)
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), open + 1, i)
                    addStyle(syntax, i, i + 1)
                    open = -1
                } else open = i
            } else if (c == '\n') open = -1
            i++
        }
    }

    private fun TextFieldBuffer.links(text: CharSequence, syntax: SpanStyle) {
        var i = 0
        val n = text.length
        while (i < n) {
            val lb = indexOf(text, "[", i); if (lb < 0) break
            val rb = indexOf(text, "](", lb + 1); if (rb < 0) break
            val close = indexOf(text, ")", rb + 2); if (close < 0) break
            if (rb > lb + 1 && !text.subSequence(lb, close).contains('\n')) {
                addStyle(syntax, lb, lb + 1)
                addStyle(SpanStyle(color = accent, textDecoration = TextDecoration.Underline), lb + 1, rb)
                addStyle(syntax.copy(color = accent.copy(alpha = 0.6f)), rb, close + 1)
            }
            i = close + 1
        }
    }

    /** Allocation-free `startsWith` for the line that spans [start, end). */
    private fun CharSequence.has(start: Int, end: Int, prefix: String): Boolean {
        if (end - start < prefix.length) return false
        for (i in prefix.indices) if (this[start + i] != prefix[i]) return false
        return true
    }

    private fun indexOf(text: CharSequence, needle: String, from: Int): Int {
        if (from >= text.length) return -1
        return text.indexOf(needle, from)
    }
}
