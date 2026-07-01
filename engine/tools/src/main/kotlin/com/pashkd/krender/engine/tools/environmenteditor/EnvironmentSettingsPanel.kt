package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.assets.environment.BackgroundMode
import com.pashkd.krender.engine.assets.environment.EnvironmentAsset
import com.pashkd.krender.engine.assets.environment.EnvironmentService
import com.pashkd.krender.engine.assets.environment.EnvironmentSettings
import com.pashkd.krender.engine.ui.editor.UiPanel
import imgui.ImGui
import imgui.SliderFlag
import imgui.api.slider

/**
 * Editable settings panel for environment runtime parameters.
 *
 * Uses mutable holder objects so the imgui `slider` extension can write back
 * through `KMutableProperty0<Float>`.
 */
class EnvironmentSettingsPanel(
    private val state: EnvironmentEditorState,
    private val environmentService: EnvironmentService,
    private val logger: Logger,
) : UiPanel {
    private val nameBuffer = ByteArray(256)
    private var nameBufferSynced = false

    private val holder = FloatHolder()

    override fun draw() {
        if (!ImGui.begin("Settings")) {
            ImGui.end()
            return
        }
        val env = state.environment
        if (env == null) {
            ImGui.text("No environment loaded.")
            ImGui.end()
            return
        }
        drawNameEditor(env)
        drawSettingsEditor(env)
        drawActions(env)
        ImGui.end()
    }

    private fun drawNameEditor(env: EnvironmentAsset) {
        if (!nameBufferSynced) {
            syncNameBuffer(env.name)
            nameBufferSynced = true
        }
        if (ImGui.inputText("Name", nameBuffer)) {
            val newName = readBuffer(nameBuffer)
            if (newName != env.name) {
                state.environment = env.copy(name = newName)
                state.dirty = true
            }
        }
    }

    private fun drawSettingsEditor(env: EnvironmentAsset) {
        val s = env.settings
        ImGui.separator()
        ImGui.text("Runtime Settings")

        holder.value = s.exposure
        if (slider("Exposure##env_exposure", holder::value, 0.01f, 10f, "%.2f", SliderFlag.AlwaysClamp)) {
            updateSettings(env) { it.copy(exposure = holder.value) }
        }

        holder.value = s.rotationDegrees
        if (slider("Rotation##env_rotation", holder::value, 0f, 360f, "%.1f deg", SliderFlag.AlwaysClamp)) {
            updateSettings(env) { it.copy(rotationDegrees = holder.value) }
        }

        ImGui.checkbox("Skybox Visible##env_skybox_visible", state::skyboxVisibleHolder)
        if (state.skyboxVisibleHolder != s.skyboxVisible) {
            updateSettings(env) { it.copy(skyboxVisible = state.skyboxVisibleHolder) }
        }

        holder.value = s.skyboxIntensity
        if (slider("Skybox Intensity##env_skybox_int", holder::value, 0f, 5f, "%.2f", SliderFlag.AlwaysClamp)) {
            updateSettings(env) { it.copy(skyboxIntensity = holder.value) }
        }

        holder.value = s.diffuseIntensity
        if (slider("Diffuse Intensity##env_diffuse_int", holder::value, 0f, 5f, "%.2f", SliderFlag.AlwaysClamp)) {
            updateSettings(env) { it.copy(diffuseIntensity = holder.value) }
        }

        holder.value = s.specularIntensity
        if (slider("Specular Intensity##env_specular_int", holder::value, 0f, 5f, "%.2f", SliderFlag.AlwaysClamp)) {
            updateSettings(env) { it.copy(specularIntensity = holder.value) }
        }

        ImGui.separator()
        val bgModes = BackgroundMode.entries.toTypedArray()
        val currentIdx = bgModes.indexOf(s.backgroundMode)
        ImGui.text("Background Mode: ${s.backgroundMode.name}")
        for ((i, mode) in bgModes.withIndex()) {
            if (ImGui.radioButton("${mode.name}##env_bg_mode_$i", i == currentIdx)) {
                updateSettings(env) { it.copy(backgroundMode = mode) }
            }
            if (i < bgModes.size - 1) ImGui.sameLine()
        }
    }

    private fun drawActions(env: EnvironmentAsset) {
        ImGui.separator()
        if (ImGui.button("Save##env_settings_save")) {
            try {
                environmentService.save(env)
                state.validation = environmentService.validate(env)
                state.dirty = false
                state.statusMessage = "Settings saved."
                logger.info(TAG) { "Environment settings saved id='${env.id.path}'" }
            } catch (e: Exception) {
                state.statusMessage = "Save failed: ${e.message}"
                logger.error(TAG, e) { "Settings save failed: ${e.message}" }
            }
        }
        ImGui.sameLine()
        if (ImGui.button("Revert##env_settings_revert")) {
            try {
                val reloaded = environmentService.load(state.manifestPath)
                state.applyLoadedEnvironment(reloaded)
                state.validation = environmentService.validate(reloaded)
                state.dirty = false
                nameBufferSynced = false
                state.statusMessage = "Reverted to saved state."
                logger.info(TAG) { "Environment reverted id='${reloaded.id.path}'" }
            } catch (e: Exception) {
                state.statusMessage = "Revert failed: ${e.message}"
                logger.error(TAG, e) { "Revert failed: ${e.message}" }
            }
        }
        if (state.dirty) {
            ImGui.sameLine()
            ImGui.text("[Unsaved changes]")
        }
    }

    private inline fun updateSettings(
        env: EnvironmentAsset,
        transform: (EnvironmentSettings) -> EnvironmentSettings,
    ) {
        state.environment = env.copy(settings = transform(env.settings))
        state.dirty = true
    }

    private fun syncNameBuffer(name: String) {
        nameBuffer.fill(0)
        val bytes = name.toByteArray(Charsets.UTF_8)
        val len = minOf(bytes.size, nameBuffer.size - 1)
        bytes.copyInto(nameBuffer, endIndex = len)
    }

    private fun readBuffer(buffer: ByteArray): String {
        val end = buffer.indexOf(0).takeIf { it >= 0 } ?: buffer.size
        return String(buffer, 0, end, Charsets.UTF_8)
    }

    companion object {
        private const val TAG = "EnvironmentSettingsPanel"
    }
}

private class FloatHolder(
    var value: Float = 0f,
)
