package com.pashkd.krender.engine.tools.textureatlaseditor

import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.assets.importing.AwtFileDialogService
import com.pashkd.krender.engine.scene.SceneConfig
import com.pashkd.krender.engine.scene.SceneConfigPresets
import com.pashkd.krender.engine.tools.textureatlaseditor.TextureAtlasCanvasMode.FinalPackedAtlas
import com.pashkd.krender.engine.tools.textureatlaseditor.FontAtlasResource
import com.pashkd.krender.engine.tools.textureatlaseditor.gdx.GdxNinePatchPixelReader
import com.pashkd.krender.engine.tools.textureatlaseditor.gdx.GdxTextureAtlasSaveService
import com.pashkd.krender.engine.tools.textureatlaseditor.gdx.GdxTextureAtlasEditorPreview
import com.pashkd.krender.engine.tools.textureatlaseditor.ui.TextureAtlasEditorDiagnosticsPanel
import com.pashkd.krender.engine.tools.textureatlaseditor.ui.TextureAtlasEditorInspectorPanel
import com.pashkd.krender.engine.tools.textureatlaseditor.ui.TextureAtlasEditorPreviewCanvasPanel
import com.pashkd.krender.engine.tools.textureatlaseditor.ui.TextureAtlasEditorResourcesPanel
import com.pashkd.krender.engine.tools.textureatlaseditor.ui.TextureAtlasEditorToolbarPanel
import com.pashkd.krender.engine.tools.textureatlaseditor.ui.TextureAtlasEditorToolsPanel
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigLoader
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.LogsPanel
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.UiSystem

