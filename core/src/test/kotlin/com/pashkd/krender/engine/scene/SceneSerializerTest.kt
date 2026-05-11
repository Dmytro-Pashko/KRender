package com.pashkd.krender.engine.scene

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.terrain.TerrainComponent
import com.pashkd.krender.engine.terrain.TerrainPreviewMode
import kotlin.test.Test
import kotlin.test.assertEquals

class SceneSerializerTest {
    @Test
    fun `round trips terrain component preview metadata`() {
        val world = SceneWorld()
        val terrain = world.createEntity("Terrain")
        terrain.add(
            TerrainComponent(
                terrain = AssetRef.terrain("terrains/terrain_01.krterrain"),
                visible = true,
                previewMode = TerrainPreviewMode.MaterialTexture,
                bakedTextureResolution = 1024,
            ),
        )

        val descriptor = SceneSerializer.toDescriptor(world, sceneName = "Terrain Scene")
        val decoded = SceneSerializer.decode(SceneSerializer.encode(descriptor))
        val decodedTerrain = decoded.entities.single()

        assertEquals(terrain.id, decoded.settings.activeTerrainEntityId)
        assertEquals("TerrainComponent", decodedTerrain.components.single { it.type == "TerrainComponent" }.type)
        assertEquals(
            "1024",
            decodedTerrain.components.single { it.type == "TerrainComponent" }.properties["bakedTextureResolution"],
        )
    }
}
