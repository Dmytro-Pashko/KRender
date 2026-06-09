package com.pashkd.krender.engine.uicomposer

import com.pashkd.krender.engine.assets.AssetCategory
import com.pashkd.krender.engine.assets.AssetRegistryService
import com.pashkd.krender.engine.assets.AssetType
import com.pashkd.krender.engine.ui.scene.UiSceneDocument
import com.pashkd.krender.engine.ui.scene.UiSceneNode
import com.pashkd.krender.engine.ui.scene.UiSceneNodeType
import com.pashkd.krender.engine.ui.scene.UiSceneValidationIssue

/**
 * One texture row shown by the UiComposer Image texture picker.
 *
 * This exists for editor asset picking and Asset Registry integration: it gives
 * Inspector UI a backend-neutral display label and the project-relative texture
 * path that `.krui` already stores. [assetId] is informational only for this
 * phase; the picker writes [path], not asset ids, and intentionally does not
 * import/copy textures, edit textures, pick atlas regions, generate
 * SpriteDrawables, support Asset Browser drag/drop, or change runtime UI
 * behavior.
 */
data class UiComposerTextureOption(
    val displayName: String,
    val path: String,
    val assetId: String? = null,
)

/**
 * Provides Image texture picker options from the existing Asset Registry.
 *
 * This class belongs to editor asset picking and Asset Registry integration,
 * not the shared `.krui` model or runtime UI. It filters registry descriptors
 * down to usable texture assets and maps them to path-based picker rows. It
 * intentionally does not scan the filesystem directly, create asset-id
 * references, import/copy textures, expose thumbnails, pick atlas regions,
 * support Asset Browser drag/drop, or change runtime loading behavior.
 */
class UiComposerTextureOptionsProvider(
    private val assets: AssetRegistryService,
) {
    /**
     * Lists usable texture/image assets for the UiComposer Image texture picker.
     *
     * Results are project-relative path rows sorted by display name and path.
     * The `.krui` document continues to store only [UiComposerTextureOption.path];
     * [UiComposerTextureOption.assetId] is retained only as informational
     * registry context for future phases.
     */
    fun listTextureOptions(): List<UiComposerTextureOption> =
        assets.byCategory(AssetCategory.Texture)
            .asSequence()
            .filter { descriptor -> descriptor.type == AssetType.Texture }
            .map { descriptor ->
                UiComposerTextureOption(
                    displayName = descriptor.metadata["displayName"]?.takeIf(String::isNotBlank) ?: descriptor.name,
                    path = descriptor.path,
                    assetId = descriptor.id.value,
                )
            }
            .filter { option -> option.path.isNotBlank() }
            .distinctBy { option -> option.path }
            .sortedWith(compareBy<UiComposerTextureOption> { it.displayName.lowercase() }.thenBy { it.path.lowercase() })
            .toList()
}

/**
 * Validates Image texture paths against Asset Registry-backed picker options.
 *
 * This belongs to editor diagnostics for path-based asset picking. It warns
 * when an Image texture points outside the registry or to a known non-texture
 * asset, but it never blocks save and never rewrites `.krui`. It intentionally
 * does not introduce asset ids, import/copy textures, inspect atlas regions,
 * generate SpriteDrawables, add drag/drop, or change runtime behavior.
 */
fun validateTextureReferences(
    document: UiSceneDocument,
    textureOptions: List<UiComposerTextureOption>,
    assetTypeByPath: Map<String, AssetType> = emptyMap(),
): List<UiSceneValidationIssue> {
    val texturePaths = textureOptions.mapTo(mutableSetOf()) { option -> normalizeTexturePath(option.path) }
    val normalizedAssetTypes = assetTypeByPath.mapKeys { (path, _) -> normalizeTexturePath(path) }
    val issues = mutableListOf<UiSceneValidationIssue>()
    collectTextureIssues(document.root, texturePaths, normalizedAssetTypes, issues)
    return issues
}

private fun collectTextureIssues(
    node: UiSceneNode,
    texturePaths: Set<String>,
    assetTypeByPath: Map<String, AssetType>,
    issues: MutableList<UiSceneValidationIssue>,
) {
    if (node.type == UiSceneNodeType.Image) {
        val texturePath = node.texture
            ?.takeIf(String::isNotBlank)
            ?.takeUnless { texture -> extractBindingPlaceholders(texture).isNotEmpty() }
            ?.let(::normalizeTexturePath)
        if (texturePath != null && texturePath !in texturePaths) {
            val knownType = assetTypeByPath[texturePath]
            val message = if (knownType != null && knownType != AssetType.Texture) {
                "Image texture path '${node.texture}' resolves to $knownType, not Texture."
            } else {
                "Image texture path '${node.texture}' is not in Asset Registry."
            }
            issues += UiSceneValidationIssue(node.id, message)
        }
    }
    // Texture diagnostics mirror the document tree so nested Image nodes are covered.
    node.children.forEach { child -> collectTextureIssues(child, texturePaths, assetTypeByPath, issues) }
}

private fun normalizeTexturePath(path: String): String =
    path.trim().replace('\\', '/').trimStart('/')
