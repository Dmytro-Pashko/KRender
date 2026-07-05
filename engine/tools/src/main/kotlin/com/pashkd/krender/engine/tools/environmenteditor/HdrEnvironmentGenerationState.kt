package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.assets.environment.BrdfLutGenerationConfig
import com.pashkd.krender.engine.assets.environment.EnvironmentIblGenerationConfig
import com.pashkd.krender.engine.assets.environment.EnvironmentIblOutputFormat
import com.pashkd.krender.engine.assets.environment.EnvironmentIblOverwritePolicy
import com.pashkd.krender.engine.assets.environment.EnvironmentRoughnessDistribution
import com.pashkd.krender.engine.assets.environment.EnvironmentToneMapping
import com.pashkd.krender.engine.assets.environment.IrradianceGenerationConfig
import com.pashkd.krender.engine.assets.environment.RadianceGenerationConfig
import com.pashkd.krender.engine.assets.environment.SkyboxGenerationConfig

enum class HdrEnvironmentGenerationDialog(
    val title: String,
) {
    Skybox("Generate Skybox From HDR/EXR"),
    Irradiance("Generate Irradiance"),
    Radiance("Generate Radiance"),
    BrdfLut("Generate BRDF LUT"),
    AllIbl("Generate All IBL"),
}

class HdrEnvironmentGenerationState {
    var openDialog: HdrEnvironmentGenerationDialog? = null
    var statusMessage: String? = null
    var skyboxRequest = HdrSkyboxGenerationRequest()
    var irradianceRequest = HdrIrradianceGenerationRequest()
    var radianceRequest = HdrRadianceGenerationRequest()
    var brdfLutRequest = HdrBrdfLutGenerationRequest()
    var allIblRequest = HdrAllIblGenerationRequest()
}

data class HdrSkyboxGenerationRequest(
    var sourceHdrPath: String = "",
    var outputDirectory: String = "skybox",
    var format: EnvironmentIblOutputFormat = EnvironmentIblOutputFormat.PNG,
    var overwritePolicy: EnvironmentIblOverwritePolicy = EnvironmentIblOverwritePolicy.Fail,
    var resolution: Int = 1024,
    var exposure: Float = 1f,
    var toneMapping: EnvironmentToneMapping = EnvironmentToneMapping.ACES,
    var rememberSource: Boolean = false,
) {
    fun toConfig(manifestPath: String): EnvironmentIblGenerationConfig =
        EnvironmentIblGenerationConfig(
            environmentManifestPath = manifestPath,
            sourceHdrPath = sourceHdrPath,
            outputDirectory = outputDirectory,
            format = format,
            overwritePolicy = overwritePolicy,
            skybox =
                SkyboxGenerationConfig(
                    enabled = true,
                    resolution = resolution,
                    exposure = exposure,
                    toneMapping = toneMapping,
                ),
            irradiance = IrradianceGenerationConfig(enabled = false),
            radiance = RadianceGenerationConfig(enabled = false),
            brdfLut = BrdfLutGenerationConfig(enabled = false),
        )
}

data class HdrIrradianceGenerationRequest(
    var sourceHdrPath: String = "",
    var outputDirectory: String = "irradiance",
    var format: EnvironmentIblOutputFormat = EnvironmentIblOutputFormat.PNG,
    var overwritePolicy: EnvironmentIblOverwritePolicy = EnvironmentIblOverwritePolicy.Fail,
    var resolution: Int = 64,
    var sampleCount: Int = 512,
    var rememberSource: Boolean = false,
) {
    fun toConfig(manifestPath: String): EnvironmentIblGenerationConfig =
        EnvironmentIblGenerationConfig(
            environmentManifestPath = manifestPath,
            sourceHdrPath = sourceHdrPath,
            outputDirectory = outputDirectory,
            format = format,
            overwritePolicy = overwritePolicy,
            skybox = SkyboxGenerationConfig(enabled = false),
            irradiance =
                IrradianceGenerationConfig(
                    enabled = true,
                    resolution = resolution,
                    sampleCount = sampleCount,
                ),
            radiance = RadianceGenerationConfig(enabled = false),
            brdfLut = BrdfLutGenerationConfig(enabled = false),
        )
}

