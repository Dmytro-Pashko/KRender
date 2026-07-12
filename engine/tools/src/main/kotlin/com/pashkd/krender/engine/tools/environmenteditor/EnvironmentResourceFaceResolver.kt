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
    ): EnvironmentResourcePreviewModel =
        when (mode) {
            EnvironmentResourceMode.Skybox -> buildSkyboxModel(environment, selectedItemId, hoveredItemId)
            EnvironmentResourceMode.Irradiance -> buildIrradianceModel(environment, selectedItemId, hoveredItemId)
            EnvironmentResourceMode.Radiance -> buildRadianceModel(environment, selectedMipLevel, selectedItemId, hoveredItemId)
            EnvironmentResourceMode.BrdfLut -> buildBrdfLutModel(environment, selectedItemId, hoveredItemId)
            EnvironmentResourceMode.ImportedSkyboxSource ->
                buildImportedSourceModel(
                    importState = skyboxImportState,
                    selectedItemId = selectedItemId,
                    hoveredItemId = hoveredItemId,
                )
        }

    private fun buildSkyboxModel(
        environment: Environment,
        selectedItemId: String?,
        hoveredItemId: String?,
    ): EnvironmentResourcePreviewModel {
        val skybox = environment.skybox
        val items =
            if (skybox == null || skybox.faces.isEmpty()) {
                emptyList()
            } else {
                EnvironmentCubemapFace.ordered.mapNotNull { face ->
                    val path =
                        skybox.faces
                            .entries
                            .firstOrNull { (key, _) -> EnvironmentCubemapFace.fromIdOrAlias(key) == face }
                            ?.value
                            ?: return@mapNotNull null
                    buildCanvasItem(
                        environmentManifestPath = environment.manifestPath,
                        label = face.id,
                        id = face.id,
                        resourceMode = EnvironmentResourceMode.Skybox,
                        manifestPath = path,
                        formatHint = skybox.format,
                        regionIndex = EnvironmentCubemapFace.ordered.indexOf(face),
                    )
                }
            }
        val diagnostics =
            buildList {
                if (skybox == null) add("Skybox resource set is not defined.")
                if (skybox != null && skybox.faces.isEmpty()) add("Skybox face list is empty.")
                if (skybox != null && items.any { !it.exists }) add("One or more skybox face files are missing.")
            }
        return buildModel(EnvironmentResourceMode.Skybox, items, selectedItemId, hoveredItemId, diagnostics)
    }

    private fun buildIrradianceModel(
        environment: Environment,
        selectedItemId: String?,
        hoveredItemId: String?,
    ): EnvironmentResourcePreviewModel {
        val items =
            resolveCubemapFaces(environment.irradiance)
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
            mip
                ?.let { selectedMip ->
                    resolveRadianceFaces(selectedMip)
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

    @Suppress("LongMethod")
    private fun buildImportedSourceModel(
        importState: SkyboxImportState?,
        selectedItemId: String? = null,
        hoveredItemId: String? = null,
    ): EnvironmentResourcePreviewModel {
        val sourcePath = importState?.sourcePath?.takeIf(String::isNotBlank)
        val preview =
            sourcePath
                ?.takeIf(::isPreviewableTexture)
                ?.let { path ->
                    queueTextureSafely(path)
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
        val positionedItems = if (singleTextureCanvas) items else layoutFaceGrid(items)
        val contentWidth = if (singleTextureCanvas) positionedItems.firstOrNull()?.region?.width ?: 1 else computeContentWidth(positionedItems)
        val contentHeight = if (singleTextureCanvas) positionedItems.firstOrNull()?.region?.height ?: 1 else computeContentHeight(positionedItems)
        val selected = positionedItems.firstOrNull { it.id == selectedItemId } ?: positionedItems.firstOrNull()
        val hovered = positionedItems.firstOrNull { it.id == hoveredItemId }
        val status =
            when {
                positionedItems.isEmpty() -> "No previewable resources are available for ${mode.label()}."
                selected == null -> "Select a resource face to inspect it."
                selected.previewHandle == null && selected.exists -> "Preview handle is not available for '${selected.label}' yet."
                selected.previewHandle == null -> "Resource '${selected.label}' is missing."
                else -> "Inspecting ${selected.label}."
            }
        return EnvironmentResourcePreviewModel(
            mode = mode,
            contentWidth = contentWidth,
            contentHeight = contentHeight,
            items = positionedItems,
            canvasPreviewHandle = null,
            drawItemsAsOverlayRegions = false,
            diagnostics = diagnostics,
            selectedItem = selected,
            hoveredItem = hovered,
            statusMessage = status,
        )
    }

    @Suppress("LongParameterList", "CyclomaticComplexMethod")
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
        val resolvedPath =
            manifestPath?.let { relativePath ->
                EnvironmentPathResolver.resolvePath(manifestPath = environmentManifestPath, relativePath = relativePath)
            }
        val file = resolvedPath?.let(::resolveAbsoluteFile)
        val metadata = file?.takeIf(File::isFile)?.let(TextureMetadataReader::read)
        val preview =
            resolvedPath
                ?.takeIf(::isPreviewableTexture)
                ?.let { path ->
                    queueTextureSafely(path)
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
                faceGridRegion(id = id, label = label, index = regionIndex, width = previewHandle?.width ?: metadata?.width ?: DefaultTileSize, height = previewHandle?.height ?: metadata?.height ?: DefaultTileSize)
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
            format =
                formatHint ?: manifestPath
                    ?.substringAfterLast('.', "")
                    .orEmpty()
                    .uppercase()
                    .ifBlank { null },
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
    ): TexturePreviewRegion<String> =
        TexturePreviewRegion(
            id = id,
            label = label,
            x = FaceGridPadding,
            y = FaceGridPadding + index * FaceGridGap,
            width = width.coerceAtLeast(1),
            height = height.coerceAtLeast(1),
        )

    private fun layoutFaceGrid(items: List<EnvironmentResourceCanvasItem>): List<EnvironmentResourceCanvasItem> {
        if (items.isEmpty()) return items
        val cellWidth = items.maxOf { it.region.width }.coerceAtLeast(1)
        val cellHeight = items.maxOf { it.region.height }.coerceAtLeast(1)
        return items.mapIndexed { index, item ->
            val column = index % FaceGridColumns
            val row = index / FaceGridColumns
            item.copy(
                region =
                    item.region.copy(
                        x = FaceGridPadding + column * (cellWidth + FaceGridGap),
                        y = FaceGridPadding + row * (cellHeight + FaceGridGap),
                    ),
            )
        }
    }

    private fun computeContentWidth(items: List<EnvironmentResourceCanvasItem>): Int = items.maxOfOrNull { it.region.x + it.region.width + FaceGridPadding } ?: 1

    private fun computeContentHeight(items: List<EnvironmentResourceCanvasItem>): Int = items.maxOfOrNull { it.region.y + it.region.height + FaceGridPadding } ?: 1

    private fun resolveCubemapFaces(cubemap: CubemapResource?): List<ResolvedEnvironmentFace> {
        val resourcePath = cubemap?.path ?: return emptyList()
        return inferEnvironmentCubemapFacePaths(
            resourcePath = resourcePath,
            directoryStem = directoryStem(resourcePath, "irradiance"),
        ).map { (face, path) ->
            ResolvedEnvironmentFace(face, path)
        }
    }

    private fun resolveRadianceFaces(mip: RadianceMip): List<ResolvedEnvironmentFace> =
        inferEnvironmentCubemapFacePaths(
            resourcePath = mip.path,
            directoryStem = directoryStem(mip.path, "radiance_${mip.level}"),
        ).map { (face, path) ->
            ResolvedEnvironmentFace(face, path)
        }

    private fun resolveAbsoluteFile(relativePath: String): File = File(assetRoot, relativePath)

    private fun isPreviewableTexture(path: String): Boolean = path.substringAfterLast('.', "").lowercase() in PreviewableTextureExtensions

    private fun queueTextureSafely(path: String) {
        runCatching { queueTexture(path) }
    }

    private data class ResolvedEnvironmentFace(
        val face: EnvironmentCubemapFace,
        val path: String,
    )

    private companion object {
        private const val DefaultTileSize = 256
        private const val FaceGridColumns = 3
        private const val FaceGridPadding = 24
        private const val FaceGridGap = 24
        private val PreviewableTextureExtensions = setOf("png", "jpg", "jpeg", "webp")
    }
}

internal fun inferEnvironmentCubemapFacePaths(
    resourcePath: String,
    directoryStem: String,
): List<Pair<EnvironmentCubemapFace, String>> {
    val normalized = resourcePath.replace('\\', '/').trim()
    if (normalized.isBlank()) return emptyList()
    val fileName = normalized.substringAfterLast('/')
    val parent = normalized.substringBeforeLast('/', "")
    return when {
        normalized.contains(ENVIRONMENT_CUBEMAP_FACE_TOKEN) ->
            EnvironmentCubemapFace.ordered.map { face ->
                face to normalized.replace(ENVIRONMENT_CUBEMAP_FACE_TOKEN, face.id)
            }
        !fileName.contains('.') ->
            EnvironmentCubemapFace.ordered.map { face ->
                val prefix = listOfNotNull(parent.takeIf(String::isNotBlank), fileName, directoryStem).joinToString("/")
                face to "${prefix}_${face.id}.png"
            }
        else -> inferSiblingEnvironmentFaceFiles(normalized)
    }
}

internal fun directoryStem(
    resourcePath: String,
    fallback: String,
): String {
    val normalized = resourcePath.replace('\\', '/').trim().trimEnd('/')
    val lastSegment = normalized.substringAfterLast('/', "")
    return lastSegment.ifBlank { fallback }
}

private fun inferSiblingEnvironmentFaceFiles(resourcePath: String): List<Pair<EnvironmentCubemapFace, String>> {
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

private const val ENVIRONMENT_CUBEMAP_FACE_TOKEN = "{face}"
