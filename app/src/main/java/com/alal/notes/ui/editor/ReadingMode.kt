package com.alal.notes.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alal.notes.R
import com.alal.notes.data.prefs.Settings
import com.alal.notes.domain.wordcount.TextStats
import com.alal.notes.ui.util.Format
import kotlin.math.roundToInt

/**
 * Distraction-free rendered view of the note: Markdown is rendered (headings, lists, quotes,
 * inline styles, links), the keyboard is gone, and the chrome hides on tap. Pinch or use A-/A+
 * to change the reading size (independent of the editor size).
 */
@Composable
fun ReadingModeScreen(
    title: String,
    body: String,
    stats: TextStats,
    settings: Settings,
    titleFamily: FontFamily,
    bodyFamily: FontFamily,
    onClose: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var chrome by rememberSaveable { mutableStateOf(true) }
    var size by rememberSaveable { mutableFloatStateOf((settings.bodySize + 1).toFloat()) }
    val blocks = remember(body) { MarkdownBlocks.parse(body) }
    val scroll = rememberScrollState()
    val insets = WindowInsets.systemBars.asPaddingValues()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    LaunchedEffect(Unit) { focus.clearFocus(force = true); keyboard?.hide() }

    Surface(color = cs.surface, modifier = Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .pinchToZoom { ratio -> size = (size * ratio).coerceIn(13f, 34f) }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { chrome = !chrome },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(insets)
                    .padding(horizontal = 28.dp),
            ) {
                Spacer(Modifier.height(56.dp))
                if (title.isNotBlank()) {
                    Text(
                        title,
                        fontFamily = titleFamily,
                        fontSize = (size * 1.55f).sp,
                        lineHeight = (size * 1.55f * 1.25f).sp,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.meta_words, Format.number(stats.words)) + "  \u00b7  " +
                            stringResource(R.string.min_read, stats.readMinutes.coerceAtLeast(1)),
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                }
                val base = TextStyle(
                    fontFamily = bodyFamily,
                    fontSize = size.sp,
                    lineHeight = (size * settings.lineHeight).sp,
                    color = cs.onSurface,
                )
                for (b in blocks) RenderBlock(b, base, size)
                Spacer(Modifier.height(96.dp))
            }

            // Top chrome
            AnimatedVisibility(visible = chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(cs.surface.copy(alpha = 0.92f))
                        .padding(WindowInsets.statusBars.asPaddingValues())
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.close)) }
                    Text(stringResource(R.string.reading_mode), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { size = (size - 1f).coerceAtLeast(13f) }) { Icon(Icons.Rounded.Remove, stringResource(R.string.reading_smaller)) }
                    Text("${size.roundToInt()}", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    IconButton(onClick = { size = (size + 1f).coerceAtMost(34f) }) { Icon(Icons.Rounded.Add, stringResource(R.string.reading_larger)) }
                }
            }
        }
    }
}

