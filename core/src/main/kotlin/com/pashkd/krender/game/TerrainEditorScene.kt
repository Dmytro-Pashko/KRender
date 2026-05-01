package com.pashkd.krender.game

import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.material.TerrainMaterialLibrary
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.Material
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.terrain.FlatTerrainGenerator
import com.pashkd.krender.engine.terrain.FractalNoiseGenerator
import com.pashkd.krender.engine.terrain.PerlinNoiseGenerator
import com.pashkd.krender.engine.terrain.SimplexNoiseGenerator
import com.pashkd.krender.engine.terrain.TerrainCameraControllerComponent
import com.pashkd.krender.engine.terrain.TerrainCameraControllerSystem
import com.pashkd.krender.engine.terrain.TerrainDataComponent
import com.pashkd.krender.engine.terrain.TerrainData
import com.pashkd.krender.engine.terrain.TerrainEditorBrushPanel
import com.pashkd.krender.engine.terrain.TerrainEditorControlsPanel
import com.pashkd.krender.engine.terrain.TerrainEditorLayersPanel
import com.pashkd.krender.engine.terrain.TerrainEditorStatisticsPanel
import com.pashkd.krender.engine.terrain.TerrainEditorTerrainPanel
import com.pashkd.krender.engine.terrain.TerrainEditorUiLayoutDefaults
import com.pashkd.krender.engine.terrain.TerrainGenerator
import com.pashkd.krender.engine.terrain.TerrainGeneratorOption
import com.pashkd.krender.engine.terrain.TerrainLayerColorDescriptor
import com.pashkd.krender.engine.terrain.TerrainMaterialOption
import com.pashkd.krender.engine.terrain.TerrainEditorState
import com.pashkd.krender.engine.terrain.TerrainEditorSystem
import com.pashkd.krender.engine.terrain.TerrainMeshSyncSystem
import com.pashkd.krender.engine.terrain.TerrainPersistence
import com.pashkd.krender.engine.terrain.TerrainRenderSystem
import com.pashkd.krender.engine.terrain.TerrainRendererComponent
import com.pashkd.krender.engine.terrain.terrainMaterialPreviewExportPath
import com.pashkd.krender.engine.ui.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.ImGuiLayoutConfigLoader
import com.pashkd.krender.engine.ui.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.LogsPanel
import com.pashkd.krender.engine.ui.UiSystem

/**
 * Scene that hosts the terrain editor viewport, terrain entity, and editing systems.
 */
