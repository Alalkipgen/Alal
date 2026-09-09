package com.alal.notes.domain.markdown

/** Android-free text selection (start <= end not required; normalised on use). */
data class TextSelection(val start: Int, val end: Int = start) {
    val min: Int get() = minOf(start, end)
    val max: Int get() = maxOf(start, end)
    val collapsed: Boolean get() = start == end
}

data class EditResult(val text: String, val selection: TextSelection)

enum class PrefixKind { HEADING, LIST, QUOTE }

/** Strips Markdown syntax for previews, auto titles and search snippets. */
object MarkdownStripper {
    private val heading = Regex("""^\s{0,3}#{1,6}\s+""")
    private val quote = Regex("""^\s*>\s?""")
    private val listMarker = Regex("""^\s*(?:[-*+]|\d+\.)\s+(?:\[[ xX]\]\s*)?""")
    private val hr = Regex("""^\s*(?:-{3,}|\*{3,}|_{3,})\s*$""")
    private val bold = Regex("""\*\*(.+?)\*\*|__(.+?)__""")
    private val italic = Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)""")
    private val strike = Regex("""~~(.+?)~~""")
    private val mark = Regex("""==(.+?)==""")
    private val underline = Regex("""\+\+(.+?)\+\+""")
    private val code = Regex("""`([^`]+)`""")
    private val link = Regex("""\[([^\]]+)\]\([^)]*\)""")

    fun strip(markdown: String): String {
        if (markdown.isEmpty()) return markdown
        val out = StringBuilder(markdown.length)
        val lines = markdown.split('\n')
        for ((index, raw) in lines.withIndex()) {
            if (hr.matches(raw)) continue
            var line = raw
            line = heading.replace(line, "")
            line = quote.replace(line, "")
            line = listMarker.replace(line, "")
            line = stripInline(line)
            out.append(line)
            if (index < lines.lastIndex) out.append('\n')
        }
        return out.toString()
    }

    fun stripInline(line: String): String {
        var s = line
        s = bold.replace(s) { m -> m.groups[1]?.value ?: m.groups[2]?.value ?: "" }
        s = italic.replace(s) { m -> m.groupValues[1] }
        s = strike.replace(s) { m -> m.groupValues[1] }
        s = mark.replace(s) { m -> m.groupValues[1] }
        s = underline.replace(s) { m -> m.groupValues[1] }
        s = code.replace(s) { m -> m.groupValues[1] }
        s = link.replace(s) { m -> m.groupValues[1] }
        return s
    }
}

object AutoTitle {
    const val MAX = 60

    /** Title if present, else the first non-empty body line (Markdown stripped), max 60 chars. */
    fun from(title: String, body: String): String {
        val t = title.trim()
        if (t.isNotEmpty()) return t
        val line = body.lineSequence().firstOrNull { it.isNotBlank() } ?: return ""
        val stripped = MarkdownStripper.strip(line).trim()
        return if (stripped.length <= MAX) stripped else stripped.substring(0, MAX).trimEnd()
    }
}

/** Pure functions that insert/toggle Markdown syntax around a selection. */
object MarkdownToggle {
    private val headingPrefix = Regex("""^#{1,6}\s""")
    private val listPrefix = Regex("""^(\s*)(?:[-*+]\s(?:\[[ xX]\]\s)?|\d+\.\s)""")
    private val quotePrefix = Regex("""^>\s?""")
    private val numbered = Regex("""^\d+\.\s""")
    private val checklist = Regex("""^[-*+]\s\[[ xX]\]\s""")
    private val bullet = Regex("""^[-*+]\s(?!\[[ xX]\]\s)""")

    /** Wrap/unwrap the selection with an inline marker such as `**` or `==`. */
    fun wrap(text: String, sel: TextSelection, marker: String, endMarker: String = marker): EditResult {
        val s = sel.min.coerceIn(0, text.length)
        val e = sel.max.coerceIn(0, text.length)
        val selected = text.substring(s, e)
        val m = marker.length
        val em = endMarker.length
        // 1. selection itself includes the markers -> unwrap
        if (selected.length >= m + em && selected.startsWith(marker) && selected.endsWith(endMarker)) {
            val inner = selected.substring(m, selected.length - em)
            return EditResult(text.substring(0, s) + inner + text.substring(e), TextSelection(s, s + inner.length))
        }
        // 2. markers sit just outside the selection -> unwrap
        if (s >= m && text.regionMatches(s - m, marker, 0, m) && e + em <= text.length && text.regionMatches(e, endMarker, 0, em)) {
            return EditResult(text.substring(0, s - m) + selected + text.substring(e + em), TextSelection(s - m, e - m))
        }
        // 3. wrap
        val result = text.substring(0, s) + marker + selected + endMarker + text.substring(e)
        return if (selected.isEmpty()) EditResult(result, TextSelection(s + m))
        else EditResult(result, TextSelection(s + m, e + m))
    }

    /**
     * Toggle a line prefix (`# `, `- `, `1. `, `- [ ] `, `> `) on every line touched by
     * the selection. If every line already carries exactly this prefix it is removed,
     * otherwise conflicting prefixes of the same [kind] are replaced.
     */
    fun linePrefix(text: String, sel: TextSelection, prefix: String, kind: PrefixKind): EditResult {
        val s = sel.min.coerceIn(0, text.length)
        var e = sel.max.coerceIn(0, text.length)
        if (e > s && text[e - 1] == '\n') e-- // selection ending right after a newline excludes that next line
        val lineStart = text.lastIndexOf('\n', s - 1) + 1
        val nl = text.indexOf('\n', e)
        val lineEnd = if (nl == -1) text.length else nl
        val block = text.substring(lineStart, lineEnd)
        val lines = block.split('\n')

        val matcher: Regex = when (kind) {
            PrefixKind.HEADING -> headingPrefix
            PrefixKind.LIST -> listPrefix
            PrefixKind.QUOTE -> quotePrefix
        }
        val isNumbered = kind == PrefixKind.LIST && numbered.matches(prefix)
        val isCheck = kind == PrefixKind.LIST && checklist.matches(prefix)
        fun hasExactly(line: String): Boolean = when {
            isNumbered -> numbered.containsMatchIn(line)
            isCheck -> checklist.containsMatchIn(line)
            kind == PrefixKind.LIST -> bullet.containsMatchIn(line)
            else -> line.startsWith(prefix)
        }
        val allHave = lines.all { it.isBlank() || hasExactly(it) } && lines.any { it.isNotBlank() }

        val newLines = ArrayList<String>(lines.size)
        var n = 1
        for (line in lines) {
            val stripped = matcher.replaceFirst(line, if (kind == PrefixKind.LIST) "$1" else "")
            if (allHave) {
                newLines += stripped
            } else {
                val p = if (isNumbered) "${n++}. " else prefix
                newLines += p + stripped.trimStart()
            }
        }
        val newBlock = newLines.joinToString("\n")
        val newText = text.substring(0, lineStart) + newBlock + text.substring(lineEnd)
        val selection = if (sel.collapsed) {
            val delta = newLines.first().length - lines.first().length
            TextSelection((s + delta).coerceAtLeast(lineStart))
        } else {
            TextSelection(lineStart, lineStart + newBlock.length)
        }
        return EditResult(newText, selection)
    }

    fun insertLink(text: String, sel: TextSelection, label: String, url: String): EditResult {
        val s = sel.min.coerceIn(0, text.length)
        val e = sel.max.coerceIn(0, text.length)
        val md = "[$label]($url)"
        val newText = text.substring(0, s) + md + text.substring(e)
        return EditResult(newText, TextSelection(s + md.length))
    }

    fun insertHorizontalRule(text: String, sel: TextSelection): EditResult {
        val s = sel.min.coerceIn(0, text.length)
        val needsNl = s > 0 && text[s - 1] != '\n'
        val insert = (if (needsNl) "\n" else "") + "---\n"
        return EditResult(text.substring(0, s) + insert + text.substring(s), TextSelection(s + insert.length))
    }

    private val continuation = Regex("""^(\s*)([-*+]\s(?:\[[ xX]\]\s)?|(\d+)\.\s)(.*)$""")

    /**
     * Typing shortcut: after Enter on a list line, continue the list (`- `, `n. `, `- [ ] `).
     * Enter on an empty list item removes the marker instead. Returns null when nothing applies.
     */
    fun continueList(oldText: String, newText: String, cursor: Int): EditResult? {
        if (newText.length != oldText.length + 1 || cursor <= 0 || cursor > newText.length) return null
        if (newText[cursor - 1] != '\n') return null
        val prevStart = newText.lastIndexOf('\n', cursor - 2) + 1
        val prevLine = newText.substring(prevStart, cursor - 1)
        val m = continuation.find(prevLine) ?: return null
        val indent = m.groupValues[1]
        val marker = m.groupValues[2]
        val number = m.groupValues[3]
        val content = m.groupValues[4]
        if (content.isBlank()) {
            // Empty item -> remove marker, keep the cursor on that (now empty) line
            val t = newText.substring(0, prevStart) + newText.substring(cursor)
            return EditResult(t, TextSelection(prevStart))
        }
        val next = when {
            number.isNotEmpty() -> "${number.toInt() + 1}. "
            marker.contains('[') -> marker.substring(0, 2) + "[ ] "
            else -> marker
        }
        val insert = indent + next
        val t = newText.substring(0, cursor) + insert + newText.substring(cursor)
        return EditResult(t, TextSelection(cursor + insert.length))
    }
}
