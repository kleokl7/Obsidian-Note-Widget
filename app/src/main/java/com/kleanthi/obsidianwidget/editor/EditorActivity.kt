package com.kleanthi.obsidianwidget.editor

import android.appwidget.AppWidgetManager
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.kleanthi.obsidianwidget.R
import com.kleanthi.obsidianwidget.vault.VaultRepository
import com.kleanthi.obsidianwidget.widget.NoteWidgetProvider
import com.kleanthi.obsidianwidget.widget.Themes
import com.kleanthi.obsidianwidget.widget.WidgetPrefs

class EditorActivity : AppCompatActivity() {
    private lateinit var repo: VaultRepository
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var notePath: String? = null
    private var openedMtime = 0L
    private lateinit var editor: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        )
        repo = VaultRepository(this)
        notePath = WidgetPrefs.getNote(this, widgetId)
        val path = notePath
        if (path == null) { finish(); return }

        val content = repo.readNote(path)
        if (content == null) {
            Toast.makeText(this, R.string.editor_load_error, Toast.LENGTH_LONG).show()
            finish(); return
        }
        openedMtime = repo.mtime(path)

        setContentView(R.layout.activity_editor)
        findViewById<TextView>(R.id.editor_title).text =
            path.substringAfterLast('/').removeSuffix(".md")
        editor = findViewById(R.id.editor_text)
        editor.setText(content)

        findViewById<Button>(R.id.btn_cancel).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_save).setOnClickListener { trySave() }
        applyTheme()
    }

    /** Match the popup to the theme of the widget it was opened from. */
    private fun applyTheme() {
        val p = Themes.forWidget(this, widgetId)
        val bg = GradientDrawable().apply {
            cornerRadius = 20 * resources.displayMetrics.density
            setColor(p.bg)
        }
        findViewById<LinearLayout>(R.id.editor_root).background = bg
        window.setBackgroundDrawable(GradientDrawable().apply { setColor(0) })

        val title = findViewById<TextView>(R.id.editor_title)
        title.setTextColor(p.heading)
        editor.setTextColor(p.text)
        editor.setHintTextColor(p.faint)

        findViewById<Button>(R.id.btn_cancel).setTextColor(p.muted)
        findViewById<Button>(R.id.btn_save).apply {
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
        finish()
    }
}
