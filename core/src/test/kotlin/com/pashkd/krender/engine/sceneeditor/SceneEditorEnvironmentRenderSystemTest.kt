package com.pashkd.krender.engine.sceneeditor

import com.pashkd.krender.engine.api.ApplyEnvironment
import com.pashkd.krender.engine.api.LogLevel
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.SceneWorld
import com.pashkd.krender.engine.scene.SceneDescriptor
import com.pashkd.krender.engine.scene.SceneEnvironmentDescriptor
import com.pashkd.krender.engine.scene.SceneFileService
import com.pashkd.krender.engine.scene.SceneLightingDescriptor
import com.pashkd.krender.engine.scene.SceneSettingsDescriptor
import com.pashkd.krender.engine.scene.SkyboxAssetDescriptor
import com.pashkd.krender.engine.scene.SkyboxAssetSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SceneEditorEnvironmentRenderSystemTest {
    @Test
    fun `submits environment command for configured scene skybox`() {
        val document = SceneEditorDocument(SceneWorld())
        document.descriptor =
            SceneDescriptor(
                id = "scene:test",
                name = "Test",
                settings =
                    SceneSettingsDescriptor(
                        lighting = SceneLightingDescriptor(ambientIntensity = 0.4f),
                        environment =
                            SceneEnvironmentDescriptor(
                                skyboxAssetPath = "skyboxes/studio.krskybox",
                                showSkybox = false,
                                environmentIntensity = 1.5f,
                            ),
                    ),
            )
        val runtimeWorld = SceneWorld()
        runtimeWorld.systems.add(
            SceneEditorEnvironmentRenderSystem(
                document = document,
                sceneFiles =
                    TestSceneFileService(
                        mapOf(
                            "skyboxes/studio.krskybox" to
                                SkyboxAssetSerializer.encode(
                                    SkyboxAssetDescriptor(
                                        id = "skybox:studio",
                                        name = "Studio",
                                        texturePath = "textures/studio.png",
                                        intensity = 0.5f,
                                    ),
                                ),
                        ),
                    ),
                logger = NoopLogger,
            ),
        )

        runtimeWorld.render(alpha = 0f)

        val environment = assertIs<ApplyEnvironment>(runtimeWorld.renderCommands.snapshot().single())
        assertEquals("textures/studio.png", environment.skyboxTexture?.id)
        assertEquals(false, environment.showSkybox)
        assertEquals(0.4f, environment.ambientIntensity)
        assertEquals(0.75f, environment.environmentIntensity)
    }

    @Test
    fun `submits environment command without skybox texture when scene has no skybox`() {
        val document = SceneEditorDocument(SceneWorld())
        document.descriptor =
            SceneDescriptor(
                id = "scene:test",
                name = "Test",
                settings =
                    SceneSettingsDescriptor(
                        environment =
                            SceneEnvironmentDescriptor(
                                skyboxAssetPath = null,
                                showSkybox = true,
                                environmentIntensity = 2f,
                            ),
                    ),
            )
        val runtimeWorld = SceneWorld()
        runtimeWorld.systems.add(
            SceneEditorEnvironmentRenderSystem(
                document = document,
                sceneFiles = TestSceneFileService(emptyMap()),
                logger = NoopLogger,
            ),
        )

        runtimeWorld.render(alpha = 0f)

        val environment = assertIs<ApplyEnvironment>(runtimeWorld.renderCommands.snapshot().single())
        assertNull(environment.skyboxTexture)
        assertEquals(true, environment.showSkybox)
        assertEquals(2f, environment.environmentIntensity)
    }

    private class TestSceneFileService(
        private val files: Map<String, String>,
    ) : SceneFileService {
        override fun writeText(
            path: String,
            text: String,
        ) = error("Test scene files are read-only")

        override fun readText(path: String): String = files[path] ?: error("Missing test scene file '$path'")

        override fun ensureDirectories(path: String) = Unit

        override fun exists(path: String): Boolean = files.containsKey(path)

        override fun describeReadableSource(path: String): String = if (exists(path)) "test" else "missing"
    }

    private object NoopLogger : Logger {
        override fun log(
            level: LogLevel,
            tag: String,
            error: Throwable?,
            message: () -> String,
        ) = Unit
    }
}
