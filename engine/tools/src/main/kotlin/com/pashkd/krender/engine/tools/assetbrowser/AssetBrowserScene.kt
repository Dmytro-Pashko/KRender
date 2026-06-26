package com.pashkd.krender.engine.tools.assetbrowser

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.assets.*
import com.pashkd.krender.engine.assets.importing.AssetImportService
import com.pashkd.krender.engine.assets.importing.AwtFileDialogService
import com.pashkd.krender.engine.assets.importing.FileDialogService
import com.pashkd.krender.engine.assets.importing.LocalAssetImportService
import com.pashkd.krender.engine.scene.SceneConfig
import com.pashkd.krender.engine.scene.SceneConfigPresets
import com.pashkd.krender.engine.terrain.TerrainData
import com.pashkd.krender.engine.terrain.TerrainPersistence
import com.pashkd.krender.engine.tools.assetbrowser.creation.createAtlasAsset
import com.pashkd.krender.engine.ui.editor.*

/**
 * Main editor tool scene for browsing, inspecting, and opening engine assets.
 *
 * The scene wires registries (importers, asset tools), services (registry, operations) and panels.
 * Activation routing goes through [AssetToolRegistry] — no `when(asset.category)` here.
 */
class AssetBrowserScene : Scene("asset_browser") {
    private lateinit var registry: AssetRegistryService
    private lateinit var browserState: AssetBrowserState
    private lateinit var importers: AssetImporterRegistry
    private lateinit var tools: AssetToolRegistry
    private lateinit var operations: AssetOperationsService
    private lateinit var importService: AssetImportService
    private lateinit var fileDialogService: FileDialogService
    private lateinit var layoutTracker: ImGuiLayoutRuntimeTracker

    override val config: SceneConfig = SceneConfigPresets.AssetBrowser

    override fun show() {
        engine.logger.info(TAG) { "Showing Asset Browser scene" }
        val layoutConfig =
            ImGuiLayoutConfigLoader(
                assetPath = AssetBrowserUiLayoutDefaults.assetPath,
                fallback = AssetBrowserUiLayoutDefaults.config,
            ).load(engine.logger, engine.sceneFiles)
        layoutTracker = ImGuiLayoutRuntimeTracker(layoutConfig)
        val panelEventLogger = ImGuiWindowEventLogger(engine.logger, "AssetBrowserUi")

        importers = AssetImporterRegistry.withDefaults(engine.logger)
        registry = engine.assetRegistry
        browserState = AssetBrowserState()
        tools =
            AssetToolRegistry(engine.logger).apply {
                register(ModelViewerAssetTool())
                register(AnimationViewerAssetTool())
                register(TerrainEditorAssetTool())
                register(TextureAtlasEditorAtlasAssetTool())
                register(SkinEditorAssetTool())
                register(UiComposerAssetTool())
                register(SceneEditorAssetTool())
                register(SceneRuntimeAssetTool())
                register(BitmapFontEditorAssetTool())
            }
        operations =
            LocalAssetOperationsService(
                registry = registry,
                importers = importers,
                logger = engine.logger,
                onChanged = { browserState.refreshRequested = true },
            )
        importService =
            LocalAssetImportService(
                registry = registry,
                importers = importers,
                logger = engine.logger,
                onChanged = { browserState.refreshRequested = true },
            )
        fileDialogService = AwtFileDialogService()

        world.systems.add(
            AssetBrowserSystem(
                registry = registry,
                assets = engine.assets,
                tasks = engine.tasks,
                logger = engine.logger,
                state = browserState,
                onAssetActivated = ::openAsset,
            ),
        )
        world.systems.add(createUiSystem(layoutConfig, panelEventLogger))
    }

