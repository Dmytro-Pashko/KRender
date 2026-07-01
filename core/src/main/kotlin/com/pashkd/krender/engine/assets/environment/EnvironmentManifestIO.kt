package com.pashkd.krender.engine.assets.environment

import com.pashkd.krender.engine.scene.SceneFileService

/**
 * Loads and saves `.environment.json` manifests through [SceneFileService].
 *
 * This keeps IO platform-neutral — the caller supplies the file service
 * that knows how to read/write asset-relative paths.
 */
object EnvironmentManifestIO {
    /**
     * Loads an [EnvironmentAsset] from the given manifest [path].
     *
     * @throws IllegalArgumentException if the file does not exist or contains invalid JSON.
     */
    fun load(
        path: String,
        fileService: SceneFileService,
    ): EnvironmentAsset {
        require(fileService.exists(path)) { "Environment manifest not found: $path" }
        val text = fileService.readText(path)
        val dto = EnvironmentManifestCodec.decode(text)
        validateSchema(dto, path)
        return EnvironmentManifestMapper.toDomain(dto, path)
    }

    /**
     * Saves an [EnvironmentAsset] back to its manifest path.
     */
    fun save(
        asset: EnvironmentAsset,
        fileService: SceneFileService,
    ) {
        val dto = EnvironmentManifestMapper.toDto(asset)
        val text = EnvironmentManifestCodec.encode(dto)
        fileService.ensureDirectories(asset.manifestPath)
        fileService.writeText(asset.manifestPath, text)
    }

    private fun validateSchema(
        dto: EnvironmentManifestDto,
        path: String,
    ) {
        require(dto.schema == ENVIRONMENT_SCHEMA) {
            "Unsupported environment schema '${dto.schema}' in '$path'. Expected '$ENVIRONMENT_SCHEMA'."
        }
        require(dto.schemaVersion >= ENVIRONMENT_SCHEMA_VERSION) {
            "Environment manifest schema version ${dto.schemaVersion} in '$path' is below minimum $ENVIRONMENT_SCHEMA_VERSION."
        }
    }
}
