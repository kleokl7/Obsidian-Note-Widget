package com.kleanthi.obsidianwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.kleanthi.obsidianwidget.R
import com.kleanthi.obsidianwidget.core.TaskToggler
import com.kleanthi.obsidianwidget.core.ToggleResult
import com.kleanthi.obsidianwidget.editor.EditorActivity
import com.kleanthi.obsidianwidget.vault.VaultRepository

class NoteWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_ROW_CLICK = "com.kleanthi.obsidianwidget.ROW_CLICK"
        const val EXTRA_WIDGET_ID = "widget_id"
        const val EXTRA_LINE = "line"
        const val EXTRA_EXPECTED = "expected"
        const val EXTRA_IS_TASK = "is_task"

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val notePath = WidgetPrefs.getNote(context, appWidgetId)
            val views = RemoteViews(context.packageName, R.layout.widget_note)

            views.setTextViewText(
                R.id.widget_title,
                notePath?.substringAfterLast('/')?.removeSuffix(".md") ?: "Obsidian Widget"
            )

            val svcIntent = Intent(context, NoteWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.block_list, svcIntent)
            views.setEmptyView(R.id.block_list, R.id.empty_view)

            // Row-click template; rows complete it with fill-in extras.
            val rowIntent = Intent(context, NoteWidgetProvider::class.java)
                .setAction(ACTION_ROW_CLICK)
                .setData(Uri.parse("obsidianwidget://row/$appWidgetId"))
            views.setPendingIntentTemplate(
                R.id.block_list,
                PendingIntent.getBroadcast(
                    context, appWidgetId, rowIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            )

            // ✏️ opens the editor.
            val editIntent = Intent(context, EditorActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .setData(Uri.parse("obsidianwidget://edit/$appWidgetId"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            views.setOnClickPendingIntent(
                R.id.btn_edit,
                PendingIntent.getActivity(
                    context, appWidgetId, editIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            // ◆ deep-links into Obsidian at this note.
            val vaultName = VaultRepository(context).vaultName
            if (vaultName != null && notePath != null) {
                val deepLink = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(
                        "obsidian://open?vault=" + Uri.encode(vaultName) +
                            "&file=" + Uri.encode(notePath.removeSuffix(".md"))
                    )
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                views.setOnClickPendingIntent(
                    R.id.btn_obsidian,
                    PendingIntent.getActivity(
                        context, appWidgetId, deepLink,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }

            // Tapping the empty state opens the app.
            views.setOnClickPendingIntent(
                R.id.empty_view,
                PendingIntent.getActivity(
                    context, appWidgetId,
                    Intent(context, com.kleanthi.obsidianwidget.setup.SetupActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.block_list)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_ROW_CLICK) return
        val widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        if (intent.getBooleanExtra(EXTRA_IS_TASK, false)) {
            val line = intent.getIntExtra(EXTRA_LINE, -1)
            val expected = intent.getStringExtra(EXTRA_EXPECTED) ?: return
            val repo = VaultRepository(context)
            val path = WidgetPrefs.getNote(context, widgetId) ?: return
            val content = repo.readNote(path) ?: return
            when (val r = TaskToggler.toggle(content, line, expected)) {
                is ToggleResult.Success -> repo.writeNote(path, r.newContent)
                ToggleResult.LineMismatch -> { /* stale view — refresh below fixes it */ }
            }
            updateWidget(context, AppWidgetManager.getInstance(context), widgetId)
        } else {
            context.startActivity(
                Intent(context, EditorActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPrefs.remove(context, it) }
    }

    override fun onEnabled(context: Context) = RefreshScheduler.schedule(context)
    override fun onDisabled(context: Context) = RefreshScheduler.cancel(context)
}
