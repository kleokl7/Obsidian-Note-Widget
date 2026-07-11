package com.kleanthi.obsidianwidget.vault

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

class VaultRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("vault", Context.MODE_PRIVATE)

    var vaultUri: Uri?
        get() = prefs.getString("tree_uri", null)?.let(Uri::parse)
        set(value) { prefs.edit().putString("tree_uri", value?.toString()).apply() }

    val vaultName: String?
        get() = root()?.name

    private fun root(): DocumentFile? = vaultUri?.let { DocumentFile.fromTreeUri(context, it) }

    fun isValidVault(): Boolean {
        val r = root() ?: return false
        return r.isDirectory && r.findFile(".obsidian") != null
    }

    data class NoteRef(val relPath: String, val name: String)

    fun listNotes(): List<NoteRef> {
        val r = root() ?: return emptyList()
        val out = mutableListOf<NoteRef>()
        fun walk(dir: DocumentFile, prefix: String) {
            for (f in dir.listFiles()) {
                val name = f.name ?: continue
                if (f.isDirectory) {
                    if (!name.startsWith(".")) walk(f, "$prefix$name/")
                } else if (name.endsWith(".md")) {
                    out.add(NoteRef("$prefix$name", name.removeSuffix(".md")))
                }
            }
        }
        walk(r, "")
        return out.sortedBy { it.relPath.lowercase() }
    }

    private fun find(relPath: String): DocumentFile? {
        var cur = root() ?: return null
        for (seg in relPath.split('/')) {
            if (seg.isEmpty()) continue
            cur = cur.findFile(seg) ?: return null
        }
        return cur
    }

    fun readNote(relPath: String): String? = try {
        find(relPath)?.let { f ->
            context.contentResolver.openInputStream(f.uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        }
    } catch (e: Exception) { null }

    fun writeNote(relPath: String, content: String): Boolean = try {
        val f = find(relPath)
        if (f == null) false
        else context.contentResolver.openOutputStream(f.uri, "wt")?.use {
            it.write(content.toByteArray(Charsets.UTF_8))
            true
        } ?: false
    } catch (e: Exception) { false }

    fun mtime(relPath: String): Long = find(relPath)?.lastModified() ?: 0L
}
