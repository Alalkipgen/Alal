package com.alal.notes.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alal.notes.R
import com.alal.notes.ui.components.PillChip
import com.alal.notes.ui.theme.ActionColors
import com.alal.notes.ui.util.Format
import com.alal.notes.ui.util.rememberIs24Hour
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Pick a date + time for a note reminder. Quick chips cover the common cases; the two buttons
 * open the Material 3 date / time pickers. `onSet(null)` removes an existing reminder.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReminderDialog(current: Long?, onDismiss: () -> Unit, onSet: (Long?) -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val is24h = rememberIs24Hour()
    val initial = remember(current) {
        current?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone) }
            ?: LocalDateTime.now(zone).plusHours(1).withMinute(0).withSecond(0).withNano(0)
    }
    var date by remember { mutableStateOf(initial.toLocalDate()) }
    var time by remember { mutableStateOf(initial.toLocalTime()) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    val chosenMillis = LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()
    val inPast = chosenMillis <= System.currentTimeMillis()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Schedule, null, tint = ActionColors.reminder) },
        title = { Text(stringResource(R.string.reminder)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (current != null) {
                    Text(
                        stringResource(R.string.reminder_current, Format.full(current)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Quick picks
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val now = LocalDateTime.now(zone)
                    QuickChip(stringResource(R.string.reminder_in_1h), date, time, now.plusHours(1).withSecond(0).withNano(0)) { date = it.toLocalDate(); time = it.toLocalTime() }
                    QuickChip(stringResource(R.string.reminder_in_3h), date, time, now.plusHours(3).withSecond(0).withNano(0)) { date = it.toLocalDate(); time = it.toLocalTime() }
                    val tonight = LocalDateTime.of(now.toLocalDate(), LocalTime.of(20, 0)).let { if (it.isBefore(now)) it.plusDays(1) else it }
                    QuickChip(stringResource(R.string.reminder_tonight), date, time, tonight) { date = it.toLocalDate(); time = it.toLocalTime() }
                    val tomorrow = LocalDateTime.of(now.toLocalDate().plusDays(1), LocalTime.of(9, 0))
                    QuickChip(stringResource(R.string.reminder_tomorrow_morning), date, time, tomorrow) { date = it.toLocalDate(); time = it.toLocalTime() }
                    val nextWeek = LocalDateTime.of(now.toLocalDate().plusWeeks(1), LocalTime.of(9, 0))
                    QuickChip(stringResource(R.string.reminder_next_week), date, time, nextWeek) { date = it.toLocalDate(); time = it.toLocalTime() }
                }
                // Explicit pickers
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.CalendarMonth, null); Text("  " + date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
                    }
                    OutlinedButton(onClick = { showTime = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Schedule, null); Text("  " + time.format(DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a")))
                    }
                }
                if (inPast) {
                    Text(stringResource(R.string.reminder_in_past), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSet(chosenMillis) }, enabled = !inPast) { Text(stringResource(R.string.reminder_set_action)) }
        },
        dismissButton = {
            Row {
                if (current != null) {
                    TextButton(onClick = { onSet(null) }) { Text(stringResource(R.string.reminder_remove), color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms -> date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = state) }
    }

    if (showTime) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = is24h)
        AlertDialog(
            onDismissRequest = { showTime = false },
            text = { TimePicker(state = state, modifier = Modifier.padding(top = 8.dp)) },
            confirmButton = {
                TextButton(onClick = { time = LocalTime.of(state.hour, state.minute); showTime = false }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun QuickChip(label: String, date: LocalDate, time: LocalTime, target: LocalDateTime, onPick: (LocalDateTime) -> Unit) {
    val selected = date == target.toLocalDate() && time.hour == target.hour && time.minute == target.minute
    PillChip(label, selected = selected, onClick = { onPick(target) })
}
