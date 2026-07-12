package com.kleanthi.obsidianwidget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test fun `heading levels and text`() {
        val b = MarkdownParser.parse("## Hello **world**")
        assertEquals(1, b.size)
        assertEquals(BlockType.HEADING, b[0].type)
        assertEquals(2, b[0].level)
        assertEquals("Hello **world**", b[0].text)
        assertEquals(0, b[0].sourceLine)
    }

    @Test fun `unchecked and checked tasks with nesting`() {
        val md = "- [ ] buy milk\n  - [x] sub done\n- [X] caps also checked"
        val b = MarkdownParser.parse(md)
        assertEquals(3, b.size)
        assertEquals(BlockType.TASK, b[0].type)
        assertEquals(false, b[0].checked)
        assertEquals("buy milk", b[0].text)
        assertEquals(0, b[0].indent)
        assertEquals(true, b[1].checked)
        assertEquals(1, b[1].indent)
        assertEquals(1, b[1].sourceLine)
        assertEquals(true, b[2].checked)
    }

    @Test fun `bullets quotes rules paragraphs`() {
        val md = "para here\n\n- a bullet\n> quoted\n---"
        val b = MarkdownParser.parse(md)
        assertEquals(listOf(BlockType.PARAGRAPH, BlockType.BULLET, BlockType.QUOTE, BlockType.RULE),
            b.map { it.type })
        assertEquals("a bullet", b[1].text)
        assertEquals("quoted", b[2].text)
    }

    @Test fun `frontmatter is skipped but line numbers stay absolute`() {
        val md = "---\ntags: [x]\n---\n# Title"
        val b = MarkdownParser.parse(md)
        assertEquals(1, b.size)
        assertEquals(BlockType.HEADING, b[0].type)
        assertEquals(3, b[0].sourceLine)
    }

    @Test fun `code fences become one block and contents are not parsed`() {
        val md = "```\n- [ ] not a task\ncode line\n```\nafter"
        val b = MarkdownParser.parse(md)
        assertEquals(2, b.size)
        assertEquals(BlockType.CODE, b[0].type)
        assertTrue(b[0].text.contains("not a task"))
        assertEquals(BlockType.PARAGRAPH, b[1].type)
        assertEquals(4, b[1].sourceLine)
    }

    @Test fun `crlf input parses like lf`() {
        val b = MarkdownParser.parse("# A\r\n- [ ] t\r\n")
        assertEquals(BlockType.HEADING, b[0].type)
        assertEquals(BlockType.TASK, b[1].type)
        assertEquals(1, b[1].sourceLine)
    }

    @Test fun `blank lines produce no blocks`() {
        assertEquals(0, MarkdownParser.parse("\n\n   \n").size)
    }

    @Test fun `star and plus bullets and tasks`() {
        val b = MarkdownParser.parse("* [ ] star task\n+ plus bullet")
        assertEquals(BlockType.TASK, b[0].type)
        assertEquals(BlockType.BULLET, b[1].type)
    }

    @Test fun `alternate task states parse with state char`() {
        val b = MarkdownParser.parse("- [/] in progress\n- [-] cancelled\n- [>] forwarded")
        assertEquals(listOf(BlockType.TASK, BlockType.TASK, BlockType.TASK), b.map { it.type })
        assertEquals('/', b[0].state)
        assertEquals(false, b[0].checked)
        assertEquals('-', b[1].state)
        assertEquals('>', b[2].state)
    }

    @Test fun `double-bracket wikilink bullet is not a task`() {
        val b = MarkdownParser.parse("- [[Some Note]]")
        assertEquals(BlockType.BULLET, b[0].type)
    }

    @Test fun `indented paragraph records indent for folding`() {
        val b = MarkdownParser.parse("- [ ] task\n  some detail text")
        assertEquals(BlockType.PARAGRAPH, b[1].type)
        assertEquals(1, b[1].indent)
        assertEquals("some detail text", b[1].text)
    }
}
