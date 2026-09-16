package com.alal.notes.ui.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.withTimeoutOrNull

/** Raw Markdown link and its complete source range: `[label](url)`. */
data class MarkdownLinkTarget(
    val start: Int,
    val endExclusive: Int,
    val label: String,
    val url: String,
)

private val markdownLinkPattern = Regex("""\[([^\]\n]+)]\(([^)\n]+)\)""")

/** Finds a Markdown link when [offset] is anywhere in its visible label or hidden syntax. */
fun findMarkdownLinkAt(text: CharSequence, offset: Int): MarkdownLinkTarget? {
    if (offset < 0 || offset > text.length) return null
    return markdownLinkPattern.findAll(text).firstNotNullOfOrNull { match ->
        val label = match.groups[1] ?: return@firstNotNullOfOrNull null
        if (offset !in match.range && offset != match.range.last + 1) return@firstNotNullOfOrNull null
        MarkdownLinkTarget(
            start = match.range.first,
            endExclusive = match.range.last + 1,
            label = label.value,
            url = match.groups[2]?.value.orEmpty(),
        )
    }
}

/**
 * Lets ordinary text-field gestures pass through, but owns gestures that begin on a link.
 * Tap opens it; long-press shows link actions. The editor's raw Markdown offsets remain intact.
 */
fun Modifier.markdownLinkGestures(
    text: () -> CharSequence,
    layout: () -> TextLayoutResult?,
    scrollY: () -> Int,
    onOpen: (MarkdownLinkTarget) -> Unit,
    onLongPress: (MarkdownLinkTarget) -> Unit,
): Modifier = pointerInput(onOpen, onLongPress) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val result = layout()
        val offset = result?.getOffsetForPosition(
            Offset(down.position.x, down.position.y + scrollY()),
        ) ?: -1
        val link = findMarkdownLinkAt(text(), offset)
        if (link == null) {
            // Observe the remainder without consuming it so normal caret/selection still works.
            waitForUpOrCancellation(pass = PointerEventPass.Final)
            return@awaitEachGesture
        }

        // A gesture that starts on a link belongs to the link, not text selection.
        down.consume()
        val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            waitForUpOrCancellation(pass = PointerEventPass.Initial)
        }
        if (up != null) {
            up.consume()
            onOpen(link)
        } else {
            onLongPress(link)
            // Consume until all pointers are up so the text field does not start selection late.
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}

fun openExternalLink(context: Context, rawUrl: String): Boolean {
    val value = rawUrl.trim()
    if (value.isEmpty()) return false
    val normalized = if ("://" in value) value else "https://$value"
    val uri = runCatching { Uri.parse(normalized) }.getOrNull() ?: return false
    if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return false
    return runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        true
    }.getOrDefault(false)
}

fun copyLink(context: Context, url: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Link", url))
}

fun replaceMarkdownLink(state: TextFieldState, link: MarkdownLinkTarget, label: String, url: String?) {
    val cleanLabel = label.ifBlank { link.label }
    val replacement = if (url.isNullOrBlank()) cleanLabel else "[$cleanLabel](${url.trim()})"
    state.edit {
        replace(link.start.coerceIn(0, length), link.endExclusive.coerceIn(0, length), replacement)
        val caret = (link.start + replacement.length).coerceIn(0, length)
        selection = TextRange(caret)
    }
}

@Composable
fun MarkdownLinkActionsDialog(
    link: MarkdownLinkTarget,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onRemove: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(link.label) },
        text = {
            Column {
                Text(link.url)
                TextButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("Open link") }
                TextButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Edit link") }
                TextButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) { Text("Copy link") }
                TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) { Text("Remove link") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun EditMarkdownLinkDialog(
    link: MarkdownLinkTarget,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var label by remember(link) { mutableStateOf(link.label) }
    var url by remember(link) { mutableStateOf(link.url) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit link") },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(label.trim(), url.trim()) }, enabled = label.isNotBlank() && url.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
