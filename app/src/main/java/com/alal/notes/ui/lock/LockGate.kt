package com.alal.notes.ui.lock

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.alal.notes.R
import com.alal.notes.data.prefs.Settings
import com.alal.notes.ui.util.rememberHaptics
import com.alal.notes.ui.theme.ActionColors

// ------------------------------------------------------------------ biometrics

fun Context.findActivity(): FragmentActivity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

fun biometricsAvailable(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
    ) == BiometricManager.BIOMETRIC_SUCCESS

fun showBiometricPrompt(context: Context, title: String, subtitle: String, onSuccess: () -> Unit, onFail: () -> Unit = {}) {
    val activity = context.findActivity() ?: return onFail()
    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail()
    })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        .build()
    prompt.authenticate(info)
}

// ------------------------------------------------------------------ unlock surface

/**
 * Full-screen PIN / biometric prompt. Used by [LockGate] (whole app) and [NoteLockOverlay] (one note).
 * Wrong attempts shake, and after 5 misses the pad pauses for 30 seconds.
 */
@Composable
fun UnlockSurface(
    settings: Settings,
    title: String,
    subtitle: String,
    onUnlocked: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var pin by rememberSaveable { mutableStateOf("") }
    var shake by remember { mutableIntStateOf(0) }
    var misses by rememberSaveable { mutableIntStateOf(0) }
    var lockedUntil by rememberSaveable { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf<Int?>(null) }

    val canBio = settings.lockBiometric && remember(context) { biometricsAvailable(context) }
    val bioTitle = stringResource(R.string.app_name)
    val bioSubtitle = stringResource(R.string.lock_biometric_subtitle)
    val bio: (() -> Unit)? = if (canBio) ({
        showBiometricPrompt(context, bioTitle, bioSubtitle, onSuccess = onUnlocked)
    }) else null

    // Offer biometrics automatically once when the surface appears.
    var autoPrompted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(canBio) {
        if (canBio && !autoPrompted) { autoPrompted = true; bio?.invoke() }
    }

    fun submit(entered: String) {
        if (System.currentTimeMillis() < lockedUntil) return
        if (PinCrypto.matches(entered, settings.lockSalt, settings.lockPinHash)) {
            haptics.confirm(); misses = 0; onUnlocked()
        } else {
            haptics.confirm(); shake++; pin = ""; misses++
            error = R.string.pin_wrong
            if (misses >= 5) { lockedUntil = System.currentTimeMillis() + 30_000; misses = 0; error = R.string.pin_too_many }
        }
    }

    if (onBack != null) BackHandler { onBack() }

    Box(Modifier.fillMaxSize().background(cs.background).systemBarsPadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.Lock, null, tint = ActionColors.lock, modifier = Modifier.size(40.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            PinPad(
                pin = pin,
                onPinChange = { pin = it; error = null },
                onComplete = ::submit,
                shakeTrigger = shake,
                onBiometric = bio,
            )
            TextButton(onClick = { submit(pin) }, enabled = pin.length >= PinCrypto.MIN_LENGTH) { Text(stringResource(R.string.unlock)) }
            val now = System.currentTimeMillis()
            val msg = when {
                now < lockedUntil -> stringResource(R.string.pin_too_many)
                error != null -> stringResource(error!!)
                else -> ""
            }
            Text(msg, style = MaterialTheme.typography.bodySmall, color = cs.error, modifier = Modifier.height(20.dp))
            if (onBack != null) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            }
        }
    }
}

// ------------------------------------------------------------------ app gate

/**
 * Wraps the whole app. When app lock is enabled, content is hidden behind [UnlockSurface]
 * on cold start and again after the app has been in the background longer than `lockTimeoutSec`.
 */
@Composable
fun LockGate(settings: Settings, content: @Composable () -> Unit) {
    val enabled = settings.lockEnabled && settings.lockPinHash.isNotBlank()
    var unlocked by rememberSaveable { mutableStateOf(!enabled) }
    var leftAt by rememberSaveable { mutableLongStateOf(0L) }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { leftAt = System.currentTimeMillis() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (enabled && unlocked && leftAt > 0) {
            val away = System.currentTimeMillis() - leftAt
            if (away >= settings.lockTimeoutSec * 1000L) unlocked = false
        }
    }
    // If the user disables the lock while the gate is showing, let them through.
    LaunchedEffect(enabled) { if (!enabled) unlocked = true }

    if (unlocked || !enabled) {
        content()
    } else {
        UnlockSurface(
            settings = settings,
            title = stringResource(R.string.app_name),
            subtitle = stringResource(R.string.lock_enter_pin),
            onUnlocked = { unlocked = true },
        )
    }
}

// ------------------------------------------------------------------ per-note overlay

/** Covers a locked note inside the editor until authenticated. */
@Composable
fun NoteLockOverlay(settings: Settings, onUnlocked: () -> Unit, onBack: () -> Unit) {
    if (settings.lockPinHash.isBlank()) {
        // Lock was set on the note but the PIN was since removed — nothing to check against.
        LaunchedEffect(Unit) { onUnlocked() }
        return
    }
    UnlockSurface(
        settings = settings,
        title = stringResource(R.string.locked_note),
        subtitle = stringResource(R.string.lock_enter_pin),
        onUnlocked = onUnlocked,
        onBack = onBack,
    )
}
