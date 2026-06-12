package com.pashkd.krender.game

import com.pashkd.krender.engine.api.AssetService
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.scene.*

/**
 * Runtime-only scene loaded from a `.krscene` descriptor.
 */
class RuntimeScene(
    private val scenePath: String,
) : Scene("runtime_scene") {
    private var descriptorCache: SceneDescriptor? = null
    private var skyboxCache: SkyboxAssetDescriptor? = null

    override fun scheduleAssets(assets: AssetService) {
        val descriptor = loadSceneDescriptor()
        val skybox = resolveSkybox(descriptor)
        descriptorCache = descriptor
        skyboxCache = skybox

        val dependencyGraph = SceneDependencyCollector(engine.sceneFiles).collect(descriptor, skybox)
        engine.logger.info(TAG) {
            "RuntimeScene scheduleAssets scene='$scenePath' dependencies=${dependencyGraph.dependencies.joinToString { dependency -> "${dependency.kind}:${dependency.path}:${dependency.requirement}" }}"
        }
        dependencyGraph.schedulableAssets.forEach(assets::queue)
    }

    override fun show() {
        val descriptor = descriptorCache ?: loadSceneDescriptor().also { descriptorCache = it }
        val skybox = skyboxCache ?: resolveSkybox(descriptor).also { skyboxCache = it }

        engine.logger.info(TAG) {
            "RuntimeScene show scene='$scenePath' id='${descriptor.id}' name='${descriptor.name}' entities=${descriptor.entities.size} " +
                "activeCameraEntityId=${descriptor.settings.activeCameraEntityId ?: "<none>"} " +
                "activeTerrainEntityId=${descriptor.settings.activeTerrainEntityId ?: "<none>"} " +
                "skybox='${descriptor.settings.environment.skyboxAssetPath ?: "<none>"}'"
        }

        val result = RuntimeSceneBuilder(engine).build(
            world = world,
            request = RuntimeSceneBuildRequest(
                scenePath = scenePath,
                descriptor = descriptor,
                skybox = skybox,
            ),
        )
        engine.logger.info(TAG) {
            "RuntimeScene built scene='$scenePath' activeCameraEntityId=${result.activeCameraEntityId} terrainPrepared=${result.terrainPrepared} " +
                "skyboxEnabled=${result.skyboxEnabled} validationErrors=${result.validationReport.errors.size} validationWarnings=${result.validationReport.warnings.size}"
        }
    }

    private fun loadSceneDescriptor(): SceneDescriptor {
        val normalizedPath = scenePath.trim().replace('\\', '/')
        require(normalizedPath.isNotBlank()) { "Runtime scene path must not be blank." }
        engine.logger.info(TAG) { "Loading runtime scene path='$normalizedPath'" }
        val text = engine.sceneFiles.readText(normalizedPath)
        return SceneSerializer.decode(text)
    }

    private fun resolveSkybox(descriptor: SceneDescriptor): SkyboxAssetDescriptor? {
        val skyboxPath = RuntimeSceneValidator.skyboxPath(descriptor) ?: return null
        return runCatching {
            SkyboxAssetService(engine.sceneFiles, engine.logger).loadRequired(skyboxPath)
        }.getOrElse { error ->
            engine.logger.warn(TAG, error) {
                "Runtime scene optional skybox '$skyboxPath' could not be loaded: ${error.message}"
            }
            null
        }
    }

    companion object {
        private const val TAG = "RuntimeScene"
    }
}