class TextureAtlasEditorScene(
    initialAtlasPath: String? = null,
) : Scene("texture_atlas_editor") {
    override val config: SceneConfig = SceneConfigPresets.TextureAtlasEditor

    private lateinit var editorState: TextureAtlasEditorState
    private lateinit var layoutTracker: ImGuiLayoutRuntimeTracker
    private lateinit var operations: TextureAtlasEditorOperations
    private lateinit var loader: TextureAtlasEditorProjectLoader
    private lateinit var preview: GdxTextureAtlasEditorPreview

    init {
        editorState = TextureAtlasEditorState(currentInputPath = initialAtlasPath?.trim()?.replace('\\', '/'))
    }

    override fun show() {
        engine.logger.info(TAG) { "Showing Texture Atlas Editor path='${editorState.currentInputPath ?: "<none>"}'" }
        editorState.currentInputPath?.let { path ->
            engine.logger.info(TAG) { "Texture Atlas Editor received launch path='$path'" }
        }
        val layoutConfig =
            ImGuiLayoutConfigLoader(
                assetPath = TextureAtlasEditorUiLayoutDefaults.assetPath,
                fallback = TextureAtlasEditorUiLayoutDefaults.config,
            ).load(engine.logger, engine.sceneFiles)
        layoutTracker = ImGuiLayoutRuntimeTracker(layoutConfig)
        operations =
            TextureAtlasEditorOperations(
                editorState,
                engine,
                layoutTracker,
                fileDialogService = AwtFileDialogService(),
                atlasSaveService = GdxTextureAtlasSaveService(engine.logger),
            )
        loader =
            TextureAtlasEditorProjectLoader(
                logger = engine.logger,
                assetRegistry = engine.assetRegistry,
                ninePatchPixelReader = GdxNinePatchPixelReader(),
            )
        preview = GdxTextureAtlasEditorPreview(engine.logger)
        editorState.pendingPathInput = editorState.currentInputPath.orEmpty()
        reloadProject()
        world.systems.add(createUiSystem())
        world.systems.add(TextureAtlasEditorPreviewSyncSystem(editorState, preview, engine.logger))
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
        editorState.dirty = false
        syncPackingSettingsFromAtlas()
        rebuildResources()
        val selectedAssetStillExists =
            editorState.selectedAssetId?.let { selectedId ->
                editorState.project.assets.any { asset -> asset.id == selectedId }
            } ?: false
        if (!selectedAssetStillExists && editorState.selectedAssetId != null) {
            engine.logger.info(TAG) {
                "Texture Atlas Editor cleared stale selection asset='${editorState.selectedAssetId?.value}' after reload"
            }
            editorState.clearPreviewSelection()
        }
        ensureValidSelectionAfterReload()
        editorState.statusMessage =
            when {
                editorState.project.rootDirectory == null -> "Open a texture, atlas, or directory to begin."
                editorState.diagnostics.any { it.severity == TextureAtlasEditorDiagnosticSeverity.Error } -> "Loaded ${loadedPathLabel()} with errors."
                editorState.diagnostics.any { it.severity == TextureAtlasEditorDiagnosticSeverity.Warning } -> "Loaded ${loadedPathLabel()} with warnings."
                editorState.currentInputPath != null -> "Loaded ${loadedPathLabel()}."
                else -> "Texture Atlas Editor ready."
            }
        editorState.project.resolvedInputPath?.let { resolvedPath ->
            if (editorState.currentInputPath != null) {
                engine.logger.info(TAG) {
                    "Texture Atlas Editor loaded input path='${editorState.currentInputPath}' resolved='$resolvedPath' diagnostics=${editorState.diagnostics.size}"
                }
            }
        }
        engine.logger.info(TAG) {
            "Texture Atlas Editor reload completed assets=${editorState.project.assets.size} textures=${editorState.project.discoveredTextureFiles.size} atlases=${editorState.project.discoveredAtlasFiles.size} diagnostics=${editorState.diagnostics.size}"
        }
    }

    private fun syncPackingSettingsFromAtlas() {
        val atlasPath = editorState.project.selectedAtlasPath ?: return
        val atlas = editorState.project.atlasDocuments[atlasPath] ?: return
        val firstPage = atlas.pages.firstOrNull() ?: return
        val encodedSize = firstPage.details["size"] ?: return
        val width = encodedSize.substringBefore(',').trim().toIntOrNull() ?: return
        val height = encodedSize.substringAfter(',', "").trim().toIntOrNull() ?: return
        editorState.packing.settings.maxPageWidth = width
        editorState.packing.settings.maxPageHeight = height
    }

    private fun loadedPathLabel(): String = "'${editorState.project.resolvedInputPath ?: editorState.currentInputPath ?: "<unknown>"}'"

    private fun ensureValidSelectionAfterReload() {
        val validAssetId =
            editorState.selectedAssetId?.takeIf { selectedId ->
                editorState.project.assets.any { asset -> asset.id == selectedId }
            } ?: preferredAssetId()

        if (validAssetId != null) {
            operations.selectAsset(validAssetId)
            engine.logger.info(TAG) { "Texture Atlas Editor reload selected asset='${validAssetId.value}'" }
        } else {
            editorState.clearPreviewSelection()
        }
    }

    private fun preferredAssetId(): TextureAssetId? {
        val preferredPath = editorState.project.selectedTexturePath ?: editorState.project.selectedAtlasPath
        val preferredAsset = preferredPath?.let { path -> editorState.project.assets.firstOrNull { asset -> asset.path == path } }
        return preferredAsset?.id ?: editorState.project.assets.firstOrNull()?.id
    }

    private fun rebuildResources() {
        val atlasPath = editorState.project.selectedAtlasPath
        val atlasDocument = atlasPath?.let { path -> editorState.project.atlasDocuments[path] }
        val selectedAtlasDirectory = atlasPath?.substringBeforeLast('/', "")
        val carryOverResources =
            editorState.resources.items.filter { resource ->
                resource !is FontAtlasResource &&
                resource.atlasRegionIdOrNull() == null &&
                    resource.sourcePathOrNull()?.let { path -> java.io.File(path).isFile } != false
            }
        val atlasResources =
            atlasDocument
                ?.regions
                ?.mapIndexedNotNull { index, region ->
                    val sourcePath =
                        resolveAtlasPreviewTexturePath(
                            atlasPath = region.id.atlasPath,
                            atlas = atlasDocument,
                            selectedPageName = region.id.pageName,
                        ) ?: return@mapIndexedNotNull null
                    val size = region.size
                    val xy = region.xy
                    if (size == null || xy == null) return@mapIndexedNotNull null
                    val resourceId = "resource:${region.id.atlasPath}:${region.id.pageName}:${region.id.regionName}:${region.index ?: index}"
                    if (region.split.isNotEmpty() || region.pad.isNotEmpty()) {
                        NinePatchAtlasResource(
                            id = resourceId,
                            name = region.id.regionName,
                            sourcePath = sourcePath,
                            sourceX = xy.first,
                            sourceY = xy.second,
                            sourceWidth = size.first,
                            sourceHeight = size.second,
                            split = region.split,
                            pad = region.pad,
                            atlasRegionId = region.id,
                            atlasIndex = region.index,
                        )
                    } else {
                        ImageAtlasResource(
                            id = resourceId,
                            name = region.id.regionName,
                            sourcePath = sourcePath,
                            sourceX = xy.first,
                            sourceY = xy.second,
                            sourceWidth = size.first,
                            sourceHeight = size.second,
                            atlasRegionId = region.id,
                            atlasIndex = region.index,
                        )
                    }
                }.orEmpty()
        val atlasRegionByName =
            atlasDocument
                ?.regions
                ?.associateBy { region -> region.id.regionName.lowercase() }
                .orEmpty()
        val fontRegionNames =
            editorState.project.fontDocuments.entries
                .asSequence()
                .filter { (path, _) ->
                    val fontDirectory = path.substringBeforeLast('/', "")
                    selectedAtlasDirectory != null && fontDirectory == selectedAtlasDirectory
                }.map { (path, document) ->
                    java.io.File(path).nameWithoutExtension.lowercase()
                }.toSet()
        val fontResources =
            editorState.project.fontDocuments.entries
                .asSequence()
                .filter { (path, _) ->
                    val fontDirectory = path.substringBeforeLast('/', "")
                    selectedAtlasDirectory != null && fontDirectory == selectedAtlasDirectory
                }
                .sortedBy { (path, _) -> java.io.File(path).nameWithoutExtension.lowercase() }
                .mapNotNull { (path, document) ->
                    val fontName = java.io.File(path).nameWithoutExtension
                    val region = atlasRegionByName[fontName.lowercase()] ?: return@mapNotNull null
                    val xy = region.xy ?: return@mapNotNull null
                    val size = region.size ?: return@mapNotNull null
                    val atlasTexturePath =
                        resolveAtlasPreviewTexturePath(
                            atlasPath = region.id.atlasPath,
                            atlas = atlasDocument,
                            selectedPageName = region.id.pageName,
                        ) ?: return@mapNotNull null
                    FontAtlasResource(
                        id = "resource:font:$path",
                        name = fontName,
                        sourcePath = path,
                        documentPath = path,
                        pageTexturePaths = document.pages.mapNotNull { page -> page.resolvedPath },
                        atlasTexturePath = atlasTexturePath,
                        atlasRegionId = region.id,
                        sourceX = xy.first,
                        sourceY = xy.second,
                        sourceWidth = size.first,
                        sourceHeight = size.second,
                        glyphCount = document.glyphs.size,
                        kerningCount = document.kernings.size,
                    )
                }.toList()
        val filteredAtlasResources =
            atlasResources.filterNot { resource ->
                resource is ImageAtlasResource && resource.name.lowercase() in fontRegionNames
            }
        val rebuiltItems = filteredAtlasResources + fontResources + carryOverResources
        val selectedResourceId =
            editorState.resources.selectedResourceId?.takeIf { selectedId ->
                rebuiltItems.any { resource -> resource.id == selectedId }
            } ?: rebuiltItems.firstOrNull()?.id
        editorState.resources =
            TextureAtlasResourceState(
                items = rebuiltItems,
                selectedResourceId = selectedResourceId,
            )
    }

    private fun createUiSystem(): UiSystem {
        val layoutConfig = layoutTracker.currentConfig()
        val eventLogger = ImGuiWindowEventLogger(engine.logger, "TextureAtlasEditorUi")
        return UiSystem(engine.ui).also { uiSystem ->
            addPanel(uiSystem, "Toolbar", TextureAtlasEditorToolbarPanel(editorState, operations, layoutConfig, layoutTracker, eventLogger))
            addPanel(uiSystem, "Preview", TextureAtlasEditorPreviewCanvasPanel(editorState, operations, engine.ui, layoutConfig, layoutTracker, eventLogger))
            addPanel(uiSystem, "Inspector", TextureAtlasEditorInspectorPanel(editorState, operations, layoutConfig, layoutTracker, eventLogger))
            addPanel(uiSystem, "Resources", TextureAtlasEditorResourcesPanel(editorState, operations, layoutConfig, layoutTracker, eventLogger))
            addPanel(uiSystem, "Tools", TextureAtlasEditorToolsPanel(editorState, operations, layoutConfig, layoutTracker, eventLogger))
            addPanel(uiSystem, "Diagnostics", TextureAtlasEditorDiagnosticsPanel(editorState, operations, layoutConfig, layoutTracker, eventLogger))
            addPanel(
                uiSystem,
                "Logs",
                LogsPanel(
                    engine.logs,
                    layoutConfig,
                    eventLogger,
                    panelId = TextureAtlasEditorPanelIds.Logs,
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
                    engine.logger.error(TAG, error) { "Texture Atlas Editor panel draw failed panel='$name': ${error.message}" }
                    throw error
                }
            },
        )
    }

    companion object {
        private const val TAG = "TextureAtlasEditorScene"
    }
}

