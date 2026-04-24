package com.pashkd.krender.engine.api

import kotlin.reflect.KClass

class ModelAsset private constructor()
class TextureAsset private constructor()
class ShaderAsset private constructor()

data class AssetRef<T : Any>(
    val path: String,
    val type: KClass<T>,
) {
    val isPrimitive: Boolean = path.startsWith(PRIMITIVE_PREFIX)

    companion object {
        private const val PRIMITIVE_PREFIX = "primitive:"

        fun model(path: String): AssetRef<ModelAsset> = AssetRef(path, ModelAsset::class)
        fun primitiveModel(name: String): AssetRef<ModelAsset> = model("$PRIMITIVE_PREFIX$name")
        fun texture(path: String): AssetRef<TextureAsset> = AssetRef(path, TextureAsset::class)
        fun shader(path: String): AssetRef<ShaderAsset> = AssetRef(path, ShaderAsset::class)
    }
}

/**
 * Declares a reusable group of asset handles required by a scene or subsystem.
 */
interface AssetPack {
    val assets: List<AssetRef<*>>
}

/**
 * Engine-level asset API that owns loading state through typed [AssetRef] handles.
 *
 * Core code should keep handles and scheduling intent here; backend-specific services
 * perform the actual runtime loading and conversion to platform objects.
 */
interface AssetService {
    fun queue(asset: AssetRef<*>)
    fun update(budgetMs: Int = 5): Float
    fun isLoaded(asset: AssetRef<*>): Boolean
    fun <T : Any> get(asset: AssetRef<T>): T
    fun unload(asset: AssetRef<*>)
}
