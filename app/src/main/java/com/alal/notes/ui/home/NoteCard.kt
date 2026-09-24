package com.alal.notes.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alal.notes.R
import com.alal.notes.data.entity.Category
import com.alal.notes.data.entity.Note
import com.alal.notes.data.prefs.Settings
import com.alal.notes.domain.markdown.AutoTitle
import com.alal.notes.domain.markdown.MarkdownStripper
import com.alal.notes.domain.model.CardStyle
import com.alal.notes.domain.model.ViewMode
import com.alal.notes.ui.components.StatusChip
import com.alal.notes.ui.theme.ActionColors
import com.alal.notes.ui.theme.Alal
import com.alal.notes.ui.theme.NoteBackgrounds
import com.alal.notes.ui.util.Format
import com.alal.notes.ui.util.rememberHaptics
import com.alal.notes.ui.util.rememberIs24Hour

/** Resolves the effective card background for a note (or null for the theme surface). */
@Composable
fun noteCardColor(note: Note, dark: Boolean): Color? {
    if (!note.showBackgroundOnCard) return null
    val g = note.backgroundGradient
    if (g != null && g in NoteBackgrounds.gradients.indices) {
        val c = NoteBackgrounds.gradients[g].start
        return if (dark) NoteBackgrounds.forDark(c) else c
    }
    val c = note.backgroundColor ?: return null
    val color = Color(c)
    return if (dark) NoteBackgrounds.forDark(color) else color
}

@Composable
fun noteCardBrush(note: Note, dark: Boolean): Brush? {
    if (!note.showBackgroundOnCard) return null
    val g = note.backgroundGradient ?: return null
    val grad = NoteBackgrounds.gradients.getOrNull(g) ?: return null
    val s = if (dark) NoteBackgrounds.forDark(grad.start) else grad.start
    val e = if (dark) NoteBackgrounds.forDark(grad.end) else grad.end
    return Brush.linearGradient(listOf(s, e))
}

/** Body preview: Markdown stripped, title line removed, collapsed whitespace. */
fun previewText(note: Note): String {
    val title = AutoTitle.from(note.title, note.body)
    val stripped = MarkdownStripper.strip(note.body)
    val lines = stripped.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    val body = if (note.title.isBlank() && lines.isNotEmpty() && lines.first().startsWith(title.take(20))) lines.drop(1) else lines
    return body.joinToString(" ").take(300)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: Note,
    category: Category?,
    settings: Settings,
    viewMode: ViewMode,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extras = Alal.extras
    val dark = extras.dark
    val cs = MaterialTheme.colorScheme
    val brush = noteCardBrush(note, dark)
    val bg = noteCardColor(note, dark) ?: cs.surfaceContainer
    val shape = if (settings.cardStyle == CardStyle.SOFT) RoundedCornerShape(20.dp) else RoundedCornerShape(4.dp)
    // Water-touch feel: the card dips under the finger, keeps its ripple, and ticks the moment
    // it is pressed, so the tap is answered before the note has even been read.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = rememberHaptics()
    LaunchedEffect(pressed) { if (pressed) haptics.tick() }
    val scale by animateFloatAsState(
        when {
            selected -> 0.97f
            pressed -> 0.98f
            else -> 1f
        },
        spring(stiffness = Spring.StiffnessMedium),
        label = "scale",
    )
    val borderColor by animateColorAsState(if (selected) cs.primary else Color.Transparent, label = "border")
    val is24 = rememberIs24Hour()
    val title = remember(note.title, note.body) { AutoTitle.from(note.title, note.body) }
    val preview = remember(note.body, note.title) { previewText(note) }
    val compact = viewMode == ViewMode.COMPACT
    val pad = if (compact) 12.dp else 16.dp

    Box(
        modifier
            .scale(scale)
            .clip(shape)
            .then(if (brush != null) Modifier.background(brush) else Modifier.background(bg))
            .border(2.dp, borderColor, shape)
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Row {
            // 4dp category strip
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(category?.let { Color(it.color) } ?: Color.Transparent),
            )
            Column(Modifier.weight(1f).padding(start = pad - 4.dp, end = pad, top = pad, bottom = pad)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(note.status, compact = true)
                    Spacer(Modifier.weight(1f))
                    if (note.reminderAt != null) {
                        Icon(Icons.Rounded.Notifications, contentDescription = stringResource(R.string.reminder), tint = ActionColors.reminder, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    if (note.isLocked) {
                        Icon(Icons.Rounded.Lock, contentDescription = stringResource(R.string.locked_note), tint = ActionColors.lock, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    if (note.isFavorite) {
                        Icon(Icons.Rounded.Star, contentDescription = stringResource(R.string.favorite), tint = ActionColors.pin, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    if (selecting) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = if (selected) cs.primary else cs.outlineVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
                Text(
                    text = title.ifBlank { stringResource(R.string.untitled) },
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = if (compact) 17.sp else 22.sp,
                        lineHeight = if (compact) 22.sp else 28.sp,
                    ),
                    color = if (title.isBlank()) cs.onSurfaceVariant else cs.onSurface,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!compact && preview.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        // Locked notes never show their body on cards.
                        text = if (note.isLocked) stringResource(R.string.locked_preview) else preview,
                        fontSize = settings.cardPreviewSize.sp,
                        lineHeight = (settings.cardPreviewSize * 1.5f).sp,
                        fontFamily = extras.type.body,
                        color = cs.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(if (compact) 4.dp else 10.dp))
                // Footer: date on the left, pin on the right - the card anatomy of the redesign.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.meta_words, Format.number(note.wordCount)) + " · " + Format.relative(note.updatedAt, is24),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (note.isPinned) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Rounded.PushPin,
                            contentDescription = stringResource(R.string.pinned),
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }
    }
}
