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
        if (asset.type != EnvironmentType.HdrIbl) {
            issues +=
                warning(
                    Codes.UNSUPPORTED_TYPE,
                    "Environment type '${asset.type}' is not yet supported. Only HdrIbl is available.",
                )
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
        val manifestDir = manifestDirectory(asset.manifestPath)
        for (source in asset.sources) {
            val resolvedPath = resolvePath(manifestDir, source.path)
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
        val gen = asset.generated
        val manifestDir = manifestDirectory(asset.manifestPath)

        validateSkybox(gen.skybox, manifestDir, fileService, issues)
        validateIrradiance(gen.irradiance, manifestDir, fileService, issues)
        validateRadiance(gen.radiance, manifestDir, fileService, issues)
        validateBrdfLut(gen.brdfLut, manifestDir, fileService, issues)
    }

    private fun validateSkybox(
        skybox: SkyboxResourceSet?,
        manifestDir: String,
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
            val resolved = resolvePath(manifestDir, path)
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
        manifestDir: String,
        fileService: SceneFileService,
        issues: MutableList<EnvironmentIssue>,
    ) {
        if (irradiance == null) {
            issues += warning(Codes.MISSING_IRRADIANCE, "No irradiance cubemap defined.")
            return
        }
        val resolved = resolvePath(manifestDir, irradiance.path)
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
        manifestDir: String,
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
            val resolved = resolvePath(manifestDir, mip.path)
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
        manifestDir: String,
        fileService: SceneFileService,
        issues: MutableList<EnvironmentIssue>,
    ) {
        if (brdfLut == null) {
            issues += warning(Codes.MISSING_BRDF_LUT, "No BRDF LUT reference defined.")
            return
        }
        val resolved = resolvePath(manifestDir, brdfLut.path)
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
        if (s.skyboxIntensity < 0f) {
            issues += warning(Codes.INVALID_INTENSITY, "Skybox intensity is negative: ${s.skyboxIntensity}.")
        }
        if (s.diffuseIntensity < 0f) {
            issues += warning(Codes.INVALID_INTENSITY, "Diffuse intensity is negative: ${s.diffuseIntensity}.")
        }
        if (s.specularIntensity < 0f) {
            issues += warning(Codes.INVALID_INTENSITY, "Specular intensity is negative: ${s.specularIntensity}.")
        }
    }

    private fun manifestDirectory(manifestPath: String): String {
        val normalized = manifestPath.replace('\\', '/')
        val lastSlash = normalized.lastIndexOf('/')
        return if (lastSlash >= 0) normalized.substring(0, lastSlash) else ""
    }

    private fun resolvePath(
        manifestDir: String,
        relativePath: String,
    ): String {
        if (manifestDir.isEmpty()) return relativePath.replace('\\', '/')
        return "$manifestDir/$relativePath".replace('\\', '/')
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
        const val UNSUPPORTED_TYPE = "ENV_UNSUPPORTED_TYPE"
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
