package com.kleanthi.obsidianwidget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutlineTest {

    private fun rows(
        md: String,
        expanded: Set<String> = emptySet(),
        collapsed: Set<String> = emptySet(),
        hideDone: Boolean = false,
    ) = Outline.buildRows(MarkdownParser.parse(md), expanded, collapsed, hideDone)

    @Test fun `done and cancelled tasks are not open, other states are`() {
        val b = MarkdownParser.parse("- [ ] a\n- [/] b\n- [>] c\n- [x] d\n- [X] e\n- [-] f\n- [?] g\nplain")
        assertEquals(listOf(true, true, true, false, false, false, true, false), b.map(Outline::isOpen))
        assertEquals(4, Outline.openTaskCount(b))
    }

    @Test fun `repeated heading text gets occurrence keys`() {
        val b = MarkdownParser.parse("# Tasks\n# Notes\n## Tasks\n# Tasks")
        assertEquals(listOf("Tasks#1", "Notes#1", "Tasks#2", "Tasks#3"), Outline.headingKeys(b))
    }

    @Test fun `heading keys do not shift when an earlier section is collapsed`() {
        val md = "# A\n## Tasks\n- [ ] one\n# B\n## Tasks\n- [ ] two"
        assertEquals(listOf("A#1", "Tasks#1", "B#1", "Tasks#2"), rows(md).map { it.headKey }.filterNotNull())
        assertEquals(listOf("A#1", "B#1", "Tasks#2"),
            rows(md, collapsed = setOf("A#1")).map { it.headKey }.filterNotNull())
    }

    @Test fun `stale collapsed keys do not count as collapsed`() {
        val b = MarkdownParser.parse("# Today\n- [ ] x")
        assertFalse(Outline.anyCollapsed(b, emptySet()))
        assertFalse(Outline.anyCollapsed(b, setOf("Yesterday#1")))
        assertTrue(Outline.anyCollapsed(b, setOf("Yesterday#1", "Today#1")))
    }

    @Test fun `collapsed heading hides its section up to the next heading of same or higher level`() {
        val md = "# A\n- [ ] a1\n## A.1\n- [ ] a2\n# B\n- [ ] b1"
        val r = rows(md, collapsed = setOf("A#1"))
        assertEquals(listOf("A", "B", "b1"), r.map { it.block.text })
        assertFalse(r[0].expanded)
        assertTrue(r[1].expanded)
    }

    @Test fun `heading counts open tasks in its whole section, -1 when it has none`() {
        val md = "# A\n- [ ] a1\n- [x] a2\n## A.1\n- [/] a3\n- [-] a4\n# B\ntext"
        val heads = rows(md).filter { it.block.type == BlockType.HEADING }
            .associate { it.block.text to it.openTasks }
        assertEquals(mapOf("A" to 2, "A.1" to 1, "B" to -1), heads)
    }

    @Test fun `tasks with children fold by default and expand by task text`() {
        val md = "- [ ] parent\n  - [ ] child\n  note under it\n- [ ] next"
        val folded = rows(md)
        assertEquals(listOf("parent", "next"), folded.map { it.block.text })
        assertTrue(folded[0].foldable)
        assertFalse(folded[0].expanded)
        assertEquals(2, folded[0].hidden)
        assertFalse(folded[1].foldable)

        val open = rows(md, expanded = setOf("parent"))
        assertEquals(listOf("parent", "child", "note under it", "next"), open.map { it.block.text })
        assertTrue(open[0].expanded)
    }

    @Test fun `hide completed drops done and cancelled tasks with their children`() {
        val md = "- [x] done\n  - [ ] sub of done\n- [-] dropped\n  more\n- [ ] open\n- [/] doing"
        assertEquals(listOf("open", "doing"), rows(md, hideDone = true).map { it.block.text })
        assertEquals(6, rows(md, expanded = setOf("done", "dropped")).size)
    }
}
