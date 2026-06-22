package com.pashkd.krender.engine.tools.skin

import com.pashkd.krender.engine.serialization.KRenderJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.charset.StandardCharsets

class SkinAssetResolver {
    fun resolve(inputPath: String?): SkinProject? {
        val normalized = inputPath?.trim()?.takeIf(String::isNotBlank) ?: return null
        val file = File(normalized)
        if (!file.exists()) return null

        val rootDirectory = if (file.isDirectory) file else file.parentFile ?: file.absoluteFile.parentFile ?: return null
        val descriptorResolution = resolveDescriptor(file, rootDirectory)

        return SkinProject(
            rootDirectory = rootDirectory,
            skinFile = descriptorResolution.selected,
            descriptorCandidates = descriptorResolution.candidates,
            descriptorResolutionMessage = descriptorResolution.message,
            atlasFiles = rootDirectory.listFilesByExtensions("atlas"),
            textureFiles = rootDirectory.listFilesByExtensions("png", "jpg", "jpeg"),
            fontFiles = rootDirectory.listFilesByExtensions("fnt", "ttf", "otf"),
        )
    }

    private fun resolveDescriptor(
        input: File,
        rootDirectory: File,
    ): DescriptorResolution =
        when {
            input.isFile -> {
                if (input.hasSkinDescriptorExtension()) {
                    DescriptorResolution(selected = input, candidates = listOf(input))
                } else {
                    DescriptorResolution(
                        selected = null,
                        candidates = listOf(input),
                        message = "Selected file is not a supported skin descriptor (.json or .uiskin).",
                    )
                }
            }

            else -> {
                val candidates =
                    rootDirectory.listFiles()
                        .orEmpty()
                        .filter { candidate -> candidate.isFile && candidate.hasSkinDescriptorExtension() }
                        .sortedBy { it.name.lowercase() }
                val prioritized =
                    DescriptorPriorityNames
                        .firstNotNullOfOrNull { expected -> candidates.firstOrNull { candidate -> candidate.name.equals(expected, ignoreCase = true) } }
                when {
                    prioritized != null -> DescriptorResolution(selected = prioritized, candidates = candidates)
                    candidates.size == 1 -> DescriptorResolution(selected = candidates.single(), candidates = candidates)
                    candidates.size > 1 ->
                        DescriptorResolution(
                            selected = null,
                            candidates = candidates,
                            message = "Multiple skin descriptors were found; select one explicitly.",
                        )

                    else -> DescriptorResolution(selected = null, candidates = emptyList())
                }
            }
        }

    private fun File.listFilesByExtensions(vararg extensions: String): List<File> =
        listFiles()
            .orEmpty()
            .filter { file -> file.isFile && extensions.any { ext -> file.extension.equals(ext, ignoreCase = true) } }
            .sortedBy { it.name }

    private fun File.hasSkinDescriptorExtension(): Boolean =
        extension.equals("json", ignoreCase = true) || extension.equals("uiskin", ignoreCase = true)

    private data class DescriptorResolution(
        val selected: File?,
        val candidates: List<File>,
        val message: String? = null,
    )

    private companion object {
        private val DescriptorPriorityNames =
            listOf(
                "skin.uiskin",
                "skin.json",
                "default.uiskin",
                "default.json",
            )
    }
}

class SkinProjectLoader {
    private val atlasParser = SkinAtlasParser()
    private val bitmapFontParser = SkinBitmapFontParser()

