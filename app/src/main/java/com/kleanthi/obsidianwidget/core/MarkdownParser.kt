package com.kleanthi.obsidianwidget.core

enum class BlockType { HEADING, PARAGRAPH, BULLET, TASK, QUOTE, CODE, RULE }

data class Block(
    val type: BlockType,
    val text: String,
    val level: Int = 0,
    val checked: Boolean = false,
    val indent: Int = 0,
    val sourceLine: Int
)

object MarkdownParser {
    private val headingRe = Regex("""^(#{1,6})\s+(.*)$""")
    private val taskRe = Regex("""^(\s*)[-*+]\s+\[( |x|X)\]\s?(.*)$""")
    private val bulletRe = Regex("""^(\s*)[-*+]\s+(.*)$""")
    private val quoteRe = Regex("""^>\s?(.*)$""")

    fun parse(markdown: String): List<Block> {
        val lines = markdown.replace("\r\n", "\n").split("\n")
        val blocks = mutableListOf<Block>()
        var i = 0

        // Skip YAML frontmatter but keep absolute line numbering.
        if (lines.firstOrNull()?.trim() == "---") {
            val end = (1 until lines.size).firstOrNull { lines[it].trim() == "---" }
            if (end != null) i = end + 1
        }

        var inCode = false
        var codeStart = 0
        val codeBuf = StringBuilder()

        while (i < lines.size) {
            val line = lines[i]
            if (inCode) {
                if (line.trimStart().startsWith("```")) {
                    blocks.add(Block(BlockType.CODE, codeBuf.toString().trimEnd('\n'), sourceLine = codeStart))
                    inCode = false
                } else {
                    codeBuf.append(line).append('\n')
                }
                i++; continue
            }
            when {
                line.trimStart().startsWith("```") -> { inCode = true; codeStart = i; codeBuf.clear() }
                line.isBlank() -> {}
                headingRe.matches(line) -> {
                    val m = headingRe.find(line)!!
                    blocks.add(Block(BlockType.HEADING, m.groupValues[2].trim(),
                        level = m.groupValues[1].length, sourceLine = i))
                }
                taskRe.matches(line) -> {
                    val m = taskRe.find(line)!!
                    blocks.add(Block(BlockType.TASK, m.groupValues[3],
                        checked = m.groupValues[2].equals("x", ignoreCase = true),
                        indent = m.groupValues[1].length / 2, sourceLine = i))
                }
                line.trim() == "---" || line.trim() == "***" || line.trim() == "___" ->
                    blocks.add(Block(BlockType.RULE, "", sourceLine = i))
                bulletRe.matches(line) -> {
                    val m = bulletRe.find(line)!!
                    blocks.add(Block(BlockType.BULLET, m.groupValues[2],
                        indent = m.groupValues[1].length / 2, sourceLine = i))
                }
                quoteRe.matches(line) -> {
                    val m = quoteRe.find(line)!!
                    blocks.add(Block(BlockType.QUOTE, m.groupValues[1], sourceLine = i))
                }
                else -> blocks.add(Block(BlockType.PARAGRAPH, line.trim(), sourceLine = i))
            }
            i++
        }
        // Unterminated fence: emit what we collected so the note isn't silently truncated.
        if (inCode) blocks.add(Block(BlockType.CODE, codeBuf.toString().trimEnd('\n'), sourceLine = codeStart))
        return blocks
    }
}
