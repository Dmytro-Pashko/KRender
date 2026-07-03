package com.pashkd.krender.engine.assets.environment

import com.pashkd.krender.engine.scene.SceneFileService

/**
 * Loads and saves `.environment.json` files through [SceneFileService].
 *
 * This keeps IO platform-neutral while using the same [Environment] model for persistence and runtime.
 */
object EnvironmentLoader {
    /**
     * Loads an [Environment] from the given file [path].
     *
     * @throws IllegalArgumentException if the file does not exist or contains invalid JSON.
     */
    fun load(
        path: String,
        fileService: SceneFileService,
    ): Environment {
        require(fileService.exists(path)) { "Environment manifest not found: $path" }
        val text = fileService.readText(path)
        val environment = EnvironmentSerializer.decode(text).copy(manifestPath = path)
        validateSchema(environment, path)
        return environment
    }

    /**
     * Saves an [Environment] back to its manifest path.
     */
    fun save(
        environment: Environment,
        fileService: SceneFileService,
    ) {
        fileService.ensureDirectories(environment.manifestPath)
        fileService.writeText(environment.manifestPath, EnvironmentSerializer.encode(environment))
    }

    private fun validateSchema(
        environment: Environment,
        path: String,
    ) {
        require(environment.schema == ENVIRONMENT_SCHEMA) {
            "Unsupported environment schema '${environment.schema}' in '$path'. Expected '$ENVIRONMENT_SCHEMA'."
        }
        require(environment.schemaVersion >= ENVIRONMENT_SCHEMA_VERSION) {
            "Environment manifest schema version ${environment.schemaVersion} in '$path' is below minimum $ENVIRONMENT_SCHEMA_VERSION."
        }
    }
}
