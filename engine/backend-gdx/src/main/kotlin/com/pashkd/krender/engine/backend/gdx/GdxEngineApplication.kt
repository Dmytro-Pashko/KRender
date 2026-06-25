package com.pashkd.krender.engine.backend.gdx

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.pashkd.krender.engine.api.EngineRuntime
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.backend.gdx.ui.runtime.RuntimeUiActorFactoryProvider
import com.pashkd.krender.engine.scene.EditorToolLauncher
import com.pashkd.krender.engine.scene.RuntimeWindowLauncher
import com.pashkd.krender.engine.scene.UnsupportedEditorToolLauncher
import com.pashkd.krender.engine.scene.UnsupportedRuntimeWindowLauncher

/**
 * LibGDX application entry point that bootstraps the engine runtime.
 */
open class GdxEngineApplication(
    private val initialScene: () -> Scene,
    private val runtimeWindowLauncherFactory: (Logger) -> RuntimeWindowLauncher = { UnsupportedRuntimeWindowLauncher },
    private val editorToolLauncherFactory: (Logger) -> EditorToolLauncher = { UnsupportedEditorToolLauncher },
    private val runtimeUiActorFactoryProvider: RuntimeUiActorFactoryProvider = RuntimeUiActorFactoryProvider.Empty,
    private val runtimeUiDefaultSkinPath: String = "ui/skins/default/uiskin.json",
) : ApplicationAdapter() {
    private lateinit var backend: LibGdxBackend
    private lateinit var runtime: EngineRuntime

    /** Creates the backend and starts the initial scene. */
    override fun create() {
        backend =
            LibGdxBackend(
                runtimeWindowLauncherFactory,
                editorToolLauncherFactory,
                runtimeUiActorFactoryProvider,
                runtimeUiDefaultSkinPath,
            )
        backend.logger.info(TAG) { "OpenGL context: ${Gdx.graphics.glVersion.debugVersionString}" }
        Gdx.input.isCursorCatched = false
        runtime = EngineRuntime(backend)
        runtime.start(initialScene())
    }

    /** Renders one engine frame using LibGDX delta time. */
    override fun render() {
        runtime.renderFrame(Gdx.graphics.deltaTime)
    }

    /** Forwards window resize events to the runtime. */
    override fun resize(
        width: Int,
        height: Int,
    ) {
        runtime.resize(width, height)
    }

    /** Disposes the runtime and all backend resources. */
    override fun dispose() {
        runtime.dispose()
    }

    companion object {
        private const val TAG = "GdxEngineApplication"
    }
}
