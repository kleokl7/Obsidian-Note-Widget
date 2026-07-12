package com.kleanthi.obsidianwidget.widget

import android.content.Context
import android.content.res.Configuration
import com.kleanthi.obsidianwidget.R

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Body text size with a matching checkbox-glyph size. */
enum class FontSize(val body: Float, val glyph: Float) {
    SMALL(12f, 15f), MEDIUM(14f, 17f), LARGE(16f, 19f)
}

data class Palette(
    val bg: Int,
    val text: Int,
    val muted: Int,
    val faint: Int,
    val heading: Int,
    val accent: Int,
    val onAccent: Int,
    val link: Int,
    val highlightBg: Int,
    val codeText: Int,
    val codeBg: Int,
    val bgRes: Int,
)

/** Palettes from the Claude-Code-Obsidian-Theme: warm paper light, warm charcoal dark. */
object Themes {
    val LIGHT = Palette(
        bg = 0xFFF9F3E7.toInt(),
        text = 0xFF34322C.toInt(),
        muted = 0xFF6E6A5C.toInt(),
        faint = 0xFF9A9684.toInt(),
        heading = 0xFF1A1915.toInt(),
        accent = 0xFFC15F3C.toInt(),
        onAccent = 0xFFFFFAF5.toInt(),
        link = 0xFFB0532F.toInt(),
        highlightBg = 0x38C15F3C,
        codeText = 0xFF52483A.toInt(),
        codeBg = 0xFFE8DFCC.toInt(),
        bgRes = R.drawable.widget_bg_light,
    )
    val DARK = Palette(
        bg = 0xFF262624.toInt(),
        text = 0xFFE6E4DC.toInt(),
        muted = 0xFFA8A496.toInt(),
        faint = 0xFF767264.toInt(),
        heading = 0xFFF6F4EE.toInt(),
        accent = 0xFFD97757.toInt(),
        onAccent = 0xFF1A1915.toInt(),
        link = 0xFFE8916F.toInt(),
        highlightBg = 0x47D97757,
        codeText = 0xFFD8CBB3.toInt(),
        codeBg = 0xFF1F1B15.toInt(),
        bgRes = R.drawable.widget_bg_dark,
    )

    fun isSystemDark(context: Context): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    fun resolve(context: Context, mode: ThemeMode): Palette = when (mode) {
        ThemeMode.LIGHT -> LIGHT
        ThemeMode.DARK -> DARK
        ThemeMode.SYSTEM -> if (isSystemDark(context)) DARK else LIGHT
    }

    fun forWidget(context: Context, appWidgetId: Int): Palette =
        resolve(context, WidgetPrefs.getTheme(context, appWidgetId))
}
