package com.alal.notes.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alal.notes.R
import com.alal.notes.data.entity.Template
import com.alal.notes.domain.template.BuiltInTemplates

fun templateIcon(name: String): ImageVector = when (name) {
    "edit" -> Icons.Rounded.Edit
    "article" -> Icons.Rounded.Article
    "mic" -> Icons.Rounded.Mic
    "science" -> Icons.Rounded.Science
    "lightbulb" -> Icons.Rounded.Lightbulb
    else -> Icons.Rounded.Description
}

@Composable
fun templateName(t: Template): String = when (t.key) {
    BuiltInTemplates.BLANK -> stringResource(R.string.template_blank)
    BuiltInTemplates.FEATURE -> stringResource(R.string.template_feature)
    BuiltInTemplates.INTERVIEW -> stringResource(R.string.template_interview)
    BuiltInTemplates.RESEARCH -> stringResource(R.string.template_research)
    BuiltInTemplates.IDEA -> stringResource(R.string.template_idea)
    else -> t.name
}

@Composable
fun TemplatePickerSheet(templates: List<Template>, onDismiss: () -> Unit, onPick: (Template?) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, shape = MaterialTheme.shapes.large) {
        Text(
            stringResource(R.string.choose_template),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        val list = if (templates.isEmpty()) BuiltInTemplates.all else templates
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { t -> TemplateRow(t, onClick = { onPick(t) }) }
        }
    }
}

@Composable
fun TemplateRow(t: Template, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = cs.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.small, color = cs.primaryContainer) {
                Icon(templateIcon(t.icon), contentDescription = null, tint = cs.onPrimaryContainer, modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(templateName(t), style = MaterialTheme.typography.titleMedium)
                val preview = t.body.lineSequence().filter { it.isNotBlank() }.take(3).joinToString(" · ") { it.trim().removePrefix("#").removePrefix("#").trim() }
                if (preview.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(preview, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (t.isBuiltIn) {
                    Text(stringResource(R.string.built_in), style = MaterialTheme.typography.labelSmall, color = cs.primary)
                }
            }
            trailing?.invoke()
        }
    }
}
