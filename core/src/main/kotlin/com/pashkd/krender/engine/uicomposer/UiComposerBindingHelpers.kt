package com.pashkd.krender.engine.uicomposer

import com.pashkd.krender.engine.ui.scene.UiSceneDocument
import com.pashkd.krender.engine.ui.scene.UiSceneBindingDefinition
import com.pashkd.krender.engine.ui.scene.UiSceneBindingType
import com.pashkd.krender.engine.ui.scene.UiSceneNode
import com.pashkd.krender.engine.ui.scene.UiSceneNodeType
import com.pashkd.krender.engine.ui.scene.UiSceneValidationIssue

/**
 * Describes one binding reference found in a `.krui` node field.
 *
 * This belongs to editor-only binding diagnostics. It does not change runtime
 * binding behavior, does not mutate `.krui`, and does not save preview payload.
 */
data class UiComposerBindingReference(
    val nodeId: String,
    val fieldName: String,
    val key: String,
    val placeholderSyntax: Boolean,
)

/**
 * Editor-only missing binding key discovered in the current `.krui` document.
 *
 * This is used by Diagnostics to offer "Add binding" actions. It is
 * not saved to `.krui` and does not represent runtime state.
 */
data class UiComposerMissingBindingKey(
    val key: String,
    val nodeIds: Set<String>,
    val fields: Set<String>,
)

private val BindingPlaceholderRegex = Regex("""\{([^{}]+)}""")

/**
 * Extracts `{key}` placeholders from text-like `.krui` fields.
 *
 * Supported syntax is intentionally simple and stable:
 * `{key}` where key is non-empty and does not contain `{` or `}`.
 */
fun extractBindingPlaceholders(value: String?): Set<String> =
    value.orEmpty()
        .let(BindingPlaceholderRegex::findAll)
        .map { match -> match.groupValues[1].trim() }
        .filter(String::isNotBlank)
        .toSet()

/**
 * Collects all binding references from fields supported by UiComposer binding helpers.
 *
 * This is editor-only introspection. It does not mutate the document and does
 * not change runtime binding behavior.
 */
fun collectBindingReferences(document: UiSceneDocument): List<UiComposerBindingReference> {
    val references = mutableListOf<UiComposerBindingReference>()

    fun visit(node: UiSceneNode) {
        when (node.type) {
            UiSceneNodeType.Label -> {
                collectPlaceholderReferences(node, fieldName = "text", value = node.text, references = references)
            }

            UiSceneNodeType.TextButton -> {
                collectPlaceholderReferences(node, fieldName = "text", value = node.text, references = references)
                collectPlaceholderReferences(node, fieldName = "action", value = node.action, references = references)
            }

            UiSceneNodeType.Image -> {
                collectPlaceholderReferences(node, fieldName = "texture", value = node.texture, references = references)
            }

            UiSceneNodeType.ProgressBar -> {
                val key = node.valueBinding?.trim().orEmpty()
                if (key.isNotBlank()) {
                    references += UiComposerBindingReference(
                        nodeId = node.id,
                        fieldName = "valueBinding",
                        key = key,
                        placeholderSyntax = false,
                    )
                }
            }

            UiSceneNodeType.Stack,
            UiSceneNodeType.Table,
            UiSceneNodeType.Container,
            UiSceneNodeType.Space,
                -> Unit
        }

        node.children.forEach(::visit)
    }

    visit(document.root)
    return references
}

/**
 * Validates `.krui` binding references against known document binding keys.
 *
 * This is editor-only diagnostics. Unknown keys are warnings, not save blockers.
 */
fun validateBindingReferences(
    document: UiSceneDocument,
    knownKeys: Set<String>,
): List<UiSceneValidationIssue> =
    collectBindingReferences(document)
        .filter { reference -> reference.key !in knownKeys }
        .map { reference ->
            UiSceneValidationIssue(
                nodeId = reference.nodeId,
                message = unknownBindingMessage(reference),
            )
        }

/**
 * Groups missing binding references by key for Diagnostics quick-add actions.
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
        }
        .sortedBy { missing -> missing.key.lowercase() }

/**
 * Appends a `{key}` placeholder to a text-like field.
 *
 * This is an editor-only string helper for the Inspector binding helper. It does
 * not parse expressions, mutate documents directly, or change runtime binding behavior.
 */
fun insertPlaceholder(
    current: String,
    key: String,
): String =
    if (current.isBlank()) "{${key}}" else "$current {${key}}"

/**
 * Returns the placeholder form used when Image.texture is backed by a binding key.
 */
fun textureBindingPlaceholder(key: String): String = "{${key}}"

/**
 * Provides heuristic editor-only preview data for a newly discovered binding key.
 *
 * The returned value seeds a document binding definition so the editor can
 * render missing bindings quickly. It is saved as `.krui` default data but does
 * not affect runtime payload data and does not define a gameplay data model.
 */
fun defaultPreviewPayloadValueFor(key: String): String =
    when {
        key.contains("texture", ignoreCase = true) -> ""
        key.contains("action", ignoreCase = true) -> "action.todo"
        key.contains("progress", ignoreCase = true) -> "0.5"
        key.contains("percent", ignoreCase = true) -> "0.5"
        key.contains("score", ignoreCase = true) -> "0"
        key.contains("health", ignoreCase = true) -> "100/100"
        else -> ""
    }

/**
 * Converts saved `.krui` binding definitions into the editor preview payload.
 */
fun previewPayloadFromBindings(bindings: List<UiSceneBindingDefinition>): Map<String, String> =
    bindings
        .filter { binding -> binding.key.isNotBlank() }
        .associate { binding -> binding.key to binding.defaultValue }

/**
 * Returns document bindings with [key]'s default preview value updated.
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
 */
fun upsertBindingDefinition(
    bindings: List<UiSceneBindingDefinition>,
    binding: UiSceneBindingDefinition,
): List<UiSceneBindingDefinition> {
    if (binding.key.isBlank()) return bindings
    var replaced = false
    val updated = bindings.map { existing ->
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

private fun collectPlaceholderReferences(
    node: UiSceneNode,
    fieldName: String,
    value: String?,
    references: MutableList<UiComposerBindingReference>,
) {
    extractBindingPlaceholders(value).forEach { key ->
        references += UiComposerBindingReference(
            nodeId = node.id,
            fieldName = fieldName,
            key = key,
            placeholderSyntax = true,
        )
    }
}

private fun unknownBindingMessage(reference: UiComposerBindingReference): String =
    if (reference.placeholderSyntax) {
        val guidance = when (reference.fieldName) {
            "texture" -> "Add a binding definition or use a static texture path."
            else -> "Add a binding definition or fix the placeholder."
        }
        "Unknown binding key '${reference.key}' in ${reference.fieldName}. $guidance"
    } else {
        "Unknown valueBinding key '${reference.key}'. Add a binding definition or clear valueBinding."
    }
