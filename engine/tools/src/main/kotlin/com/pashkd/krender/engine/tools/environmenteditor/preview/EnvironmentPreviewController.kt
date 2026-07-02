package com.pashkd.krender.engine.tools.environmenteditor.preview

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.DynamicMesh
import com.pashkd.krender.engine.api.DynamicModel
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.render3d.Material

class EnvironmentPreviewController {
    val previewModel = AssetRef.model(MaterialSpheresPreviewRig.PreviewModelPath)
    val groundObject: EnvironmentPreviewObject = MaterialSpheresPreviewRig.groundPlane
    val groundModel: DynamicModel = buildGroundModel(groundObject)

    fun groundMaterial(): Material =
        Material(
            baseColor = groundObject.baseColor.copy(),
            metallic = groundObject.metallic,
            roughness = groundObject.roughness,
        )

    private fun buildGroundModel(ground: EnvironmentPreviewObject): DynamicModel {
        val halfWidth = ground.scale.x * 0.5f
        val halfDepth = ground.scale.z * 0.5f
        val y = ground.position.y
        return DynamicModel(
            id = "environment-preview-ground",
            revision = 1L,
            mesh =
                DynamicMesh(
                    positions =
                        floatArrayOf(
                            -halfWidth, y, -halfDepth,
                            halfWidth, y, -halfDepth,
                            halfWidth, y, halfDepth,
                            -halfWidth, y, halfDepth,
                        ),
                    normals =
                        floatArrayOf(
                            0f, 1f, 0f,
                            0f, 1f, 0f,
                            0f, 1f, 0f,
                            0f, 1f, 0f,
                        ),
                    uvs =
                        floatArrayOf(
                            0f, 0f,
                            1f, 0f,
                            1f, 1f,
                            0f, 1f,
                        ),
                    indices = intArrayOf(0, 1, 2, 0, 2, 3),
                ),
        )
    }

    companion object {
        val PreviewModelScale = Vec3(1.35f, 1.35f, 1.35f)
        val PreviewModelPosition = Vec3(0f, 0f, 0.35f)
        val GroundBaseColor = Color(0.42f, 0.42f, 0.42f, 1f)
    }
}
