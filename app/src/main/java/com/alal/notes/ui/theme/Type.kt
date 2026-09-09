package com.alal.notes.ui.theme

import android.content.Context
import android.content.res.Resources
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Fonts are bundled at build time by `scripts/fetch-fonts.sh` into res/font. Because the
 * files might be absent (offline build), we resolve them by name at runtime and fall back
 * to system families, so the app compiles and runs either way.
 */
object Fonts {
    data class Option(val key: String, val label: String, val regular: String, val bold: String? = null, val fallback: FontFamily)

    val latin = listOf(
        Option("inter", "Inter", "inter", fallback = FontFamily.SansSerif),
        Option("literata", "Literata", "literata", fallback = FontFamily.Serif),
        Option("system", "System", "", fallback = FontFamily.Default),
        Option("serif", "System serif", "", fallback = FontFamily.Serif),
    )
    val myanmar = listOf(
        Option("pyidaungsu", "Pyidaungsu", "pyidaungsu_regular", "pyidaungsu_bold", FontFamily.Default),
        Option("notosansmyanmar", "Noto Sans Myanmar", "notosansmyanmar_regular", "notosansmyanmar_bold", FontFamily.SansSerif),
        Option("system", "System", "", fallback = FontFamily.Default),
    )

    private val cache = HashMap<String, FontFamily>()

    fun fontId(res: Resources, pkg: String, name: String): Int =
        if (name.isEmpty()) 0 else res.getIdentifier(name, "font", pkg)

    fun family(context: Context, key: String): FontFamily {
        cache[key]?.let { return it }
        val option = (latin + myanmar).firstOrNull { it.key == key } ?: latin.first()
        val res = context.resources
        val pkg = context.packageName
        val regular = fontId(res, pkg, option.regular)
        val family = if (regular == 0) option.fallback else {
            val bold = option.bold?.let { fontId(res, pkg, it) } ?: 0
            if (bold != 0) FontFamily(Font(regular, FontWeight.Normal), Font(bold, FontWeight.Bold))
            else FontFamily(Font(regular, FontWeight.Normal))
        }
        cache[key] = family
        return family
    }

    fun isBundled(context: Context, key: String): Boolean {
        val option = (latin + myanmar).firstOrNull { it.key == key } ?: return false
        return option.regular.isEmpty() || fontId(context.resources, context.packageName, option.regular) != 0
    }
}

/** Editorial type ramp (§3.3). */
data class AlalType(
    val title: FontFamily,
    val body: FontFamily,
    val myanmar: FontFamily,
    val bodySize: Int,
    val lineHeight: Float,
)

@Composable
fun rememberFontFamily(key: String): FontFamily {
    val context = LocalContext.current
    return remember(key) { Fonts.family(context, key) }
}

fun buildTypography(title: FontFamily, body: FontFamily): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = title),
        displayMedium = base.displayMedium.copy(fontFamily = title),
        displaySmall = base.displaySmall.copy(fontFamily = title),
        headlineLarge = base.headlineLarge.copy(fontFamily = title),
        headlineMedium = base.headlineMedium.copy(fontFamily = title, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontFamily = title, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = body),
        titleSmall = base.titleSmall.copy(fontFamily = body),
        bodyLarge = base.bodyLarge.copy(fontFamily = body, fontSize = 18.sp, lineHeight = 28.8.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = body, fontSize = 15.sp, lineHeight = 22.sp),
        bodySmall = base.bodySmall.copy(fontFamily = body, fontSize = 12.sp),
        labelLarge = base.labelLarge.copy(fontFamily = body),
        labelMedium = base.labelMedium.copy(fontFamily = body),
        labelSmall = base.labelSmall.copy(fontFamily = body, fontSize = 12.sp),
    )
}

val MetaTextStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
