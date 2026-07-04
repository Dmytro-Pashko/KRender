package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.assets.TextureMetadataReader
import com.pashkd.krender.engine.assets.environment.CubemapResource
import com.pashkd.krender.engine.assets.environment.Environment
import com.pashkd.krender.engine.assets.environment.EnvironmentPathResolver
import com.pashkd.krender.engine.assets.environment.RadianceMip
import com.pashkd.krender.engine.tools.common.EditorTexturePreviewService
import com.pashkd.krender.engine.tools.common.TexturePreviewResult
import com.pashkd.krender.engine.tools.common.texturepreview.TexturePreviewRegion
import java.io.File

class EnvironmentResourceFaceResolver(
    private val assetRoot: File,
    private val texturePreviewService: EditorTexturePreviewService,
    private val queueTexture: (String) -> Unit,
) {
    fun buildPreviewModel(
        environment: Environment,
        mode: EnvironmentResourceMode,
        selectedMipLevel: Int,
        selectedItemId: String?,
        hoveredItemId: String?,
        skyboxImportState: SkyboxImportState? = null,
    ): EnvironmentResourcePreviewModel {
        return when (mode) {
            EnvironmentResourceMode.Skybox -> buildSkyboxModel(environment, selectedItemId, hoveredItemId)
            EnvironmentResourceMode.Irradiance -> buildIrradianceModel(environment, selectedItemId, hoveredItemId)
            EnvironmentResourceMode.Radiance -> buildRadianceModel(environment, selectedMipLevel, selectedItemId, hoveredItemId)
            EnvironmentResourceMode.BrdfLut -> buildBrdfLutModel(environment, selectedItemId, hoveredItemId)
            EnvironmentResourceMode.ImportedSkyboxSource ->
                buildImportedSourceModel(
                    environment = environment,
                    importState = skyboxImportState,
                    selectedItemId = selectedItemId,
                    hoveredItemId = hoveredItemId,
                )
        }
    }

    private fun buildSkyboxModel(
        environment: Environment,
        selectedItemId: String?,
        hoveredItemId: String?,
    ): EnvironmentResourcePreviewModel {
        val items =
            EnvironmentCubemapFace.ordered.map { face ->
                val path =
                    environment.skybox
                        ?.faces
                        ?.entries
                        ?.firstOrNull { (key, _) -> EnvironmentCubemapFace.fromIdOrAlias(key) == face }
                        ?.value
                buildCanvasItem(
                    environmentManifestPath = environment.manifestPath,
                    label = face.id,
                    id = face.id,
                    resourceMode = EnvironmentResourceMode.Skybox,
                    manifestPath = path,
                    formatHint = environment.skybox?.format,
                    regionIndex = EnvironmentCubemapFace.ordered.indexOf(face),
                )
            }
        val diagnostics =
            buildList {
                if (environment.skybox == null) add("Skybox resource set is not defined.")
                if (environment.skybox != null && items.any { !it.exists }) add("One or more skybox face files are missing.")
            }
        return buildModel(EnvironmentResourceMode.Skybox, items, selectedItemId, hoveredItemId, diagnostics)
    }

    private fun buildIrradianceModel(
        environment: Environment,
        selectedItemId: String?,
        hoveredItemId: String?,
    ): EnvironmentResourcePreviewModel {
        val items =
            resolveCubemapFaces(environment, environment.irradiance)
                .mapIndexed { index, resolved ->
                    buildCanvasItem(
                        environmentManifestPath = environment.manifestPath,
                        label = resolved.face.id,
                        id = resolved.face.id,
                        resourceMode = EnvironmentResourceMode.Irradiance,
                        manifestPath = resolved.path,
                        formatHint = environment.irradiance?.format,
                        regionIndex = index,
                    )
                }
        val diagnostics =
            buildList {
                if (environment.irradiance == null) add("Irradiance resource is not defined.")
                if (environment.irradiance != null && items.any { !it.exists }) add("One or more irradiance face files are missing.")
            }
        return buildModel(EnvironmentResourceMode.Irradiance, items, selectedItemId, hoveredItemId, diagnostics)
    }

    private fun buildRadianceModel(
        environment: Environment,
        selectedMipLevel: Int,
        selectedItemId: String?,
        hoveredItemId: String?,
    ): EnvironmentResourcePreviewModel {
        val radiance = environment.radiance
        val mip = radiance?.mips?.firstOrNull { it.level == selectedMipLevel } ?: radiance?.mips?.firstOrNull()
        val items =
            mip?.let { selectedMip ->
                resolveRadianceFaces(environment, selectedMip)
                    .mapIndexed { index, resolved ->
                        buildCanvasItem(
                            environmentManifestPath = environment.manifestPath,
                            label = resolved.face.id,
                            id = resolved.face.id,
                            resourceMode = EnvironmentResourceMode.Radiance,
                            manifestPath = resolved.path,
                            formatHint = "PNG",
                            regionIndex = index,
                            mipLevel = selectedMip.level,
                            roughness = selectedMip.roughness,
                        )
                    }
            }.orEmpty()
        val diagnostics =
            buildList {
                if (radiance == null) add("Radiance mip chain is not defined.")
                if (radiance != null && radiance.mips.isEmpty()) add("Radiance mip chain has no mip entries.")
                if (mip != null && items.any { !it.exists }) add("One or more radiance face files are missing for mip ${mip.level}.")
            }
        return buildModel(EnvironmentResourceMode.Radiance, items, selectedItemId, hoveredItemId, diagnostics)
    }

    private fun buildBrdfLutModel(
        environment: Environment,
        selectedItemId: String?,
        hoveredItemId: String?,
    ): EnvironmentResourcePreviewModel {
        val items =
            environment.brdfLut
                ?.let { brdf ->
                    listOf(
                        buildCanvasItem(
                            environmentManifestPath = environment.manifestPath,
                            label = "BRDF LUT",
                            id = "brdf_lut",
                            resourceMode = EnvironmentResourceMode.BrdfLut,
                            manifestPath = brdf.path,
                            formatHint = "PNG",
                            regionIndex = 0,
                            singleTextureCanvas = true,
                        ),
                    )
                }.orEmpty()
        val diagnostics =
            buildList {
                if (environment.brdfLut == null) add("BRDF LUT resource is not defined.")
                if (items.any { !it.exists }) add("BRDF LUT file is missing or unavailable.")
            }
        return buildModel(EnvironmentResourceMode.BrdfLut, items, selectedItemId, hoveredItemId, diagnostics, singleTextureCanvas = true)
    }

    private fun buildImportedSourceModel(
        environment: Environment,
        importState: SkyboxImportState?,
        selectedItemId: String? = null,
        hoveredItemId: String? = null,
    ): EnvironmentResourcePreviewModel {
        val sourcePath = importState?.sourcePath?.takeIf(String::isNotBlank)
        val preview =
            sourcePath
                ?.takeIf(::isPreviewableTexture)
                ?.let { path ->
                    queueTexture(path)
                    texturePreviewService.preview(path)
                }
        val previewHandle = (preview as? TexturePreviewResult.Available)?.handle
        val sourceFile = sourcePath?.let { path -> File(assetRoot, path) }
        val metadata = sourceFile?.takeIf(File::isFile)?.let(TextureMetadataReader::read)
        if (sourcePath == null || previewHandle == null || importState == null) {
            return EnvironmentResourcePreviewModel(
                mode = EnvironmentResourceMode.ImportedSkyboxSource,
                contentWidth = 1,
                contentHeight = 1,
                items = emptyList(),
                diagnostics = listOf("No imported skybox source preview is available yet. Enter a previewable source texture path in Skybox Import."),
                statusMessage = "Imported Skybox Source preview is not available.",
            )
        }
        val items =
            importState.regions.values
                .sortedBy { it.face.ordinal }
                .map { region ->
                    EnvironmentResourceCanvasItem(
                        id = region.face.id,
                        label = region.face.id,
                        resourceMode = EnvironmentResourceMode.ImportedSkyboxSource,
                        region =
                            TexturePreviewRegion(
                                id = region.face.id,
                                label = region.face.id,
                                x = region.x,
                                y = region.y,
                                width = region.width,
                                height = region.height,
                            ),
                        manifestPath = sourcePath,
                        resolvedPath = sourcePath,
                        previewHandle = previewHandle,
                        width = previewHandle.width,
                        height = previewHandle.height,
                        format = sourcePath.substringAfterLast('.', "").uppercase(),
                        exists = sourceFile?.exists() == true,
                        sourceRegion =
                            EnvironmentResourceSourceRegion(
                                x = region.x,
                                y = region.y,
                                width = region.width,
                                height = region.height,
                                u0 = region.x / previewHandle.width.toFloat(),
                                v0 = region.y / previewHandle.height.toFloat(),
                                u1 = (region.x + region.width) / previewHandle.width.toFloat(),
                                v1 = (region.y + region.height) / previewHandle.height.toFloat(),
                            ),
                        warnings =
                            buildList {
                                if (sourceFile?.exists() != true) add("Source file is missing.")
                            },
                    )
                }
        val selected = items.firstOrNull { it.id == selectedItemId } ?: items.firstOrNull()
        return EnvironmentResourcePreviewModel(
            mode = EnvironmentResourceMode.ImportedSkyboxSource,
            contentWidth = metadata?.width ?: previewHandle.width,
            contentHeight = metadata?.height ?: previewHandle.height,
            items = items,
            canvasPreviewHandle = previewHandle,
            drawItemsAsOverlayRegions = true,
            diagnostics = if (sourceFile?.exists() == true) emptyList() else listOf("Imported skybox source file is missing."),
            selectedItem = selected,
            hoveredItem = items.firstOrNull { it.id == hoveredItemId },
            statusMessage = if (selected != null) "Inspecting imported source region ${selected.label}." else "Inspecting imported skybox source.",
        )
    }

    private fun buildModel(
        mode: EnvironmentResourceMode,
        items: List<EnvironmentResourceCanvasItem>,
        selectedItemId: String?,
        hoveredItemId: String?,
        diagnostics: List<String>,
        singleTextureCanvas: Boolean = false,
    ): EnvironmentResourcePreviewModel {
        val contentWidth = if (singleTextureCanvas) items.firstOrNull()?.region?.width ?: 1 else DefaultCanvasWidth
        val contentHeight = if (singleTextureCanvas) items.firstOrNull()?.region?.height ?: 1 else DefaultCanvasHeight
        val selected = items.firstOrNull { it.id == selectedItemId } ?: items.firstOrNull()
        val hovered = items.firstOrNull { it.id == hoveredItemId }
        val status =
            when {
                items.isEmpty() -> "No previewable resources are available for ${mode.label()}."
                selected == null -> "Select a resource face to inspect it."
                selected.previewHandle == null && selected.exists -> "Preview handle is not available for '${selected.label}' yet."
                selected.previewHandle == null -> "Resource '${selected.label}' is missing."
                else -> "Inspecting ${selected.label}."
            }
        return EnvironmentResourcePreviewModel(
            mode = mode,
            contentWidth = contentWidth,
            contentHeight = contentHeight,
            items = items,
            canvasPreviewHandle = null,
            drawItemsAsOverlayRegions = false,
            diagnostics = diagnostics,
            selectedItem = selected,
            hoveredItem = hovered,
            statusMessage = status,
        )
    }

    private fun buildCanvasItem(
        environmentManifestPath: String,
        label: String,
        id: String,
        resourceMode: EnvironmentResourceMode,
        manifestPath: String?,
        formatHint: String?,
        regionIndex: Int,
        mipLevel: Int? = null,
        roughness: Float? = null,
        singleTextureCanvas: Boolean = false,
    ): EnvironmentResourceCanvasItem {
        val resolvedPath = manifestPath?.let { relativePath ->
            EnvironmentPathResolver.resolvePath(manifestPath = environmentManifestPath, relativePath = relativePath)
        }
        val file = resolvedPath?.let(::resolveAbsoluteFile)
        val metadata = file?.takeIf(File::isFile)?.let(TextureMetadataReader::read)
        val preview =
            resolvedPath
                ?.takeIf(::isPreviewableTexture)
                ?.let { path ->
                    queueTexture(path)
                    texturePreviewService.preview(path)
                }
        val previewHandle = (preview as? TexturePreviewResult.Available)?.handle
        val warnings =
            buildList {
                if (manifestPath == null) add("No manifest path is defined.")
                if (manifestPath != null && (file == null || !file.exists())) add("File is missing.")
                if (preview is TexturePreviewResult.Unavailable && file?.exists() == true) add(preview.reason)
            }
        val region =
            if (singleTextureCanvas) {
                TexturePreviewRegion(
                    id = id,
                    label = label,
                    x = 0,
                    y = 0,
                    width = (previewHandle?.width ?: metadata?.width ?: 512).coerceAtLeast(1),
                    height = (previewHandle?.height ?: metadata?.height ?: 512).coerceAtLeast(1),
                )
            } else {
                faceGridRegion(
                    id = id,
                    label = label,
                    index = regionIndex,
                    width = previewHandle?.width ?: metadata?.width ?: DefaultTileSize,
                    height = previewHandle?.height ?: metadata?.height ?: DefaultTileSize,
                )
            }
        return EnvironmentResourceCanvasItem(
            id = id,
            label = label,
            resourceMode = resourceMode,
            region = region,
            manifestPath = manifestPath,
            resolvedPath = resolvedPath,
            previewHandle = previewHandle,
            width = previewHandle?.width ?: metadata?.width,
            height = previewHandle?.height ?: metadata?.height,
            format = formatHint ?: manifestPath?.substringAfterLast('.', "").orEmpty().uppercase().ifBlank { null },
            exists = file?.exists() == true,
            mipLevel = mipLevel,
            roughness = roughness,
            warnings = warnings,
        )
    }

    private fun faceGridRegion(
        id: String,
        label: String,
        index: Int,
        width: Int,
        height: Int,
    ): TexturePreviewRegion<String> {
        val column = index % 3
        val row = index / 3
        return TexturePreviewRegion(
            id = id,
            label = label,
            x = FaceGridPadding + column * (DefaultTileSize + FaceGridGap),
            y = FaceGridPadding + row * (DefaultTileSize + FaceGridGap),
            width = width.coerceAtLeast(1),
            height = height.coerceAtLeast(1),
        )
    }

    private fun resolveCubemapFaces(
        environment: Environment,
        cubemap: CubemapResource?,
    ): List<ResolvedEnvironmentFace> {
        val resourcePath = cubemap?.path ?: return emptyList()
        return inferCubemapFaces(environment.manifestPath, resourcePath).map { (face, path) ->
            ResolvedEnvironmentFace(face, path)
        }
    }

    private fun resolveRadianceFaces(
        environment: Environment,
        mip: RadianceMip,
    ): List<ResolvedEnvironmentFace> {
        return inferCubemapFaces(environment.manifestPath, mip.path).map { (face, path) ->
            ResolvedEnvironmentFace(face, path)
        }
    }

    private fun inferCubemapFaces(
        manifestPath: String,
        resourcePath: String,
    ): List<Pair<EnvironmentCubemapFace, String>> {
        val normalized = resourcePath.replace('\\', '/').trim()
        if (normalized.isBlank()) return emptyList()
        return when {
            normalized.contains(FaceToken) ->
                EnvironmentCubemapFace.ordered.map { face ->
                    face to normalized.replace(FaceToken, face.id)
                }
            !normalized.substringAfterLast('/').contains('.') ->
                EnvironmentCubemapFace.ordered.map { face ->
                    face to "${normalized}_${
                        face.id
                    }.png"
                }
            else -> inferSiblingFaceFiles(normalized)
        }
    }

    private fun inferSiblingFaceFiles(resourcePath: String): List<Pair<EnvironmentCubemapFace, String>> {
        val fileName = resourcePath.substringAfterLast('/')
        val parent = resourcePath.substringBeforeLast('/', "")
        val extension = fileName.substringAfterLast('.', "")
        val stem = fileName.substringBeforeLast('.')
        val matchedFace = EnvironmentCubemapFace.fromIdOrAlias(stem.substringAfterLast('_', stem.substringAfterLast('-', stem)))
        if (matchedFace == null) return emptyList()
        val separator = if (stem.endsWith("-${matchedFace.id}")) "-" else "_"
        val baseStem = stem.removeSuffix("$separator${matchedFace.id}")
        val prefix = if (parent.isBlank()) baseStem else "$parent/$baseStem"
        return EnvironmentCubemapFace.ordered.map { face ->
            face to "$prefix$separator${face.id}.$extension"
        }
    }

    private fun resolveAbsoluteFile(relativePath: String): File = File(assetRoot, relativePath)

    private fun isPreviewableTexture(path: String): Boolean = path.substringAfterLast('.', "").lowercase() in PreviewableTextureExtensions

    private data class ResolvedEnvironmentFace(
        val face: EnvironmentCubemapFace,
        val path: String,
    )

    private companion object {
        private const val FaceToken = "{face}"
        private const val DefaultTileSize = 256
        private const val FaceGridPadding = 24
        private const val FaceGridGap = 24
        private const val DefaultCanvasWidth = FaceGridPadding * 2 + DefaultTileSize * 3 + FaceGridGap * 2
        private const val DefaultCanvasHeight = FaceGridPadding * 2 + DefaultTileSize * 2 + FaceGridGap
        private val PreviewableTextureExtensions = setOf("png", "jpg", "jpeg", "webp")
    }
}
