package com.pashkd.krender.game

import com.pashkd.krender.engine.animationviewer.AnimationViewerAnimationsPanel
import com.pashkd.krender.engine.animationviewer.AnimationViewerBoundingBoxSystem
import com.pashkd.krender.engine.animationviewer.AnimationViewerLoadingPanel
import com.pashkd.krender.engine.animationviewer.AnimationViewerModelRenderSystem
import com.pashkd.krender.engine.animationviewer.AnimationViewerOperations
import com.pashkd.krender.engine.animationviewer.AnimationViewerPlaybackPanel
import com.pashkd.krender.engine.animationviewer.AnimationViewerSkeletonPanel
import com.pashkd.krender.engine.animationviewer.AnimationViewerSkeletonRenderSystem
import com.pashkd.krender.engine.animationviewer.AnimationViewerState
import com.pashkd.krender.engine.animationviewer.AnimationViewerSystem
import com.pashkd.krender.engine.animationviewer.AnimationViewerToolbarPanel
import com.pashkd.krender.engine.animationviewer.AnimationViewerUiLayoutDefaults
import com.pashkd.krender.engine.animationviewer.AnimationViewerViewportGuideSystem
import com.pashkd.krender.engine.animationviewer.AnimationViewerViewportPanel
import com.pashkd.krender.engine.api.AssetPack
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.AssetService
import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.ModelAsset
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.editor.viewport.EditorViewportCameraComponent
import com.pashkd.krender.engine.editor.viewport.EditorViewportCameraSystem
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.Material
import com.pashkd.krender.engine.render3d.ModelComponent
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.scene.SceneConfig
import com.pashkd.krender.engine.scene.SceneConfigPresets
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigLoader
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.LogsPanel
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.UiSystem

/**
 * Builds the single-model animation inspection scene, runtime systems, and ImGui panels.
 */
