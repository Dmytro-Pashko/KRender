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

    /** Reports whether a scheduled asset is ready for use. */
    fun isLoaded(asset: AssetRef<*>): Boolean

    /** Resolves a loaded asset into its backend-specific runtime object. */
    fun <T : Any> get(asset: AssetRef<T>): T

    /** Releases any runtime state associated with the given asset. */
    fun unload(asset: AssetRef<*>)
}
