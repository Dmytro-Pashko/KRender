package com.pashkd.krender.game

import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.Material
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.terrain.FlatTerrainGenerator
import com.pashkd.krender.engine.terrain.TerrainCameraControllerComponent
import com.pashkd.krender.engine.terrain.TerrainCameraControllerSystem
import com.pashkd.krender.engine.terrain.TerrainComponent
import com.pashkd.krender.engine.terrain.TerrainData
import com.pashkd.krender.engine.terrain.TerrainEditorSystem
import com.pashkd.krender.engine.terrain.TerrainMeshSyncSystem
import com.pashkd.krender.engine.terrain.TerrainRenderSystem
import com.pashkd.krender.engine.terrain.TerrainRendererComponent

class TerrainEditorScene(
    private val terrainResolution: Int = 128,
    private val vertexSpacing: Float = 1f,
) : Scene("terrain_editor") {
    private lateinit var editorSystem: TerrainEditorSystem

    override fun show() {
        editorSystem = TerrainEditorSystem(engine.input, engine.logger)

        world.systems.add(TerrainCameraControllerSystem(engine.input))
        world.systems.add(editorSystem)
        world.systems.add(TerrainMeshSyncSystem())
        world.systems.add(TerrainRenderSystem())

        createCamera()
        createLights()
        createTerrain()
    }

    override fun update(dt: Float) {
        super.update(dt)

        val terrainEntity = world.query<TerrainComponent, TerrainRendererComponent>().firstOrNull()
        val terrain = terrainEntity?.get<TerrainComponent>()
        val renderer = terrainEntity?.get<TerrainRendererComponent>()
        val hovered = editorSystem.hoveredHit?.worldPosition

        if (terrain != null && renderer != null) {
            engine.debug.put("Terrain size", "${terrain.data.width} x ${terrain.data.height}")
            engine.debug.put("Terrain spacing", "%.2f".format(terrain.data.vertexSpacing))
            engine.debug.put("Terrain vertices", renderer.vertexCount)
            engine.debug.put("Terrain triangles", renderer.triangleCount)
            engine.debug.put("Terrain layers", terrain.data.allLayers().size)
            engine.debug.put("Terrain display", renderer.displayMode.name)
        }
        engine.debug.put("Brush mode", editorSystem.activeBrushMode.name)
        engine.debug.put("Brush radius", "%.2f".format(editorSystem.brush.radius))
        engine.debug.put("Brush strength", "%.2f".format(editorSystem.brush.strength))
        engine.debug.put(
            "Hovered terrain",
            hovered?.let { "%.2f, %.2f, %.2f".format(it.x, it.y, it.z) } ?: "none",
        )

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

    private fun createTerrain() {
        val terrainData = TerrainData(
            width = terrainResolution,
            height = terrainResolution,
            vertexSpacing = vertexSpacing,
        )
        val baseLayer = terrainData.addLayer(name = "Base Layer", materialId = "terrain/base")
        editorSystem.brush.targetLayerId = baseLayer.id
        FlatTerrainGenerator().generate(terrainData)

        val terrain = world.createEntity("Terrain")
        terrain.add(TerrainComponent(terrainData))
        terrain.add(
            TerrainRendererComponent(
                modelId = "terrain_${terrainResolution}x${terrainResolution}",
                material = Material(baseColor = Color(0.38f, 0.63f, 0.31f)),
            ),
        )
    }
}
