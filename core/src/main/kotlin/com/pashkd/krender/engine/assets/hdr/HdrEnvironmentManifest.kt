package com.pashkd.krender.engine.assets.hdr

import com.pashkd.krender.engine.serialization.KRenderJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

data class HdrEnvironmentManifest(
    val schema: String,
    val version: Int,
    val name: String,
    val displayName: String,
    val description: String? = null,
    val source: HdrEnvironmentSource,
    val skybox: HdrSkyboxConfig?,
    val irradiance: HdrIrradianceConfig,
    val radiance: HdrRadianceConfig,
    val brdfLut: HdrBrdfLutConfig,
    val defaults: HdrEnvironmentDefaults,
)

data class HdrEnvironmentSource(
    val activeVariant: String,
    val variants: List<HdrEnvironmentSourceVariant>,
)

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

enum class HdrSourceFormat {
    EXR,
    HDR,
}

enum class HdrProjection {
    EQUIRECTANGULAR,
}

enum class HdrColorSpace {
    LINEAR,
    SRGB,
}

enum class HdrSkyboxType {
    CUBEMAP_CROSS_4X3,
}

data class HdrSkyboxConfig(
    val type: HdrSkyboxType,
    val path: String,
    val generatedFacesPath: String,
    val faces: List<String>,
)

data class HdrIrradianceConfig(
    val generated: Boolean,
    val path: String,
    val size: Int,
    val faces: List<String>,
)

data class HdrRadianceConfig(
    val generated: Boolean,
    val path: String,
    val baseSize: Int,
    val mipLevels: Int,
    val faces: List<String>,
)

data class HdrBrdfLutConfig(
    val path: String,
    val size: Int,
    val shared: Boolean,
)

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
    fun decode(text: String): HdrEnvironmentManifest {
        val root = KRenderJson.Pretty.parseToJsonElement(text).jsonObject
        return HdrEnvironmentManifest(
            schema = root.requiredString("schema"),
            version = root.requiredInt("version"),
            name = root.requiredString("name"),
            displayName = root.requiredString("displayName"),
            description = root.optionalString("description"),
            source = decodeSource(root.requiredObject("source")),
            skybox = decodeSkybox(root.optionalObject("skybox")),
            irradiance = decodeIrradiance(root.requiredObject("irradiance")),
            radiance = decodeRadiance(root.requiredObject("radiance")),
            brdfLut = decodeBrdfLut(root.requiredObject("brdfLut")),
            defaults = decodeDefaults(root.requiredObject("defaults")),
        )
    }

    fun encode(manifest: HdrEnvironmentManifest): String {
        val root =
            buildJsonObject {
                put("schema", manifest.schema)
                put("version", manifest.version)
                put("name", manifest.name)
                put("displayName", manifest.displayName)
                manifest.description?.let { put("description", it) }
                put("source", encodeSource(manifest.source))
                manifest.skybox?.let { skybox ->
                    put("skybox", encodeSkybox(skybox))
                }
                put("irradiance", encodeIrradiance(manifest.irradiance))
                put("radiance", encodeRadiance(manifest.radiance))
                put("brdfLut", encodeBrdfLut(manifest.brdfLut))
                put("defaults", encodeDefaults(manifest.defaults))
            }
        return KRenderJson.Pretty.encodeToString(JsonObject.serializer(), root)
    }

    private fun decodeSource(source: JsonObject): HdrEnvironmentSource =
        HdrEnvironmentSource(
            activeVariant = source.requiredString("activeVariant"),
            variants = source.requiredArray("variants").map { element -> decodeSourceVariant(element.jsonObject) },
        )

    private fun decodeSourceVariant(variant: JsonObject): HdrEnvironmentSourceVariant =
        HdrEnvironmentSourceVariant(
            id = variant.requiredString("id"),
            path = variant.requiredString("path"),
            format = variant.requiredEnum("format"),
            projection = variant.requiredEnum("projection"),
            resolution = variant.optionalString("resolution"),
            width = variant.optionalInt("width"),
            height = variant.optionalInt("height"),
            colorSpace = variant.optionalEnum("colorSpace", HdrColorSpace.LINEAR),
        )

    private fun decodeSkybox(skybox: JsonObject?): HdrSkyboxConfig? =
        skybox?.let {
            HdrSkyboxConfig(
                type = it.requiredEnum("type"),
                path = it.requiredString("path"),
                generatedFacesPath = it.requiredString("generatedFacesPath"),
                faces = it.requiredStringList("faces"),
            )
        }

    private fun decodeIrradiance(irradiance: JsonObject): HdrIrradianceConfig =
        HdrIrradianceConfig(
            generated = irradiance.requiredBoolean("generated"),
            path = irradiance.requiredString("path"),
            size = irradiance.requiredInt("size"),
            faces = irradiance.requiredStringList("faces"),
        )

    private fun decodeRadiance(radiance: JsonObject): HdrRadianceConfig =
        HdrRadianceConfig(
            generated = radiance.requiredBoolean("generated"),
            path = radiance.requiredString("path"),
            baseSize = radiance.requiredInt("baseSize"),
            mipLevels = radiance.requiredInt("mipLevels"),
            faces = radiance.requiredStringList("faces"),
        )

    private fun decodeBrdfLut(brdfLut: JsonObject): HdrBrdfLutConfig =
        HdrBrdfLutConfig(
            path = brdfLut.requiredString("path"),
            size = brdfLut.requiredInt("size"),
            shared = brdfLut.requiredBoolean("shared"),
        )

    private fun decodeDefaults(defaults: JsonObject): HdrEnvironmentDefaults =
        HdrEnvironmentDefaults(
            exposure = defaults.requiredDouble("exposure"),
            toneMapping = defaults.requiredString("toneMapping"),
            gammaCorrection = defaults.requiredBoolean("gammaCorrection"),
            srgbTextures = defaults.requiredBoolean("srgbTextures"),
            skyboxEnabled = defaults.requiredBoolean("skyboxEnabled"),
            environmentRotationDegrees = defaults.requiredDouble("environmentRotationDegrees"),
            ambientIntensity = defaults.requiredDouble("ambientIntensity"),
        )

    private fun encodeSource(source: HdrEnvironmentSource): JsonObject =
        buildJsonObject {
            put("activeVariant", source.activeVariant)
            put(
                "variants",
                buildJsonArray {
                    source.variants.forEach { variant -> add(encodeSourceVariant(variant)) }
                },
            )
        }

    private fun encodeSourceVariant(variant: HdrEnvironmentSourceVariant): JsonObject =
        buildJsonObject {
            put("id", variant.id)
            put("path", variant.path)
            put("format", variant.format.name)
            put("projection", variant.projection.name)
            variant.resolution?.let { put("resolution", it) }
            variant.width?.let { put("width", it) }
            variant.height?.let { put("height", it) }
            put("colorSpace", variant.colorSpace.name)
        }

    private fun encodeSkybox(skybox: HdrSkyboxConfig): JsonObject =
        buildJsonObject {
            put("type", skybox.type.name)
            put("path", skybox.path)
            put("generatedFacesPath", skybox.generatedFacesPath)
            put("faces", skybox.faces.toJsonArray())
        }

    private fun encodeIrradiance(irradiance: HdrIrradianceConfig): JsonObject =
        buildJsonObject {
            put("generated", irradiance.generated)
            put("path", irradiance.path)
            put("size", irradiance.size)
            put("faces", irradiance.faces.toJsonArray())
        }

    private fun encodeRadiance(radiance: HdrRadianceConfig): JsonObject =
        buildJsonObject {
            put("generated", radiance.generated)
            put("path", radiance.path)
            put("baseSize", radiance.baseSize)
            put("mipLevels", radiance.mipLevels)
            put("faces", radiance.faces.toJsonArray())
        }

    private fun encodeBrdfLut(brdfLut: HdrBrdfLutConfig): JsonObject =
        buildJsonObject {
            put("path", brdfLut.path)
            put("size", brdfLut.size)
            put("shared", brdfLut.shared)
        }

    private fun encodeDefaults(defaults: HdrEnvironmentDefaults): JsonObject =
        buildJsonObject {
            put("exposure", defaults.exposure)
            put("toneMapping", defaults.toneMapping)
            put("gammaCorrection", defaults.gammaCorrection)
            put("srgbTextures", defaults.srgbTextures)
            put("skyboxEnabled", defaults.skyboxEnabled)
            put("environmentRotationDegrees", defaults.environmentRotationDegrees)
            put("ambientIntensity", defaults.ambientIntensity)
        }
}

