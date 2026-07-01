package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.assets.environment.DefaultEnvironmentService
import com.pashkd.krender.engine.assets.environment.EnvironmentService
import com.pashkd.krender.engine.scene.SceneConfig
import com.pashkd.krender.engine.scene.SceneConfigPresets
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.UiSystem

/**
 * Editor tool scene for inspecting, editing, and previewing Environment assets.
 *
 * Opened from Asset Browser for `.environment.json` manifests.
 */
class EnvironmentEditorScene(
    val environmentPath: String,
) : Scene("environment_editor") {
    override val config: SceneConfig = SceneConfigPresets.EditorTool

    private lateinit var editorState: EnvironmentEditorState
    private lateinit var environmentService: EnvironmentService

    override fun show() {
        engine.logger.info(TAG) { "EnvironmentEditor show environmentPath='$environmentPath'" }

        environmentService = DefaultEnvironmentService(engine.sceneFiles)
        editorState = EnvironmentEditorState(environmentPath)
        loadEnvironment()

        val uiSystem = UiSystem(engine.ui)
        uiSystem.addPanel(EnvironmentEditorToolbarPanel(editorState, environmentService, engine.logger))
        uiSystem.addPanel(EnvironmentInspectorPanel(editorState))
        uiSystem.addPanel(EnvironmentSettingsPanel(editorState, environmentService, engine.logger))
        world.systems.add(uiSystem)
    }

    private fun loadEnvironment() {
        try {
            val asset = environmentService.load(editorState.manifestPath)
            editorState.environment = asset
            editorState.selectedEnvironmentId = asset.id
            editorState.validation = environmentService.validate(asset)
            editorState.loadError = null
            editorState.dirty = false
            engine.logger.info(TAG) { "Environment loaded id='${asset.id.path}' name='${asset.name}'" }
        } catch (e: Exception) {
            editorState.environment = null
            editorState.validation = null
            editorState.loadError = e.message ?: "Unknown error"
            engine.logger.error(TAG, e) { "Failed to load environment: ${e.message}" }
        }
    }

    companion object {
        private const val TAG = "EnvironmentEditorScene"
    }
}
