package com.pashkd.krender.engine.assets.environment

/**
 * Backend-neutral configuration for generating runtime Environment IBL resources.
 *
 * Future implementations should emit separate face files for cubemap outputs:
 * skybox/<face>.png, irradiance/<face>.png, radiance/mip_<n>/<face>.png,
 * plus brdf_lut.png.
 */
data class EnvironmentIblGenerationConfig(
    val environmentManifestPath: String,
    val sourceHdrPath: String,
    val outputDirectory: String,
    val format: EnvironmentIblOutputFormat = EnvironmentIblOutputFormat.PNG,
    val overwritePolicy: EnvironmentIblOverwritePolicy = EnvironmentIblOverwritePolicy.Fail,
    val faceNaming: EnvironmentIblFaceNaming = EnvironmentIblFaceNaming.PosNegAxes,
    val skybox: SkyboxGenerationConfig = SkyboxGenerationConfig(),
    val irradiance: IrradianceGenerationConfig = IrradianceGenerationConfig(),
    val radiance: RadianceGenerationConfig = RadianceGenerationConfig(),
    val brdfLut: BrdfLutGenerationConfig = BrdfLutGenerationConfig(),
)

data class SkyboxGenerationConfig(
    val enabled: Boolean = true,
    val resolution: Int = 1024,
    val exposure: Float = 1f,
    val toneMapping: EnvironmentToneMapping = EnvironmentToneMapping.ACES,
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

/**
 * Tone-mapping operator applied when HDR data must be collapsed into an LDR output such as PNG.
 *
 * The generator samples source environment data in linear HDR space, optionally multiplies it by
 * exposure, and then uses one of these operators before converting the result to sRGB 8-bit output.
 *
 * - [None] keeps values unchanged and only clamps them during LDR conversion. This is useful for
 *   debugging or already tone-mapped inputs, but it can easily blow out highlights.
 * - [Reinhard] applies a simple photographic compression curve `x / (1 + x)`. It is cheap and
 *   stable, but tends to flatten bright highlights more aggressively.
 * - [ACES] applies an ACES-inspired filmic curve. It generally preserves highlight roll-off and
 *   contrast better for skyboxes authored from HDR/EXR sources, so it is the current default.
 */
enum class EnvironmentToneMapping {
    None,
    Reinhard,
    ACES,
}

data class EnvironmentIblGenerationResult(
    val skyboxFaces: Map<String, String> = emptyMap(),
    val irradianceFaces: Map<String, String> = emptyMap(),
    val radianceMipFaces: Map<Int, Map<String, String>> = emptyMap(),
    val brdfLutPath: String? = null,
)

interface EnvironmentIblGenerationService {
    fun generate(config: EnvironmentIblGenerationConfig): EnvironmentIblGenerationResult
}
