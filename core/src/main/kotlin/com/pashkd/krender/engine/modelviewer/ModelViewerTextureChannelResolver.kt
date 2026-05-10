package com.pashkd.krender.engine.modelviewer

import com.pashkd.krender.engine.api.ModelAssetInfo
import com.pashkd.krender.engine.api.ModelTextureSlotInfo

internal fun isModelViewerTextureDebugMode(mode: ModelViewerDebugMode): Boolean = when (mode) {
    ModelViewerDebugMode.BaseColor,
    ModelViewerDebugMode.Normal,
    ModelViewerDebugMode.Emission,
    ModelViewerDebugMode.MetallicRoughness,
    ModelViewerDebugMode.Occlusion,
    ModelViewerDebugMode.Alpha,
    -> true

    ModelViewerDebugMode.None,
    ModelViewerDebugMode.UvChecker,
    -> false
}

internal fun matchingModelViewerTextureSlots(
    info: ModelAssetInfo?,
    mode: ModelViewerDebugMode,
    selectedMaterialIndex: Int?,
): List<ModelTextureSlotInfo> {
    if (!isModelViewerTextureDebugMode(mode)) return emptyList()
    val aliases = modelViewerTextureAliasesFor(mode)
    return info?.materials
        .orEmpty()
        .asSequence()
        .filter { material -> selectedMaterialIndex == null || material.index == selectedMaterialIndex }
        .flatMap { material -> material.textureSlots.asSequence() }
        .filter { slot -> normalizeModelViewerTextureChannel(slot.channel) in aliases }
        .toList()
}

internal fun preferredModelViewerTextureChannel(
    info: ModelAssetInfo?,
    mode: ModelViewerDebugMode,
    selectedMaterialIndex: Int?,
): String? = matchingModelViewerTextureSlots(info, mode, selectedMaterialIndex).firstOrNull()?.channel

internal fun hasModelViewerTextureChannel(
    info: ModelAssetInfo?,
    mode: ModelViewerDebugMode,
    selectedMaterialIndex: Int?,
): Boolean = matchingModelViewerTextureSlots(info, mode, selectedMaterialIndex)
    .any { slot -> !slot.texturePath.isNullOrBlank() }

internal fun modelViewerTextureAliasesFor(mode: ModelViewerDebugMode): Set<String> = when (mode) {
    ModelViewerDebugMode.BaseColor -> setOf("basecolor", "basecolortexture", "diffuse", "diffusetexture")
    ModelViewerDebugMode.Normal -> setOf("normal", "normaltexture", "bump")
    ModelViewerDebugMode.Emission -> setOf("emission", "emissive", "emissivetexture")
    ModelViewerDebugMode.MetallicRoughness -> setOf("metallicroughness", "metallic", "roughness", "orm")
    ModelViewerDebugMode.Occlusion -> setOf("occlusion", "ao", "ambientocclusion")
    ModelViewerDebugMode.Alpha -> setOf("alpha", "opacity", "alphatexture")
    ModelViewerDebugMode.None,
    ModelViewerDebugMode.UvChecker,
    -> emptySet()
}

private fun normalizeModelViewerTextureChannel(channel: String?): String =
    channel.orEmpty()
        .lowercase()
        .filter { char -> char.isLetterOrDigit() }
