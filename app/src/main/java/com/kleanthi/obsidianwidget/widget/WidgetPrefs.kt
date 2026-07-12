package com.kleanthi.obsidianwidget.widget

import android.content.Context

object WidgetPrefs {
    private fun prefs(context: Context) =
        context.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun setNote(context: Context, appWidgetId: Int, relPath: String) =
        prefs(context).edit().putString("note_$appWidgetId", relPath).apply()

    fun getNote(context: Context, appWidgetId: Int): String? =
        prefs(context).getString("note_$appWidgetId", null)

    fun setTheme(context: Context, appWidgetId: Int, mode: ThemeMode) =
        prefs(context).edit().putString("theme_$appWidgetId", mode.name).apply()

    fun getTheme(context: Context, appWidgetId: Int): ThemeMode =
        prefs(context).getString("theme_$appWidgetId", null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM

    fun remove(context: Context, appWidgetId: Int) =
        prefs(context).edit()
            .remove("note_$appWidgetId")
            .remove("expanded_$appWidgetId")
            .remove("theme_$appWidgetId")
            .apply()

    // Fold state: tasks are folded by default; this stores the expanded ones,
    // keyed by task text so the state survives line-number shifts.
    fun isExpanded(context: Context, appWidgetId: Int, key: String): Boolean =
        prefs(context).getStringSet("expanded_$appWidgetId", emptySet())!!.contains(key)

    fun toggleExpanded(context: Context, appWidgetId: Int, key: String) {
        val cur = HashSet(prefs(context).getStringSet("expanded_$appWidgetId", emptySet())!!)
        if (!cur.add(key)) cur.remove(key)
        prefs(context).edit().putStringSet("expanded_$appWidgetId", cur).apply()
    }
}
