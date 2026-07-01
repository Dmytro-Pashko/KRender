package com.pashkd.krender.engine.assets.environment

import kotlinx.serialization.Serializable

/**
 * Serializable DTO matching the `.environment.json` manifest schema.
 *
 * This is the JSON-facing shape. Conversion to/from the domain [EnvironmentAsset]
 * is handled by [EnvironmentManifestMapper].
 */
@Serializable
data class EnvironmentManifestDto(
    val schema: String = ENVIRONMENT_SCHEMA,
    val schemaVersion: Int = ENVIRONMENT_SCHEMA_VERSION,
    val assetType: String = "Environment",
    val environmentType: String = "HdrIbl",
    val id: String,
    val name: String,
    val description: String? = null,
    val settings: EnvironmentSettings = EnvironmentSettings(),
    val sources: List<EnvironmentSourceVariant> = emptyList(),
    val generated: EnvironmentGeneratedResources = EnvironmentGeneratedResources(),
    val generation: EnvironmentGenerationSettings? = null,
)

const val ENVIRONMENT_SCHEMA = "krender.environment"
const val ENVIRONMENT_SCHEMA_VERSION = 1
