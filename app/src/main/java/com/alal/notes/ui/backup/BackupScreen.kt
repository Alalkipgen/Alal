package com.alal.notes.ui.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.alal.notes.R
import com.alal.notes.data.backup.BackupFile
import com.alal.notes.data.backup.BackupInfo
import com.alal.notes.data.backup.BackupManager
import com.alal.notes.data.backup.RestoreMode
import com.alal.notes.data.prefs.Settings
import com.alal.notes.data.prefs.UserPreferences
import com.alal.notes.data.work.AutoBackupWorker
import com.alal.notes.ui.more.SubScreenTopBar
import com.alal.notes.ui.util.Format
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ------------------------------------------------------------------ view model

sealed interface BackupUiEvent {
    data class Message(val resId: Int, val arg: String? = null) : BackupUiEvent
    data class Share(val uri: Uri) : BackupUiEvent
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backup: BackupManager,
    private val prefs: UserPreferences,
) : ViewModel() {

    val settings: StateFlow<Settings> = prefs.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _event = MutableStateFlow<BackupUiEvent?>(null)
    val event: StateFlow<BackupUiEvent?> = _event.asStateFlow()
    fun consumeEvent() { _event.value = null }

    /** A backup file the user picked, waiting for confirmation. */
    private val _pending = MutableStateFlow<Pair<BackupFile, BackupInfo>?>(null)
    val pending: StateFlow<Pair<BackupFile, BackupInfo>?> = _pending.asStateFlow()
    fun cancelRestore() { _pending.value = null }

    fun suggestedName() = backup.suggestedFileName()

    fun exportTo(uri: Uri) = run {
        try {
            backup.writeTo(uri)
            _event.value = BackupUiEvent.Message(R.string.backup_saved)
        } catch (e: Exception) {
            _event.value = BackupUiEvent.Message(R.string.backup_failed, e.message)
        }
    }

    fun share() = run {
        try {
            _event.value = BackupUiEvent.Share(backup.writeForShare())
        } catch (e: Exception) {
            _event.value = BackupUiEvent.Message(R.string.backup_failed, e.message)
        }
    }

    fun inspect(uri: Uri) = run {
        try {
            val file = backup.read(uri)
            _pending.value = file to backup.describe(file)
        } catch (e: Exception) {
            _event.value = BackupUiEvent.Message(R.string.restore_invalid_file, e.message)
        }
    }

    fun restore(mode: RestoreMode, applySettings: Boolean) {
        val (file, _) = _pending.value ?: return
        _pending.value = null
        run {
            try {
                val r = backup.restore(file, mode, applySettings)
                _event.value = BackupUiEvent.Message(R.string.restore_done, r.notesAdded.toString())
            } catch (e: Exception) {
                _event.value = BackupUiEvent.Message(R.string.restore_failed, e.message)
            }
        }
    }

    fun setAutoBackupFolder(uri: Uri?) = viewModelScope.launch {
        if (uri == null) {
            prefs.setAutoBackupUri("")
            _event.value = BackupUiEvent.Message(R.string.auto_backup_off)
        } else {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            prefs.setAutoBackupUri(uri.toString())
            AutoBackupWorker.schedule(context)
            AutoBackupWorker.runNow(context)
            _event.value = BackupUiEvent.Message(R.string.auto_backup_on)
        }
    }

    fun backupToFolderNow() = run {
        val uriString = settings.value.autoBackupUri
        if (uriString.isBlank()) return@run
        val ok = try { backup.writeToFolder(Uri.parse(uriString)) } catch (e: Exception) { false }
        _event.value = if (ok) BackupUiEvent.Message(R.string.backup_saved) else BackupUiEvent.Message(R.string.auto_backup_folder_lost)
    }

    private fun run(block: suspend () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try { block() } finally { _busy.value = false }
        }
    }
}

// ------------------------------------------------------------------ screen

