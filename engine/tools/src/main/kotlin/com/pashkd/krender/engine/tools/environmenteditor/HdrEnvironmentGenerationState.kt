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
    AllIbl("Generate IBL"),
}

class HdrEnvironmentGenerationState {
    var openDialog: HdrEnvironmentGenerationDialog? = null
    var statusMessage: String? = null
    var allIblRequest = HdrAllIblGenerationRequest()
}

data class HdrAllIblGenerationRequest(
    var sourceHdrPath: String = "",
    var outputRoot: String = "",
    var overwritePolicy: EnvironmentIblOverwritePolicy = EnvironmentIblOverwritePolicy.Fail,
    var skyboxEnabled: Boolean = true,
    var skyboxResolution: Int = 1024,
    var skyboxExposure: Float = 1f,
    var skyboxToneMapping: EnvironmentToneMapping = EnvironmentToneMapping.ACES,
    var irradianceEnabled: Boolean = true,
    var irradianceResolution: Int = 64,
    var irradianceSampleCount: Int = 512,
    var irradianceExposure: Float = 0.3183099f,
    var irradianceToneMapping: EnvironmentToneMapping = EnvironmentToneMapping.ACES,
    var radianceEnabled: Boolean = true,
    var radianceBaseResolution: Int = 256,
    var radianceMipCount: Int = 8,
    var radianceSampleCount: Int = 1024,
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
                    exposure = irradianceExposure,
                    toneMapping = irradianceToneMapping,
                ),
            radiance =
                RadianceGenerationConfig(
                    enabled = radianceEnabled,
                    baseResolution = radianceBaseResolution,
                    mipCount = radianceMipCount,
                    sampleCount = radianceSampleCount,
                ),
            brdfLut = BrdfLutGenerationConfig(enabled = false),
        )
}
