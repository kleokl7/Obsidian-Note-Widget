package com.kleanthi.obsidianwidget.widget

import android.content.Context
import com.kleanthi.obsidianwidget.vault.VaultRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WidgetPrefs {
    /** Note pref marker: widget follows today's daily note (YYYY-MM-DD.md). */
    const val DAILY = "::daily::"

    private fun prefs(context: Context) =
        context.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun setNote(context: Context, appWidgetId: Int, relPath: String) =
        prefs(context).edit().putString("note_$appWidgetId", relPath).apply()

    fun getNote(context: Context, appWidgetId: Int): String? =
        prefs(context).getString("note_$appWidgetId", null)

    /**
     * The actual path to render: the configured note, or today's daily note
     * (file named YYYY-MM-DD.md anywhere in the vault). The daily lookup scans
     * the vault at most once per day per widget; the result is cached here.
     */
    fun resolveNote(context: Context, appWidgetId: Int, repo: VaultRepository): String? {
        val pref = getNote(context, appWidgetId) ?: return null
        if (pref != DAILY) return pref
        val today = todayName()
        val p = prefs(context)
        if (p.getString("daily_date_$appWidgetId", null) == today) {
            p.getString("daily_path_$appWidgetId", null)?.let { return it }
        }
        val found = repo.listNotes()
            .firstOrNull { it.relPath.substringAfterLast('/') == "$today.md" }
            ?.relPath ?: return null
        p.edit().putString("daily_path_$appWidgetId", found)
            .putString("daily_date_$appWidgetId", today).apply()
        return found
    }

    /** Today's daily-note file name without the extension (YYYY-MM-DD). */
    fun todayName(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun setTheme(context: Context, appWidgetId: Int, mode: ThemeMode) =
        prefs(context).edit().putString("theme_$appWidgetId", mode.name).apply()

    fun getTheme(context: Context, appWidgetId: Int): ThemeMode =
        prefs(context).getString("theme_$appWidgetId", null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM

    fun setOpacity(context: Context, appWidgetId: Int, percent: Int) =
        prefs(context).edit().putInt("opacity_$appWidgetId", percent.coerceIn(10, 100)).apply()

    fun getOpacity(context: Context, appWidgetId: Int): Int =
        prefs(context).getInt("opacity_$appWidgetId", 100)

    fun setFontSize(context: Context, appWidgetId: Int, size: FontSize) =
        prefs(context).edit().putString("font_$appWidgetId", size.name).apply()

    fun getFontSize(context: Context, appWidgetId: Int): FontSize =
        prefs(context).getString("font_$appWidgetId", null)
            ?.let { runCatching { FontSize.valueOf(it) }.getOrNull() }
            ?: FontSize.MEDIUM

    fun setHideDone(context: Context, appWidgetId: Int, hide: Boolean) =
        prefs(context).edit().putBoolean("hidedone_$appWidgetId", hide).apply()

    fun getHideDone(context: Context, appWidgetId: Int): Boolean =
        prefs(context).getBoolean("hidedone_$appWidgetId", false)

    fun remove(context: Context, appWidgetId: Int) =
        prefs(context).edit()
            .remove("note_$appWidgetId")
            .remove("expanded_$appWidgetId")
            .remove("collapsedheads_$appWidgetId")
            .remove("theme_$appWidgetId")
            .remove("opacity_$appWidgetId")
            .remove("font_$appWidgetId")
            .remove("hidedone_$appWidgetId")
            .remove("showcount_$appWidgetId")
            .remove("daily_date_$appWidgetId")
            .remove("daily_path_$appWidgetId")
            .apply()

    // Fold state: tasks are folded by default; this stores the expanded ones,
    // keyed by task text so the state survives line-number shifts.
    fun getExpanded(context: Context, appWidgetId: Int): Set<String> =
        prefs(context).getStringSet("expanded_$appWidgetId", emptySet())!!

    fun toggleExpanded(context: Context, appWidgetId: Int, key: String) {
        val cur = HashSet(getExpanded(context, appWidgetId))
        if (!cur.add(key)) cur.remove(key)
        prefs(context).edit().putStringSet("expanded_$appWidgetId", cur).apply()
    }

    // Heading fold state: headings are expanded by default; this stores the
    // collapsed ones, keyed by Outline.headingKeys ("Today#1").
    fun toggleHeadingCollapsed(context: Context, appWidgetId: Int, key: String) {
        val cur = HashSet(getCollapsedHeadings(context, appWidgetId))
        if (!cur.add(key)) cur.remove(key)
        setCollapsedHeadings(context, appWidgetId, cur)
    }

    fun getCollapsedHeadings(context: Context, appWidgetId: Int): Set<String> =
        prefs(context).getStringSet("collapsedheads_$appWidgetId", emptySet())!!

    fun setCollapsedHeadings(context: Context, appWidgetId: Int, keys: Set<String>) =
        prefs(context).edit().putStringSet("collapsedheads_$appWidgetId", keys).apply()

    fun setShowCount(context: Context, appWidgetId: Int, show: Boolean) =
        prefs(context).edit().putBoolean("showcount_$appWidgetId", show).apply()

    fun getShowCount(context: Context, appWidgetId: Int): Boolean =
        prefs(context).getBoolean("showcount_$appWidgetId", false)
}
