package com.pashkd.krender.engine.tools.environmenteditor

enum class SkyboxImportLayoutPreset {
    CubeCross4x3,
    HorizontalRow6x1,
    VerticalColumn1x6,
    Custom,
}

data class SkyboxFaceTransform(
    val rotateDegrees: Int = 0,
    val flipX: Boolean = false,
    val flipY: Boolean = false,
)

data class SkyboxImportRegion(
    val face: EnvironmentCubemapFace,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val transform: SkyboxFaceTransform = SkyboxFaceTransform(),
)

class SkyboxImportState {
    var sourcePath: String = ""
    var layoutPreset: SkyboxImportLayoutPreset = SkyboxImportLayoutPreset.CubeCross4x3
    var outputDirectory: String = "skybox"
    var selectedFace: EnvironmentCubemapFace = EnvironmentCubemapFace.PosX
    var regions: Map<EnvironmentCubemapFace, SkyboxImportRegion> = emptyMap()
}
