package com.pashkd.krender.engine.tools.uicomposer

import com.pashkd.krender.engine.ui.scene.*
import com.pashkd.krender.engine.ui.scene.validation.UiSceneBindingReference
import com.pashkd.krender.engine.ui.scene.validation.unknownBindingMessage
import com.pashkd.krender.engine.ui.scene.validation.bindingKeys as sceneBindingKeys
import com.pashkd.krender.engine.ui.scene.validation.collectBindingReferences as collectSceneBindingReferences
import com.pashkd.krender.engine.ui.scene.validation.extractBindingPlaceholders as extractSceneBindingPlaceholders

typealias UiComposerBindingReference = UiSceneBindingReference

/**
 * Editor-only missing binding key discovered in the current `.krui` document.
 *
 * This is used by the Scene Bindings panel to offer "Add binding" actions. It
 * is not saved to `.krui` and does not represent runtime state.
 */
data class UiComposerMissingBindingKey(
    val key: String,
    val nodeIds: Set<String>,
    val fields: Set<String>,
)

private const val DefaultPreviewTexturePath = "textures/default_skybox_studio.png"

/**
 * Extracts `{key}` placeholders from text-like `.krui` fields.
 *
 * Supported syntax is intentionally simple and stable:
 * `{key}` where key is non-empty and does not contain `{` or `}`.
 */
fun extractBindingPlaceholders(value: String?): Set<String> = extractSceneBindingPlaceholders(value)

/**
 * Collects all binding references from fields supported by UiComposer binding helpers.
 *
 * This is editor-only introspection. It does not mutate the document and does
 * not change runtime binding behavior.
 */
fun collectBindingReferences(document: UiSceneDocument): List<UiComposerBindingReference> = collectSceneBindingReferences(document)

/**
 * Validates `.krui` binding references against known document binding keys.
 *
 * This is editor-only diagnostics. Unknown keys are validation errors, but
 * runtime binding fallback behavior is intentionally unchanged in this phase.
 */
fun validateBindingReferences(
    document: UiSceneDocument,
    knownKeys: Set<String>,
): List<UiSceneValidationIssue> =
    collectBindingReferences(document)
        .filter { reference -> reference.key !in knownKeys }
        .map { reference ->
            error(
                code = UiSceneValidationCode.UnknownBindingKey,
                nodeId = reference.nodeId,
                fieldName = reference.fieldName,
                bindingKey = reference.key,
                message = unknownBindingMessage(reference),
            )
        }

/**
 * Groups missing binding references by key for Scene Bindings quick-add actions.
 */
fun missingBindingKeys(
    document: UiSceneDocument,
    knownKeys: Set<String>,
): List<UiComposerMissingBindingKey> =
    collectBindingReferences(document)
        .filter { reference -> reference.key !in knownKeys }
        .groupBy { reference -> reference.key }
        .map { (key, references) ->
            UiComposerMissingBindingKey(
                key = key,
                nodeIds = references.mapTo(sortedSetOf()) { reference -> reference.nodeId },
                fields = references.mapTo(sortedSetOf()) { reference -> reference.fieldName },
            )
        }.sortedBy { missing -> missing.key.lowercase() }

/**
 * Returns the document-owned binding keys that define this scene contract.
 */
fun bindingKeys(document: UiSceneDocument): Set<String> = sceneBindingKeys(document)

/**
 * Appends a `{key}` placeholder to a text-like field.
 *
 * This is an editor-only string helper for the Inspector binding helper. It does
 * not parse expressions, mutate documents directly, or change runtime binding behavior.
 */
fun insertPlaceholder(
    current: String,
    key: String,
): String = if (current.isBlank()) "{$key}" else "$current {$key}"

/**
 * Returns the placeholder form used when Image.texture is backed by a binding key.
 */
fun textureBindingPlaceholder(key: String): String = "{$key}"

/**
 * Provides heuristic editor-only preview data for a newly discovered binding key.
 *
 * The returned value seeds a document binding definition so the editor can
 * render missing bindings quickly. It is saved as a `.krui` editor default
 * preview value but does not affect runtime payload data and does not define a
 * gameplay data model.
 */
fun defaultPreviewPayloadValueFor(key: String): String =
    when {
        key.contains("texture", ignoreCase = true) -> DefaultPreviewTexturePath
        key.contains("action", ignoreCase = true) -> "action.todo"
        key.contains("progress", ignoreCase = true) -> "0.5"
        key.contains("percent", ignoreCase = true) -> "0.5"
        key.contains("score", ignoreCase = true) -> "0"
        key.contains("health", ignoreCase = true) -> "100/100"
        else -> ""
    }

/**
 * Converts saved `.krui` binding definitions into the transient editor preview payload.
 */
fun previewPayloadFromBindings(bindings: List<UiSceneBindingDefinition>): Map<String, String> =
    bindings
        .filter { binding -> binding.key.isNotBlank() }
        .associate { binding -> binding.key to binding.defaultValue }

/**
 * Returns document bindings with [key]'s saved editor default preview value updated.
 */
fun updateBindingDefaultValue(
    bindings: List<UiSceneBindingDefinition>,
    key: String,
    defaultValue: String,
): List<UiSceneBindingDefinition> =
    bindings.map { binding ->
        if (binding.key == key) binding.copy(defaultValue = defaultValue) else binding
    }

/**
 * Returns document bindings with [binding] inserted or replaced by key.
 *
 * Binding definitions are document-owned scene contract metadata; their default
 * values are editor preview defaults, not runtime payload fallback values.
 */
fun upsertBindingDefinition(
    bindings: List<UiSceneBindingDefinition>,
    binding: UiSceneBindingDefinition,
): List<UiSceneBindingDefinition> {
    if (binding.key.isBlank()) return bindings
    var replaced = false
    val updated =
        bindings.map { existing ->
            if (existing.key == binding.key) {
                replaced = true
                binding
            } else {
                existing
            }
        }
    return if (replaced) updated else (updated + binding).sortedBy { it.key.lowercase() }
}

/**
 * Infers an editor binding type for quick-added missing bindings.
 *
 * This is a UI convenience only. Runtime binding still resolves string keys from
 * payload values and does not enforce this type.
 */
fun defaultBindingTypeFor(missing: UiComposerMissingBindingKey): UiSceneBindingType =
    when {
        "texture" in missing.fields -> UiSceneBindingType.Texture
        "action" in missing.fields -> UiSceneBindingType.Action
        "valueBinding" in missing.fields -> UiSceneBindingType.Number
        missing.key.contains("progress", ignoreCase = true) -> UiSceneBindingType.Number
        missing.key.contains("percent", ignoreCase = true) -> UiSceneBindingType.Number
        else -> UiSceneBindingType.Text
    }
