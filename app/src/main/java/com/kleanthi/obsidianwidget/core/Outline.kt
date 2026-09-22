package com.kleanthi.obsidianwidget.core

/**
 * Fold, hide and count rules for the widget's outline. The row factory, the
 * header (title count, ⊖/⊕ glyph) and the tap trampoline all use these, so
 * they can't drift apart.
 */
object Outline {

    data class Row(
        val block: Block,
        val foldable: Boolean,
        val expanded: Boolean,
        val hidden: Int,              // tasks: number of folded children
        val openTasks: Int = -1,      // headings: open tasks in section (-1 = no tasks at all)
        val headKey: String? = null,  // headings: fold-state key ("text#occurrence")
    )

    private val childTypes = setOf(BlockType.TASK, BlockType.BULLET, BlockType.PARAGRAPH, BlockType.QUOTE)

    /** Anything not done `[x]` or cancelled `[-]` is open, so `[/]`, `[>]`, … count. */
    fun isOpen(b: Block): Boolean = b.type == BlockType.TASK && !b.checked && b.state != '-'

    /** Done or cancelled: what "Hide completed tasks" hides. */
    fun isClosed(b: Block): Boolean = b.type == BlockType.TASK && (b.checked || b.state == '-')

    fun openTaskCount(blocks: List<Block>): Int = blocks.count(::isOpen)

    /**
     * Fold-state key per heading block index: "text#occurrence" (1-based),
     * because the same heading text often repeats within a note. Counted over
     * the whole note, so a key never depends on what is currently folded.
     */
    private fun keysByIndex(blocks: List<Block>): Map<Int, String> {
        val seen = mutableMapOf<String, Int>()
        val out = LinkedHashMap<Int, String>()
        blocks.forEachIndexed { i, b ->
            if (b.type == BlockType.HEADING) out[i] = "${b.text}#${seen.merge(b.text, 1, Int::plus)!!}"
        }
        return out
    }

    /** Every heading's fold-state key, in document order. */
    fun headingKeys(blocks: List<Block>): List<String> = keysByIndex(blocks).values.toList()

    /**
     * Whether any heading of this note is collapsed. Stored keys whose heading
     * was renamed or deleted don't count, so they can't leave the header on ⊕
     * while every section is open.
     */
    fun anyCollapsed(blocks: List<Block>, collapsed: Set<String>): Boolean =
        collapsed.isNotEmpty() && headingKeys(blocks).any { it in collapsed }

    /**
     * Tasks with indented children fold (folded unless their text is in
     * [expanded]), and headings fold their whole section — everything up to
     * the next heading of the same or higher level. With [hideDone], done and
     * cancelled tasks disappear together with their indented children.
     */
    fun buildRows(
        blocks: List<Block>,
        expanded: Set<String>,
        collapsed: Set<String>,
        hideDone: Boolean,
    ): List<Row> {
        val keys = keysByIndex(blocks)
        val out = mutableListOf<Row>()
        var i = 0
        while (i < blocks.size) {
            val b = blocks[i]
            if (b.type == BlockType.HEADING) {
                var j = i + 1
                while (j < blocks.size &&
                    !(blocks[j].type == BlockType.HEADING && blocks[j].level <= b.level)) j++
                val section = blocks.subList(i + 1, j)
                val hasTasks = section.any { it.type == BlockType.TASK }
                val key = keys.getValue(i)
                val isCollapsed = key in collapsed
                out.add(Row(b, foldable = true, expanded = !isCollapsed, hidden = 0,
                    openTasks = if (hasTasks) openTaskCount(section) else -1, headKey = key))
                i = if (isCollapsed) j else i + 1
            } else if (b.type == BlockType.TASK) {
                var j = i + 1
                while (j < blocks.size && blocks[j].type in childTypes && blocks[j].indent > b.indent) j++
                if (hideDone && isClosed(b)) { i = j; continue }
                val kids = j - i - 1
                val isExpanded = kids > 0 && b.text in expanded
                out.add(Row(b, kids > 0, isExpanded, kids))
                i = if (kids > 0 && !isExpanded) j else i + 1
            } else {
                out.add(Row(b, false, false, 0))
                i++
            }
        }
        return out
    }
}