@Composable
private fun RenderBlock(b: MarkdownBlocks.Block, base: TextStyle, size: Float) {
    val cs = MaterialTheme.colorScheme
    val link = TextLinkStyles(SpanStyle(color = cs.primary, textDecoration = TextDecoration.Underline))
    when (b.kind) {
        MarkdownBlocks.Kind.BLANK -> Spacer(Modifier.height((size * 0.9f).dp))
        MarkdownBlocks.Kind.HR -> HorizontalDivider(Modifier.padding(vertical = 16.dp), color = cs.outlineVariant)
        MarkdownBlocks.Kind.HEADING -> {
            val scale = when (b.level) { 1 -> 1.45f; 2 -> 1.25f; else -> 1.1f }
            Spacer(Modifier.height((size * 0.6f).dp))
            Text(
                inline(b.text, link),
                style = base.copy(fontSize = (size * scale).sp, lineHeight = (size * scale * 1.3f).sp, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height((size * 0.3f).dp))
        }
        MarkdownBlocks.Kind.QUOTE -> Row(Modifier.padding(vertical = 4.dp)) {
            Box(Modifier.width(3.dp).height((size * 1.6f).dp).background(cs.primary))
            Spacer(Modifier.width(14.dp))
            Text(inline(b.text, link), style = base.copy(fontStyle = FontStyle.Italic, color = cs.onSurfaceVariant))
        }
        MarkdownBlocks.Kind.BULLET, MarkdownBlocks.Kind.NUMBER, MarkdownBlocks.Kind.TASK -> Row(Modifier.padding(start = (b.level * 16).dp)) {
            val marker = when (b.kind) {
                MarkdownBlocks.Kind.NUMBER -> "${b.number}."
                MarkdownBlocks.Kind.TASK -> if (b.checked) "\u2611" else "\u2610"
                else -> "\u2022"
            }
            Text(marker, style = base.copy(color = cs.primary, fontWeight = FontWeight.Bold), modifier = Modifier.width(26.dp))
            Text(
                inline(b.text, link),
                style = if (b.kind == MarkdownBlocks.Kind.TASK && b.checked) base.copy(textDecoration = TextDecoration.LineThrough, color = cs.onSurfaceVariant) else base,
                modifier = Modifier.weight(1f),
            )
        }
        MarkdownBlocks.Kind.PARAGRAPH -> Text(inline(b.text, link), style = base)
    }
}

// ------------------------------------------------------------------ Markdown -> blocks / inline

object MarkdownBlocks {
    enum class Kind { PARAGRAPH, HEADING, QUOTE, BULLET, NUMBER, TASK, HR, BLANK }

    data class Block(
        val kind: Kind,
        val text: String = "",
        val level: Int = 0,
        val number: Int = 0,
        val checked: Boolean = false,
    )

    private val heading = Regex("""^\s{0,3}(#{1,3})\s+(.*?)\s*#*\s*$""")
    private val task = Regex("""^(\s*)[-*+]\s+\[([ xX])\]\s*(.*)$""")
    private val bullet = Regex("""^(\s*)[-*+]\s+(.*)$""")
    private val number = Regex("""^(\s*)(\d{1,3})\.\s+(.*)$""")
    private val quote = Regex("""^\s*>\s?(.*)$""")
    private val hr = Regex("""^\s*(?:-{3,}|\*{3,}|_{3,})\s*$""")

    /** Consecutive plain lines are joined into one paragraph (soft wraps), like the editor shows them. */
    fun parse(body: String): List<Block> {
        val out = ArrayList<Block>()
        val para = StringBuilder()
        fun flush() {
            if (para.isNotEmpty()) { out += Block(Kind.PARAGRAPH, para.toString()); para.setLength(0) }
        }
        for (raw in body.split('\n')) {
            val line = raw.trimEnd()
            when {
                line.isBlank() -> { flush(); if (out.lastOrNull()?.kind != Kind.BLANK) out += Block(Kind.BLANK) }
                hr.matches(line) -> { flush(); out += Block(Kind.HR) }
                else -> {
                    val h = heading.matchEntire(line)
                    val t = task.matchEntire(line)
                    val bl = bullet.matchEntire(line)
                    val nu = number.matchEntire(line)
                    val q = quote.matchEntire(line)
                    when {
                        h != null -> { flush(); out += Block(Kind.HEADING, h.groupValues[2], level = h.groupValues[1].length) }
                        t != null -> { flush(); out += Block(Kind.TASK, t.groupValues[3], level = t.groupValues[1].length / 2, checked = t.groupValues[2] != " ") }
                        bl != null -> { flush(); out += Block(Kind.BULLET, bl.groupValues[2], level = bl.groupValues[1].length / 2) }
                        nu != null -> { flush(); out += Block(Kind.NUMBER, nu.groupValues[3], level = nu.groupValues[1].length / 2, number = nu.groupValues[2].toInt()) }
                        q != null -> { flush(); out += Block(Kind.QUOTE, q.groupValues[1]) }
                        else -> { if (para.isNotEmpty()) para.append('\n'); para.append(line) }
                    }
                }
            }
        }
        flush()
        // Trim leading/trailing blanks
        while (out.firstOrNull()?.kind == Kind.BLANK) out.removeAt(0)
        while (out.lastOrNull()?.kind == Kind.BLANK) out.removeAt(out.lastIndex)
        return out
    }
}

private val linkRegex = Regex("""\[([^\]]+)\]\(([^)\s]+)\)""")

/** Renders inline Markdown (`**bold**`, `*italic*`, `++underline++`, `~~strike~~`, `==mark==`, `` `code` ``, links). */
private fun inline(text: String, link: TextLinkStyles): AnnotatedString = buildAnnotatedString { appendInline(this, text, link) }

private fun appendInline(b: androidx.compose.ui.text.AnnotatedString.Builder, text: String, link: TextLinkStyles) {
    var i = 0
    val n = text.length
    while (i < n) {
        // Links first: [label](url)
        if (text[i] == '[') {
            val m = linkRegex.find(text, i)
            if (m != null && m.range.first == i) {
                b.withLink(LinkAnnotation.Url(m.groupValues[2], link)) { appendInline(b, m.groupValues[1], link) }
                i = m.range.last + 1
                continue
            }
        }
        val marker = when {
            text.startsWith("**", i) -> "**"
            text.startsWith("++", i) -> "++"
            text.startsWith("~~", i) -> "~~"
            text.startsWith("==", i) -> "=="
            text[i] == '`' -> "`"
            text[i] == '*' -> "*"
            else -> null
        }
        if (marker != null) {
            val start = i + marker.length
            val close = text.indexOf(marker, start)
            if (close > start) {
                val inner = text.substring(start, close)
                val style = when (marker) {
                    "**" -> SpanStyle(fontWeight = FontWeight.Bold)
                    "++" -> SpanStyle(textDecoration = TextDecoration.Underline)
                    "~~" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                    "==" -> SpanStyle(background = Color(0x66FFD54F))
                    "`" -> SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x1A808080))
                    else -> SpanStyle(fontStyle = FontStyle.Italic)
                }
                b.withStyle(style) {
                    if (marker == "`") b.append(inner) else appendInline(b, inner, link)
                }
                i = close + marker.length
                continue
            }
        }
        b.append(text[i])
        i++
    }
}
