package com.pashkd.krender.engine.tools.texturemanager

import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.scene.SceneConfig
import com.pashkd.krender.engine.scene.SceneConfigPresets
import com.pashkd.krender.engine.tools.texturemanager.gdx.GdxTextureManagerPreview
import com.pashkd.krender.engine.tools.texturemanager.ui.TextureManagerAssetBrowserPanel
import com.pashkd.krender.engine.tools.texturemanager.ui.TextureManagerAtlasRegionsPanel
import com.pashkd.krender.engine.tools.texturemanager.ui.TextureManagerDiagnosticsPanel
import com.pashkd.krender.engine.tools.texturemanager.ui.TextureManagerInspectorPanel
import com.pashkd.krender.engine.tools.texturemanager.ui.TextureManagerPreviewCanvasPanel
import com.pashkd.krender.engine.tools.texturemanager.ui.TextureManagerToolbarPanel
import com.pashkd.krender.engine.tools.texturemanager.ui.TextureManagerToolsPanel
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigLoader
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.LogsPanel
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.UiSystem

class TextureManagerScene(
    initialTexturePath: String? = null,
) : Scene("texture_manager") {
    override val config: SceneConfig = SceneConfigPresets.TextureManager

    private lateinit var editorState: TextureManagerState
    private lateinit var layoutTracker: ImGuiLayoutRuntimeTracker
    private lateinit var operations: TextureManagerOperations
    private lateinit var loader: TextureManagerProjectLoader
    private lateinit var preview: GdxTextureManagerPreview

    init {
        editorState = TextureManagerState(currentInputPath = initialTexturePath?.trim()?.replace('\\', '/'))
    }

    override fun show() {
        engine.logger.info(TAG) { "Showing Texture Manager path='${editorState.currentInputPath ?: "<none>"}'" }
        val layoutConfig =
            ImGuiLayoutConfigLoader(
                assetPath = TextureManagerUiLayoutDefaults.assetPath,
                fallback = TextureManagerUiLayoutDefaults.config,
            ).load(engine.logger, engine.sceneFiles)
        layoutTracker = ImGuiLayoutRuntimeTracker(layoutConfig)
        operations = TextureManagerOperations(editorState, engine, layoutTracker)
        loader = TextureManagerProjectLoader(engine.logger, engine.assetRegistry)
        preview = GdxTextureManagerPreview(engine.logger)
        editorState.pendingPathInput = editorState.currentInputPath.orEmpty()
        reloadProject()
        world.systems.add(createUiSystem())
        world.systems.add(TextureManagerPreviewSyncSystem(editorState, preview, engine.logger))
    }

    override fun update(dt: Float) {
        if (editorState.reloadRequested) {
            reloadProject()
        }
        super.update(dt)
    }

    override fun dispose() {
        if (::preview.isInitialized) {
            preview.dispose()
        }
        super.dispose()
    }

    private fun reloadProject() {
        editorState.reloadRequested = false
        val result = loader.load(editorState.currentInputPath)
        editorState.project = result.project
        editorState.diagnostics = result.diagnostics
        if (editorState.project.assets.isNotEmpty() && editorState.selectedAssetId == null) {
            val preferredPath = editorState.project.selectedTexturePath ?: editorState.project.selectedAtlasPath
            val preferredAsset = preferredPath?.let { path -> editorState.project.assets.firstOrNull { it.path == path } }
            editorState.selectedAssetId = preferredAsset?.id ?: editorState.project.assets.first().id
        }
        editorState.selectedAssetId?.let { operations.selectAsset(it) }
        editorState.statusMessage =
            when {
                editorState.project.rootDirectory == null -> "Open a texture, atlas, or directory to begin."
                editorState.diagnostics.any { it.severity == TextureManagerDiagnosticSeverity.Error } -> "Loaded with errors."
                editorState.diagnostics.any { it.severity == TextureManagerDiagnosticSeverity.Warning } -> "Loaded with warnings."
                else -> "Texture Manager ready."
            }
    }

    private fun createUiSystem(): UiSystem {
        val layoutConfig = layoutTracker.currentConfig()
        val eventLogger = ImGuiWindowEventLogger(engine.logger, "TextureManagerUi")
        return UiSystem(engine.ui).also { uiSystem ->
            addPanel(uiSystem, "Toolbar", TextureManagerToolbarPanel(editorState, operations, layoutConfig, layoutTracker, eventLogger))
            addPanel(uiSystem, "Assets", TextureManagerAssetBrowserPanel(editorState, operations, layoutConfig, layoutTracker, eventLogger))
            addPanel(uiSystem, "Preview", TextureManagerPreviewCanvasPanel(editorState, operations, engine.ui, layoutConfig, layoutTracker, eventLogger))
            addPanel(uiSystem, "Inspector", TextureManagerInspectorPanel(editorState, layoutConfig, layoutTracker, eventLogger))
            addPanel(uiSystem, "Regions", TextureManagerAtlasRegionsPanel(editorState, operations, layoutConfig, layoutTracker, eventLogger))
            addPanel(uiSystem, "Tools", TextureManagerToolsPanel(editorState, operations, layoutConfig, layoutTracker, eventLogger))
            addPanel(uiSystem, "Diagnostics", TextureManagerDiagnosticsPanel(editorState, operations, layoutConfig, layoutTracker, eventLogger))
            addPanel(
                uiSystem,
                "Logs",
                LogsPanel(
                    engine.logs,
                    layoutConfig,
                    eventLogger,
                    panelId = TextureManagerPanelIds.Logs,
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
                    engine.logger.error(TAG, error) { "Texture Manager panel draw failed panel='$name': ${error.message}" }
                    throw error
                }
            },
        )
    }

    companion object {
        private const val TAG = "TextureManagerScene"
    }
}

private class TextureManagerPreviewSyncSystem(
    private val state: TextureManagerState,
    private val preview: GdxTextureManagerPreview,
    private val logger: com.pashkd.krender.engine.api.Logger,
) : System() {
    override fun update(
        world: SceneWorld,
        dt: Float,
    ) {
        val previewPath = state.selectedPreviewTexturePath()
        val asset = state.selectedAsset()
        val atlasPage = state.selectedAtlasPageName
        state.previewInfo =
            preview.update(
                texturePath = previewPath,
                atlasPageName = atlasPage,
                selectedAssetPath = asset?.path,
            )
        if (state.previewInfo.texturePreviewHandle == null && previewPath != null) {
            logger.warn(TAG) { "Texture preview unavailable path='$previewPath'" }
        }
    }

    companion object {
        private const val TAG = "TextureManagerPreviewSync"
    }
}
