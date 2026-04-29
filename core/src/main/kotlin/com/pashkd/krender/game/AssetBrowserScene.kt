package com.pashkd.krender.game

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.assets.AssetBrowserOperationsHandler
import com.pashkd.krender.engine.assets.AssetBrowserPanel
import com.pashkd.krender.engine.assets.AssetBrowserState
import com.pashkd.krender.engine.assets.AssetBrowserSystem
import com.pashkd.krender.engine.assets.AssetBrowserUiLayoutDefaults
import com.pashkd.krender.engine.assets.AssetCategory
import com.pashkd.krender.engine.assets.AssetDescriptor
import com.pashkd.krender.engine.assets.AssetDetailsPanel
import com.pashkd.krender.engine.assets.AssetImporterRegistry
import com.pashkd.krender.engine.assets.AssetOperationsService
import com.pashkd.krender.engine.assets.AssetTool
import com.pashkd.krender.engine.assets.AssetToolDescriptor
import com.pashkd.krender.engine.assets.AssetToolRegistry
import com.pashkd.krender.engine.assets.AssetType
import com.pashkd.krender.engine.assets.CreateAssetRequest
import com.pashkd.krender.engine.assets.LocalAssetOperationsService
import com.pashkd.krender.engine.assets.LocalAssetRegistryService
import com.pashkd.krender.engine.ui.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.ImGuiLayoutConfigLoader
import com.pashkd.krender.engine.ui.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.LogsPanel
import com.pashkd.krender.engine.ui.UiSystem

/**
 * Main editor tool scene for browsing, inspecting, and opening engine assets.
 *
 * The scene wires registries (importers, asset tools), services (registry, operations) and panels.
 * Activation routing goes through [AssetToolRegistry] — no `when(asset.category)` here.
 */
class AssetBrowserScene : Scene("asset_browser") {
    private lateinit var registry: LocalAssetRegistryService
    private lateinit var browserState: AssetBrowserState
    private lateinit var importers: AssetImporterRegistry
    private lateinit var tools: AssetToolRegistry
    private lateinit var operations: AssetOperationsService

    override fun show() {
        engine.logger.info(TAG) { "Showing Asset Browser scene" }
        val layoutConfig = ImGuiLayoutConfigLoader(
            assetPath = AssetBrowserUiLayoutDefaults.assetPath,
            fallback = AssetBrowserUiLayoutDefaults.config,
        ).load(engine.logger)
        val panelEventLogger = ImGuiWindowEventLogger(engine.logger, "AssetBrowserUi")

        importers = AssetImporterRegistry.withDefaults(engine.logger)
        registry = LocalAssetRegistryService(engine.logger, importers)
        browserState = AssetBrowserState()
        tools = AssetToolRegistry(engine.logger).apply {
            register(ModelViewerAssetTool { registry })
            register(TerrainEditorAssetTool())
            register(SceneEditorAssetTool())
        }
        operations = LocalAssetOperationsService(
            registry = registry,
            importers = importers,
            logger = engine.logger,
            onChanged = { browserState.refreshRequested = true },
        )

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
    ): UiSystem =
        UiSystem(engine.ui).also { uiSystem ->
            uiSystem.addPanel(
                AssetBrowserPanel(
                    state = browserState,
                    onAssetSelected = { asset -> browserState.selectedAssetId = asset.id },
                    onAssetActivated = { asset -> browserState.activationRequestedAssetId = asset.id },
                    layoutConfig = layoutConfig,
                    eventLogger = panelEventLogger,
                    operations = SceneOperationsHandler(
                        operations = operations,
                        toolRegistry = tools,
                        engineProvider = { engine },
                        logger = engine.logger,
                    ),
                ),
            )
            uiSystem.addPanel(AssetDetailsPanel(browserState, layoutConfig, panelEventLogger))
            uiSystem.addPanel(LogsPanel(engine.logs, layoutConfig, panelEventLogger))
        }

