package com.pashkd.krender.engine.render3d

import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.scene.SceneEnvironmentDescriptor
import com.pashkd.krender.engine.scene.SceneLightingDescriptor
import com.pashkd.krender.engine.scene.SceneSettingsDescriptor
import com.pashkd.krender.engine.scene.SkyboxAssetDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals

class RuntimeEnvironmentFactoryTest {
    @Test
    fun `creates runtime environment from scene settings and skybox asset`() {
        val environment = RuntimeEnvironmentFactory.fromSceneSettings(
            settings = SceneSettingsDescriptor(
                lighting = SceneLightingDescriptor(
                    ambientColor = Color(0.2f, 0.3f, 0.4f, 1f),
                    ambientIntensity = 0.6f,
                ),
                environment = SceneEnvironmentDescriptor(
                    skyboxAssetPath = "skyboxes/studio.krskybox",
                    showSkybox = false,
                    environmentIntensity = 2f,
                ),
            ),
            skybox = SkyboxAssetDescriptor(
                id = "skybox:studio",
                name = "Studio",
                texturePath = "textures/studio.png",
                intensity = 0.5f,
            ),
        )

        assertEquals("textures/studio.png", environment.skyboxTexturePath)
        assertEquals(false, environment.showSkybox)
        assertEquals(0.2f, environment.ambientColor.r)
        assertEquals(0.3f, environment.ambientColor.g)
        assertEquals(0.4f, environment.ambientColor.b)
        assertEquals(0.6f, environment.ambientIntensity)
        assertEquals(1f, environment.environmentIntensity)
    }

    @Test
    fun `creates runtime environment without skybox texture when skybox is unresolved`() {
        val environment = RuntimeEnvironmentFactory.fromSceneSettings(
            settings = SceneSettingsDescriptor(
                lighting = SceneLightingDescriptor(
                    ambientColor = Color(0.1f, 0.2f, 0.3f, 1f),
                    ambientIntensity = 0.4f,
                ),
                environment = SceneEnvironmentDescriptor(
                    skyboxAssetPath = null,
                    showSkybox = true,
                    environmentIntensity = 2f,
                ),
            ),
            skybox = null,
        )

        assertEquals(null, environment.skyboxTexturePath)
        assertEquals(false, environment.showSkybox)
        assertEquals(2f, environment.environmentIntensity)
    }
}
