package com.alal.notes.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.alal.notes.R
import com.alal.notes.data.prefs.Settings
import com.alal.notes.domain.model.MarginMode
import com.alal.notes.domain.model.TitleSize
import com.alal.notes.domain.model.WordCountMethod
import com.alal.notes.ui.more.SubScreenTopBar
import com.alal.notes.ui.theme.Alal

@Composable
fun EditorSettingsScreen(settings: Settings, onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val cs = MaterialTheme.colorScheme
    Scaffold(containerColor = cs.background, topBar = { SubScreenTopBar(stringResource(R.string.editor_settings), onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {

            SettingSection(stringResource(R.string.text_settings)) {
                SettingSlider(stringResource(R.string.font_size), settings.bodySize, 14..30, valueLabel = { "${it}sp" }) { vm.edit { setBodySize(it) } }
                SettingChoice(stringResource(R.string.line_height), listOf(1.4f, 1.6f, 1.8f), settings.lineHeight, { String.format("%.1f", it) }) { vm.edit { setLineHeight(it) } }
                SettingChoice(stringResource(R.string.margin), MarginMode.entries, settings.margin, { m ->
                    when (m) { MarginMode.NARROW -> stringResource(R.string.margin_narrow); MarginMode.NORMAL -> stringResource(R.string.margin_normal); MarginMode.WIDE -> stringResource(R.string.margin_wide) }
                }) { vm.edit { setMargin(it) } }
                SettingChoice(stringResource(R.string.title_size), TitleSize.entries, settings.titleSize, { it.name }) { vm.edit { setTitleSize(it) } }
                Text(
                    stringResource(R.string.preview_text),
                    fontFamily = Alal.extras.type.body,
                    fontSize = settings.bodySize.sp,
                    lineHeight = (settings.bodySize * settings.lineHeight).sp,
                    modifier = Modifier.fillMaxWidth().background(cs.surfaceContainer, MaterialTheme.shapes.medium).padding(16.dp),
                )
            }

            SettingSection(stringResource(R.string.focus_mode)) {
                SettingSwitch(stringResource(R.string.typewriter_mode), settings.typewriterMode) { vm.edit { setTypewriterMode(it) } }
                SettingSwitch(stringResource(R.string.paragraph_focus), settings.paragraphFocus) { vm.edit { setParagraphFocus(it) } }
            }

            SettingSection(stringResource(R.string.save)) {
                SettingSlider(stringResource(R.string.autosave_delay), settings.autoSaveDelayMs, 300..5000, step = 100, valueLabel = { stringResource(R.string.milliseconds, it) }) { vm.edit { setAutoSaveDelayMs(it) } }
            }

            SettingSection(stringResource(R.string.word_count_method)) {
                SettingChoice(stringResource(R.string.word_count_method), WordCountMethod.entries, settings.wordCountMethod, { m ->
                    when (m) { WordCountMethod.MYANMAR_SYLLABLE -> stringResource(R.string.method_myanmar_syllable); WordCountMethod.SPACE_SPLIT -> stringResource(R.string.method_space_split) }
                }) { vm.edit { setWordCountMethod(it) } }
            }
        }
    }
}
