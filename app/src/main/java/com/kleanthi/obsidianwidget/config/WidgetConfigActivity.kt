package com.kleanthi.obsidianwidget.config

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
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

open class WidgetConfigActivity : AppCompatActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var allNotes: List<VaultRepository.NoteRef> = emptyList()
    private var shown: List<VaultRepository.NoteRef> = emptyList()
    private var loaded = false
    private lateinit var adapter: NoteAdapter
    private lateinit var noteStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Must default to CANCELED so abandoning config removes the widget.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        setContentView(R.layout.activity_config)
        // Settings are the list's header so the whole page scrolls as one and
        // the search box stays reachable above the keyboard.
        val list = findViewById<ListView>(R.id.note_list)
        val h = layoutInflater.inflate(R.layout.config_header, list, false)
        list.addHeaderView(h, null, false)

        val themeGroup = h.findViewById<RadioGroup>(R.id.theme_group)
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

        val opacityLabel = h.findViewById<TextView>(R.id.opacity_label)
        val opacitySeek = h.findViewById<SeekBar>(R.id.opacity_seek)
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

        val fontGroup = h.findViewById<RadioGroup>(R.id.font_group)
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

        val hideDone = h.findViewById<CheckBox>(R.id.hide_done)
        hideDone.isChecked = WidgetPrefs.getHideDone(this, appWidgetId)
        hideDone.setOnCheckedChangeListener { _, checked ->
            applyChange { WidgetPrefs.setHideDone(this, appWidgetId, checked) }
        }

        val showCount = h.findViewById<CheckBox>(R.id.show_count)
        showCount.isChecked = WidgetPrefs.getShowCount(this, appWidgetId)
        showCount.setOnCheckedChangeListener { _, checked ->
            applyChange { WidgetPrefs.setShowCount(this, appWidgetId, checked) }
        }

        h.findViewById<Button>(R.id.daily_button).setOnClickListener {
            WidgetPrefs.setNote(this, appWidgetId, WidgetPrefs.DAILY)
            NoteWidgetProvider.updateWidget(this, AppWidgetManager.getInstance(this), appWidgetId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }

        noteStatus = h.findViewById(R.id.note_status)
        adapter = NoteAdapter()
        list.adapter = adapter

        val searchBox = h.findViewById<EditText>(R.id.search_box)
        searchBox.doAfterTextChanged { q ->
            filter(q?.toString().orEmpty())
            pinSearchToTop()
        }
        // With the keyboard up, the results only fit if the box sits at the top.
        searchBox.setOnFocusChangeListener { _, focused -> if (focused) pinSearchToTop() }
        // The keyboard resizes the window after focus lands; re-pin on that resize.
        list.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) pinSearchToTop()
        }
        list.setOnItemClickListener { _, _, pos, _ ->
            val note = shown.getOrNull(pos - list.headerViewsCount) ?: return@setOnItemClickListener
            WidgetPrefs.setNote(this, appWidgetId, note.relPath)
            // The launcher only renders on first configure; reconfigure paths
            // (widget ⚙ button, app widget list) need an explicit update.
            NoteWidgetProvider.updateWidget(this, AppWidgetManager.getInstance(this), appWidgetId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }

        thread {
            val notes = VaultRepository(this).listNotes()
            runOnUiThread {
                allNotes = notes
                loaded = true
                filter(h.findViewById<EditText>(R.id.search_box).text?.toString().orEmpty())
            }
        }
    }

    private fun pinSearchToTop() {
        val list = findViewById<ListView>(R.id.note_list)
        val box = list.findViewById<View>(R.id.search_box) ?: return
        if (!box.hasFocus()) return
        val gap = (12 * resources.displayMetrics.density).toInt()
        list.post { list.setSelectionFromTop(0, -(box.top - gap)) }
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
        adapter.notifyDataSetChanged()
        // The status line speaks only when the list can't: loading, an empty
        // vault, or a search with no hits.
        noteStatus.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        noteStatus.text = when {
            !loaded -> getString(R.string.config_loading)
            allNotes.isEmpty() -> getString(R.string.config_no_notes)
            else -> getString(R.string.config_no_matches, query.trim())
        }
    }

    /** Note name on the first line, its folder (if any) on the second. */
    private inner class NoteAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView
                ?: LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false)
            val note = shown[position]
            val folder = note.relPath.substringBeforeLast('/', "")
            view.findViewById<TextView>(R.id.note_name).text = note.name
            view.findViewById<TextView>(R.id.note_folder).apply {
                text = folder
                visibility = if (folder.isEmpty()) View.GONE else View.VISIBLE
            }
            return view
        }
    }
}
