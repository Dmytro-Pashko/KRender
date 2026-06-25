package com.pashkd.krender.engine.tools.skin

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.scene.SceneConfig
import com.pashkd.krender.engine.scene.SceneConfigPresets
import com.pashkd.krender.engine.tools.skin.gdx.GdxSkinEditorPreview
import com.pashkd.krender.engine.tools.skin.gdx.GdxSkinResourcePreview
import com.pashkd.krender.engine.tools.skin.gdx.SkinReloadService
import com.pashkd.krender.engine.tools.skin.ui.SkinEditorInspectorPanel
import com.pashkd.krender.engine.tools.skin.ui.SkinEditorPreviewCanvasPanel
import com.pashkd.krender.engine.tools.skin.ui.SkinEditorPreviewControlsPanel
import com.pashkd.krender.engine.tools.skin.ui.SkinEditorProblemsPanel
import com.pashkd.krender.engine.tools.skin.ui.SkinEditorResourceBrowserPanel
import com.pashkd.krender.engine.tools.skin.ui.SkinEditorStyleInspectorPanel
import com.pashkd.krender.engine.tools.skin.ui.SkinEditorStyleTreePanel
import com.pashkd.krender.engine.tools.skin.ui.SkinEditorToolbarPanel
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigLoader
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.LogsPanel
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.UiSystem

