package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.Color

object EnvironmentEditorConfig {
    val defaultBackgroundColor = Color(0.08f, 0.09f, 0.11f, 1f)

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

data class EnvironmentEditorTestModel(
    val id: String,
    val displayName: String,
    val assetPath: String,
)
