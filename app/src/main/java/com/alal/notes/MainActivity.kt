package com.alal.notes

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.alal.notes.ui.AlalRoot
import com.alal.notes.ui.MainViewModel
import com.alal.notes.ui.theme.AlalTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    /** One-shot launch intent for app shortcuts; consumed by the root composable. */
    private val pendingAction = mutableStateOf<String?>(null)

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hold the splash until the stored settings are known: drawing from defaults would skip
        // the app lock and flash the default theme/language.
        splash.setKeepOnScreenCondition { vm.settings.value == null }
        enableEdgeToEdge()
        pendingAction.value = encodeAction(intent)

        setContent {
            val settings = vm.settings.collectAsState().value ?: return@setContent
            LaunchedEffect(settings.language) { vm.applyLocale(settings.language) }
            AlalTheme(settings = settings) {
                AlalRoot(
                    settings = settings,
                    pendingAction = pendingAction.value,
                    onActionConsumed = { pendingAction.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingAction.value = encodeAction(intent)
    }

    /** Turns a launch intent into a single string the root composable can consume once. */
    private fun encodeAction(intent: Intent?): String? {
        val action = intent?.action?.takeIf { it.startsWith("com.alal.notes.action.") } ?: return null
        if (action == ACTION_OPEN_NOTE) {
            val id = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
            if (id <= 0) return null
            return "$ACTION_OPEN_NOTE:$id"
        }
        return action
    }

    companion object {
        const val ACTION_NEW_NOTE = "com.alal.notes.action.NEW_NOTE"
        const val ACTION_SEARCH = "com.alal.notes.action.SEARCH"
        /** From a reminder notification. Encoded as "<action>:<noteId>". */
        const val ACTION_OPEN_NOTE = "com.alal.notes.action.OPEN_NOTE"
        const val EXTRA_NOTE_ID = "noteId"
    }
}