    private fun createUiSystem(
        layoutConfig: ImGuiLayoutConfig,
        panelEventLogger: ImGuiWindowEventLogger,
    ): UiSystem {
        val operationsHandler =
            SceneOperationsHandler(
                operations = operations,
                toolRegistry = tools,
                state = browserState,
                engineProvider = { engine },
                logger = engine.logger,
            )
        val uiOperations = AssetBrowserUiOperations(browserState, engine, layoutTracker)
        return UiSystem(engine.ui).also { uiSystem ->
            uiSystem.addPanel(
                AssetControlsPanel(
                    browserState,
                    uiOperations,
                    layoutConfig,
                    layoutTracker,
                    panelEventLogger,
                ),
            )
            uiSystem.addPanel(
                AssetBrowserPanel(
                    state = browserState,
                    onAssetSelected = { asset -> browserState.selectedAssetId = asset.id },
                    onAssetActivated = { asset -> browserState.activationRequestedAssetId = asset.id },
                    layoutConfig = layoutConfig,
                    layoutTracker = layoutTracker,
                    eventLogger = panelEventLogger,
                    operations = operationsHandler,
                    importService = importService,
                    fileDialogService = fileDialogService,
                ),
            )
            uiSystem.addPanel(
                AssetDetailsPanel(
                    state = browserState,
                    assets = engine.assets,
                    ui = engine.ui,
                    layoutConfig = layoutConfig,
                    eventLogger = panelEventLogger,
                    layoutTracker = layoutTracker,
                    operations = operationsHandler,
                ),
            )
            uiSystem.addPanel(
                LogsPanel(
                    engine.logs,
                    layoutConfig,
                    panelEventLogger,
                    layoutTracker = layoutTracker,
                    initialAutoScrollToLatest = true,
                ),
            )
        }
    }

    private fun openAsset(asset: AssetDescriptor) {
        if (!asset.canOpenWithTools()) {
            browserState.statusMessage = "No default editor registered for ${asset.category.displayName} assets."
            engine.logger.info(TAG) {
                "Asset '${asset.path}' is visible-only or has no editor routing category=${asset.category} type=${asset.type}"
            }
            return
        }
        val tool = tools.defaultToolFor(asset)
        if (tool == null) {
            browserState.statusMessage = "No default editor registered for ${asset.category.displayName} assets."
            engine.logger.info(TAG) {
                "No tool registered for asset '${asset.path}' category=${asset.category} type=${asset.type}"
            }
            return
        }
        engine.logger.info(TAG) { "Asset Browser default action selected tool='${tool.id}' path='${asset.path}' type=${asset.type}" }
        try {
            tool.open(asset, engine)
            browserState.statusMessage = "Opened ${tool.displayName}: ${asset.path}"
        } catch (error: Exception) {
            browserState.statusMessage = "Failed to open tool: ${error.message}"
            engine.logger.error(TAG, error) {
                "Failed to open asset '${asset.path}' with tool '${tool.id}': ${error.message}"
            }
        }
    }

    companion object {
        private const val TAG = "AssetBrowserScene"
    }
}

/**
 * Bridges [AssetBrowserPanel] callbacks to the operations service and tool registry.
 */
