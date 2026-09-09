package com.alal.notes.ui.editor

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alal.notes.R
import com.alal.notes.data.export.ExportFormat
import com.alal.notes.domain.markdown.OutlineItem
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ Export sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(vm: EditorViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var format by rememberSaveable { mutableStateOf(ExportFormat.MARKDOWN) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        SheetTitle(stringResource(R.string.export))
        SectionLabel(stringResource(R.string.export_format))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FormatRow(ExportFormat.MARKDOWN, Icons.Rounded.Description, R.string.export_markdown, R.string.export_markdown_hint, format) { format = it }
            FormatRow(ExportFormat.PLAIN, Icons.Rounded.Notes, R.string.export_plain, R.string.export_plain_hint, format) { format = it }
            FormatRow(ExportFormat.PDF, Icons.Rounded.PictureAsPdf, R.string.export_pdf, R.string.export_pdf_hint, format) { format = it }
        }
        Spacer(Modifier.height(16.dp))

        // One launcher per format because CreateDocument fixes the MIME type at construction.
        val save = key(format) {
            rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(format.mime)) { uri ->
                if (uri != null) { vm.exportTo(uri, format); onDismiss() }
            }
        }
        Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { save.launch(vm.exportFileName(format)) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Save, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.export_save_file))
            }
            Button(
                onClick = {
                    scope.launch {
                        val (uri, mime) = vm.exportForShare(format) ?: return@launch
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = mime
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, vm.exportFileName(format))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, context.getString(R.string.share)))
                        onDismiss()
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.share))
            }
        }
        Text(
            stringResource(R.string.export_hint),
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FormatRow(
    value: ExportFormat,
    icon: ImageVector,
    title: Int,
    hint: Int,
    selected: ExportFormat,
    onSelect: (ExportFormat) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val isSel = value == selected
    Surface(
        color = if (isSel) cs.primaryContainer else cs.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().clickable { onSelect(value) },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (isSel) cs.onPrimaryContainer else cs.onSurfaceVariant)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal)
                Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Text("." + value.extension, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
    }
}

// ------------------------------------------------------------------ Outline sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutlineSheet(items: List<OutlineItem>, cursor: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val hasHeadings = items.any { it.level > 0 }
    // The entry whose range contains the caret.
    val currentIndex = remember(items, cursor) { items.indexOfLast { it.offset <= cursor }.coerceAtLeast(if (items.isEmpty()) -1 else 0) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        SheetTitle(stringResource(R.string.outline))
        if (items.isEmpty()) {
            Text(
                stringResource(R.string.outline_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        } else {
            Text(
                stringResource(if (hasHeadings) R.string.outline_hint_headings else R.string.outline_hint_paragraphs, items.size),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            LazyColumn(Modifier.heightIn(max = 480.dp).padding(top = 8.dp)) {
                itemsIndexed(items) { index, item ->
                    val current = index == currentIndex
                    val indent = when (item.level) { 3 -> 40.dp; 2 -> 24.dp; else -> 8.dp }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(item.offset); onDismiss() }
                            .padding(start = 16.dp + indent, end = 24.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape)) {
                            Surface(color = if (current) cs.primary else cs.outlineVariant, shape = CircleShape, modifier = Modifier.size(8.dp)) {}
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(
                            item.text,
                            style = when (item.level) {
                                1 -> MaterialTheme.typography.titleMedium
                                2 -> MaterialTheme.typography.bodyLarge
                                else -> MaterialTheme.typography.bodyMedium
                            },
                            fontWeight = if (current) FontWeight.SemiBold else if (item.level == 1) FontWeight.Medium else FontWeight.Normal,
                            color = if (current) cs.primary else cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
