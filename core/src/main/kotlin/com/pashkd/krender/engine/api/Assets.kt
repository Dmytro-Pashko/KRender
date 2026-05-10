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

    /** Returns cached local-space bounds for a loaded model asset when available. */
    fun modelBounds(asset: AssetRef<ModelAsset>): ModelAssetBounds? = null

    /** Returns an opaque UI preview handle for a loaded texture path or backend texture id. */
    fun texturePreviewHandle(texturePathOrId: String): TexturePreviewHandle? = null

    /** Releases any runtime state associated with the given asset. */
    fun unload(asset: AssetRef<*>)
}

/**
 * Opaque backend texture handle that can be rendered by the active UI service.
 */
data class TexturePreviewHandle(
    /** Backend-defined texture identifier. Core code must treat it as opaque. */
    val id: Int,
    /** Source texture width in pixels, when known. */
    val width: Int,
    /** Source texture height in pixels, when known. */
    val height: Int,
)

/**
 * Backend-neutral reference to a texture binding used by material tooling and debug rendering.
 */
data class MaterialTextureRef(
    /** Normalized asset path or stable backend texture identifier. */
    val id: String,
    /** Material texture channel this binding represents, when known. */
    val channel: String? = null,
    /** UV channel index used to sample the texture. */
    val uvChannel: Int = 0,
)

/**
 * Local-space model bounds extracted from a loaded backend model asset.
 */
data class ModelAssetBounds(
    /** Minimum model-space corner. */
    val min: Vec3,
    /** Maximum model-space corner. */
    val max: Vec3,
)

/**
 * Describes one backend-neutral texture slot assigned to a model material.
 */
data class ModelTextureSlotInfo(
    /** Normalized channel label such as diffuse, baseColor, normal, or metallicRoughness. */
    val channel: String,
    /** Source path or stable backend identifier for the assigned texture, when available. */
    val texturePath: String?,
    /** UV channel label used by this texture slot, when exposed by the backend. */
    val uvChannel: String?,
    /** Material index that owns this texture slot. */
    val materialIndex: Int,
    /** Material id that owns this texture slot, when available. */
    val materialId: String?,
)

/**
 * Describes one renderable mesh part in a loaded model.
 */
data class ModelMeshPartInfo(
    /** Stable index within the metadata snapshot. */
    val index: Int,
    /** Name/id of the node that owns the mesh part, when exposed by the backend. */
    val nodeName: String?,
    /** Backend mesh identifier, when available. */
    val meshId: String?,
    /** Backend mesh-part identifier, when available. */
    val partId: String?,
    /** Material identifier bound to this part, when available. */
    val materialId: String?,
    /** Material index bound to this part, when available. */
    val materialIndex: Int? = null,
    /** Primitive topology label such as TRIANGLES or TRIANGLE_STRIP. */
    val primitiveType: String?,
    /** Vertex count for the referenced mesh or part, when available. */
    val vertexCount: Int?,
    /** Triangle count for this mesh part, when the topology is supported. */
    val triangleCount: Int?,
)

/**
 * Describes one material in a loaded model.
 */
data class ModelMaterialInfo(
    /** Stable index within the metadata snapshot. */
    val index: Int,
    /** Backend material identifier, when available. */
    val id: String?,
    /** Diffuse/base-color texture reference, when available. */
    val diffuseTexture: String?,
    /** Normal texture reference, when available. */
    val normalTexture: String?,
    /** Emissive texture reference, when available. */
    val emissiveTexture: String?,
    /** Generic texture slot metadata. Prefer this over fixed legacy fields in new UI. */
    val textureSlots: List<ModelTextureSlotInfo> = emptyList(),
    /** Diffuse/base color, when available. */
    val baseColor: Color?,
    /** Material opacity, when available. */
    val opacity: Float?,
)

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
    /** Model-space bounding-box minimum corner, when available. */
    val boundsMin: Vec3? = null,
    /** Model-space bounding-box maximum corner, when available. */
    val boundsMax: Vec3? = null,
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
    /** Detailed mesh-part metadata for inspection UI. */
    val meshParts: List<ModelMeshPartInfo> = emptyList(),
    /** Detailed material metadata for inspection UI. */
    val materials: List<ModelMaterialInfo> = emptyList(),
)
