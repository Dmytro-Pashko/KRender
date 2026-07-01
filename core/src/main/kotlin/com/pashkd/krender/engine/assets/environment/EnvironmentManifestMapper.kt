package com.pashkd.krender.engine.assets.environment

/**
 * Maps between the JSON-facing [EnvironmentManifestDto] and the domain [EnvironmentAsset].
 */
object EnvironmentManifestMapper {
    fun toDomain(
        dto: EnvironmentManifestDto,
        manifestPath: String,
    ): EnvironmentAsset =
        EnvironmentAsset(
            id = EnvironmentAssetId(dto.id),
            name = dto.name,
            manifestPath = manifestPath,
            version = dto.schemaVersion,
            type = parseEnvironmentType(dto.environmentType),
            description = dto.description,
            sources = dto.sources,
            generated = dto.generated,
            settings = dto.settings,
            generation = dto.generation,
        )

    fun toDto(asset: EnvironmentAsset): EnvironmentManifestDto =
        EnvironmentManifestDto(
            schema = ENVIRONMENT_SCHEMA,
            schemaVersion = asset.version,
            assetType = "Environment",
            environmentType = asset.type.name,
            id = asset.id.path,
            name = asset.name,
            description = asset.description,
            settings = asset.settings,
            sources = asset.sources,
            generated = asset.generated,
            generation = asset.generation,
        )

    private fun parseEnvironmentType(value: String): EnvironmentType =
        try {
            EnvironmentType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Unsupported environmentType '$value'.", e)
        }
}
