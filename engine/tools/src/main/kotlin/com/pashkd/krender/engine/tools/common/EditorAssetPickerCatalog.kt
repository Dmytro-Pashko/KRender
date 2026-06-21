package com.pashkd.krender.engine.tools.common

import com.pashkd.krender.engine.assets.AssetCategory
import com.pashkd.krender.engine.assets.AssetRegistryService
import com.pashkd.krender.engine.assets.AssetType

/**
 * Backend-neutral editor picker row derived from the shared asset registry.
 */
data class EditorAssetPickerOption(
    val displayName: String,
    val path: String,
    val assetId: String?,
    val category: AssetCategory,
    val type: AssetType,
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * Shared catalog for editor asset-picker options backed by [AssetRegistryService].
 */
class EditorAssetPickerCatalog(
    private val registry: AssetRegistryService,
) {
    fun listAssets(
        category: AssetCategory,
        type: AssetType? = null,
    ): List<EditorAssetPickerOption> =
        registry
            .byCategory(category)
            .asSequence()
            .filter { descriptor -> type == null || descriptor.type == type }
            .mapNotNull { descriptor ->
                val path = descriptor.path
                if (path.isBlank()) return@mapNotNull null
                EditorAssetPickerOption(
                    displayName = descriptor.metadata["displayName"]?.takeIf(String::isNotBlank) ?: descriptor.name,
                    path = path,
                    assetId = descriptor.id.value,
                    category = descriptor.category,
                    type = descriptor.type,
                    metadata = descriptor.metadata,
                )
            }.distinctBy { option -> option.path }
            .sortedWith(compareBy<EditorAssetPickerOption> { it.displayName.lowercase() }.thenBy { it.path.lowercase() })
            .toList()

    fun assetTypeByPath(): Map<String, AssetType> =
        registry.assets
            .asSequence()
            .filter { descriptor -> descriptor.path.isNotBlank() }
            .associate { descriptor -> descriptor.path to descriptor.type }
}
