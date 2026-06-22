package com.pashkd.krender.engine.tools.skin

import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.scene.SceneConfig
import com.pashkd.krender.engine.scene.SceneConfigPresets
import com.pashkd.krender.engine.tools.skin.gdx.GdxSkinEditorPreview
import com.pashkd.krender.engine.tools.skin.gdx.SkinReloadService
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigLoader
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.LogsPanel
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.UiSystem

class SkinEditorScene(
    initialSkinPath: String? = null,
) : Scene("skin_editor") {
    override val config: SceneConfig = SceneConfigPresets.EditorTool

    private lateinit var editorState: SkinEditorState
    private lateinit var preview: GdxSkinEditorPreview
    private lateinit var reloadService: SkinReloadService
    private lateinit var layoutTracker: ImGuiLayoutRuntimeTracker
    private lateinit var operations: SkinEditorOperations
    private val previewLayouts = PreviewLayoutRegistry()

    init {
        editorState = SkinEditorState(currentInputPath = initialSkinPath?.trim()?.replace('\\', '/'))
    }

    override fun show() {
        engine.logger.info(TAG) { "Showing Skin Editor path='${editorState.currentInputPath ?: "<none>"}'" }
        preview = GdxSkinEditorPreview(engine.logger)
        reloadService =
            SkinReloadService(
                logger = engine.logger,
                assetResolver = SkinAssetResolver(),
                projectLoader = SkinProjectLoader(),
                validators =
                    listOf(
                        AtlasValidator(),
                        FontValidator(),
                        DrawableValidator(),
                        StyleReferenceValidator(),
                        UnusedResourceAnalyzer(),
                    ),
            )
        val layoutConfig =
            ImGuiLayoutConfigLoader(
                assetPath = SkinEditorUiLayoutDefaults.assetPath,
                fallback = SkinEditorUiLayoutDefaults.config,
            ).load(engine.logger, engine.sceneFiles)
        layoutTracker = ImGuiLayoutRuntimeTracker(layoutConfig)
        operations = SkinEditorOperations(editorState, engine, layoutTracker)

        editorState.pendingPathInput = editorState.currentInputPath.orEmpty()
        reloadSkin()

        world.systems.add(createUiSystem())
        world.systems.add(SkinEditorPreviewUpdateSystem(editorState, preview, previewLayouts, reloadService, engine.logger))
    }

    override fun update(dt: Float) {
        if (editorState.reloadRequested) {
            reloadSkin()
        }
        super.update(dt)
    }

    override fun overlayRender() {
        if (::preview.isInitialized) {
            preview.render()
        }
    }

    override fun resize(
        width: Int,
        height: Int,
    ) {
        if (::preview.isInitialized) {
            preview.resize(width, height)
        }
    }

    override fun dispose() {
        if (::preview.isInitialized) {
            preview.dispose()
        }
        if (::reloadService.isInitialized) {
            reloadService.dispose()
        }
        super.dispose()
    }

    private fun reloadSkin() {
        editorState.reloadRequested = false
        editorState.loadResult = reloadService.reload(editorState.currentInputPath)
        editorState.statusMessage =
            when {
                editorState.loadResult.project == null -> "Select a Scene2D skin to begin."
                editorState.loadResult.previewSkinAvailable -> "Skin loaded for preview."
                else -> "Skin loaded with problems."
            }
        editorState.previewDirty = true
        editorState.selectedStyleKey = editorState.loadResult.styleIndex.styles.firstOrNull()?.key
        editorState.selectedResourceKey =
            editorState.loadResult.resourceIndex.resources.firstOrNull()?.key
                .takeIf { editorState.selectedStyleKey == null }
        editorState.selectedProblemIndex =
            editorState.loadResult.problems.indices.firstOrNull()
                .takeIf { editorState.selectedStyleKey == null && editorState.selectedResourceKey == null }
    }

    private fun createUiSystem(): UiSystem {
        val layoutConfig = layoutTracker.currentConfig()
        val eventLogger = ImGuiWindowEventLogger(engine.logger, "SkinEditorUi")
        return UiSystem(engine.ui).also { uiSystem ->
            addPanel(
                uiSystem,
                "Toolbar",
                SkinEditorToolbarPanel(editorState, operations, layoutConfig, layoutTracker, eventLogger),
            )
            addPanel(
                uiSystem,
                "StyleTree",
                SkinEditorStyleTreePanel(editorState, layoutConfig, layoutTracker, eventLogger),
            )
            addPanel(
                uiSystem,
                "Resources",
                SkinEditorResourceBrowserPanel(editorState, layoutConfig, layoutTracker, eventLogger),
            )
            addPanel(
                uiSystem,
                "Problems",
                SkinEditorProblemsPanel(editorState, layoutConfig, layoutTracker, eventLogger),
            )
            addPanel(
                uiSystem,
                "PreviewCanvas",
                SkinEditorPreviewCanvasPanel(editorState, layoutConfig, layoutTracker, eventLogger),
            )
            addPanel(
                uiSystem,
                "Inspector",
                SkinEditorInspectorPanel(editorState, layoutConfig, layoutTracker, eventLogger),
            )
            addPanel(
                uiSystem,
                "PreviewControls",
                SkinEditorPreviewControlsPanel(editorState, operations, previewLayouts, layoutConfig, layoutTracker, eventLogger),
            )
            addPanel(
                uiSystem,
                "Logs",
                LogsPanel(
                    engine.logs,
                    layoutConfig,
                    eventLogger,
                    panelId = SkinEditorPanelIds.Logs,
                    layoutTracker = layoutTracker,
                    initialAutoScrollToLatest = true,
                ),
            )
        }
    }

    private fun addPanel(
        uiSystem: UiSystem,
        name: String,
        panel: UiPanel,
    ) {
        uiSystem.addPanel(
            UiPanel {
                try {
                    panel.draw()
                } catch (error: Exception) {
                    engine.logger.error(TAG, error) { "Skin Editor panel draw failed panel='$name': ${error.message}" }
                    throw error
                }
            },
        )
    }

    companion object {
        private const val TAG = "SkinEditorScene"
    }
}

