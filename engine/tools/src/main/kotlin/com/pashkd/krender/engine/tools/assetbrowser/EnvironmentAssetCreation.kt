package com.pashkd.krender.engine.tools.assetbrowser

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.assets.AssetDescriptor
import com.pashkd.krender.engine.assets.AssetType
import com.pashkd.krender.engine.assets.environment.CubemapResource
import com.pashkd.krender.engine.assets.environment.DefaultEnvironmentService
import com.pashkd.krender.engine.assets.environment.ENVIRONMENT_SCHEMA_VERSION
import com.pashkd.krender.engine.assets.environment.Environment
import com.pashkd.krender.engine.assets.environment.EnvironmentSettings
import com.pashkd.krender.engine.assets.environment.EnvironmentSourceFormat
import com.pashkd.krender.engine.assets.environment.EnvironmentSourceVariant
import com.pashkd.krender.engine.assets.environment.EnvironmentType
import com.pashkd.krender.engine.assets.environment.RadianceMip
import com.pashkd.krender.engine.assets.environment.RadianceMipChain
import com.pashkd.krender.engine.assets.environment.SkyboxResourceSet
import com.pashkd.krender.engine.assets.environment.SourceVariantRole
import com.pashkd.krender.engine.assets.environment.TextureResourceRef
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal data class CreateEnvironmentFromSourceRequest(
    val sourcePath: Path,
    val sourceFormat: EnvironmentSourceFormat,
    val targetRoot: Path,
    val preferredEnvironmentId: String? = null,
    val copySource: Boolean = true,
    val openAfterCreate: Boolean = false,
)

internal data class CreateEnvironmentResult(
    val environmentId: String,
    val manifestPath: String,
    val sourcePath: String,
)

internal object EnvironmentAssetCreation {
    private const val CreateFromExrActionId = "create-environment-from-exr"

    fun actionsFor(asset: AssetDescriptor): List<AssetActionDescriptor> {
        val actions = mutableListOf<AssetActionDescriptor>()
        if (asset.type == AssetType.HdrSource) {
            when {
                asset.extension.equals("exr", ignoreCase = true) ->
                    actions += AssetActionDescriptor(CreateFromExrActionId, "Create Environment from EXR")
            }
        }
        return actions
    }

    fun createFromAsset(
        asset: AssetDescriptor,
        engine: EngineContext,
        logger: Logger,
    ): CreateEnvironmentResult {
        require(asset.type == AssetType.HdrSource) { "Asset '${asset.path}' is not an HDR source." }
        val targetRoot =
            engine.assetRegistry
                .baseDir()
                .toPath()
                .toAbsolutePath()
                .normalize()
        val sourcePath = resolveAssetPath(targetRoot, asset.path)
        return createEnvironmentFromSource(
            request =
                CreateEnvironmentFromSourceRequest(
                    sourcePath = sourcePath,
                    sourceFormat = sourceFormatForExtension(asset.extension),
                    targetRoot = targetRoot,
                    preferredEnvironmentId = environmentBaseId(sourcePath.fileName.toString()),
                    copySource = true,
                    openAfterCreate = false,
                ),
            engine = engine,
            logger = logger,
        )
    }

    fun createFromExternalSourcePath(
        sourcePath: String,
        preferredEnvironmentId: String?,
        engine: EngineContext,
        logger: Logger,
    ): CreateEnvironmentResult {
        val normalizedSource = Path.of(sourcePath).toAbsolutePath().normalize()
        return createEnvironmentFromSource(
            request =
                CreateEnvironmentFromSourceRequest(
                    sourcePath = normalizedSource,
                    sourceFormat = sourceFormatForExtension(normalizedSource.fileName.toString().substringAfterLast('.', "")),
                    targetRoot =
                        engine.assetRegistry
                            .baseDir()
                            .toPath()
                            .toAbsolutePath()
                            .normalize(),
                    preferredEnvironmentId = preferredEnvironmentId,
                    copySource = true,
                    openAfterCreate = false,
                ),
            engine = engine,
            logger = logger,
        )
    }

    fun runAction(
        asset: AssetDescriptor,
        actionId: String,
        engine: EngineContext,
        logger: Logger,
    ): CreateEnvironmentResult =
        when (actionId) {
            CreateFromExrActionId,
            -> createFromAsset(asset, engine, logger)
            else -> error("Unsupported asset action '$actionId'.")
        }

