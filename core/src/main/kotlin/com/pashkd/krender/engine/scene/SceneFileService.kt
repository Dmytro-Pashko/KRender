package com.pashkd.krender.engine.scene

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Platform-neutral text file access used by scene persistence.
 */
interface SceneFileService {
    /**
     * Writes [text] to [path], creating or replacing the target file content.
     */
    fun writeText(
        path: String,
        text: String,
    )

    /**
     * Reads and returns the full text content stored at [path].
     */
    fun readText(path: String): String

    /**
     * Ensures the directories required for [path] exist before a write occurs.
     */
    fun ensureDirectories(path: String)

    /**
     * Returns true when [path] can be read from the active storage backend.
     */
    fun exists(path: String): Boolean

    /**
     * Describes where [path] will be read from for diagnostics.
     */
    fun describeReadableSource(path: String): String = if (exists(path)) "readable" else "missing"
}

/**
 * JVM/classpath-backed fallback file service used by core-only helpers.
 */
object DefaultSceneFileService : SceneFileService {
    override fun writeText(
        path: String,
        text: String,
    ) {
        val normalized = normalize(path)
        ensureDirectories(normalized)
        Files.writeString(localPath(normalized), text, StandardCharsets.UTF_8)
    }

    override fun readText(path: String): String {
        val normalized = normalize(path)
        val localPath = localPath(normalized)
        if (Files.exists(localPath)) {
            return Files.readString(localPath, StandardCharsets.UTF_8)
        }
        val resource =
            Thread.currentThread().contextClassLoader?.getResourceAsStream(normalized)
                ?: DefaultSceneFileService::class.java.classLoader.getResourceAsStream(normalized)
        if (resource != null) {
            return resource.bufferedReader(StandardCharsets.UTF_8).use { reader -> reader.readText() }
        }
        throw IllegalArgumentException("File not found: '$normalized'")
    }

    override fun ensureDirectories(path: String) {
        val parent = localPath(normalize(path)).parent ?: return
        Files.createDirectories(parent)
    }

    override fun exists(path: String): Boolean {
        val normalized = normalize(path)
        return Files.exists(localPath(normalized)) ||
            Thread.currentThread().contextClassLoader?.getResource(normalized) != null ||
            DefaultSceneFileService::class.java.classLoader.getResource(normalized) != null
    }

    override fun describeReadableSource(path: String): String {
        val normalized = normalize(path)
        return when {
            Files.exists(localPath(normalized)) -> "local"
            Thread.currentThread().contextClassLoader?.getResource(normalized) != null -> "classpath"
            DefaultSceneFileService::class.java.classLoader.getResource(normalized) != null -> "classpath"
            else -> "missing"
        }
    }

    private fun localPath(path: String): Path = Paths.get(path)

    private fun normalize(path: String): String = path.trim().replace('\\', '/')
}
