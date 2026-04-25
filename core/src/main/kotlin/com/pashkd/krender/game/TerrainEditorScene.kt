package com.pashkd.krender.game

import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.api.Vec3
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
import com.pashkd.krender.engine.terrain.TerrainComponent
import com.pashkd.krender.engine.terrain.TerrainData
import com.pashkd.krender.engine.terrain.TerrainEditorBrushPanel
import com.pashkd.krender.engine.terrain.TerrainEditorControlsPanel
import com.pashkd.krender.engine.terrain.TerrainEditorLayersPanel
import com.pashkd.krender.engine.terrain.TerrainEditorTerrainPanel
import com.pashkd.krender.engine.terrain.TerrainEditorUiLayoutDefaults
import com.pashkd.krender.engine.terrain.TerrainGenerator
import com.pashkd.krender.engine.terrain.TerrainGeneratorOption
import com.pashkd.krender.engine.terrain.TerrainEditorState
import com.pashkd.krender.engine.terrain.TerrainEditorSystem
import com.pashkd.krender.engine.terrain.TerrainMeshSyncSystem
import com.pashkd.krender.engine.terrain.TerrainRenderSystem
import com.pashkd.krender.engine.terrain.TerrainRendererComponent
import com.pashkd.krender.engine.terrain.TerrainViewportDebugRenderSystem
import com.pashkd.krender.engine.ui.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.ImGuiLayoutConfigLoader
import com.pashkd.krender.engine.ui.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.UiSystem

/**
 * Scene that hosts the terrain editor viewport, terrain entity, and editing systems.
 */
class TerrainEditorScene(
    private val terrainResolution: Int = 128,
    private val vertexSpacing: Float = 1f,
) : Scene("terrain_editor") {
    private val terrainGenerators = listOf(
        FlatTerrainGenerator(),
        PerlinNoiseGenerator(),
        SimplexNoiseGenerator(),
        FractalNoiseGenerator(),
    )
    private lateinit var editorState: TerrainEditorState
    private lateinit var editorSystem: TerrainEditorSystem

    /**
     * Creates the terrain editor camera, lights, terrain entity, and terrain systems.
     */
    override fun show() {
        val layoutConfig = ImGuiLayoutConfigLoader(
            assetPath = TerrainEditorUiLayoutDefaults.assetPath,
            fallback = TerrainEditorUiLayoutDefaults.config,
        ).load(engine.logger)
        engine.ui.setDebugWindowLayout(layoutConfig)
        val panelEventLogger = ImGuiWindowEventLogger(engine.logger, "TerrainEditorUi")
        editorState = TerrainEditorState(
            generators = terrainGenerators.map(::toGeneratorOption),
            selectedGeneratorId = terrainGenerators.firstOrNull()?.id,
            terrainResolution = terrainResolution,
            vertexSpacing = vertexSpacing,
        )
        editorSystem = TerrainEditorSystem(
            engine.input,
            engine.logger,
            editorState,
            terrainGenerators.associateBy(TerrainGenerator::id),
        )

        world.systems.add(TerrainCameraControllerSystem(engine.input))
        world.systems.add(editorSystem)
        world.systems.add(TerrainMeshSyncSystem())
        world.systems.add(TerrainViewportDebugRenderSystem(editorState))
        world.systems.add(createUiSystem(layoutConfig, panelEventLogger))
        world.systems.add(TerrainRenderSystem())

        createCamera()
        createLights()
        createTerrain()
    }

    /**
     * Publishes terrain editor state into the debug overlay each frame.
     */
    override fun update(dt: Float) {
        super.update(dt)

        engine.debug.line("Use the Terrain ImGui panel for editor controls.")
        engine.debug.line("Mouse drag - Apply brush")
        engine.debug.line("Mouse wheel - Brush radius")
        engine.debug.line("Shift + Mouse wheel - Brush strength")
        engine.debug.line("F1/F2/F3/F4 - Raise/Lower/Flatten/Smooth")
        engine.debug.line("Space - Paint active layer")
        engine.debug.line("G - Toggle terrain wireframe")
        engine.debug.line("W/A/S/D - Pan camera")
        engine.debug.line("Ctrl/Shift - Up/Down")
        engine.debug.line("Q/E - Rotate camera")
    }

    /**
     * Registers every Terrain Editor ImGui panel against the shared UI system.
     */
    private fun createUiSystem(
        layoutConfig: ImGuiLayoutConfig,
        panelEventLogger: ImGuiWindowEventLogger,
    ): UiSystem =
        UiSystem(engine.ui).also { uiSystem ->
            uiSystem.addPanel(TerrainEditorTerrainPanel(editorState, layoutConfig, panelEventLogger))
            uiSystem.addPanel(TerrainEditorBrushPanel(editorState, layoutConfig, panelEventLogger))
            uiSystem.addPanel(TerrainEditorLayersPanel(editorState, layoutConfig, panelEventLogger))
            uiSystem.addPanel(TerrainEditorControlsPanel(editorState, layoutConfig, panelEventLogger))
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
     * Creates a flat terrain entity and attaches data/render components.
     */
    private fun createTerrain() {
        val terrainData = TerrainData(
            width = terrainResolution,
            height = terrainResolution,
            vertexSpacing = vertexSpacing,
        )
        val baseLayer = terrainData.addLayer(name = "Base Layer", materialId = "terrain/base")
        editorState.selectedLayerId = baseLayer.id
        activeGenerator().generate(terrainData)

        val terrain = world.createEntity("Terrain")
        terrain.add(TerrainComponent(terrainData))
        terrain.add(
            TerrainRendererComponent(
                modelId = "terrain_${terrainResolution}x${terrainResolution}",
                material = Material(baseColor = Color(0.38f, 0.63f, 0.31f)),
            ),
        )
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
}
