package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.assets.environment.BackgroundMode
import com.pashkd.krender.engine.assets.environment.Environment
import com.pashkd.krender.engine.assets.environment.EnvironmentColor
import com.pashkd.krender.engine.assets.environment.EnvironmentSettings
import imgui.ImGui
import imgui.SliderFlag
import imgui.api.colorEdit4
import imgui.api.slider

/**
 * Composes the independent settings sections and their shared mutation policy.
 *
 * UI sections never replace editor state directly; all edits pass through
 * [EnvironmentSettingsMutator] so dirty tracking and structured logging remain consistent.
 */
internal class EnvironmentSettingsEditor(
    state: EnvironmentEditorState,
    logger: Logger,
) {
    private val mutator = EnvironmentSettingsMutator(state, logger)
    private val runtimeSection = EnvironmentRuntimeSettingsSection(mutator)
    private val backgroundSection = EnvironmentBackgroundSettingsSection(mutator)

    fun draw(environment: Environment) {
        ImGui.text("1. Runtime Settings")
        runtimeSection.draw(environment)
        ImGui.separator()
        ImGui.text("2. Background Type")
        backgroundSection.drawSelector(environment)
        ImGui.separator()
        ImGui.text("3. Background Options")
        backgroundSection.drawOptions(environment)
    }
}

private class EnvironmentRuntimeSettingsSection(
    private val mutator: EnvironmentSettingsMutator,
) {
    private val holder = FloatHolder()

    fun draw(environment: Environment) {
        val settings = environment.settings
        slider(environment, FloatSetting("Exposure##env_exposure", settings.exposure, 0.01f, 10f, "%.2f", "Adjusts overall preview brightness.")) {
            copy(exposure = it)
        }
        slider(environment, FloatSetting("Rotation##env_rotation", settings.rotationDegrees, 0f, 360f, "%.1f deg", "Rotates the environment around the vertical axis.")) {
            copy(rotationDegrees = it)
        }
        slider(environment, FloatSetting("Diffuse Intensity##env_diffuse_int", settings.diffuseIntensity, 0f, 5f, "%.2f", "Controls diffuse environment lighting.")) {
            copy(diffuseIntensity = it)
        }
        slider(environment, FloatSetting("Specular Intensity##env_specular_int", settings.specularIntensity, 0f, 1f, "%.2f", "Controls environment reflections.")) {
            copy(specularIntensity = it)
        }
    }

    private fun slider(
        environment: Environment,
        setting: FloatSetting,
        update: EnvironmentSettings.(Float) -> EnvironmentSettings,
    ) {
        holder.value = setting.value
        if (slider(setting.label, holder::value, setting.min, setting.max, setting.format, SliderFlag.AlwaysClamp)) {
            mutator.update(environment) { it.update(holder.value) }
        }
        tooltipOnHover(setting.hint)
    }
}

private class EnvironmentBackgroundSettingsSection(
    private val mutator: EnvironmentSettingsMutator,
) {
    private val holder = FloatHolder()

    fun drawSelector(environment: Environment) {
        val selectedMode = environment.settings.backgroundMode
        if (ImGui.beginCombo("Display Background##env_bg_mode", selectedMode.displayName)) {
            BackgroundMode.entries.forEach { mode ->
                val selected = selectedMode == mode
                if (ImGui.selectable("${mode.displayName}##env_bg_mode_${mode.name}", selected)) {
                    mutator.update(environment) { it.copy(backgroundMode = mode) }
                }
                if (selected) ImGui.setItemDefaultFocus()
            }
            ImGui.endCombo()
        }
        tooltipOnHover(selectedMode.description)
    }

    fun drawOptions(environment: Environment) {
        val settings = environment.settings
        ImGui.textWrapped(settings.backgroundMode.description)
        when (settings.backgroundMode) {
            BackgroundMode.Skybox -> drawSkyboxIntensity(environment)
            BackgroundMode.SolidColor -> drawSolidColor(environment)
            BackgroundMode.Transparent, BackgroundMode.None -> Unit
        }
    }

    private fun drawSkyboxIntensity(environment: Environment) {
        holder.value = environment.settings.skyboxIntensity
        if (slider("Skybox Intensity##env_skybox_int", holder::value, 0f, 1f, "%.2f", SliderFlag.AlwaysClamp)) {
            mutator.update(environment) { it.copy(skyboxIntensity = holder.value) }
        }
        tooltipOnHover("Controls skybox brightness without changing IBL intensity.")
    }

    private fun drawSolidColor(environment: Environment) {
        val color = environment.settings.backgroundColor ?: EnvironmentEditorConfig.defaultBackgroundColor
        colorEdit4("Background Color##env_background_color", color.r, color.g, color.b, color.a) { r, g, b, a ->
            mutator.update(environment) {
                it.copy(backgroundColor = EnvironmentColor(r, g, b, a))
            }
        }
        tooltipOnHover("Chooses the viewport clear color used in Solid Color mode.")
    }
}

/** Centralizes immutable settings updates, dirty tracking, and change logging. */
private class EnvironmentSettingsMutator(
    private val state: EnvironmentEditorState,
    private val logger: Logger,
) {
    fun update(
        environment: Environment,
        transform: (EnvironmentSettings) -> EnvironmentSettings,
    ) {
        val updated = transform(environment.settings)
        if (updated == environment.settings) return
        state.updateEnvironment { current -> current.copy(settings = updated) }
        logger.info(TAG) {
            "Environment settings changed path='${state.manifestPath}' " +
                "backgroundMode=${updated.backgroundMode} backgroundColor=${updated.backgroundColor} " +
                "exposure=${updated.exposure} rotation=${updated.rotationDegrees} " +
                "skyboxIntensity=${updated.skyboxIntensity} diffuseIntensity=${updated.diffuseIntensity} " +
                "specularIntensity=${updated.specularIntensity}"
        }
    }

    companion object {
        private const val TAG = "EnvironmentSettings"
    }
}

private class FloatHolder(
    var value: Float = 0f,
)

private data class FloatSetting(
    val label: String,
    val value: Float,
    val min: Float,
    val max: Float,
    val format: String,
    val hint: String,
)
