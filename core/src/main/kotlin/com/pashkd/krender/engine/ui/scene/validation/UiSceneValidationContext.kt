package com.pashkd.krender.engine.ui.scene.validation

import com.pashkd.krender.engine.ui.scene.UiSceneBindingDefinition
import com.pashkd.krender.engine.ui.scene.UiSceneDocument
import com.pashkd.krender.engine.ui.scene.UiSceneNode

/**
 * Immutable context shared by all `.krui` validators.
 *
 * Validators should not mutate this context. They only inspect it and return
 * issues. This keeps validation rules small, composable, and easy to test.
 */
data class UiSceneValidationContext(
    val document: UiSceneDocument,
    val skinMetadata: UiSceneSkinValidationMetadata? = null,
    val textureMetadata: UiSceneTextureValidationMetadata? = null,
) {
    val nodes: List<UiSceneNode> = document.root.flattenNodes()
    val nodesById: Map<String, List<UiSceneNode>> = nodes.groupBy { node -> node.id }
    val bindingDefinitionsByKey: Map<String, UiSceneBindingDefinition> =
        document.bindings
            .filter { binding -> binding.key.isNotBlank() }
            .associateBy { binding -> binding.key }
}

fun UiSceneNode.flattenNodes(): List<UiSceneNode> = listOf(this) + children.flatMap { child -> child.flattenNodes() }

/**
 * Skin metadata available to validation.
 *
 * This type is backend-neutral and should not expose LibGDX Skin.
 */
data class UiSceneSkinValidationMetadata(
    val labelStyles: Set<String> = emptySet(),
    val textButtonStyles: Set<String> = emptySet(),
    val progressBarStyles: Set<String> = emptySet(),
    val drawables: Set<String> = emptySet(),
    val loadError: String? = null,
)

/**
 * Texture metadata available to validation.
 *
 * Paths are project-relative texture asset paths known to the editor Asset Registry.
 */
data class UiSceneTextureValidationMetadata(
    val texturePaths: Set<String> = emptySet(),
    val nonTextureAssetPaths: Set<String> = emptySet(),
)
