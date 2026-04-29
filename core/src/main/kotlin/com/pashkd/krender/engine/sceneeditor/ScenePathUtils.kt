package com.pashkd.krender.engine.sceneeditor

/**
 * Shared Scene Editor path handling for scene persistence actions.
 */
object ScenePathUtils {
    fun normalizeScenePath(path: String): String {
        val trimmed = path.trim().replace('\\', '/')
        require(trimmed.isNotBlank()) { "Scene path cannot be blank" }
        val leaf = trimmed.substringAfterLast('/')
        val extension = leaf.substringAfterLast('.', missingDelimiterValue = "")
        if (extension.isBlank()) {
            return "$trimmed.krscene"
        }
        if (extension.equals("krscene", ignoreCase = true) || extension.equals("json", ignoreCase = true)) {
            return trimmed
        }
        return trimmed.removeSuffix(".$extension") + ".krscene"
    }
}
