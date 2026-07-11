package com.kleanthi.obsidianwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.kleanthi.obsidianwidget.R
import com.kleanthi.obsidianwidget.core.Block
import com.kleanthi.obsidianwidget.core.BlockType
import com.kleanthi.obsidianwidget.core.InlineStyler
import com.kleanthi.obsidianwidget.core.MarkdownParser
import com.kleanthi.obsidianwidget.vault.VaultRepository

class NoteWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        NoteRemoteViewsFactory(
            applicationContext,
            intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        )
}

class NoteRemoteViewsFactory(
    private val context: Context,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private var blocks: List<Block> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val path = WidgetPrefs.getNote(context, appWidgetId)
        val content = path?.let { VaultRepository(context).readNote(it) }
        blocks = content?.let { MarkdownParser.parse(it) } ?: emptyList()
    }

    override fun getCount() = blocks.size

    override fun getViewAt(position: Int): RemoteViews {
        val b = blocks[position]
        val density = context.resources.displayMetrics.density
        val rv: RemoteViews
        if (b.type == BlockType.TASK) {
            rv = RemoteViews(context.packageName, R.layout.row_task)
            rv.setTextViewText(R.id.task_checkbox, if (b.checked) "☑" else "☐")
            rv.setTextViewText(
                R.id.task_text,
                SpanMapper.toSpannable(InlineStyler.style(b.text), strike = b.checked)
            )
            rv.setViewPadding(R.id.task_row, (b.indent * 16 * density).toInt(), 0, 0, 0)
        } else {
            rv = RemoteViews(context.packageName, R.layout.row_text)
            rv.setTextViewText(R.id.block_text, SpanMapper.render(b))
        }
        // Fill-in intents completing the provider's pending-intent template.
        val fillIn = Intent().apply {
            putExtra(NoteWidgetProvider.EXTRA_WIDGET_ID, appWidgetId)
            putExtra(NoteWidgetProvider.EXTRA_IS_TASK, b.type == BlockType.TASK)
            putExtra(NoteWidgetProvider.EXTRA_LINE, b.sourceLine)
            putExtra(NoteWidgetProvider.EXTRA_EXPECTED, b.text)
        }
        rv.setOnClickFillInIntent(
            if (b.type == BlockType.TASK) R.id.task_row else R.id.block_text, fillIn
        )
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount() = 2
    override fun getItemId(position: Int) = position.toLong()
    override fun hasStableIds() = false
    override fun onDestroy() {}
}
