package com.alal.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alal.notes.R
import com.alal.notes.domain.model.NoteStatus
import com.alal.notes.ui.theme.Alal
import com.alal.notes.ui.theme.color

@Composable
fun NoteStatus.label(): String = stringResource(
    when (this) {
        NoteStatus.IDEA -> R.string.status_idea
        NoteStatus.RESEARCH -> R.string.status_research
        NoteStatus.DRAFT -> R.string.status_draft
        NoteStatus.EDITING -> R.string.status_editing
        NoteStatus.SUBMITTED -> R.string.status_submitted
        NoteStatus.PUBLISHED -> R.string.status_published
    },
)

/** Small tinted status chip used on cards and in the editor. */
@Composable
fun StatusChip(status: NoteStatus, modifier: Modifier = Modifier, compact: Boolean = false, onClick: (() -> Unit)? = null) {
    val dark = Alal.extras.dark
    val tint = status.color(dark)
    val bg = tint.copy(alpha = if (dark) 0.22f else 0.14f)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 2.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(tint))
        Spacer(Modifier.width(6.dp))
        Text(
            status.label(),
            color = tint,
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** A round colour swatch with a check mark when selected. */
@Composable
fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 40,
    content: (@Composable () -> Unit)? = null,
) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (content != null) content()
        else if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = if (color.luminance() > 0.5f) Color(0xFF1C1B1F) else Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Pill-shaped filter chip (category / tag). */
@Composable
fun PillChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dotColor: Color? = null,
) {
    val cs = MaterialTheme.colorScheme
    val bg = if (selected) cs.primary else Color.Transparent
    val fg = if (selected) cs.onPrimary else cs.onSurface
    Row(
        modifier
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else cs.outline.copy(alpha = 0.45f), CircleShape)
            .clickable(onClick = onClick)
            .padding(start = if (selected) 10.dp else 16.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Selected chips lead with a check, the Material You filter-chip pattern used in the design.
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = fg, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
        } else if (dotColor != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/**
 * An icon sitting on a pale tonal tile with a squircle-ish corner radius. This is the
 * "note type icon" container of the design system and is reused for every sheet action.
 */
@Composable
fun IconTile(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Int = 40,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    tint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Box(
        modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.3f).dp))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size((size * 0.55f).dp))
    }
}