class AnimationViewerScene(
    private val model: AssetRef<ModelAsset>,
    private val modelScale: Float = 1f,
) : Scene("animation_viewer") {
    constructor(
        modelPath: String,
        modelScale: Float = 1f,
    ) : this(AssetRef.model(modelPath), modelScale)

    override val requiredAssets: List<AssetPack> = listOf(
        object : AssetPack {
            override val assets = listOf(model)
        },
    )

    override val config: SceneConfig = SceneConfigPresets.EditorTool

    private lateinit var viewerState: AnimationViewerState
    private lateinit var operations: AnimationViewerOperations
    private lateinit var layoutTracker: ImGuiLayoutRuntimeTracker
    private var updateFrameIndex = 0L
    private var renderFrameIndex = 0L

    override fun scheduleAssets(assets: AssetService) {
        engine.logger.info(TAG) {
            "AnimationViewer scheduleAssets start model='${model.path}' primitive=${model.isPrimitive} requiredPacks=${requiredAssets.size}"
        }
        try {
            super.scheduleAssets(assets)
            engine.logger.info(TAG) { "AnimationViewer scheduleAssets complete queued='${model.path}'" }
        } catch (error: Exception) {
            engine.logger.error(TAG, error) {
                "AnimationViewer scheduleAssets failed model='${model.path}': ${error.message}"
            }
            throw error
        }
    }

    override fun show() {
        engine.logger.info(TAG) { "AnimationViewer show start model='${model.path}' scale=${"%.3f".format(modelScale)}" }
        try {
            showInternal()
            engine.logger.info(TAG) {
                "AnimationViewer show complete model='${model.path}' entities=${world.all().size} modelEntityId=${viewerState.modelEntityId}"
            }
        } catch (error: Exception) {
            engine.logger.error(TAG, error) {
                "AnimationViewer show failed model='${model.path}' entities=${world.all().size}: ${error.message}"
            }
            throw error
        }
    }

    private fun showInternal() {
        engine.logger.debug(TAG) {
            "Loading AnimationViewer UI layout path='${AnimationViewerUiLayoutDefaults.assetPath}' fallbackPanels=${AnimationViewerUiLayoutDefaults.config.panels.keys.joinToString()}"
        }
        val layoutConfig = ImGuiLayoutConfigLoader(
            assetPath = AnimationViewerUiLayoutDefaults.assetPath,
            fallback = AnimationViewerUiLayoutDefaults.config,
        ).load(engine.logger)
        val panelEventLogger = ImGuiWindowEventLogger(engine.logger, "AnimationViewerUi")
        layoutTracker = ImGuiLayoutRuntimeTracker(layoutConfig)
        viewerState = AnimationViewerState(
            model = model,
            modelScale = modelScale,
        )
        operations = AnimationViewerOperations(viewerState, engine, layoutTracker)

        createCamera()
        createAmbientLight()
        createModelEntity()

        addSystem("AnimationViewerViewportGuideSystem", AnimationViewerViewportGuideSystem(viewerState))
        addSystem("AnimationViewerSystem", createAnimationViewerSystem())
        addSystem("UiSystem", createUiSystem(layoutConfig, panelEventLogger))
        addSystem("EditorViewportCameraSystem", EditorViewportCameraSystem(engine.input, viewerState.camera, viewerState.viewport))
        addSystem("AnimationViewerBoundingBoxSystem", AnimationViewerBoundingBoxSystem(viewerState, engine.assets))
        addSystem("AnimationViewerModelRenderSystem", AnimationViewerModelRenderSystem(viewerState))
        addSystem("AnimationViewerSkeletonRenderSystem", AnimationViewerSkeletonRenderSystem(viewerState))
    }

    override fun hide() {
        engine.logger.info(TAG) {
            "AnimationViewer hide model='${model.path}' stateInitialized=${::viewerState.isInitialized} entities=${world.all().size}"
        }
        engine.input.setCursorCaptured(false)
    }

    override fun update(dt: Float) {
        updateFrameIndex += 1
        if (updateFrameIndex == 1L) {
            engine.logger.info(TAG) {
                "AnimationViewer scene first update dt=${"%.4f".format(dt)} entities=${world.all().size} systemsInitialized=true"
            }
        }
        try {
            super.update(dt)
        } catch (error: Exception) {
            engine.logger.error(TAG, error) {
                "AnimationViewer scene update failed frame=$updateFrameIndex dt=${"%.4f".format(dt)} model='${model.path}': ${error.message}"
            }
            throw error
        }
    }

    override fun render(alpha: Float) {
        renderFrameIndex += 1
        if (renderFrameIndex == 1L) {
            engine.logger.info(TAG) {
                "AnimationViewer scene first render alpha=${"%.4f".format(alpha)} entities=${world.all().size}"
            }
        }
        try {
            super.render(alpha)
        } catch (error: Exception) {
            engine.logger.error(TAG, error) {
                "AnimationViewer scene render failed frame=$renderFrameIndex alpha=${"%.4f".format(alpha)} model='${model.path}': ${error.message}"
            }
            throw error
        }
    }

    override fun dispose() {
        engine.logger.info(TAG) {
            "AnimationViewer dispose model='${model.path}' entitiesBeforeFlush=${world.all().size}"
        }
        super.dispose()
        engine.logger.info(TAG) {
            "AnimationViewer dispose complete model='${model.path}' entitiesAfterFlush=${world.all().size}"
        }
    }

    private fun createModelEntity() {
        val modelEntity = world.createEntity("Animated Model")
        viewerState.modelEntityId = modelEntity.id
        modelEntity.transform.scale.set(modelScale, modelScale, modelScale)
        modelEntity.add(
            ModelComponent(
                model = model,
                material = Material(baseColor = Color.white()),
            ),
        )
        engine.logger.info(TAG) {
            "AnimationViewer model entity created id=${modelEntity.id} model='${model.path}' scale=${"%.3f".format(modelScale)}"
        }
    }

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
            "AnimationViewer camera created id=${camera.id} position=${formatVec3(camera.transform.position)} euler=${formatVec3(camera.transform.eulerDegrees)}"
        }
    }

    private fun createAmbientLight() {
        val ambient = world.createEntity("Ambient Light")
        viewerState.ambientLightEntityId = ambient.id
        ambient.add(
            LightComponent(
                type = LightType.Ambient,
                color = Color(0.55f, 0.58f, 0.64f),
                intensity = viewerState.ambientLightIntensity,
            ),
        )
        engine.logger.info(TAG) {
            "AnimationViewer ambient light created id=${ambient.id} color=0.55,0.58,0.64 intensity=${"%.2f".format(viewerState.ambientLightIntensity)}"
        }
    }

    private fun createAnimationViewerSystem(): AnimationViewerSystem =
        AnimationViewerSystem(
            assets = engine.assets,
            logger = engine.logger,
            state = viewerState,
            onExitRequested = engine::requestExit,
        )

    private fun createUiSystem(
        layoutConfig: ImGuiLayoutConfig,
        panelEventLogger: ImGuiWindowEventLogger,
    ): UiSystem =
        UiSystem(engine.ui).also { uiSystem ->
            engine.logger.debug(TAG) { "Registering AnimationViewer UI panels" }
            addPanel(
                uiSystem,
                "Toolbar",
                AnimationViewerToolbarPanel(viewerState, operations, layoutConfig, layoutTracker, panelEventLogger),
            )
            addPanel(
                uiSystem,
                "Viewport",
                AnimationViewerViewportPanel(viewerState, operations, layoutConfig, layoutTracker, panelEventLogger),
            )
            addPanel(
                uiSystem,
                "Animations",
                AnimationViewerAnimationsPanel(viewerState, operations, layoutConfig, layoutTracker, panelEventLogger),
            )
            addPanel(
                uiSystem,
                "Playback",
                AnimationViewerPlaybackPanel(viewerState, operations, layoutConfig, layoutTracker, panelEventLogger),
            )
            addPanel(
                uiSystem,
                "Skeleton",
                AnimationViewerSkeletonPanel(viewerState, operations, layoutConfig, layoutTracker, panelEventLogger),
            )
            addPanel(
                uiSystem,
                "Loading",
                AnimationViewerLoadingPanel(viewerState, layoutConfig, layoutTracker, panelEventLogger),
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
            engine.logger.info(TAG) { "AnimationViewer UI panels registered count=7" }
        }

    private fun addSystem(name: String, system: com.pashkd.krender.engine.api.System) {
        world.systems.add(system)
        engine.logger.info(TAG) { "AnimationViewer system added name='$name'" }
    }

    private fun addPanel(uiSystem: UiSystem, name: String, panel: UiPanel) {
        uiSystem.addPanel(
            UiPanel {
                try {
                    panel.draw()
                } catch (error: Exception) {
                    engine.logger.error(TAG, error) {
                        "AnimationViewer UI panel draw failed panel='$name' model='${model.path}': ${error.message}"
                    }
                    throw error
                }
            },
        )
        engine.logger.debug(TAG) { "AnimationViewer UI panel registered name='$name'" }
    }

    private fun formatVec3(value: com.pashkd.krender.engine.api.Vec3): String =
        "%.3f,%.3f,%.3f".format(value.x, value.y, value.z)

    companion object {
        private const val TAG = "AnimationViewerScene"
    }
}
