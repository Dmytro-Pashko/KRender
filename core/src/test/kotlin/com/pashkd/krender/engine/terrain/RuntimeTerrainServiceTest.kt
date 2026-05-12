package com.pashkd.krender.engine.terrain

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.LogLevel
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.material.TerrainMaterialDescriptor
import com.pashkd.krender.engine.material.TerrainMaterialLibrary
import com.pashkd.krender.engine.scene.SceneDescriptor
import com.pashkd.krender.engine.scene.SceneSettingsDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RuntimeTerrainServiceTest {
    @Test
    fun `prepares only active terrain`() {
        val world = SceneWorld()
        val inactiveTerrain = world.createEntityWithId(1L, "Inactive Terrain")
        inactiveTerrain.add(TerrainComponent(terrain = AssetRef.terrain("terrains/inactive.json"), bakedTextureResolution = 256))
        val activeTerrain = world.createEntityWithId(2L, "Active Terrain")
        activeTerrain.add(TerrainComponent(terrain = AssetRef.terrain("terrains/active.json"), bakedTextureResolution = 1024))

        val loadedPaths = mutableListOf<String>()
        val service = RuntimeTerrainService(
            logger = NoopLogger,
            terrainLoader = RuntimeTerrainLoader { path ->
                loadedPaths += path
                terrainWithOneLayer()
            },
            materialBakeService = TerrainMaterialBakeService(testMaterialLibrary(), NoopLogger),
        )

        val result = service.prepareActiveTerrain(
            world = world,
            descriptor = descriptor(activeTerrainEntityId = 2L),
        )

        assertEquals(listOf("terrains/active.json"), loadedPaths)
        assertEquals(2L, result.entityId)
        assertEquals(1024, result.finalSplatResolution)
        assertNotNull(activeTerrain.get<TerrainDataComponent>())
        assertNotNull(activeTerrain.get<TerrainRendererComponent>())
        assertNull(inactiveTerrain.get<TerrainDataComponent>())
        assertNull(inactiveTerrain.get<TerrainRendererComponent>())
    }

    @Test
    fun `throws when active terrain invalid`() {
        val world = SceneWorld()
        world.createEntityWithId(7L, "Broken Terrain")

        val service = RuntimeTerrainService(
            logger = NoopLogger,
            terrainLoader = RuntimeTerrainLoader { terrainWithOneLayer() },
            materialBakeService = TerrainMaterialBakeService(testMaterialLibrary(), NoopLogger),
        )

        val error = assertFailsWith<IllegalStateException> {
            service.prepareActiveTerrain(world, descriptor(activeTerrainEntityId = 7L))
        }

        assertEquals(
            "Runtime scene activeTerrainEntityId=7 does not reference an entity with TerrainComponent.",
            error.message,
        )
    }

    @Test
    fun `throws when active terrain path fails to load`() {
        val world = SceneWorld()
        val terrain = world.createEntityWithId(9L, "Terrain")
        terrain.add(TerrainComponent(terrain = AssetRef.terrain("terrains/missing.json"), bakedTextureResolution = 512))

        val service = RuntimeTerrainService(
            logger = NoopLogger,
            terrainLoader = RuntimeTerrainLoader { throw IllegalArgumentException("missing") },
            materialBakeService = TerrainMaterialBakeService(testMaterialLibrary(), NoopLogger),
        )

        val error = assertFailsWith<IllegalStateException> {
            service.prepareActiveTerrain(world, descriptor(activeTerrainEntityId = 9L))
        }

        assertEquals(
            "Failed to load runtime terrain entityId=9 path='terrains/missing.json': missing",
            error.message,
        )
    }

    private fun descriptor(activeTerrainEntityId: Long): SceneDescriptor =
        SceneDescriptor(
            id = "scene:test",
            name = "Runtime Terrain",
            settings = SceneSettingsDescriptor(activeTerrainEntityId = activeTerrainEntityId),
        )

    private fun terrainWithOneLayer(): TerrainData {
        val terrain = TerrainData(width = 2, height = 2, vertexSpacing = 1f)
        val layer = terrain.addLayer(
            name = "Base",
            materialId = "terrain/grass",
            color = TerrainLayerColorDescriptor(0.9f, 0.1f, 0.2f, 1f),
            visible = true,
        )
        for (y in 0 until terrain.height) {
            for (x in 0 until terrain.width) {
                terrain.setLayerWeight(layer.id, x, y, 1f)
            }
        }
        return terrain
    }

    private fun testMaterialLibrary(): TerrainMaterialLibrary =
        TerrainMaterialLibrary.fromMaterials(
            listOf(
                TerrainMaterialDescriptor(
                    id = "terrain/grass",
                    name = "Grass",
                    albedoTexture = "textures/t_grass_01_s.png",
                    fallbackColor = TerrainLayerColorDescriptor(0.18f, 0.55f, 0.14f, 1f),
                    defaultTiling = 8f,
                ),
            ),
        )

    private fun interface RuntimeTerrainLoader : com.pashkd.krender.engine.terrain.RuntimeTerrainLoader

    private object NoopLogger : Logger {
        override fun log(level: LogLevel, tag: String, error: Throwable?, message: () -> String) = Unit
    }
}
