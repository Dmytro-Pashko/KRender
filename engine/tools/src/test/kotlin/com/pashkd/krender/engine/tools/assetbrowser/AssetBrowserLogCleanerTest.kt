package com.pashkd.krender.engine.tools.assetbrowser

import java.nio.file.Files
import kotlin.io.path.createDirectory
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssetBrowserLogCleanerTest {
    @Test
    fun `clean removes saved log files and leaves directories intact`() {
        val logsDirectory = Files.createTempDirectory("krender-asset-browser-logs")
        val firstLog = logsDirectory.resolve("runtime-old.log")
        val secondLog = logsDirectory.resolve("runtime-new.log")
        val archive = logsDirectory.resolve("archive").createDirectory()
        firstLog.writeText("old")
        secondLog.writeText("new")

        val result = AssetBrowserLogCleaner.clean(logsDirectory)

        assertEquals(2, result.deletedCount)
        assertTrue(result.failures.isEmpty())
        assertFalse(firstLog.exists())
        assertFalse(secondLog.exists())
        assertTrue(archive.exists())
    }

    @Test
    fun `clean succeeds when logs directory is missing`() {
        val missingDirectory = Files.createTempDirectory("krender-asset-browser-logs-missing").resolve("logs")

        val result = AssetBrowserLogCleaner.clean(missingDirectory)

        assertEquals(0, result.deletedCount)
        assertTrue(result.failures.isEmpty())
    }
}