private fun JsonObject.requiredObject(name: String): JsonObject = this[name]?.jsonObject ?: error("Missing JSON object '$name'.")

private fun JsonObject.optionalObject(name: String): JsonObject? = this[name]?.takeUnless { it is JsonPrimitive && it.contentOrNull == "null" }?.jsonObject

private fun JsonObject.requiredArray(name: String): JsonArray = this[name]?.jsonArray ?: error("Missing JSON array '$name'.")

private fun JsonObject.requiredString(name: String): String = this[name]?.jsonPrimitive?.contentOrNull ?: error("Missing JSON string '$name'.")

private fun JsonObject.optionalString(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

private fun JsonObject.requiredInt(name: String): Int = this[name]?.jsonPrimitive?.intOrNull ?: error("Missing JSON integer '$name'.")

private fun JsonObject.optionalInt(name: String): Int? = this[name]?.jsonPrimitive?.intOrNull

private fun JsonObject.requiredDouble(name: String): Double = this[name]?.jsonPrimitive?.doubleOrNull ?: error("Missing JSON number '$name'.")

private fun JsonObject.requiredBoolean(name: String): Boolean = this[name]?.jsonPrimitive?.booleanOrNull ?: error("Missing JSON boolean '$name'.")

private fun JsonObject.requiredStringList(name: String): List<String> = requiredArray(name).map { it.jsonPrimitive.content }

private inline fun <reified T : Enum<T>> JsonObject.requiredEnum(name: String): T = enumValueOf(requiredString(name))

private inline fun <reified T : Enum<T>> JsonObject.optionalEnum(
    name: String,
    default: T,
): T = optionalString(name)?.let { value -> enumValueOf<T>(value) } ?: default

private fun List<String>.toJsonArray(): JsonArray =
    buildJsonArray {
        this@toJsonArray.forEach { value -> add(JsonPrimitive(value)) }
    }
