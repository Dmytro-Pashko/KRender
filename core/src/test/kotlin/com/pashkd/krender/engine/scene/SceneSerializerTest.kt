package com.pashkd.krender.engine.scene

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.terrain.TerrainComponent
import com.pashkd.krender.engine.terrain.TerrainPreviewMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        assertEquals(SceneComponentTypes.Terrain, decodedTerrain.components.single { it.type == SceneComponentTypes.Terrain }.type)
        assertEquals(
            "1024",
            decodedTerrain.components.single { it.type == SceneComponentTypes.Terrain }.properties["bakedTextureResolution"],
        )
    }

    @Test
    fun `round trips nested scene settings`() {
        val descriptor = SceneDescriptor(
            id = "scene:test",
            name = "Scene Settings",
            settings = SceneSettingsDescriptor(
                activeCameraEntityId = 11L,
                activeTerrainEntityId = 22L,
                lighting = SceneLightingDescriptor(
                    ambientColor = Color(0.1f, 0.2f, 0.3f, 1f),
                    ambientIntensity = 0.7f,
                ),
                environment = SceneEnvironmentDescriptor(
                    skyboxAssetPath = "skyboxes/studio.krskybox",
                    showSkybox = false,
                    environmentIntensity = 1.25f,
                ),
                terrain = SceneTerrainSettingsDescriptor(
                    materialLibraryPath = "materials/runtime_terrain_materials.json",
                ),
            ),
        )

        val decoded = SceneSerializer.decode(SceneSerializer.encode(descriptor))

        assertEquals(11L, decoded.settings.activeCameraEntityId)
        assertEquals(22L, decoded.settings.activeTerrainEntityId)
        assertEquals(0.1f, decoded.settings.lighting.ambientColor.r)
        assertEquals(0.2f, decoded.settings.lighting.ambientColor.g)
        assertEquals(0.3f, decoded.settings.lighting.ambientColor.b)
        assertEquals(0.7f, decoded.settings.lighting.ambientIntensity)
        assertEquals("skyboxes/studio.krskybox", decoded.settings.environment.skyboxAssetPath)
        assertEquals(false, decoded.settings.environment.showSkybox)
        assertEquals(1.25f, decoded.settings.environment.environmentIntensity)
        assertEquals("materials/runtime_terrain_materials.json", decoded.settings.terrain.materialLibraryPath)
    }

    @Test
    fun `decodes legacy ambient settings when lighting node is missing`() {
        val decoded = SceneSerializer.decode(
            """
            {
              "schemaVersion": 1,
              "id": "scene:legacy",
              "name": "Legacy",
              "entities": [],
              "settings": {
                "activeCameraEntityId": null,
                "activeTerrainEntityId": null,
                "ambientLightEntityId": null,
                "ambientLightColor": "0.4,0.5,0.6,1.0",
                "ambientLightIntensity": 0.8
              }
            }
            """.trimIndent(),
        )

        assertEquals(0.4f, decoded.settings.lighting.ambientColor.r)
        assertEquals(0.5f, decoded.settings.lighting.ambientColor.g)
        assertEquals(0.6f, decoded.settings.lighting.ambientColor.b)
        assertEquals(0.8f, decoded.settings.lighting.ambientIntensity)
    }

    @Test
    fun `decodes literal string null skybox path as missing skybox`() {
        val decoded = SceneSerializer.decode(
            """
            {
              "schemaVersion": 1,
              "id": "scene:legacy-null-skybox",
              "name": "Legacy Null Skybox",
              "entities": [],
              "settings": {
                "environment": {
                  "skyboxAssetPath": "null",
                  "showSkybox": true,
                  "environmentIntensity": 1.0
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals(null, decoded.settings.environment.skyboxAssetPath)
        assertEquals(true, decoded.settings.environment.showSkybox)
        assertEquals(1f, decoded.settings.environment.environmentIntensity)
    }

    @Test
    fun `serializer writes only new settings format`() {
        val encoded = SceneSerializer.encode(
            SceneDescriptor(
                id = "scene:new-format",
                name = "New Format",
                settings = SceneSettingsDescriptor(
                    lighting = SceneLightingDescriptor(
                        ambientColor = Color(0.25f, 0.35f, 0.45f, 1f),
                        ambientIntensity = 0.9f,
                    ),
                    terrain = SceneTerrainSettingsDescriptor(
                        materialLibraryPath = "materials/terrain_runtime.json",
                    ),
                ),
            ),
        )

        assertTrue(encoded.contains("\"lighting\""))
        assertTrue(encoded.contains("\"environment\""))
        assertTrue(encoded.contains("\"terrain\""))
        assertFalse(encoded.contains("\"ambientLightColor\""))
        assertFalse(encoded.contains("\"ambientLightIntensity\""))
        assertFalse(encoded.contains("\"ambientLightEntityId\""))
    }
}