class SkinEditorScene(
    initialSkinPath: String? = null,
) : Scene("skin_editor") {
    override val config: SceneConfig = SceneConfigPresets.SkinEditor

    private lateinit var editorState: SkinEditorState
    private lateinit var preview: GdxSkinEditorPreview
    private lateinit var resourcePreview: GdxSkinResourcePreview
    private lateinit var reloadService: SkinReloadService
    private lateinit var layoutTracker: ImGuiLayoutRuntimeTracker
    private lateinit var operations: SkinEditorOperations
    private val previewLayouts = PreviewLayoutRegistry()
    private var lastLoggedProblemsSignature: String? = null

    init {
        editorState = SkinEditorState(currentInputPath = initialSkinPath?.trim()?.replace('\\', '/'))
    }

    override fun show() {
        engine.logger.info(TAG) { "Showing Skin Editor path='${editorState.currentInputPath ?: "<none>"}'" }
        preview = GdxSkinEditorPreview(engine.logger)
        resourcePreview = GdxSkinResourcePreview(engine.logger)
        reloadService =
            SkinReloadService(
                logger = engine.logger,
                assetResolver = SkinAssetResolver(),
                projectLoader = SkinProjectLoader(),
                validators = defaultSkinValidators(),
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
        world.systems.add(SkinResourcePreviewUpdateSystem(editorState, resourcePreview, reloadService))
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
        if (::resourcePreview.isInitialized) {
            resourcePreview.dispose()
        }
        if (::reloadService.isInitialized) {
            reloadService.dispose()
        }
        super.dispose()
    }

    private fun reloadSkin() {
        val previousSelectedStyleKey = editorState.selectedStyleKey
        val previousSelectedResourceKey = editorState.selectedResourceKey
        val pendingStatusOverride = editorState.pendingStatusAfterReload
        editorState.reloadRequested = false
        editorState.loadResult = reloadService.reload(editorState.currentInputPath)
        logProblemsIfChanged(context = "reload")
        editorState.statusMessage =
            when {
                editorState.loadResult.project == null -> "Select a Scene2D skin to begin."
                editorState.loadResult.previewSkinAvailable -> "Skin loaded for preview."
                else -> "Skin loaded with problems."
            }
        resetEditorStateAfterReload(previousSelectedStyleKey, previousSelectedResourceKey)
        pendingStatusOverride?.let { status ->
            editorState.statusMessage = status
            editorState.pendingStatusAfterReload = null
        }
    }

    private fun logProblemsIfChanged(context: String) {
        val signature = skinProblemsSignature(editorState.loadResult.problems)
        if (signature == lastLoggedProblemsSignature) return
        lastLoggedProblemsSignature = signature
        logSkinProblemsSnapshot(
            logger = engine.logger,
            tag = TAG,
            context = context,
            inputPath = editorState.currentInputPath,
            problems = editorState.loadResult.problems,
        )
    }

    private fun resetEditorStateAfterReload(
        preferredStyleKey: StyleKey? = null,
        preferredResourceKey: SkinResourceKey? = null,
    ) {
        editorState.previewDirty = true
        editorState.editSession = SkinEditSessionFactory.create(editorState.loadResult)
        editorState.selectedEditFieldName = null
        editorState.selectedStyleKey =
            preferredStyleKey?.takeIf { key ->
                editorState.loadResult.styleIndex.styles
                    .any { style -> style.key == key }
            } ?: editorState.loadResult.styleIndex.styles
                .firstOrNull()
                ?.key
        editorState.selectedResourceKey =
            preferredResourceKey?.takeIf { key ->
                editorState.selectedStyleKey == null &&
                    editorState.loadResult.resourceIndex.resources
                        .any { resource -> resource.key == key }
            } ?: editorState.loadResult.resourceIndex.resources
                .firstOrNull()
                ?.key
                .takeIf { editorState.selectedStyleKey == null }
        editorState.selectedProblemIndex =
            editorState.loadResult.problems.indices
                .firstOrNull()
                .takeIf { editorState.selectedStyleKey == null && editorState.selectedResourceKey == null }
        editorState.pendingPreviewPointerEvents.clear()
        editorState.previewSettings.interaction.hoveredActorPath = null
        editorState.previewSettings.interaction.focusedActorPath = null
        editorState.previewSettings.interaction.lastInputStatus = null
        editorState.previewSettings.interaction.cursorCanvasX = null
        editorState.previewSettings.interaction.cursorCanvasY = null
        editorState.previewSettings.interaction.cursorStageX = null
        editorState.previewSettings.interaction.cursorStageY = null
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
                SkinEditorStyleTreePanel(editorState, operations, layoutConfig, layoutTracker, eventLogger),
            )
            addPanel(
                uiSystem,
                "Resources",
                SkinEditorResourceBrowserPanel(editorState, operations, engine.ui, layoutConfig, layoutTracker, eventLogger),
            )
            addPanel(
                uiSystem,
                "Problems",
                SkinEditorProblemsPanel(editorState, operations, layoutConfig, layoutTracker, eventLogger),
            )
            addPanel(
                uiSystem,
                "PreviewCanvas",
                SkinEditorPreviewCanvasPanel(editorState, operations, layoutConfig, layoutTracker, eventLogger),
            )
            addPanel(
                uiSystem,
                "Inspector",
                SkinEditorInspectorPanel(editorState, layoutConfig, layoutTracker, eventLogger),
            )
            addPanel(
                uiSystem,
                "StyleInspector",
                SkinEditorStyleInspectorPanel(editorState, operations, layoutConfig, layoutTracker, eventLogger),
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

private fun defaultSkinValidators(): List<SkinValidator> =
    listOf(
        AtlasValidator(),
        FontValidator(),
        ColorValidator(),
        DuplicateResourceNameValidator(),
        DrawableValidator(),
        StyleReferenceValidator(),
        UnusedResourceAnalyzer(),
    )

private class SkinEditorPreviewUpdateSystem(
    private val state: SkinEditorState,
    private val preview: GdxSkinEditorPreview,
    private val previewLayouts: PreviewLayoutRegistry,
    private val reloadService: SkinReloadService,
    private val logger: Logger,
) : System() {
    @Suppress("LongMethod")
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
                showCheckerboard = state.previewSettings.showCheckerboard,
                showBounds = state.previewSettings.showBounds,
                highlightSelectedStyle = state.previewSettings.highlightSelectedStyle,
                cameraPanX = state.previewSettings.camera.panX,
                cameraPanY = state.previewSettings.camera.panY,
                cameraZoom = state.previewSettings.camera.zoom,
            )
        } else {
            preview.clearCanvasViewport()
        }
        if (state.pendingPreviewPointerEvents.isNotEmpty()) {
            var latestFeedback: SkinPreviewInteractionFeedback? = null
            state.pendingPreviewPointerEvents.forEach { event ->
                latestFeedback = preview.handlePointerEvent(event)
            }
            state.pendingPreviewPointerEvents.clear()
            latestFeedback?.let { feedback ->
                state.previewSettings.interaction.hoveredActorPath = feedback.hoveredActorPath
                state.previewSettings.interaction.focusedActorPath = feedback.focusedActorPath
                state.previewSettings.interaction.cursorCanvasX = feedback.cursorCanvasX
                state.previewSettings.interaction.cursorCanvasY = feedback.cursorCanvasY
                state.previewSettings.interaction.cursorStageX = feedback.cursorStageX
                state.previewSettings.interaction.cursorStageY = feedback.cursorStageY
                feedback.lastInputStatus?.let { status ->
                    state.previewSettings.interaction.lastInputStatus = status
                }
            }
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
                        editSession = state.editSession,
                        previewText = state.previewSettings.text,
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
        val selectedProblem = state.selectedProblemIndex?.let(state.loadResult.problems::getOrNull)
        state.loadResult =
            state.loadResult.copy(
                problems =
                    state.loadResult.problems.filterNot { problem -> problem.category == SkinProblemCategory.Preview } +
                        previewProblems,
            )
        state.loadResult = state.loadResult.copy(problems = state.loadResult.problems.sortedForDisplay())
        state.selectedProblemIndex = selectedProblem?.let(state.loadResult.problems::indexOf)?.takeIf { index -> index >= 0 }
        if (state.selectedProblemIndex == null && state.selectedStyleKey == null && state.selectedResourceKey == null) {
            state.selectedProblemIndex =
                state.loadResult.problems.indices
                    .firstOrNull()
        }
    }

    private fun previewSkinHandle(): com.pashkd.krender.engine.tools.skin.gdx.LoadedSkinHandle? = reloadService.currentSkinHandle

    private companion object {
        private const val TAG = "SkinEditorPreviewUpdateSystem"
    }
}

private class SkinResourcePreviewUpdateSystem(
    private val state: SkinEditorState,
    private val resourcePreview: GdxSkinResourcePreview,
    private val reloadService: SkinReloadService,
) : System() {
    override fun update(
        world: SceneWorld,
        dt: Float,
    ) {
        val selectedResource =
            state.loadResult.resourceIndex.resources
                .firstOrNull { it.key == state.selectedResourceKey }
        state.resourceVisualPreviewInfo =
            resourcePreview.update(
                project = state.loadResult.project,
                resourceIndex = state.loadResult.resourceIndex,
                selectedResource = selectedResource,
                previewState = state.resourceVisualPreview,
                loadedSkin = reloadService.currentSkinHandle,
                editSession = state.editSession,
            )
    }
}

private fun skinProblemsSignature(problems: List<SkinProblem>): String =
    problems.joinToString(separator = "\n") { problem ->
        listOf(
            problem.severity.name,
            problem.category.name,
            problem.message,
            problem.source.orEmpty(),
            problem.suggestedFix.orEmpty(),
            problem.styleKey?.let { "${it.type}.${it.name}" }.orEmpty(),
            problem.resourceKey?.name.orEmpty(),
        ).joinToString("|")
    }

private fun logSkinProblemsSnapshot(
    logger: Logger,
    tag: String,
    context: String,
    inputPath: String?,
    problems: List<SkinProblem>,
) {
    logger.info(tag) {
        "Skin Editor problems snapshot context='$context' path='${inputPath ?: "<none>"}' count=${problems.size}"
    }
    problems.forEachIndexed { index, problem ->
        val message = buildSkinProblemLogLine(context, inputPath, index, problem)
        when (problem.severity) {
            SkinProblemSeverity.Info -> logger.info(tag) { message }
            SkinProblemSeverity.Warning -> logger.warn(tag) { message }
            SkinProblemSeverity.Error -> logger.error(tag) { message }
        }
    }
}

private fun buildSkinProblemLogLine(
    context: String,
    inputPath: String?,
    index: Int,
    problem: SkinProblem,
): String {
    val source = problem.source?.let { " source='$it'" }.orEmpty()
    val suggestedFix = problem.suggestedFix?.let { " suggestedFix='$it'" }.orEmpty()
    val style = problem.styleKey?.let { " style='${it.type}.${it.name}'" }.orEmpty()
    val resource = problem.resourceKey?.let { " resource='${it.category}.${it.name}'" }.orEmpty()
    return "Skin Editor problem[$index] context='$context' path='${inputPath ?: "<none>"}' severity=${problem.severity} category=${problem.category}$style$resource$source suggestedMessage='${problem.message}'$suggestedFix"
}
