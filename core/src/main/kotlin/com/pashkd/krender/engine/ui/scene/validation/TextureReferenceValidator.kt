package com.pashkd.krender.engine.ui.scene.validation

import com.pashkd.krender.engine.ui.scene.*

object TextureReferenceValidator : UiSceneValidationRule {
    override val id: String = "TextureReferenceValidator"

    override fun validate(context: UiSceneValidationContext): List<UiSceneValidationIssue> {
        val metadata = context.textureMetadata ?: return emptyList()
        val texturePaths = metadata.texturePaths.mapTo(mutableSetOf(), ::normalizeTexturePath)
        val nonTextureAssetPaths = metadata.nonTextureAssetPaths.mapTo(mutableSetOf(), ::normalizeTexturePath)
        val issues = mutableListOf<UiSceneValidationIssue>()
        context.nodes.forEach { node ->
            validateImageTexture(node, texturePaths, nonTextureAssetPaths, issues)
        }
        context.document.bindings
            .filter { binding -> binding.type == UiSceneBindingType.Texture }
            .forEach { binding ->
                validateTextureBindingDefault(
                    key = binding.key,
                    defaultValue = binding.defaultValue,
                    texturePaths = texturePaths,
                    nonTextureAssetPaths = nonTextureAssetPaths,
                    issues = issues,
                )
            }
        return issues
    }

    private fun validateImageTexture(
        node: UiSceneNode,
        texturePaths: Set<String>,
        nonTextureAssetPaths: Set<String>,
        issues: MutableList<UiSceneValidationIssue>,
    ) {
        if (node.type != UiSceneNodeType.Image) return
        val rawTexturePath =
            node.texture
                ?.takeIf(String::isNotBlank)
                ?.takeUnless(::isSingleBindingPlaceholder)
                ?: return
        val texturePath = normalizeTexturePath(rawTexturePath)
        when {
            texturePath in nonTextureAssetPaths -> {
                issues +=
                    warning(
                        code = UiSceneValidationCode.NonTextureAsset,
                        message = "Image texture path '$rawTexturePath' resolves to non-texture asset, not Texture.",
                        nodeId = node.id.takeIf(String::isNotBlank),
                        fieldName = "texture",
                    )
            }

            texturePath !in texturePaths -> {
                issues +=
                    warning(
                        code = UiSceneValidationCode.MissingTexture,
                        message = "Image texture path '$rawTexturePath' is not in Asset Registry.",
                        nodeId = node.id.takeIf(String::isNotBlank),
                        fieldName = "texture",
                    )
            }
        }
    }

    private fun validateTextureBindingDefault(
        key: String,
        defaultValue: String,
        texturePaths: Set<String>,
        nonTextureAssetPaths: Set<String>,
        issues: MutableList<UiSceneValidationIssue>,
    ) {
        if (defaultValue.isBlank()) return
        val texturePath = normalizeTexturePath(defaultValue)
        when {
            texturePath in nonTextureAssetPaths -> {
                issues +=
                    warning(
                        code = UiSceneValidationCode.NonTextureAsset,
                        message = "Texture binding '$key' defaultValue '$defaultValue' resolves to non-texture asset, not Texture.",
                        fieldName = "bindings.defaultValue",
                        bindingKey = key.takeIf(String::isNotBlank),
                    )
            }

            texturePath !in texturePaths -> {
                issues +=
                    warning(
                        code = UiSceneValidationCode.MissingTextureBindingDefault,
                        message = "Texture binding '$key' defaultValue '$defaultValue' was not found in Asset Registry.",
                        fieldName = "bindings.defaultValue",
                        bindingKey = key.takeIf(String::isNotBlank),
                    )
            }
        }
    }
}

fun normalizeTexturePath(path: String): String = path.trim().replace('\\', '/').trimStart('/')
