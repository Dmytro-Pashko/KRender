package com.pashkd.krender.engine.tools.texturemanager.ui

import imgui.ImGui
import java.nio.charset.StandardCharsets

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
    val count = minOf(bytes.size, buffer.size - 1)
    bytes.copyInto(buffer, endIndex = count)
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

internal fun formatBytes(bytes: Long): String =
    when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024f)
        else -> "%.2f MB".format(bytes / (1024f * 1024f))
    }

internal fun textLine(value: String) {
    ImGui.textUnformatted(value)
}

