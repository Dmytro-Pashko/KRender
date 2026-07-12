package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewState

enum class EnvironmentResourceMode {
    Skybox,
    Irradiance,
    Radiance,
    BrdfLut,
    ImportedSkyboxSource,
}

enum class EnvironmentCubemapFace(
    val id: String,
    val aliases: Set<String>,
) {
    PosX("posx", setOf("px", "posx")),
    NegX("negx", setOf("nx", "negx")),
    PosY("posy", setOf("py", "posy")),
    NegY("negy", setOf("ny", "negy")),
    PosZ("posz", setOf("pz", "posz")),
    NegZ("negz", setOf("nz", "negz")),
    ;

    companion object {
        val ordered: List<EnvironmentCubemapFace> = entries

        fun fromIdOrAlias(value: String?): EnvironmentCubemapFace? {
            val normalized = value?.trim()?.lowercase().orEmpty()
            return ordered.firstOrNull { face -> normalized in face.aliases }
        }
    }
}

class EnvironmentResourceInspectorState {
    var selectedResourceMode: EnvironmentResourceMode = EnvironmentResourceMode.Skybox
    var selectedFace: EnvironmentCubemapFace? = EnvironmentCubemapFace.PosX
    var selectedRadianceMip: Int = 0
    var hoveredRegionOrFace: String? = null
    var selectedRegionOrFace: String? = EnvironmentCubemapFace.PosX.id
    val resourcePreviewState: TexturePreviewState = TexturePreviewState()
    val selectedResourcePreviewState: TexturePreviewState = TexturePreviewState()
}
