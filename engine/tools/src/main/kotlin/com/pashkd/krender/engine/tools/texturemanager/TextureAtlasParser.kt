package com.pashkd.krender.engine.tools.texturemanager

import java.io.File
import java.nio.charset.StandardCharsets

class TextureAtlasParser {
    /**
     * Parses a libGDX-style text atlas into tolerant page/region structures.
     *
     * Malformed fields become diagnostics instead of throwing. Unknown fields
     * are preserved in the corresponding details maps so the inspector can
     * still surface them in the MVP.
     */
    fun parse(file: File): TextureAtlasDocument {
        val diagnostics = mutableListOf<TextureManagerDiagnostic>()
        return runCatching {
            val pages = mutableListOf<MutablePage>()
            var currentPage: MutablePage? = null
            var currentRegion: MutableRegion? = null
            var expectPageName = true

            file.readLines(StandardCharsets.UTF_8).forEachIndexed { index, rawLine ->
                val lineNumber = index + 1
                val trimmed = rawLine.trim()
                when {
                    trimmed.isEmpty() -> {
                        currentRegion = null
                        if (currentPage != null) {
                            expectPageName = false
                        }
                    }

                    !rawLine.first().isWhitespace() && ':' !in trimmed -> {
                        if (currentPage == null || expectPageName) {
                            currentPage = MutablePage(name = trimmed)
                            pages += currentPage!!
                            currentRegion = null
                            expectPageName = false
                        } else {
                            val pageName = currentPage?.name ?: "<unknown>"
                            currentRegion =
                                MutableRegion(
                                    name = trimmed,
                                    atlasPath = normalizePath(file.path),
                                    pageName = pageName,
                                )
                            currentPage?.regions?.add(currentRegion!!)
                        }
                    }

                    ':' in trimmed -> {
                        val key = trimmed.substringBefore(':').trim()
                        val value = trimmed.substringAfter(':').trim()
                        if (key.isBlank()) return@forEachIndexed
                        if (currentRegion != null) {
                            currentRegion!!.details[key] = value
                        } else if (currentPage != null) {
                            currentPage!!.details[key] = value
                        } else {
                            diagnostics +=
                                TextureManagerDiagnostic(
                                    severity = TextureManagerDiagnosticSeverity.Warning,
                                    category = TextureManagerDiagnosticCategory.Atlas,
                                    message = "Ignored atlas property '$key' before any page declaration.",
                                    source = "${normalizePath(file.path)}:$lineNumber",
                                )
                        }
                    }

                    else -> {
                        diagnostics +=
                            TextureManagerDiagnostic(
                                severity = TextureManagerDiagnosticSeverity.Warning,
                                category = TextureManagerDiagnosticCategory.Atlas,
                                message = "Ignored malformed atlas line '$trimmed'.",
                                source = "${normalizePath(file.path)}:$lineNumber",
                            )
                    }
                }
            }

            if (pages.isEmpty()) {
                diagnostics +=
                    TextureManagerDiagnostic(
                        severity = TextureManagerDiagnosticSeverity.Error,
                        category = TextureManagerDiagnosticCategory.Atlas,
                        message = "Atlas contains no pages.",
                        source = normalizePath(file.path),
                    )
            }

            val immutablePages =
                pages.map { page ->
                    TextureAtlasPage(
                        name = page.name,
                        details = page.details.toMap(),
                    )
                }
            val immutableRegions =
                pages.flatMap { page ->
                    page.regions.map { region ->
                        createRegion(file, page.name, region, diagnostics)
                    }
                }

            immutableRegions
                .groupBy { region -> region.id }
                .filterValues { regions -> regions.size > 1 }
                .forEach { (id, regions) ->
                    diagnostics +=
                        TextureManagerDiagnostic(
                            severity = TextureManagerDiagnosticSeverity.Warning,
                            category = TextureManagerDiagnosticCategory.Atlas,
                            message = "Duplicate region '${id.regionName}' on page '${id.pageName}' (${regions.size} entries).",
                            source = id.atlasPath,
                        )
                }

            TextureAtlasDocument(
                file = file,
                pages = immutablePages,
                regions = immutableRegions,
                diagnostics = diagnostics,
                readable = pages.isNotEmpty(),
            )
        }.getOrElse { error ->
            TextureAtlasDocument(
                file = file,
                readable = false,
                diagnostics =
                    listOf(
                        TextureManagerDiagnostic(
                            severity = TextureManagerDiagnosticSeverity.Error,
                            category = TextureManagerDiagnosticCategory.Atlas,
                            message = "Failed to parse atlas: ${error.message ?: error::class.simpleName ?: "unknown error"}",
                            source = normalizePath(file.path),
                        ),
                    ),
            )
        }
    }

