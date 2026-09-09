package com.alal.notes.ui.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.alal.notes.R
import com.alal.notes.data.prefs.Settings
import com.alal.notes.domain.model.AccentMode
import com.alal.notes.domain.model.AppThemeKey
import com.alal.notes.domain.model.CardStyle
import com.alal.notes.domain.model.PaperTexture
import com.alal.notes.domain.model.UiDensity
import com.alal.notes.ui.components.ColorSwatch
import com.alal.notes.ui.components.PaperTextureBackground
import com.alal.notes.ui.editor.label
import com.alal.notes.ui.editor.parseHex
import com.alal.notes.ui.home.toArgbInt
import com.alal.notes.ui.more.SubScreenTopBar
import com.alal.notes.ui.theme.Accents
import com.alal.notes.ui.theme.Fonts
import com.alal.notes.ui.theme.Palettes
import com.alal.notes.ui.theme.ThemePalette
import com.alal.notes.ui.theme.rememberFontFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(settings: Settings, onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val cs = MaterialTheme.colorScheme
    Scaffold(containerColor = cs.background, topBar = { SubScreenTopBar(stringResource(R.string.appearance), onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {

            SettingSection(stringResource(R.string.theme)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemeTile(null, settings.theme == AppThemeKey.SYSTEM, stringResource(R.string.theme_system)) { vm.edit { setTheme(AppThemeKey.SYSTEM) } }
                    for (p in Palettes.all) {
                        ThemeTile(p, settings.theme == p.key, themeLabel(p.key)) { vm.edit { setTheme(p.key) } }
                    }
                }
            }

            SettingSection(stringResource(R.string.accent)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingSwitch(stringResource(R.string.dynamic_color), settings.accentMode == AccentMode.DYNAMIC && settings.dynamicColor) { on ->
                        vm.edit { setDynamicColor(on); setAccentMode(if (on) AccentMode.DYNAMIC else AccentMode.PRESET) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    for (preset in Accents.presets) {
                        ColorSwatch(
                            color = preset.color,
                            selected = settings.accentMode == AccentMode.PRESET && settings.accentColor == preset.color.toArgbInt(),
                            onClick = { vm.edit { setAccentMode(AccentMode.PRESET); setAccentColor(preset.color.toArgbInt()) } },
                            size = 34,
                        )
                    }
                }
                var hex by remember(settings.accentColor) { mutableStateOf(String.format("#%06X", 0xFFFFFF and settings.accentColor)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = hex, onValueChange = { hex = it.take(9) },
                        label = { Text(stringResource(R.string.hex_color)) }, singleLine = true,
                        modifier = Modifier.weight(1f),
                        isError = parseHex(hex) == null,
                    )
                    Spacer(Modifier.width(12.dp))
                    val parsed = parseHex(hex)
                    Box(
                        Modifier.size(40.dp).background(parsed ?: cs.surfaceVariant, RoundedCornerShape(12.dp))
                            .border(BorderStroke(if (settings.accentMode == AccentMode.CUSTOM) 2.dp else 0.dp, cs.onSurface), RoundedCornerShape(12.dp))
                            .clickable(enabled = parsed != null) { parsed?.let { c -> vm.edit { setAccentMode(AccentMode.CUSTOM); setAccentColor(c.toArgbInt()) } } },
                    )
                }
            }

            SettingSection(stringResource(R.string.font_latin)) {
                SettingChoice(stringResource(R.string.body_font), Fonts.latin, (Fonts.latin.firstOrNull { it.key == settings.bodyFont } ?: Fonts.latin.first()), { it.label }) { vm.edit { setBodyFont(it.key) } }
                SettingChoice(stringResource(R.string.title_font), Fonts.latin, (Fonts.latin.firstOrNull { it.key == settings.titleFont } ?: Fonts.latin[1]), { it.label }) { vm.edit { setTitleFont(it.key) } }
            }
            SettingSection(stringResource(R.string.font_myanmar)) {
                SettingChoice(stringResource(R.string.font_myanmar), Fonts.myanmar, (Fonts.myanmar.firstOrNull { it.key == settings.myanmarFont } ?: Fonts.myanmar.first()), { it.label }) { vm.edit { setMyanmarFont(it.key) } }
                val fam: FontFamily = rememberFontFamily(settings.myanmarFont)
                Text(
                    stringResource(R.string.preview_text),
                    fontFamily = fam, fontSize = settings.bodySize.sp, lineHeight = (settings.bodySize * settings.lineHeight).sp,
                    modifier = Modifier.fillMaxWidth().background(cs.surfaceContainer, MaterialTheme.shapes.medium).padding(16.dp),
                )
            }

            SettingSection(stringResource(R.string.paper_texture)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (t in PaperTexture.entries) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { vm.edit { setPaperTexture(t) } }) {
                            Box(
                                Modifier.size(64.dp).background(cs.surface, RoundedCornerShape(12.dp))
                                    .border(BorderStroke(if (settings.paperTexture == t) 2.dp else 1.dp, if (settings.paperTexture == t) cs.primary else cs.outlineVariant), RoundedCornerShape(12.dp)),
                            ) { PaperTextureBackground(t, cs.onSurface.copy(alpha = 0.25f), Modifier.fillMaxSize().padding(2.dp), spacing = 12.dp) }
                            Text(t.label(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }

            SettingSection(stringResource(R.string.tab_notes)) {
                SettingSlider(stringResource(R.string.card_preview_size), settings.cardPreviewSize, 13..17, valueLabel = { "${it}sp" }) { vm.edit { setCardPreviewSize(it) } }
                SettingChoice(stringResource(R.string.ui_density), UiDensity.entries, settings.uiDensity, { d ->
                    when (d) { UiDensity.COMPACT -> stringResource(R.string.density_compact); UiDensity.DEFAULT -> stringResource(R.string.density_default); UiDensity.COMFORTABLE -> stringResource(R.string.density_comfortable) }
                }) { vm.edit { setUiDensity(it) } }
                SettingChoice(stringResource(R.string.card_style), CardStyle.entries, settings.cardStyle, { s ->
                    when (s) { CardStyle.SOFT -> stringResource(R.string.card_soft); CardStyle.DIVIDER -> stringResource(R.string.card_divider) }
                }) { vm.edit { setCardStyle(it) } }
                SettingSwitch(stringResource(R.string.themed_icon), settings.themedIcon, stringResource(R.string.themed_icon_hint)) { vm.edit { setThemedIcon(it) } }
            }
        }
    }
}

@Composable
private fun ThemeTile(p: ThemePalette?, selected: Boolean, label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier.size(width = 64.dp, height = 84.dp)
                .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) cs.primary else cs.outlineVariant), RoundedCornerShape(14.dp)),
        ) {
            if (p == null) {
                Row(Modifier.fillMaxSize().padding(2.dp)) {
                    Box(Modifier.weight(1f).fillMaxSize().background(Palettes.paper.background, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)))
                    Box(Modifier.weight(1f).fillMaxSize().background(Palettes.ink.background, RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)))
                }
            } else {
                Column(Modifier.fillMaxSize().padding(2.dp).background(p.background, RoundedCornerShape(12.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.fillMaxWidth(0.7f).height(6.dp).background(p.onBackground, RoundedCornerShape(3.dp)))
                    Box(Modifier.fillMaxWidth().height(4.dp).background(p.onBackground.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
                    Box(Modifier.fillMaxWidth().height(4.dp).background(p.onBackground.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.fillMaxWidth().height(22.dp).background(p.surface, RoundedCornerShape(6.dp)))
                }
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
    }
}

@Composable
fun themeLabel(key: AppThemeKey): String = when (key) {
    AppThemeKey.PAPER -> stringResource(R.string.theme_paper)
    AppThemeKey.WHITE -> stringResource(R.string.theme_white)
    AppThemeKey.SEPIA -> stringResource(R.string.theme_sepia)
    AppThemeKey.MINT -> stringResource(R.string.theme_mint)
    AppThemeKey.SKY -> stringResource(R.string.theme_sky)
    AppThemeKey.ROSE -> stringResource(R.string.theme_rose)
    AppThemeKey.INK -> stringResource(R.string.theme_ink)
    AppThemeKey.SLATE -> stringResource(R.string.theme_slate)
    AppThemeKey.FOREST -> stringResource(R.string.theme_forest)
    AppThemeKey.BLACK -> stringResource(R.string.theme_black)
    AppThemeKey.SYSTEM -> stringResource(R.string.theme_system)
}
