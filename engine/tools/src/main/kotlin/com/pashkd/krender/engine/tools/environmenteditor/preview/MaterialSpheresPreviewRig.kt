package com.pashkd.krender.engine.tools.environmenteditor.preview

import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.Vec3

enum class EnvironmentPreviewShapeType {
    Sphere,
    Plane,
}

data class EnvironmentPreviewObject(
    val name: String,
    val shapeType: EnvironmentPreviewShapeType,
    val position: Vec3,
    val scale: Vec3 = Vec3.one(),
    val radius: Float = 0.5f,
    val baseColor: Color = Color.white(),
    val metallic: Float = 0f,
    val roughness: Float = 0.75f,
)

object MaterialSpheresPreviewRig {
    const val PreviewModelPath = "model/tests/MetalRoughSpheres.glb"

    val objects: List<EnvironmentPreviewObject> =
        listOf(
            EnvironmentPreviewObject(
                name = "Chrome Sphere",
                shapeType = EnvironmentPreviewShapeType.Sphere,
                position = Vec3(-1.3f, 0.55f, -1.8f),
                radius = 0.55f,
                baseColor = Color.white(),
                metallic = 1f,
                roughness = 0f,
            ),
            EnvironmentPreviewObject(
                name = "Matte Gray Sphere",
                shapeType = EnvironmentPreviewShapeType.Sphere,
                position = Vec3(1.3f, 0.55f, -1.8f),
                radius = 0.55f,
                baseColor = Color(0.55f, 0.55f, 0.55f, 1f),
                metallic = 0f,
                roughness = 0.7f,
            ),
            EnvironmentPreviewObject(
                name = "Metallic Roughness 0.00",
                shapeType = EnvironmentPreviewShapeType.Sphere,
                position = Vec3(-2f, 0.45f, 0f),
                radius = 0.45f,
                baseColor = Color.white(),
                metallic = 1f,
                roughness = 0f,
            ),
            EnvironmentPreviewObject(
                name = "Metallic Roughness 0.25",
                shapeType = EnvironmentPreviewShapeType.Sphere,
                position = Vec3(-1f, 0.45f, 0f),
                radius = 0.45f,
                baseColor = Color.white(),
                metallic = 1f,
                roughness = 0.25f,
            ),
            EnvironmentPreviewObject(
                name = "Metallic Roughness 0.50",
                shapeType = EnvironmentPreviewShapeType.Sphere,
                position = Vec3(0f, 0.45f, 0f),
                radius = 0.45f,
                baseColor = Color.white(),
                metallic = 1f,
                roughness = 0.5f,
            ),
            EnvironmentPreviewObject(
                name = "Metallic Roughness 0.75",
                shapeType = EnvironmentPreviewShapeType.Sphere,
                position = Vec3(1f, 0.45f, 0f),
                radius = 0.45f,
                baseColor = Color.white(),
                metallic = 1f,
                roughness = 0.75f,
            ),
            EnvironmentPreviewObject(
                name = "Metallic Roughness 1.00",
                shapeType = EnvironmentPreviewShapeType.Sphere,
                position = Vec3(2f, 0.45f, 0f),
                radius = 0.45f,
                baseColor = Color.white(),
                metallic = 1f,
                roughness = 1f,
            ),
            EnvironmentPreviewObject(
                name = "Dielectric Roughness 0.00",
                shapeType = EnvironmentPreviewShapeType.Sphere,
                position = Vec3(-2f, 0.45f, 1.45f),
                radius = 0.45f,
                baseColor = Color(0.72f, 0.72f, 0.72f, 1f),
                metallic = 0f,
                roughness = 0f,
            ),
            EnvironmentPreviewObject(
                name = "Dielectric Roughness 0.25",
                shapeType = EnvironmentPreviewShapeType.Sphere,
                position = Vec3(-1f, 0.45f, 1.45f),
                radius = 0.45f,
                baseColor = Color(0.72f, 0.72f, 0.72f, 1f),
                metallic = 0f,
                roughness = 0.25f,
            ),
            EnvironmentPreviewObject(
                name = "Dielectric Roughness 0.50",
                shapeType = EnvironmentPreviewShapeType.Sphere,
                position = Vec3(0f, 0.45f, 1.45f),
                radius = 0.45f,
                baseColor = Color(0.72f, 0.72f, 0.72f, 1f),
                metallic = 0f,
                roughness = 0.5f,
            ),
            EnvironmentPreviewObject(
                name = "Dielectric Roughness 0.75",
                shapeType = EnvironmentPreviewShapeType.Sphere,
                position = Vec3(1f, 0.45f, 1.45f),
                radius = 0.45f,
                baseColor = Color(0.72f, 0.72f, 0.72f, 1f),
                metallic = 0f,
                roughness = 0.75f,
            ),
            EnvironmentPreviewObject(
                name = "Dielectric Roughness 1.00",
                shapeType = EnvironmentPreviewShapeType.Sphere,
                position = Vec3(2f, 0.45f, 1.45f),
                radius = 0.45f,
                baseColor = Color(0.72f, 0.72f, 0.72f, 1f),
                metallic = 0f,
                roughness = 1f,
            ),
        )

    val chromeSphere: EnvironmentPreviewObject
        get() = objects.first { it.name == "Chrome Sphere" }

    val matteSphere: EnvironmentPreviewObject
        get() = objects.first { it.name == "Matte Gray Sphere" }

    val metallicLadder: List<EnvironmentPreviewObject>
        get() = objects.filter { it.name.startsWith("Metallic Roughness") }

    val dielectricLadder: List<EnvironmentPreviewObject>
        get() = objects.filter { it.name.startsWith("Dielectric Roughness") }
}
