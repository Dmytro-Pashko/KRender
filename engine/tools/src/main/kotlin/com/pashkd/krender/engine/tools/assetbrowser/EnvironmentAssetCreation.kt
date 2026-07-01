package com.pashkd.krender.engine.tools.assetbrowser

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.assets.AssetDescriptor
import com.pashkd.krender.engine.assets.AssetRegistryService
import com.pashkd.krender.engine.assets.AssetType
import com.pashkd.krender.engine.assets.environment.*
import java.nio.file.Files
import java.nio.file.Path

internal object EnvironmentAssetCreation {
    private const val CreateFromExrActionId = "create-environment-from-exr"
    private const val CreateFromHdrActionId = "create-environment-from-hdr"

    fun actionsFor(asset: AssetDescriptor): List<AssetActionDescriptor> =
        when {
            asset.type != AssetType.HdrSource -> emptyList()
            asset.extension.equals("exr", ignoreCase = true) ->
                listOf(AssetActionDescriptor(CreateFromExrActionId, "Create Environment from EXR"))

            asset.extension.equals("hdr", ignoreCase = true) ->
                listOf(AssetActionDescriptor(CreateFromHdrActionId, "Create Environment from HDR"))

            else -> emptyList()
        }

    fun runAction(
        asset: AssetDescriptor,
        actionId: String,
        engine: EngineContext,
        logger: Logger,
    ): String =
        when (actionId) {
            CreateFromExrActionId,
            CreateFromHdrActionId,
            -> createEnvironmentFromHdrSource(asset, engine, logger)

            else -> error("Unsupported asset action '$actionId'.")
        }

    private fun createEnvironmentFromHdrSource(
        asset: AssetDescriptor,
        engine: EngineContext,
        logger: Logger,
    ): String {
        require(asset.type == AssetType.HdrSource) { "Asset '${asset.path}' is not an HDR source." }
        val registry = engine.assetRegistry
        val basePath = registry.baseDir().toPath().toAbsolutePath().normalize()
        val sourcePath = resolveAssetPath(basePath, asset.path)
        require(Files.isRegularFile(sourcePath)) { "Source file not found: ${asset.path}" }

        val sourceFileName = sourcePath.fileName.toString()
        val environmentId = uniqueEnvironmentId(basePath, environmentBaseId(sourceFileName))
        val environmentDir = basePath.resolve(assetBrowserNormalizePath("environments/$environmentId")).normalize()
        val sourcesDir = environmentDir.resolve("sources")
        Files.createDirectories(sourcesDir)

        val copiedSourcePath = sourcesDir.resolve(sourceFileName)
        Files.copy(sourcePath, copiedSourcePath)

        val manifestPath = assetBrowserNormalizePath("environments/$environmentId/$environmentId.environment.json")
        val environmentAsset =
            EnvironmentAsset(
                id = EnvironmentAssetId(environmentId),
                name = environmentDisplayName(environmentId),
                manifestPath = manifestPath,
                version = ENVIRONMENT_SCHEMA_VERSION,
                type = EnvironmentType.HdrIbl,
                sources =
                    listOf(
                        EnvironmentSourceVariant(
                            id = "source",
                            path = "sources/$sourceFileName",
                            format = sourceFormatFor(asset),
                            role = SourceVariantRole.Source,
                            isDefault = true,
                            resolution = null,
                            colorSpace = "Linear",
                            dynamicRange = "HDR",
                        ),
                    ),
                generated =
                    EnvironmentGeneratedResources(
                        skybox =
                            SkyboxResourceSet(
                                layout = "SixFaces",
                                resolution = 1024,
                                format = "KTX",
                                faces =
                                    linkedMapOf(
                                        "px" to "generated/skybox/px.ktx",
                                        "nx" to "generated/skybox/nx.ktx",
                                        "py" to "generated/skybox/py.ktx",
                                        "ny" to "generated/skybox/ny.ktx",
                                        "pz" to "generated/skybox/pz.ktx",
                                        "nz" to "generated/skybox/nz.ktx",
                                    ),
                            ),
                        irradiance = CubemapResource(path = "generated/irradiance/irradiance.ktx", resolution = 64, format = "KTX"),
                        radiance =
                            RadianceMipChain(
                                baseResolution = 256,
                                mips =
                                    listOf(
                                        RadianceMip(level = 0, roughness = 0f, path = "generated/radiance/radiance_mip_00.ktx"),
                                        RadianceMip(level = 1, roughness = 0.25f, path = "generated/radiance/radiance_mip_01.ktx"),
                                        RadianceMip(level = 2, roughness = 0.5f, path = "generated/radiance/radiance_mip_02.ktx"),
                                        RadianceMip(level = 3, roughness = 0.75f, path = "generated/radiance/radiance_mip_03.ktx"),
                                        RadianceMip(level = 4, roughness = 1f, path = "generated/radiance/radiance_mip_04.ktx"),
                                    ),
                            ),
                        brdfLut = TextureResourceRef(path = "../../shared/pbr/brdf_lut.ktx", shared = true),
                    ),
                settings = EnvironmentSettings(),
                generation =
                    EnvironmentGenerationSettings(
                        sourceVariantId = "source",
                        generator = "KRenderIBLGenerator",
                        generatorVersion = "1",
                        generatedAt = null,
                        skyboxResolution = 1024,
                        irradianceResolution = 64,
                        radianceResolution = 256,
                        radianceMipCount = 5,
                        outputFormat = "KTX",
                    ),
            )

        val environmentService = DefaultEnvironmentService(engine.sceneFiles)
        environmentService.save(environmentAsset)
        val createdAsset = environmentService.load(manifestPath)
        environmentService.validate(createdAsset)
        engine.editorToolLauncher.launchEnvironmentEditor(manifestPath)
        logger.info(TAG) {
            "Created environment manifest '$manifestPath' from source='${asset.path}' copiedTo='${assetBrowserNormalizePath("environments/$environmentId/sources/$sourceFileName")}'"
        }
        return manifestPath
    }

