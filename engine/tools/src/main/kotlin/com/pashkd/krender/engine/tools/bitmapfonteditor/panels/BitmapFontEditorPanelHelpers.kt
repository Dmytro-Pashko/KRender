package com.pashkd.krender.engine.tools.bitmapfonteditor.panels

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

internal fun textLine(value: String) {
    ImGui.textUnformatted(value)
}

internal fun wrappedTextLine(value: String) {
    ImGui.pushTextWrapPos(0f)
    ImGui.textUnformatted(value)
    ImGui.popTextWrapPos()
}

internal fun tooltipOnHover(value: String) {
    if (ImGui.isItemHovered()) {
        ImGui.beginTooltip()
        ImGui.textUnformatted(value)
        ImGui.endTooltip()
    }
}
