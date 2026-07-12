package com.kleanthi.obsidianwidget.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import com.kleanthi.obsidianwidget.R
import com.kleanthi.obsidianwidget.core.BlockType
import com.kleanthi.obsidianwidget.core.MarkdownParser
import com.kleanthi.obsidianwidget.core.TaskToggler
import com.kleanthi.obsidianwidget.core.ToggleResult
import com.kleanthi.obsidianwidget.editor.EditorActivity
import com.kleanthi.obsidianwidget.vault.VaultRepository

/**
 * Invisible trampoline for widget row taps. ListView rows can only fire one
 * pending-intent template; making it an activity (not a broadcast) keeps
 * Android 12+ background-launch restrictions out of the way.
 */
class WidgetActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(
            NoteWidgetProvider.EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        when (intent.getStringExtra(NoteWidgetProvider.EXTRA_ACTION)) {
            NoteWidgetProvider.ACT_TOGGLE -> {
                val line = intent.getIntExtra(NoteWidgetProvider.EXTRA_LINE, -1)
                val expected = intent.getStringExtra(NoteWidgetProvider.EXTRA_EXPECTED)
                val repo = VaultRepository(this)
                val path = WidgetPrefs.resolveNote(this, widgetId, repo)
                val content = path?.let { repo.readNote(it) }
                if (expected != null && path != null && content != null) {
                    when (val r = TaskToggler.toggle(content, line, expected)) {
                        is ToggleResult.Success -> repo.writeNote(path, r.newContent)
                        ToggleResult.LineMismatch -> { /* stale view — refresh below fixes it */ }
                    }
                }
                NoteWidgetProvider.updateWidget(this, AppWidgetManager.getInstance(this), widgetId)
            }
            NoteWidgetProvider.ACT_FOLD -> {
                val key = intent.getStringExtra(NoteWidgetProvider.EXTRA_EXPECTED)
                if (key != null) WidgetPrefs.toggleExpanded(this, widgetId, key)
                AppWidgetManager.getInstance(this)
                    .notifyAppWidgetViewDataChanged(widgetId, R.id.block_list)
            }
            NoteWidgetProvider.ACT_FOLD_HEAD -> {
                val key = intent.getStringExtra(NoteWidgetProvider.EXTRA_EXPECTED)
                if (key != null) WidgetPrefs.toggleHeadingCollapsed(this, widgetId, key)
                // Full update: the ⊖/⊕ header glyph tracks the collapsed set.
                NoteWidgetProvider.updateWidget(this, AppWidgetManager.getInstance(this), widgetId)
            }
            NoteWidgetProvider.ACT_FOLD_ALL -> {
                val repo = VaultRepository(this)
                val path = WidgetPrefs.resolveNote(this, widgetId, repo)
                val content = path?.let { repo.readNote(it) }
                if (content != null) {
                    if (WidgetPrefs.getCollapsedHeadings(this, widgetId).isEmpty()) {
                        // Collapse all: keys mirror the factory's "text#occurrence" scheme.
                        val seen = mutableMapOf<String, Int>()
                        val keys = mutableSetOf<String>()
                        for (b in MarkdownParser.parse(content)) {
                            if (b.type == BlockType.HEADING) {
                                keys.add("${b.text}#${seen.merge(b.text, 1, Int::plus)!!}")
                            }
                        }
                        WidgetPrefs.setCollapsedHeadings(this, widgetId, keys)
                    } else {
                        WidgetPrefs.setCollapsedHeadings(this, widgetId, emptySet())
                    }
                }
                NoteWidgetProvider.updateWidget(this, AppWidgetManager.getInstance(this), widgetId)
            }
            NoteWidgetProvider.ACT_EDIT -> {
                startActivity(
                    Intent(this, EditorActivity::class.java)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                )
            }
        }
        finish()
    }
}
