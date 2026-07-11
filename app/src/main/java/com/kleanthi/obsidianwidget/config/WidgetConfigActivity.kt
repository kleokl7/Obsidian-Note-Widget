package com.kleanthi.obsidianwidget.config

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.kleanthi.obsidianwidget.R
import com.kleanthi.obsidianwidget.vault.VaultRepository
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
        val list = findViewById<ListView>(R.id.note_list)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        list.adapter = adapter

        findViewById<EditText>(R.id.search_box).doAfterTextChanged { q ->
            filter(q?.toString().orEmpty())
        }
        list.setOnItemClickListener { _, _, pos, _ ->
            val note = shown[pos]
            WidgetPrefs.setNote(this, appWidgetId, note.relPath)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }

        thread {
            val notes = VaultRepository(this).listNotes()
            runOnUiThread { allNotes = notes; filter("") }
        }
    }

    private fun filter(query: String) {
        shown = if (query.isBlank()) allNotes
                else allNotes.filter { it.relPath.contains(query, ignoreCase = true) }
        adapter.clear()
        adapter.addAll(shown.map { it.relPath.removeSuffix(".md") })
    }
}
