package com.pashkd.krender.engine.scene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SkyboxAssetSerializerTest {
    @Test
    fun `round trips skybox asset descriptor`() {
        val descriptor = SkyboxAssetDescriptor(
            id = "skybox:studio",
            name = "Studio",
            texturePath = "textures/default_skybox_studio.png",
            intensity = 1.5f,
        )

        val decoded = SkyboxAssetSerializer.decode(SkyboxAssetSerializer.encode(descriptor))

        assertEquals("skybox:studio", decoded.id)
        assertEquals("Studio", decoded.name)
        assertEquals("textures/default_skybox_studio.png", decoded.texturePath)
        assertEquals(1.5f, decoded.intensity)
    }

    @Test
    fun `blank texturePath throws`() {
        val error = assertFailsWith<IllegalArgumentException> {
            SkyboxAssetSerializer.decode(
                """
                {
                  "id": "skybox:blank",
                  "name": "Blank",
                  "texturePath": "   "
                }
                """.trimIndent(),
            )
        }

        assertEquals("Skybox asset texturePath must not be blank", error.message)
    }

    @Test
    fun `negative intensity throws`() {
        val error = assertFailsWith<IllegalArgumentException> {
            SkyboxAssetSerializer.decode(
                """
                {
                  "id": "skybox:negative",
                  "name": "Negative",
                  "texturePath": "textures/studio.png",
                  "intensity": -1.0
                }
                """.trimIndent(),
            )
        }

        assertEquals("Skybox asset intensity must be greater than or equal to 0", error.message)
    }
}
