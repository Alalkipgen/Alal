package com.alal.notes.data.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.alal.notes.data.entity.Note
import com.alal.notes.data.prefs.UserPreferences
import com.alal.notes.domain.markdown.AutoTitle
import com.alal.notes.domain.markdown.MarkdownStripper
import com.alal.notes.ui.theme.Fonts
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class ExportFormat(val extension: String, val mime: String) {
    MARKDOWN("md", "text/markdown"),
    PLAIN("txt", "text/plain"),
    PDF("pdf", "application/pdf"),
}

/**
 * Exports a single note as Markdown, plain text or a paginated A4 PDF.
 * Fully offline: the caller either hands us a SAF document URI (Save file) or asks for a
 * shareable content:// URI (Share).
 */
@Singleton
class NoteExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: UserPreferences,
) {
    fun titleOf(note: Note): String = note.title.ifBlank { AutoTitle.from("", note.body) }.ifBlank { "Untitled" }

    fun fileName(note: Note, format: ExportFormat): String {
        val base = titleOf(note)
            .replace(Regex("""[\\/:*?"<>|\n\r\t]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(60)
            .ifBlank { "alal-note" }
        return "$base.${format.extension}"
    }

    fun markdown(note: Note): String {
        val title = titleOf(note)
        val body = note.body.trimEnd()
        return if (note.title.isBlank()) body + "\n" else "# $title\n\n$body\n"
    }

    fun plainText(note: Note): String {
        val title = titleOf(note)
        val body = MarkdownStripper.strip(note.body).trimEnd()
        return if (note.title.isBlank()) body + "\n" else "$title\n\n$body\n"
    }

    /** Writes [format] to a user-chosen document (from ACTION_CREATE_DOCUMENT). */
    suspend fun writeTo(uri: Uri, note: Note, format: ExportFormat) = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open $uri")
        out.use { write(it, note, format) }
    }

    /** Writes into the app cache and returns a content:// URI usable in ACTION_SEND. */
    suspend fun writeForShare(note: Note, format: ExportFormat): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, fileName(note, format))
        file.outputStream().use { write(it, note, format) }
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    private suspend fun write(out: OutputStream, note: Note, format: ExportFormat) {
        when (format) {
            ExportFormat.MARKDOWN -> out.write(markdown(note).toByteArray(Charsets.UTF_8))
            ExportFormat.PLAIN -> out.write(plainText(note).toByteArray(Charsets.UTF_8))
            ExportFormat.PDF -> writePdf(out, note)
        }
        out.flush()
    }

    // ------------------------------------------------------------------ PDF

    private suspend fun writePdf(out: OutputStream, note: Note) {
        val settings = prefs.settings.first()
        val regular = typeface(settings.myanmarFont, bold = false)
        val bold = typeface(settings.myanmarFont, bold = true)

        val doc = PdfDocument()
        try {
            val pageW = 595 // A4 @ 72 dpi
            val pageH = 842
            val margin = 56
            val contentW = pageW - margin * 2
            val contentH = pageH - margin * 2

            val titlePaint = TextPaint().apply {
                isAntiAlias = true; typeface = bold; textSize = 20f; color = Color.BLACK
            }
            val metaPaint = TextPaint().apply {
                isAntiAlias = true; typeface = regular; textSize = 9f; color = 0xFF777777.toInt()
            }
            val bodyPaint = TextPaint().apply {
                isAntiAlias = true; typeface = regular; textSize = 11.5f; color = 0xFF1C1B1F.toInt()
            }
            val footerPaint = TextPaint().apply {
                isAntiAlias = true; typeface = regular; textSize = 9f; color = 0xFF999999.toInt()
            }

            val title = titleOf(note)
            val meta = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(note.updatedAt)) +
                "  \u00b7  ${note.wordCount} words"
            val body = MarkdownStripper.strip(note.body).trimEnd()

            val titleLayout = layout(title, titlePaint, contentW, 1.15f)
            val metaLayout = layout(meta, metaPaint, contentW, 1.2f)
            val bodyLayout = layout(body, bodyPaint, contentW, 1.6f)

            val headerH = titleLayout.height + 6 + metaLayout.height + 18
            var pageNo = 0
            var line = 0
            val lineCount = bodyLayout.lineCount
            val totalPagesEstimate = estimatePages(bodyLayout, contentH, headerH)

            while (pageNo == 0 || line < lineCount) {
                pageNo++
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
                val canvas = page.canvas
                var y = margin
                if (pageNo == 1) {
                    draw(canvas, titleLayout, margin, y); y += titleLayout.height + 6
                    draw(canvas, metaLayout, margin, y); y += metaLayout.height + 18
                }
                val bottom = margin + contentH
                // Draw as many body lines as fit on this page.
                val firstLine = line
                while (line < lineCount) {
                    val lineTop = bodyLayout.getLineTop(line)
                    val lineBottom = bodyLayout.getLineBottom(line)
                    val h = lineBottom - lineTop
                    if (y + h > bottom) break
                    line++
                    y += h
                }
                if (line > firstLine) {
                    val startTop = bodyLayout.getLineTop(firstLine)
                    val endBottom = bodyLayout.getLineBottom(line - 1)
                    canvas.save()
                    val topY = if (pageNo == 1) margin + headerH else margin
                    canvas.clipRect(margin, topY, margin + contentW, topY + (endBottom - startTop))
                    canvas.translate(margin.toFloat(), (topY - startTop).toFloat())
                    bodyLayout.draw(canvas)
                    canvas.restore()
                } else if (line < lineCount) {
                    // A single line taller than a page (should not happen) - skip it to avoid looping forever.
                    line++
                }
                val footer = "$title  \u00b7  $pageNo / $totalPagesEstimate"
                canvas.drawText(footer, margin.toFloat(), (pageH - margin / 2).toFloat(), footerPaint)
                doc.finishPage(page)
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    private fun estimatePages(body: StaticLayout, contentH: Int, headerH: Int): Int {
        var pages = 1
        var used = headerH
        for (i in 0 until body.lineCount) {
            val h = body.getLineBottom(i) - body.getLineTop(i)
            if (used + h > contentH) { pages++; used = 0 }
            used += h
        }
        return pages
    }

    private fun layout(text: CharSequence, paint: TextPaint, width: Int, spacing: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, spacing)
            .setIncludePad(true)
            .build()

    private fun draw(canvas: Canvas, layout: StaticLayout, x: Int, y: Int) {
        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())
        layout.draw(canvas)
        canvas.restore()
    }

    private fun typeface(myanmarFontKey: String, bold: Boolean): Typeface {
        val option = Fonts.myanmar.firstOrNull { it.key == myanmarFontKey } ?: Fonts.myanmar.first()
        val name = if (bold) option.bold ?: option.regular else option.regular
        val id = Fonts.fontId(context.resources, context.packageName, name)
        val loaded = if (id != 0) runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() else null
        return loaded ?: if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
}
