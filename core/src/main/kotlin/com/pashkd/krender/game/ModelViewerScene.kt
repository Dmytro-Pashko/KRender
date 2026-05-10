package com.pashkd.krender.game

import com.pashkd.krender.engine.api.AssetPack
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.AssetService
import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.ModelAsset
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.editor.viewport.EditorViewportCameraComponent
import com.pashkd.krender.engine.editor.viewport.EditorViewportCameraSystem
import com.pashkd.krender.engine.modelviewer.ModelViewerBoundingBoxSystem
import com.pashkd.krender.engine.modelviewer.ModelViewerInfoPanel
import com.pashkd.krender.engine.modelviewer.ModelViewerLoadingPanel
import com.pashkd.krender.engine.modelviewer.ModelViewerMaterialsPanel
import com.pashkd.krender.engine.modelviewer.ModelViewerMeshPartsPanel
import com.pashkd.krender.engine.modelviewer.ModelViewerModelRenderSystem
import com.pashkd.krender.engine.modelviewer.ModelViewerOperations
import com.pashkd.krender.engine.modelviewer.ModelViewerState
import com.pashkd.krender.engine.modelviewer.ModelViewerSystem
import com.pashkd.krender.engine.modelviewer.ModelViewerTextureChannelsPanel
import com.pashkd.krender.engine.modelviewer.ModelViewerToolbarPanel
import com.pashkd.krender.engine.modelviewer.ModelViewerUiLayoutDefaults
import com.pashkd.krender.engine.modelviewer.ModelViewerViewportGuideSystem
import com.pashkd.krender.engine.modelviewer.ModelViewerViewportPanel
import com.pashkd.krender.engine.modelviewer.UV_CHECKER_TEXTURE_OPTIONS
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.Material
import com.pashkd.krender.engine.render3d.ModelComponent
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.ui.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.ImGuiLayoutConfigLoader
import com.pashkd.krender.engine.ui.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.LogsPanel
import com.pashkd.krender.engine.ui.UiPanel
import com.pashkd.krender.engine.ui.UiSystem

/**
 * Builds the single-model inspection scene, runtime systems, and ImGui panels.
 */
