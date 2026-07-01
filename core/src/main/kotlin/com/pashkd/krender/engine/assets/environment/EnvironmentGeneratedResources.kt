package com.pashkd.krender.engine.assets.environment

import kotlinx.serialization.Serializable

/**
 * References to all generated IBL resources for an environment.
 */
@Serializable
data class EnvironmentGeneratedResources(
    val skybox: SkyboxResourceSet? = null,
    val irradiance: CubemapResource? = null,
    val radiance: RadianceMipChain? = null,
    val brdfLut: TextureResourceRef? = null,
)

/**
 * Skybox cubemap face set.
 */
@Serializable
data class SkyboxResourceSet(
    val layout: String = "SixFaces",
    val resolution: Int = 1024,
    val format: String = "KTX",
    val faces: Map<String, String> = emptyMap(),
)

/**
 * A single cubemap resource (e.g. irradiance).
 */
@Serializable
data class CubemapResource(
    val path: String,
    val resolution: Int = 64,
    val format: String = "KTX",
)

/**
 * Radiance pre-filtered mip chain for specular IBL.
 */
@Serializable
data class RadianceMipChain(
    val baseResolution: Int = 256,
    val mips: List<RadianceMip> = emptyList(),
)

/**
 * Single mip level in the radiance chain.
 */
@Serializable
data class RadianceMip(
    val level: Int,
    val roughness: Float = 0f,
    val path: String,
)

/**
 * Reference to a single texture resource (e.g. BRDF LUT).
 */
@Serializable
data class TextureResourceRef(
    val path: String,
    val shared: Boolean = false,
)
