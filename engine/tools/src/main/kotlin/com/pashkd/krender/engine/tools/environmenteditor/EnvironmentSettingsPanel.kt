package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.assets.environment.BackgroundMode
import com.pashkd.krender.engine.assets.environment.EnvironmentAsset
import com.pashkd.krender.engine.assets.environment.EnvironmentColor
import com.pashkd.krender.engine.assets.environment.EnvironmentSettings
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.beginImGuiPanel
import imgui.ImGui
import imgui.SliderFlag
import imgui.api.colorEdit4
import imgui.api.slider

/**
 * Editable settings panel for environment runtime parameters.
 *
 * Uses mutable holder objects so the imgui `slider` extension can write back
 * through `KMutableProperty0<Float>`.
 */
class EnvironmentSettingsPanel(
    private val state: EnvironmentEditorState,
    private val logger: Logger,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    private val holder = FloatHolder()

    override fun draw() {
        val layout = layoutConfig.panels.getValue(EnvironmentEditorPanelIds.Settings)
        val expanded = beginImGuiPanel(EnvironmentEditorPanelIds.Settings, layout, layoutTracker)
        eventLogger.observe(EnvironmentEditorPanelIds.Settings, layout.title)
        if (!expanded) {
            ImGui.end()
            return
        }
        val env = state.environment
        if (env == null) {
            ImGui.text("No environment loaded.")
            ImGui.end()
            return
        }
        drawSettingsEditor(env)
        ImGui.end()
    }

    private fun drawSettingsEditor(env: EnvironmentAsset) {
        val s = env.settings
        ImGui.separator()
        ImGui.text("1. Runtime Settings")

        holder.value = s.exposure
        if (slider("Exposure##env_exposure", holder::value, 0.01f, 10f, "%.2f", SliderFlag.AlwaysClamp)) {
            updateSettings(env) { it.copy(exposure = holder.value) }
        }
        tooltipOnHover("Adjusts overall environment preview brightness.")

        holder.value = s.rotationDegrees
        if (slider("Rotation##env_rotation", holder::value, 0f, 360f, "%.1f deg", SliderFlag.AlwaysClamp)) {
            updateSettings(env) { it.copy(rotationDegrees = holder.value) }
        }
        tooltipOnHover("Rotates the environment around the vertical axis.")

        holder.value = s.diffuseIntensity
        if (slider("Diffuse Intensity##env_diffuse_int", holder::value, 0f, 5f, "%.2f", SliderFlag.AlwaysClamp)) {
            updateSettings(env) { it.copy(diffuseIntensity = holder.value) }
        }
        tooltipOnHover("Controls diffuse environment lighting contribution.")

        holder.value = s.specularIntensity
        if (slider("Specular Intensity##env_specular_int", holder::value, 0f, 1f, "%.2f", SliderFlag.AlwaysClamp)) {
            updateSettings(env) { it.copy(specularIntensity = holder.value) }
        }
        tooltipOnHover("Controls specular reflections from the environment.")

        ImGui.separator()
        ImGui.text("2. Background Type")
        if (ImGui.beginCombo("Display Background##env_bg_mode", backgroundModeLabel(s.backgroundMode))) {
            BackgroundMode.entries.forEach { mode ->
                val selected = s.backgroundMode == mode
                if (ImGui.selectable("${backgroundModeLabel(mode)}##env_bg_mode_${mode.name}", selected)) {
                    updateSettings(env) {
                        it.copy(
                            backgroundMode = mode,
                            skyboxVisible = mode != BackgroundMode.None,
                        )
                    }
                }
                if (selected) {
                    ImGui.setItemDefaultFocus()
                }
            }
            ImGui.endCombo()
        }
        tooltipOnHover(backgroundModeTooltip(s.backgroundMode))

        ImGui.separator()
        ImGui.text("3. Background Options")
        drawBackgroundModeHelp(s)
        when (s.backgroundMode) {
            BackgroundMode.Skybox -> drawSkyboxOptions(env, s)
            BackgroundMode.SolidColor -> drawSolidBackgroundColorEditor(env, s)
            BackgroundMode.Transparent, BackgroundMode.None -> Unit
        }
    }

    private fun drawBackgroundModeHelp(settings: com.pashkd.krender.engine.assets.environment.EnvironmentSettings) {
        when (settings.backgroundMode) {
            BackgroundMode.Skybox ->
                ImGui.textWrapped("Skybox uses the generated skybox cubemap as the preview background.")
            BackgroundMode.SolidColor ->
                ImGui.textWrapped("Solid Color uses the selected color as the preview background.")
            BackgroundMode.Transparent ->
                ImGui.textWrapped("Transparent uses a transparent preview background when the renderer path supports alpha in the backbuffer.")
            BackgroundMode.None ->
                ImGui.textWrapped("None disables background rendering and is the replacement for the old Show Background off state.")
        }
        if (state.dirty) {
            ImGui.text("[Unsaved changes]")
        }
    }

    private fun drawSkyboxOptions(
        env: EnvironmentAsset,
        settings: com.pashkd.krender.engine.assets.environment.EnvironmentSettings,
    ) {
        holder.value = settings.skyboxIntensity
        if (slider("Skybox Intensity##env_skybox_int", holder::value, 0f, 1f, "%.2f", SliderFlag.AlwaysClamp)) {
            updateSettings(env) { it.copy(skyboxIntensity = holder.value) }
        }
        tooltipOnHover("Controls the brightness of the skybox background in Skybox mode.")
    }

    private fun drawSolidBackgroundColorEditor(
        env: EnvironmentAsset,
        settings: com.pashkd.krender.engine.assets.environment.EnvironmentSettings,
    ) {
        val color = settings.backgroundColor ?: EnvironmentColor(0.08f, 0.09f, 0.11f, 1f)
        colorEdit4(
            "Background Color##env_background_color",
            color.r,
            color.g,
            color.b,
            color.a,
        ) { r, g, b, a ->
            updateSettings(env) {
                it.copy(
                    backgroundColor = EnvironmentColor(r = r, g = g, b = b, a = a),
                )
            }
        }
        tooltipOnHover("Chooses the background color used in Solid Color mode.")
    }

    private fun backgroundModeTooltip(mode: BackgroundMode): String =
        when (mode) {
            BackgroundMode.Skybox -> "Uses the generated skybox cubemap as the preview background."
            BackgroundMode.SolidColor -> "Uses a flat color as the preview background."
            BackgroundMode.Transparent -> "Leaves the background transparent when the renderer path supports it."
            BackgroundMode.None -> "Disables background rendering while keeping environment lighting."
        }

    private fun backgroundModeLabel(mode: BackgroundMode): String =
        when (mode) {
            BackgroundMode.Skybox -> "Skybox"
            BackgroundMode.SolidColor -> "Solid Color"
            BackgroundMode.Transparent -> "Transparent"
            BackgroundMode.None -> "None"
        }

    private inline fun updateSettings(
        env: EnvironmentAsset,
        transform: (EnvironmentSettings) -> EnvironmentSettings,
    ) {
        val previous = env.settings
        val updated = transform(previous)
        if (updated == previous) return
        state.environment = env.copy(settings = updated)
        state.dirty = true
        logger.info(TAG) {
            "Environment settings changed path='${state.manifestPath}' " +
                "backgroundVisible=${updated.skyboxVisible} backgroundMode=${updated.backgroundMode} " +
                "backgroundColor=${updated.backgroundColor?.let { "(${it.r},${it.g},${it.b},${it.a})" } ?: "<default>"} " +
                "exposure=${updated.exposure} rotation=${updated.rotationDegrees} " +
                "skyboxIntensity=${updated.skyboxIntensity} diffuseIntensity=${updated.diffuseIntensity} " +
                "specularIntensity=${updated.specularIntensity}"
        }
    }

    companion object {
        private const val TAG = "EnvironmentSettingsPanel"
    }
}

private class FloatHolder(
    var value: Float = 0f,
)