private class SkinEditorPreviewUpdateSystem(
    private val state: SkinEditorState,
    private val preview: GdxSkinEditorPreview,
    private val previewLayouts: PreviewLayoutRegistry,
    private val reloadService: SkinReloadService,
    private val logger: Logger,
) : System() {
    override fun update(
        world: SceneWorld,
        dt: Float,
    ) {
        val rect = state.canvasRect
        if (rect.isValid) {
            val preset = SkinPreviewScreenPresets.presetOrDefault(state.previewSettings.screenPresetId)
            preview.setCanvasViewport(
                x = rect.x.toInt(),
                y = rect.y.toInt(),
                width = rect.width.toInt(),
                height = rect.height.toInt(),
                logicalWidth = preset.width,
                logicalHeight = preset.height,
                scale = state.previewSettings.scale,
                showBounds = state.previewSettings.showBounds,
            )
        } else {
            preview.clearCanvasViewport()
        }
        if (state.previewDirty) {
            val layout = previewLayouts.layoutOrDefault(state.previewLayoutId)
            val preset = SkinPreviewScreenPresets.presetOrDefault(state.previewSettings.screenPresetId)
            try {
                val buildResult =
                    preview.rebuild(
                        loadResult = state.loadResult,
                        layout = layout,
                        loadedSkin = previewSkinHandle(),
                        selectedStyleKey = state.selectedStyleKey,
                        selectedResourceName = state.selectedResourceKey?.name,
                    )
                state.previewInfo =
                    buildResult.previewInfo.copy(
                        layoutId = layout.id,
                        logicalWidth = preset.width,
                        logicalHeight = preset.height,
                        scale = state.previewSettings.scale,
                        fallbackIssueCount = buildResult.issues.size,
                    )
                replacePreviewProblems(
                    if (state.previewSettings.showFallbackWarnings) {
                        buildResult.issues.map { issue ->
                            SkinProblem(
                                severity = SkinProblemSeverity.Warning,
                                category = SkinProblemCategory.Preview,
                                message = issue.message,
                            )
                        }
                    } else {
                        emptyList()
                    },
                )
            } catch (error: Exception) {
                preview.clear()
                state.previewInfo =
                    SkinEditorPreviewStageInfo(
                        layoutId = layout.id,
                        logicalWidth = preset.width,
                        logicalHeight = preset.height,
                        scale = state.previewSettings.scale,
                    )
                replacePreviewProblems(
                    listOf(
                        SkinProblem(
                            severity = SkinProblemSeverity.Error,
                            category = SkinProblemCategory.Preview,
                            message = error.message ?: error::class.simpleName ?: "Unknown preview error.",
                        ),
                    ),
                )
                state.statusMessage = "Failed to build preview."
                logger.error(TAG, error) { "Skin Editor preview rebuild failed: ${error.message}" }
            } finally {
                state.previewDirty = false
            }
        }
        preview.update(dt)
    }

    private fun replacePreviewProblems(previewProblems: List<SkinProblem>) {
        state.loadResult =
            state.loadResult.copy(
                problems =
                    state.loadResult.problems.filterNot { problem -> problem.category == SkinProblemCategory.Preview } +
                        previewProblems,
            )
        state.loadResult = state.loadResult.copy(problems = state.loadResult.problems.sortedForDisplay())
        if (state.selectedStyleKey == null && state.selectedResourceKey == null) {
            state.selectedProblemIndex = state.loadResult.problems.indices.firstOrNull()
        }
    }

    private fun previewSkinHandle(): com.pashkd.krender.engine.tools.skin.gdx.LoadedSkinHandle? = reloadService.currentSkinHandle

    private companion object {
        private const val TAG = "SkinEditorPreviewUpdateSystem"
    }
}