    private fun createRegion(
        file: File,
        pageName: String,
        region: MutableRegion,
        diagnostics: MutableList<TextureManagerDiagnostic>,
    ): TextureAtlasRegion {
        val regionId =
            AtlasRegionId(
                atlasPath = normalizePath(file.path),
                pageName = pageName,
                regionName = region.name,
            )
        val xy = parseIntPair(region.details["xy"], file, regionId, "xy", diagnostics)
        val size = parseIntPair(region.details["size"], file, regionId, "size", diagnostics)
        val orig = parseIntPair(region.details["orig"], file, regionId, "orig", diagnostics)
        val offset = parseIntPair(region.details["offset"], file, regionId, "offset", diagnostics)
        val split = parseIntList(region.details["split"], file, regionId, "split", diagnostics)
        val pad = parseIntList(region.details["pad"], file, regionId, "pad", diagnostics)
        val index = region.details["index"]?.toIntOrNull().also { parsed ->
            if (region.details["index"] != null && parsed == null) {
                diagnostics +=
                    TextureManagerDiagnostic(
                        severity = TextureManagerDiagnosticSeverity.Warning,
                        category = TextureManagerDiagnosticCategory.Atlas,
                        message = "Malformed 'index' for region '${regionId.regionName}'.",
                        source = regionId.atlasPath,
                    )
            }
        }

        if (size == null || size.first <= 0 || size.second <= 0) {
            diagnostics +=
                TextureManagerDiagnostic(
                    severity = TextureManagerDiagnosticSeverity.Warning,
                    category = TextureManagerDiagnosticCategory.Atlas,
                    message = "Region '${regionId.regionName}' has empty or malformed size.",
                    source = regionId.atlasPath,
                )
        }

        return TextureAtlasRegion(
            id = regionId,
            rotate = region.details["rotate"],
            xy = xy,
            size = size,
            orig = orig,
            offset = offset,
            split = split,
            pad = pad,
            index = index,
            details = region.details.toMap(),
        )
    }

    private fun parseIntPair(
        value: String?,
        file: File,
        regionId: AtlasRegionId,
        fieldName: String,
        diagnostics: MutableList<TextureManagerDiagnostic>,
    ): Pair<Int, Int>? {
        if (value == null) return null
        val parts = value.split(',').map(String::trim)
        if (parts.size < 2) {
            diagnostics += malformedRegionField(file, regionId, fieldName)
            return null
        }
        val first = parts[0].toIntOrNull()
        val second = parts[1].toIntOrNull()
        if (first == null || second == null) {
            diagnostics += malformedRegionField(file, regionId, fieldName)
            return null
        }
        return first to second
    }

    private fun parseIntList(
        value: String?,
        file: File,
        regionId: AtlasRegionId,
        fieldName: String,
        diagnostics: MutableList<TextureManagerDiagnostic>,
    ): List<Int> {
        if (value == null) return emptyList()
        val parsed =
            value
                .split(',')
                .map(String::trim)
                .map(String::toIntOrNull)
        if (parsed.any { it == null }) {
            diagnostics += malformedRegionField(file, regionId, fieldName)
            return emptyList()
        }
        return parsed.filterNotNull()
    }

    private fun malformedRegionField(
        file: File,
        regionId: AtlasRegionId,
        fieldName: String,
    ): TextureManagerDiagnostic =
        TextureManagerDiagnostic(
            severity = TextureManagerDiagnosticSeverity.Warning,
            category = TextureManagerDiagnosticCategory.Atlas,
            message = "Malformed '$fieldName' for region '${regionId.regionName}'.",
            source = normalizePath(file.path),
        )

    private data class MutablePage(
        val name: String,
        val details: LinkedHashMap<String, String> = linkedMapOf(),
        val regions: MutableList<MutableRegion> = mutableListOf(),
    )

    private data class MutableRegion(
        val name: String,
        val atlasPath: String,
        val pageName: String,
        val details: LinkedHashMap<String, String> = linkedMapOf(),
    )
}
