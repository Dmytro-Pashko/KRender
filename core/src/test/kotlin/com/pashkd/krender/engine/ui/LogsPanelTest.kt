package com.pashkd.krender.engine.ui

import com.pashkd.krender.engine.api.LogEntry
import com.pashkd.krender.engine.api.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogsPanelTest {
    @Test
    fun `log panel display line is capped for very large entries`() {
        val entry = LogEntry(
            level = LogLevel.Info,
            tag = "LongLog",
            message = "x".repeat(25_000),
            frame = 1L,
            threadName = "test",
            timestampMillis = 0L,
        )

        val formatted = formatLogEntryForPanel(entry)

        assertEquals(LogsPanel.MaxDisplayedEntryLength, formatted.length)
        assertTrue(formatted.endsWith("..."))
    }
}
