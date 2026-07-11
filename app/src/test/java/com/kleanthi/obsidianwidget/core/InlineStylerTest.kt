package com.kleanthi.obsidianwidget.core

import org.junit.Assert.assertEquals
import org.junit.Test

class InlineStylerTest {

    @Test fun `bold syntax stripped and span recorded`() {
        val s = InlineStyler.style("a **bold** word")
        assertEquals("a bold word", s.text)
        assertEquals(listOf(Span(2, 6, InlineStyle.BOLD)), s.spans)
    }

    @Test fun `wikilink with alias shows alias`() {
        val s = InlineStyler.style("see [[Some Note|the note]] ok")
        assertEquals("see the note ok", s.text)
        assertEquals(listOf(Span(4, 12, InlineStyle.LINK)), s.spans)
    }

    @Test fun `plain wikilink shows target`() {
        val s = InlineStyler.style("[[Daily/2026-07-10]]")
        assertEquals("Daily/2026-07-10", s.text)
        assertEquals(listOf(Span(0, 16, InlineStyle.LINK)), s.spans)
    }

    @Test fun `markdown link shows label`() {
        val s = InlineStyler.style("go [here](https://x.com) now")
        assertEquals("go here now", s.text)
        assertEquals(listOf(Span(3, 7, InlineStyle.LINK)), s.spans)
    }

    @Test fun `highlight strike code italic`() {
        assertEquals(listOf(Span(0, 2, InlineStyle.HIGHLIGHT)), InlineStyler.style("==hi==").spans)
        assertEquals(listOf(Span(0, 4, InlineStyle.STRIKE)), InlineStyler.style("~~gone~~").spans)
        assertEquals(listOf(Span(0, 4, InlineStyle.CODE)), InlineStyler.style("`code`").spans)
        assertEquals(listOf(Span(0, 2, InlineStyle.ITALIC)), InlineStyler.style("*it*").spans)
        assertEquals(listOf(Span(0, 2, InlineStyle.ITALIC)), InlineStyler.style("_it_").spans)
    }

    @Test fun `multiple spans in one line`() {
        val s = InlineStyler.style("**a** and ==b==")
        assertEquals("a and b", s.text)
        assertEquals(listOf(Span(0, 1, InlineStyle.BOLD), Span(6, 7, InlineStyle.HIGHLIGHT)), s.spans)
    }

    @Test fun `plain text untouched`() {
        val p = InlineStyler.style("just words, no markers")
        assertEquals("just words, no markers", p.text)
        assertEquals(0, p.spans.size)
    }

    @Test fun `bold not eaten by italic`() {
        val s = InlineStyler.style("**b**")
        assertEquals(listOf(Span(0, 1, InlineStyle.BOLD)), s.spans)
    }
}
