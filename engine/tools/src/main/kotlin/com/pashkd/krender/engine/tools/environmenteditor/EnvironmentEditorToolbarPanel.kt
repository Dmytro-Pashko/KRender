package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.assets.environment.EnvironmentService
import com.pashkd.krender.engine.assets.environment.ValidationStatus
import com.pashkd.krender.engine.ui.editor.UiPanel
import imgui.ImGui

/**
 * Top toolbar for the Environment Editor showing title, status, and primary actions.
 */
class EnvironmentEditorToolbarPanel(
    private val state: EnvironmentEditorState,
    private val environmentService: EnvironmentService,
    private val logger: Logger,
) : UiPanel {
    override fun draw() {
        ImGui.begin("Environment Editor")
        drawHeader()
        drawActions()
        drawStatus()
        ImGui.end()
    }

    private fun drawHeader() {
        val env = state.environment
        if (env != null) {
            ImGui.text("Environment: ${env.name}")
            ImGui.text("ID: ${env.id.path}")
            ImGui.text("Type: ${env.type}")
            ImGui.text("Manifest: ${state.manifestPath}")
            val validationStatus = state.validation?.status ?: ValidationStatus.Valid
            ImGui.text("Validation: $validationStatus")
            if (state.dirty) {
                ImGui.sameLine()
                ImGui.text(" [Modified]")
            }
        } else if (state.loadError != null) {
            ImGui.text("ERROR: Failed to load environment")
            ImGui.text("Error: ${state.loadError}")
            ImGui.text("Path: ${state.manifestPath}")
        } else {
            ImGui.text("No environment loaded")
            ImGui.text("Path: ${state.manifestPath}")
        }
    }

    private fun drawActions() {
        if (state.environment == null) return
        ImGui.separator()
        if (ImGui.button("Save")) {
            save()
        }
        ImGui.sameLine()
        if (ImGui.button("Reload")) {
            reload()
        }
        ImGui.sameLine()
        if (ImGui.button("Validate")) {
            validate()
        }
    }

    private fun drawStatus() {
        val msg = state.statusMessage
        if (msg != null) {
            ImGui.separator()
            ImGui.text(msg)
        }
    }

    private fun save() {
        val env = state.environment ?: return
        try {
            environmentService.save(env)
            state.validation = environmentService.validate(env)
            state.dirty = false
            state.statusMessage = "Saved successfully."
            logger.info(TAG) { "Environment saved id='${env.id.path}'" }
        } catch (e: Exception) {
            state.statusMessage = "Save failed: ${e.message}"
            logger.error(TAG, e) { "Environment save failed: ${e.message}" }
        }
    }

    private fun reload() {
        try {
            val asset = environmentService.load(state.manifestPath)
            state.applyLoadedEnvironment(asset)
            state.validation = environmentService.validate(asset)
            state.loadError = null
            state.dirty = false
            state.statusMessage = "Reloaded."
            logger.info(TAG) { "Environment reloaded id='${asset.id.path}'" }
        } catch (e: Exception) {
            state.loadError = e.message ?: "Unknown error"
            state.statusMessage = "Reload failed: ${e.message}"
            logger.error(TAG, e) { "Environment reload failed: ${e.message}" }
        }
    }

    private fun validate() {
        val env = state.environment ?: return
        state.validation = environmentService.validate(env)
        val report = state.validation
        state.statusMessage =
            if (report != null) {
                "Validation: ${report.status} (${report.issues.size} issue(s))"
            } else {
                "Validation complete."
            }
    }

    companion object {
        private const val TAG = "EnvironmentEditorToolbar"
    }
}
