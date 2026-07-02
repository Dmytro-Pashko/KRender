package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.assets.environment.EnvironmentColor

/**
 * Stable defaults and bundled preview assets used by Environment Editor.
 *
 * Keeping these values together makes the preview deterministic and provides a
 * single future integration point for persisted per-tool preferences.
 */
object EnvironmentEditorConfig {
    val defaultBackgroundColor = EnvironmentColor(0.08f, 0.09f, 0.11f, 1f)

    const val defaultCameraDistance = 6f
    const val defaultCameraYawDegrees = 35f
    const val defaultCameraPitchDegrees = 20f

    val testModels =
        listOf(
            EnvironmentEditorTestModel(
                id = "metal-rough-spheres",
                displayName = "Metal Rough Spheres",
                assetPath = "model/tests/MetalRoughSpheres.glb",
            ),
        )

    val defaultPreviewModel: EnvironmentEditorTestModel
        get() = testModels.first()
}

/** A model bundled specifically for visually evaluating environment lighting. */
data class EnvironmentEditorTestModel(
    val id: String,
    val displayName: String,
    val assetPath: String,
)
