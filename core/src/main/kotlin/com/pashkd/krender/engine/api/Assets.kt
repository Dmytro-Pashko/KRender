package com.pashkd.krender.engine.api

import kotlin.reflect.KClass

/**
 * Marker type for file-backed or procedural model assets.
 */
class ModelAsset private constructor()

/**
 * Marker type for texture assets.
 */
class TextureAsset private constructor()

/**
 * Marker type for terrain descriptor assets.
 */
class TerrainAsset private constructor()

/**
 * Marker type for shader source assets.
 */
class ShaderAsset private constructor()

/**
 * Typed handle used to identify an asset without exposing backend storage details.
 */
data class AssetRef<T : Any>(
    /** Normalized asset path or primitive identifier. */
    val path: String,
    /** Asset category carried by this handle. */
    val type: KClass<T>,
) {
    /** Indicates that this handle refers to a generated primitive instead of a file. */
    val isPrimitive: Boolean = path.startsWith(PRIMITIVE_PREFIX)

    companion object {
        private const val PRIMITIVE_PREFIX = "primitive:"

        /** Creates a typed model asset reference from a path. */
        fun model(path: String): AssetRef<ModelAsset> = AssetRef(path, ModelAsset::class)

        /** Creates a typed model reference for a built-in primitive mesh. */
        fun primitiveModel(name: String): AssetRef<ModelAsset> = model("$PRIMITIVE_PREFIX$name")

        /** Creates a typed texture asset reference from a path. */
        fun texture(path: String): AssetRef<TextureAsset> = AssetRef(path, TextureAsset::class)

        /** Creates a typed terrain asset reference from a path. */
        fun terrain(path: String): AssetRef<TerrainAsset> = AssetRef(path, TerrainAsset::class)

        /** Creates a typed shader asset reference from a path. */
        fun shader(path: String): AssetRef<ShaderAsset> = AssetRef(path, ShaderAsset::class)
    }
}

/**
 * Declares a reusable group of asset handles required by a scene or subsystem.
 */
interface AssetPack {
    /** Lists every asset handle required by this pack. */
    val assets: List<AssetRef<*>>
}

/**
 * Engine-level asset API that owns loading state through typed [AssetRef] handles.
 *
 * Core code should keep handles and scheduling intent here; backend-specific services
 * perform the actual runtime loading and conversion to platform objects.
 */
interface AssetService {
    /** Schedules an asset for loading or preparation. */
    fun queue(asset: AssetRef<*>)

    /** Advances pending asset work and returns normalized progress. */
    fun update(budgetMs: Int = 5): Float

    /** Returns normalized progress for currently queued assets. */
    fun progress(): Float = 1f

    /** Reports whether a scheduled asset is ready for use. */
    fun isLoaded(asset: AssetRef<*>): Boolean

    /** Resolves a loaded asset into its backend-specific runtime object. */
    fun <T : Any> get(asset: AssetRef<T>): T

    /** Returns the triangle count for a loaded model asset when available. */
    fun triangleCount(asset: AssetRef<ModelAsset>): Int? = null

    /** Returns a metadata snapshot for a loaded model asset when available. */
    fun modelInfo(asset: AssetRef<ModelAsset>): ModelAssetInfo? = null

    /** Releases any runtime state associated with the given asset. */
    fun unload(asset: AssetRef<*>)
}

/**
 * Summarizes the essential runtime metadata extracted from a loaded model asset.
 */
data class ModelAssetInfo(
    /** Source path used to load the model. */
    val path: String,
    /** File or backend format label such as glTF, OBJ, or G3D. */
    val format: String,
    /** Total node count across the loaded model hierarchy. */
    val nodeCount: Int,
    /** Total mesh count referenced by the model. */
    val meshCount: Int,
    /** Total mesh-part count across all meshes. */
    val meshPartCount: Int,
    /** Total material count defined by the model. */
    val materialCount: Int,
    /** Total vertex count across all meshes. */
    val vertexCount: Int,
    /** Total triangle count across all renderable mesh parts. */
    val triangleCount: Int,
    /** Model-space bounding-box size. */
    val size: Vec3?,
    /** Vertex channels present in the model meshes. */
    val vertexChannels: List<String>,
    /** UV channel labels present in the model meshes. */
    val uvChannels: List<String>,
    /** Texture channel aliases referenced by materials. */
    val textureChannels: List<String>,
    /** Total number of unique textures referenced by the model. */
    val textureCount: Int,
    /** Total number of texture slots referenced by the model materials. */
    val textureSlotCount: Int,
    /** Indicates whether the model contains skinned mesh parts. */
    val hasSkeleton: Boolean,
    /** Maximum number of bones referenced by a single skinned mesh part. */
    val boneCount: Int,
    /** Maximum number of bone-weight channels present per vertex. */
    val boneWeightChannelCount: Int,
    /** Total number of animations available on the model. */
    val animationCount: Int,
    /** Animation ids exposed by the loaded model. */
    val animationNames: List<String>,
)
