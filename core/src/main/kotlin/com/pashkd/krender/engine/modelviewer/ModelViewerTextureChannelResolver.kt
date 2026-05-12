package com.pashkd.krender.engine.modelviewer

import com.pashkd.krender.engine.api.MaterialDebugMode
import com.pashkd.krender.engine.api.MaterialDebugTextureRef
import com.pashkd.krender.engine.api.MaterialTextureRef
import com.pashkd.krender.engine.api.ModelAssetInfo
import com.pashkd.krender.engine.api.ModelTextureSlotInfo
import com.pashkd.krender.engine.api.TextureDebugComponent

internal fun isModelViewerTextureDebugMode(mode: MaterialDebugMode): Boolean = when (mode) {
    MaterialDebugMode.BaseColor,
    MaterialDebugMode.Normal,
    MaterialDebugMode.Emission,
    MaterialDebugMode.Roughness,
    MaterialDebugMode.Metallic,
    MaterialDebugMode.MetallicRoughnessPacked,
    MaterialDebugMode.Occlusion,
    MaterialDebugMode.Alpha,
    -> true

    MaterialDebugMode.None,
    MaterialDebugMode.UvChecker,
    -> false
}

internal fun matchingModelViewerTextureSlots(
    info: ModelAssetInfo?,
    mode: MaterialDebugMode,
    selectedMaterialIndex: Int?,
    selectedTextureChannel: String? = null,
): List<ModelTextureSlotInfo> {
    if (!isModelViewerTextureDebugMode(mode)) return emptyList()
    val aliases = modelViewerTextureAliasesFor(mode)
    val selectedChannel = normalizeModelViewerTextureChannel(selectedTextureChannel)
    return info?.materials
        .orEmpty()
        .asSequence()
        .filter { material -> selectedMaterialIndex == null || material.index == selectedMaterialIndex }
        .flatMap { material -> material.textureSlots.asSequence() }
        .filter { slot ->
            val channel = normalizeModelViewerTextureChannel(slot.channel)
            if (selectedChannel.isNotBlank()) channel == selectedChannel else channel in aliases
        }
        .toList()
}

internal fun resolvedModelViewerDebugTextureRefs(
    info: ModelAssetInfo?,
    mode: MaterialDebugMode,
    selectedMaterialIndex: Int?,
    selectedTextureChannel: String?,
): List<MaterialDebugTextureRef> =
    matchingModelViewerTextureSlots(
        info = info,
        mode = mode,
        selectedMaterialIndex = selectedMaterialIndex,
        selectedTextureChannel = selectedTextureChannel,
    ).mapNotNull { slot ->
        val texturePath = slot.texturePath?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        MaterialDebugTextureRef(
            materialIndex = slot.materialIndex,
            materialId = slot.materialId,
            texture = MaterialTextureRef(
                id = texturePath,
                channel = slot.channel,
                uvChannel = modelViewerUvChannelIndex(slot.uvChannel) ?: 0,
            ),
            component = textureDebugComponentFor(mode),
        )
    }

internal fun preferredModelViewerTextureChannel(
    info: ModelAssetInfo?,
    mode: MaterialDebugMode,
    selectedMaterialIndex: Int?,
): String? = matchingModelViewerTextureSlots(info, mode, selectedMaterialIndex).firstOrNull()?.channel

internal fun hasModelViewerTextureChannel(
    info: ModelAssetInfo?,
    mode: MaterialDebugMode,
    selectedMaterialIndex: Int?,
): Boolean = matchingModelViewerTextureSlots(info, mode, selectedMaterialIndex)
    .any { slot -> !slot.texturePath.isNullOrBlank() }

internal fun modelViewerTextureAliasesFor(mode: MaterialDebugMode): Set<String> = when (mode) {
    MaterialDebugMode.BaseColor -> setOf("basecolor", "basecolortexture", "diffuse", "diffusetexture")
    MaterialDebugMode.Normal -> setOf("normal", "normaltexture", "bump")
    MaterialDebugMode.Emission -> setOf("emission", "emissive", "emissivetexture")
    MaterialDebugMode.Roughness -> setOf(
        "metallicroughness",
        "metallicroughnesstexture",
        "roughness",
        "roughnesstexture",
        "orm",
    )
    MaterialDebugMode.Metallic -> setOf(
        "metallicroughness",
        "metallicroughnesstexture",
        "metallic",
        "metallictexture",
        "orm",
    )
    MaterialDebugMode.MetallicRoughnessPacked -> setOf(
        "metallicroughness",
        "metallicroughnesstexture",
        "orm",
    )
    MaterialDebugMode.Occlusion -> setOf("occlusion", "ao", "ambientocclusion")
    MaterialDebugMode.Alpha -> setOf("alpha", "opacity", "alphatexture", "basecolor", "basecolortexture", "diffuse")
    MaterialDebugMode.None,
    MaterialDebugMode.UvChecker,
    -> emptySet()
}

private fun textureDebugComponentFor(mode: MaterialDebugMode): TextureDebugComponent =
    when (mode) {
        MaterialDebugMode.Roughness -> TextureDebugComponent.G
        MaterialDebugMode.Metallic -> TextureDebugComponent.B
        MaterialDebugMode.Occlusion -> TextureDebugComponent.R
        MaterialDebugMode.Alpha -> TextureDebugComponent.A
        MaterialDebugMode.MetallicRoughnessPacked,
        MaterialDebugMode.BaseColor,
        MaterialDebugMode.Normal,
        MaterialDebugMode.Emission,
        -> TextureDebugComponent.RGB

        MaterialDebugMode.None,
        MaterialDebugMode.UvChecker,
        -> TextureDebugComponent.RGB
    }

private fun normalizeModelViewerTextureChannel(channel: String?): String =
    channel.orEmpty()
        .lowercase()
        .filter { char -> char.isLetterOrDigit() }

private fun modelViewerUvChannelIndex(channel: String?): Int? {
    val trimmed = channel?.trim().orEmpty()
    return when {
        trimmed.startsWith("UV", ignoreCase = true) -> trimmed.drop(2).toIntOrNull()
        trimmed.startsWith("TEXCOORD_", ignoreCase = true) -> trimmed.substringAfter('_').toIntOrNull()
        else -> trimmed.toIntOrNull()
    }
}
