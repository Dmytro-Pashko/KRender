package com.pashkd.krender.engine.assets.importing

import com.pashkd.krender.engine.assets.AssetCategory
import com.pashkd.krender.engine.assets.AssetType

/**
 * Collision strategy for import target paths.
 */
enum class AssetImportCollisionPolicy {
    Rename,
    Skip,
    Overwrite,
}

/**
 * Immutable plan for importing one or more source files.
 */
data class AssetImportPlan(
    val entries: List<AssetImportEntry>,
    val collisionPolicy: AssetImportCollisionPolicy = AssetImportCollisionPolicy.Rename,
) {
    val supportedEntries: List<AssetImportEntry>
        get() = entries.filter(AssetImportEntry::supported)

    val requiresOverwriteConfirmation: Boolean
        get() = collisionPolicy == AssetImportCollisionPolicy.Overwrite &&
            supportedEntries.any(AssetImportEntry::mainTargetExists)
}

/**
 * One source-to-target import decision.
 */
data class AssetImportEntry(
    val sourcePath: String,
    val sourceName: String,
    val supported: Boolean,
    val type: AssetType = AssetType.Unknown,
    val category: AssetCategory = AssetCategory.Other,
    val importerId: String? = null,
    val targetDirectory: String? = null,
    val targetPath: String? = null,
    val mainTargetExists: Boolean = false,
    val dependencies: List<AssetImportDependency> = emptyList(),
    val warnings: List<String> = emptyList(),
    val status: String,
)

data class AssetImportDependency(
    val sourcePath: String,
    val targetPath: String,
    val kind: AssetImportDependencyKind,
    val overwriteExisting: Boolean = true,
    val targetExists: Boolean = false,
)

enum class AssetImportDependencyKind {
    Texture,
    Atlas,
    BitmapFont,
    Font,
    Other,
}

/**
 * Result of executing an [AssetImportPlan].
 */
data class AssetImportResult(
    val imported: List<AssetImportEntry>,
    val skipped: List<AssetImportEntry>,
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
) {
    val succeeded: Boolean
        get() = errors.isEmpty() && imported.isNotEmpty()
}

/**
 * Backend-neutral asset import planning and execution service.
 */
interface AssetImportService {
    fun planImport(
        sourcePath: String,
        collisionPolicy: AssetImportCollisionPolicy = AssetImportCollisionPolicy.Rename,
        importName: String? = null,
    ): AssetImportPlan

    fun importAssets(plan: AssetImportPlan): AssetImportResult
}
