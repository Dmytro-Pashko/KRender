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
        editorState.currentInputPath?.let { path ->
            engine.logger.info(TAG) { "Texture Manager received launch path='$path'" }
        }
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
        val selectedAssetStillExists =
            editorState.selectedAssetId?.let { selectedId ->
                editorState.project.assets.any { asset -> asset.id == selectedId }
            } ?: false
        if (!selectedAssetStillExists && editorState.selectedAssetId != null) {
            engine.logger.info(TAG) {
                "Texture Manager cleared stale selection asset='${editorState.selectedAssetId?.value}' after reload"
            }
            editorState.clearPreviewSelection()
        }
        ensureValidSelectionAfterReload()
        editorState.statusMessage =
            when {
                editorState.project.rootDirectory == null -> "Open a texture, atlas, or directory to begin."
                editorState.diagnostics.any { it.severity == TextureManagerDiagnosticSeverity.Error } -> "Loaded ${loadedPathLabel()} with errors."
                editorState.diagnostics.any { it.severity == TextureManagerDiagnosticSeverity.Warning } -> "Loaded ${loadedPathLabel()} with warnings."
                editorState.currentInputPath != null -> "Loaded ${loadedPathLabel()}."
                else -> "Texture Manager ready."
            }
        editorState.project.resolvedInputPath?.let { resolvedPath ->
            if (editorState.currentInputPath != null) {
                engine.logger.info(TAG) {
                    "Texture Manager loaded input path='${editorState.currentInputPath}' resolved='$resolvedPath' diagnostics=${editorState.diagnostics.size}"
                }
            }
        }
        engine.logger.info(TAG) {
            "Texture Manager reload completed assets=${editorState.project.assets.size} textures=${editorState.project.discoveredTextureFiles.size} atlases=${editorState.project.discoveredAtlasFiles.size} diagnostics=${editorState.diagnostics.size}"
        }
    }

    private fun loadedPathLabel(): String = "'${editorState.project.resolvedInputPath ?: editorState.currentInputPath ?: "<unknown>"}'"

    private fun ensureValidSelectionAfterReload() {
        val validAssetId =
            editorState.selectedAssetId?.takeIf { selectedId ->
                editorState.project.assets.any { asset -> asset.id == selectedId }
            } ?: preferredAssetId()

        if (validAssetId != null) {
            operations.selectAsset(validAssetId)
            engine.logger.info(TAG) { "Texture Manager reload selected asset='${validAssetId.value}'" }
        } else {
            editorState.clearPreviewSelection()
        }
    }

    private fun preferredAssetId(): TextureAssetId? {
        val preferredPath = editorState.project.selectedTexturePath ?: editorState.project.selectedAtlasPath
        val preferredAsset = preferredPath?.let { path -> editorState.project.assets.firstOrNull { asset -> asset.path == path } }
        return preferredAsset?.id ?: editorState.project.assets.firstOrNull()?.id
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
    private var lastResolvedPreviewKey: String? = null
    private var lastMissingPreviewKey: String? = null

    override fun update(
        world: SceneWorld,
        dt: Float,
    ) {
        val previewPath = state.selectedPreviewTexturePath()
        val asset = state.selectedAsset()
        val atlasPage = state.selectedAtlasPageName
        logPreviewResolution(asset?.path, previewPath, atlasPage)
        state.previewInfo =
            preview.update(
                texturePath = previewPath,
                atlasPageName = atlasPage,
                selectedAssetPath = asset?.path,
            )
    }

    private fun logPreviewResolution(
        assetPath: String?,
        previewPath: String?,
        atlasPageName: String?,
    ) {
        if (previewPath != null) {
            val resolutionKey = "${assetPath.orEmpty()}|${atlasPageName.orEmpty()}|$previewPath"
            if (lastResolvedPreviewKey != resolutionKey) {
                lastResolvedPreviewKey = resolutionKey
                lastMissingPreviewKey = null
                if (assetPath?.endsWith(".atlas", ignoreCase = true) == true || state.project.selectedAtlasPath != null) {
                    logger.info(TAG) {
                        "Texture Manager resolved atlas preview page='${atlasPageName ?: "<first>"}' asset='${assetPath ?: state.project.selectedAtlasPath ?: "<none>"}' texture='$previewPath'"
                    }
                }
            }
            return
        }

        val atlasPath = assetPath?.takeIf { it.endsWith(".atlas", ignoreCase = true) } ?: state.project.selectedAtlasPath
        if (atlasPath != null) {
            val missingKey = "${atlasPath}|${atlasPageName.orEmpty()}"
            if (lastMissingPreviewKey != missingKey) {
                lastMissingPreviewKey = missingKey
                lastResolvedPreviewKey = null
                val atlasDocument = state.project.atlasDocuments[atlasPath]
                val reason =
                    when {
                        atlasDocument == null -> "atlas document missing"
                        atlasDocument.pages.isEmpty() -> "atlas has no pages"
                        else -> "page texture path could not be resolved"
                    }
                logger.warn(TAG) {
                    "Texture Manager could not resolve atlas preview asset='$atlasPath' page='${atlasPageName ?: "<first>"}': $reason"
                }
            }
        }
    }

    companion object {
        private const val TAG = "TextureManagerPreviewSync"
    }
}
