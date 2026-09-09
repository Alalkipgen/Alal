package com.alal.notes.domain.markdown

/** One entry of the outline navigator. [offset] is the character index of the line start in the body. */
data class OutlineItem(
    /** 1..3 for Markdown headings, 0 for a paragraph fallback entry. */
    val level: Int,
    val text: String,
    val offset: Int,
)

/**
 * Builds a navigable outline from a Markdown body.
 * - If the body contains `#`, `##` or `###` headings, those are returned (in document order).
 * - Otherwise every paragraph (block separated by a blank line) contributes its first line, so
 *   long feature drafts without headings can still be jumped through.
 */
object Outline {
    private val heading = Regex("""^\s{0,3}(#{1,3})\s+(.+?)\s*#*\s*$""")
    private const val MAX_LEN = 60
    private const val MAX_ITEMS = 400

    fun extract(body: String): List<OutlineItem> {
        if (body.isBlank()) return emptyList()
        val headings = ArrayList<OutlineItem>()
        var offset = 0
        for (line in body.split('\n')) {
            heading.matchEntire(line)?.let { m ->
                val text = MarkdownStripper.stripInline(m.groupValues[2]).trim()
                if (text.isNotEmpty()) headings += OutlineItem(m.groupValues[1].length, clip(text), offset)
            }
            offset += line.length + 1
        }
        if (headings.isNotEmpty()) return headings.take(MAX_ITEMS)
        return paragraphs(body).take(MAX_ITEMS)
    }

    fun hasHeadings(body: String): Boolean = body.lineSequence().any { heading.matches(it) }

    private fun paragraphs(body: String): List<OutlineItem> {
        val out = ArrayList<OutlineItem>()
        var offset = 0
        var inParagraph = false
        for (line in body.split('\n')) {
            val blank = line.isBlank()
            if (!blank && !inParagraph) {
                val text = MarkdownStripper.strip(line).trim()
                if (text.isNotEmpty()) out += OutlineItem(0, clip(text), offset)
            }
            inParagraph = !blank
            offset += line.length + 1
        }
        return out
    }

    private fun clip(s: String): String = if (s.length <= MAX_LEN) s else s.substring(0, MAX_LEN).trimEnd() + "\u2026"
}
