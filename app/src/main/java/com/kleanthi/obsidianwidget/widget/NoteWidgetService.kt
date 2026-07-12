package com.kleanthi.obsidianwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
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

    private data class Row(val block: Block, val foldable: Boolean, val expanded: Boolean, val hidden: Int)

    private var rows: List<Row> = emptyList()
    private var palette: Palette = Themes.DARK
    private var fontSize: FontSize = FontSize.MEDIUM
    private var hideDone: Boolean = false

    private val childTypes = setOf(BlockType.TASK, BlockType.BULLET, BlockType.PARAGRAPH, BlockType.QUOTE)

    override fun onCreate() {}

    override fun onDataSetChanged() {
        palette = Themes.forWidget(context, appWidgetId)
        fontSize = WidgetPrefs.getFontSize(context, appWidgetId)
        hideDone = WidgetPrefs.getHideDone(context, appWidgetId)
        val repo = VaultRepository(context)
        val path = WidgetPrefs.resolveNote(context, appWidgetId, repo)
        val content = path?.let { repo.readNote(it) }
        val blocks = content?.let { MarkdownParser.parse(it) } ?: emptyList()
        rows = buildRows(blocks)
    }

    /** Tasks with indented children fold; folded children are skipped. */
    private fun buildRows(blocks: List<Block>): List<Row> {
        val out = mutableListOf<Row>()
        var i = 0
        while (i < blocks.size) {
            val b = blocks[i]
            if (b.type == BlockType.TASK) {
                var j = i + 1
                while (j < blocks.size && blocks[j].type in childTypes && blocks[j].indent > b.indent) j++
                // Hidden completed tasks take their indented children with them.
                if (hideDone && (b.checked || b.state == '-')) { i = j; continue }
                val kids = j - i - 1
                val expanded = kids > 0 && WidgetPrefs.isExpanded(context, appWidgetId, b.text)
                out.add(Row(b, kids > 0, expanded, kids))
                i = if (kids > 0 && !expanded) j else i + 1
            } else {
                out.add(Row(b, false, false, 0))
                i++
            }
        }
        return out
    }

    private fun glyph(state: Char): String = when (state) {
        ' ' -> "☐"
        'x', 'X' -> "☑"
        '/' -> "◧"          // in progress
        '-' -> "⊟"          // cancelled
        '>' -> "➤"          // forwarded/scheduled
        else -> "[$state]"  // any other custom state, shown as-is
    }

    override fun getCount() = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val row = rows[position]
        val b = row.block
        val density = context.resources.displayMetrics.density
        val rv: RemoteViews
        if (b.type == BlockType.TASK) {
            rv = RemoteViews(context.packageName, R.layout.row_task)
            rv.setTextViewText(R.id.task_checkbox, glyph(b.state))
            rv.setTextColor(R.id.task_checkbox, palette.accent)
            rv.setTextViewTextSize(R.id.task_checkbox, TypedValue.COMPLEX_UNIT_SP, fontSize.glyph)
            rv.setTextViewTextSize(R.id.task_text, TypedValue.COMPLEX_UNIT_SP, fontSize.body)

            val body = SpanMapper.toSpannable(InlineStyler.style(b.text), palette, strike = b.checked)
            val text: CharSequence = if (row.foldable) {
                val suffix = if (row.expanded) "  ▾" else "  ▸${row.hidden}"
                SpannableStringBuilder(body).append(suffix).apply {
                    setSpan(ForegroundColorSpan(palette.faint),
                        length - suffix.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            } else body
            rv.setTextViewText(R.id.task_text, text)
            rv.setTextColor(R.id.task_text, if (b.checked) palette.muted else palette.text)
            // setViewPadding sets all four sides — keep the XML's 4dp vertical padding.
            val pad = (4 * density).toInt()
            rv.setViewPadding(R.id.task_row, (b.indent * 16 * density).toInt(), pad, 0, pad)

            // Checkbox toggles done; task text folds/unfolds its children.
            rv.setOnClickFillInIntent(R.id.task_checkbox, Intent().apply {
                putExtra(NoteWidgetProvider.EXTRA_WIDGET_ID, appWidgetId)
                putExtra(NoteWidgetProvider.EXTRA_ACTION, NoteWidgetProvider.ACT_TOGGLE)
                putExtra(NoteWidgetProvider.EXTRA_LINE, b.sourceLine)
                putExtra(NoteWidgetProvider.EXTRA_EXPECTED, b.text)
            })
            rv.setOnClickFillInIntent(R.id.task_text, Intent().apply {
                putExtra(NoteWidgetProvider.EXTRA_WIDGET_ID, appWidgetId)
                putExtra(NoteWidgetProvider.EXTRA_ACTION, NoteWidgetProvider.ACT_FOLD)
                putExtra(NoteWidgetProvider.EXTRA_EXPECTED, b.text)
            })
        } else {
            rv = RemoteViews(context.packageName, R.layout.row_text)
            rv.setTextViewText(R.id.block_text, SpanMapper.render(b, palette))
            rv.setTextColor(R.id.block_text, palette.text)
            rv.setTextViewTextSize(R.id.block_text, TypedValue.COMPLEX_UNIT_SP, fontSize.body)
            val pad = (3 * density).toInt()
            rv.setViewPadding(R.id.block_text, (b.indent * 16 * density).toInt(), pad, 0, pad)
            rv.setOnClickFillInIntent(R.id.block_text, Intent().apply {
                putExtra(NoteWidgetProvider.EXTRA_WIDGET_ID, appWidgetId)
                putExtra(NoteWidgetProvider.EXTRA_ACTION, NoteWidgetProvider.ACT_EDIT)
            })
        }
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount() = 2
    override fun getItemId(position: Int) = position.toLong()
    override fun hasStableIds() = false
    override fun onDestroy() {}
}
