package com.kleanthi.obsidianwidget.setup

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.kleanthi.obsidianwidget.R
import com.kleanthi.obsidianwidget.vault.VaultRepository
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
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        com.kleanthi.obsidianwidget.widget.WidgetRefresher.refreshAll(this)
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
