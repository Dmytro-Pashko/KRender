package com.pashkd.krender.engine.tools.skin

import com.pashkd.krender.engine.serialization.KRenderJson
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
                SkinLoadResult(project = project, resourceIndex = buildResourceIndex(project, root), styleIndex = buildStyleIndex(root), problems = problems)
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
    ): SkinResourceIndex {
        val references = linkedMapOf<String, ResourceReference>()
        project.atlasFiles.forEach { file ->
            references[file.nameWithoutExtension] = ResourceReference(name = file.nameWithoutExtension, type = "atlas", source = file.path)
        }
        project.textureFiles.forEach { file ->
            references[file.nameWithoutExtension] = ResourceReference(name = file.nameWithoutExtension, type = "texture", source = file.path)
        }
        project.fontFiles.forEach { file ->
            references[file.nameWithoutExtension] = ResourceReference(name = file.nameWithoutExtension, type = "font", source = file.path)
        }
        root.values
            .filterIsInstance<JsonObject>()
            .forEach { bucket ->
                bucket.forEach { (name, value) ->
                    if (value is JsonPrimitive && value.isString) {
                        references.putIfAbsent(name, inferResourceReference(name, value.content))
                    }
                }
            }
        return SkinResourceIndex(references.values.sortedBy { it.name })
    }

    private fun buildStyleIndex(root: JsonObject): SkinStyleIndex {
        val styles = mutableListOf<StyleInfo>()
        root.forEach { (typeName, value) ->
            val styleGroup = value as? JsonObject ?: return@forEach
            if (!looksLikeStyleGroup(typeName)) return@forEach
            styleGroup.forEach { (styleName, styleValue) ->
                val styleObject = styleValue as? JsonObject ?: return@forEach
                val fields =
                    styleObject.entries.map { (fieldName, fieldValue) ->
                        val rawValue = fieldValue.renderPreview()
                        StyleFieldInfo(
                            name = fieldName,
                            valueType = inferValueType(fieldValue),
                            rawValue = rawValue,
                            reference = inferReference(fieldName, fieldValue),
                        )
                    }
                styles += StyleInfo(name = styleName, type = typeName, fields = fields.sortedBy { it.name })
            }
        }
        return SkinStyleIndex(styles.sortedWith(compareBy(StyleInfo::type, StyleInfo::name)))
    }

    private fun looksLikeStyleGroup(typeName: String): Boolean = typeName.contains("Style", ignoreCase = true)

    private fun inferValueType(value: JsonElement): String =
        when (value) {
            is JsonObject -> "object"
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
    ): ResourceReference? {
        val primitive = value as? JsonPrimitive ?: return null
        if (!primitive.isString) return null
        return inferResourceReference(fieldName, primitive.content)
    }

    private fun inferResourceReference(
        name: String,
        value: String,
    ): ResourceReference {
        val lowered = value.lowercase()
        val type =
            when {
                lowered.endsWith(".atlas") -> "atlas"
                lowered.endsWith(".png") || lowered.endsWith(".jpg") || lowered.endsWith(".jpeg") -> "texture"
                lowered.endsWith(".fnt") || lowered.endsWith(".ttf") || lowered.endsWith(".otf") || name.contains("font", ignoreCase = true) -> "font"
                name.contains("drawable", ignoreCase = true) -> "drawable"
                else -> "reference"
            }
        return ResourceReference(name = name, type = type, source = value)
    }

    private fun JsonElement.renderPreview(): String? =
        when (this) {
            is JsonPrimitive -> content
            is JsonObject -> "{${keys.joinToString()}}"
            else -> toString()
        }
}