@Composable
fun BackupScreen(onBack: () -> Unit, vm: BackupViewModel = hiltViewModel()) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val event by vm.event.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportTo(uri)
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.inspect(uri)
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setAutoBackupFolder(uri)
    }

    LaunchedEffect(event) {
        when (val e = event) {
            null -> Unit
            is BackupUiEvent.Message -> {
                val text = context.getString(e.resId) + (e.arg?.let { " ($it)" } ?: "")
                vm.consumeEvent()
                snackbar.showSnackbar(text)
            }
            is BackupUiEvent.Share -> {
                vm.consumeEvent()
                val send = Intent(Intent.ACTION_SEND)
                    .setType("application/json")
                    .putExtra(Intent.EXTRA_STREAM, e.uri)
                    .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.backup_share_subject))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, context.getString(R.string.backup_share)))
            }
        }
    }

    Scaffold(
        containerColor = cs.background,
        topBar = { SubScreenTopBar(stringResource(R.string.backup_restore), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(stringResource(R.string.backup_intro), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)

            // ---- Backup ----
            SectionCard(title = stringResource(R.string.backup_title), icon = Icons.Rounded.Backup) {
                if (settings.lastBackupAt > 0) {
                    Text(
                        stringResource(R.string.backup_last, Format.full(settings.lastBackupAt)),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { createDoc.launch(vm.suggestedName()) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.backup_save_file))
                    }
                    FilledTonalButton(onClick = { vm.share() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.backup_share))
                    }
                }
                Text(stringResource(R.string.backup_share_hint), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }

            // ---- Auto backup ----
            SectionCard(title = stringResource(R.string.auto_backup_title), icon = Icons.Rounded.CloudUpload) {
                val on = settings.autoBackupUri.isNotBlank()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.auto_backup_daily), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (on) folderLabel(settings.autoBackupUri) else stringResource(R.string.auto_backup_pick_hint),
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        )
                    }
                    Switch(checked = on, onCheckedChange = { checked -> if (checked) pickFolder.launch(null) else vm.setAutoBackupFolder(null) })
                }
                if (on) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { pickFolder.launch(null) }, enabled = !busy) { Text(stringResource(R.string.auto_backup_change_folder)) }
                        OutlinedButton(onClick = { vm.backupToFolderNow() }, enabled = !busy) { Text(stringResource(R.string.auto_backup_now)) }
                    }
                }
                Text(stringResource(R.string.auto_backup_hint), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }

            // ---- Restore ----
            SectionCard(title = stringResource(R.string.restore_title), icon = Icons.Rounded.Restore) {
                Text(stringResource(R.string.restore_hint), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                OutlinedButton(onClick = { openDoc.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }, enabled = !busy) {
                    Text(stringResource(R.string.restore_pick_file))
                }
            }

            if (busy) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    pending?.let { (_, info) ->
        RestoreDialog(info = info, onDismiss = { vm.cancelRestore() }) { mode, applySettings -> vm.restore(mode, applySettings) }
    }
}

@Composable
private fun SectionCard(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null, tint = cs.primary, modifier = Modifier.size(22.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
private fun RestoreDialog(info: BackupInfo, onDismiss: () -> Unit, onConfirm: (RestoreMode, Boolean) -> Unit) {
    var mode by remember { mutableStateOf(RestoreMode.MERGE) }
    var applySettings by remember { mutableStateOf(info.hasSettings) }
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (info.exportedAt > 0) {
                    Text(stringResource(R.string.restore_file_date, Format.full(info.exportedAt)), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Text(
                    stringResource(R.string.restore_file_summary, info.notes, info.trashedNotes, info.categories, info.tags),
                    style = MaterialTheme.typography.bodyMedium,
                )
                HorizontalDivider()
                ModeRow(stringResource(R.string.restore_mode_merge), stringResource(R.string.restore_mode_merge_hint), mode == RestoreMode.MERGE) { mode = RestoreMode.MERGE }
                ModeRow(stringResource(R.string.restore_mode_replace), stringResource(R.string.restore_mode_replace_hint), mode == RestoreMode.REPLACE) { mode = RestoreMode.REPLACE }
                if (info.hasSettings) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.restore_apply_settings), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = applySettings, onCheckedChange = { applySettings = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(mode, applySettings) },
                colors = if (mode == RestoreMode.REPLACE) androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = cs.error, contentColor = cs.onError) else androidx.compose.material3.ButtonDefaults.buttonColors(),
            ) { Text(stringResource(R.string.restore_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ModeRow(title: String, hint: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "content://com.android.externalstorage.documents/tree/primary%3AAlal" -> "primary:Alal" */
private fun folderLabel(uriString: String): String = try {
    val uri = Uri.parse(uriString)
    android.provider.DocumentsContract.getTreeDocumentId(uri)
} catch (e: Exception) {
    uriString
}
