package com.pashkd.krender.game

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.assets.AssetBrowserOperationsHandler
import com.pashkd.krender.engine.assets.AssetBrowserPanel
import com.pashkd.krender.engine.assets.AssetBrowserState
import com.pashkd.krender.engine.assets.AssetBrowserSystem
import com.pashkd.krender.engine.assets.AssetBrowserUiOperations
import com.pashkd.krender.engine.assets.AssetBrowserUiLayoutDefaults
import com.pashkd.krender.engine.assets.AssetCategory
import com.pashkd.krender.engine.assets.AssetControlsPanel
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
import com.pashkd.krender.engine.scene.SceneConfig
import com.pashkd.krender.engine.scene.SceneConfigPresets
import com.pashkd.krender.engine.terrain.TerrainData
import com.pashkd.krender.engine.terrain.TerrainPersistence
import com.pashkd.krender.engine.ui.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.ImGuiLayoutConfigLoader
import com.pashkd.krender.engine.ui.ImGuiLayoutRuntimeTracker
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
    private lateinit var layoutTracker: ImGuiLayoutRuntimeTracker

    override val config: SceneConfig = SceneConfigPresets.AssetBrowser

    override fun show() {
        engine.logger.info(TAG) { "Showing Asset Browser scene" }
        val layoutConfig = ImGuiLayoutConfigLoader(
            assetPath = AssetBrowserUiLayoutDefaults.assetPath,
            fallback = AssetBrowserUiLayoutDefaults.config,
        ).load(engine.logger)
        layoutTracker = ImGuiLayoutRuntimeTracker(layoutConfig)
        val panelEventLogger = ImGuiWindowEventLogger(engine.logger, "AssetBrowserUi")

        importers = AssetImporterRegistry.withDefaults(engine.logger)
        registry = LocalAssetRegistryService(engine.logger, importers)
        browserState = AssetBrowserState()
        tools = AssetToolRegistry(engine.logger).apply {
            register(ModelViewerAssetTool())
            register(AnimationViewerAssetTool())
            register(TerrainEditorAssetTool())
            register(SceneEditorAssetTool())
            register(SceneRuntimeAssetTool())
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
    ): UiSystem {
        val operationsHandler = SceneOperationsHandler(
            operations = operations,
            toolRegistry = tools,
            state = browserState,
            engineProvider = { engine },
            logger = engine.logger,
        )
        val uiOperations = AssetBrowserUiOperations(browserState, engine, layoutTracker)
        return UiSystem(engine.ui).also { uiSystem ->
            uiSystem.addPanel(AssetControlsPanel(browserState, uiOperations, layoutConfig, layoutTracker, panelEventLogger))
            uiSystem.addPanel(
                AssetBrowserPanel(
                    state = browserState,
                    onAssetSelected = { asset -> browserState.selectedAssetId = asset.id },
                    onAssetActivated = { asset -> browserState.activationRequestedAssetId = asset.id },
                    layoutConfig = layoutConfig,
                    layoutTracker = layoutTracker,
                    eventLogger = panelEventLogger,
                    operations = operationsHandler,
                ),
            )
            uiSystem.addPanel(
                AssetDetailsPanel(
                    browserState,
                    layoutConfig,
                    panelEventLogger,
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
        val tool = tools.defaultToolFor(asset)
        if (tool == null) {
            browserState.statusMessage = "No default editor registered for ${asset.category.displayName} assets."
            engine.logger.info(TAG) {
                "No tool registered for asset '${asset.path}' category=${asset.category} type=${asset.type}"
            }
            return
        }
        engine.logger.info(TAG) { "Opening asset '${asset.path}' with tool '${tool.id}'" }
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
                initialContent = defaultContent(name, category),
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
        try {
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

    private fun defaultDirFor(category: AssetCategory): String =
        when (category) {
            AssetCategory.Model -> "model"
            AssetCategory.Texture -> "textures"
            AssetCategory.Skybox -> "skyboxes"
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
            AssetType.Skybox -> "krskybox"
            AssetType.Terrain -> "json"
            AssetType.Scene -> "krscene"
            AssetType.Material -> "json"
            AssetType.Shader -> "glsl"
            AssetType.Unknown -> when (category) {
                AssetCategory.Scene -> "krscene"
                AssetCategory.Skybox -> "krskybox"
                AssetCategory.Material, AssetCategory.Terrain -> "json"
                AssetCategory.Shader -> "glsl"
                else -> "bin"
            }
        }

    private fun defaultContent(name: String, category: AssetCategory): ByteArray? =
        when (category) {
            AssetCategory.Material -> "{}\n".toByteArray()
            AssetCategory.Terrain -> defaultTerrainContent(name).toByteArray()
            AssetCategory.Skybox -> defaultSkyboxContent().toByteArray()
            AssetCategory.Scene -> defaultSceneContent().toByteArray()
            else -> null
        }

    private fun defaultSkyboxContent(): String =
        """
        {
          "schemaVersion": 1,
          "id": "skybox:new",
          "name": "New Skybox",
          "texturePath": "textures/default_skybox_studio.png",
          "intensity": 1.0
        }
        """.trimIndent()

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

    companion object {
        private const val TAG = "AssetBrowserSceneOps"
    }
}

internal fun defaultTerrainContent(name: String): String {
    val terrainName = name.trim().ifBlank { "terrain" }
    val encoded = TerrainPersistence().encode(
        data = TerrainData(
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

    override fun open(asset: AssetDescriptor, context: EngineContext) {
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

    override fun open(asset: AssetDescriptor, context: EngineContext) {
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

    override fun open(asset: AssetDescriptor, context: EngineContext) {
        val path = normalizedAssetPath(asset)
        context.logger.info(TAG) { "Opening terrain asset '$path' in Terrain Editor" }
        context.editorToolLauncher.launchTerrainEditor(path)
    }

    companion object {
        private const val TAG = "TerrainEditorAssetTool"
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

    override fun open(asset: AssetDescriptor, context: EngineContext) {
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
class SceneRuntimeAssetTool : AssetTool {
    override val id = "scene-runtime"
    override val displayName = "Run in Runtime"
    override val supportedCategories = setOf(AssetCategory.Scene)
    override val defaultAction = false

    override fun open(asset: AssetDescriptor, context: EngineContext) {
        val path = normalizedAssetPath(asset)
        context.logger.info(TAG) { "Launching scene asset '$path' in Runtime" }
        context.runtimeLauncher.launchRuntimeScene(path)
    }

    companion object {
        private const val TAG = "SceneRuntimeAssetTool"
    }
}

private fun normalizedAssetPath(asset: AssetDescriptor): String =
    asset.path.trim().replace('\\', '/')