private class TextureAtlasEditorPreviewSyncSystem(
    private val state: TextureAtlasEditorState,
    private val preview: GdxTextureAtlasEditorPreview,
    private val logger: com.pashkd.krender.engine.api.Logger,
) : System() {
    private var lastResolvedPreviewKey: String? = null
    private var lastMissingPreviewKey: String? = null

    override fun update(
        world: SceneWorld,
        dt: Float,
    ) {
        val previewPath = state.selectedPreviewTexturePath()
        val previewSlice = state.selectedPreviewSlice()
        val asset = state.selectedAsset()
        val atlasPage = state.selectedAtlasPageName
        val showPackedPreview = state.isShowingPackedAtlasPreview() || state.preview.canvasMode == FinalPackedAtlas
        val packedPage = state.selectedPackingPage().takeIf { showPackedPreview }
        if (!showPackedPreview) {
            logPreviewResolution(asset?.path, previewPath, atlasPage)
        } else {
            lastResolvedPreviewKey = null
            lastMissingPreviewKey = null
        }
        state.previewInfo =
            preview.update(
                texturePath = previewPath,
                atlasPageName = atlasPage,
                selectedAssetPath = asset?.path,
                previewSlice = previewSlice,
                packedPage = packedPage,
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
                        "Texture Atlas Editor resolved atlas preview page='${atlasPageName ?: "<first>"}' asset='${assetPath ?: state.project.selectedAtlasPath ?: "<none>"}' texture='$previewPath'"
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
                    "Texture Atlas Editor could not resolve atlas preview asset='$atlasPath' page='${atlasPageName ?: "<first>"}': $reason"
                }
            }
        }
    }

    companion object {
        private const val TAG = "TextureAtlasEditorPreviewSync"
    }
}
