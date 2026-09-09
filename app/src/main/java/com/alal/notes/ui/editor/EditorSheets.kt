package com.alal.notes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alal.notes.R
import com.alal.notes.data.entity.Note
import com.alal.notes.domain.model.PaperTexture
import com.alal.notes.domain.wordcount.TextStats
import com.alal.notes.ui.components.ColorSwatch
import com.alal.notes.ui.components.GoalProgressBar
import com.alal.notes.ui.components.PillChip
import com.alal.notes.ui.theme.NoteBackgrounds
import com.alal.notes.ui.util.Format
import kotlin.math.roundToInt

@Composable
fun SheetTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp).padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
fun PaperTexture.label(): String = stringResource(
    when (this) {
        PaperTexture.PLAIN -> R.string.texture_plain
        PaperTexture.DOTTED -> R.string.texture_dotted
        PaperTexture.LINED -> R.string.texture_lined
        PaperTexture.GRID -> R.string.texture_grid
    },
)

// ------------------------------------------------------------------ Find & Replace bar

@Composable
fun FindBar(state: FindState, vm: EditorViewModel) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceContainer, tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setFindQuery,
                    placeholder = { Text(stringResource(R.string.find)) },
                    singleLine = true,
                    isError = state.error != null,
                    supportingText = state.error?.let { { Text(stringResource(R.string.regex_error, it), maxLines = 1) } },
                    trailingIcon = {
                        Text(
                            if (state.matches.isEmpty()) if (state.query.isEmpty()) "" else stringResource(R.string.no_matches)
                            else stringResource(R.string.match_count, state.current + 1, state.matches.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = vm::prevMatch, enabled = state.matches.isNotEmpty()) { Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.prev_match)) }
                IconButton(onClick = vm::nextMatch, enabled = state.matches.isNotEmpty()) { Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.next_match)) }
                IconButton(onClick = vm::toggleShowReplace) { Icon(Icons.Rounded.SwapHoriz, stringResource(R.string.replace), tint = if (state.showReplace) cs.primary else cs.onSurfaceVariant) }
                IconButton(onClick = vm::closeFind) { Icon(Icons.Rounded.Close, stringResource(R.string.close)) }
            }
            if (state.showReplace) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.replacement,
                        onValueChange = vm::setReplacement,
                        placeholder = { Text(stringResource(R.string.replace)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = vm::replaceCurrent, enabled = state.current >= 0) { Text(stringResource(R.string.replace)) }
                    TextButton(onClick = vm::replaceAll, enabled = state.matches.isNotEmpty()) { Text(stringResource(R.string.replace_all)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                val o = state.options
                FilterChip(selected = o.caseSensitive, onClick = { vm.setFindOptions(o.copy(caseSensitive = !o.caseSensitive)) }, label = { Text(stringResource(R.string.case_sensitive)) })
                FilterChip(selected = o.wholeWord, onClick = { vm.setFindOptions(o.copy(wholeWord = !o.wholeWord)) }, label = { Text(stringResource(R.string.whole_word)) })
                FilterChip(selected = o.regex, onClick = { vm.setFindOptions(o.copy(regex = !o.regex)) }, label = { Text(stringResource(R.string.regex)) })
            }
        }
    }
}

// ------------------------------------------------------------------ Text sheet (font size / line height for this note)

@Composable
fun TextSheet(
    note: Note,
    defaultSize: Int,
    onSize: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var size by remember { mutableStateOf((note.fontSizeOverride ?: defaultSize).toFloat()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        SheetTitle(stringResource(R.string.text_settings))
        SectionLabel(stringResource(R.string.font_size) + "  ·  " + stringResource(R.string.size_badge, size.roundToInt()))
        Slider(
            value = size,
            onValueChange = { size = it },
            onValueChangeFinished = { onSize(size.roundToInt()) },
            valueRange = 14f..30f,
            steps = 15,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Text(
            stringResource(R.string.preview_text),
            fontSize = size.sp,
            lineHeight = (size * 1.6f).sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.apply_this_note_only), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { onSize(null); size = defaultSize.toFloat() }) { Text(stringResource(R.string.clear)) }
        }
        Spacer(Modifier.height(32.dp))
    }
}

