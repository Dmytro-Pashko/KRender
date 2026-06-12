package com.pashkd.krender.engine.assets

import com.pashkd.krender.engine.ui.scene.UiSceneSerializer
import com.pashkd.krender.engine.ui.scene.UiSceneValidator
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Lightweight Asset Browser metadata extracted from a `.krui` UiScene document.
 *
 * This type belongs to asset indexing and asset metadata, not runtime UI. It lets the browser show
 * document id, Skin path, schema version, and validation diagnostics without building Scene2D actors.
 * It intentionally does not implement preview rendering, hierarchy editing, bounds overlays, Skin editing,
 * drag/drop editing, or asset-id based references; those belong to the future UiComposerScene phase.
 */
data class UiSceneAssetMetadata(
    val documentId: String? = null,
    val skinPath: String? = null,
    val schemaVersion: Int? = null,
    val parseError: String? = null,
    val validationIssues: List<String> = emptyList(),
) {
    /**
     * Converts parsed UiScene metadata into the flat map used by [AssetDescriptor].
     */
    fun toMetadataMap(): Map<String, String> =
        buildMap {
            put("uiSceneStatus", if (parseError == null) "valid" else "invalid")
            documentId?.let { put("uiSceneDocumentId", it) }
            skinPath?.let { put("uiSceneSkinPath", it) }
            schemaVersion?.let { put("uiSceneSchemaVersion", it.toString()) }
            parseError?.let { put("uiSceneParseError", it) }
            put("uiSceneValidationWarningCount", validationIssues.size.toString())
            if (validationIssues.isNotEmpty()) {
                put("uiSceneValidationIssuePreview", validationIssues.take(3).joinToString("; "))
            }
        }
}

/**
 * Reads `.krui` UiScene metadata for asset indexing.
 *
 * The reader uses [UiSceneSerializer] and [UiSceneValidator] only to inspect document headers and
 * lightweight validation issues. It is deliberately non-blocking for Asset Browser: parse failures
 * are returned as metadata instead of escaping, and no Scene2D runtime actors or previews are built.
 */
object UiSceneAssetMetadataReader {
    /**
     * Extracts metadata from [file] while keeping invalid `.krui` files indexable.
     */
    fun read(file: File): UiSceneAssetMetadata =
        try {
            val document = UiSceneSerializer().decode(file.readText(StandardCharsets.UTF_8))
            val issues =
                UiSceneValidator().validate(document).map { issue ->
                    if (issue.nodeId == null) issue.message else "${issue.nodeId}: ${issue.message}"
                }
            UiSceneAssetMetadata(
                documentId = document.id,
                skinPath = document.skin,
                schemaVersion = document.schemaVersion,
                validationIssues = issues,
            )
        } catch (error: Exception) {
            UiSceneAssetMetadata(parseError = error.message ?: error.javaClass.simpleName)
        }
}
