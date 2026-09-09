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

    private data class Row(
        val block: Block,
        val foldable: Boolean,
        val expanded: Boolean,
        val hidden: Int,
        val openTasks: Int = -1,      // headings: open tasks in section (-1 = no tasks at all)
        val headKey: String? = null,  // headings: fold-state key ("text#occurrence")
    )

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

    /**
     * Tasks with indented children fold (folded children are skipped), and
     * headings fold their whole section — everything up to the next heading
     * of the same or higher level.
     */
    private fun buildRows(blocks: List<Block>): List<Row> {
        val out = mutableListOf<Row>()
        val headSeen = mutableMapOf<String, Int>()
        var i = 0
        while (i < blocks.size) {
            val b = blocks[i]
            if (b.type == BlockType.HEADING) {
                var j = i + 1
                while (j < blocks.size &&
                    !(blocks[j].type == BlockType.HEADING && blocks[j].level <= b.level)) j++
                var tasks = 0
                var open = 0
                for (k in i + 1 until j) {
                    val t = blocks[k]
                    if (t.type == BlockType.TASK) {
                        tasks++
                        if (!t.checked && t.state != '-') open++
                    }
                }
                val n = headSeen.merge(b.text, 1, Int::plus)!!
                val key = "${b.text}#$n"
                val collapsed = WidgetPrefs.isHeadingCollapsed(context, appWidgetId, key)
                out.add(Row(b, foldable = true, expanded = !collapsed, hidden = 0,
                    openTasks = if (tasks > 0) open else -1, headKey = key))
                i = if (collapsed) j else i + 1
            } else if (b.type == BlockType.TASK) {
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

    private fun glyph(state: Char): String = SpanMapper.taskGlyph(state)

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
            rv.setContentDescription(
                R.id.task_checkbox,
                context.getString(if (b.checked) R.string.widget_task_done else R.string.widget_task_open)
            )
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
            val body: CharSequence = if (b.type == BlockType.HEADING) {
                SpannableStringBuilder(SpanMapper.render(b, palette)).apply {
                    val decor = StringBuilder()
                    if (row.openTasks >= 0) decor.append("  (${row.openTasks})")
                    if (!row.expanded) decor.append("  ▸")
                    if (decor.isNotEmpty()) {
                        val start = length
                        append(decor)
                        setSpan(ForegroundColorSpan(palette.faint),
                            start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            } else SpanMapper.render(b, palette)
            rv.setTextViewText(R.id.block_text, body)
            rv.setTextColor(R.id.block_text, palette.text)
            rv.setTextViewTextSize(R.id.block_text, TypedValue.COMPLEX_UNIT_SP, fontSize.body)
            val pad = (3 * density).toInt()
            // Headings open a section: extra air above them (not for the first row)
            // gives the note its rhythm without divider lines.
            val topPad = if (b.type == BlockType.HEADING && position > 0) (10 * density).toInt() else pad
            rv.setViewPadding(R.id.block_text, (b.indent * 16 * density).toInt(), topPad, 0, pad)

            // Headings fold their section; other rows open the popup editor.
            rv.setOnClickFillInIntent(R.id.block_text, Intent().apply {
                putExtra(NoteWidgetProvider.EXTRA_WIDGET_ID, appWidgetId)
                if (b.type == BlockType.HEADING) {
                    putExtra(NoteWidgetProvider.EXTRA_ACTION, NoteWidgetProvider.ACT_FOLD_HEAD)
                    putExtra(NoteWidgetProvider.EXTRA_EXPECTED, row.headKey)
                } else {
                    putExtra(NoteWidgetProvider.EXTRA_ACTION, NoteWidgetProvider.ACT_EDIT)
                }
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
