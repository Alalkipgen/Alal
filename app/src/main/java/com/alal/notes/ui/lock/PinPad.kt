package com.alal.notes.ui.lock

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alal.notes.ui.util.rememberHaptics
import java.security.MessageDigest
import java.security.SecureRandom

/** PIN hashing helpers. Never store the PIN itself; store SHA-256(salt + pin) plus the salt. */
object PinCrypto {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8

    fun newSalt(): String {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hash(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest((salt + pin).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun matches(pin: String, salt: String, expectedHash: String): Boolean {
        if (salt.isBlank() || expectedHash.isBlank()) return false
        return MessageDigest.isEqual(hash(pin, salt).toByteArray(), expectedHash.toByteArray())
    }
}

/**
 * Dots + numeric keypad. `shakeTrigger` increments to play the wrong-PIN shake.
 * `onBiometric` (nullable) shows a fingerprint key in the bottom-left slot.
 */
@Composable
fun PinPad(
    pin: String,
    onPinChange: (String) -> Unit,
    onComplete: (String) -> Unit,
    shakeTrigger: Int,
    maxLength: Int = PinCrypto.MAX_LENGTH,
    autoSubmitLength: Int? = null,
    onBiometric: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    var shakeKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(shakeTrigger) { if (shakeTrigger > 0) shakeKey++ }
    val shake by animateFloatAsState(
        targetValue = if (shakeKey % 2 == 1) 1f else 0f,
        animationSpec = keyframes {
            durationMillis = 380
            0f at 0; 1f at 60; -1f at 140; 0.6f at 220; -0.4f at 300; 0f at 380
        },
        label = "shake",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
        // Dots
        Row(
            Modifier.offset(x = (shake * 10).dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val shown = maxOf(pin.length, autoSubmitLength ?: PinCrypto.MIN_LENGTH)
            repeat(shown) { i ->
                val filled = i < pin.length
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (filled) cs.primary else Color.Transparent)
                        .border(1.5.dp, if (filled) cs.primary else cs.outline, CircleShape),
                )
            }
        }

        // Keys
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (row in rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    for (k in row) Key(k) {
                        if (pin.length < maxLength) {
                            haptics.tick()
                            val next = pin + k
                            onPinChange(next)
                            if (autoSubmitLength != null && next.length == autoSubmitLength) onComplete(next)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBiometric != null) {
                    IconButton(onClick = onBiometric, modifier = Modifier.size(72.dp)) {
                        Icon(Icons.Rounded.Fingerprint, null, tint = cs.primary, modifier = Modifier.size(32.dp))
                    }
                } else {
                    Spacer(Modifier.size(72.dp))
                }
                Key("0") {
                    if (pin.length < maxLength) {
                        haptics.tick()
                        val next = pin + "0"
                        onPinChange(next)
                        if (autoSubmitLength != null && next.length == autoSubmitLength) onComplete(next)
                    }
                }
                IconButton(onClick = { if (pin.isNotEmpty()) { haptics.tick(); onPinChange(pin.dropLast(1)) } }, modifier = Modifier.size(72.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.Backspace, null, tint = cs.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Key(label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(cs.surfaceContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.headlineSmall, color = cs.onSurface)
    }
}
