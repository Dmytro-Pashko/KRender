package com.pashkd.krender.engine.scene

import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SceneSerializerTest {
    @Test
    fun `decode reads ambient settings directly from scene settings`() {
        val descriptor = SceneSerializer.decode(
            """
            {
              "schemaVersion": 1,
              "id": "scene:test",
              "name": "Test Scene",
              "entities": [],
              "settings": {
                "activeCameraEntityId": null,
                "ambientLightEntityId": null,
                "ambientLightColor": "0.1,0.2,0.3,1.0",
                "ambientLightIntensity": 0.65
              }
            }
            """.trimIndent(),
        )

        assertEquals("scene:test", descriptor.id)
        assertEquals(0.1f, descriptor.settings.ambientLightColor.r)
        assertEquals(0.2f, descriptor.settings.ambientLightColor.g)
        assertEquals(0.3f, descriptor.settings.ambientLightColor.b)
        assertEquals(1f, descriptor.settings.ambientLightColor.a)
        assertEquals(0.65f, descriptor.settings.ambientLightIntensity)
    }

    @Test
    fun `toDescriptor preserves ambient settings instead of reading ambient light entities`() {
        val world = SceneWorld()
        world.createEntity("Ambient Light").add(
            LightComponent(
                type = LightType.Ambient,
                color = Color(0.9f, 0.1f, 0.1f, 1f),
                intensity = 3f,
            ),
        )
        val existingDescriptor = SceneDescriptor(
            id = "scene:test",
            name = "Existing Scene",
            settings = SceneSettingsDescriptor(
                ambientLightColor = Color(0.2f, 0.3f, 0.4f, 1f),
                ambientLightIntensity = 0.45f,
            ),
        )

        val descriptor = SceneSerializer.toDescriptor(
            world = world,
            sceneName = "Updated Scene",
            existingDescriptor = existingDescriptor,
        )

        assertTrue(descriptor.entities.isEmpty())
        assertEquals(0.2f, descriptor.settings.ambientLightColor.r)
        assertEquals(0.3f, descriptor.settings.ambientLightColor.g)
        assertEquals(0.4f, descriptor.settings.ambientLightColor.b)
        assertEquals(1f, descriptor.settings.ambientLightColor.a)
        assertEquals(0.45f, descriptor.settings.ambientLightIntensity)
    }
}