class ModelViewerScene(
    private val model: AssetRef<ModelAsset>,
    private val modelScale: Float = 1f,
) : Scene("model_viewer") {
    constructor(
        modelPath: String,
        modelScale: Float = 1f,
    ) : this(AssetRef.model(modelPath), modelScale)

    override val requiredAssets: List<AssetPack> = listOf(
        object : AssetPack {
            override val assets = listOf(model) + UV_CHECKER_TEXTURE_OPTIONS.map { option ->
                AssetRef.texture(option.texturePath)
            }
        },
    )

    private lateinit var viewerState: ModelViewerState
    private lateinit var operations: ModelViewerOperations
    private lateinit var layoutTracker: ImGuiLayoutRuntimeTracker
    private var updateFrameIndex = 0L
    private var renderFrameIndex = 0L

    /**
     * Logs and schedules the single model asset required by this viewer.
     */
    override fun scheduleAssets(assets: AssetService) {
        engine.logger.info(TAG) {
            "ModelViewer scheduleAssets start model='${model.path}' primitive=${model.isPrimitive} requiredPacks=${requiredAssets.size}"
        }
        try {
            super.scheduleAssets(assets)
            engine.logger.info(TAG) { "ModelViewer scheduleAssets complete queued='${model.path}'" }
        } catch (error: Exception) {
            engine.logger.error(TAG, error) {
                "ModelViewer scheduleAssets failed model='${model.path}': ${error.message}"
            }
            throw error
        }
    }

    /**
     * Creates viewer state, systems, and scene entities.
     */
    override fun show() {
        engine.logger.info(TAG) { "ModelViewer show start model='${model.path}' scale=${"%.3f".format(modelScale)}" }
        try {
            showInternal()
            engine.logger.info(TAG) {
                "ModelViewer show complete model='${model.path}' entities=${world.all().size} modelEntityId=${viewerState.modelEntityId}"
            }
        } catch (error: Exception) {
            engine.logger.error(TAG, error) {
                "ModelViewer show failed model='${model.path}' entities=${world.all().size}: ${error.message}"
            }
            throw error
        }
    }

    private fun showInternal() {
        engine.logger.debug(TAG) {
            "Loading ModelViewer UI layout path='${ModelViewerUiLayoutDefaults.assetPath}' fallbackPanels=${ModelViewerUiLayoutDefaults.config.panels.keys.joinToString()}"
        }
        val layoutConfig = ImGuiLayoutConfigLoader(
            assetPath = ModelViewerUiLayoutDefaults.assetPath,
            fallback = ModelViewerUiLayoutDefaults.config,
        ).load(engine.logger)
        engine.logger.info(TAG) {
            "ModelViewer UI layout loaded panels=${layoutConfig.panels.keys.joinToString()}"
        }
        val panelEventLogger = ImGuiWindowEventLogger(engine.logger, "ModelViewerUi")
        layoutTracker = ImGuiLayoutRuntimeTracker(layoutConfig)
        engine.logger.debug(TAG) { "ModelViewer layout tracker created" }
        viewerState = ModelViewerState(
            model = model,
            modelScale = modelScale,
        )
        engine.logger.info(TAG) {
            "ModelViewer state created model='${viewerState.modelPath}' displayMode=${viewerState.displayMode} " +
                "debugMode=${viewerState.debugMode} grid=${viewerState.showGrid} axes=${viewerState.showAxes} " +
                "bounds=${viewerState.showBoundingBox}"
        }
        operations = ModelViewerOperations(viewerState, engine, layoutTracker)
        engine.logger.debug(TAG) { "ModelViewer operations created" }

        createCamera()
        createAmbientLight()
        createModelEntity()

        addSystem("ModelViewerViewportGuideSystem", ModelViewerViewportGuideSystem(viewerState))
        addSystem("ModelViewerSystem", createModelViewerSystem())
        addSystem("UiSystem", createUiSystem(layoutConfig, panelEventLogger))
        addSystem("EditorViewportCameraSystem", EditorViewportCameraSystem(engine.input, viewerState.camera, viewerState.viewport))
        addSystem("ModelViewerBoundingBoxSystem", ModelViewerBoundingBoxSystem(viewerState, engine.assets))
        addSystem("ModelViewerModelRenderSystem", ModelViewerModelRenderSystem(viewerState))
    }

    /**
     * Releases cursor handling when the viewer scene is hidden.
     */
    override fun hide() {
        engine.logger.info(TAG) {
            "ModelViewer hide model='${model.path}' stateInitialized=${::viewerState.isInitialized} entities=${world.all().size}"
        }
        engine.input.setCursorCaptured(false)
    }

    /**
     * Logs first-frame and failure details around scene update.
     */
    override fun update(dt: Float) {
        updateFrameIndex += 1
        if (updateFrameIndex == 1L) {
            engine.logger.info(TAG) {
                "ModelViewer scene first update dt=${"%.4f".format(dt)} entities=${world.all().size} systemsInitialized=true"
            }
        }
        try {
            super.update(dt)
        } catch (error: Exception) {
            engine.logger.error(TAG, error) {
                "ModelViewer scene update failed frame=$updateFrameIndex dt=${"%.4f".format(dt)} " +
                    "model='${model.path}' entities=${world.all().size}: ${error.message}"
            }
            throw error
        }
    }

    /**
     * Logs first-frame and failure details around render command collection.
     */
    override fun render(alpha: Float) {
        renderFrameIndex += 1
        if (renderFrameIndex == 1L) {
            engine.logger.info(TAG) {
                "ModelViewer scene first render alpha=${"%.4f".format(alpha)} entities=${world.all().size}"
            }
        }
        try {
            super.render(alpha)
        } catch (error: Exception) {
            engine.logger.error(TAG, error) {
                "ModelViewer scene render failed frame=$renderFrameIndex alpha=${"%.4f".format(alpha)} " +
                    "model='${model.path}' entities=${world.all().size}: ${error.message}"
            }
            throw error
        }
    }

    /**
     * Logs final scene teardown details.
     */
    override fun dispose() {
        engine.logger.info(TAG) {
            "ModelViewer dispose model='${model.path}' entitiesBeforeFlush=${world.all().size}"
        }
        super.dispose()
        engine.logger.info(TAG) {
            "ModelViewer dispose complete model='${model.path}' entitiesAfterFlush=${world.all().size}"
        }
    }

    /**
     * Creates the renderable model entity for the active asset.
     */
    private fun createModelEntity() {
        val modelEntity = world.createEntity("Asset Model")
        viewerState.modelEntityId = modelEntity.id
        modelEntity.transform.scale.set(modelScale, modelScale, modelScale)
        modelEntity.add(
            ModelComponent(
                model = model,
                material = Material(baseColor = Color.white()),
            ),
        )
        engine.logger.info(TAG) {
            "ModelViewer model entity created id=${modelEntity.id} model='${model.path}' scale=${"%.3f".format(modelScale)}"
        }
    }

    /**
     * Spawns the editor-style camera used for model inspection.
     */
    private fun createCamera() {
        val camera = world.createEntity("Viewer Camera")
        camera.transform.position.set(
            viewerState.camera.position.x,
            viewerState.camera.position.y,
            viewerState.camera.position.z,
        )
        camera.transform.eulerDegrees.set(
            viewerState.camera.eulerDegrees.x,
            viewerState.camera.eulerDegrees.y,
            viewerState.camera.eulerDegrees.z,
        )
        camera.add(EditorViewportCameraComponent())
        camera.add(
            PerspectiveCameraComponent(
                fieldOfViewDegrees = 67f,
                near = 0.05f,
                far = 250f,
            ),
        )
        engine.logger.info(TAG) {
            "ModelViewer camera created id=${camera.id} position=${formatVec3(camera.transform.position)} " +
                "euler=${formatVec3(camera.transform.eulerDegrees)}"
        }
    }

    /**
     * Adds ambient-only lighting for neutral model inspection.
     */
    private fun createAmbientLight() {
        val ambient = world.createEntity("Ambient Light")
        ambient.add(
            LightComponent(
                type = LightType.Ambient,
                color = Color(0.55f, 0.58f, 0.64f),
                intensity = 0.8f,
            ),
        )
        engine.logger.info(TAG) {
            "ModelViewer ambient light created id=${ambient.id} color=0.55,0.58,0.64 intensity=0.80"
        }
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
            engine.logger.debug(TAG) { "Registering ModelViewer UI panels" }
            addPanel(
                uiSystem,
                "Toolbar",
                ModelViewerToolbarPanel(viewerState, operations, layoutConfig, layoutTracker, panelEventLogger),
            )
            addPanel(
                uiSystem,
                "Viewport",
                ModelViewerViewportPanel(viewerState, layoutConfig, layoutTracker, panelEventLogger),
            )
            addPanel(
                uiSystem,
                "ModelInfo",
                ModelViewerInfoPanel(viewerState, layoutConfig, layoutTracker, panelEventLogger),
            )
            addPanel(
                uiSystem,
                "MeshParts",
                ModelViewerMeshPartsPanel(viewerState, layoutConfig, layoutTracker, panelEventLogger),
            )
            addPanel(
                uiSystem,
                "Materials",
                ModelViewerMaterialsPanel(
                    viewerState,
                    engine.assets,
                    engine.ui,
                    layoutConfig,
                    layoutTracker,
                    panelEventLogger,
                ),
            )
            addPanel(
                uiSystem,
                "TextureChannels",
                ModelViewerTextureChannelsPanel(
                    viewerState,
                    engine.assets,
                    engine.ui,
                    layoutConfig,
                    layoutTracker,
                    panelEventLogger,
                ),
            )
            addPanel(
                uiSystem,
                "Loading",
                ModelViewerLoadingPanel(viewerState, layoutConfig, layoutTracker, panelEventLogger),
            )
            addPanel(
                uiSystem,
                "Logs",
                LogsPanel(
                    engine.logs,
                    layoutConfig,
                    panelEventLogger,
                    layoutTracker = layoutTracker,
                    initialAutoScrollToLatest = true,
                ),
            )
            engine.logger.info(TAG) { "ModelViewer UI panels registered count=8" }
        }

    private fun addSystem(name: String, system: com.pashkd.krender.engine.api.System) {
        world.systems.add(system)
        engine.logger.info(TAG) { "ModelViewer system added name='$name'" }
    }

    private fun addPanel(uiSystem: UiSystem, name: String, panel: UiPanel) {
        uiSystem.addPanel(
            UiPanel {
                try {
                    panel.draw()
                } catch (error: Exception) {
                    engine.logger.error(TAG, error) {
                        "ModelViewer UI panel draw failed panel='$name' model='${model.path}': ${error.message}"
                    }
                    throw error
                }
            },
        )
        engine.logger.debug(TAG) { "ModelViewer UI panel registered name='$name'" }
    }

    private fun formatVec3(value: com.pashkd.krender.engine.api.Vec3): String =
        "%.3f,%.3f,%.3f".format(value.x, value.y, value.z)

    companion object {
        private const val TAG = "ModelViewerScene"
    }
}
