package com.alal.notes.ui.theme

import androidx.compose.ui.graphics.Color
import com.alal.notes.domain.model.AppThemeKey
import com.alal.notes.domain.model.NoteStatus

/** Background / text / surface triple for one theme (§3.4). */
data class ThemePalette(val key: AppThemeKey, val background: Color, val onBackground: Color, val surface: Color, val dark: Boolean)

object Palettes {
    val paper = ThemePalette(AppThemeKey.PAPER, Color(0xFFFAF8F3), Color(0xFF1C1B1F), Color(0xFFFFFFFF), false)
    val white = ThemePalette(AppThemeKey.WHITE, Color(0xFFFFFFFF), Color(0xFF1C1B1F), Color(0xFFF4F4F5), false)
    val sepia = ThemePalette(AppThemeKey.SEPIA, Color(0xFFF4ECD8), Color(0xFF3B2F1E), Color(0xFFFBF6EA), false)
    val mint = ThemePalette(AppThemeKey.MINT, Color(0xFFEEF5F1), Color(0xFF1B2E25), Color(0xFFF7FBF9), false)
    val sky = ThemePalette(AppThemeKey.SKY, Color(0xFFEDF3F9), Color(0xFF152131), Color(0xFFF6F9FC), false)
    val rose = ThemePalette(AppThemeKey.ROSE, Color(0xFFF9EFF1), Color(0xFF2E1C21), Color(0xFFFDF6F7), false)
    val ink = ThemePalette(AppThemeKey.INK, Color(0xFF121212), Color(0xFFE6E1D8), Color(0xFF1E1E1E), true)
    val slate = ThemePalette(AppThemeKey.SLATE, Color(0xFF1A1D24), Color(0xFFDDE1E8), Color(0xFF242832), true)
    val forest = ThemePalette(AppThemeKey.FOREST, Color(0xFF0F1A15), Color(0xFFD6E4DA), Color(0xFF182420), true)
    val black = ThemePalette(AppThemeKey.BLACK, Color(0xFF000000), Color(0xFFE6E1D8), Color(0xFF121212), true)

    val all = listOf(paper, white, sepia, mint, sky, rose, ink, slate, forest, black)

    fun resolve(key: AppThemeKey, systemDark: Boolean): ThemePalette = when (key) {
        AppThemeKey.SYSTEM -> if (systemDark) ink else paper
        else -> all.first { it.key == key }
    }
}

data class AccentPreset(val name: String, val color: Color)

object Accents {
    val teal = Color(0xFF2A7F7F)
    val presets = listOf(
        AccentPreset("Teal", teal),
        AccentPreset("Indigo", Color(0xFF4F5BD5)),
        AccentPreset("Amber", Color(0xFFD98E04)),
        AccentPreset("Coral", Color(0xFFE0645C)),
        AccentPreset("Forest", Color(0xFF2E7D4F)),
        AccentPreset("Plum", Color(0xFF7B4FA3)),
        AccentPreset("Ocean", Color(0xFF1E6FB8)),
        AccentPreset("Graphite", Color(0xFF3A3A3A)),
    )
}

/** Fixed semantic colours for icon actions (§3.2). */
object ActionColors {
    val pin = Color(0xFFF2A900)      // amber
    val reminder = Color(0xFF2E9E5B) // green
    val lock = Color(0xFF2F6FDB)     // blue
    val archive = Color(0xFF7B4FA3)  // purple
    val trash = Color(0xFFD9483B)    // red
}

fun NoteStatus.color(dark: Boolean): Color = when (this) {
    NoteStatus.IDEA -> if (dark) Color(0xFFB0B0B0) else Color(0xFF6F6F6F)
    NoteStatus.RESEARCH -> if (dark) Color(0xFFB79BE0) else Color(0xFF7B4FA3)
    NoteStatus.DRAFT -> if (dark) Color(0xFFF0B84A) else Color(0xFFD98E04)
    NoteStatus.EDITING -> if (dark) Color(0xFF7FB3F5) else Color(0xFF1E6FB8)
    NoteStatus.SUBMITTED -> if (dark) Color(0xFFF5A46B) else Color(0xFFE0702B)
    NoteStatus.PUBLISHED -> if (dark) Color(0xFF7ACF97) else Color(0xFF2E7D4F)
}

/** 12 pastel note backgrounds (light). Darkened automatically in dark themes. */
object NoteBackgrounds {
    val pastels: List<Color> = listOf(
        Color(0xFFFFF4E0), Color(0xFFFFE8E0), Color(0xFFFDE2EA), Color(0xFFF1E4F7),
        Color(0xFFE3E8FB), Color(0xFFDFF1FA), Color(0xFFDFF6EE), Color(0xFFE8F5D9),
        Color(0xFFFBF7CF), Color(0xFFF3EDE4), Color(0xFFE9E9EC), Color(0xFFFFEFD5),
    )

    data class Gradient(val name: String, val start: Color, val end: Color)

    val gradients: List<Gradient> = listOf(
        Gradient("Dawn", Color(0xFFFFE8D6), Color(0xFFFFD1DC)),
        Gradient("Mist", Color(0xFFE4F0F6), Color(0xFFF3E9F5)),
        Gradient("Leaf", Color(0xFFE6F5E1), Color(0xFFD8F0EB)),
        Gradient("Sand", Color(0xFFF8EFD8), Color(0xFFF1E1CC)),
        Gradient("Lilac", Color(0xFFEDE4FA), Color(0xFFDDE8FA)),
        Gradient("Slate", Color(0xFFE6E9EF), Color(0xFFD7DCE5)),
    )

    /** Darkens a pastel so it works on a dark theme (~22% luminance). */
    fun forDark(c: Color): Color {
        val r = c.red * 0.28f
        val g = c.green * 0.28f
        val b = c.blue * 0.28f
        return Color(r + 0.06f, g + 0.06f, b + 0.06f, 1f)
    }
}
