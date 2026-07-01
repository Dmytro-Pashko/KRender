package com.pashkd.krender.engine.assets

/**
 * Stable unique asset identifier stored in sidecar metadata.
 */
@JvmInline
value class AssetId(
    val value: String,
)

/**
 * High-level asset grouping used for browser filters and sorting.
 */
enum class AssetCategory(
    val displayName: String,
    val sortOrder: Int,
) {
    Model("Model", 0),
    Texture("Texture", 1),
    Material("Material", 3),
    Terrain("Terrain", 4),
    Scene2D("Scene2D", 5),

    /**
     * UI document category used by asset indexing and browser filters for KRender-native `.krui` UiScene files.
     *
     * `.krui` assets in this category are routed to UI Composer for validation, preview,
     * hierarchy/inspector editing, undo/redo, and save workflows. Current limitations still apply:
     * no canvas drag/drop authoring, no actor resizing on canvas, no multi-select, no canvas-based
     * structure editing, no Skin editing, and no asset-id references.
     */
    UI("UI", 6),
    Environment("Environment", 7),
    Scene("Scene", 8),
    Other("Other", 9),
}

/**
 * Concrete asset kind detected from paths and metadata.
 */
enum class AssetType {
    GltfModel,
    ObjModel,
    GdxModel,
    Texture,
    Atlas,
    Font,
    Terrain,

    /**
     * KRender-native Scene2D UI scene document stored as `.krui`.
     *
     * This type belongs to asset metadata and tool routing: runtime UI already consumes the shared
     * `engine.ui.scene` model, while Asset Browser indexes these files and opens UiComposerScene for
     * validation, preview, hierarchy/inspector editing, undo/redo, and save workflows. Current
     * limitations remain intentional: no canvas drag/drop, no actor resizing on canvas, no multi-select,
     * no canvas-based structure editing, no Skin editing, and no asset-id references.
     */
    UiScene,

    /**
     * LibGDX Scene2D Skin JSON descriptor used by `.krui` UI scenes and future Skin tooling.
     *
     * Asset Browser can index and inspect Skin JSON files for `.krui` workflows, but Skin editing,
     * dedicated Skin preview tooling, and asset-id based texture migration are intentionally out of scope.
     */
    Scene2DSkin,
    Environment,
    HdrSource,
    EnvironmentSkybox,
    EnvironmentCubemap,
    EnvironmentGeneratedMap,
    BrdfLut,
    Scene,
    Material,
    Unknown,
}

/**
 * Backend-neutral snapshot describing one discovered engine asset.
 */
data class AssetDescriptor(
    val id: AssetId,
    val name: String,
    val path: String,
    val category: AssetCategory,
    val type: AssetType,
    val extension: String,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
    val tags: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
)