// ------------------------------------------------------------------ Background sheet

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackgroundSheet(
    note: Note,
    dark: Boolean,
    defaultTexture: PaperTexture,
    onBackground: (Int?, Int?) -> Unit,
    onTexture: (PaperTexture?) -> Unit,
    onShowOnCard: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var hex by rememberSaveable { mutableStateOf(note.backgroundColor?.let { String.format("#%06X", it and 0xFFFFFF) } ?: "") }
    var showCustom by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        SheetTitle(stringResource(R.string.background))
        SectionLabel(stringResource(R.string.color))
        FlowRow(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ColorSwatch(cs.surface, selected = note.backgroundColor == null && note.backgroundGradient == null, onClick = { onBackground(null, null) }) {
                Icon(Icons.Rounded.Block, stringResource(R.string.none), tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            for (c in NoteBackgrounds.pastels) {
                val shown = if (dark) NoteBackgrounds.forDark(c) else c
                ColorSwatch(shown, selected = note.backgroundGradient == null && note.backgroundColor == c.toArgb(), onClick = { onBackground(c.toArgb(), null) })
            }
            ColorSwatch(
                cs.surfaceContainerHigh,
                selected = showCustom,
                onClick = { showCustom = !showCustom },
            ) { Text("#", fontWeight = FontWeight.Bold, color = cs.onSurfaceVariant) }
        }
        if (showCustom) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = hex,
                    onValueChange = { hex = it.take(7) },
                    label = { Text(stringResource(R.string.hex_color)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                val parsed = parseHex(hex)
                Box(Modifier.size(40.dp).clip(CircleShape).background(parsed ?: cs.surfaceVariant).border(1.dp, cs.outlineVariant, CircleShape))
                TextButton(enabled = parsed != null, onClick = { parsed?.let { onBackground(it.toArgb(), null) } }) { Text(stringResource(R.string.done)) }
            }
        }
        SectionLabel(stringResource(R.string.gradient))
        Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NoteBackgrounds.gradients.forEachIndexed { i, g ->
                val s = if (dark) NoteBackgrounds.forDark(g.start) else g.start
                val e = if (dark) NoteBackgrounds.forDark(g.end) else g.end
                val selected = note.backgroundGradient == i
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(s, e)))
                        .border(if (selected) 3.dp else 1.dp, if (selected) cs.primary else cs.outlineVariant, CircleShape)
                        .clickable { onBackground(null, i) },
                    contentAlignment = Alignment.Center,
                ) { if (selected) Icon(Icons.Rounded.Check, null, tint = cs.onSurface, modifier = Modifier.size(18.dp)) }
            }
        }
        SectionLabel(stringResource(R.string.paper_texture))
        Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val current = note.paperTexture ?: defaultTexture
            for (t in PaperTexture.entries) {
                PillChip(t.label(), selected = current == t, onClick = { onTexture(if (t == defaultTexture) null else t) })
            }
        }
        Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.show_on_card), modifier = Modifier.weight(1f))
            Switch(checked = note.showBackgroundOnCard, onCheckedChange = onShowOnCard)
        }
        Spacer(Modifier.height(24.dp))
    }
}

fun parseHex(input: String): Color? {
    val s = input.trim().removePrefix("#")
    if (s.length != 6 || !s.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    return Color((0xFF000000L or s.toLong(16)).toInt())
}

// ------------------------------------------------------------------ Stats sheet

@Composable
fun StatsSheet(
    stats: TextStats,
    selection: TextStats?,
    goal: Int?,
    onGoal: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var goalValue by remember { mutableStateOf((goal ?: 0).toFloat()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        SheetTitle(stringResource(R.string.stats))
        Column(Modifier.padding(horizontal = 24.dp)) {
            StatRow(stringResource(R.string.words), Format.number(stats.words))
            StatRow(stringResource(R.string.characters), Format.number(stats.chars))
            StatRow(stringResource(R.string.chars_no_spaces), Format.number(stats.charsNoSpaces))
            StatRow(stringResource(R.string.sentences), Format.number(stats.sentences))
            StatRow(stringResource(R.string.paragraphs), Format.number(stats.paragraphs))
            StatRow(stringResource(R.string.read_time), stringResource(R.string.min_read, stats.readMinutes))
            StatRow(stringResource(R.string.avg_words_sentence), String.format("%.1f", stats.avgWordsPerSentence))
            if (selection != null) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                StatRow(stringResource(R.string.selection), Format.number(selection.words) + " · " + Format.number(selection.chars))
            }
        }
        SectionLabel(stringResource(R.string.word_goal) + "  ·  " + if (goalValue < 1f) stringResource(R.string.no_goal) else Format.number(goalValue.roundToInt()))
        Slider(
            value = goalValue,
            onValueChange = { goalValue = (it / 100f).roundToInt() * 100f },
            onValueChangeFinished = { onGoal(goalValue.roundToInt().takeIf { it > 0 }) },
            valueRange = 0f..20_000f,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        if (goalValue >= 1f) {
            val p = (stats.words / goalValue).coerceIn(0f, 1f)
            GoalProgressBar(progress = p, modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(8.dp), color = if (p >= 1f) cs.tertiary else cs.primary)
            Text(
                if (p >= 1f) stringResource(R.string.goal_reached) else stringResource(R.string.progress_percent, (p * 100).roundToInt()),
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

// ------------------------------------------------------------------ Dialogs

@Composable
fun LinkDialog(initialLabel: String, onDismiss: () -> Unit, onInsert: (String, String) -> Unit) {
    var label by rememberSaveable { mutableStateOf(initialLabel) }
    var url by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.insert_link)) },
        text = {
            Column {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text(stringResource(R.string.link_text)) }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text(stringResource(R.string.link_url)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            }
        },
        confirmButton = { TextButton(onClick = { onInsert(label, url) }) { Text(stringResource(R.string.done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun DetailsDialog(note: Note, stats: TextStats, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.note_details)) },
        text = {
            Column {
                StatRow(stringResource(R.string.created), Format.full(note.createdAt))
                StatRow(stringResource(R.string.modified), Format.full(note.updatedAt))
                StatRow(stringResource(R.string.words), Format.number(stats.words))
                StatRow(stringResource(R.string.characters), Format.number(stats.chars))
                StatRow(stringResource(R.string.read_time), stringResource(R.string.min_read, stats.readMinutes))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

@Composable
fun TagDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_tag)) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.tag_name)) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onAdd(name) }) { Text(stringResource(R.string.done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Small segmented control used for heading level choice. */
@Composable
fun HeadingPicker(onPick: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = 12.dp)) {
        listOf(R.string.h1, R.string.h2, R.string.h3).forEachIndexed { i, res ->
            SegmentedButton(
                selected = false,
                onClick = { onPick(i + 1) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = 3),
            ) { Text(stringResource(res)) }
        }
    }
}
