package com.kleanthi.obsidianwidget.core

sealed class ToggleResult {
    data class Success(val newContent: String, val nowChecked: Boolean) : ToggleResult()
    object LineMismatch : ToggleResult()
}

object TaskToggler {
    private val lineRe = Regex("""^(\s*[-*+]\s+\[)(.)(]\s?)(.*)$""")

    fun toggle(content: String, lineIndex: Int, expectedText: String): ToggleResult {
        val newline = if (content.contains("\r\n")) "\r\n" else "\n"
        val lines = content.replace("\r\n", "\n").split("\n").toMutableList()
        if (lineIndex !in lines.indices) return ToggleResult.LineMismatch
        val m = lineRe.find(lines[lineIndex]) ?: return ToggleResult.LineMismatch
        if (m.groupValues[4] != expectedText) return ToggleResult.LineMismatch
        // x/X -> unchecked; anything else (incl. alternate states like /) -> done.
        val cur = m.groupValues[2][0]
        val nowChecked = !(cur == 'x' || cur == 'X')
        val mark = if (nowChecked) "x" else " "
        lines[lineIndex] = m.groupValues[1] + mark + m.groupValues[3] + m.groupValues[4]
        return ToggleResult.Success(lines.joinToString(newline), nowChecked)
    }
}
