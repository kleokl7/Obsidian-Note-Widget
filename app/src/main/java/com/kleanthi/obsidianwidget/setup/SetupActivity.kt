package com.kleanthi.obsidianwidget.setup

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.kleanthi.obsidianwidget.R
import com.kleanthi.obsidianwidget.config.WidgetConfigActivity
import com.kleanthi.obsidianwidget.vault.VaultRepository
import com.kleanthi.obsidianwidget.widget.NoteWidgetProvider
import com.kleanthi.obsidianwidget.widget.WidgetPrefs
import com.kleanthi.obsidianwidget.widget.WidgetRefresher
import kotlin.concurrent.thread

class SetupActivity : AppCompatActivity() {
    private lateinit var repo: VaultRepository
    private lateinit var status: TextView

    private val pickFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        repo.vaultUri = uri
        refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        repo = VaultRepository(this)
        status = findViewById(R.id.status_text)
        findViewById<Button>(R.id.pick_button).setOnClickListener { pickFolder.launch(null) }
        findViewById<Button>(R.id.refresh_button).setOnClickListener {
            WidgetRefresher.refreshAll(this)
            Toast.makeText(this, R.string.setup_refreshed, Toast.LENGTH_SHORT).show()
        }
        findViewById<TextView>(R.id.version_text).text =
            "v" + packageManager.getPackageInfo(packageName, 0).versionName
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshWidgetList()
        WidgetRefresher.refreshAll(this)
    }

    /** One row per home-screen widget: note name + theme, tap to open its settings. */
    private fun refreshWidgetList() {
        val container = findViewById<LinearLayout>(R.id.widget_list)
        container.removeAllViews()
        val ids = AppWidgetManager.getInstance(this)
            .getAppWidgetIds(ComponentName(this, NoteWidgetProvider::class.java))
        findViewById<TextView>(R.id.widgets_header).text =
            getString(R.string.setup_widgets_header, ids.size)

        if (ids.isEmpty()) {
            container.addView(TextView(this).apply {
                setText(R.string.setup_no_widgets)
                alpha = 0.6f
                setPadding(0, 16, 0, 16)
            })
            return
        }
        for (id in ids) {
            val pref = WidgetPrefs.getNote(this, id)
            val note = when {
                pref == WidgetPrefs.DAILY -> "📅 Daily note"
                pref != null -> pref.substringAfterLast('/').removeSuffix(".md")
                else -> "Not configured"
            }
            val meta = buildList {
                add(WidgetPrefs.getTheme(this@SetupActivity, id).name.lowercase()
                    .replaceFirstChar { it.uppercase() } + " theme")
                val opacity = WidgetPrefs.getOpacity(this@SetupActivity, id)
                if (opacity < 100) add("$opacity%")
                when (WidgetPrefs.getFontSize(this@SetupActivity, id)) {
                    com.kleanthi.obsidianwidget.widget.FontSize.SMALL -> add("Small text")
                    com.kleanthi.obsidianwidget.widget.FontSize.LARGE -> add("Large text")
                    else -> {}
                }
                if (WidgetPrefs.getHideDone(this@SetupActivity, id)) add("Hides done")
            }.joinToString("  ·  ")

            val row = layoutInflater.inflate(R.layout.item_widget, container, false)
            row.findViewById<TextView>(R.id.widget_note_name).text = note
            row.findViewById<TextView>(R.id.widget_meta).text = meta
            val openConfig = android.view.View.OnClickListener {
                startActivity(
                    Intent(this@SetupActivity, WidgetConfigActivity::class.java)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                )
            }
            row.setOnClickListener(openConfig)
            row.findViewById<Button>(R.id.widget_edit_btn).setOnClickListener(openConfig)
            container.addView(row)
        }
    }

    private fun refreshStatus() {
        when {
            repo.vaultUri == null -> status.setText(R.string.setup_no_vault)
            !repo.isValidVault() -> status.setText(R.string.setup_invalid)
            else -> {
                status.text = "Connected: ${repo.vaultName}\nCounting notes…"
                thread {
                    val count = repo.listNotes().size
                    runOnUiThread {
                        status.text = "Connected: ${repo.vaultName}\n$count notes found.\n\n" +
                            "Long-press your home screen → Widgets → Obsidian Widget to place one."
                    }
                }
            }
        }
    }
}