    fun inspect(project: SkinProject?): SkinLoadResult {
        if (project == null) {
            return SkinLoadResult(
                problems =
                    listOf(
                        SkinProblem(
                            severity = SkinProblemSeverity.Info,
                            category = SkinProblemCategory.Project,
                            message = "No skin path selected.",
                            suggestedFix = "Open a Scene2D skin JSON file or a directory that contains one.",
                        ),
                    ),
            )
        }

        val problems = mutableListOf<SkinProblem>()
        val skinFile = project.skinFile
        project.descriptorResolutionMessage?.let { message ->
            problems +=
                SkinProblem(
                    severity = SkinProblemSeverity.Warning,
                    category = SkinProblemCategory.Project,
                    message = message,
                    source = project.rootDirectory.path,
                    suggestedFix = project.descriptorCandidates.takeIf(List<File>::isNotEmpty)?.joinToString { it.name },
                )
        }
        if (skinFile == null) {
            problems +=
                SkinProblem(
                    severity = SkinProblemSeverity.Warning,
                    category = SkinProblemCategory.Project,
                    message =
                        if (project.descriptorCandidates.isEmpty()) {
                            "No supported skin descriptor (.json or .uiskin) was discovered in the selected location."
                        } else {
                            "No skin descriptor was selected for preview loading."
                        },
                    source = project.rootDirectory.path,
                )
            return SkinLoadResult(project = project, problems = problems)
        }
        if (!skinFile.exists()) {
            problems +=
                SkinProblem(
                    severity = SkinProblemSeverity.Error,
                    category = SkinProblemCategory.Loading,
                    message = "Skin file does not exist.",
                    source = skinFile.path,
                )
            return SkinLoadResult(project = project, problems = problems)
        }

        return try {
            val root = KRenderJson.Pretty.parseToJsonElement(skinFile.readText(StandardCharsets.UTF_8)) as? JsonObject
            if (root == null) {
                SkinLoadResult(
                    project = project,
                    problems =
                        problems + SkinProblem(
                            severity = SkinProblemSeverity.Error,
                            category = SkinProblemCategory.Loading,
                            message = "Skin root must be a JSON object.",
                            source = skinFile.path,
                        ),
                )
            } else {
                val rawStyleIndex = buildStyleIndex(root)
                val resourceIndex = buildResourceIndex(project, root, rawStyleIndex)
                SkinLoadResult(
                    project = project,
                    resourceIndex = resourceIndex,
                    styleIndex = resolveStyleReferences(rawStyleIndex, resourceIndex),
                    problems = problems,
                )
            }
        } catch (error: Exception) {
            SkinLoadResult(
                project = project,
                problems =
                    problems + SkinProblem(
                        severity = SkinProblemSeverity.Error,
                        category = SkinProblemCategory.Loading,
                        message = error.message ?: error::class.simpleName ?: "Unknown skin parse error.",
                        source = skinFile.path,
                    ),
            )
        }
    }