private class SceneOperationsHandler(
    private val operations: AssetOperationsService,
    private val toolRegistry: AssetToolRegistry,
    private val state: AssetBrowserState,
    private val engineProvider: () -> EngineContext,
    private val logger: Logger,
) : AssetBrowserOperationsHandler {
    override fun create(draft: CreateAssetDraft) {
        if (draft.kind == CreatableAssetKind.Atlas) {
            consumeResult(createAtlasAsset(draft, engineProvider().assetRegistry.baseDir(), logger))
            if (state.errorMessage == null) {
                state.refreshRequested = true
            }
            return
        }
        consumeResult(
            operations.create(
                CreateAssetRequest(
                    name = draft.name,
                    type = draft.kind.type,
                    category = draft.kind.category,
                    targetDirectory = draft.kind.targetDirectory,
                    extension = draft.kind.extension,
                    initialContent = defaultContent(draft),
                ),
            ),
        )
    }

    override fun rename(
        asset: AssetDescriptor,
        newName: String,
    ) {
        consumeResult(operations.rename(asset, newName))
    }

    override fun duplicate(
        asset: AssetDescriptor,
        targetName: String,
    ) {
        consumeResult(operations.duplicate(asset, targetName))
    }

    override fun delete(asset: AssetDescriptor) {
        consumeResult(operations.delete(asset))
    }

    override fun reveal(asset: AssetDescriptor) {
        consumeResult(operations.reveal(asset))
    }

    override fun toolsFor(asset: AssetDescriptor): List<AssetToolDescriptor> =
        if (!asset.canOpenWithTools()) {
            emptyList()
        } else {
            toolRegistry.toolsFor(asset).map { AssetToolDescriptor(it.id, it.displayName) }
        }

    override fun openWith(
        asset: AssetDescriptor,
        toolId: String,
    ) {
        if (!asset.canOpenWithTools()) {
            state.statusMessage = "Open With is unavailable for ${asset.category.displayName} assets."
            logger.info(TAG) { "Open With rejected for visible-only asset '${asset.path}'" }
            return
        }
        val tool = toolRegistry.toolsFor(asset).firstOrNull { it.id == toolId }
        if (tool == null) {
            logger.warn(TAG) { "Open with: tool '$toolId' is not registered" }
            return
        }
        try {
            logger.info(TAG) { "Asset Browser Open With selected tool='${tool.id}' path='${asset.path}' type=${asset.type}" }
            tool.open(asset, engineProvider())
            state.statusMessage = "Opened ${tool.displayName}: ${asset.path}"
            logger.info(TAG) { "Open with succeeded tool='${tool.id}' path='${asset.path}'" }
        } catch (error: Exception) {
            state.statusMessage = "Failed to open tool: ${error.message}"
            logger.error(TAG, error) {
                "Open with failed tool='${tool.id}' path='${asset.path}': ${error.message}"
            }
        }
    }

    private fun defaultContent(draft: CreateAssetDraft): ByteArray =
        when (draft.kind.type) {
            AssetType.UiScene ->
                defaultUiSceneContent(
                    draft.name,
                    normalizeUiSceneSkinPath(draft.uiSceneSkinPath),
                ).toByteArray()

            AssetType.Terrain -> defaultTerrainContent(draft.name).toByteArray()
            AssetType.Scene -> defaultSceneContent().toByteArray()
            else -> ByteArray(0)
        }

    private fun defaultSceneContent(): String =
        """
        {
          "schemaVersion": 1,
          "id": "scene:new",
          "name": "Untitled Scene",
          "entities": [],
          "settings": {}
        }
        """.trimIndent() + "\n"

    private fun consumeResult(result: AssetOperationResult) {
        when (result) {
            is AssetOperationResult.Success -> {
                state.statusMessage = result.message
                state.errorMessage = null
            }

            is AssetOperationResult.Failure -> {
                state.errorMessage = result.message
                state.statusMessage = result.message
            }
        }
    }

    companion object {
        private const val TAG = "AssetBrowserSceneOps"
    }
}

/**
 * Creates the minimal `.krui` document used by the generic Create Asset action.
 */
internal fun defaultUiSceneContent(
    name: String,
    skinPath: String = DefaultUiSceneSkinPath,
): String {
    val id = name.trim().replace(Regex("[^A-Za-z0-9_\\-:.]"), "_").ifBlank { "new_ui_scene" }
    val normalizedSkinPath = normalizeUiSceneSkinPath(skinPath)
    return """
        {
          "schemaVersion": 1,
          "id": "$id",
          "skin": "$normalizedSkinPath",
          "root": {
            "id": "root",
            "type": "Stack",
            "children": []
          }
        }
        """.trimIndent() + "\n"
}

private fun normalizeUiSceneSkinPath(path: String): String = path.trim().replace('\\', '/').ifBlank { DefaultUiSceneSkinPath }

