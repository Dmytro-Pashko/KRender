package com.pashkd.krender.engine.assets.environment

import kotlinx.serialization.Serializable

/**
 * Describes one source variant of an environment (e.g. a 2K or 4K EXR file).
 */
@Serializable
data class EnvironmentSourceVariant(
    val id: String,
    val path: String,
    val format: EnvironmentSourceFormat,
    val role: SourceVariantRole = SourceVariantRole.Source,
    val isDefault: Boolean = false,
    val resolution: String? = null,
    val colorSpace: String? = null,
    val dynamicRange: String? = null,
)

/**
 * Source image format.
 */
@Serializable
enum class EnvironmentSourceFormat {
    EXR,
    HDR,
}

/**
 * Role of a source variant within the environment.
 */
@Serializable
enum class SourceVariantRole {
    Source,
    Preview,
    BakeInput,
    RuntimeFallback,
}
