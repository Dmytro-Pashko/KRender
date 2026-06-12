package com.pashkd.krender.engine.assets

import com.pashkd.krender.engine.scene.ComponentDescriptor
import com.pashkd.krender.engine.scene.EntityDescriptor
import com.pashkd.krender.engine.scene.SceneComponentTypes
import com.pashkd.krender.engine.scene.SceneDescriptor
import com.pashkd.krender.engine.scene.SceneEnvironmentDescriptor
import com.pashkd.krender.engine.scene.SceneLightingDescriptor
import com.pashkd.krender.engine.scene.SceneSerializer
import com.pashkd.krender.engine.scene.SceneSettingsDescriptor
import com.pashkd.krender.engine.scene.SceneTerrainSettingsDescriptor
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SceneAssetMetadataReaderTest {
    @Test
    fun `reads scene summary including terrain environment and bounds`() {
        val baseDir = Files.createTempDirectory("scene-asset-metadata-test")
        baseDir.resolve("scenes").createDirectories()
        baseDir.resolve("terrains").createDirectories()
        baseDir.resolve("terrains/test_terrain.json").writeText(
            """
            {
              "width": 512,
              "height": 256,
              "layers": [
                { "id": "grass" },
                { "id": "rock" },
                { "id": "sand" }
              ]
            }
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val sceneFile = baseDir.resolve("scenes/test_scene.krscene")
        sceneFile.writeText(
            SceneSerializer.encode(
                SceneDescriptor(
                    id = "scene:test",
                    name = "Test Scene",
                    entities =
                        listOf(
                            EntityDescriptor(
                                id = 1L,
                                name = "Main Camera",
                                active = true,
                                components =
                                    listOf(
                                        transform("-5.0,1.0,2.0"),
                                        ComponentDescriptor(SceneComponentTypes.Camera, mapOf("fieldOfViewDegrees" to "67.0")),
                                    ),
                            ),
                            EntityDescriptor(
                                id = 2L,
                                name = "Sun",
                                active = true,
                                components =
                                    listOf(
                                        transform("1.0,4.0,-1.0"),
                                        ComponentDescriptor(SceneComponentTypes.Light, mapOf("type" to "Directional")),
                                    ),
                            ),
                            EntityDescriptor(
                                id = 3L,
                                name = "Lamp",
                                active = false,
                                components =
                                    listOf(
                                        transform("3.0,2.0,6.0"),
                                        ComponentDescriptor(SceneComponentTypes.Light, mapOf("type" to "Point")),
                                    ),
                            ),
                            EntityDescriptor(
                                id = 4L,
                                name = "Building",
                                active = true,
                                components =
                                    listOf(
                                        transform("4.0,0.0,8.0"),
                                        ComponentDescriptor(SceneComponentTypes.Model, mapOf("model" to "model/building.glb")),
                                    ),
                            ),
                            EntityDescriptor(
                                id = 5L,
                                name = "Terrain",
                                active = true,
                                parentId = 4L,
                                components =
                                    listOf(
                                        transform("0.0,0.0,0.0"),
                                        ComponentDescriptor(
                                            SceneComponentTypes.Terrain,
                                            mapOf(
                                                "terrain" to "terrains/test_terrain.json",
                                                "bakedTextureResolution" to "2048",
                                            ),
                                        ),
                                    ),
                            ),
                        ),
                    settings =
                        SceneSettingsDescriptor(
                            activeCameraEntityId = 1L,
                            activeTerrainEntityId = 5L,
                            lighting = SceneLightingDescriptor(ambientIntensity = 0.35f),
                            environment =
                                SceneEnvironmentDescriptor(
                                    skyboxAssetPath = "skyboxes/test.krskybox",
                                    showSkybox = true,
                                    environmentIntensity = 1.5f,
                                ),
                            terrain =
                                SceneTerrainSettingsDescriptor(
                                    materialLibraryPath = "materials/terrain_materials.json",
                                ),
                        ),
                ),
            ),
            StandardCharsets.UTF_8,
        )

        val metadata = SceneAssetMetadataReader.read(sceneFile.toFile(), baseDir.toFile())

        assertNotNull(metadata)
        assertEquals("Test Scene", metadata.sceneName)
        assertEquals(5, metadata.entityCount)
        assertEquals(4, metadata.activeEntityCount)
        assertEquals(1, metadata.inactiveEntityCount)
        assertEquals(4, metadata.rootEntityCount)
        assertEquals(1, metadata.cameraCount)
        assertEquals(2, metadata.lightCount)
        assertEquals(1, metadata.directionalLightCount)
        assertEquals(1, metadata.pointLightCount)
        assertEquals(1, metadata.modelCount)
        assertEquals(1, metadata.terrainCount)
        assertEquals("Main Camera", metadata.activeCameraName)
        assertEquals("Terrain", metadata.activeTerrainName)
        assertEquals("terrains/test_terrain.json", metadata.activeTerrainPath)
        assertEquals("512 x 256", metadata.activeTerrainSize)
        assertEquals(3, metadata.activeTerrainLayerCount)
        assertEquals(2048, metadata.activeTerrainBakedResolution)
        assertEquals("skyboxes/test.krskybox", metadata.skyboxPath)
        assertEquals(true, metadata.showSkybox)
        assertEquals(1.5f, metadata.environmentIntensity)
        assertEquals(0.35f, metadata.ambientIntensity)
        assertEquals("materials/terrain_materials.json", metadata.terrainMaterialLibraryPath)
        assertEquals("9.00 x 4.00 x 9.00", metadata.sceneBounds?.formatted())
        assertEquals(4, metadata.dependencyCount)
        assertEquals(3, metadata.missingDependencyCount)
        assertEquals(1, metadata.validationWarningCount)
    }

    @Test
    fun `omits terrain detail fields when active terrain metadata cannot be resolved`() {
        val descriptor =
            SceneDescriptor(
                id = "scene:no-terrain-file",
                name = "No Terrain File",
                entities =
                    listOf(
                        EntityDescriptor(
                            id = 10L,
                            name = "Terrain",
                            components =
                                listOf(
                                    ComponentDescriptor(
                                        SceneComponentTypes.Terrain,
                                        mapOf(
                                            "terrain" to "terrains/missing.json",
                                            "bakedTextureResolution" to "1024",
                                        ),
                                    ),
                                ),
                        ),
                    ),
                settings = SceneSettingsDescriptor(activeTerrainEntityId = 10L),
            )

        val metadata =
            SceneAssetMetadataReader.fromDescriptor(
                descriptor = descriptor,
                baseDirectory = Files.createTempDirectory("scene-asset-metadata-empty").toFile(),
            )

        assertEquals("Terrain", metadata.activeTerrainName)
        assertEquals("terrains/missing.json", metadata.activeTerrainPath)
        assertEquals(1024, metadata.activeTerrainBakedResolution)
        assertNull(metadata.activeTerrainSize)
        assertNull(metadata.activeTerrainLayerCount)
        assertEquals(3, metadata.validationErrorCount)
    }

    private fun transform(position: String): ComponentDescriptor =
        ComponentDescriptor(
            SceneComponentTypes.Transform,
            mapOf(
                "position" to position,
                "rotation" to "0.0,0.0,0.0",
                "scale" to "1.0,1.0,1.0",
            ),
        )
}
