package com.kleanthi.obsidianwidget.config

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.kleanthi.obsidianwidget.R
import com.kleanthi.obsidianwidget.vault.VaultRepository
import com.kleanthi.obsidianwidget.widget.FontSize
import com.kleanthi.obsidianwidget.widget.NoteWidgetProvider
import com.kleanthi.obsidianwidget.widget.ThemeMode
import com.kleanthi.obsidianwidget.widget.WidgetPrefs
import kotlin.concurrent.thread

class WidgetConfigActivity : AppCompatActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var allNotes: List<VaultRepository.NoteRef> = emptyList()
    private var shown: List<VaultRepository.NoteRef> = emptyList()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Must default to CANCELED so abandoning config removes the widget.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        setContentView(R.layout.activity_config)

        val themeGroup = findViewById<RadioGroup>(R.id.theme_group)
        themeGroup.check(
            when (WidgetPrefs.getTheme(this, appWidgetId)) {
                ThemeMode.SYSTEM -> R.id.theme_system
                ThemeMode.LIGHT -> R.id.theme_light
                ThemeMode.DARK -> R.id.theme_dark
            }
        )
        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            applyChange {
                WidgetPrefs.setTheme(
                    this, appWidgetId,
                    when (checkedId) {
                        R.id.theme_light -> ThemeMode.LIGHT
                        R.id.theme_dark -> ThemeMode.DARK
                        else -> ThemeMode.SYSTEM
                    }
                )
            }
        }

        val opacityLabel = findViewById<TextView>(R.id.opacity_label)
        val opacitySeek = findViewById<SeekBar>(R.id.opacity_seek)
        val opacity = WidgetPrefs.getOpacity(this, appWidgetId)
        opacitySeek.progress = opacity
        opacityLabel.text = getString(R.string.config_opacity_label, opacity)
        opacitySeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, value: Int, fromUser: Boolean) {
                opacityLabel.text = getString(R.string.config_opacity_label, value)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {
                applyChange { WidgetPrefs.setOpacity(this@WidgetConfigActivity, appWidgetId, sb.progress) }
            }
        })

        val fontGroup = findViewById<RadioGroup>(R.id.font_group)
        fontGroup.check(
            when (WidgetPrefs.getFontSize(this, appWidgetId)) {
                FontSize.SMALL -> R.id.font_small
                FontSize.MEDIUM -> R.id.font_medium
                FontSize.LARGE -> R.id.font_large
            }
        )
        fontGroup.setOnCheckedChangeListener { _, checkedId ->
            applyChange {
                WidgetPrefs.setFontSize(
                    this, appWidgetId,
                    when (checkedId) {
                        R.id.font_small -> FontSize.SMALL
                        R.id.font_large -> FontSize.LARGE
                        else -> FontSize.MEDIUM
                    }
                )
            }
        }

        val hideDone = findViewById<CheckBox>(R.id.hide_done)
        hideDone.isChecked = WidgetPrefs.getHideDone(this, appWidgetId)
        hideDone.setOnCheckedChangeListener { _, checked ->
            applyChange { WidgetPrefs.setHideDone(this, appWidgetId, checked) }
        }

        val showCount = findViewById<CheckBox>(R.id.show_count)
        showCount.isChecked = WidgetPrefs.getShowCount(this, appWidgetId)
        showCount.setOnCheckedChangeListener { _, checked ->
            applyChange { WidgetPrefs.setShowCount(this, appWidgetId, checked) }
        }

        findViewById<TextView>(R.id.daily_button).setOnClickListener {
            WidgetPrefs.setNote(this, appWidgetId, WidgetPrefs.DAILY)
            NoteWidgetProvider.updateWidget(this, AppWidgetManager.getInstance(this), appWidgetId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }

        val list = findViewById<ListView>(R.id.note_list)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        list.adapter = adapter

        findViewById<EditText>(R.id.search_box).doAfterTextChanged { q ->
            filter(q?.toString().orEmpty())
        }
        list.setOnItemClickListener { _, _, pos, _ ->
            val note = shown[pos]
            WidgetPrefs.setNote(this, appWidgetId, note.relPath)
            // The launcher only renders on first configure; reconfigure paths
            // (widget ⚙ button, app widget list) need an explicit update.
            NoteWidgetProvider.updateWidget(this, AppWidgetManager.getInstance(this), appWidgetId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }

        thread {
            val notes = VaultRepository(this).listNotes()
            runOnUiThread { allNotes = notes; filter("") }
        }
    }

    /** Persist a setting and re-render the widget if it's already showing a note. */
    private fun applyChange(write: () -> Unit) {
        write()
        if (WidgetPrefs.getNote(this, appWidgetId) != null) {
            NoteWidgetProvider.updateWidget(this, AppWidgetManager.getInstance(this), appWidgetId)
        }
    }

    private fun filter(query: String) {
        shown = if (query.isBlank()) allNotes
                else allNotes.filter { it.relPath.contains(query, ignoreCase = true) }
        adapter.clear()
        adapter.addAll(shown.map { it.relPath.removeSuffix(".md") })
    }
}
