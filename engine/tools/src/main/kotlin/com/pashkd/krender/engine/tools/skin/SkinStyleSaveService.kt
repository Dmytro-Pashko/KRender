package com.pashkd.krender.engine.tools.skin

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.scene.SceneFileService
import com.pashkd.krender.engine.serialization.KRenderJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

data class SkinStyleSaveResult(
    val success: Boolean,
    val file: File? = null,
    val backupFile: File? = null,
    val savedChangeCount: Int = 0,
    val message: String,
    val error: Throwable? = null,
)

class SkinStyleSaveService(
    private val logger: Logger,
    private val files: SceneFileService,
) {
    private val loader = SkinProjectLoader()

    @Suppress("ReturnCount")
    fun save(
        project: SkinProject?,
        loadResult: SkinLoadResult,
        editSession: SkinEditSession,
    ): SkinStyleSaveResult {
        val skinFile =
            project?.skinFile
                ?: return SkinStyleSaveResult(success = false, message = "No skin file is loaded.")
        if (!skinFile.exists()) {
            return SkinStyleSaveResult(success = false, file = skinFile, message = "Skin file does not exist.")
        }
        val snapshot = editSession.toEditedSnapshot()
        if (!snapshot.dirty || snapshot.changes.isEmpty()) {
            return SkinStyleSaveResult(success = false, file = skinFile, message = "No draft style changes to save.")
        }

        return try {
            val originalText = files.readText(skinFile.path)
            val root =
                KRenderJson.Pretty.parseToJsonElement(originalText) as? JsonObject
                    ?: return SkinStyleSaveResult(
                        success = false,
                        file = skinFile,
                        message = "Skin root must be a JSON object.",
                    )
            val backupFile = createBackup(skinFile)
            val updatedRoot = applySnapshot(root, snapshot, loadResult)
            val encoded = KRenderJson.Pretty.encodeToString(JsonObject.serializer(), updatedRoot)
            files.writeText(skinFile.path, encoded)
            logger.info(TAG) {
                "Saved Skin Editor draft changes file='${skinFile.path}' changes=${snapshot.changes.size} backup='${backupFile.path}'"
            }
            SkinStyleSaveResult(
                success = true,
                file = skinFile,
                backupFile = backupFile,
                savedChangeCount = snapshot.changes.size,
                message = "Saved style changes to ${skinFile.name}. Backup: ${backupFile.name}.",
            )
        } catch (error: Exception) {
            logger.error(TAG, error) { "Failed to save Skin Editor draft changes: ${error.message}" }
            SkinStyleSaveResult(
                success = false,
                file = skinFile,
                savedChangeCount = snapshot.changes.size,
                message = "Failed to save style changes: ${error.message ?: error::class.simpleName ?: "Unknown error."}",
                error = error,
            )
        }
    }

    private fun createBackup(skinFile: File): File {
        val backupFile = File("${skinFile.path}.bak")
        try {
            skinFile.copyTo(backupFile, overwrite = true)
        } catch (error: Exception) {
            throw IllegalStateException("Could not create backup '${backupFile.name}': ${error.message}", error)
        }
        return backupFile
    }

    private fun applySnapshot(
        root: JsonObject,
        snapshot: SkinEditedSnapshot,
        loadResult: SkinLoadResult,
    ): JsonObject {
        val updatedRoot = LinkedHashMap(root)
        snapshot.deletedStyles.forEach { style ->
            removeStyle(updatedRoot, style)
        }
        snapshot.styles.forEach { style ->
            applyStyle(updatedRoot, style)
        }
        snapshot.deletedResources.forEach { resource ->
            removeResource(updatedRoot, resource)
        }
        snapshot.resources
            .filter { resource -> resource.key.category == SkinResourceCategory.Color }
            .forEach { resource ->
                applyColorResource(updatedRoot, resource, loadResult)
            }
        return JsonObject(updatedRoot)
    }

    private fun applyStyle(
        root: MutableMap<String, JsonElement>,
        style: EditableStyle,
    ) {
        val sectionKey = findOrCreateStyleSectionKey(root, style.key.type)
        val section = root[sectionKey] as? JsonObject ?: JsonObject(emptyMap())
        val updatedSection = LinkedHashMap(section)
        style.sourceKey
            ?.takeIf { sourceKey ->
                !style.createdInEditor &&
                    sourceKey.type == style.key.type &&
                    sourceKey.name != style.key.name
            }?.let { sourceKey -> updatedSection.remove(sourceKey.name) }
        val existingStyleObject =
            (section[style.key.name] as? JsonObject)
                ?: style.sourceKey?.let { sourceKey -> section[sourceKey.name] as? JsonObject }
                ?: JsonObject(emptyMap())
        updatedSection[style.key.name] = buildStyleObject(style, existingStyleObject)
        root[sectionKey] = JsonObject(updatedSection)
    }

    @Suppress("ReturnCount")
    private fun removeStyle(
        root: MutableMap<String, JsonElement>,
        style: EditableStyle,
    ) {
        if (style.createdInEditor) return
        val targetKey = style.sourceKey ?: style.key
        val sectionKey = findExistingSectionKey(root, targetKey.type) ?: return
        val section = root[sectionKey] as? JsonObject ?: return
        val updatedSection = LinkedHashMap(section)
        updatedSection.remove(targetKey.name)
        if (updatedSection.isEmpty()) {
            root.remove(sectionKey)
        } else {
            root[sectionKey] = JsonObject(updatedSection)
        }
    }

    private fun applyColorResource(
        root: MutableMap<String, JsonElement>,
        resource: EditableResource,
        loadResult: SkinLoadResult,
    ) {
        val sectionKey = findOrCreateResourceSectionKey(root, ColorSectionTypeName)
        val section = root[sectionKey] as? JsonObject ?: JsonObject(emptyMap())
        val updatedSection = LinkedHashMap(section)
        val originalValue = section[resource.key.name]
        val resourceInfo = loadResult.resourceIndex.colors.firstOrNull { info -> info.key == resource.key }
        updatedSection[resource.key.name] = buildColorElement(resource, originalValue, resourceInfo)
        root[sectionKey] = JsonObject(updatedSection)
    }

    private fun removeResource(
        root: MutableMap<String, JsonElement>,
        resource: EditableResource,
    ) {
        val sectionKey = findExistingSectionKey(root, resource.key.category.sectionTypeName()) ?: return
        val section = root[sectionKey] as? JsonObject ?: return
        val updatedSection = LinkedHashMap(section)
        updatedSection.remove(resource.key.name)
        if (updatedSection.isEmpty()) {
            root.remove(sectionKey)
        } else {
            root[sectionKey] = JsonObject(updatedSection)
        }
    }

    private fun buildStyleObject(
        style: EditableStyle,
        existingStyleObject: JsonObject,
    ): JsonObject {
        val ordered = linkedMapOf<String, JsonElement>()
        val handled = linkedSetOf<String>()
        existingStyleObject.keys.forEach { fieldName ->
            val field = style.fields[fieldName] ?: return@forEach
            ordered[fieldName] = fieldToJson(field)
            handled += fieldName
        }
        style.fields.values.forEach { field ->
            if (field.name in handled) return@forEach
            ordered[field.name] = fieldToJson(field)
        }
        return JsonObject(ordered)
    }

    @Suppress("ReturnCount")
    private fun buildColorElement(
        resource: EditableResource,
        existingValue: JsonElement?,
        resourceInfo: SkinResourceInfo?,
    ): JsonElement {
        val preferObject =
            existingValue is JsonObject ||
                listOf("r", "g", "b").all { channel -> channel in resource.values } ||
                listOf("r", "g", "b").all { channel -> channel in resource.originalValues } ||
                resourceInfo?.details?.containsKey("r") == true
        if (!preferObject) {
            return JsonPrimitive(resource.values["hex"] ?: resource.values["value"] ?: "")
        }
        val objectFields = linkedMapOf<String, JsonElement>()
        listOf("r", "g", "b", "a").forEach { channel ->
            resource.values[channel]?.takeIf(String::isNotBlank)?.let { value ->
                objectFields[channel] = scalarToJson(value, preserveColorHexString = false)
            }
        }
        if (objectFields.isEmpty()) {
            val fallback = resource.values["hex"] ?: resource.values["value"] ?: ""
            return JsonPrimitive(fallback)
        }
        return JsonObject(objectFields)
    }

    private fun fieldToJson(field: EditableStyleField): JsonElement =
        if (field.isReference) {
            JsonPrimitive(field.value)
        } else {
            scalarToJson(field.value, preserveColorHexString = field.referenceCategory == SkinResourceCategory.Color)
        }

    @Suppress("ReturnCount")
    private fun scalarToJson(
        value: String,
        preserveColorHexString: Boolean,
    ): JsonElement {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return JsonPrimitive("")
        if (trimmed.equals("true", ignoreCase = true)) return JsonPrimitive(true)
        if (trimmed.equals("false", ignoreCase = true)) return JsonPrimitive(false)
        if (!preserveColorHexString && trimmed.toLongOrNull() != null) return JsonPrimitive(trimmed.toLong())
        if (!preserveColorHexString && trimmed.toDoubleOrNull() != null) return JsonPrimitive(trimmed.toDouble())
        return JsonPrimitive(trimmed)
    }

    private fun findOrCreateStyleSectionKey(
        root: Map<String, JsonElement>,
        normalizedTypeName: String,
    ): String = findExistingSectionKey(root, normalizedTypeName) ?: normalizedTypeName.toCanonicalStyleSectionKey()

    private fun findOrCreateResourceSectionKey(
        root: Map<String, JsonElement>,
        normalizedTypeName: String,
    ): String = findExistingSectionKey(root, normalizedTypeName) ?: normalizedTypeName

    private fun findExistingSectionKey(
        root: Map<String, JsonElement>,
        normalizedTypeName: String,
    ): String? = root.keys.firstOrNull { key -> loader.normalizeSkinTypeName(key) == normalizedTypeName }

    private fun String.toCanonicalStyleSectionKey(): String = CanonicalStyleSectionKeys[this] ?: this

    private fun SkinResourceCategory.sectionTypeName(): String =
        when (this) {
            SkinResourceCategory.Color -> ColorSectionTypeName
            else -> name
        }

    private companion object {
        private const val TAG = "SkinStyleSaveService"
        private const val ColorSectionTypeName = "Color"
        private val CanonicalStyleSectionKeys =
            mapOf(
                "LabelStyle" to "com.badlogic.gdx.scenes.scene2d.ui.Label\$LabelStyle",
                "TextButtonStyle" to "com.badlogic.gdx.scenes.scene2d.ui.TextButton\$TextButtonStyle",
                "ImageButtonStyle" to "com.badlogic.gdx.scenes.scene2d.ui.ImageButton\$ImageButtonStyle",
                "ButtonStyle" to "com.badlogic.gdx.scenes.scene2d.ui.Button\$ButtonStyle",
                "CheckBoxStyle" to "com.badlogic.gdx.scenes.scene2d.ui.CheckBox\$CheckBoxStyle",
                "TextFieldStyle" to "com.badlogic.gdx.scenes.scene2d.ui.TextField\$TextFieldStyle",
                "TextAreaStyle" to "com.badlogic.gdx.scenes.scene2d.ui.TextArea\$TextAreaStyle",
                "ListStyle" to "com.badlogic.gdx.scenes.scene2d.ui.List\$ListStyle",
                "SelectBoxStyle" to "com.badlogic.gdx.scenes.scene2d.ui.SelectBox\$SelectBoxStyle",
                "ScrollPaneStyle" to "com.badlogic.gdx.scenes.scene2d.ui.ScrollPane\$ScrollPaneStyle",
                "SliderStyle" to "com.badlogic.gdx.scenes.scene2d.ui.Slider\$SliderStyle",
                "ProgressBarStyle" to "com.badlogic.gdx.scenes.scene2d.ui.ProgressBar\$ProgressBarStyle",
                "WindowStyle" to "com.badlogic.gdx.scenes.scene2d.ui.Window\$WindowStyle",
                "TreeStyle" to "com.badlogic.gdx.scenes.scene2d.ui.Tree\$TreeStyle",
                "TextTooltipStyle" to "com.badlogic.gdx.scenes.scene2d.ui.TextTooltip\$TextTooltipStyle",
                "SplitPaneStyle" to "com.badlogic.gdx.scenes.scene2d.ui.SplitPane\$SplitPaneStyle",
            )
    }
}