    fun createEnvironmentFromSource(
        request: CreateEnvironmentFromSourceRequest,
        engine: EngineContext,
        logger: Logger,
    ): CreateEnvironmentResult {
        val sourcePath = request.sourcePath.toAbsolutePath().normalize()
        require(Files.isRegularFile(sourcePath)) { "Source file does not exist: $sourcePath" }
        require(sourceFormatMatchesFileName(request.sourceFormat, sourcePath.fileName.toString())) {
            "Selected file does not match ${request.sourceFormat.name} format: ${sourcePath.fileName}"
        }

        val targetRoot = request.targetRoot.toAbsolutePath().normalize()
        require(Files.exists(targetRoot)) { "Target asset root does not exist: $targetRoot" }

        val baseId = sanitizeEnvironmentId(request.preferredEnvironmentId ?: environmentBaseId(sourcePath.fileName.toString()))
        val target = prepareEnvironmentTarget(targetRoot, baseId)
        val manifestSourcePath = prepareEnvironmentSource(request, targetRoot, target.directory, sourcePath)
        val environment = buildEnvironment(target, manifestSourcePath, request.sourceFormat)

        val environmentService = DefaultEnvironmentService(engine.sceneFiles)
        environmentService.save(environment)
        val createdAsset = environmentService.load(target.manifestPath)
        environmentService.validate(createdAsset)
        if (request.openAfterCreate) {
            engine.editorToolLauncher.launchEnvironmentEditor(target.manifestPath)
        }

        val result =
            CreateEnvironmentResult(
                environmentId = target.environmentId,
                manifestPath = target.manifestPath,
                sourcePath = manifestSourcePath,
            )
        logger.info(TAG) {
            "Created environment id='${result.environmentId}' manifest='${result.manifestPath}' source='${result.sourcePath}' from='$sourcePath'"
        }
        return result
    }

    private fun prepareEnvironmentTarget(
        targetRoot: Path,
        baseId: String,
    ): EnvironmentTarget {
        val environmentId = uniqueEnvironmentId(targetRoot, baseId)
        val directory = resolveInside(targetRoot, "environments/$environmentId")
        val manifestPath = assetBrowserNormalizePath("environments/$environmentId/$environmentId.environment.json")
        return EnvironmentTarget(environmentId, directory, manifestPath)
    }

    private fun prepareEnvironmentSource(
        request: CreateEnvironmentFromSourceRequest,
        targetRoot: Path,
        environmentDir: Path,
        sourcePath: Path,
    ): String {
        val sourceFileName = sourcePath.fileName.toString()
        val copiedSourcePath = resolveInside(environmentDir, "sources/$sourceFileName")
        Files.createDirectories(copiedSourcePath.parent)
        return if (request.copySource) {
            Files.copy(sourcePath, copiedSourcePath, StandardCopyOption.COPY_ATTRIBUTES)
            "sources/$sourceFileName"
        } else {
            require(sourcePath.startsWith(targetRoot)) {
                "Non-copy environment sources must stay inside the asset root: $sourcePath"
            }
            environmentDir.relativize(sourcePath).toString().replace('\\', '/')
        }
    }

    private fun buildEnvironment(
        target: EnvironmentTarget,
        manifestSourcePath: String,
        sourceFormat: EnvironmentSourceFormat,
    ): Environment =
        Environment(
            schemaVersion = ENVIRONMENT_SCHEMA_VERSION,
            id = target.environmentId,
            name = environmentDisplayName(target.environmentId),
            manifestPath = target.manifestPath,
            type = EnvironmentType.HdrIbl,
            description = null,
            settings = EnvironmentSettings(),
            sources =
                listOf(
                    EnvironmentSourceVariant(
                        id = "source",
                        path = manifestSourcePath,
                        format = sourceFormat,
                        role = SourceVariantRole.Source,
                        isDefault = true,
                        resolution = null,
                        colorSpace = "Linear",
                        dynamicRange = "HDR",
                    ),
                ),
            skybox = null,
            irradiance = null,
            radiance = null,
            brdfLut = null,
        )

    private fun resolveAssetPath(
        basePath: Path,
        relativePath: String,
    ): Path {
        val resolved = basePath.resolve(assetBrowserNormalizePath(relativePath)).normalize()
        require(resolved.startsWith(basePath)) { "Asset path escapes asset root: '$relativePath'" }
        return resolved
    }

    private fun resolveInside(
        root: Path,
        relativePath: String,
    ): Path {
        val resolved = root.resolve(assetBrowserNormalizePath(relativePath)).normalize()
        require(resolved.startsWith(root)) { "Path escapes asset root: '$relativePath'" }
        return resolved
    }

    private fun uniqueEnvironmentId(
        targetRoot: Path,
        baseId: String,
    ): String {
        var candidate = baseId
        var suffix = 1
        while (environmentExists(targetRoot, candidate)) {
            candidate = "${baseId}_$suffix"
            suffix += 1
        }
        return candidate
    }

    private fun environmentExists(
        targetRoot: Path,
        environmentId: String,
    ): Boolean {
        val environmentDir = resolveInside(targetRoot, "environments/$environmentId")
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

    private fun sanitizeEnvironmentId(value: String): String =
        value
            .trim()
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

    private fun sourceFormatMatchesFileName(
        format: EnvironmentSourceFormat,
        fileName: String,
    ): Boolean =
        when (format) {
            EnvironmentSourceFormat.EXR -> fileName.endsWith(".exr", ignoreCase = true)
            EnvironmentSourceFormat.HDR -> fileName.endsWith(".hdr", ignoreCase = true)
        }

    private fun sourceFormatForExtension(extension: String): EnvironmentSourceFormat =
        when {
            extension.equals("exr", ignoreCase = true) -> EnvironmentSourceFormat.EXR
            extension.equals("hdr", ignoreCase = true) -> EnvironmentSourceFormat.HDR
            else -> error("Selected file is not a supported environment source: .$extension")
        }

    private data class EnvironmentTarget(
        val environmentId: String,
        val directory: Path,
        val manifestPath: String,
    )

    private const val TAG = "EnvironmentAssetCreation"
}
