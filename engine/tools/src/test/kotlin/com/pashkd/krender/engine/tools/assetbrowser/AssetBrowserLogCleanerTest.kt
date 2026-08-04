package com.pashkd.krender.engine.tools.assetbrowser

import java.nio.file.Files
import kotlin.io.path.createDirectory
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssetBrowserLogCleanerTest {
    @Test
    fun `clean removes saved log files directories and nested files`() {
        val logsDirectory = Files.createTempDirectory("krender-asset-browser-logs")
        val firstLog = logsDirectory.resolve("runtime-old.log")
        val secondLog = logsDirectory.resolve("runtime-new.log")
        val archive = logsDirectory.resolve("archive").createDirectory()
        val nestedDirectory = logsDirectory.resolve("nested/deep").createDirectories()
        val nestedLog = nestedDirectory.resolve("runtime-nested.log")
        firstLog.writeText("old")
        secondLog.writeText("new")
        nestedLog.writeText("nested")

        val result = AssetBrowserLogCleaner.clean(logsDirectory)

        assertEquals(6, result.deletedCount)
        assertTrue(result.failures.isEmpty())
        assertFalse(firstLog.exists())
        assertFalse(secondLog.exists())
        assertFalse(archive.exists())
        assertFalse(nestedLog.exists())
        assertFalse(logsDirectory.resolve("nested").exists())
        assertTrue(logsDirectory.exists())
    }

    @Test
    fun `clean succeeds when logs directory is missing`() {
        val missingDirectory = Files.createTempDirectory("krender-asset-browser-logs-missing").resolve("logs")

        val result = AssetBrowserLogCleaner.clean(missingDirectory)

        assertEquals(0, result.deletedCount)
        assertTrue(result.failures.isEmpty())
    }
}
