package com.pashkd.krender.engine.sceneplayer

import com.pashkd.krender.engine.api.AssetService
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.assets.environment.DefaultEnvironmentService
import com.pashkd.krender.engine.assets.environment.Environment
import com.pashkd.krender.engine.scene.RuntimeSceneValidator
import com.pashkd.krender.engine.scene.SceneDependencyCollector
import com.pashkd.krender.engine.scene.SceneDescriptor
import com.pashkd.krender.engine.scene.SceneSerializer

/**
 * Runtime-only scene loaded from a `.krscene` descriptor.
 */
class ScenePlayerScene(
    private val scenePath: String,
) : Scene("scene_player") {
    private var descriptorCache: SceneDescriptor? = null
    private var environmentCache: Environment? = null

    override fun scheduleAssets(assets: AssetService) {
        val descriptor = loadSceneDescriptor()
        val environment = resolveEnvironment(descriptor)
        descriptorCache = descriptor
        environmentCache = environment

        val dependencyGraph = SceneDependencyCollector(engine.sceneFiles).collect(descriptor)
        engine.logger.info(TAG) {
            "ScenePlayer scheduleAssets scene='$scenePath' dependencies=${
                dependencyGraph.dependencies.joinToString { dependency ->
                    "${dependency.kind}:${dependency.path}:${dependency.requirement}"
                }
            }"
        }
        dependencyGraph.schedulableAssets.forEach(assets::queue)
    }

    override fun show() {
        val descriptor = descriptorCache ?: loadSceneDescriptor().also { descriptorCache = it }
        val environment = environmentCache ?: resolveEnvironment(descriptor).also { environmentCache = it }

        engine.logger.info(TAG) {
            "ScenePlayer show scene='$scenePath' id='${descriptor.id}' name='${descriptor.name}' entities=${descriptor.entities.size} " +
                "activeCameraEntityId=${descriptor.settings.activeCameraEntityId ?: "<none>"} " +
                "activeTerrainEntityId=${descriptor.settings.activeTerrainEntityId ?: "<none>"} " +
                "environment='${descriptor.settings.environment.environmentAssetPath ?: "<none>"}'"
        }

        val result =
            ScenePlayerBuilder(engine).build(
                world = world,
                request =
                    ScenePlayerBuildRequest(
                        scenePath = scenePath,
                        descriptor = descriptor,
                        environment = environment,
                    ),
            )
        engine.logger.info(TAG) {
            "ScenePlayer built scene='$scenePath' activeCameraEntityId=${result.activeCameraEntityId} terrainPrepared=${result.terrainPrepared} " +
                "environmentEnabled=${result.environmentEnabled} validationErrors=${result.validationReport.errors.size} validationWarnings=${result.validationReport.warnings.size}"
        }
    }

    private fun loadSceneDescriptor(): SceneDescriptor {
        val normalizedPath = scenePath.trim().replace('\\', '/')
        require(normalizedPath.isNotBlank()) { "Runtime scene path must not be blank." }
        engine.logger.info(TAG) { "Loading runtime scene path='$normalizedPath'" }
        val text = engine.sceneFiles.readText(normalizedPath)
        return SceneSerializer.decode(text)
    }

    private fun resolveEnvironment(descriptor: SceneDescriptor): Environment? {
        val environmentPath = RuntimeSceneValidator.environmentAssetPath(descriptor) ?: return null
        return runCatching {
            DefaultEnvironmentService(engine.sceneFiles).load(environmentPath)
        }.getOrElse { error ->
            engine.logger.warn(TAG, error) {
                "ScenePlayer optional environment '$environmentPath' could not be loaded: ${error.message}"
            }
            null
        }
    }

    companion object {
        private const val TAG = "ScenePlayerScene"
    }
}