    private fun buildResourceIndex(
        project: SkinProject,
        root: JsonObject,
        styleIndex: SkinStyleIndex,
    ): SkinResourceIndex {
        val resources = linkedMapOf<SkinResourceKey, SkinResourceInfo>()

        fun addResource(resource: SkinResourceInfo) {
            val existing = resources[resource.key]
            resources[resource.key] =
                if (existing == null) {
                    resource
                } else {
                    existing.copy(
                        source = existing.source ?: resource.source,
                        referencedBy = (existing.referencedBy + resource.referencedBy).distinct().sorted(),
                        resolved = existing.resolved || resource.resolved,
                        details = existing.details + resource.details,
                    )
                }
        }

        project.atlasFiles.forEach { file ->
            val atlas = atlasParser.parse(file)
            addResource(
                file.toResourceInfo(SkinResourceCategory.Atlas).copy(
                    details =
                        file.fileDetails() +
                            mapOf(
                                "origin" to "file",
                                "pageCount" to atlas.pages.size.toString(),
                                "regionCount" to atlas.regions.size.toString(),
                                "pages" to atlas.pages.joinToString { page -> page.name },
                                "readable" to atlas.readable.toString(),
                            ),
                ),
            )
            atlas.regions.forEach { region ->
                addResource(
                    SkinResourceInfo(
                        name = region.name,
                        category = SkinResourceCategory.AtlasRegion,
                        type = "AtlasRegion",
                        source = file.path,
                        details =
                            mapOf(
                                "origin" to "atlas",
                                "atlas" to file.name,
                                "page" to region.page.orEmpty(),
                            ) + region.details,
                    ),
                )
            }
        }
        project.textureFiles.forEach { file ->
            addResource(file.toResourceInfo(SkinResourceCategory.Texture))
        }
        project.fontFiles.forEach { file ->
            addResource(file.toResourceInfo(SkinResourceCategory.Font))
        }

        root.forEach { (rawTypeName, value) ->
            val category = resourceCategoryForType(rawTypeName) ?: return@forEach
            val bucket = value as? JsonObject ?: return@forEach
            bucket.forEach { (name, resourceValue) ->
                addResource(
                    SkinResourceInfo(
                        name = name,
                        category = category,
                        type = normalizeSkinTypeName(rawTypeName),
                        source = "${project.skinFile?.path}#$rawTypeName.$name",
                        details = resourceDetails(rawTypeName, category, resourceValue),
                    ),
                )
            }
        }

        matchFontFiles(resources, project.fontFiles)

        styleIndex.styles.forEach { style ->
            style.fields.forEach { field ->
                val reference = field.reference ?: return@forEach
                val referencedBy = "${style.displayName}.${field.name}"
                val matchingKeys =
                    matchingResourceKeys(
                        resources = resources,
                        category = reference.category,
                        name = reference.name,
                    )
                matchingKeys.forEach { key ->
                    resources[key]?.let { resource ->
                        val resolutionDetails =
                            if (reference.category == SkinResourceCategory.Drawable && key.category != SkinResourceCategory.Drawable) {
                                mapOf("resolvesDrawableAs" to key.category.name)
                            } else {
                                emptyMap()
                            }
                        resources[key] =
                            resource.copy(
                                referencedBy = (resource.referencedBy + referencedBy).distinct().sorted(),
                                details = resource.details + resolutionDetails,
                            )
                    }
                }
                if (matchingKeys.isEmpty()) {
                    val category = reference.category ?: SkinResourceCategory.Unknown
                    addResource(
                        SkinResourceInfo(
                            name = reference.name,
                            category = category,
                            type = "${category.name}Reference",
                            source = reference.source,
                            referencedBy = listOf(referencedBy),
                            resolved = false,
                            details =
                                mapOf(
                                    "origin" to "style-reference",
                                    "expectedCategory" to category.name,
                                ),
                        ),
                    )
                }
            }
        }

        fun resourcesFor(category: SkinResourceCategory): List<SkinResourceInfo> =
            resources.values
                .filter { resource -> resource.category == category }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, SkinResourceInfo::name))

        return SkinResourceIndex(
            colors = resourcesFor(SkinResourceCategory.Color),
            fonts = resourcesFor(SkinResourceCategory.Font),
            drawables = resourcesFor(SkinResourceCategory.Drawable),
            atlasRegions = resourcesFor(SkinResourceCategory.AtlasRegion),
            textures = resourcesFor(SkinResourceCategory.Texture),
            atlasFiles = resourcesFor(SkinResourceCategory.Atlas),
            unknownReferences = resourcesFor(SkinResourceCategory.Unknown),
        )
    }

    private fun buildStyleIndex(root: JsonObject): SkinStyleIndex {
        val styles = mutableListOf<StyleInfo>()
        root.forEach { (rawTypeName, value) ->
            val styleGroup = value as? JsonObject ?: return@forEach
            if (!isStyleType(rawTypeName)) return@forEach
            val typeName = normalizeSkinTypeName(rawTypeName)
            styleGroup.forEach { (styleName, styleValue) ->
                val styleObject = styleValue as? JsonObject ?: return@forEach
                val fields =
                    styleObject.entries.map { (fieldName, fieldValue) ->
                        val rawValue = fieldValue.renderPreview()
                        StyleFieldInfo(
                            name = fieldName,
                            valueType = inferValueType(fieldValue),
                            rawValue = rawValue,
                            reference =
                                inferReference(
                                    fieldName = fieldName,
                                    value = fieldValue,
                                    source = "$typeName.$styleName.$fieldName",
                                ),
                        )
                    }
                val sortedFields = fields.sortedBy { it.name }
                styles +=
                    StyleInfo(
                        name = styleName,
                        type = typeName,
                        fields = sortedFields,
                        resourceReferences = sortedFields.mapNotNull(StyleFieldInfo::reference),
                        rawFieldCount = styleObject.size,
                    )
            }
        }
        return SkinStyleIndex(styles.sortedWith(compareBy(StyleInfo::type, StyleInfo::name)))
    }

    private fun resolveStyleReferences(
        styleIndex: SkinStyleIndex,
        resourceIndex: SkinResourceIndex,
    ): SkinStyleIndex =
        SkinStyleIndex(
            styles =
                styleIndex.styles.map { style ->
                    val fields =
                        style.fields.map { field ->
                            field.copy(reference = field.reference?.let { reference -> reference.copy(resolved = resourceIndex.resolves(reference)) })
                        }
                    style.copy(
                        fields = fields,
                        resourceReferences = fields.mapNotNull(StyleFieldInfo::reference),
                    )
                },
        )

    internal fun normalizeSkinTypeName(rawTypeName: String): String =
        rawTypeName
            .substringAfterLast('.')
            .substringAfterLast('$')

    internal fun isStyleType(typeName: String): Boolean {
        val normalized = normalizeSkinTypeName(typeName)
        return normalized in KnownStyleTypes || normalized.contains("Style", ignoreCase = true)
    }

    internal fun resourceCategoryForType(typeName: String): SkinResourceCategory? {
        val normalized = normalizeSkinTypeName(typeName)
        return when {
            normalized.equals("Color", ignoreCase = true) -> SkinResourceCategory.Color
            normalized.equals("BitmapFont", ignoreCase = true) -> SkinResourceCategory.Font
            normalized.equals("AtlasRegion", ignoreCase = true) -> SkinResourceCategory.AtlasRegion
            normalized.contains("Drawable", ignoreCase = true) -> SkinResourceCategory.Drawable
            normalized in DrawableResourceTypes -> SkinResourceCategory.Drawable
            normalized.equals("Texture", ignoreCase = true) -> SkinResourceCategory.Texture
            else -> null
        }
    }

    private fun inferValueType(value: JsonElement): String =
        when (value) {
            is JsonObject -> "object"
            is JsonArray -> "array"
            is JsonPrimitive ->
                when {
                    value.isString -> "string"
                    value.content.equals("true", ignoreCase = true) || value.content.equals("false", ignoreCase = true) -> "boolean"
                    value.content.toLongOrNull() != null -> "integer"
                    value.content.toDoubleOrNull() != null -> "number"
                    else -> "primitive"
                }

            else -> value::class.simpleName ?: "value"
        }

    private fun inferReference(
        fieldName: String,
        value: JsonElement,
        source: String,
    ): ResourceReference? {
        val primitive = value as? JsonPrimitive ?: return null
        if (!primitive.isString) return null
        val category = resourceCategoryForField(fieldName) ?: return null
        return ResourceReference(
            name = primitive.content,
            category = category,
            source = source,
        )
    }

    private fun resourceCategoryForField(fieldName: String): SkinResourceCategory? {
        val lowered = fieldName.lowercase()
        return when {
            lowered.contains("font") && !lowered.contains("color") -> SkinResourceCategory.Font
            lowered.contains("color") -> SkinResourceCategory.Color
            lowered in DrawableFieldNames || lowered.endsWith("drawable") -> SkinResourceCategory.Drawable
            lowered.contains("texture") -> SkinResourceCategory.Texture
            else -> null
        }
    }

    private fun matchingResourceKeys(
        resources: Map<SkinResourceKey, SkinResourceInfo>,
        category: SkinResourceCategory?,
        name: String,
    ): List<SkinResourceKey> {
        val acceptedCategories =
            when (category) {
                SkinResourceCategory.Drawable ->
                    setOf(
                        SkinResourceCategory.Drawable,
                        SkinResourceCategory.AtlasRegion,
                        SkinResourceCategory.Texture,
                    )

                null -> SkinResourceCategory.entries.toSet()
                else -> setOf(category)
            }
        return resources.keys.filter { key -> key.name == name && key.category in acceptedCategories }
    }

    private fun File.toResourceInfo(category: SkinResourceCategory): SkinResourceInfo =
        SkinResourceInfo(
            name = if (category == SkinResourceCategory.Atlas) name else nameWithoutExtension,
            category = category,
            type = extension.lowercase(),
            source = path,
            details = mapOf("origin" to "file") + fileDetails(),
        )

    private fun resourceDetails(
        rawTypeName: String,
        category: SkinResourceCategory,
        value: JsonElement,
    ): Map<String, String> =
        buildMap {
            put("origin", "skin")
            put("rawType", rawTypeName)
            put("rawValue", value.toString())
            if (category == SkinResourceCategory.Font) {
                put("declaredInSkin", "true")
            }
            when (value) {
                is JsonPrimitive -> put("value", value.content)
                is JsonObject -> {
                    value["file"]?.renderPreview()?.let { file -> put("file", file) }
                    if (category == SkinResourceCategory.Color) {
                        listOf("r", "g", "b", "a", "hex").forEach { field ->
                            value[field]?.renderPreview()?.let { fieldValue -> put(field, fieldValue) }
                        }
                    }
                }

                else -> Unit
            }
        }

    private fun matchFontFiles(
        resources: MutableMap<SkinResourceKey, SkinResourceInfo>,
        fontFiles: List<File>,
    ) {
        resources.keys
            .filter { key -> key.category == SkinResourceCategory.Font }
            .forEach { key ->
                val resource = resources[key] ?: return@forEach
                val declaredFile = resource.details["file"]?.let(::File)?.name
                val match =
                    fontFiles.firstOrNull { file ->
                        file.nameWithoutExtension.equals(resource.name, ignoreCase = true) ||
                            declaredFile?.equals(file.name, ignoreCase = true) == true ||
                            declaredFile?.let { File(it).nameWithoutExtension.equals(file.nameWithoutExtension, ignoreCase = true) } == true
                    }
                resources[key] =
                    resource.copy(
                        details =
                            resource.details +
                                if (match != null) {
                                    val previewDetails =
                                        when {
                                            match.extension.equals("fnt", ignoreCase = true) -> parseBitmapFontDetails(match)
                                            else -> emptyMap()
                                        }
                                    match.fileDetails().mapKeys { (name, _) -> "matchedFile${name.replaceFirstChar(Char::uppercase)}" } +
                                        mapOf(
                                            "matchedFile" to match.path,
                                            "fontPreviewAvailable" to match.extension.equals("fnt", ignoreCase = true).toString(),
                                        ) +
                                        previewDetails
                                } else {
                                    mapOf(
                                        "matchedFile" to "<none>",
                                        "fontPreviewAvailable" to "false",
                                    )
                                },
                    )
            }
    }

    private fun File.fileDetails(): Map<String, String> =
        mapOf(
            "extension" to extension.lowercase(),
            "sizeBytes" to length().toString(),
            "discoveredFile" to "true",
        )

    private fun parseBitmapFontDetails(file: File): Map<String, String> {
        val info = bitmapFontParser.parse(file)
        return buildMap {
            put("fntReadable", info.readable.toString())
            info.face?.let { put("fntFace", it) }
            info.size?.let { put("fntSize", it) }
            info.lineHeight?.let { put("fntLineHeight", it) }
            info.base?.let { put("fntBase", it) }
            info.pages?.let { put("fntPages", it) }
            info.charCount?.let { put("fntCharCount", it.toString()) }
            info.asciiGlyphCoverage?.let { put("asciiGlyphCoverage", it) }
            info.ukrainianGlyphCoverage?.let { put("ukrainianGlyphCoverage", it) }
            info.missingUkrainianGlyphs?.let { put("missingUkrainianGlyphs", it) }
        }
    }

    private fun JsonElement.renderPreview(): String? =
        when (this) {
            is JsonPrimitive -> content
            is JsonObject -> "{${keys.joinToString()}}"
            else -> toString()
        }

    private companion object {
        private val KnownStyleTypes =
            setOf(
                "LabelStyle",
                "TextButtonStyle",
                "ImageButtonStyle",
                "ButtonStyle",
                "CheckBoxStyle",
                "TextFieldStyle",
                "TextAreaStyle",
                "ListStyle",
                "SelectBoxStyle",
                "ScrollPaneStyle",
                "SliderStyle",
                "ProgressBarStyle",
                "WindowStyle",
                "TreeStyle",
                "TooltipStyle",
            )

        private val DrawableResourceTypes =
            setOf(
                "NinePatch",
                "Sprite",
                "TextureRegion",
            )

        private val DrawableFieldNames =
            setOf(
                "up",
                "down",
                "over",
                "checked",
                "disabled",
                "background",
                "selection",
                "cursor",
                "knob",
                "knobbefore",
                "knobafter",
                "vscroll",
                "vscrollknob",
                "hscroll",
                "hscrollknob",
                "checkboxon",
                "checkboxoff",
            )
    }
}