internal fun defaultTerrainContent(name: String): String {
    val terrainName = name.trim().ifBlank { "terrain" }
    val encoded =
        TerrainPersistence().encode(
            data =
                TerrainData(
                    width = 64,
                    height = 64,
                    vertexSpacing = 1f,
                ),
            name = terrainName,
        )
    return "$encoded\n"
}

/**
 * Opens model assets in a separate Model Viewer window.
 */
class ModelViewerAssetTool : AssetTool {
    override val id = "model-viewer"
    override val displayName = "Open in Model Viewer"
    override val supportedCategories = setOf(AssetCategory.Model)

    override fun open(
        asset: AssetDescriptor,
        context: EngineContext,
    ) {
        val path = normalizedAssetPath(asset)
        context.logger.info(TAG) { "Opening model asset '$path' in Model Viewer" }
        context.editorToolLauncher.launchModelViewer(path)
    }

    companion object {
        private const val TAG = "ModelViewerAssetTool"
    }
}

/**
 * Opens model assets in a separate Animation Viewer window.
 */
class AnimationViewerAssetTool : AssetTool {
    override val id = "animation-viewer"
    override val displayName = "Open in Animation Viewer"
    override val supportedCategories = setOf(AssetCategory.Model)
    override val defaultAction = false

    override fun open(
        asset: AssetDescriptor,
        context: EngineContext,
    ) {
        val path = normalizedAssetPath(asset)
        context.logger.info(TAG) { "Opening model asset '$path' in Animation Viewer" }
        context.editorToolLauncher.launchAnimationViewer(path)
    }

    companion object {
        private const val TAG = "AnimationViewerAssetTool"
    }
}

/**
 * Opens terrain assets in a separate Terrain Editor window.
 */
class TerrainEditorAssetTool : AssetTool {
    override val id = "terrain-editor"
    override val displayName = "Open in Terrain Editor"
    override val supportedCategories = setOf(AssetCategory.Terrain)

    override fun open(
        asset: AssetDescriptor,
        context: EngineContext,
    ) {
        val path = normalizedAssetPath(asset)
        context.logger.info(TAG) { "Opening terrain asset '$path' in Terrain Editor" }
        context.editorToolLauncher.launchTerrainEditor(path)
    }

    companion object {
        private const val TAG = "TerrainEditorAssetTool"
    }
}

class TextureAtlasEditorAtlasAssetTool : AssetTool {
    override val id = "texture-atlas-editor-atlas"
    override val displayName = "Open in Texture Atlas Editor"
    override val supportedCategories = setOf(AssetCategory.Scene2D)

    /**
     * Atlas assets default to Texture Atlas Editor because it owns the atlas
     * region inspection and packing workflow.
     */
    override val defaultAction = true

    override fun canOpen(asset: AssetDescriptor): Boolean = asset.category == AssetCategory.Scene2D && asset.type == AssetType.Atlas

    override fun open(
        asset: AssetDescriptor,
        context: EngineContext,
    ) = launchTextureAtlasEditorAsset(asset, context, TAG)

    companion object {
        private const val TAG = "TextureAtlasEditorAtlasTool"
    }
}

/**
 * Opens `.krui` UiScene assets in UI Composer.
 *
 * This tool belongs to editor/tool routing: it connects Asset Browser's UiScene metadata to
 * UiComposerScene for validation, preview, hierarchy/inspector editing, undo/redo, and save
 * workflows. Current UI Composer limitations still apply, including no canvas drag/drop authoring,
 * no Skin editing, and no asset-id references.
 */
class UiComposerAssetTool : AssetTool {
    override val id = "ui-composer"
    override val displayName = "Open with UI Composer"
    override val supportedCategories = setOf(AssetCategory.UI)

    override fun canOpen(asset: AssetDescriptor): Boolean = asset.category == AssetCategory.UI && asset.type == AssetType.UiScene

    /**
     * Launches UI Composer for the selected UiScene path.
     */
    override fun open(
        asset: AssetDescriptor,
        context: EngineContext,
    ) {
        val path = normalizedAssetPath(asset)
        context.logger.info(TAG) { "Opening UiScene asset '$path' in UI Composer" }
        context.editorToolLauncher.launchUiComposer(path)
    }

