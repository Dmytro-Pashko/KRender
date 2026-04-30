package com.pashkd.krender.game

import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.ModelRenderSystem
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.sceneeditor.EditorOnlyComponent
import com.pashkd.krender.engine.sceneeditor.SceneEditorDocument
import com.pashkd.krender.engine.sceneeditor.SceneEditorOperations
import com.pashkd.krender.engine.sceneeditor.SceneEditorCameraComponent
import com.pashkd.krender.engine.sceneeditor.SceneEditorCameraSystem
import com.pashkd.krender.engine.sceneeditor.SceneEditorState
import com.pashkd.krender.engine.sceneeditor.SceneEditorToolbarPanel
import com.pashkd.krender.engine.sceneeditor.SceneEditorUiLayoutDefaults
import com.pashkd.krender.engine.sceneeditor.SceneEditorViewportGuideSystem
import com.pashkd.krender.engine.sceneeditor.SceneHierarchyPanel
import com.pashkd.krender.engine.sceneeditor.SceneInspectorPanel
import com.pashkd.krender.engine.sceneeditor.SceneViewportPanel
import com.pashkd.krender.engine.ui.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.ImGuiLayoutConfigLoader
import com.pashkd.krender.engine.ui.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.LogsPanel
import com.pashkd.krender.engine.ui.UiSystem

/**
 * MVP foundation scene for composing and inspecting engine scene data.
 */
class SceneEditorScene(
    private val scenePath: String? = null,
    private val initialSceneName: String = "Untitled Scene",
) : Scene("scene_editor") {
    private lateinit var editorState: SceneEditorState
    private lateinit var document: SceneEditorDocument
    private lateinit var operations: SceneEditorOperations

    override fun show() {
        engine.logger.info(TAG) { "Showing Scene Editor scene path='${scenePath ?: "<memory>"}'" }
        engine.logger.info(TAG) { "Initializing Scene Editor runtime world" }
        val layoutConfig = ImGuiLayoutConfigLoader(
            assetPath = SceneEditorUiLayoutDefaults.assetPath,
            fallback = SceneEditorUiLayoutDefaults.config,
        ).load(engine.logger)
        val panelEventLogger = ImGuiWindowEventLogger(engine.logger, "SceneEditorUi")

        editorState = SceneEditorState(
            currentScenePath = scenePath,
            sceneName = initialSceneName,
        )
        document = SceneEditorDocument(world = SceneWorld())
        operations = SceneEditorOperations(document, editorState, engine)
        engine.logger.info(TAG) { "Initializing Scene Editor document world" }
        operations.createNewScene()
        scenePath?.let { path ->
            operations.open(path)
        }

        createEditorCamera()
        createEditorLights()

        world.systems.add(SceneEditorViewportGuideSystem(editorState))
        world.systems.add(createUiSystem(layoutConfig, panelEventLogger))
        world.systems.add(SceneEditorCameraSystem(engine.input, editorState))
        world.systems.add(ModelRenderSystem())
    }

    override fun hide() {
        engine.input.setCursorCaptured(false)
    }

    private fun createUiSystem(
        layoutConfig: ImGuiLayoutConfig,
        panelEventLogger: ImGuiWindowEventLogger,
    ): UiSystem =
        UiSystem(engine.ui).also { uiSystem ->
            uiSystem.addPanel(SceneEditorToolbarPanel(editorState, operations, layoutConfig, panelEventLogger))
            uiSystem.addPanel(SceneHierarchyPanel(editorState, document, operations, layoutConfig, panelEventLogger, engine.logger))
            uiSystem.addPanel(SceneInspectorPanel(editorState, document, operations, layoutConfig, panelEventLogger, engine.logger))
            uiSystem.addPanel(SceneViewportPanel(editorState, document, layoutConfig, panelEventLogger))
            uiSystem.addPanel(LogsPanel(engine.logs, layoutConfig, panelEventLogger))
        }

    private fun createEditorCamera() {
        // Editor camera is not part of scene data.
        val camera = world.createEntity("Editor Camera")
        camera.transform.position.set(0f, 2f, 6f)
        camera.transform.eulerDegrees.set(-10f, 180f, 0f)
        camera.add(EditorOnlyComponent())
        camera.add(SceneEditorCameraComponent())
        camera.add(
            PerspectiveCameraComponent(
                fieldOfViewDegrees = 67f,
                near = 0.05f,
                far = 250f,
            ),
        )
        editorState.camera.position = camera.transform.position.copy()
        editorState.camera.eulerDegrees = camera.transform.eulerDegrees.copy()
    }

    private fun createEditorLights() {
        val keyLight = world.createEntity("Editor Key Light")
        keyLight.add(EditorOnlyComponent())
        keyLight.add(
            LightComponent(
                type = LightType.Directional,
                color = Color(1f, 0.96f, 0.88f),
                intensity = 1.2f,
                direction = Vec3(-0.45f, -0.8f, -0.35f),
            ),
        )

        val ambientLight = world.createEntity("Editor Ambient Light")
        ambientLight.add(EditorOnlyComponent())
        ambientLight.add(
            LightComponent(
                type = LightType.Ambient,
                color = Color(0.45f, 0.5f, 0.58f),
                intensity = 0.55f,
            ),
        )
    }

    companion object {
        private const val TAG = "SceneEditorScene"
    }
}
