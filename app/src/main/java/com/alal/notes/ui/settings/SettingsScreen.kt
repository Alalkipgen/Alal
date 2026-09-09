package com.alal.notes.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.alal.notes.R
import com.alal.notes.data.prefs.Settings
import com.alal.notes.domain.model.SortMode
import com.alal.notes.domain.model.ViewMode
import com.alal.notes.ui.more.SubScreenTopBar
import com.alal.notes.ui.util.Format

@Composable
fun SettingsScreen(settings: Settings, onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val cs = MaterialTheme.colorScheme
    Scaffold(containerColor = cs.background, topBar = { SubScreenTopBar(stringResource(R.string.settings), onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {

            SettingSection(stringResource(R.string.language)) {
                SettingChoice(stringResource(R.string.language), listOf("", "en", "my"), settings.language, { l ->
                    when (l) { "en" -> stringResource(R.string.language_en); "my" -> stringResource(R.string.language_my); else -> stringResource(R.string.language_system) }
                }) { vm.edit { setLanguage(it) } }
            }

            SettingSection(stringResource(R.string.daily_goal)) {
                SettingSlider(stringResource(R.string.daily_goal), settings.dailyGoal, 0..20000, step = 100, valueLabel = { if (it == 0) stringResource(R.string.no_goal) else Format.number(it) }) { vm.edit { setDailyGoal(it) } }
            }

            SettingSection(stringResource(R.string.tab_notes)) {
                SettingChoice(stringResource(R.string.view_mode), ViewMode.entries, settings.viewMode, { v ->
                    when (v) { ViewMode.GRID -> stringResource(R.string.view_grid); ViewMode.LIST -> stringResource(R.string.view_list); ViewMode.COMPACT -> stringResource(R.string.view_compact) }
                }) { vm.edit { setViewMode(it) } }
                SettingChoice(stringResource(R.string.sort), SortMode.entries, settings.sortMode, { s ->
                    when (s) { SortMode.MODIFIED -> stringResource(R.string.sort_modified); SortMode.CREATED -> stringResource(R.string.sort_created); SortMode.TITLE -> stringResource(R.string.sort_title); SortMode.WORDS -> stringResource(R.string.sort_words) }
                }) { vm.edit { setSortMode(it) } }
            }
        }
    }
}
