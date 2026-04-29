package com.pashkd.krender.engine.scene

/**
 * Platform-neutral text file access used by scene persistence.
 */
interface SceneFileService {
    /**
     * Writes [text] to [path], creating or replacing the target file content.
     */
    fun writeText(path: String, text: String)

    /**
     * Reads and returns the full text content stored at [path].
     */
    fun readText(path: String): String

    /**
     * Ensures the directories required for [path] exist before a write occurs.
     */
    fun ensureDirectories(path: String)
}

