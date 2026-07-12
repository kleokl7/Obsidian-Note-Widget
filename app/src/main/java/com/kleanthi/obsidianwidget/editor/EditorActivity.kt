package com.kleanthi.obsidianwidget.editor

import android.appwidget.AppWidgetManager
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.kleanthi.obsidianwidget.R
import com.kleanthi.obsidianwidget.core.BlockType
import com.kleanthi.obsidianwidget.core.InlineStyler
import com.kleanthi.obsidianwidget.core.MarkdownParser
import com.kleanthi.obsidianwidget.core.TaskToggler
import com.kleanthi.obsidianwidget.core.ToggleResult
import com.kleanthi.obsidianwidget.vault.VaultRepository
import com.kleanthi.obsidianwidget.widget.NoteWidgetProvider
import com.kleanthi.obsidianwidget.widget.Palette
import com.kleanthi.obsidianwidget.widget.SpanMapper
import com.kleanthi.obsidianwidget.widget.Themes
import com.kleanthi.obsidianwidget.widget.WidgetPrefs

/**
 * Popup note view/editor. Opens in view mode — rendered markdown with
 * tappable task checkboxes — and switches to raw-markdown editing on demand.
 */
class EditorActivity : AppCompatActivity() {
    private lateinit var repo: VaultRepository
    private lateinit var palette: Palette
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var notePath: String? = null
    private var openedMtime = 0L

    private lateinit var editor: EditText
    private lateinit var renderScroll: ScrollView
    private lateinit var renderList: LinearLayout
    private lateinit var btnEditMode: Button
    private lateinit var btnCancel: Button
    private lateinit var btnSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        )
        repo = VaultRepository(this)
        palette = Themes.forWidget(this, widgetId)
        notePath = WidgetPrefs.resolveNote(this, widgetId, repo)
        val path = notePath
        if (path == null) { finish(); return }
        if (repo.readNote(path) == null) {
            Toast.makeText(this, R.string.editor_load_error, Toast.LENGTH_LONG).show()
            finish(); return
        }

        setContentView(R.layout.activity_editor)
        findViewById<TextView>(R.id.editor_title).text =
            path.substringAfterLast('/').removeSuffix(".md")
        editor = findViewById(R.id.editor_text)
        renderScroll = findViewById(R.id.render_scroll)
        renderList = findViewById(R.id.render_list)
        btnEditMode = findViewById(R.id.btn_edit_mode)
        btnCancel = findViewById(R.id.btn_cancel)
        btnSave = findViewById(R.id.btn_save)

        btnEditMode.setOnClickListener { showEditMode() }
        btnCancel.setOnClickListener { if (editor.isShown) showViewMode() else finish() }
        btnSave.setOnClickListener { trySave() }
        applyTheme()
        showViewMode()
    }

    private fun showViewMode() {
        val path = notePath ?: return
        val content = repo.readNote(path) ?: return
        renderNote(content)
        renderScroll.visibility = android.view.View.VISIBLE
        editor.visibility = android.view.View.GONE
        btnSave.visibility = android.view.View.GONE
        btnEditMode.visibility = android.view.View.VISIBLE
        btnCancel.setText(R.string.editor_close)
    }

    private fun showEditMode() {
        val path = notePath ?: return
        val content = repo.readNote(path) ?: return
        editor.setText(content)
        openedMtime = repo.mtime(path)
        renderScroll.visibility = android.view.View.GONE
        editor.visibility = android.view.View.VISIBLE
        btnSave.visibility = android.view.View.VISIBLE
        btnEditMode.visibility = android.view.View.GONE
        btnCancel.setText(R.string.editor_cancel)
    }

    private fun renderNote(content: String) {
        renderList.removeAllViews()
        val density = resources.displayMetrics.density
        for (b in MarkdownParser.parse(content)) {
            val tv = TextView(this)
            tv.textSize = 15f
            val indent = (b.indent * 16 * density).toInt()
            val vpad = (4 * density).toInt()
            tv.setPadding(indent, vpad, 0, vpad)
            if (b.type == BlockType.TASK) {
                val glyph = SpanMapper.taskGlyph(b.state)
                val body = SpanMapper.toSpannable(
                    InlineStyler.style(b.text), palette, strike = b.checked
                )
                tv.text = SpannableStringBuilder("$glyph  ").apply {
                    setSpan(ForegroundColorSpan(palette.accent), 0, glyph.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    append(body)
                }
                tv.setTextColor(if (b.checked) palette.muted else palette.text)
                val line = b.sourceLine
                val expected = b.text
                tv.setOnClickListener { toggleTask(line, expected) }
            } else {
                tv.text = SpanMapper.render(b, palette)
                tv.setTextColor(palette.text)
            }
            renderList.addView(tv)
        }
    }

    private fun toggleTask(line: Int, expected: String) {
        val path = notePath ?: return
        val content = repo.readNote(path) ?: return
        when (val r = TaskToggler.toggle(content, line, expected)) {
            is ToggleResult.Success -> {
                if (!repo.writeNote(path, r.newContent)) {
                    Toast.makeText(this, R.string.editor_save_error, Toast.LENGTH_LONG).show()
                    return
                }
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    NoteWidgetProvider.updateWidget(this, AppWidgetManager.getInstance(this), widgetId)
                }
            }
            ToggleResult.LineMismatch -> { /* stale render — re-render below */ }
        }
        showViewMode()
    }

    /** Match the popup to the theme of the widget it was opened from. */
    private fun applyTheme() {
        val p = palette
        val bg = GradientDrawable().apply {
            cornerRadius = 20 * resources.displayMetrics.density
            setColor(p.bg)
        }
        findViewById<LinearLayout>(R.id.editor_root).background = bg
        window.setBackgroundDrawable(GradientDrawable().apply { setColor(0) })

        findViewById<TextView>(R.id.editor_title).setTextColor(p.heading)
        editor.setTextColor(p.text)
        editor.setHintTextColor(p.faint)

        btnEditMode.setTextColor(p.accent)
        btnCancel.setTextColor(p.muted)
        btnSave.apply {
            backgroundTintList = ColorStateList.valueOf(p.accent)
            setTextColor(p.onAccent)
        }
    }

    private fun trySave() {
        val path = notePath ?: return
        if (repo.mtime(path) != openedMtime) {
            AlertDialog.Builder(this)
                .setTitle(R.string.editor_conflict_title)
                .setMessage(R.string.editor_conflict_msg)
                .setPositiveButton(R.string.editor_overwrite) { _, _ -> save(path) }
                .setNegativeButton(R.string.editor_reload) { _, _ ->
                    val fresh = repo.readNote(path)
                    if (fresh != null) {
                        editor.setText(fresh)
                        openedMtime = repo.mtime(path)
                    }
                }
                .show()
            return
        }
        save(path)
    }

    private fun save(path: String) {
        // Preserve the file's newline style.
        val original = repo.readNote(path)
        var text = editor.text.toString()
        if (original != null && original.contains("\r\n")) {
            text = text.replace("\r\n", "\n").replace("\n", "\r\n")
        }
        if (!repo.writeNote(path, text)) {
            Toast.makeText(this, R.string.editor_save_error, Toast.LENGTH_LONG).show()
            return
        }
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            NoteWidgetProvider.updateWidget(this, AppWidgetManager.getInstance(this), widgetId)
        }
        showViewMode()
    }
}
