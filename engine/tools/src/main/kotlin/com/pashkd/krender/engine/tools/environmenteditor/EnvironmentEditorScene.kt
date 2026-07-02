package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.api.AssetPack
import com.pashkd.krender.engine.render3d.ActiveCameraComponent
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.Material
import com.pashkd.krender.engine.render3d.ModelComponent
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.assets.environment.DefaultEnvironmentService
import com.pashkd.krender.engine.assets.environment.EnvironmentGenerationService
import com.pashkd.krender.engine.assets.environment.EnvironmentService
import com.pashkd.krender.engine.assets.environment.PlaceholderEnvironmentGenerationService
import com.pashkd.krender.engine.scene.SceneConfig
import com.pashkd.krender.engine.scene.SceneConfigPresets
import com.pashkd.krender.engine.tools.environmenteditor.preview.EnvironmentPreviewCamera
import com.pashkd.krender.engine.tools.environmenteditor.preview.EnvironmentPreviewCameraSystem
import com.pashkd.krender.engine.tools.environmenteditor.preview.EnvironmentPreviewController
import com.pashkd.krender.engine.tools.environmenteditor.preview.EnvironmentPreviewRenderSystem
import com.pashkd.krender.engine.ui.editor.UiSystem

/**
 * Editor tool scene for inspecting, editing, and previewing Environment assets.
 *
 * Opened from Asset Browser for `.environment.json` manifests.
 */
class EnvironmentEditorScene(
    val environmentPath: String,
) : Scene("environment_editor") {
    private val previewController = EnvironmentPreviewController()

    override val requiredAssets: List<AssetPack> =
        listOf(
            object : AssetPack {
                override val assets = listOf(previewController.previewModel)
            },
        )

    override val config: SceneConfig = SceneConfigPresets.EditorTool

    private lateinit var editorState: EnvironmentEditorState
    private lateinit var environmentService: EnvironmentService
    private lateinit var generationService: EnvironmentGenerationService

    override fun show() {
        engine.logger.info(TAG) { "EnvironmentEditor show environmentPath='$environmentPath'" }

        environmentService = DefaultEnvironmentService(engine.sceneFiles)
        generationService = PlaceholderEnvironmentGenerationService
        editorState = EnvironmentEditorState(environmentPath)
        loadEnvironment()
        createPreviewCamera()
        createPreviewLights()
        createPreviewModel()

        val uiSystem = UiSystem(engine.ui)
        uiSystem.addPanel(EnvironmentEditorToolbarPanel(editorState, environmentService, engine.logger))
        uiSystem.addPanel(EnvironmentInspectorPanel(editorState))
        uiSystem.addPanel(EnvironmentSettingsPanel(editorState, environmentService, engine.logger))
        uiSystem.addPanel(EnvironmentSourceVariantsPanel(editorState))
        uiSystem.addPanel(EnvironmentGeneratedMapsPanel(editorState, generationService, engine.logger))
        uiSystem.addPanel(EnvironmentDiagnosticsPanel(editorState, environmentService))
        uiSystem.addPanel(EnvironmentPreviewPanel(editorState))
        world.systems.add(EnvironmentPreviewCameraSystem(editorState))
        world.systems.add(EnvironmentPreviewRenderSystem(editorState, previewController))
        world.systems.add(uiSystem)
    }

    private fun loadEnvironment() {
        try {
            val asset = environmentService.load(editorState.manifestPath)
            editorState.applyLoadedEnvironment(asset)
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

    private fun createPreviewCamera() {
        val orbitPosition = EnvironmentPreviewCamera.orbitPosition(editorState.previewState)
        val camera = world.createEntity("Environment Preview Camera")
        camera.transform.position.set(orbitPosition.x, orbitPosition.y, orbitPosition.z)
        camera.add(ActiveCameraComponent())
        camera.add(
            PerspectiveCameraComponent(
                fieldOfViewDegrees = 50f,
                near = 0.05f,
                far = 200f,
                lookAt = EnvironmentPreviewCamera.FocusTarget.copy(),
            ),
        )
    }

    private fun createPreviewLights() {
        world.createEntity("Environment Preview Ambient").add(
            LightComponent(
                type = LightType.Ambient,
                intensity = 0.8f,
            ),
        )
        world.createEntity("Environment Preview Directional").add(
            LightComponent(
                type = LightType.Directional,
                intensity = 0.65f,
            ),
        )
    }

    private fun createPreviewModel() {
        val preview = world.createEntity("Environment Material Spheres")
        editorState.previewModelEntityId = preview.id
        preview.transform.position.set(
            EnvironmentPreviewController.PreviewModelPosition.x,
            EnvironmentPreviewController.PreviewModelPosition.y,
            EnvironmentPreviewController.PreviewModelPosition.z,
        )
        preview.transform.scale.set(
            EnvironmentPreviewController.PreviewModelScale.x,
            EnvironmentPreviewController.PreviewModelScale.y,
            EnvironmentPreviewController.PreviewModelScale.z,
        )
        preview.add(
            ModelComponent(
                model = previewController.previewModel,
                material = Material(),
            ),
        )
    }

    companion object {
        private const val TAG = "EnvironmentEditorScene"
    }
}
