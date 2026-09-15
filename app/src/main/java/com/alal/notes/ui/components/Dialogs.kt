package com.alal.notes.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.alal.notes.ui.util.rememberHaptics

/**
 * One shape, one rhythm, one button hierarchy for every prompt in the app: a rounded 28dp
 * container, an optional accent icon, a filled primary action and a quiet dismiss action.
 * The primary action also fires the matching haptic (success when it runs, a refusal buzz when
 * the form is still incomplete) so prompts feel the same everywhere.
 */
@Composable
fun AlalDialog(
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    supporting: String? = null,
    confirmLabel: String? = null,
    confirmEnabled: Boolean = true,
    onConfirm: (() -> Unit)? = null,
    dismissLabel: String? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 3.dp,
        icon = icon?.let { { Icon(it, null, tint = MaterialTheme.colorScheme.primary) } },
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (supporting != null) {
                    Text(
                        supporting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                content?.invoke(this)
            }
        },
        confirmButton = {
            if (confirmLabel != null && onConfirm != null) {
                Button(
                    onClick = { haptics.success(); onConfirm() },
                    enabled = confirmEnabled,
                ) { Text(confirmLabel) }
            }
        },
        dismissButton = dismissLabel?.let {
            { TextButton(onClick = { haptics.tick(); onDismiss() }) { Text(it) } }
        },
    )
}

/** Focus requester that grabs the keyboard as soon as the prompt appears. */
@Composable
fun rememberAutoFocus(): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    return requester
}
