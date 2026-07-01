package com.pashkd.krender.engine.backend.gdx

import com.badlogic.gdx.Files
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.assets.environment.ENVIRONMENT_SCHEMA
import com.pashkd.krender.engine.assets.environment.EnvironmentManifestCodec
import com.pashkd.krender.engine.assets.environment.EnvironmentManifestDto
import com.pashkd.krender.engine.assets.environment.EnvironmentSourceVariant
import com.pashkd.krender.engine.assets.hdr.HdrEnvironmentAssets
import com.pashkd.krender.engine.assets.hdr.HdrEnvironmentDefaults
import com.pashkd.krender.engine.assets.hdr.HdrEnvironmentManifest
import com.pashkd.krender.engine.assets.hdr.HdrEnvironmentManifestCodec
import com.pashkd.krender.engine.assets.hdr.HdrEnvironmentManifestLoader
import com.pashkd.krender.engine.backend.gdx.tools.hdr.SharedBrdfLutExporter

internal class GdxHdrEnvironmentResolver(
    private val logger: Logger,
) {
    fun resolve(presetNameOrPath: String = DEFAULT_ENVIRONMENT_PRESET): GdxResolvedHdrEnvironment? {
        val manifestPath = manifestPathFor(presetNameOrPath)
        val manifestFile = Gdx.files.internal(manifestPath)
        if (!manifestFile.exists()) {
            logger.warn(TAG) { "HDR environment manifest is missing: '$manifestPath'." }
            return null
        }
        return try {
            val manifestText = manifestFile.readString("UTF-8")
            val resolved =
                when (detectSchema(manifestText)) {
                    ENVIRONMENT_SCHEMA -> resolveEnvironmentManifest(manifestPath, manifestText)
                    else -> resolveLegacyManifest(manifestPath, manifestFile, manifestText)
                }
            warnForMissingGeneratedMaps(resolved)
            resolved
        } catch (error: Throwable) {
            logger.warn(TAG, error) {
                "Failed to resolve HDR environment '$manifestPath': ${error.message ?: error::class.simpleName}."
            }
            null
        }
    }

    private fun resolveLegacyManifest(
        manifestPath: String,
        manifestFile: FileHandle,
        manifestText: String,
    ): GdxResolvedHdrEnvironment {
        val manifest = HdrEnvironmentManifestCodec.decode(manifestText)
        validate(manifest, manifestFile)
        val activeVariant = manifest.source.variants.first { it.id == manifest.source.activeVariant }
        return GdxResolvedHdrEnvironment(
            preset = manifest.name,
            manifestPath = manifestPath,
            defaults = manifest.defaults,
            activeSource = resolvePath(manifestPath, activeVariant.path),
            skyboxCross = manifest.skybox?.let { resolvePath(manifestPath, it.path) },
            skyboxFaces =
                manifest.skybox?.faces.orEmpty().associateWith { face ->
                    resolvePath(manifestPath, manifest.skybox!!.generatedFacesPath.replace(FACE_TOKEN, face))
                },
            irradianceFaces =
                manifest.irradiance.faces.associateWith { face ->
                    resolvePath(manifestPath, manifest.irradiance.path.replace(FACE_TOKEN, face))
                },
            radianceFaces =
                (0 until manifest.radiance.mipLevels).associateWith { mip ->
                    manifest.radiance.faces.associateWith { face ->
                        resolvePath(
                            manifestPath,
                            manifest.radiance.path
                                .replace(MIP_TOKEN, mip.toString())
                                .replace(FACE_TOKEN, face),
                        )
                    }
                },
            brdfLut = resolveBrdfLut(manifestPath, manifest.brdfLut.path),
        )
    }

    private fun resolveEnvironmentManifest(
        manifestPath: String,
        manifestText: String,
    ): GdxResolvedHdrEnvironment {
        val manifest = EnvironmentManifestCodec.decode(manifestText)
        validateEnvironment(manifest, manifestPath)
        val activeSource = manifest.sources.firstOrNull(EnvironmentSourceVariant::isDefault) ?: manifest.sources.first()
        return GdxResolvedHdrEnvironment(
            preset = manifest.name,
            manifestPath = manifestPath,
            defaults = manifest.defaults(),
            activeSource = resolvePath(manifestPath, activeSource.path),
            skyboxCross = null,
            skyboxFaces = resolveSkyboxFaces(manifestPath, manifest),
            irradianceFaces = resolveIrradianceFaces(manifestPath, manifest),
            radianceFaces = resolveRadianceFaces(manifestPath, manifest),
            brdfLut = manifest.generated.brdfLut?.let { brdf -> resolveBrdfLut(manifestPath, brdf.path) },
        )
    }

    private fun validate(
        manifest: HdrEnvironmentManifest,
        manifestFile: FileHandle,
    ) {
        require(manifest.schema == HdrEnvironmentManifestLoader.SCHEMA) {
            "Unsupported HDR environment schema '${manifest.schema}'."
        }
        require(manifest.version >= HdrEnvironmentManifestLoader.MINIMUM_VERSION) {
            "HDR environment manifest version must be >= ${HdrEnvironmentManifestLoader.MINIMUM_VERSION}."
        }
        require(manifest.source.variants.isNotEmpty()) {
            "HDR environment source.variants must not be empty."
        }
        require(
            manifest.source.variants
                .map { it.id }
                .toSet()
                .size == manifest.source.variants.size,
        ) {
            "HDR environment source variant ids must be unique."
        }
        val activeVariant =
            manifest.source.variants.firstOrNull { it.id == manifest.source.activeVariant }
                ?: error("HDR environment active variant '${manifest.source.activeVariant}' does not exist.")
        require(Gdx.files.internal(resolvePath(manifestFile.path(), activeVariant.path)).exists()) {
            "HDR environment active source is missing: '${activeVariant.path}'."
        }
        manifest.skybox?.let { skybox ->
            require(Gdx.files.internal(resolvePath(manifestFile.path(), skybox.path)).exists()) {
                "HDR environment skybox is missing: '${skybox.path}'."
            }
        }
    }

    private fun validateEnvironment(
        manifest: EnvironmentManifestDto,
        manifestPath: String,
    ) {
        require(manifest.schema == ENVIRONMENT_SCHEMA) {
            "Unsupported environment schema '${manifest.schema}' in '$manifestPath'."
        }
        require(manifest.sources.isNotEmpty()) {
            "Environment sources must not be empty."
        }
        val activeSource = manifest.sources.firstOrNull(EnvironmentSourceVariant::isDefault) ?: manifest.sources.first()
        require(Gdx.files.internal(resolvePath(manifestPath, activeSource.path)).exists()) {
            "Environment source is missing: '${activeSource.path}'."
        }
    }

    private fun warnForMissingGeneratedMaps(environment: GdxResolvedHdrEnvironment) {
        if (environment.irradianceFaces.values.any { !Gdx.files.internal(it).exists() }) {
            logger.warn(TAG) {
                "HDR environment '${environment.preset}' has missing irradiance maps; runtime fallback will be used."
            }
        }
        if (environment.radianceFaces.values
                .flatMap { it.values }
                .any { !Gdx.files.internal(it).exists() }
        ) {
            logger.warn(TAG) {
                "HDR environment '${environment.preset}' has missing radiance maps; runtime fallback will be used."
            }
        }
        if (environment.brdfLut == null) {
            logger.warn(TAG) {
                "HDR environment '${environment.preset}' BRDF LUT is missing from the manifest, shared assets, and gdx-gltf."
            }
        }
    }

    private fun resolveBrdfLut(
        manifestPath: String,
        manifestBrdfPath: String,
    ): GdxHdrAssetLocation? =
        listOf(
            GdxHdrAssetLocation(
                path = resolvePath(manifestPath, manifestBrdfPath),
                type = Files.FileType.Internal,
            ),
            GdxHdrAssetLocation(
                path = HdrEnvironmentAssets.SHARED_BRDF_LUT,
                type = Files.FileType.Internal,
            ),
            GdxHdrAssetLocation(
                path = SharedBrdfLutExporter.BUNDLED_BRDF_LUT,
                type = Files.FileType.Classpath,
            ),
        ).firstOrNull { location -> location.file().exists() }

    private fun resolveSkyboxFaces(
        manifestPath: String,
        manifest: EnvironmentManifestDto,
    ): Map<String, String> =
        manifest.generated.skybox
            ?.faces
            .orEmpty()
            .mapNotNull { (face, path) ->
                environmentFaceName(face)?.let { resolvedFace ->
                    resolvedFace to resolvePath(manifestPath, path)
                }
            }.toMap(linkedMapOf())

    private fun resolveIrradianceFaces(
        manifestPath: String,
        manifest: EnvironmentManifestDto,
    ): Map<String, String> =
        inferCubemapFaces(
            manifestPath = manifestPath,
            resourcePath = manifest.generated.irradiance?.path,
            directoryStem = "irradiance",
        )

    private fun resolveRadianceFaces(
        manifestPath: String,
        manifest: EnvironmentManifestDto,
    ): Map<Int, Map<String, String>> =
        manifest.generated.radiance
            ?.mips
            .orEmpty()
            .associate { mip ->
                mip.level to
                    inferCubemapFaces(
                        manifestPath = manifestPath,
                        resourcePath = mip.path,
                        directoryStem = "radiance_${mip.level}",
                    )
            }.filterValues { faces -> faces.isNotEmpty() }

    private fun inferCubemapFaces(
        manifestPath: String,
        resourcePath: String?,
        directoryStem: String,
    ): Map<String, String> {
        val normalized = resourcePath?.replace('\\', '/')?.trim().orEmpty()
        if (normalized.isBlank()) return emptyMap()
        if (normalized.contains(FACE_TOKEN)) {
            return orderedFaceNames.associateWith { face ->
                resolvePath(manifestPath, normalized.replace(FACE_TOKEN, face))
            }
        }
        val fileName = normalized.substringAfterLast('/')
        val parent = normalized.substringBeforeLast('/', "")
        if (!fileName.contains('.')) {
            val prefix = listOfNotNull(parent.takeIf(String::isNotBlank), fileName, directoryStem).joinToString("/")
            return orderedFaceNames.associateWith { face ->
                resolvePath(manifestPath, "${prefix}_$face.png")
            }
        }
        val extension = fileName.substringAfterLast('.')
        val stem = fileName.substringBeforeLast('.')
        val matchedFace = orderedFaceNames.firstOrNull { face -> stem.endsWith("_$face") || stem.endsWith("-$face") }
        if (matchedFace != null) {
            val separator = if (stem.endsWith("-$matchedFace")) "-" else "_"
            val baseStem = stem.removeSuffix("${separator}$matchedFace")
            val prefix = if (parent.isBlank()) baseStem else "$parent/$baseStem"
            return orderedFaceNames.associateWith { face ->
                resolvePath(manifestPath, "$prefix${separator}$face.$extension")
            }
        }
        return emptyMap()
    }

    private fun environmentFaceName(face: String): String? =
        when (face.lowercase()) {
            "px", "posx" -> "posx"
            "nx", "negx" -> "negx"
            "py", "posy" -> "posy"
            "ny", "negy" -> "negy"
            "pz", "posz" -> "posz"
            "nz", "negz" -> "negz"
            else -> null
        }

    private fun detectSchema(manifestText: String): String? = schemaRegex.find(manifestText)?.groupValues?.getOrNull(1)

    private fun EnvironmentManifestDto.defaults(): HdrEnvironmentDefaults =
        HdrEnvironmentDefaults(
            exposure = settings.exposure.toDouble(),
            toneMapping = "ACES",
            gammaCorrection = true,
            srgbTextures = true,
            skyboxEnabled = settings.skyboxVisible,
            environmentRotationDegrees = settings.rotationDegrees.toDouble(),
            ambientIntensity = settings.diffuseIntensity.toDouble(),
        )

    companion object {
        const val DEFAULT_ENVIRONMENT_PRESET = HdrEnvironmentAssets.DEFAULT_PRESET
        const val DEFAULT_ENVIRONMENT_MANIFEST = HdrEnvironmentAssets.DEFAULT_MANIFEST
        private const val FACE_TOKEN = "{face}"
        private const val MIP_TOKEN = "{mip}"
        private const val TAG = "GdxHdrEnvironmentResolver"
        private val orderedFaceNames = listOf("posx", "negx", "posy", "negy", "posz", "negz")
        private val schemaRegex = Regex(""""schema"\s*:\s*"([^"]+)"""")

        fun manifestPathFor(presetNameOrPath: String): String = HdrEnvironmentAssets.manifestPathForPreset(presetNameOrPath)

        fun resolvePath(
            manifestPath: String,
            relativePath: String,
        ): String = HdrEnvironmentAssets.resolveRelativeToManifest(manifestPath, relativePath)
    }
}

internal data class GdxResolvedHdrEnvironment(
    val preset: String,
    val manifestPath: String,
    val defaults: HdrEnvironmentDefaults,
    val activeSource: String,
    val skyboxCross: String?,
    val skyboxFaces: Map<String, String>,
    val irradianceFaces: Map<String, String>,
    val radianceFaces: Map<Int, Map<String, String>>,
    val brdfLut: GdxHdrAssetLocation?,
)

internal data class GdxHdrAssetLocation(
    val path: String,
    val type: Files.FileType,
) {
    fun file(): FileHandle =
        when (type) {
            Files.FileType.Classpath -> Gdx.files.classpath(path)
            Files.FileType.Internal -> Gdx.files.internal(path)
            Files.FileType.External -> Gdx.files.external(path)
            Files.FileType.Absolute -> Gdx.files.absolute(path)
            Files.FileType.Local -> Gdx.files.local(path)
        }
}