class TerrainEditorScene(
    private val terrainResolution: Int = 128,
    private val vertexSpacing: Float = 1f,
    private val terrainFilePath: String = "terrains/terrain_01.krterrain",
) : Scene("terrain_editor") {
    private val terrainGenerators = listOf(
        FlatTerrainGenerator(),
        PerlinNoiseGenerator(),
        SimplexNoiseGenerator(),
        FractalNoiseGenerator(),
    )
    private lateinit var terrainPersistence: TerrainPersistence
    private lateinit var terrainMaterialLibrary: TerrainMaterialLibrary
    private lateinit var editorState: TerrainEditorState
    private lateinit var editorSystem: TerrainEditorSystem
    private lateinit var meshSyncSystem: TerrainMeshSyncSystem

    /**
     * Creates the terrain editor camera, lights, terrain entity, and terrain systems.
     */
    override fun show() {
        terrainPersistence = TerrainPersistence(engine.logger)
        terrainMaterialLibrary = TerrainMaterialLibrary(engine.logger).also { library ->
            library.load("materials/terrain_materials.json")
        }
        engine.logger.info(TAG) {
            "Showing terrain editor path='$terrainFilePath' defaultResolution=$terrainResolution spacing=${"%.2f".format(vertexSpacing)} " +
                "generators=${terrainGenerators.joinToString { it.id }} materials=${terrainMaterialLibrary.all().size}"
        }
        val layoutConfig = ImGuiLayoutConfigLoader(
            assetPath = TerrainEditorUiLayoutDefaults.assetPath,
            fallback = TerrainEditorUiLayoutDefaults.config,
        ).load(engine.logger)
        val panelEventLogger = ImGuiWindowEventLogger(engine.logger, "TerrainEditorUi")
        editorState = TerrainEditorState(
            generators = terrainGenerators.map(::toGeneratorOption),
            selectedGeneratorId = terrainGenerators.firstOrNull()?.id,
            terrainResolution = terrainResolution,
            vertexSpacing = vertexSpacing,
            terrainFilePath = terrainFilePath,
            materialPreviewExportPath = terrainMaterialPreviewExportPath(terrainFilePath),
            terrainSaveName = terrainPersistence.fileNameFromPath(terrainFilePath),
            terrainMaterials = terrainMaterialLibrary.all().map { material ->
                TerrainMaterialOption(
                    id = material.id,
                    name = material.name,
                    albedoTexture = material.albedoTexture,
                    fallbackColor = material.fallbackColor,
                    defaultTiling = material.defaultTiling,
                )
            },
        )
        editorSystem = TerrainEditorSystem(
            engine.input,
            engine.logger,
            editorState,
            terrainGenerators.associateBy(TerrainGenerator::id),
            terrainMaterialLibrary.all().associateBy { it.id },
        )

        world.systems.add(TerrainCameraControllerSystem(engine.input, editorState))
        world.systems.add(editorSystem)
        meshSyncSystem = TerrainMeshSyncSystem(
            materialColorResolver = { materialId ->
                terrainMaterialLibrary.find(materialId)?.fallbackColor
            },
            blendModeProvider = { editorState.layerBlendMode },
            layerColorPreviewProvider = { editorState.showLayerColorPreview },
            previewModeProvider = { editorState.terrainPreviewMode },
            materialPreviewResolutionProvider = { editorState.materialPreviewResolution },
            materialPreviewDirtyProvider = { editorState.materialPreviewDirty },
            selectedLayerIdProvider = { editorState.selectedLayerId },
            materialPreviewStatusSink = { message ->
                editorState.materialPreviewMessage = message
                editorState.previewMessage = message
            },
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
            },
            materialPreviewCleanSink = {
                editorState.materialPreviewDirty = false
            },
            materialPreviewExportRequestedProvider = { editorState.materialPreviewExportRequested },
            materialPreviewExportPathProvider = { editorState.materialPreviewExportPath },
            materialPreviewExportCompleteSink = { message ->
                editorState.materialPreviewExportRequested = false
                editorState.materialPreviewMessage = message
                editorState.previewMessage = message
            },
            brushActiveProvider = { editorState.brushActive },
            materialLibrary = terrainMaterialLibrary,
            logger = engine.logger,
        )
        world.systems.add(meshSyncSystem)
        world.systems.add(createUiSystem(layoutConfig, panelEventLogger))
        world.systems.add(TerrainRenderSystem())

        createCamera()
        createLights()
        createTerrain()
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
            uiSystem.addPanel(TerrainEditorStatisticsPanel(engine.runtimeStats, engine.profiler, layoutConfig, panelEventLogger))
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
    private fun createTerrain() {
        val terrainData = createInitialTerrainData()
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
     * Loads the configured terrain file when present, otherwise creates a generated default terrain.
     */
    private fun createInitialTerrainData(): TerrainData {
        editorState.terrainFileExists = terrainPersistence.exists(terrainFilePath)
        engine.logger.debug(TAG) { "Initial terrain file check path='$terrainFilePath' exists=${editorState.terrainFileExists}" }
        if (editorState.terrainFileExists) {
            return try {
                engine.logger.info(TAG) { "Initial terrain load started path='$terrainFilePath'" }
                val descriptor = terrainPersistence.loadDescriptor(terrainFilePath)
                editorState.terrainSaveName = descriptor.name
                editorState.persistenceMessage = "Loaded terrain: $terrainFilePath"
                editorState.persistenceError = false
                TerrainData.fromDescriptor(descriptor.terrain).also { data ->
                    engine.logger.info(TAG) { "Initial terrain load completed path='$terrainFilePath' name='${descriptor.name}' (${data.describeTerrain()})" }
                }
            } catch (error: Exception) {
                engine.logger.error(TAG, error) {
                    "Failed to load terrain '$terrainFilePath': ${error.message}"
                }
                editorState.persistenceMessage = "Load failed: ${error.message}"
                editorState.persistenceError = true
                engine.logger.warn(TAG) { "Falling back to generated default terrain after load failure path='$terrainFilePath'" }
                createDefaultTerrain()
            }
        }

        editorState.persistenceMessage = "Created default terrain: $terrainFilePath"
        editorState.persistenceError = false
        engine.logger.info(TAG) { "No terrain file found. Creating generated default terrain path='$terrainFilePath'" }
        return createDefaultTerrain()
    }

    /**
     * Creates a generated default terrain without writing it to disk.
     */
    private fun createDefaultTerrain(): TerrainData {
        val generator = activeGenerator()
        engine.logger.info(TAG) {
            "Default terrain generation started generator='${generator.id}' resolution=$terrainResolution spacing=${"%.2f".format(vertexSpacing)}"
        }
        val terrainData = TerrainData(
            width = terrainResolution,
            height = terrainResolution,
            vertexSpacing = vertexSpacing,
        )
        val baseMaterial = preferredBaseMaterial()
        terrainData.addLayer(
            name = "Base Layer",
            materialId = baseMaterial?.id ?: "terrain/base",
            color = baseMaterial?.fallbackColor ?: TerrainLayerColorDescriptor(),
            tiling = baseMaterial?.defaultTiling ?: 1f,
        )
        generator.generate(terrainData)
        engine.logger.info(TAG) { "Default terrain generation completed (${terrainData.describeTerrain()})" }
        return terrainData
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

    private fun preferredBaseMaterial() =
        terrainMaterialLibrary.find("terrain/grass")
            ?: terrainMaterialLibrary.find("terrain/ground_grass")
            ?: terrainMaterialLibrary.firstOrNull()

    companion object {
        private const val TAG = "TerrainEditor"
    }
}

private fun TerrainData.describeTerrain(): String =
    "size=${width}x${height} spacing=${"%.2f".format(vertexSpacing)} layers=${allLayers().size} [${allLayers().joinToString { layer -> "${layer.id}:${layer.name}" }}]"
