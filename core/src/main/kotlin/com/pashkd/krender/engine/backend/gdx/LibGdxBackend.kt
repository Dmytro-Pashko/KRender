package com.pashkd.krender.engine.backend.gdx

import com.badlogic.gdx.Gdx
import com.pashkd.krender.engine.api.*
import com.pashkd.krender.engine.assets.AssetImporterRegistry
import com.pashkd.krender.engine.assets.AssetRegistryService
import com.pashkd.krender.engine.assets.LocalAssetRegistryService
import com.pashkd.krender.engine.backend.gdx.scene.GdxSceneFileService
import com.pashkd.krender.engine.backend.gdx.ui.runtime.GdxRuntimeUiBackend
import com.pashkd.krender.engine.backend.gdx.ui.runtime.WoolboyRuntimeUiFactory
import com.pashkd.krender.engine.scene.*
import com.pashkd.krender.engine.terrain.TerrainMaterialTextureSamplerFactory
import com.pashkd.krender.engine.ui.editor.NoOpUiService
import com.pashkd.krender.engine.ui.editor.UiService
import com.pashkd.krender.engine.ui.runtime.RuntimeUiBackend
import com.pashkd.krender.engine.window.WindowService

/**
 * Concrete engine backend that wires LibGDX services into the core runtime.
 */
class LibGdxBackend(
    private val runtimeWindowLauncherFactory: (Logger) -> RuntimeWindowLauncher = { UnsupportedRuntimeWindowLauncher },
    private val editorToolLauncherFactory: (Logger) -> EditorToolLauncher = { UnsupportedEditorToolLauncher },
) : EngineBackend {
    override val runtimeStats: RuntimeStatsService = FrameRuntimeStatsService()
    override val logs: EngineLogService = EngineLogService(frameProvider = runtimeStats::frame).also { service ->
        service.addSink(GdxAppLogSink())
        runCatching { FileLogSink() }
            .onSuccess(service::addSink)
            .onFailure { error ->
                Gdx.app.error(TAG, "File logging disabled: ${error.message}", error)
            }
    }
    override val profiler: ProfilerService = FrameProfilerService()
    override val logger: Logger = logs
    override val input: GdxInputService = GdxInputService().also {
        Gdx.input.inputProcessor = it
    }
    override val runtimeUi: RuntimeUiBackend = GdxRuntimeUiBackend(
        logger = logger,
        input = input,
        screenFactoryProvider = { _, actionHandlerProvider ->
            listOf(WoolboyRuntimeUiFactory(logger, actionHandlerProvider))
        },
    )
    override val ui: UiService = if (Gdx.app.type == com.badlogic.gdx.Application.ApplicationType.Android) {
        NoOpUiService()
    } else {
        GdxImGuiService(input, runtimeStats)
    }
    override val assets: GdxAssetService = GdxAssetService(logger)
    override val assetRegistry: AssetRegistryService =
        LocalAssetRegistryService(logger, AssetImporterRegistry.withDefaults(logger))
    override val sceneFiles: SceneFileService = GdxSceneFileService()
    override val runtimeLauncher: RuntimeWindowLauncher = runtimeWindowLauncherFactory(logger)
    override val editorToolLauncher: EditorToolLauncher = editorToolLauncherFactory(logger)
    override val tasks: TaskService = GdxTaskService()
    override val terrainTextureSamplerFactory: TerrainMaterialTextureSamplerFactory =
        TerrainMaterialTextureSamplerFactory { GdxTerrainMaterialTextureSampler(logger) }
    override val window: WindowService = GdxWindowService(logger)
    override val renderer: Renderer = GdxRenderer3D(assets, logger)

    /** Requests application shutdown through the LibGDX app instance. */
    override fun requestExit() {
        Gdx.app.exit()
    }

    companion object {
        private const val TAG = "LibGdxBackend"
    }
}
