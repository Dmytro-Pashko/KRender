package com.pashkd.krender.engine.backend.gdx

import com.badlogic.gdx.Files
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.assets.hdr.HdrEnvironmentAssets
import com.pashkd.krender.engine.assets.hdr.HdrEnvironmentManifest
import com.pashkd.krender.engine.assets.hdr.HdrEnvironmentManifestCodec
import com.pashkd.krender.engine.assets.hdr.HdrEnvironmentManifestLoader
import com.pashkd.krender.engine.assets.hdr.HdrEnvironmentSourceVariant
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
            val manifest = HdrEnvironmentManifestCodec.decode(manifestFile.readString("UTF-8"))
            validate(manifest, manifestFile)
            val activeVariant = manifest.source.variants.first { it.id == manifest.source.activeVariant }
            val resolved =
                GdxResolvedHdrEnvironment(
                    preset = manifest.name,
                    manifestPath = manifestPath,
                    manifest = manifest,
                    activeSource = resolvePath(manifestPath, activeVariant.path),
                    activeSourceVariant = activeVariant,
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
            warnForMissingGeneratedMaps(resolved)
            resolved
        } catch (error: Throwable) {
            logger.warn(TAG, error) {
                "Failed to resolve HDR environment '$manifestPath': ${error.message ?: error::class.simpleName}."
            }
            null
        }
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

    companion object {
        const val DEFAULT_ENVIRONMENT_PRESET = HdrEnvironmentAssets.DEFAULT_PRESET
        const val DEFAULT_ENVIRONMENT_MANIFEST = HdrEnvironmentAssets.DEFAULT_MANIFEST
        private const val FACE_TOKEN = "{face}"
        private const val MIP_TOKEN = "{mip}"
        private const val TAG = "GdxHdrEnvironmentResolver"

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
    val manifest: HdrEnvironmentManifest,
    val activeSource: String,
    val activeSourceVariant: HdrEnvironmentSourceVariant,
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
