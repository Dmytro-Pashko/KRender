package com.pashkd.krender.game

import com.pashkd.krender.engine.terrain.TerrainPersistence
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssetBrowserSceneTest {
    @Test
    fun `default terrain content creates valid empty flat 64x64 terrain`() {
        val content = defaultTerrainContent("woolboy_terrain_sandbox")
        val descriptor = TerrainPersistence().decodeDescriptor(content)

        assertEquals("woolboy_terrain_sandbox", descriptor.name)
        assertEquals(64, descriptor.terrain.width)
        assertEquals(64, descriptor.terrain.height)
        assertEquals(1f, descriptor.terrain.vertexSpacing)
        assertEquals(64 * 64, descriptor.terrain.heights.size)
        assertTrue(descriptor.terrain.layers.isEmpty())
        assertContentEquals(FloatArray(64 * 64), descriptor.terrain.heights)
    }
}
