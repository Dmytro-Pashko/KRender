package com.pashkd.krender.engine.terrain

import com.pashkd.krender.engine.ui.UiPanel
import imgui.ImGui
import imgui.SliderFlag
import imgui.api.slider
import imgui.dsl

class TerrainEditorPanel(
    private val state: TerrainEditorState,
) : UiPanel {
    private var resolutionIndex: Int = 1

    override fun draw() {
        syncResolutionIndex()

        with(dsl) {
            window("Terrain") {
                drawBrushSection()
                ImGui.separator()
                drawTerrainSection()
                ImGui.separator()
                drawLayersSection()
                ImGui.separator()
                drawDebugSection()
            }
        }
    }

    private fun drawBrushSection() {
        ImGui.text("Brush")
        slider("Radius", state::brushRadius, 1f, 64f, "%.1f", SliderFlag.AlwaysClamp)
        slider("Strength", state::brushStrength, 0.1f, 32f, "%.1f", SliderFlag.AlwaysClamp)
        slider("Falloff", state::brushFalloff, 1f, 4f, "%.2f", SliderFlag.AlwaysClamp)

        brushModeButton("Raise", TerrainBrushMode.Raise)
        ImGui.sameLine()
        brushModeButton("Lower", TerrainBrushMode.Lower)
        ImGui.sameLine()
        brushModeButton("Flatten", TerrainBrushMode.Flatten)
        ImGui.sameLine()
        brushModeButton("Smooth", TerrainBrushMode.Smooth)
        brushModeButton("PaintLayer", TerrainBrushMode.PaintLayer)
    }

    private fun drawTerrainSection() {
        ImGui.text("Terrain")
        if (slider("Resolution", ::resolutionIndex, 0, RESOLUTION_OPTIONS.lastIndex, RESOLUTION_LABELS[resolutionIndex], SliderFlag.AlwaysClamp)) {
            state.terrainResolution = RESOLUTION_OPTIONS[resolutionIndex]
        }
        slider("Vertex spacing", state::vertexSpacing, 0.25f, 4f, "%.2f", SliderFlag.AlwaysClamp)
        dsl.button("Regenerate terrain") {
            state.regenerateRequested = true
        }
    }

    private fun drawLayersSection() {
        ImGui.text("Layers")
        state.layers.forEach { layer ->
            if (ImGui.selectable(layer.name, layer.id == state.selectedLayerId)) {
                state.selectedLayerId = layer.id
            }
        }

        dsl.button("Add layer") {
            state.addLayerRequested = true
        }
        ImGui.sameLine()
        dsl.button("Remove layer") {
            state.removeLayerRequested = true
        }
    }

    private fun drawDebugSection() {
        ImGui.text("Debug")
        ImGui.text("vertices: ${state.vertices}")
        ImGui.text("triangles: ${state.triangles}")
        ImGui.text("active mode: ${state.brushMode.name}")
    }

    private fun brushModeButton(label: String, mode: TerrainBrushMode) {
        dsl.button(label) {
            state.brushMode = mode
        }
    }

    private fun syncResolutionIndex() {
        resolutionIndex = RESOLUTION_OPTIONS.indexOf(state.terrainResolution)
            .takeIf { it >= 0 }
            ?: DEFAULT_RESOLUTION_INDEX
    }

    companion object {
        private val RESOLUTION_OPTIONS = intArrayOf(64, 128, 256)
        private val RESOLUTION_LABELS = arrayOf("64", "128", "256")
        private const val DEFAULT_RESOLUTION_INDEX = 1
    }
}
