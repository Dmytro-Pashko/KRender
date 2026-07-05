package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.AssetPack
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.assets.importing.FileDialogService
import com.pashkd.krender.engine.assets.importing.NoOpFileDialogService
import com.pashkd.krender.engine.assets.environment.DefaultEnvironmentService
import com.pashkd.krender.engine.scene.SceneConfig
import com.pashkd.krender.engine.scene.SceneConfigPresets
import com.pashkd.krender.engine.tools.environmenteditor.preview.EnvironmentPreviewController
import com.pashkd.krender.engine.tools.environmenteditor.preview.EnvironmentPreviewSceneAssembler
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigLoader
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker

/**
 * Lifecycle coordinator for editing and previewing one `.environment.json` manifest.
 *
 * The scene owns service composition only. UI construction, file operations, and
 * preview entity/render setup are delegated to focused collaborators.
 */
class EnvironmentEditorScene(
    val environmentPath: String,
    private val fileDialogService: FileDialogService = NoOpFileDialogService,
) : Scene("environment_editor") {
    private lateinit var previewController: EnvironmentPreviewController

    override val requiredAssets: List<AssetPack> =
        listOf(
            object : AssetPack {
                override val assets = listOf(AssetRef.model(EnvironmentEditorConfig.defaultPreviewModel.assetPath))
            },
        )

    override val config: SceneConfig = SceneConfigPresets.EnvironmentEditor

    override fun show() {
        engine.logger.info(TAG) { "Environment Editor opened path='$environmentPath'" }
        val state = EnvironmentEditorState(environmentPath)
        val environmentService = DefaultEnvironmentService(engine.sceneFiles)
        previewController = EnvironmentPreviewController(engine.sceneFiles)
        val resourcePreviewController = EnvironmentResourcePreviewController(state, engine)
        val selectedResourcePreviewController = EnvironmentSelectedResourcePreviewController(state)
        val skyboxImportController =
            SkyboxAtlasImportController(
                state,
                engine.assetRegistry.baseDir(),
                engine.logger,
            ) { updatedEnvironment ->
                state.validation = environmentService.validate(updatedEnvironment)
                val availability = previewController.availability(updatedEnvironment)
                engine.logger.info(TAG) {
                    "Skybox import applied to editor state manifest='${updatedEnvironment.manifestPath}' " +
                        "validation=${state.validation?.status} cacheRevision=${state.environmentCacheRevision} " +
                        "hasSkybox=${availability.hasSkybox} hasIrradiance=${availability.hasIrradiance} " +
                        "hasRadiance=${availability.hasRadiance} hasBrdfLut=${availability.hasBrdfLut} " +
                        "showSkybox=${availability.effectiveShowSkybox}"
                }
            }
        val layoutTracker = loadLayout()
        val controller = EnvironmentEditorController(state, engine, environmentService, layoutTracker)

        controller.reload()
        world.systems.add(EnvironmentEditorStateLoggingSystem(state, engine.logger))
        EnvironmentPreviewSceneAssembler(world, state, previewController).install()
        world.systems.add(
            EnvironmentEditorUiFactory(
                state,
                controller,
                previewController,
                resourcePreviewController,
                selectedResourcePreviewController,
                skyboxImportController,
                environmentService,
                layoutTracker,
                engine,
                fileDialogService,
            ).create(),
        )
    }

    private fun loadLayout(): ImGuiLayoutRuntimeTracker {
        val layout =
            ImGuiLayoutConfigLoader(
                assetPath = EnvironmentEditorUiLayoutDefaults.assetPath,
                fallback = EnvironmentEditorUiLayoutDefaults.config,
            ).load(engine.logger, engine.sceneFiles)
        return ImGuiLayoutRuntimeTracker(layout)
    }

    companion object {
        private const val TAG = "EnvironmentEditorScene"
    }
}
