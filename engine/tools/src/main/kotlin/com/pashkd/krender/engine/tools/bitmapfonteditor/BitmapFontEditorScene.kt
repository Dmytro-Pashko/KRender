package com.pashkd.krender.engine.tools.bitmapfonteditor

import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.assets.importing.FileDialogService
import com.pashkd.krender.engine.assets.importing.NoOpFileDialogService
import com.pashkd.krender.engine.scene.SceneConfig
import com.pashkd.krender.engine.scene.SceneConfigPresets
import com.pashkd.krender.engine.tools.bitmapfonteditor.panels.BitmapFontToolbarPanel
import com.pashkd.krender.engine.tools.bitmapfonteditor.panels.FontDiagnosticsPanel
import com.pashkd.krender.engine.tools.bitmapfonteditor.panels.FontGenerationPanel
import com.pashkd.krender.engine.tools.bitmapfonteditor.panels.FontPageCanvasPanel
import com.pashkd.krender.engine.tools.bitmapfonteditor.panels.GlyphInspectorPanel
import com.pashkd.krender.engine.tools.bitmapfonteditor.panels.GlyphListPanel
import com.pashkd.krender.engine.tools.bitmapfonteditor.workflow.OpenBitmapFontWorkflow
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigLoader
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.LogsPanel
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.UiSystem

class BitmapFontEditorScene(
    initialFontPath: String? = null,
    private val fileDialogService: FileDialogService = NoOpFileDialogService,
) : Scene("bitmap_font_editor") {
    override val config: SceneConfig = SceneConfigPresets.BitmapFontEditor

    private val editorState = BitmapFontEditorState(inputPath = initialFontPath?.trim()?.replace('\\', '/'))
    private lateinit var controller: BitmapFontEditorController
    private lateinit var layoutTracker: ImGuiLayoutRuntimeTracker

    private lateinit var openWorkflow: OpenBitmapFontWorkflow

    override fun show() {
        engine.logger.info(TAG) { "Showing Bitmap Font Editor path='${editorState.inputPath ?: "<none>"}'" }
        val layoutConfig =
            ImGuiLayoutConfigLoader(
                assetPath = BitmapFontEditorUiLayoutDefaults.assetPath,
                fallback = BitmapFontEditorUiLayoutDefaults.config,
            ).load(engine.logger, engine.sceneFiles)
        layoutTracker = ImGuiLayoutRuntimeTracker(layoutConfig)
        controller = BitmapFontEditorController(editorState, engine, layoutTracker, fileDialogService)
        openWorkflow = OpenBitmapFontWorkflow(editorState, engine)
        editorState.inputPath?.let { path -> openWorkflow.openFromPath(path) }
        world.systems.add(BitmapFontEditorPreviewSyncSystem(editorState, engine))
        world.systems.add(createUiSystem())
    }

    private fun createUiSystem(): UiSystem {
        val layoutConfig = layoutTracker.currentConfig()
        val eventLogger = ImGuiWindowEventLogger(engine.logger, "BitmapFontEditorUi")
        return UiSystem(engine.ui).also { uiSystem ->
            addPanel(uiSystem, "Toolbar", BitmapFontToolbarPanel(editorState, controller, layoutConfig, layoutTracker, eventLogger))
            addPanel(uiSystem, "Preview", FontPageCanvasPanel(editorState, controller, layoutConfig, layoutTracker, eventLogger))
            addPanel(uiSystem, "GlyphList", GlyphListPanel(editorState, controller, layoutConfig, layoutTracker, eventLogger))
            addPanel(uiSystem, "Inspector", GlyphInspectorPanel(editorState, controller, layoutConfig, layoutTracker, eventLogger))
            addPanel(uiSystem, "Tools", FontGenerationPanel(editorState, controller, layoutConfig, layoutTracker, eventLogger))
            addPanel(uiSystem, "Diagnostics", FontDiagnosticsPanel(editorState, layoutConfig, layoutTracker, eventLogger))
            addPanel(
                uiSystem,
                "Logs",
                LogsPanel(
                    engine.logs,
                    layoutConfig,
                    eventLogger,
                    panelId = BitmapFontEditorPanelIds.Logs,
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
                    engine.logger.error(TAG, error) { "Bitmap Font Editor panel draw failed panel='$name': ${error.message}" }
                    throw error
                }
            },
        )
    }

    companion object {
        private const val TAG = "BitmapFontEditorScene"
    }
}

private class BitmapFontEditorPreviewSyncSystem(
    private val state: BitmapFontEditorState,
    private val engine: com.pashkd.krender.engine.api.EngineContext,
) : System() {
    private var lastPreviewPath: String? = null

    @Suppress("ReturnCount")
    override fun update(
        world: SceneWorld,
        dt: Float,
    ) {
        val document = state.document ?: return
        val pageIndex = state.selectedPageIndex.coerceIn(0, (document.pages.size - 1).coerceAtLeast(0))
        val page = document.pages.getOrNull(pageIndex) ?: return
        val resolvedPath = state.previewTexturePath?.let { path -> java.io.File(engine.assetRegistry.baseDir(), path).path } ?: page.resolvedPath ?: return
        val assetRoot = engine.assetRegistry.baseDir()
        val rootPath = assetRoot.canonicalPath.replace('\\', '/')
        val pagePath =
            java.io
                .File(resolvedPath)
                .canonicalPath
                .replace('\\', '/')
        val relPath = if (pagePath.startsWith(rootPath)) pagePath.removePrefix(rootPath).removePrefix("/") else return
        if (relPath != lastPreviewPath || state.previewTextureRevision > 0L) {
            lastPreviewPath = relPath
            val ref =
                com.pashkd.krender.engine.api.AssetRef
                    .texture(relPath)
            if (!engine.assets.isLoaded(ref)) {
                engine.assets.queue(ref)
            }
        }
        val handle = engine.assets.texturePreviewHandle(relPath)
        state.texturePreviewHandle = handle
        state.textureWidth = handle?.width ?: document.common?.scaleW ?: 0
        state.textureHeight = handle?.height ?: document.common?.scaleH ?: 0
        state.previewTextureRevision = 0L
    }
}
