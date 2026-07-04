package com.pashkd.krender.engine.assets.environment

/**
 * Backend-neutral configuration for generating runtime Environment IBL resources.
 *
 * Future implementations should emit separate face files for cubemap outputs:
 * irradiance/<face>.png and radiance/mip_<n>/<face>.png, plus brdf_lut.png.
 */
data class EnvironmentIblGenerationConfig(
    val sourceEnvironmentPath: String,
    val outputDirectory: String,
    val format: EnvironmentIblOutputFormat = EnvironmentIblOutputFormat.PNG,
    val overwritePolicy: EnvironmentIblOverwritePolicy = EnvironmentIblOverwritePolicy.Fail,
    val faceNaming: EnvironmentIblFaceNaming = EnvironmentIblFaceNaming.PosNegAxes,
    val irradiance: IrradianceGenerationConfig = IrradianceGenerationConfig(),
    val radiance: RadianceGenerationConfig = RadianceGenerationConfig(),
    val brdfLut: BrdfLutGenerationConfig = BrdfLutGenerationConfig(),
)

data class IrradianceGenerationConfig(
    val enabled: Boolean = true,
    val resolution: Int = 64,
    val sampleCount: Int = 512,
)

data class RadianceGenerationConfig(
    val enabled: Boolean = true,
    val baseResolution: Int = 256,
    val mipCount: Int = 10,
    val sampleCount: Int = 1024,
    val roughnessDistribution: EnvironmentRoughnessDistribution = EnvironmentRoughnessDistribution.Linear,
)

data class BrdfLutGenerationConfig(
    val enabled: Boolean = true,
    val resolution: Int = 512,
    val sampleCount: Int = 1024,
)

enum class EnvironmentIblOutputFormat {
    PNG,
}

enum class EnvironmentIblOverwritePolicy {
    Fail,
    Replace,
    SkipExisting,
}

enum class EnvironmentIblFaceNaming {
    PosNegAxes,
}

enum class EnvironmentRoughnessDistribution {
    Linear,
}

data class EnvironmentIblGenerationResult(
    val irradianceFaces: Map<String, String> = emptyMap(),
    val radianceMipFaces: Map<Int, Map<String, String>> = emptyMap(),
    val brdfLutPath: String? = null,
)

interface EnvironmentIblGenerationService {
    fun generate(config: EnvironmentIblGenerationConfig): EnvironmentIblGenerationResult
}
