package com.pashkd.krender.engine.tools.texturemanager

import java.io.File

object TextureManagerPathValidator {
    fun resolveAssetDirectory(
        assetRoot: File,
        relativePath: String,
    ): File? {
        val normalized = normalizeRelativePath(relativePath) ?: return null
        val rootCanonical = assetRoot.canonicalFile
        val targetCanonical = File(rootCanonical, normalized).canonicalFile
        return targetCanonical.takeIf { isInsideRoot(rootCanonical, it) }
    }

    fun resolveAssetFile(
        assetRoot: File,
        relativeDirectory: String,
        fileName: String,
    ): File? {
        if (fileName.isBlank()) return null
        val directory = resolveAssetDirectory(assetRoot, relativeDirectory) ?: return null
        val target = File(directory, fileName).canonicalFile
        return target.takeIf { isInsideRoot(assetRoot.canonicalFile, it) }
    }

    fun isInsideRoot(
        root: File,
        target: File,
    ): Boolean {
        val normalizedRoot = root.canonicalFile.toPath().normalize()
        val normalizedTarget = target.canonicalFile.toPath().normalize()
        return normalizedTarget.startsWith(normalizedRoot)
    }

    private fun normalizeRelativePath(path: String): String? {
        val trimmed = path.trim().replace('\\', '/').trim('/')
        if (trimmed.isBlank()) return null
        if (trimmed.startsWith("../") || trimmed == "..") return null
        if (File(trimmed).isAbsolute) return null
        return trimmed
    }
}
