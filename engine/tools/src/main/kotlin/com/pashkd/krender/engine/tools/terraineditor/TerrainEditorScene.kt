package com.pashkd.krender.engine.tools.terraineditor

import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.material.TerrainMaterialLibrary
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.Material
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.scene.SceneConfig
import com.pashkd.krender.engine.scene.SceneConfigPresets
import com.pashkd.krender.engine.terrain.FlatTerrainGenerator
import com.pashkd.krender.engine.terrain.FractalNoiseGenerator
import com.pashkd.krender.engine.terrain.PerlinNoiseGenerator
import com.pashkd.krender.engine.terrain.SimplexNoiseGenerator
import com.pashkd.krender.engine.terrain.TerrainCameraControllerComponent
import com.pashkd.krender.engine.terrain.TerrainData
import com.pashkd.krender.engine.terrain.TerrainDataComponent
import com.pashkd.krender.engine.terrain.TerrainGenerator
import com.pashkd.krender.engine.terrain.TerrainPersistence
import com.pashkd.krender.engine.terrain.TerrainPreviewMode
import com.pashkd.krender.engine.terrain.TerrainRendererComponent
import com.pashkd.krender.engine.ui.editor.*

/**
 * Scene that hosts the terrain editor viewport, terrain entity, and editing systems.
 */
