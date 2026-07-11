package com.kleanthi.obsidianwidget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskTogglerTest {

    @Test fun `checks an unchecked task`() {
        val r = TaskToggler.toggle("- [ ] buy milk\n- [ ] other", 0, "buy milk")
        r as ToggleResult.Success
        assertEquals("- [x] buy milk\n- [ ] other", r.newContent)
        assertEquals(true, r.nowChecked)
    }

    @Test fun `unchecks a checked task including capital X`() {
        val r = TaskToggler.toggle("- [X] done thing", 0, "done thing")
        r as ToggleResult.Success
        assertEquals("- [ ] done thing", r.newContent)
        assertEquals(false, r.nowChecked)
    }

    @Test fun `preserves indentation and marker`() {
        val r = TaskToggler.toggle("  * [ ] nested", 0, "nested")
        r as ToggleResult.Success
        assertEquals("  * [x] nested", r.newContent)
    }

    @Test fun `mismatched text returns LineMismatch`() {
        val r = TaskToggler.toggle("- [ ] changed meanwhile", 0, "what widget showed")
        assertTrue(r is ToggleResult.LineMismatch)
    }

    @Test fun `non-task line returns LineMismatch`() {
        assertTrue(TaskToggler.toggle("just a paragraph", 0, "just a paragraph") is ToggleResult.LineMismatch)
    }

    @Test fun `out of range returns LineMismatch`() {
        assertTrue(TaskToggler.toggle("- [ ] a", 5, "a") is ToggleResult.LineMismatch)
    }

    @Test fun `crlf newlines are preserved`() {
        val r = TaskToggler.toggle("# t\r\n- [ ] win file\r\n", 1, "win file")
        r as ToggleResult.Success
        assertEquals("# t\r\n- [x] win file\r\n", r.newContent)
    }

    @Test fun `unicode task text round trips`() {
        val r = TaskToggler.toggle("- [ ] καφές ☕", 0, "καφές ☕")
        r as ToggleResult.Success
        assertEquals("- [x] καφές ☕", r.newContent)
    }
}
