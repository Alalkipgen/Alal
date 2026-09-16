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
import com.alal.notes.domain.markdown.MarkdownSpans
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
 * inline code, links, quotes, list markers) drawn on top of the raw text, plus optional
 * paragraph-focus dimming. The buffer text itself is never modified, so offsets stay identical
 * to the stored note.
 *
 * The ranges come from [MarkdownSpans], a single allocation-light pass over the text, and are
 * cached against the last seen text. Re-running the transformation for a caret move, focus
 * change or IME round-trip therefore costs one `contentEquals` walk instead of a rescan, and
 * long notes are styled in full: the previous 8 000-character inline cut-off (which left raw
 * `**`, `++`, `*` symbols visible in long notes) is gone.
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
        /**
         * Safety valve only. Styling a note this large is still cheap for the scanner, but the
         * text layout itself becomes the bottleneck long before, so beyond this we render plain.
         */
        const val STYLE_LIMIT = 400_000
    }

    // One SpanStyle per kind, created once. Keep Markdown in storage for reliable editing and
    // export, but collapse its inline syntax visually: a near-zero transparent span preserves
    // the raw offset mapping used by the state-based text field while making markers and link
    // destinations effectively absent.
    private val styles: Array<SpanStyle?> = Array(MarkdownSpans.KIND_COUNT) { kind ->
        when (kind) {
            MarkdownSpans.SYNTAX -> SpanStyle(color = Color.Transparent, fontSize = 0.01.sp, letterSpacing = 0.sp)
            MarkdownSpans.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
            MarkdownSpans.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            MarkdownSpans.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
            MarkdownSpans.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough, color = muted)
            MarkdownSpans.HIGHLIGHT -> SpanStyle(background = highlight)
            MarkdownSpans.CODE -> SpanStyle(fontFamily = FontFamily.Monospace, background = muted.copy(alpha = 0.12f))
            MarkdownSpans.LINK -> SpanStyle(color = accent, textDecoration = TextDecoration.Underline)
            MarkdownSpans.H1 -> heading(bodySize * 1.5f)
            MarkdownSpans.H2 -> heading(bodySize * 1.3f)
            MarkdownSpans.H3 -> heading(bodySize * 1.15f)
            MarkdownSpans.H1_MARK -> headingMark(bodySize * 1.5f)
            MarkdownSpans.H2_MARK -> headingMark(bodySize * 1.3f)
            MarkdownSpans.H3_MARK -> headingMark(bodySize * 1.15f)
            MarkdownSpans.QUOTE_MARK -> SpanStyle(color = accent, fontWeight = FontWeight.Bold)
            MarkdownSpans.QUOTE_TEXT -> SpanStyle(fontStyle = FontStyle.Italic, color = muted)
            MarkdownSpans.LIST_MARK -> SpanStyle(color = accent, fontWeight = FontWeight.Bold)
            MarkdownSpans.ORDERED_MARK -> SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)
            MarkdownSpans.CHECK_MARK -> SpanStyle(color = accent, fontWeight = FontWeight.Bold)
            MarkdownSpans.CHECK_DONE -> SpanStyle(textDecoration = TextDecoration.LineThrough, color = muted)
            MarkdownSpans.RULE -> SpanStyle(color = muted.copy(alpha = 0.5f), letterSpacing = 4.sp)
            else -> null
        }
    }
    private val dim = SpanStyle(color = onSurface.copy(alpha = 0.35f))

    private fun heading(size: TextUnit) = SpanStyle(fontFamily = titleFont, fontWeight = FontWeight.Bold, fontSize = size)
    private fun headingMark(size: TextUnit) = SpanStyle(color = muted.copy(alpha = 0.5f), fontSize = size * 0.8f)

    // Cache of the last scanned text. Output transformations run on the main thread only.
    private var cachedText: String? = null
    private var cachedSpans: MarkdownSpans.SpanList = MarkdownSpans.SpanList()

    private fun spansFor(text: CharSequence): MarkdownSpans.SpanList {
        val cached = cachedText
        if (cached != null && cached.length == text.length && text.contentEquals(cached)) return cachedSpans
        val value = text.toString()
        cachedSpans = MarkdownSpans.scan(value, cachedSpans)
        cachedText = value
        return cachedSpans
    }

    override fun TextFieldBuffer.transformOutput() {
        val text = asCharSequence()
        val n = text.length
        if (n == 0 || n > STYLE_LIMIT) return

        val spans = spansFor(text)
        val kindStyles = styles
        for (i in 0 until spans.size) {
            val style = kindStyles[spans.kind(i)] ?: continue
            val start = spans.start(i)
            val end = spans.end(i)
            if (start < 0 || end > n || start >= end) continue
            addStyle(style, start, end)
        }

        if (paragraphFocus) {
            val at = cursor
            if (at in 0..n) {
                // Expand to paragraph boundaries (blank lines) around the caret.
                var ps = at
                while (ps > 0 && text[ps - 1] != '\n') ps--
                while (ps > 0) {
                    var prevStart = ps - 1
                    while (prevStart > 0 && text[prevStart - 1] != '\n') prevStart--
                    if (prevStart == ps - 1) break // previous line is empty
                    ps = prevStart
                }
                var pe = at
                while (pe < n && text[pe] != '\n') pe++
                while (pe < n) {
                    var nextEnd = pe + 1
                    val nextStart = nextEnd
                    while (nextEnd < n && text[nextEnd] != '\n') nextEnd++
                    if (nextStart == nextEnd) break // next line is empty
                    pe = nextEnd
                }
                if (ps > 0) addStyle(dim, 0, ps)
                if (pe < n) addStyle(dim, pe, n)
            }
        }
    }
}
