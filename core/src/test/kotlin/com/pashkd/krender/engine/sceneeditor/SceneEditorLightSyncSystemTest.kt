package com.pashkd.krender.engine.sceneeditor

import com.pashkd.krender.engine.api.Color
import com.pashkd.krender.engine.api.LogLevel
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SceneEditorLightSyncSystemTest {
    @Test
    fun `mirrors only directional and point lights into the editor runtime world`() {
        val document = SceneEditorDocument(SceneWorld())

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

        assertEquals(1, mirroredLights.size)
        assertEquals(LightType.Directional, mirroredLights.single().type)
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
