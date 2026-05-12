package com.pashkd.krender.engine.sceneeditor

import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.LogLevel
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.scene.SceneDescriptor
import com.pashkd.krender.engine.scene.SceneLightingDescriptor
import com.pashkd.krender.engine.scene.SceneSettingsDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SceneEditorLightSyncSystemTest {
    @Test
    fun `mirrors scene ambient settings and only directional and point entity lights into the editor runtime world`() {
        val document = SceneEditorDocument(SceneWorld())
        document.descriptor = SceneDescriptor(
            id = "scene:test",
            name = "Test Scene",
            settings = SceneSettingsDescriptor(
                lighting = SceneLightingDescriptor(
                    ambientColor = Color(0.2f, 0.3f, 0.4f, 1f),
                    ambientIntensity = 0.75f,
                ),
            ),
        )

        val directional = document.world.createEntity("Sun")
        directional.get<TransformComponent>()?.position?.set(1f, 2f, 3f)
        directional.add(
            LightComponent(
                type = LightType.Directional,
                color = Color(1f, 0.95f, 0.9f, 1f),
                intensity = 1.5f,
            ),
        )

        val ambient = document.world.createEntity("Legacy Ambient")
        ambient.add(
            LightComponent(
                type = LightType.Ambient,
                color = Color(0.4f, 0.5f, 0.6f, 1f),
                intensity = 0.5f,
            ),
        )

        val runtimeWorld = SceneWorld()
        runtimeWorld.systems.add(SceneEditorLightSyncSystem(document, NoopLogger))

        runtimeWorld.update(dt = 0f)
        runtimeWorld.flushCommands()

        val mirroredLights = runtimeWorld.all()
            .mapNotNull { entity -> entity.get<LightComponent>() }

        assertEquals(2, mirroredLights.size)
        assertNotNull(mirroredLights.firstOrNull { it.type == LightType.Directional })
        val mirroredAmbient = assertNotNull(mirroredLights.firstOrNull { it.type == LightType.Ambient })
        assertEquals(0.2f, mirroredAmbient.color.r)
        assertEquals(0.3f, mirroredAmbient.color.g)
        assertEquals(0.4f, mirroredAmbient.color.b)
        assertEquals(0.75f, mirroredAmbient.intensity)
        assertNotNull(
            runtimeWorld.all().firstOrNull { entity ->
                entity.get<SceneEditorMirroredLightComponent>()?.sourceEntityId == directional.id
            },
        )
    }

    private object NoopLogger : Logger {
        override fun log(level: LogLevel, tag: String, error: Throwable?, message: () -> String) = Unit
    }
}
