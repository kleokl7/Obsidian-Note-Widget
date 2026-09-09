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
import com.kleanthi.obsidianwidget.config.WidgetReconfigActivity
import com.kleanthi.obsidianwidget.vault.VaultRepository
import com.kleanthi.obsidianwidget.widget.FontSize
import com.kleanthi.obsidianwidget.widget.NoteWidgetProvider
import com.kleanthi.obsidianwidget.widget.ThemeMode
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
            if (ids.isEmpty()) getString(R.string.setup_widgets_header)
            else getString(R.string.setup_widgets_header_count, ids.size)

        if (ids.isEmpty()) {
            val density = resources.displayMetrics.density
            container.addView(TextView(this).apply {
                setText(R.string.setup_no_widgets)
                setTextColor(getColor(R.color.ink_muted))
                textSize = 14f
                setPadding(0, (4 * density).toInt(), 0, (16 * density).toInt())
            })
            return
        }
        for (id in ids) {
            val pref = WidgetPrefs.getNote(this, id)
            val note = when {
                pref == WidgetPrefs.DAILY -> getString(R.string.setup_daily_note)
                pref != null -> pref.substringAfterLast('/').removeSuffix(".md")
                else -> getString(R.string.setup_not_configured)
            }
            val meta = buildList {
                val theme = when (WidgetPrefs.getTheme(this@SetupActivity, id)) {
                    ThemeMode.SYSTEM -> getString(R.string.config_theme_system)
                    ThemeMode.LIGHT -> getString(R.string.config_theme_light)
                    ThemeMode.DARK -> getString(R.string.config_theme_dark)
                }
                add(getString(R.string.setup_meta_theme, theme))
                val opacity = WidgetPrefs.getOpacity(this@SetupActivity, id)
                if (opacity < 100) add(getString(R.string.setup_meta_opacity, opacity))
                when (WidgetPrefs.getFontSize(this@SetupActivity, id)) {
                    FontSize.SMALL -> add(getString(R.string.setup_meta_small))
                    FontSize.LARGE -> add(getString(R.string.setup_meta_large))
                    else -> {}
                }
                if (WidgetPrefs.getHideDone(this@SetupActivity, id)) add(getString(R.string.setup_meta_hide_done))
            }.joinToString(" · ")

            val row = layoutInflater.inflate(R.layout.item_widget, container, false)
            row.findViewById<TextView>(R.id.widget_note_name).text = note
            row.findViewById<TextView>(R.id.widget_meta).text = meta
            val openConfig = android.view.View.OnClickListener {
                startActivity(
                    Intent(this@SetupActivity, WidgetReconfigActivity::class.java)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                )
            }
            row.setOnClickListener(openConfig)
            row.findViewById<Button>(R.id.widget_edit_btn).setOnClickListener(openConfig)
            container.addView(row)
        }
    }

    /** Vault card: one state line, one hint, and a button named for the next step. */
    private fun refreshStatus() {
        val hint = findViewById<TextView>(R.id.status_hint)
        val pick = findViewById<Button>(R.id.pick_button)
        when {
            repo.vaultUri == null -> {
                status.setText(R.string.setup_no_vault)
                hint.setText(R.string.setup_no_vault_hint)
                pick.setText(R.string.setup_pick_vault)
            }
            !repo.isValidVault() -> {
                status.setText(R.string.setup_invalid)
                hint.setText(R.string.setup_invalid_hint)
                pick.setText(R.string.setup_pick_vault)
            }
            else -> {
                val vault = repo.vaultName
                status.text = getString(R.string.setup_connected, vault)
                hint.setText(R.string.setup_counting)
                pick.setText(R.string.setup_change_vault)
                thread {
                    val count = repo.listNotes().size
                    runOnUiThread {
                        if (repo.vaultName != vault) return@runOnUiThread
                        val found = if (count == 1) getString(R.string.setup_note_count_one)
                                    else getString(R.string.setup_note_count, count)
                        hint.text = found + " " + getString(R.string.setup_place_hint)
                    }
                }
            }
        }
    }
}
