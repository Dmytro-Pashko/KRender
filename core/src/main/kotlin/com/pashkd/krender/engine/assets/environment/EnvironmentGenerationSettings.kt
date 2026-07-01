package com.pashkd.krender.engine.assets.environment

import kotlinx.serialization.Serializable

/**
 * Parameters that control how generated IBL resources are produced from the source.
 */
@Serializable
data class EnvironmentGenerationSettings(
    val sourceVariantId: String,
    val generator: String = "KRenderIBLGenerator",
    val generatorVersion: String = "1",
    val generatedAt: String? = null,
    val skyboxResolution: Int = 1024,
    val irradianceResolution: Int = 64,
    val radianceResolution: Int = 256,
    val radianceMipCount: Int = 5,
    val outputFormat: String = "KTX",
)
