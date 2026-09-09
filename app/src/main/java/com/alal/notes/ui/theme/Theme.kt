package com.alal.notes.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alal.notes.data.prefs.Settings
import com.alal.notes.domain.model.AccentMode
import com.alal.notes.domain.model.UiDensity

val AlalShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),   // chips
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),      // cards
    large = RoundedCornerShape(28.dp),       // sheets
    extraLarge = RoundedCornerShape(28.dp),
)

/** Extra per-app theme values not covered by Material. */
data class AlalExtras(
    val palette: ThemePalette,
    val type: AlalType,
    val density: UiDensity,
    val settings: Settings,
) {
    val dark: Boolean get() = palette.dark
    val spacing: Int get() = when (density) { UiDensity.COMPACT -> 8; UiDensity.DEFAULT -> 12; UiDensity.COMFORTABLE -> 16 }
}

val LocalAlalExtras = staticCompositionLocalOf<AlalExtras> { error("AlalTheme not applied") }

object Alal {
    val extras: AlalExtras @Composable get() = LocalAlalExtras.current
}

@Suppress("UNUSED_PARAMETER")
private fun Color.contrastOn(bg: Color): Color =
    if (this.luminance() > 0.5f) Color(0xFF1C1B1F) else Color.White

private fun blend(a: Color, b: Color, t: Float): Color = Color(
    a.red + (b.red - a.red) * t,
    a.green + (b.green - a.green) * t,
    a.blue + (b.blue - a.blue) * t,
    1f,
)

/** Builds a full Material 3 scheme from the palette + an accent seed. */
fun buildColorScheme(palette: ThemePalette, accent: Color): ColorScheme {
    val dark = palette.dark
    val bg = palette.background
    val text = palette.onBackground
    val surface = palette.surface
    val primary = if (dark) blend(accent, Color.White, 0.35f) else accent
    val onPrimary = primary.contrastOn(bg)
    val primaryContainer = if (dark) blend(accent, bg, 0.6f) else blend(accent, Color.White, 0.82f)
    val onPrimaryContainer = if (dark) blend(accent, Color.White, 0.75f) else blend(accent, Color.Black, 0.55f)
    val secondary = blend(text, accent, 0.4f)
    val secondaryContainer = if (dark) blend(surface, accent, 0.22f) else blend(surface, accent, 0.14f)
    val outline = text.copy(alpha = 0.32f).compositeOver(bg)
    val outlineVariant = text.copy(alpha = 0.12f).compositeOver(bg)
    val surfaceVariant = if (dark) blend(surface, Color.White, 0.06f) else blend(surface, text, 0.05f)
    val onSurfaceVariant = text.copy(alpha = 0.72f).compositeOver(surface)
    val error = if (dark) Color(0xFFFFB4AB) else Color(0xFFBA1A1A)

    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = if (dark) accent else blend(accent, Color.White, 0.5f),
        secondary = secondary,
        onSecondary = secondary.contrastOn(bg),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = text,
        tertiary = blend(accent, if (dark) Color(0xFFFFC0A0) else Color(0xFF9A4B2E), 0.5f),
        onTertiary = onPrimary,
        tertiaryContainer = blend(primaryContainer, Color(0xFFFFD8C0), 0.3f),
        onTertiaryContainer = onPrimaryContainer,
        background = bg,
        onBackground = text,
        surface = bg,
        onSurface = text,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        inverseSurface = text,
        inverseOnSurface = bg,
        outline = outline,
        outlineVariant = outlineVariant,
        error = error,
        onError = if (dark) Color(0xFF690005) else Color.White,
        errorContainer = if (dark) Color(0xFF93000A) else Color(0xFFFFDAD6),
        onErrorContainer = if (dark) Color(0xFFFFDAD6) else Color(0xFF410002),
        scrim = Color.Black,
        surfaceBright = if (dark) blend(surface, Color.White, 0.08f) else Color.White,
        surfaceDim = if (dark) bg else blend(bg, text, 0.08f),
        surfaceContainerLowest = if (dark) bg else Color.White,
        surfaceContainerLow = if (dark) blend(bg, surface, 0.5f) else blend(bg, surface, 0.5f),
        surfaceContainer = surface,
        surfaceContainerHigh = if (dark) blend(surface, Color.White, 0.04f) else blend(surface, text, 0.03f),
        surfaceContainerHighest = if (dark) blend(surface, Color.White, 0.08f) else blend(surface, text, 0.06f),
    )
}

@Composable
fun AlalTheme(settings: Settings, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val palette = Palettes.resolve(settings.theme, systemDark)
    val context = LocalContext.current

    val accent: Color = when (settings.accentMode) {
        AccentMode.DYNAMIC -> {
            if (settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val dyn = if (palette.dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                if (palette.dark) blend(dyn.primary, dyn.inversePrimary, 0.3f) else dyn.primary
            } else Accents.teal
        }
        AccentMode.PRESET, AccentMode.CUSTOM -> Color(settings.accentColor)
    }

    val scheme = buildColorScheme(palette, accent)
    val titleFamily = rememberFontFamily(settings.titleFont)
    val bodyFamily = rememberFontFamily(settings.bodyFont)
    val myanmarFamily = rememberFontFamily(settings.myanmarFont)
    val typography = buildTypography(titleFamily, bodyFamily)
    val extras = AlalExtras(
        palette = palette,
        type = AlalType(titleFamily, bodyFamily, myanmarFamily, settings.bodySize, settings.lineHeight),
        density = settings.uiDensity,
        settings = settings,
    )

    CompositionLocalProvider(LocalAlalExtras provides extras) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = AlalShapes, content = content)
    }
}