class TerrainEditorScene(
    private val terrainFilePath: String,
) : Scene("terrain_editor") {
    override val config: SceneConfig = SceneConfigPresets.EditorTool

    private val terrainGenerators =
        listOf(
            FlatTerrainGenerator(),
            PerlinNoiseGenerator(),
            SimplexNoiseGenerator(),
            FractalNoiseGenerator(),
        )
    private lateinit var terrainPersistence: TerrainPersistence
    private lateinit var terrainMaterialLibrary: TerrainMaterialLibrary
    private lateinit var editorState: TerrainEditorState
    private lateinit var editorSystem: TerrainEditorSystem
    private lateinit var meshSyncSystem: TerrainEditorMeshSyncSystem

    /**
     * Creates the terrain editor camera, lights, terrain entity, and terrain systems.
     */
    override fun show() {
        terrainPersistence = TerrainPersistence(engine.logger)
        terrainMaterialLibrary =
            TerrainMaterialLibrary(engine.logger).also { library ->
                library.load("materials/terrain_materials.json")
            }
        val initialTerrain = loadInitialTerrainData()
        engine.logger.info(TAG) {
            "Showing terrain editor path='$terrainFilePath' resolution=${initialTerrain.data.width} spacing=${
                "%.2f".format(
                    initialTerrain.data.vertexSpacing,
                )
            } " +
                "generators=${terrainGenerators.joinToString { it.id }} materials=${terrainMaterialLibrary.all().size}"
        }
        val layoutConfig =
            ImGuiLayoutConfigLoader(
                assetPath = TerrainEditorUiLayoutDefaults.assetPath,
                fallback = TerrainEditorUiLayoutDefaults.config,
            ).load(engine.logger)
        val panelEventLogger = ImGuiWindowEventLogger(engine.logger, "TerrainEditorUi")
        editorState =
            TerrainEditorState(
                generators = terrainGenerators.map(::toGeneratorOption),
                selectedGeneratorId = terrainGenerators.firstOrNull()?.id,
                terrainResolution = initialTerrain.data.width,
                vertexSpacing = initialTerrain.data.vertexSpacing,
                terrainFilePath = terrainFilePath,
                materialPreviewExportPath = terrainMaterialPreviewExportPath(terrainFilePath),
                terrainSaveName = initialTerrain.name,
                terrainFileExists = true,
                terrainMaterials =
                    terrainMaterialLibrary.all().map { material ->
                        TerrainMaterialOption(
                            id = material.id,
                            name = material.name,
                            albedoTexture = material.albedoTexture,
                            fallbackColor = material.fallbackColor,
                            defaultTiling = material.defaultTiling,
                        )
                    },
                persistenceMessage = initialTerrain.message,
                persistenceError = false,
            )
        editorSystem =
            TerrainEditorSystem(
                engine.input,
                engine.logger,
                editorState,
                terrainGenerators.associateBy(TerrainGenerator::id),
                terrainMaterialLibrary.all().associateBy { it.id },
            )

        world.systems.add(
            com.pashkd.krender.engine.terrain.TerrainCameraControllerSystem(engine.input) {
                editorState.inputFocus == TerrainEditorInputFocus.Viewport
            },
        )
        world.systems.add(editorSystem)
        meshSyncSystem =
            TerrainEditorMeshSyncSystem(
                bindings =
                    TerrainEditorMeshSyncBindings().apply {
                        materialColorResolver = { materialId -> terrainMaterialLibrary.find(materialId)?.fallbackColor }
                        blendModeProvider = { editorState.layerBlendMode }
                        layerColorPreviewProvider = { editorState.showLayerColorPreview }
                        previewModeProvider = { editorState.terrainPreviewMode }
                        materialPreviewResolutionProvider = { editorState.materialPreviewResolution }
                        materialPreviewDirtyProvider = { editorState.materialPreviewDirty }
                        selectedLayerIdProvider = { editorState.selectedLayerId }
                        materialPreviewStatusSink = { message ->
                            editorState.materialPreviewMessage = message
                            editorState.previewMessage = message
                        }
                        previewBakeStatsSink = { elapsedMs, stats ->
                            editorState.lastPreviewBakeTimeMs = elapsedMs
                            val previousCount = editorState.previewBakeCount
                            editorState.averagePreviewBakeTimeMs =
                                if (previousCount <= 0) {
                                    elapsedMs
                                } else {
                                    ((editorState.averagePreviewBakeTimeMs * previousCount) + elapsedMs) / (previousCount + 1)
                                }
                            editorState.previewBakeCount = previousCount + 1
                            editorState.previewTextureCacheSize = stats.textureCount
                            editorState.previewTextureCacheMemoryBytes = stats.approximateMemoryBytes
                        }
                        materialPreviewCleanSink = {
                            editorState.materialPreviewDirty = false
                        }
                        materialPreviewExportRequestedProvider = { editorState.materialPreviewExportRequested }
                        materialPreviewExportPathProvider = { editorState.materialPreviewExportPath }
                        materialPreviewExportCompleteSink = { message ->
                            editorState.materialPreviewExportRequested = false
                            editorState.materialPreviewMessage = message
                            editorState.previewMessage = message
                        }
                        brushActiveProvider = { editorState.brushActive }
                    },
                materialLibrary = terrainMaterialLibrary,
                logger = engine.logger,
            )
        world.systems.add(meshSyncSystem)
        world.systems.add(createUiSystem(layoutConfig, panelEventLogger))
        world.systems.add(com.pashkd.krender.engine.terrain.TerrainRenderSystem())

        createCamera()
        createLights()
        createTerrain(initialTerrain.data)
    }

    override fun dispose() {
        if (::meshSyncSystem.isInitialized) {
            meshSyncSystem.dispose()
        }
        super.dispose()
    }

    /**
     * Registers every Terrain Editor ImGui panel against the shared UI system.
     */
    private fun createUiSystem(
        layoutConfig: ImGuiLayoutConfig,
        panelEventLogger: ImGuiWindowEventLogger,
    ): UiSystem =
        UiSystem(engine.ui).also { uiSystem ->
            uiSystem.addPanel(
                TerrainEditorStatisticsPanel(
                    engine.runtimeStats,
                    engine.profiler,
                    layoutConfig,
                    panelEventLogger,
                ),
            )
            uiSystem.addPanel(TerrainEditorTerrainPanel(editorState, layoutConfig, panelEventLogger))
            uiSystem.addPanel(TerrainEditorBrushPanel(editorState, layoutConfig, panelEventLogger))
            uiSystem.addPanel(TerrainEditorLayersPanel(editorState, layoutConfig, panelEventLogger))
            uiSystem.addPanel(TerrainEditorControlsPanel(editorState, layoutConfig, panelEventLogger))
            uiSystem.addPanel(LogsPanel(engine.logs, layoutConfig, panelEventLogger))
        }

    /**
     * Creates the editor camera with a fixed look-at target.
     */
    private fun createCamera() {
        val camera = world.createEntity("Terrain Camera")
        camera.transform.position.set(0f, 48f, 48f)
        camera.add(
            PerspectiveCameraComponent(
                fieldOfViewDegrees = 60f,
                near = 0.1f,
                far = 512f,
                lookAt = Vec3.zero(),
            ),
        )
        camera.add(TerrainCameraControllerComponent())
    }

    /**
     * Adds basic directional and ambient lighting for terrain inspection.
     */
    private fun createLights() {
        val sun = world.createEntity("Terrain Sun")
        sun.add(
            LightComponent(
                type = LightType.Directional,
                color = Color(1f, 0.95f, 0.84f),
                intensity = 1.3f,
                direction = Vec3(-0.45f, -0.8f, -0.3f),
            ),
        )

        val ambient = world.createEntity("Terrain Ambient")
        ambient.add(
            LightComponent(
                type = LightType.Ambient,
                color = Color(0.44f, 0.5f, 0.58f),
                intensity = 0.6f,
            ),
        )
    }

    /**
     * Creates or loads a terrain entity and attaches data/render components.
     */
    private fun createTerrain(terrainData: TerrainData) {
        engine.logger.info(TAG) { "Creating terrain entity (${terrainData.describeTerrain()})" }
        editorState.selectedLayerId = terrainData.allLayers().firstOrNull()?.id
        editorState.terrainResolution = terrainData.width
        editorState.vertexSpacing = terrainData.vertexSpacing

        val terrain = world.createEntity("Terrain")
        terrain.add(TerrainDataComponent(terrainData))
        terrain.add(
            TerrainRendererComponent(
                modelId = "terrain_${terrainData.width}x${terrainData.height}",
                material = Material(baseColor = Color(0.38f, 0.63f, 0.31f)),
            ),
        )
    }

    /**
     * Loads the configured terrain file and fails fast when it is missing or invalid.
     */
    private fun loadInitialTerrainData(): InitialTerrainData {
        val terrainFileExists = terrainPersistence.exists(terrainFilePath)
        engine.logger.debug(TAG) { "Initial terrain file check path='$terrainFilePath' exists=$terrainFileExists" }
        require(terrainFileExists) { "Terrain asset not found: '$terrainFilePath'" }

        try {
            engine.logger.info(TAG) { "Initial terrain load started path='$terrainFilePath'" }
            val descriptor = terrainPersistence.loadDescriptor(terrainFilePath)
            val data = TerrainData.fromDescriptor(descriptor.terrain)
            engine.logger.info(TAG) {
                "Initial terrain load completed path='$terrainFilePath' name='${descriptor.name}' (${data.describeTerrain()})"
            }
            return InitialTerrainData(
                data = data,
                name = descriptor.name,
                message = "Loaded terrain: $terrainFilePath",
            )
        } catch (error: Exception) {
            engine.logger.error(TAG, error) {
                "Failed to load terrain '$terrainFilePath': ${error.message}"
            }
            throw IllegalStateException("Terrain asset '$terrainFilePath' could not be loaded: ${error.message}", error)
        }
    }

    /**
     * Resolves the currently selected terrain generator for scene setup.
     */
    private fun activeGenerator(): TerrainGenerator =
        terrainGenerators.firstOrNull { it.id == editorState.selectedGeneratorId } ?: terrainGenerators.first()

    /**
     * Builds one UI option from a runtime terrain generator.
     */
    private fun toGeneratorOption(generator: TerrainGenerator): TerrainGeneratorOption =
        TerrainGeneratorOption(
            id = generator.id,
            label = generator.id.replaceFirstChar { char -> char.uppercase() },
        )

    companion object {
        private const val TAG = "TerrainEditor"
    }
}

private data class InitialTerrainData(
    val data: TerrainData,
    val name: String,
    val message: String,
)

private fun TerrainData.describeTerrain(): String =
    "size=${width}x$height spacing=${"%.2f".format(vertexSpacing)} layers=${allLayers().size} [${
        allLayers().joinToString { layer ->
            "${layer.id}:${layer.name}"
        }
    }]"
