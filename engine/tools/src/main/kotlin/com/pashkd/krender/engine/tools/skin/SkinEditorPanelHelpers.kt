package com.pashkd.krender.engine.tools.skin

import imgui.ImGui
import java.io.File
import java.nio.charset.StandardCharsets

@Suppress("TopLevelPropertyNaming")
internal val PreviewScales = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f)

@Suppress("TopLevelPropertyNaming")
internal val FontPreviewScales = listOf(0.5f, 1f, 1.5f, 2f)

@Suppress("TopLevelPropertyNaming")
internal const val MaxInspectorAtlasRegions = 100

@Suppress("TopLevelPropertyNaming")
internal const val ResourceSearchWidth = 300f

@Suppress("TopLevelPropertyNaming")
internal const val MaxInlineResourcePreviewHeight = 320f

@Suppress("TopLevelPropertyNaming")
internal const val MinInlineResourcePreviewScale = 0.05f

@Suppress("TopLevelPropertyNaming")
internal const val FontPreviewTextHeight = 96f

@Suppress("TopLevelPropertyNaming")
internal const val ResourcePreviewViewportHeight = 360f

@Suppress("TopLevelPropertyNaming")
internal const val ResourcePreviewClickDragThreshold = 4f

@Suppress("TopLevelPropertyNaming")
internal const val MinResourcePreviewGridScreenSpacing = 8f

internal fun formatPreviewScale(scale: Float): String = "${(scale * 100f).toInt()}%"

internal fun formatResourcePreviewZoom(zoomMode: SkinResourceVisualPreviewZoomMode): String =
    when (zoomMode) {
        SkinResourceVisualPreviewZoomMode.Fit -> "Fit"
        SkinResourceVisualPreviewZoomMode.Percent50 -> "50%"
        SkinResourceVisualPreviewZoomMode.Percent100 -> "100%"
        SkinResourceVisualPreviewZoomMode.Percent200 -> "200%"
        SkinResourceVisualPreviewZoomMode.Custom -> "Custom"
    }

internal fun parseResourceColor(values: Map<String, String>): FloatArray? {
    val color = SkinColorValueParser.parse(values) ?: return null
    return floatArrayOf(color.r, color.g, color.b, color.a)
}

internal fun selectedResourceSummary(state: SkinEditorState): String? =
    state.selectedResourceKey?.let { key ->
        val resource =
            state.loadResult.resourceIndex.resources
                .firstOrNull { it.key == key }
                ?: return@let "${key.category}.${key.name}"
        "${resource.category}.${resource.name}${if (resource.resolved) "" else " (missing)"}"
    }

internal fun drawSelectedResourcePreviewHint(state: SkinEditorState) {
    val selectedResourceKey = state.selectedResourceKey ?: return
    val resource =
        state.loadResult.resourceIndex.resources
            .firstOrNull { it.key == selectedResourceKey } ?: return
    if (resource.category in AvailablePreviewCategories) {
        ImGui.textWrapped("Visual preview is available in the Resources preview section.")
    }
}

internal fun readBuffer(buffer: ByteArray): String {
    val end = buffer.indexOf(0).takeIf { it >= 0 } ?: buffer.size
    return String(buffer, 0, end, StandardCharsets.UTF_8)
}

internal fun writeBuffer(
    buffer: ByteArray,
    value: String,
) {
    buffer.fill(0)
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    bytes.copyInto(buffer, endIndex = bytes.size.coerceAtMost(buffer.size - 1))
}

internal fun safeTextWrapped(value: String) {
    ImGui.textWrapped(value.toAsciiSafeText())
}

internal fun String.toAsciiSafeText(): String =
    buildString(length) {
        for (character in this@toAsciiSafeText) {
            when {
                character == '\n' || character == '\r' || character == '\t' -> append(character)
                character.code in 32..126 -> append(character)
                else ->
                    append("\\u").append(
                        character.code
                            .toString(16)
                            .uppercase()
                            .padStart(4, '0'),
                    )
            }
        }
    }

internal fun String.shortSource(): String {
    val normalized = substringBefore('#')
    val suffix = substringAfter('#', missingDelimiterValue = "")
    val fileName = File(normalized).name.ifBlank { normalized }
    return if (suffix.isBlank()) fileName else "$fileName#$suffix"
}

internal fun packImColor(
    red: Int,
    green: Int,
    blue: Int,
    alpha: Int = 255,
): Int =
    (alpha.coerceIn(0, 255) shl 24) or
        (blue.coerceIn(0, 255) shl 16) or
        (green.coerceIn(0, 255) shl 8) or
        red.coerceIn(0, 255)

private val AvailablePreviewCategories =
    setOf(
        SkinResourceCategory.Atlas,
        SkinResourceCategory.AtlasRegion,
        SkinResourceCategory.Texture,
        SkinResourceCategory.Font,
        SkinResourceCategory.Color,
    )
