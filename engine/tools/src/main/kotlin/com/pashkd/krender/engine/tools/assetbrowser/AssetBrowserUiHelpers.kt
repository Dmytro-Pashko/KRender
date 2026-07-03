package com.pashkd.krender.engine.tools.assetbrowser

import com.pashkd.krender.engine.assets.AssetCategory
import com.pashkd.krender.engine.assets.AssetDescriptor
import com.pashkd.krender.engine.assets.AssetType
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

internal fun assetBrowserFormatTimestamp(millis: Long): String = AssetBrowserTimestampFormatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

internal fun assetBrowserNormalizePath(path: String): String = path.replace('\\', '/').trim().trimStart('/')

internal fun assetBrowserIcon(asset: AssetDescriptor): String = AssetTypeIcons[asset.type] ?: AssetCategoryIcons.getValue(asset.category)

internal val SupportedBrowserCategories =
    setOf(
        AssetCategory.Model,
        AssetCategory.Texture,
        AssetCategory.Material,
        AssetCategory.Terrain,
        AssetCategory.Scene2D,
        AssetCategory.UI,
        AssetCategory.Environment,
        AssetCategory.Scene,
        AssetCategory.Other,
    )

private val AssetBrowserTimestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private val AssetTypeIcons =
    mapOf(
        AssetType.Scene2DSkin to "[Skin]",
        AssetType.Atlas to "[Atlas]",
        AssetType.Font to "[Font]",
        AssetType.HdrSource to "[HDR]",
    )

private val AssetCategoryIcons =
    mapOf(
        AssetCategory.Model to "[M]",
        AssetCategory.Texture to "[T]",
        AssetCategory.Material to "[Mat]",
        AssetCategory.Terrain to "[Ter]",
        AssetCategory.Scene2D to "[S2D]",
        AssetCategory.UI to "[UI]",
        AssetCategory.Environment to "[Env]",
        AssetCategory.Scene to "[Sc]",
        AssetCategory.Other to "[?]",
    )
