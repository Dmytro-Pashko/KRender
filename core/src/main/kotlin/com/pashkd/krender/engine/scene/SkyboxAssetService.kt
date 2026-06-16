package com.pashkd.krender.engine.scene

import com.pashkd.krender.engine.api.Logger

class SkyboxAssetService(
    private val sceneFiles: SceneFileService,
    private val logger: Logger,
) {
    fun loadRequired(path: String): SkyboxAssetDescriptor {
        val normalizedPath = path.trim().replace('\\', '/')
        require(normalizedPath.isNotBlank()) { "Runtime skybox path must not be blank." }
        return try {
            val descriptor = SkyboxAssetSerializer.decode(sceneFiles.readText(normalizedPath))
            logger.info(TAG) {
                "Runtime skybox descriptor loaded path='$normalizedPath' texture='${descriptor.texturePath}'"
            }
            descriptor
        } catch (error: Exception) {
            throw IllegalStateException("Failed to load runtime skybox '$normalizedPath': ${error.message}", error)
        }
    }

    companion object {
        private const val TAG = "SkyboxAssetService"
    }
}
