package com.pashkd.krender.engine.ui.scene

/**
 * Shared `.krui` binding helpers used by runtime builders and future editor preview.
 *
 * Binding is intentionally minimal in Phase 1: text supports `{key}` placeholder
 * replacement from a string payload, and progress values read a single float key.
 * There is no expression language or typed payload system yet.
 */
object UiSceneBindings {
    private val PlaceholderPattern = Regex("\\{([A-Za-z0-9_.-]+)}")

    /**
     * Replaces `{key}` placeholders with payload values and leaves missing keys intact.
     */
    fun bindText(
        template: String,
        payload: Map<String, String>,
    ): String =
        PlaceholderPattern.replace(template) { match ->
            payload[match.groupValues[1]] ?: match.value
        }

    /**
     * Resolves a float binding from the payload or returns [fallback] when absent or invalid.
     */
    fun boundFloat(
        key: String?,
        payload: Map<String, String>,
        fallback: Float,
    ): Float =
        key
            ?.let(payload::get)
            ?.toFloatOrNull()
            ?: fallback
}
