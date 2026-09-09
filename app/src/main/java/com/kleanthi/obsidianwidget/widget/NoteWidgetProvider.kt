package com.kleanthi.obsidianwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.RemoteViews
import com.kleanthi.obsidianwidget.R
import com.kleanthi.obsidianwidget.config.WidgetReconfigActivity
import com.kleanthi.obsidianwidget.core.BlockType
import com.kleanthi.obsidianwidget.core.MarkdownParser
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
        const val ACT_FOLD_ALL = "fold_all"
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
            views.setTextColor(R.id.btn_edit, palette.accent)
            views.setTextColor(R.id.empty_view, palette.muted)

            val content = notePath?.let { repo.readNote(it) }
            val blocks = content?.let { MarkdownParser.parse(it) } ?: emptyList()

            val titleName = notePath?.substringAfterLast('/')?.removeSuffix(".md")
                ?: if (prefNote == WidgetPrefs.DAILY) "Daily note" else "Obsidian Widget"
            val title = SpannableStringBuilder(titleName)
            if (WidgetPrefs.getShowCount(context, appWidgetId) && blocks.isNotEmpty()) {
                val open = blocks.count {
                    it.type == BlockType.TASK && !it.checked && it.state != '-'
                }
                val start = title.length
                title.append("  ($open)")
                title.setSpan(ForegroundColorSpan(palette.faint),
                    start, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            views.setTextViewText(R.id.widget_title, title)

            // ⊖/⊕ collapses or expands every heading section at once.
            if (blocks.any { it.type == BlockType.HEADING }) {
                views.setViewVisibility(R.id.btn_fold_all, View.VISIBLE)
                val anyCollapsed =
                    WidgetPrefs.getCollapsedHeadings(context, appWidgetId).isNotEmpty()
                views.setTextViewText(R.id.btn_fold_all, if (anyCollapsed) "⊕" else "⊖")
                views.setContentDescription(
                    R.id.btn_fold_all,
                    context.getString(
                        if (anyCollapsed) R.string.widget_expand_all else R.string.widget_collapse_all
                    )
                )
                views.setTextColor(R.id.btn_fold_all, palette.accent)
                val foldAllIntent = Intent(context, WidgetActionActivity::class.java)
                    .putExtra(EXTRA_WIDGET_ID, appWidgetId)
                    .putExtra(EXTRA_ACTION, ACT_FOLD_ALL)
                    .setData(Uri.parse("obsidianwidget://foldall/$appWidgetId"))
                views.setOnClickPendingIntent(
                    R.id.btn_fold_all,
                    PendingIntent.getActivity(
                        context, appWidgetId * 8 + 4, foldAllIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            } else {
                views.setViewVisibility(R.id.btn_fold_all, View.GONE)
            }

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
                    context, appWidgetId * 8, rowIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            )

            // ✎ opens this note in the Obsidian app.
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
                        context, appWidgetId * 8 + 1, deepLink,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }

            // ⚙ opens widget settings (theme + note choice).
            val settingsIntent = Intent(context, WidgetReconfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .setData(Uri.parse("obsidianwidget://config/$appWidgetId"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            views.setOnClickPendingIntent(
                R.id.btn_settings,
                PendingIntent.getActivity(
                    context, appWidgetId * 8 + 2, settingsIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            // Empty state: name the problem and send the tap to the fix.
            // The common path (note read fine) skips the vault checks entirely.
            val noteName = notePath?.substringAfterLast('/')?.removeSuffix(".md")
            val emptyText: String
            val emptyTarget: Intent
            when {
                content != null -> {
                    emptyText = context.getString(R.string.widget_empty_blank)
                    emptyTarget = Intent(context, WidgetActionActivity::class.java)
                        .putExtra(EXTRA_WIDGET_ID, appWidgetId)
                        .putExtra(EXTRA_ACTION, ACT_EDIT)
                        .setData(Uri.parse("obsidianwidget://empty/$appWidgetId"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                repo.vaultUri == null || !repo.isValidVault() -> {
                    emptyText = context.getString(R.string.widget_empty_no_vault)
                    emptyTarget = Intent(context, com.kleanthi.obsidianwidget.setup.SetupActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                prefNote == null -> {
                    emptyText = context.getString(R.string.widget_empty_no_note)
                    emptyTarget = settingsIntent
                }
                notePath == null -> {
                    emptyText = context.getString(R.string.widget_empty_no_daily, WidgetPrefs.todayName())
                    emptyTarget = settingsIntent
                }
                else -> {
                    emptyText = context.getString(R.string.widget_empty_unreadable, noteName)
                    emptyTarget = settingsIntent
                }
            }
            views.setTextViewText(R.id.empty_view, emptyText)
            views.setOnClickPendingIntent(
                R.id.empty_view,
                PendingIntent.getActivity(
                    context, appWidgetId * 8 + 3, emptyTarget,
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
