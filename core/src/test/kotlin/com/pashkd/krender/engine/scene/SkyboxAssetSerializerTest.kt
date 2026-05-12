package com.pashkd.krender.engine.scene

import kotlin.test.Test
import kotlin.test.assertEquals

class SkyboxAssetSerializerTest {
    @Test
    fun `round trips skybox asset descriptor`() {
        val descriptor = SkyboxAssetDescriptor(
            id = "skybox:studio",
            name = "Studio",
            modelPath = "model/skybox.glb",
            texturePath = "textures/default_skybox_studio.png",
            intensity = 1.5f,
        )

        val decoded = SkyboxAssetSerializer.decode(SkyboxAssetSerializer.encode(descriptor))

        assertEquals("skybox:studio", decoded.id)
        assertEquals("Studio", decoded.name)
        assertEquals("model/skybox.glb", decoded.modelPath)
        assertEquals("textures/default_skybox_studio.png", decoded.texturePath)
        assertEquals(1.5f, decoded.intensity)
    }
}
