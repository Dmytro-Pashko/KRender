package com.pashkd.krender.engine.assets.hdr

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

@Serializable
data class HdrEnvironmentManifest(
    val schema: String,
    val version: Int,
    val name: String,
    val displayName: String,
    val description: String? = null,
    val source: HdrEnvironmentSource,
    val skybox: HdrSkyboxConfig? = null,
    val irradiance: HdrIrradianceConfig,
    val radiance: HdrRadianceConfig,
    val brdfLut: HdrBrdfLutConfig,
    val defaults: HdrEnvironmentDefaults,
)

@Serializable
data class HdrEnvironmentSource(
    val activeVariant: String,
    val variants: List<HdrEnvironmentSourceVariant>,
)

@Serializable
data class HdrEnvironmentSourceVariant(
    val id: String,
    val path: String,
    val format: HdrSourceFormat,
    val projection: HdrProjection,
    val resolution: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val colorSpace: HdrColorSpace = HdrColorSpace.LINEAR,
)

@Serializable
enum class HdrSourceFormat {
    EXR,
    HDR,
}

@Serializable
enum class HdrProjection {
    EQUIRECTANGULAR,
}

@Serializable
enum class HdrColorSpace {
    LINEAR,
    SRGB,
}

@Serializable
enum class HdrSkyboxType {
    CUBEMAP_CROSS_4X3,
}

@Serializable
data class HdrSkyboxConfig(
    val type: HdrSkyboxType,
    val path: String,
    val generatedFacesPath: String,
    val faces: List<String>,
)

@Serializable
data class HdrIrradianceConfig(
    val generated: Boolean,
    val path: String,
    val size: Int,
    val faces: List<String>,
)

@Serializable
data class HdrRadianceConfig(
    val generated: Boolean,
    val path: String,
    val baseSize: Int,
    val mipLevels: Int,
    val faces: List<String>,
)

@Serializable
data class HdrBrdfLutConfig(
    val path: String,
    val size: Int,
    val shared: Boolean,
)

@Serializable
data class HdrEnvironmentDefaults(
    val exposure: Double,
    val toneMapping: String,
    val gammaCorrection: Boolean,
    val srgbTextures: Boolean,
    val skyboxEnabled: Boolean,
    val environmentRotationDegrees: Double,
    val ambientIntensity: Double,
)

data class LoadedHdrEnvironmentManifest(
    val manifestPath: Path,
    val manifest: HdrEnvironmentManifest,
    val activeSourcePath: Path,
)

object HdrEnvironmentManifestLoader {
    fun load(manifestPath: Path): LoadedHdrEnvironmentManifest {
        val normalizedPath = manifestPath.toAbsolutePath().normalize()
        require(Files.isRegularFile(normalizedPath)) {
            "HDR environment manifest does not exist: $normalizedPath"
        }
        val manifest =
            HdrEnvironmentManifestCodec.decode(
                Files.readString(normalizedPath, StandardCharsets.UTF_8),
            )
        validate(manifest, normalizedPath)
        val activeVariant = manifest.source.variants.first { it.id == manifest.source.activeVariant }
        return LoadedHdrEnvironmentManifest(
            manifestPath = normalizedPath,
            manifest = manifest,
            activeSourcePath = resolve(normalizedPath, activeVariant.path),
        )
    }

    fun validate(
        manifest: HdrEnvironmentManifest,
        manifestPath: Path,
    ) {
        require(manifest.schema == SCHEMA) {
            "Unsupported HDR environment schema '${manifest.schema}'. Expected '$SCHEMA'."
        }
        require(manifest.version >= MINIMUM_VERSION) {
            "HDR environment manifest version must be >= $MINIMUM_VERSION."
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
                ?: error(
                    "HDR environment active variant '${manifest.source.activeVariant}' " +
                        "does not exist in source.variants.",
                )
        require(Files.isRegularFile(resolve(manifestPath, activeVariant.path))) {
            "HDR environment active source does not exist: ${activeVariant.path}"
        }
        manifest.skybox?.let { skybox ->
            require(skybox.faces.isNotEmpty()) { "HDR skybox faces must not be empty." }
            if (skybox.type == HdrSkyboxType.CUBEMAP_CROSS_4X3) {
                require(Files.isRegularFile(resolve(manifestPath, skybox.path))) {
                    "HDR cubemap-cross skybox does not exist: ${skybox.path}"
                }
            }
        }
        require(manifest.irradiance.size > 0) { "HDR irradiance size must be positive." }
        require(manifest.radiance.baseSize > 0) { "HDR radiance baseSize must be positive." }
        require(manifest.radiance.mipLevels > 0) { "HDR radiance mipLevels must be positive." }
        require(manifest.brdfLut.size > 0) { "HDR BRDF LUT size must be positive." }
    }

    fun resolve(
        manifestPath: Path,
        relativePath: String,
    ): Path =
        manifestPath
            .toAbsolutePath()
            .normalize()
            .parent
            .resolve(relativePath)
            .normalize()

    const val SCHEMA = "krender.hdr-environment"
    const val MINIMUM_VERSION = 2
}

object HdrEnvironmentManifestCodec {
    private val json =
        Json(from = HdrManifestJson) {
            encodeDefaults = true
        }

    fun decode(text: String): HdrEnvironmentManifest =
        try {
            json.decodeFromString<HdrEnvironmentManifest>(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to decode HDR environment manifest: ${e.message}", e)
        }

    fun encode(manifest: HdrEnvironmentManifest): String = json.encodeToString(manifest)
}

private val HdrManifestJson =
    Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        ignoreUnknownKeys = true
        explicitNulls = false
    }
