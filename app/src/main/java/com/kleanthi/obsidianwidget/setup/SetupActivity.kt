package com.kleanthi.obsidianwidget.setup

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Obsidian Widget — setup coming in Task 5" })
    }
}
