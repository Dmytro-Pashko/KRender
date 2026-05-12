package com.pashkd.krender.engine.scene

import kotlin.test.Test
import kotlin.test.assertEquals

class SceneAssetCollectorTest {
    @Test
    fun `collects model assets active terrain and skybox descriptor assets`() {
        val assets = SceneAssetCollector.collect(
            descriptor = SceneDescriptor(
                id = "scene:assets",
                name = "Assets",
                entities = listOf(
                    EntityDescriptor(
                        id = 1L,
                        name = "Model",
                        components = listOf(
                            ComponentDescriptor(SceneComponentTypes.Model, mapOf("model" to "model/tree.glb")),
                        ),
                    ),
                    EntityDescriptor(
                        id = 2L,
                        name = "Terrain A",
                        components = listOf(
                            ComponentDescriptor(SceneComponentTypes.Terrain, mapOf("terrain" to "terrains/field_a.krterrain")),
                        ),
                    ),
                    EntityDescriptor(
                        id = 3L,
                        name = "Terrain B",
                        components = listOf(
                            ComponentDescriptor(SceneComponentTypes.Terrain, mapOf("terrain" to "terrains/field_b.krterrain")),
                        ),
                    ),
                ),
                settings = SceneSettingsDescriptor(
                    activeTerrainEntityId = 3L,
                    environment = SceneEnvironmentDescriptor(skyboxAssetPath = "skyboxes/studio.krskybox"),
                ),
            ),
            skybox = SkyboxAssetDescriptor(
                id = "skybox:studio",
                name = "Studio",
                texturePath = "textures/studio.png",
            ),
        )

        assertEquals(
            listOf("model/tree.glb", "terrains/field_b.krterrain", "textures/studio.png"),
            assets.assetRefs.map { it.path },
        )
        assertEquals(listOf("skyboxes/studio.krskybox"), assets.descriptorPaths)
    }
}
