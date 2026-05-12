package com.pashkd.krender.engine.scene

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.material.TerrainMaterialLibrary

class RuntimeTerrainMaterialLibraryService(
    private val sceneFiles: SceneFileService,
    private val logger: Logger,
) {
    fun loadRequired(path: String): TerrainMaterialLibrary {
        val normalizedPath = path.trim().replace('\\', '/')
        require(normalizedPath.isNotBlank()) { "Runtime terrain material library path must not be blank." }
        return TerrainMaterialLibrary(logger = logger, files = sceneFiles).also { library ->
            library.load(normalizedPath)
            logger.info(TAG) {
                "Runtime terrain material library loaded path='$normalizedPath' materials=${library.all().size}"
            }
        }
    }

    companion object {
        private const val TAG = "RuntimeTerrainMaterialLibrary"
    }
}