    private fun openAsset(asset: AssetDescriptor) {
        val tool = tools.defaultToolFor(asset)
        if (tool == null) {
            browserState.statusMessage = "No editor registered for ${asset.category.displayName} assets."
            engine.logger.info(TAG) {
                "No tool registered for asset '${asset.path}' category=${asset.category} type=${asset.type}"
            }
            return
        }
        engine.logger.info(TAG) { "Opening asset '${asset.path}' with tool '${tool.id}'" }
        tool.open(asset, engine)
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
    private val engineProvider: () -> EngineContext,
    private val logger: Logger,
) : AssetBrowserOperationsHandler {
    override fun create(name: String, type: AssetType, category: AssetCategory) {
        val targetDir = defaultDirFor(category)
        val ext = defaultExtensionFor(type, category)
        operations.create(
            CreateAssetRequest(
                name = name,
                type = type,
                category = category,
                targetDirectory = targetDir,
                extension = ext,
                initialContent = defaultContent(category),
            ),
        )
    }

    override fun rename(asset: AssetDescriptor, newName: String) {
        operations.rename(asset, newName)
    }

    override fun duplicate(asset: AssetDescriptor, targetName: String) {
        operations.duplicate(asset, targetName)
    }

    override fun delete(asset: AssetDescriptor) {
        operations.delete(asset)
    }

    override fun reveal(asset: AssetDescriptor) {
        operations.reveal(asset)
    }

    override fun toolsFor(asset: AssetDescriptor): List<AssetToolDescriptor> =
        toolRegistry.toolsFor(asset).map { AssetToolDescriptor(it.id, it.displayName) }

    override fun openWith(asset: AssetDescriptor, toolId: String) {
        val tool = toolRegistry.toolsFor(asset).firstOrNull { it.id == toolId }
        if (tool == null) {
            logger.warn(TAG) { "Open with: tool '$toolId' is not registered" }
            return
        }
        tool.open(asset, engineProvider())
    }

    private fun defaultDirFor(category: AssetCategory): String =
        when (category) {
            AssetCategory.Model -> "model"
            AssetCategory.Texture -> "textures"
            AssetCategory.Material -> "materials"
            AssetCategory.Terrain -> "terrains"
            AssetCategory.Scene -> "scenes"
            AssetCategory.Shader -> "shaders"
            else -> "assets"
        }

    private fun defaultExtensionFor(type: AssetType, category: AssetCategory): String =
        when (type) {
            AssetType.GltfModel -> "gltf"
            AssetType.ObjModel -> "obj"
            AssetType.GdxModel -> "g3dj"
            AssetType.Texture -> "png"
            AssetType.Terrain -> "json"
            AssetType.Scene -> "json"
            AssetType.Material -> "json"
            AssetType.Shader -> "glsl"
            AssetType.Unknown -> when (category) {
                AssetCategory.Material, AssetCategory.Terrain, AssetCategory.Scene -> "json"
                AssetCategory.Shader -> "glsl"
                else -> "bin"
            }
        }

    private fun defaultContent(category: AssetCategory): ByteArray? =
        when (category) {
            AssetCategory.Material, AssetCategory.Terrain -> "{}\n".toByteArray()
            AssetCategory.Scene -> defaultSceneContent().toByteArray()
            else -> null
        }

    private fun defaultSceneContent(): String =
        """
        {
          "schemaVersion": 1,
          "name": "Untitled Scene",
          "entities": [],
          "settings": {}
        }
        """.trimIndent() + "\n"

    companion object {
        private const val TAG = "AssetBrowserSceneOps"
    }
}

/**
 * Opens model assets in the [ModelViewerScene].
 */
class ModelViewerAssetTool(
    private val registryProvider: () -> LocalAssetRegistryService,
) : AssetTool {
    override val id = "model-viewer"
    override val displayName = "Model Viewer"
    override val supportedCategories = setOf(AssetCategory.Model)

    override fun open(asset: AssetDescriptor, context: EngineContext) {
        context.logger.info(TAG) { "Opening model asset '${asset.path}' in ModelViewerScene" }
        val registry = registryProvider()
        context.scenes.replace(
            ModelViewerScene(
                model = AssetRef.model(asset.path),
                availableModels = registry.byCategory(AssetCategory.Model).map { AssetRef.model(it.path) },
            ),
        )
    }

    companion object {
        private const val TAG = "ModelViewerAssetTool"
    }
}

/**
 * Opens terrain assets in the [TerrainEditorScene].
 */
class TerrainEditorAssetTool : AssetTool {
    override val id = "terrain-editor"
    override val displayName = "Terrain Editor"
    override val supportedCategories = setOf(AssetCategory.Terrain)

    override fun open(asset: AssetDescriptor, context: EngineContext) {
        context.logger.info(TAG) { "Opening terrain asset '${asset.path}' in TerrainEditorScene" }
        context.scenes.replace(TerrainEditorScene(terrainFilePath = asset.path))
    }

    companion object {
        private const val TAG = "TerrainEditorAssetTool"
    }
}

/**
 * Opens scene assets in the [SceneEditorScene].
 */
class SceneEditorAssetTool : AssetTool {
    override val id = "scene-editor"
    override val displayName = "Scene Editor"
    override val supportedCategories = setOf(AssetCategory.Scene)

    override fun open(asset: AssetDescriptor, context: EngineContext) {
        context.logger.info(TAG) { "Opening scene asset '${asset.path}' in SceneEditorScene" }
        context.scenes.replace(
            SceneEditorScene(
                scenePath = asset.path,
                initialSceneName = asset.name,
            ),
        )
    }

    companion object {
        private const val TAG = "SceneEditorAssetTool"
    }
}
