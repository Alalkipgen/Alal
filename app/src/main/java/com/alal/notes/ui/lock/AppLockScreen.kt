package com.alal.notes.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.alal.notes.R
import com.alal.notes.data.prefs.Settings
import com.alal.notes.data.prefs.UserPreferences
import com.alal.notes.data.repository.NoteRepository
import com.alal.notes.ui.more.SubScreenTopBar
import com.alal.notes.ui.settings.SettingChoice
import com.alal.notes.ui.settings.SettingSection
import com.alal.notes.ui.settings.SettingSwitch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val prefs: UserPreferences,
    private val repository: NoteRepository,
) : ViewModel() {
    val settings: StateFlow<Settings?> = prefs.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun savePin(pin: String) = viewModelScope.launch {
        val salt = PinCrypto.newSalt()
        prefs.setLockPin(PinCrypto.hash(pin, salt), salt)
        prefs.setLockEnabled(true)
    }

    fun setEnabled(v: Boolean) = viewModelScope.launch { prefs.setLockEnabled(v) }
    fun setBiometric(v: Boolean) = viewModelScope.launch { prefs.setLockBiometric(v) }
    fun setTimeout(sec: Int) = viewModelScope.launch { prefs.setLockTimeoutSec(sec) }

    /** Removes the PIN and unlocks every note so nothing becomes unreachable. */
    fun removeLock() = viewModelScope.launch {
        prefs.clearLock()
        repository.unlockAllNotes()
    }
}

private enum class PinFlow { NONE, VERIFY_OLD, ENTER_NEW, CONFIRM_NEW, VERIFY_REMOVE }

@Composable
fun AppLockScreen(onBack: () -> Unit, vm: AppLockViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val hasPin = s.lockPinHash.isNotBlank()
    val bioAvailable = remember(context) { biometricsAvailable(context) }

    var flow by remember { mutableStateOf(PinFlow.NONE) }
    var pin by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var shake by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<Int?>(null) }
    var afterVerify by remember { mutableStateOf(PinFlow.ENTER_NEW) }

    fun reset() { flow = PinFlow.NONE; pin = ""; firstPin = ""; error = null }

    Scaffold(
        containerColor = cs.background,
        topBar = { SubScreenTopBar(stringResource(R.string.app_lock), onBack) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.lock_intro),
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            SettingSection(stringResource(R.string.lock_pin_section)) {
                if (!hasPin) {
                    Button(onClick = { flow = PinFlow.ENTER_NEW }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.lock_set_pin)) }
                } else {
                    SettingSwitch(
                        title = stringResource(R.string.lock_enabled),
                        subtitle = stringResource(R.string.lock_enabled_hint),
                        checked = s.lockEnabled,
                    ) { vm.setEnabled(it) }
                    OutlinedButton(onClick = { afterVerify = PinFlow.ENTER_NEW; flow = PinFlow.VERIFY_OLD }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.lock_change_pin))
                    }
                    TextButton(onClick = { flow = PinFlow.VERIFY_REMOVE }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.lock_remove_pin), color = cs.error)
                    }
                }
            }

            if (hasPin) {
                SettingSection(stringResource(R.string.lock_options)) {
                    SettingSwitch(
                        title = stringResource(R.string.lock_biometric),
                        subtitle = if (bioAvailable) stringResource(R.string.lock_biometric_hint) else stringResource(R.string.lock_biometric_unavailable),
                        checked = s.lockBiometric && bioAvailable,
                    ) { if (bioAvailable) vm.setBiometric(it) }
                    val options = listOf(0, 30, 60, 300, 900)
                    SettingChoice(
                        title = stringResource(R.string.lock_timeout),
                        options = options,
                        selected = if (s.lockTimeoutSec in options) s.lockTimeoutSec else 0,
                        label = { sec ->
                            when (sec) {
                                0 -> stringResource(R.string.lock_timeout_immediately)
                                30 -> stringResource(R.string.lock_timeout_seconds, 30)
                                60 -> stringResource(R.string.lock_timeout_minutes, 1)
                                else -> stringResource(R.string.lock_timeout_minutes, sec / 60)
                            }
                        },
                    ) { vm.setTimeout(it) }
                }
                SettingSection(stringResource(R.string.lock_notes_section)) {
                    Text(stringResource(R.string.lock_notes_hint), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // ---- PIN entry dialog (set / change / verify / remove)
    if (flow != PinFlow.NONE) {
        val title = when (flow) {
            PinFlow.VERIFY_OLD, PinFlow.VERIFY_REMOVE -> stringResource(R.string.lock_enter_current_pin)
            PinFlow.ENTER_NEW -> stringResource(R.string.lock_enter_new_pin)
            PinFlow.CONFIRM_NEW -> stringResource(R.string.lock_confirm_new_pin)
            PinFlow.NONE -> ""
        }
        fun submit(entered: String) {
            when (flow) {
                PinFlow.VERIFY_OLD, PinFlow.VERIFY_REMOVE -> {
                    if (PinCrypto.matches(entered, s.lockSalt, s.lockPinHash)) {
                        if (flow == PinFlow.VERIFY_REMOVE) { vm.removeLock(); reset() } else { flow = afterVerify; pin = ""; error = null }
                    } else { shake++; pin = ""; error = R.string.pin_wrong }
                }
                PinFlow.ENTER_NEW -> {
                    if (entered.length < PinCrypto.MIN_LENGTH) { shake++; error = R.string.pin_too_short } else { firstPin = entered; pin = ""; flow = PinFlow.CONFIRM_NEW; error = null }
                }
                PinFlow.CONFIRM_NEW -> {
                    if (entered == firstPin) { vm.savePin(entered); reset() } else { shake++; pin = ""; firstPin = ""; flow = PinFlow.ENTER_NEW; error = R.string.pin_mismatch }
                }
                PinFlow.NONE -> Unit
            }
        }
        AlertDialog(
            onDismissRequest = ::reset,
            title = { Text(title) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.pin_length_hint, PinCrypto.MIN_LENGTH, PinCrypto.MAX_LENGTH),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
                    PinPad(pin = pin, onPinChange = { pin = it; error = null }, onComplete = ::submit, shakeTrigger = shake)
                    Text(error?.let { stringResource(it) } ?: "", style = MaterialTheme.typography.bodySmall, color = cs.error, modifier = Modifier.height(20.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = { submit(pin) }, enabled = pin.length >= PinCrypto.MIN_LENGTH) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = ::reset) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
