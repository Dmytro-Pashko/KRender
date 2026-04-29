package com.pashkd.krender.engine.sceneeditor

/**
 * Shared Scene Editor path handling for scene persistence actions.
 */
object ScenePathUtils {
    fun normalizeScenePath(path: String): String {
        // Trimming whitespace and normalizing path separators.
        val trimmed = path.trim().replace('\\', '/')
        // Validating that the normalized path is not blank.
        require(trimmed.isNotBlank()) { "Scene path cannot be blank" }
        // Extracting the file name portion from the normalized path.
        val leaf = trimmed.substringAfterLast('/')
        // Reading the file extension, if one is present.
        val extension = leaf.substringAfterLast('.', missingDelimiterValue = "")
        // Appending the default scene extension when none is provided.
        if (extension.isBlank()) {
            return "$trimmed.krscene"
        }
        // Preserving supported scene file extensions as-is.
        if (extension.equals("krscene", ignoreCase = true) || extension.equals("json", ignoreCase = true)) {
            return trimmed
        }
        // Replacing unsupported extensions with the scene extension.
        return trimmed.removeSuffix(".$extension") + ".krscene"
    }
}
