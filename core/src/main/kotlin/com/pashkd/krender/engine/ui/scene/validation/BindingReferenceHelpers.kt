package com.pashkd.krender.engine.ui.scene.validation

import com.pashkd.krender.engine.ui.scene.UiSceneDocument
import com.pashkd.krender.engine.ui.scene.UiSceneNode
import com.pashkd.krender.engine.ui.scene.UiSceneNodeType

data class UiSceneBindingReference(
    val nodeId: String,
    val fieldName: String,
    val key: String,
    val placeholderSyntax: Boolean,
)

private val BindingPlaceholderRegex = Regex("""\{([^{}]+)}""")

fun extractBindingPlaceholders(value: String?): Set<String> =
    value
        .orEmpty()
        .let(BindingPlaceholderRegex::findAll)
        .map { match -> match.groupValues[1].trim() }
        .filter(String::isNotBlank)
        .toSet()

fun isSingleBindingPlaceholder(value: String?): Boolean {
    val text = value?.trim().orEmpty()
    if (text.isBlank()) return false
    val match = BindingPlaceholderRegex.matchEntire(text) ?: return false
    return match.groupValues[1].trim().isNotBlank()
}

fun collectBindingReferences(document: UiSceneDocument): List<UiSceneBindingReference> {
    val references = mutableListOf<UiSceneBindingReference>()

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
                    references +=
                        UiSceneBindingReference(
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

fun bindingKeys(document: UiSceneDocument): Set<String> =
    document.bindings
        .map { binding -> binding.key }
        .filter(String::isNotBlank)
        .toSet()

fun findMalformedBindingPlaceholders(value: String?): List<String> {
    val text = value ?: return emptyList()
    val malformed = mutableListOf<String>()
    var index = 0
    while (index < text.length) {
        when (text[index]) {
            '{' -> {
                val closeIndex = text.indexOf('}', startIndex = index + 1)
                if (closeIndex == -1) {
                    malformed += text.substring(index)
                    index = text.length
                } else {
                    val rawEnd =
                        if (
                            index + 1 < text.length &&
                            text[index + 1] == '{' &&
                            closeIndex + 1 < text.length &&
                            text[closeIndex + 1] == '}'
                        ) {
                            closeIndex + 2
                        } else {
                            closeIndex + 1
                        }
                    val raw = text.substring(index, rawEnd)
                    val inner = raw.substring(1, raw.length - 1).trim()
                    if (inner.isBlank() || inner.contains('{') || inner.contains('}')) {
                        malformed += raw
                    }
                    index = rawEnd
                }
            }

            '}' -> {
                malformed += "}"
                index += 1
            }

            else -> index += 1
        }
    }
    return malformed
}

private fun collectPlaceholderReferences(
    node: UiSceneNode,
    fieldName: String,
    value: String?,
    references: MutableList<UiSceneBindingReference>,
) {
    extractBindingPlaceholders(value).forEach { key ->
        references +=
            UiSceneBindingReference(
                nodeId = node.id,
                fieldName = fieldName,
                key = key,
                placeholderSyntax = true,
            )
    }
}
