package com.pashkd.krender.engine.scene

import kotlin.test.Test
import kotlin.test.assertEquals

class SceneAssetCollectorTest {
    @Test
    fun `collects scene entity assets and skybox descriptor assets`() {
        val assets = SceneAssetCollector.collect(
            descriptor = SceneDescriptor(
                id = "scene:assets",
                name = "Assets",
                entities = listOf(
                    EntityDescriptor(
                        id = 1L,
                        name = "Model",
                        components = listOf(
                            ComponentDescriptor("ModelComponent", mapOf("model" to "model/tree.glb")),
                        ),
                    ),
                    EntityDescriptor(
                        id = 2L,
                        name = "Terrain",
                        components = listOf(
                            ComponentDescriptor("TerrainComponent", mapOf("terrain" to "terrains/field.krterrain")),
                        ),
                    ),
                ),
                settings = SceneSettingsDescriptor(
                    environment = SceneEnvironmentDescriptor(skyboxAssetPath = "skyboxes/studio.krskybox"),
                ),
            ),
            skybox = SkyboxAssetDescriptor(
                id = "skybox:studio",
                name = "Studio",
                modelPath = "model/skybox.glb",
                texturePath = "textures/studio.png",
            ),
        )

        assertEquals(
            listOf("model/tree.glb", "terrains/field.krterrain", "model/skybox.glb", "textures/studio.png"),
            assets.map { it.path },
        )
    }
}
