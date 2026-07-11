package com.kleanthi.obsidianwidget.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

object WidgetRefresher {
    fun refreshAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, NoteWidgetProvider::class.java))
        ids.forEach { NoteWidgetProvider.updateWidget(context, mgr, it) }
    }
}
