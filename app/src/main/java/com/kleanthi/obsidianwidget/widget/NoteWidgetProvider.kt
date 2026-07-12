package com.kleanthi.obsidianwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.kleanthi.obsidianwidget.R
import com.kleanthi.obsidianwidget.config.WidgetConfigActivity
import com.kleanthi.obsidianwidget.vault.VaultRepository

class NoteWidgetProvider : AppWidgetProvider() {

    companion object {
        const val EXTRA_WIDGET_ID = "widget_id"
        const val EXTRA_ACTION = "row_action"
        const val EXTRA_LINE = "line"
        const val EXTRA_EXPECTED = "expected"
        const val ACT_TOGGLE = "toggle"
        const val ACT_FOLD = "fold"
        const val ACT_FOLD_HEAD = "fold_head"
        const val ACT_EDIT = "edit"

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val repo = VaultRepository(context)
            val prefNote = WidgetPrefs.getNote(context, appWidgetId)
            val notePath = WidgetPrefs.resolveNote(context, appWidgetId, repo)
            val views = RemoteViews(context.packageName, R.layout.widget_note)

            val palette = Themes.forWidget(context, appWidgetId)
            views.setImageViewResource(R.id.widget_bg_img, palette.bgRes)
            views.setInt(
                R.id.widget_bg_img, "setImageAlpha",
                WidgetPrefs.getOpacity(context, appWidgetId) * 255 / 100
            )
            views.setTextColor(R.id.widget_title, palette.heading)
            views.setTextColor(R.id.btn_settings, palette.accent)
            views.setTextColor(R.id.empty_view, palette.muted)

            views.setTextViewText(
                R.id.widget_title,
                notePath?.substringAfterLast('/')?.removeSuffix(".md")
                    ?: if (prefNote == WidgetPrefs.DAILY) "Daily note" else "Obsidian Widget"
            )

            val svcIntent = Intent(context, NoteWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.block_list, svcIntent)
            views.setEmptyView(R.id.block_list, R.id.empty_view)

            // Row-tap template: an invisible trampoline activity (rows fill in the
            // action). Request codes are spaced so no two pending intents collide.
            val rowIntent = Intent(context, WidgetActionActivity::class.java)
                .setData(Uri.parse("obsidianwidget://row/$appWidgetId"))
            views.setPendingIntentTemplate(
                R.id.block_list,
                PendingIntent.getActivity(
                    context, appWidgetId * 4, rowIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            )

            // ✏️ opens this note in the Obsidian app.
            val vaultName = repo.vaultName
            if (vaultName != null && notePath != null) {
                val deepLink = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(
                        "obsidian://open?vault=" + Uri.encode(vaultName) +
                            "&file=" + Uri.encode(notePath.removeSuffix(".md"))
                    )
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                views.setOnClickPendingIntent(
                    R.id.btn_edit,
                    PendingIntent.getActivity(
                        context, appWidgetId * 4 + 1, deepLink,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }

            // ⚙ opens widget settings (theme + note choice).
            val settingsIntent = Intent(context, WidgetConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .setData(Uri.parse("obsidianwidget://config/$appWidgetId"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            views.setOnClickPendingIntent(
                R.id.btn_settings,
                PendingIntent.getActivity(
                    context, appWidgetId * 4 + 2, settingsIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            // Tapping the empty state opens the app.
            views.setOnClickPendingIntent(
                R.id.empty_view,
                PendingIntent.getActivity(
                    context, appWidgetId * 4 + 3,
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

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPrefs.remove(context, it) }
    }

    override fun onEnabled(context: Context) = RefreshScheduler.schedule(context)
    override fun onDisabled(context: Context) = RefreshScheduler.cancel(context)
}