    private fun resolveAssetPath(
        basePath: Path,
        relativePath: String,
    ): Path {
        val resolved = basePath.resolve(assetBrowserNormalizePath(relativePath)).normalize()
        require(resolved.startsWith(basePath)) { "Asset path escapes asset root: '$relativePath'" }
        return resolved
    }

    private fun uniqueEnvironmentId(
        basePath: Path,
        baseId: String,
    ): String {
        var candidate = baseId
        var suffix = 1
        while (environmentExists(basePath, candidate)) {
            candidate = "${baseId}_$suffix"
            suffix += 1
        }
        return candidate
    }

    private fun environmentExists(
        basePath: Path,
        environmentId: String,
    ): Boolean {
        val environmentDir = basePath.resolve(assetBrowserNormalizePath("environments/$environmentId")).normalize()
        val manifest = environmentDir.resolve("$environmentId.environment.json")
        return Files.exists(environmentDir) || Files.exists(manifest)
    }

    private fun environmentBaseId(sourceFileName: String): String =
        sourceFileName
            .substringBeforeLast('.')
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "environment" }

    private fun environmentDisplayName(environmentId: String): String =
        environmentId
            .split('_')
            .filter { token -> token.isNotBlank() }
            .joinToString(" ") { token -> token.replaceFirstChar { it.uppercase() } }
            .ifBlank { "Environment" }

    private fun sourceFormatFor(asset: AssetDescriptor): EnvironmentSourceFormat =
        when {
            asset.extension.equals("exr", ignoreCase = true) -> EnvironmentSourceFormat.EXR
            asset.extension.equals("hdr", ignoreCase = true) -> EnvironmentSourceFormat.HDR
            else -> error("Unsupported HDR source extension '${asset.extension}'.")
        }

    private const val TAG = "EnvironmentAssetCreation"
}
