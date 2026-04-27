package com.pashkd.krender.game

import com.pashkd.krender.engine.api.AssetPack
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.EntityId
import com.pashkd.krender.engine.api.ModelAsset
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.modelviewer.ModelViewerPanel
import com.pashkd.krender.engine.modelviewer.ModelViewerState
import com.pashkd.krender.engine.modelviewer.ModelViewerStatsPanel
import com.pashkd.krender.engine.modelviewer.ModelViewerSystem
import com.pashkd.krender.engine.modelviewer.ModelViewerCameraSystem
import com.pashkd.krender.engine.modelviewer.ModelViewerControlsPanel
import com.pashkd.krender.engine.modelviewer.ModelViewerLoadingPanel
import com.pashkd.krender.engine.modelviewer.ModelViewerUiLayoutDefaults
import com.pashkd.krender.engine.render3d.FreeCameraControllerComponent
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.Material
import com.pashkd.krender.engine.render3d.ModelComponent
import com.pashkd.krender.engine.render3d.ModelRenderSystem
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.render3d.WorldGridSystem
import com.pashkd.krender.engine.ui.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.ImGuiLayoutConfigLoader
import com.pashkd.krender.engine.ui.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.LogsPanel
import com.pashkd.krender.engine.ui.UiSystem

/**
 * Builds the ModelViewer scene, runtime systems, and ImGui panels.
 */
class ModelViewerScene(
    private val model: AssetRef<ModelAsset>? = null,
    private val availableModels: List<AssetRef<ModelAsset>> = model?.let(::listOf) ?: emptyList(),
    private val modelScale: Float = 1f,
    private var wireframeMode: Boolean = false,
) : Scene("model_viewer") {
    private val models = availableModels.distinctBy(AssetRef<ModelAsset>::path)

    override val requiredAssets: List<AssetPack> = listOf(
        object : AssetPack {
            override val assets = listOfNotNull(model)
        },
    )

    private val initialSelectedModelIndex: Int = model?.let(models::indexOf)?.takeIf { it >= 0 } ?: 0

    private lateinit var viewerState: ModelViewerState
    private var modelEntityId: EntityId? = null

    /**
     * Creates viewer state, systems, and scene entities.
     */
    override fun show() {
        val layoutConfig = ImGuiLayoutConfigLoader(
            assetPath = ModelViewerUiLayoutDefaults.assetPath,
            fallback = ModelViewerUiLayoutDefaults.config,
        ).load(engine.logger)
        val panelEventLogger = ImGuiWindowEventLogger(engine.logger, "ModelViewerUi")
        viewerState = ModelViewerState(
            availableModels = models,
            selectedModelIndex = initialSelectedModelIndex,
            loadedModel = model,
            modelScale = modelScale,
            wireframeEnabled = wireframeMode,
        )
        world.systems.add(WorldGridSystem(halfExtentCells = 24, cellSize = 1f))
        world.systems.add(createModelViewerSystem())
        world.systems.add(ModelViewerCameraSystem(engine.input))
        world.systems.add(createUiSystem(layoutConfig, panelEventLogger))
        world.systems.add(ModelRenderSystem())

        createCamera()
        createLights()
        model?.let(::createModelEntity)
    }

    /**
     * Releases cursor handling when the viewer scene is hidden.
     */
    override fun hide() {
        engine.input.setCursorCaptured(false)
    }

    /**
     * Recreates the renderable model entity for the selected asset.
     */
    private fun createModelEntity(modelRef: AssetRef<ModelAsset>) {
        modelEntityId?.let(world::removeEntity)
        val modelEntity = world.createEntity("Asset Model")
        modelEntityId = modelEntity.id
        modelEntity.transform.scale.set(modelScale, modelScale, modelScale)
        modelEntity.add(
            ModelComponent(
                model = modelRef,
                material = Material(baseColor = Color.white(), wireframe = viewerState.wireframeEnabled),
            ),
        )
    }

    /**
     * Spawns the free camera used for model inspection.
     */
    private fun createCamera() {
        val camera = world.createEntity("Viewer Camera")
        camera.transform.position.set(0f, 1.6f, 5f)
        camera.transform.eulerDegrees.set(0f, 180f, 0f)
        camera.add(
            PerspectiveCameraComponent(
                fieldOfViewDegrees = 67f,
                near = 0.05f,
                far = 250f,
            ),
        )
        camera.add(FreeCameraControllerComponent())
    }

    /**
     * Adds the default directional and ambient lights for the viewer.
     */
    private fun createLights() {
        val light = world.createEntity("Key Light")
        light.add(
            LightComponent(
                type = LightType.Directional,
                color = Color(1f, 0.96f, 0.88f),
                intensity = 1.25f,
                direction = Vec3(-0.5f, -0.8f, -0.35f),
            ),
        )

        val ambient = world.createEntity("Ambient Light")
        ambient.add(
            LightComponent(
                type = LightType.Ambient,
                color = Color(0.45f, 0.5f, 0.58f),
                intensity = 0.55f,
            ),
        )
    }

    /**
     * Builds the system that applies UI-driven viewer actions.
     */
    private fun createModelViewerSystem(): ModelViewerSystem =
        ModelViewerSystem(
            input = engine.input,
            assets = engine.assets,
            logger = engine.logger,
            state = viewerState,
            onReloadSelection = ::reloadWithSelectedModel,
            onExitRequested = engine::requestExit,
        )

    /**
     * Registers every ModelViewer ImGui panel against the shared UI system.
     */
    private fun createUiSystem(
        layoutConfig: ImGuiLayoutConfig,
        panelEventLogger: ImGuiWindowEventLogger,
    ): UiSystem =
        UiSystem(engine.ui).also { uiSystem ->
            uiSystem.addPanel(ModelViewerPanel(viewerState, layoutConfig, panelEventLogger))
            uiSystem.addPanel(ModelViewerStatsPanel(viewerState, layoutConfig, panelEventLogger))
            uiSystem.addPanel(ModelViewerControlsPanel(viewerState, layoutConfig, panelEventLogger))
            uiSystem.addPanel(ModelViewerLoadingPanel(viewerState, layoutConfig, panelEventLogger))
            uiSystem.addPanel(LogsPanel(engine.logs, layoutConfig, panelEventLogger))
        }

    /**
     * Replaces the scene so the newly selected model becomes the active asset.
     */
    private fun reloadWithSelectedModel(selectedModel: AssetRef<ModelAsset>) {
        engine.scenes.replace(
            ModelViewerScene(
                model = selectedModel,
                availableModels = models,
                modelScale = modelScale,
                wireframeMode = viewerState.wireframeEnabled,
            ),
        )
    }
}
