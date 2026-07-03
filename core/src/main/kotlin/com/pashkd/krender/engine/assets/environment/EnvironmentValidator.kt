package com.pashkd.krender.engine.assets.environment

import com.pashkd.krender.engine.scene.SceneFileService

/**
 * Validates an [EnvironmentAsset] and returns a structured [EnvironmentValidationReport].
 *
 * Validation checks cover manifest completeness, source availability, and generated resource
 * presence. File existence is checked through [SceneFileService] so the validator stays
 * platform-neutral.
 */
object EnvironmentValidator {
    fun validate(
        asset: EnvironmentAsset,
        fileService: SceneFileService,
    ): EnvironmentValidationReport {
        val issues = mutableListOf<EnvironmentIssue>()

        validateManifest(asset, issues)
        validateSources(asset, fileService, issues)
        validateGenerated(asset, fileService, issues)
        validateSettings(asset, issues)

        val status =
            when {
                issues.any { it.severity == IssueSeverity.Error } -> ValidationStatus.Error
                issues.any { it.severity == IssueSeverity.Warning } -> ValidationStatus.Warning
                else -> ValidationStatus.Valid
            }
        return EnvironmentValidationReport(status, issues)
    }

    private fun validateManifest(
        asset: EnvironmentAsset,
        issues: MutableList<EnvironmentIssue>,
    ) {
        if (asset.name.isBlank()) {
            issues += error(Codes.MISSING_NAME, "Environment name is empty.")
        }
        if (asset.id.path.isBlank()) {
            issues += error(Codes.MISSING_ID, "Environment id is empty.")
        }
    }

    private fun validateSources(
        asset: EnvironmentAsset,
        fileService: SceneFileService,
        issues: MutableList<EnvironmentIssue>,
    ) {
        if (asset.sources.isEmpty()) {
            issues += error(Codes.NO_SOURCES, "No source variants defined.")
            return
        }
        val hasDefault = asset.sources.any { it.isDefault }
        if (!hasDefault) {
            issues += warning(Codes.NO_DEFAULT_SOURCE, "No source variant is marked as default.")
        }
        for (source in asset.sources) {
            val resolvedPath = EnvironmentPathResolver.resolvePath(asset.manifestPath, source.path)
            if (!fileService.exists(resolvedPath)) {
                issues +=
                    warning(
                        Codes.SOURCE_FILE_MISSING,
                        "Source '${source.id}' file not found: ${source.path}",
                        source.path,
                    )
            }
        }
    }

    private fun validateGenerated(
        asset: EnvironmentAsset,
        fileService: SceneFileService,
        issues: MutableList<EnvironmentIssue>,
    ) {
        validateSkybox(asset.skybox, asset.manifestPath, fileService, issues)
        validateIrradiance(asset.irradiance, asset.manifestPath, fileService, issues)
        validateRadiance(asset.radiance, asset.manifestPath, fileService, issues)
        validateBrdfLut(asset.brdfLut, asset.manifestPath, fileService, issues)
    }

    private fun validateSkybox(
        skybox: SkyboxResourceSet?,
        manifestPath: String,
        fileService: SceneFileService,
        issues: MutableList<EnvironmentIssue>,
    ) {
        if (skybox == null) {
            issues += warning(Codes.MISSING_SKYBOX, "No skybox resource set defined.")
            return
        }
        if (skybox.faces.isEmpty()) {
            issues += warning(Codes.SKYBOX_NO_FACES, "Skybox has no face paths defined.")
            return
        }
        for ((face, path) in skybox.faces) {
            val resolved = EnvironmentPathResolver.resolvePath(manifestPath, path)
            if (!fileService.exists(resolved)) {
                issues +=
                    warning(
                        Codes.SKYBOX_FACE_MISSING,
                        "Skybox face '$face' not found: $path",
                        path,
                    )
            }
        }
    }

    private fun validateIrradiance(
        irradiance: CubemapResource?,
        manifestPath: String,
        fileService: SceneFileService,
        issues: MutableList<EnvironmentIssue>,
    ) {
        if (irradiance == null) {
            issues += warning(Codes.MISSING_IRRADIANCE, "No irradiance cubemap defined.")
            return
        }
        val resolved = EnvironmentPathResolver.resolvePath(manifestPath, irradiance.path)
        if (!fileService.exists(resolved)) {
            issues +=
                warning(
                    Codes.IRRADIANCE_FILE_MISSING,
                    "Irradiance file not found: ${irradiance.path}",
                    irradiance.path,
                )
        }
    }

