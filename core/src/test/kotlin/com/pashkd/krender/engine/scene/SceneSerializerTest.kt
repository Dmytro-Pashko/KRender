package com.pashkd.krender.engine.scene

import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.Color
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
    fun `serializes and deserializes environment skybox ref`() {
        val decoded = SceneSerializer.decode(
            SceneSerializer.encode(
                SceneDescriptor(
                    id = "scene:env",
                    name = "Environment",
                    settings = SceneSettingsDescriptor(
                        environment = SceneEnvironmentDescriptor(
                            skyboxAssetPath = "skyboxes/studio.krskybox",
                            showSkybox = true,
                            environmentIntensity = 2f,
                        ),
                    ),
                ),
            ),
        )

        assertEquals("skyboxes/studio.krskybox", decoded.settings.environment.skyboxAssetPath)
        assertEquals(true, decoded.settings.environment.showSkybox)
        assertEquals(2f, decoded.settings.environment.environmentIntensity)
    }
}
