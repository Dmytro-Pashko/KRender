package com.pashkd.krender.engine.tools.assetbrowser

import com.pashkd.krender.engine.assets.*

import imgui.ImGui
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun assetBrowserTextLine(value: String) {
    ImGui.textUnformatted(value)
}

internal fun assetBrowserReadBuffer(buffer: ByteArray): String {
    val length = buffer.indexOf(0).takeIf { it >= 0 } ?: buffer.size
    return String(buffer, 0, length, StandardCharsets.UTF_8)
}

internal fun assetBrowserWriteBuffer(
    buffer: ByteArray,
    value: String,
) {
    buffer.fill(0)
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    val length = minOf(bytes.size, buffer.size - 1)
    bytes.copyInto(buffer, endIndex = length)
}

internal fun assetBrowserFormatByteCount(bytes: Long): String =
    when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024f)
        else -> "%.2f MB".format(bytes / (1024f * 1024f))
    }

internal fun assetBrowserFormatTimestamp(millis: Long): String =
    AssetBrowserTimestampFormatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

internal fun assetBrowserNormalizePath(path: String): String = path.replace('\\', '/').trim().trimStart('/')

internal fun assetBrowserIcon(asset: AssetDescriptor): String =
    when {
        asset.type == AssetType.Scene2DSkin -> "[Skin]"
        else ->
            when (asset.category) {
                AssetCategory.Model -> "[M]"
                AssetCategory.Texture -> "[T]"
                AssetCategory.Skybox -> "[Sky]"
                AssetCategory.Material -> "[Mat]"
                AssetCategory.Terrain -> "[Ter]"
                AssetCategory.UI -> "[UI]"
                AssetCategory.Scene -> "[Sc]"
                AssetCategory.Other -> "[?]"
            }
    }

internal val SupportedBrowserCategories =
    setOf(
        AssetCategory.Model,
        AssetCategory.Texture,
        AssetCategory.Skybox,
        AssetCategory.Material,
        AssetCategory.Terrain,
        AssetCategory.UI,
        AssetCategory.Scene,
        AssetCategory.Other,
    )

private val AssetBrowserTimestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
