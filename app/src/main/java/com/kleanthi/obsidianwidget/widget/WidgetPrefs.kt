package com.kleanthi.obsidianwidget.widget

import android.content.Context

object WidgetPrefs {
    private fun prefs(context: Context) =
        context.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun setNote(context: Context, appWidgetId: Int, relPath: String) =
        prefs(context).edit().putString("note_$appWidgetId", relPath).apply()

    fun getNote(context: Context, appWidgetId: Int): String? =
        prefs(context).getString("note_$appWidgetId", null)

    fun remove(context: Context, appWidgetId: Int) =
        prefs(context).edit().remove("note_$appWidgetId").apply()
}
