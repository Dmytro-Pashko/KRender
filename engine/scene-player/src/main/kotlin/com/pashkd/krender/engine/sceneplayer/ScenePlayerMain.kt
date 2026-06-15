package com.pashkd.krender.engine.sceneplayer

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.backend.gdx.GdxEngineApplication
import com.pashkd.krender.engine.scene.EditorToolLauncher
import com.pashkd.krender.engine.scene.RuntimeWindowLauncher

/**
 * Convenience GDX application wrapper for running Scene Player directly.
 *
 * The backend-neutral playback logic lives in [ScenePlayerScene], [ScenePlayerBuilder],
 * and [ScenePlayerModule]. This class only wires that player route into the existing
 * [GdxEngineApplication] bootstrap for platforms that want a dedicated player entry point.
 */
class ScenePlayerMain(
    sceneName: String? = configuredSceneName(),
    scenePath: String? = configuredScenePath(),
    runtimeWindowLauncherFactory: (Logger) -> RuntimeWindowLauncher = {
        com.pashkd.krender.engine.scene.UnsupportedRuntimeWindowLauncher
    },
    editorToolLauncherFactory: (Logger) -> EditorToolLauncher = {
        com.pashkd.krender.engine.scene.UnsupportedEditorToolLauncher
    },
) : GdxEngineApplication(
        initialScene = {
            val requestedScene = sceneName?.trim()?.takeIf(String::isNotBlank) ?: DEFAULT_SCENE
            ScenePlayerModule.createScene(
                sceneName = requestedScene,
                scenePath = scenePath,
            ) ?: throw IllegalArgumentException(
                "Unknown krender.scene '$requestedScene'. Supported scene-player routes: scene-player, scene-viewer, runtime-scene.",
            )
        },
        runtimeWindowLauncherFactory = runtimeWindowLauncherFactory,
        editorToolLauncherFactory = editorToolLauncherFactory,
    ) {
    companion object {
        private const val DEFAULT_SCENE = "scene-player"

        fun configuredSceneName(): String? = System.getProperty("krender.scene")?.takeIf(String::isNotBlank)

        fun configuredScenePath(): String? = System.getProperty("krender.scene.path")?.takeIf(String::isNotBlank)
    }
}
