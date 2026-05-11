package com.pashkd.krender.engine.terrain

import com.pashkd.krender.engine.api.DrawDynamicModel
import com.pashkd.krender.engine.api.DynamicMesh
import com.pashkd.krender.engine.api.DynamicModel
import com.pashkd.krender.engine.api.LogLevel
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.MaterialTextureRef
import com.pashkd.krender.engine.api.RuntimeTextureData
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.material.TerrainMaterialLibrary
import com.pashkd.krender.engine.render3d.Material
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TerrainRuntimePipelineTest {
    @Test
    fun `bakes final splat texture from material fallback colors`() {
        val terrain = terrainWithOneLayer(materialId = "terrain/grass")
        val texture = TerrainMaterialBakeService(TerrainMaterialLibrary(), NoopLogger).bakeFinalSplatTexture(
            terrain = terrain,
            resolution = 2,
            textureId = "runtime:test:grass",
            revision = 7L,
        )

        assertEquals("runtime:test:grass", texture.id)
        assertEquals(7L, texture.revision)
        assertEquals(2, texture.width)
        assertEquals(2, texture.height)
        assertTrue(texture.rgba8888.all { it == rgba8888(0.18f, 0.55f, 0.14f, 1f) })
    }

    @Test
    fun `bakes final splat texture from layer color when material id is invalid`() {
        val layerColor = TerrainLayerColorDescriptor(0.2f, 0.3f, 0.4f, 1f)
        val terrain = terrainWithOneLayer(materialId = "terrain/missing", color = layerColor)

        val texture = TerrainMaterialBakeService(TerrainMaterialLibrary(), NoopLogger).bakeFinalSplatTexture(
            terrain = terrain,
            resolution = 2,
            textureId = "runtime:test:layer-color",
            revision = 1L,
        )

        assertTrue(texture.rgba8888.all { it == rgba8888(layerColor.r, layerColor.g, layerColor.b, layerColor.a) })
    }

    @Test
    fun `runtime factory creates fallback terrain with base material when source is missing`() {
        val generator = RecordingGenerator()
        val terrain = TerrainRuntimeFactory(
            logger = NoopLogger,
            persistence = MissingTerrainPersistence,
            materialLibrary = TerrainMaterialLibrary(),
        ).loadOrCreate(
            terrainFilePath = "terrains/missing.krterrain",
            defaultResolution = 4,
            vertexSpacing = 2f,
            generator = generator,
        )

        assertTrue(generator.called)
        assertEquals(4, terrain.width)
        assertEquals(4, terrain.height)
        assertEquals(2f, terrain.vertexSpacing)
        val baseLayer = terrain.allLayers().single()
        assertEquals("terrain/grass", baseLayer.materialId)
        assertEquals(1f, terrain.getLayerWeight(baseLayer.id, 0, 0))
    }

    @Test
    fun `terrain render commands prefer final texture over editor preview texture`() {
        val world = SceneWorld()
        val entity = world.createEntity("Terrain")
        val finalTexture = runtimeTexture("runtime:final")
        val previewTexture = runtimeTexture("runtime:preview")
        entity.add(
            TerrainRendererComponent(
                modelId = "terrain_model",
                model = dummyModel("terrain_model"),
                material = Material(diffuseTextureRef = MaterialTextureRef("fallback")),
                previewDiffuseTexture = previewTexture,
                finalSplatTexture = finalTexture,
            ),
        )

        val commands = mutableListOf<DrawDynamicModel>()
        TerrainRenderCommands.submit(world, commands::add)

        val command = commands.single()
        assertEquals("runtime:final", command.material.diffuseTextureRef?.id)
        assertEquals(listOf(finalTexture), command.runtimeTextures)
    }

    @Test
    fun `runtime final splat texture helper creates unique ids per terrain`() {
        val first = runtimeTerrainFinalSplatTextureId(entityId = 1L, modelId = "runtime terrain")
        val second = runtimeTerrainFinalSplatTextureId(entityId = 2L, modelId = "runtime terrain")

        assertNotEquals(first, second)
        assertTrue(first.startsWith(RUNTIME_TERRAIN_FINAL_SPLAT_TEXTURE_ID))
        assertFalse(first.contains(" "))
    }

    @Test
    fun `runtime mesh system gives multiple terrains unique final texture ids`() {
        val world = SceneWorld()
        val first = world.createEntity("First Terrain")
        val second = world.createEntity("Second Terrain")
        first.add(TerrainDataComponent(terrainWithOneLayer()))
        second.add(TerrainDataComponent(terrainWithOneLayer()))
        val firstRenderer = first.add(TerrainRendererComponent(modelId = "terrain"))
        val secondRenderer = second.add(TerrainRendererComponent(modelId = "terrain"))

        RuntimeTerrainMeshSystem(
            materialBakeService = TerrainMaterialBakeService(TerrainMaterialLibrary(), NoopLogger),
            logger = NoopLogger,
            finalSplatResolution = 2,
        ).update(world, dt = 0f)

        val firstTexture = assertNotNull(firstRenderer.finalSplatTexture)
        val secondTexture = assertNotNull(secondRenderer.finalSplatTexture)
        assertNotEquals(firstTexture.id, secondTexture.id)
        assertEquals(firstTexture.id, firstRenderer.material.diffuseTextureRef?.id)
        assertEquals(secondTexture.id, secondRenderer.material.diffuseTextureRef?.id)
    }

    private fun terrainWithOneLayer(
        materialId: String = "terrain/grass",
        color: TerrainLayerColorDescriptor = TerrainLayerColorDescriptor(0.9f, 0.1f, 0.2f, 1f),
    ): TerrainData {
        val terrain = TerrainData(width = 2, height = 2, vertexSpacing = 1f)
        val layer = terrain.addLayer(
            name = "Base",
            materialId = materialId,
            color = color,
            visible = true,
        )
        for (y in 0 until terrain.height) {
            for (x in 0 until terrain.width) {
                terrain.setLayerWeight(layer.id, x, y, 1f)
            }
        }
        return terrain
    }

    private fun dummyModel(id: String): DynamicModel =
        DynamicModel(
            id = id,
            mesh = DynamicMesh(
                positions = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f),
                normals = floatArrayOf(0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f),
                uvs = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f),
                indices = intArrayOf(0, 1, 2),
            ),
        )

    private fun runtimeTexture(id: String): RuntimeTextureData =
        RuntimeTextureData(
            id = id,
            revision = 1L,
            width = 1,
            height = 1,
            rgba8888 = intArrayOf(rgba8888(1f, 1f, 1f, 1f)),
        )

    private fun rgba8888(r: Float, g: Float, b: Float, a: Float): Int {
        val red = (r.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        val green = (g.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        val blue = (b.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        val alpha = (a.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        return (red shl 24) or (green shl 16) or (blue shl 8) or alpha
    }

    private object NoopLogger : Logger {
        override fun log(level: LogLevel, tag: String, error: Throwable?, message: () -> String) = Unit
    }

    private object MissingTerrainPersistence : TerrainRuntimePersistence {
        override fun existsReadable(filePath: String): Boolean = false
        override fun readableSource(filePath: String): String = "missing"
        override fun loadDescriptor(filePath: String): TerrainFileDescriptor =
            error("Missing persistence should not load descriptors")
    }

    private class RecordingGenerator : TerrainGenerator {
        override val id: String = "recording"
        var called: Boolean = false
            private set

        override fun generate(data: TerrainData) {
            called = true
            data.setHeight(0, 0, 3f)
        }
    }
}
