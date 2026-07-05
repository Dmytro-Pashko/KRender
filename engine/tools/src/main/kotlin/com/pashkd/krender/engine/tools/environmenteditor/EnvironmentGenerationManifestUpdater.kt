package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.assets.environment.CubemapResource
import com.pashkd.krender.engine.assets.environment.Environment
import com.pashkd.krender.engine.assets.environment.EnvironmentIblGenerationConfig
import com.pashkd.krender.engine.assets.environment.EnvironmentIblGenerationResult
import com.pashkd.krender.engine.assets.environment.EnvironmentSourceFormat
import com.pashkd.krender.engine.assets.environment.EnvironmentSourceVariant
import com.pashkd.krender.engine.assets.environment.RadianceMip
import com.pashkd.krender.engine.assets.environment.RadianceMipChain
import com.pashkd.krender.engine.assets.environment.SkyboxResourceSet
import com.pashkd.krender.engine.assets.environment.TextureResourceRef
import java.io.File

class EnvironmentGenerationManifestUpdater {
    fun applySkybox(
        environment: Environment,
        config: EnvironmentIblGenerationConfig,
        result: EnvironmentIblGenerationResult,
        rememberSource: Boolean,
    ): Environment =
        rememberSource(environment, config, rememberSource).copy(
            skybox =
                SkyboxResourceSet(
                    layout = "SixFaces",
                    resolution = config.skybox.resolution,
                    format = config.format.name,
                    faces = linkedMapOfFaces(result.skyboxFaces),
                ),
        )

    fun applyIrradiance(
        environment: Environment,
        config: EnvironmentIblGenerationConfig,
        rememberSource: Boolean,
    ): Environment =
        rememberSource(environment, config, rememberSource).copy(
            irradiance =
                CubemapResource(
                    path = "irradiance/{face}.png",
                    resolution = config.irradiance.resolution,
                    format = config.format.name,
                ),
        )

    fun applyRadiance(
        environment: Environment,
        config: EnvironmentIblGenerationConfig,
        rememberSource: Boolean,
    ): Environment =
        rememberSource(environment, config, rememberSource).copy(
            radiance =
                RadianceMipChain(
                    baseResolution = config.radiance.baseResolution,
                    mips =
                        (0 until config.radiance.mipCount).map { level ->
                            RadianceMip(
                                level = level,
                                roughness =
                                    level.toFloat() /
                                        (config.radiance.mipCount - 1).coerceAtLeast(1),
                                path = "radiance/mip_$level/{face}.png",
                            )
                        },
                ),
        )

    fun applyBrdfLut(environment: Environment): Environment =
        environment.copy(brdfLut = TextureResourceRef("brdf_lut.png"))

    fun applyAll(
        environment: Environment,
        config: EnvironmentIblGenerationConfig,
        result: EnvironmentIblGenerationResult,
        rememberSource: Boolean,
    ): Environment {
        var updated = rememberSource(environment, config, rememberSource)
        if (result.skyboxFaces.isNotEmpty()) {
            updated = applySkybox(updated, config, result, rememberSource = false)
        }
        if (config.irradiance.enabled) {
            updated = applyIrradiance(updated, config, rememberSource = false)
        }
        if (config.radiance.enabled) {
            updated = applyRadiance(updated, config, rememberSource = false)
        }
        if (config.brdfLut.enabled && result.brdfLutPath != null) {
            updated = applyBrdfLut(updated)
        }
        return updated
    }

    private fun rememberSource(
        environment: Environment,
        config: EnvironmentIblGenerationConfig,
        rememberSource: Boolean,
    ): Environment {
        if (!rememberSource || config.sourceHdrPath.isBlank()) return environment
        val sourceFormat = sourceFormatFor(config.sourceHdrPath) ?: return environment
        val sourceFile = File(config.sourceHdrPath)
        val sourceId = sourceFile.nameWithoutExtension.ifBlank { "hdr-source" }
        val hasDefault = environment.sources.any(EnvironmentSourceVariant::isDefault)
        val updatedSource =
            EnvironmentSourceVariant(
                id = sourceId,
                path = config.sourceHdrPath.replace('\\', '/'),
                format = sourceFormat,
                isDefault = !hasDefault,
                dynamicRange = "HDR",
                colorSpace = "Linear",
            )
        val remaining = environment.sources.filterNot { it.id == sourceId || it.path == updatedSource.path }
        return environment.copy(sources = remaining + updatedSource)
    }

    private fun sourceFormatFor(path: String): EnvironmentSourceFormat? =
        when (path.substringAfterLast('.', "").lowercase()) {
            "exr" -> EnvironmentSourceFormat.EXR
            "hdr" -> EnvironmentSourceFormat.HDR
            else -> null
        }

    private fun linkedMapOfFaces(faces: Map<String, String>): Map<String, String> =
        linkedMapOf<String, String>().also { ordered ->
            EnvironmentCubemapFace.ordered.forEach { face ->
                faces[face.id]?.let { ordered[face.id] = it }
            }
        }
}
