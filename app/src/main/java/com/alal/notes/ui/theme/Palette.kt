package com.alal.notes.ui.theme

import androidx.compose.ui.graphics.Color
import com.alal.notes.domain.model.AppThemeKey
import com.alal.notes.domain.model.NoteStatus
import kotlin.math.abs

/** Background / text / surface triple for one theme (§3.4). */
data class ThemePalette(val key: AppThemeKey, val background: Color, val onBackground: Color, val surface: Color, val dark: Boolean)

object Palettes {
    /** Material You light: the near-white lavender surface the redesign is built on. */
    val material = ThemePalette(AppThemeKey.MATERIAL, Color(0xFFFAF7FD), Color(0xFF1D1B20), Color(0xFFFFFFFF), false)
    val paper = ThemePalette(AppThemeKey.PAPER, Color(0xFFFAF8F3), Color(0xFF1C1B1F), Color(0xFFFFFFFF), false)
    val white = ThemePalette(AppThemeKey.WHITE, Color(0xFFFFFFFF), Color(0xFF1C1B1F), Color(0xFFF4F4F5), false)
    val sepia = ThemePalette(AppThemeKey.SEPIA, Color(0xFFF4ECD8), Color(0xFF3B2F1E), Color(0xFFFBF6EA), false)
    val mint = ThemePalette(AppThemeKey.MINT, Color(0xFFEEF5F1), Color(0xFF1B2E25), Color(0xFFF7FBF9), false)
    val sky = ThemePalette(AppThemeKey.SKY, Color(0xFFEDF3F9), Color(0xFF152131), Color(0xFFF6F9FC), false)
    val rose = ThemePalette(AppThemeKey.ROSE, Color(0xFFF9EFF1), Color(0xFF2E1C21), Color(0xFFFDF6F7), false)

    /** Material You dark: a warm near-black with the same violet cast as the light theme. */
    val midnight = ThemePalette(AppThemeKey.MIDNIGHT, Color(0xFF131016), Color(0xFFE7E0EA), Color(0xFF1D1A22), true)
    val ink = ThemePalette(AppThemeKey.INK, Color(0xFF121212), Color(0xFFE6E1D8), Color(0xFF1E1E1E), true)
    val slate = ThemePalette(AppThemeKey.SLATE, Color(0xFF1A1D24), Color(0xFFDDE1E8), Color(0xFF242832), true)
    val forest = ThemePalette(AppThemeKey.FOREST, Color(0xFF0F1A15), Color(0xFFD6E4DA), Color(0xFF182420), true)
    val black = ThemePalette(AppThemeKey.BLACK, Color(0xFF000000), Color(0xFFE6E1D8), Color(0xFF121212), true)

    val all = listOf(material, paper, white, sepia, mint, sky, rose, midnight, ink, slate, forest, black)

    fun resolve(key: AppThemeKey, systemDark: Boolean): ThemePalette = when (key) {
        AppThemeKey.SYSTEM -> if (systemDark) midnight else material
        else -> all.firstOrNull { it.key == key } ?: if (systemDark) midnight else material
    }
}

data class AccentPreset(val name: String, val color: Color)

object Accents {
    /** Material You primary. Default accent when no wallpaper colours are available. */
    val purple = Color(0xFF6750A4)
    val teal = Color(0xFF2A7F7F)
    val presets = listOf(
        AccentPreset("Purple", purple),
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

/**
 * The twelve note / category tints of the redesign. The first ten are the Material You
 * "category colours" from the spec, in order; the last two round the picker out to a 6 x 2 grid.
 */
object NoteBackgrounds {
    val pastels: List<Color> = listOf(
        Color(0xFFE8DEF8), // lavender
        Color(0xFFD9E7DB), // sage
        Color(0xFFFFDCC2), // peach
        Color(0xFFD6E4F7), // sky
        Color(0xFFF7D8E3), // pink
        Color(0xFFF5EDD8), // sand
        Color(0xFFDCE7E6), // mist
        Color(0xFFEADDF5), // lilac
        Color(0xFFFFE0E0), // blush
        Color(0xFFE2E6D8), // olive
        Color(0xFFFFF1C9), // butter
        Color(0xFFE6E0E9), // stone
    )

    /** Display names for the ten spec colours, used by the colour picker. */
    val names: List<String> = listOf(
        "Lavender", "Sage", "Peach", "Sky", "Pink", "Sand",
        "Mist", "Lilac", "Blush", "Olive", "Butter", "Stone",
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

    /**
     * Dark-theme counterpart of a pastel. The old version simply scaled the RGB down, which
     * turned every tint into the same muddy grey; this keeps the hue, keeps enough chroma for
     * the card to stay recognisable, and pins the lightness where white body text is readable.
     */
    fun forDark(c: Color): Color {
        val (h, s, _) = c.toHsl()
        if (s < 0.06f) return Color(0xFF232027) // neutrals become the dark surface tone
        return hsl(h, (s * 1.1f).coerceIn(0.26f, 0.55f), 0.28f)
    }

    private fun Color.toHsl(): Triple<Float, Float, Float> {
        val max = maxOf(red, green, blue)
        val min = minOf(red, green, blue)
        val l = (max + min) / 2f
        val d = max - min
        if (d < 1e-4f) return Triple(0f, 0f, l)
        val s = d / (1f - abs(2f * l - 1f)).coerceAtLeast(1e-4f)
        val h = when (max) {
            red -> (green - blue) / d + if (green < blue) 6f else 0f
            green -> (blue - red) / d + 2f
            else -> (red - green) / d + 4f
        } * 60f
        return Triple(h, s.coerceIn(0f, 1f), l)
    }

    private fun hsl(h: Float, s: Float, l: Float): Color {
        val c = (1f - abs(2f * l - 1f)) * s
        val hp = (((h % 360f) + 360f) % 360f) / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val rgb = when (hp.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = l - c / 2f
        return Color(
            (rgb.first + m).coerceIn(0f, 1f),
            (rgb.second + m).coerceIn(0f, 1f),
            (rgb.third + m).coerceIn(0f, 1f),
            1f,
        )
    }
}
