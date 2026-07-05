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

/**
 * Applies environment-generation results back into the in-memory [Environment] manifest model.
 *
 * This class deliberately keeps manifest shaping rules out of ImGui panels and generation services:
 *
 * - UI code should decide *when* generation runs, not how manifest fields are written.
 * - generator code should produce files and relative paths, not know the full Environment schema.
 *
 * The updater also centralizes the optional "remember source" behavior so HDR/EXR inputs stay
 * editor-owned metadata instead of becoming mandatory runtime dependencies by accident.
 */
class EnvironmentGenerationManifestUpdater {
    /**
     * Writes generated skybox face references into `environment.skybox`.
     *
     * The output is normalized to the runtime `SixFaces` layout expected by the current renderer.
     */
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

    /**
     * Updates the manifest with the conventional irradiance cubemap face-pattern reference.
     *
     * The generator is expected to have written six separate files under `irradiance/`; the
     * manifest stores the face pattern rather than six duplicated explicit entries.
     */
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

    /**
     * Updates the manifest with the conventional radiance mip-chain description.
     *
     * Roughness values are distributed monotonically from `0` to `1` across the requested mip
     * count so runtime sampling can treat lower mips as blurrier reflections.
     */
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
                                // The manifest stores a path pattern for each mip level; runtime
                                // face resolution happens later by replacing the `{face}` token.
                                path = "radiance/mip_$level/{face}.png",
                            )
                        },
                ),
        )

    /**
     * Writes the canonical BRDF LUT reference.
     */
    fun applyBrdfLut(environment: Environment): Environment =
        environment.copy(brdfLut = TextureResourceRef("brdf_lut.png"))

    /**
     * Applies all enabled generation outputs in one pass.
     *
     * This is intentionally selective:
     *
     * - skybox is only written when real generated face paths exist;
     * - irradiance/radiance are written from config shape when their stages are enabled;
     * - BRDF LUT is only written when the stage is enabled and a concrete result path exists.
     */
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

    /**
     * Optionally stores the HDR/EXR input as authoring metadata in `environment.sources`.
     *
     * This method does not make the source mandatory. If the caller disables `rememberSource`, the
     * runtime resource references remain fully self-contained.
     */
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
        // Replace any previous entry that points to the same logical source so repeated generation
        // does not accumulate duplicate authoring-source rows.
        val remaining = environment.sources.filterNot { it.id == sourceId || it.path == updatedSource.path }
        return environment.copy(sources = remaining + updatedSource)
    }

    /**
     * Infers the Environment source format from a file extension.
     */
    private fun sourceFormatFor(path: String): EnvironmentSourceFormat? =
        when (path.substringAfterLast('.', "").lowercase()) {
            "exr" -> EnvironmentSourceFormat.EXR
            "hdr" -> EnvironmentSourceFormat.HDR
            else -> null
        }

    /**
     * Rebuilds a face map in canonical cubemap-face order.
     *
     * Stable ordering keeps serialized manifests predictable and easier to review in diffs.
     */
    private fun linkedMapOfFaces(faces: Map<String, String>): Map<String, String> =
        linkedMapOf<String, String>().also { ordered ->
            EnvironmentCubemapFace.ordered.forEach { face ->
                faces[face.id]?.let { ordered[face.id] = it }
            }
        }
}