    private fun validateRadiance(
        radiance: RadianceMipChain?,
        manifestPath: String,
        fileService: SceneFileService,
        issues: MutableList<EnvironmentIssue>,
    ) {
        if (radiance == null) {
            issues += warning(Codes.MISSING_RADIANCE, "No radiance mip chain defined.")
            return
        }
        if (radiance.mips.isEmpty()) {
            issues += warning(Codes.RADIANCE_NO_MIPS, "Radiance mip chain has no mip entries.")
            return
        }
        for (mip in radiance.mips) {
            val resolved = EnvironmentPathResolver.resolvePath(manifestPath, mip.path)
            if (!fileService.exists(resolved)) {
                issues +=
                    warning(
                        Codes.RADIANCE_MIP_MISSING,
                        "Radiance mip ${mip.level} not found: ${mip.path}",
                        mip.path,
                    )
            }
        }
    }

    private fun validateBrdfLut(
        brdfLut: TextureResourceRef?,
        manifestPath: String,
        fileService: SceneFileService,
        issues: MutableList<EnvironmentIssue>,
    ) {
        if (brdfLut == null) {
            issues += warning(Codes.MISSING_BRDF_LUT, "No BRDF LUT reference defined.")
            return
        }
        val resolved = EnvironmentPathResolver.resolvePath(manifestPath, brdfLut.path)
        if (!fileService.exists(resolved)) {
            issues +=
                warning(
                    Codes.BRDF_LUT_MISSING,
                    "BRDF LUT file not found: ${brdfLut.path}",
                    brdfLut.path,
                )
        }
    }

    private fun validateSettings(
        asset: EnvironmentAsset,
        issues: MutableList<EnvironmentIssue>,
    ) {
        val s = asset.settings
        if (s.exposure <= 0f) {
            issues += warning(Codes.INVALID_EXPOSURE, "Exposure should be positive, got ${s.exposure}.")
        }
        if (s.skyboxIntensity !in 0f..1f) {
            issues += warning(Codes.INVALID_INTENSITY, "Skybox intensity must be between 0 and 1, got ${s.skyboxIntensity}.")
        }
        if (s.diffuseIntensity < 0f) {
            issues += warning(Codes.INVALID_INTENSITY, "Diffuse intensity is negative: ${s.diffuseIntensity}.")
        }
        if (s.specularIntensity !in 0f..1f) {
            issues += warning(Codes.INVALID_INTENSITY, "Specular intensity must be between 0 and 1, got ${s.specularIntensity}.")
        }
    }

    private fun error(
        code: String,
        message: String,
        relatedPath: String? = null,
    ) = EnvironmentIssue(IssueSeverity.Error, code, message, relatedPath)

    private fun warning(
        code: String,
        message: String,
        relatedPath: String? = null,
    ) = EnvironmentIssue(IssueSeverity.Warning, code, message, relatedPath)

    /**
     * Issue codes used in validation reports.
     */
    object Codes {
        const val MISSING_NAME = "ENV_MISSING_NAME"
        const val MISSING_ID = "ENV_MISSING_ID"
        const val NO_SOURCES = "ENV_NO_SOURCES"
        const val NO_DEFAULT_SOURCE = "ENV_NO_DEFAULT_SOURCE"
        const val SOURCE_FILE_MISSING = "ENV_SOURCE_FILE_MISSING"
        const val MISSING_SKYBOX = "ENV_MISSING_SKYBOX"
        const val SKYBOX_NO_FACES = "ENV_SKYBOX_NO_FACES"
        const val SKYBOX_FACE_MISSING = "ENV_SKYBOX_FACE_MISSING"
        const val MISSING_IRRADIANCE = "ENV_MISSING_IRRADIANCE"
        const val IRRADIANCE_FILE_MISSING = "ENV_IRRADIANCE_FILE_MISSING"
        const val MISSING_RADIANCE = "ENV_MISSING_RADIANCE"
        const val RADIANCE_NO_MIPS = "ENV_RADIANCE_NO_MIPS"
        const val RADIANCE_MIP_MISSING = "ENV_RADIANCE_MIP_MISSING"
        const val MISSING_BRDF_LUT = "ENV_MISSING_BRDF_LUT"
        const val BRDF_LUT_MISSING = "ENV_BRDF_LUT_MISSING"
        const val INVALID_EXPOSURE = "ENV_INVALID_EXPOSURE"
        const val INVALID_INTENSITY = "ENV_INVALID_INTENSITY"
    }
}