    companion object {
        private const val TAG = "UiComposerAssetTool"
    }
}

class SkinEditorAssetTool : AssetTool {
    override val id = "skin-editor"
    override val displayName = "Open in Skin Editor"
    override val supportedCategories = setOf(AssetCategory.Scene2D)

    override fun canOpen(asset: AssetDescriptor): Boolean = asset.category == AssetCategory.Scene2D && asset.type == AssetType.Scene2DSkin

    override fun open(
        asset: AssetDescriptor,
        context: EngineContext,
    ) {
        val path = normalizedAssetPath(asset)
        context.logger.info(TAG) { "Opening Scene2D Skin asset '$path' in Skin Editor" }
        context.editorToolLauncher.launchSkinEditor(path)
    }

    companion object {
        private const val TAG = "SkinEditorAssetTool"
    }
}

/**
 * Opens scene assets in a separate Scene Editor window.
 */
class SceneEditorAssetTool : AssetTool {
    override val id = "scene-editor"
    override val displayName = "Open in Scene Editor"
    override val supportedCategories = setOf(AssetCategory.Scene)
    override val defaultAction = false

    override fun open(
        asset: AssetDescriptor,
        context: EngineContext,
    ) {
        val path = normalizedAssetPath(asset)
        context.logger.info(TAG) { "Opening scene asset '$path' in Scene Editor" }
        context.editorToolLauncher.launchSceneEditorWithScene(path)
    }

    companion object {
        private const val TAG = "SceneEditorAssetTool"
    }
}

/**
 * Launches scene assets in the runtime player.
 */
class BitmapFontEditorAssetTool : AssetTool {
    override val id = "bitmap-font-editor"
    override val displayName = "Open in Bitmap Font Editor"
    override val supportedCategories = setOf(AssetCategory.Scene2D)

    override fun canOpen(asset: AssetDescriptor): Boolean =
        asset.type == AssetType.Font && (
            asset.extension.equals("fnt", ignoreCase = true) ||
                asset.path.endsWith(".kfont.json", ignoreCase = true)
            )

    override fun open(
        asset: AssetDescriptor,
        context: EngineContext,
    ) {
        val path = normalizedAssetPath(asset)
        context.logger.info(TAG) { "Opening font asset '$path' in Bitmap Font Editor" }
        context.editorToolLauncher.launchBitmapFontEditor(path)
    }

    companion object {
        private const val TAG = "BitmapFontEditorAssetTool"
    }
}

class SceneRuntimeAssetTool : AssetTool {
    override val id = "scene-runtime"
    override val displayName = "Run in Runtime"
    override val supportedCategories = setOf(AssetCategory.Scene)
    override val defaultAction = false

    override fun open(
        asset: AssetDescriptor,
        context: EngineContext,
    ) {
        val path = normalizedAssetPath(asset)
        context.logger.info(TAG) { "Launching scene asset '$path' in Runtime" }
        context.runtimeLauncher.launchRuntimeScene(path)
    }

    companion object {
        private const val TAG = "SceneRuntimeAssetTool"
    }
}

private fun launchTextureAtlasEditorAsset(
    asset: AssetDescriptor,
    context: EngineContext,
    tag: String,
) {
    val path = normalizedAssetPath(asset)
    if (asset.category != AssetCategory.Scene2D || asset.type != AssetType.Atlas) {
        context.logger.warn(tag) {
            "Rejected unsupported Texture Atlas Editor asset path='$path' category=${asset.category} type=${asset.type}"
        }
        return
    }
    context.editorToolLauncher.launchTextureAtlasEditor(path)
    context.logger.info(tag) { "Texture Atlas Editor launch requested path='$path'" }
}

private fun normalizedAssetPath(asset: AssetDescriptor): String = asset.path.trim().replace('\\', '/')
