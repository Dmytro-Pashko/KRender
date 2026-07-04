package com.pashkd.krender.engine.tools.environmenteditor

import imgui.ImGui

internal fun tooltipOnHover(value: String) {
    if (ImGui.isItemHovered()) {
        ImGui.setTooltip(value)
    }
}

internal fun readBuffer(buffer: ByteArray): String {
    val end = buffer.indexOf(0).let { if (it >= 0) it else buffer.size }
    return String(buffer, 0, end, Charsets.UTF_8)
}

internal fun writeBuffer(
    buffer: ByteArray,
    value: String,
) {
    buffer.fill(0)
    value.encodeToByteArray()
        .copyInto(buffer, endIndex = minOf(buffer.size - 1, value.encodeToByteArray().size))
}
