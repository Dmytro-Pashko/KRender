package com.pashkd.krender.engine.uicomposer

/**
 * Immutable snapshot of Skin-defined names used by the UiComposer Inspector picker UX.
 *
 * This model belongs to editor style picking and backend-neutral composer state.
 * It exists so the Inspector can offer stable style/background dropdowns for the
 * currently loaded `.krui` document without importing LibGDX `Skin` types into
 * shared editor state. It intentionally does not edit Skin files, mutate Skin
 * contents, introduce asset ids, provide visual previews, or change runtime UI
 * behavior; the stored names are plain Skin entry names read from one path-based
 * Skin file.
 */
data class UiComposerSkinMetadata(
    val skinPath: String,
    val labelStyles: List<String> = emptyList(),
    val textButtonStyles: List<String> = emptyList(),
    val progressBarStyles: List<String> = emptyList(),
    val drawables: List<String> = emptyList(),
    val loadError: String? = null,
)