data class HdrRadianceGenerationRequest(
    var sourceHdrPath: String = "",
    var outputDirectory: String = "radiance",
    var format: EnvironmentIblOutputFormat = EnvironmentIblOutputFormat.PNG,
    var overwritePolicy: EnvironmentIblOverwritePolicy = EnvironmentIblOverwritePolicy.Fail,
    var baseResolution: Int = 256,
    var mipCount: Int = 8,
    var sampleCount: Int = 1024,
    var roughnessDistribution: EnvironmentRoughnessDistribution = EnvironmentRoughnessDistribution.Linear,
    var rememberSource: Boolean = false,
) {
    fun toConfig(manifestPath: String): EnvironmentIblGenerationConfig =
        EnvironmentIblGenerationConfig(
            environmentManifestPath = manifestPath,
            sourceHdrPath = sourceHdrPath,
            outputDirectory = outputDirectory,
            format = format,
            overwritePolicy = overwritePolicy,
            skybox = SkyboxGenerationConfig(enabled = false),
            irradiance = IrradianceGenerationConfig(enabled = false),
            radiance =
                RadianceGenerationConfig(
                    enabled = true,
                    baseResolution = baseResolution,
                    mipCount = mipCount,
                    sampleCount = sampleCount,
                    roughnessDistribution = roughnessDistribution,
                ),
            brdfLut = BrdfLutGenerationConfig(enabled = false),
        )
}

data class HdrBrdfLutGenerationRequest(
    var outputPath: String = "brdf_lut.png",
    var format: EnvironmentIblOutputFormat = EnvironmentIblOutputFormat.PNG,
    var overwritePolicy: EnvironmentIblOverwritePolicy = EnvironmentIblOverwritePolicy.Fail,
    var resolution: Int = 512,
    var sampleCount: Int = 1024,
)

data class HdrAllIblGenerationRequest(
    var sourceHdrPath: String = "",
    var outputRoot: String = "",
    var overwritePolicy: EnvironmentIblOverwritePolicy = EnvironmentIblOverwritePolicy.Fail,
    var rememberSource: Boolean = false,
    var skyboxEnabled: Boolean = true,
    var skyboxResolution: Int = 1024,
    var skyboxExposure: Float = 1f,
    var skyboxToneMapping: EnvironmentToneMapping = EnvironmentToneMapping.ACES,
    var irradianceEnabled: Boolean = true,
    var irradianceResolution: Int = 64,
    var irradianceSampleCount: Int = 512,
    var radianceEnabled: Boolean = true,
    var radianceBaseResolution: Int = 256,
    var radianceMipCount: Int = 8,
    var radianceSampleCount: Int = 1024,
    var brdfLutEnabled: Boolean = true,
    var brdfLutResolution: Int = 512,
    var brdfLutSampleCount: Int = 1024,
) {
    fun toConfig(manifestPath: String): EnvironmentIblGenerationConfig =
        EnvironmentIblGenerationConfig(
            environmentManifestPath = manifestPath,
            sourceHdrPath = sourceHdrPath,
            outputDirectory = outputRoot,
            overwritePolicy = overwritePolicy,
            skybox =
                SkyboxGenerationConfig(
                    enabled = skyboxEnabled,
                    resolution = skyboxResolution,
                    exposure = skyboxExposure,
                    toneMapping = skyboxToneMapping,
                ),
            irradiance =
                IrradianceGenerationConfig(
                    enabled = irradianceEnabled,
                    resolution = irradianceResolution,
                    sampleCount = irradianceSampleCount,
                ),
            radiance =
                RadianceGenerationConfig(
                    enabled = radianceEnabled,
                    baseResolution = radianceBaseResolution,
                    mipCount = radianceMipCount,
                    sampleCount = radianceSampleCount,
                ),
            brdfLut =
                BrdfLutGenerationConfig(
                    enabled = brdfLutEnabled,
                    resolution = brdfLutResolution,
                    sampleCount = brdfLutSampleCount,
                ),
        )
}
