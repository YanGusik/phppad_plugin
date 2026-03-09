package com.github.yangusik.phppadplugin

import com.github.yangusik.phppadplugin.services.PhpPadHistoryEntry
import com.github.yangusik.phppadplugin.services.PhpPadSettings
import org.junit.Assert.*
import org.junit.Test

class PhpHistoryTest {

    private fun entry(code: String = "<?php echo 1;"): PhpPadHistoryEntry =
        PhpPadHistoryEntry().apply { this.code = code }

    // ── addHistoryTo ─────────────────────────────────────────────────────────

    @Test fun `addHistoryTo prepends entry to front`() {
        val list = mutableListOf(entry("old"))
        PhpPadSettings.addHistoryTo(list, entry("new"))
        assertEquals("new", list[0].code)
        assertEquals("old", list[1].code)
    }

    @Test fun `addHistoryTo stores up to MAX_HISTORY entries`() {
        val list = mutableListOf<PhpPadHistoryEntry>()
        repeat(PhpPadSettings.MAX_HISTORY) { list.add(entry("item $it")) }
        PhpPadSettings.addHistoryTo(list, entry("overflow"))
        assertEquals(PhpPadSettings.MAX_HISTORY, list.size)
    }

    @Test fun `addHistoryTo drops oldest when over limit`() {
        val list = mutableListOf<PhpPadHistoryEntry>()
        repeat(PhpPadSettings.MAX_HISTORY) { i -> list.add(entry("item $i")) }
        val newest = entry("newest")
        PhpPadSettings.addHistoryTo(list, newest)
        assertEquals(newest.id, list[0].id)
        assertEquals(PhpPadSettings.MAX_HISTORY, list.size)
    }

    @Test fun `addHistoryTo on empty list`() {
        val list = mutableListOf<PhpPadHistoryEntry>()
        PhpPadSettings.addHistoryTo(list, entry("first"))
        assertEquals(1, list.size)
        assertEquals("first", list[0].code)
    }

    @Test fun `addHistoryTo multiple calls preserve order newest-first`() {
        val list = mutableListOf<PhpPadHistoryEntry>()
        listOf("a", "b", "c").forEach { PhpPadSettings.addHistoryTo(list, entry(it)) }
        assertEquals(listOf("c", "b", "a"), list.map { it.code })
    }

    @Test fun `MAX_HISTORY is 200`() {
        assertEquals(200, PhpPadSettings.MAX_HISTORY)
    }
}
