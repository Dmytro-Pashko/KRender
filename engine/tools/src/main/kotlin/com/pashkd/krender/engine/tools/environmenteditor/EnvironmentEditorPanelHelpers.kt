package com.pashkd.krender.engine.tools.environmenteditor

import imgui.ImGui

internal fun tooltipOnHover(value: String) {
    if (ImGui.isItemHovered()) {
        ImGui.setTooltip(value)
    }
}
