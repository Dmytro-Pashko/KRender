package com.pashkd.krender.engine.api

import com.pashkd.krender.engine.render3d.Material

/**
 * Immutable transform snapshot captured for rendering.
 */
data class TransformSnapshot(
    /** World-space position. */
    val position: Vec3 = Vec3.zero(),
    /** Quaternion orientation. */
    val rotation: Quat = Quat.identity(),
    /** Euler rotation in degrees, when available. */
    val eulerDegrees: Vec3 = Vec3.zero(),
    /** Non-uniform scale. */
    val scale: Vec3 = Vec3.one(),
)

/**
 * Backend-neutral draw request produced by render systems and consumed by renderers.
 */
sealed interface RenderCommand {
    /** Sort key used to order commands before submission. */
    val sortKey: Int
}

/**
 * Draw request for one file-backed or primitive model instance.
 */
data class DrawModel(
    /** Source entity for the draw request. */
    val entityId: EntityId,
    /** Asset reference for the model to draw. */
    val model: AssetRef<ModelAsset>,
    /** Captured transform used for rendering. */
    val transform: TransformSnapshot,
    /** Material parameters applied for rendering. */
    val material: Material,
    /** Optional backend-neutral mesh-part index filter. Null renders the full model. */
    val visibleMeshPartIndices: Set<Int>? = null,
    /** Optional material/texture debug rendering request. Null keeps the normal material path. */
    val debugView: MaterialDebugView? = null,
    /** Optional glTF PBR preview request. Debug rendering has priority when both are present. */
    val pbrPreview: PbrPreviewView? = null,
    /** Relative ordering priority for this command. */
    override val sortKey: Int = 0,
) : RenderCommand

/**
 * Backend-neutral material debug modes supported by shader debug renderers.
 */
enum class MaterialDebugMode {
    None,
    BaseColor,
    Normal,
    Emission,
    MetallicRoughness,
    Occlusion,
    Alpha,
    UvChecker,
}

/**
 * Backend-neutral culling behavior for debug preview passes.
 */
enum class DebugCullingMode {
    Backface,
    DoubleSided,
}

/**
 * Resolved texture slot for a material debug pass.
 */
data class MaterialDebugTextureRef(
    /** Material index that owns this texture, when known. */
    val materialIndex: Int?,
    /** Material id that owns this texture, when known. */
    val materialId: String?,
    /** Backend-neutral texture reference for this debug slot. */
    val texture: MaterialTextureRef,
)

/**
 * Backend-neutral request for shader-based material/texture debug rendering.
 */
data class MaterialDebugView(
    /** Typed debug mode requested by the producer system. */
    val mode: MaterialDebugMode,
    /** Optional material index to inspect. Null lets the backend resolve per material. */
    val selectedMaterialIndex: Int? = null,
    /** Optional exact texture channel chosen in the UI. Backends may also use mode aliases. */
    val selectedTextureChannel: String? = null,
    /** Resolved texture slots from model metadata. */
    val textureRefs: List<MaterialDebugTextureRef> = emptyList(),
    /** Texture override used by UV checker rendering. */
    val uvCheckerTexture: MaterialTextureRef? = null,
    /** UV channel index to sample. */
    val uvChannel: Int = 0,
    /** Multiplier applied to UV coordinates for checker rendering. */
    val uvScale: Float = 1f,
    /** Culling behavior for the debug render pass. */
    val culling: DebugCullingMode = DebugCullingMode.Backface,
) {
    val active: Boolean
        get() = mode != MaterialDebugMode.None
}

/**
 * Backend-neutral request for glTF PBR preview rendering.
 */
data class PbrPreviewView(
    val enabled: Boolean = false,
    val exposure: Float = 1f,
    val showSkybox: Boolean = true,
    val skyboxTexture: MaterialTextureRef? = null,
    val environmentIntensity: Float = 1f,
    val directionalLightEnabled: Boolean = true,
    val directionalLightYawDegrees: Float = 45f,
    val directionalLightPitchDegrees: Float = -35f,
)

/**
 * Backend-neutral mesh payload for runtime-generated geometry.
 *
 * Terrain uses this to render generated heightfield meshes without going through
 * file-backed asset loading.
 */
data class DynamicMesh(
    /** Packed XYZ vertex positions. */
    val positions: FloatArray,
    /** Packed XYZ vertex normals. */
    val normals: FloatArray,
    /** Packed UV texture coordinates. */
    val uvs: FloatArray,
    /** Triangle index buffer. */
    val indices: IntArray,
    /** Optional packed tangents. */
    val tangents: FloatArray? = null,
    /** Optional packed RGBA vertex colors. */
    val colors: FloatArray? = null,
) {
    /**
     * Number of vertices encoded in [positions].
     */
    val vertexCount: Int
        get() = positions.size / 3

    /**
     * Number of triangles represented by indices or expanded vertices.
     */
    val triangleCount: Int
        get() = if (indices.isNotEmpty()) indices.size / 3 else vertexCount / 3
}

/**
 * Versioned runtime model wrapper around [DynamicMesh].
 */
data class DynamicModel(
    /** Stable runtime identifier for this generated model. */
    val id: String,
    /** Mesh payload uploaded through the renderer. */
    val mesh: DynamicMesh,
    /** Monotonic revision used for cache invalidation. */
    val revision: Long = 0L,
)

/**
 * Draw command for runtime-generated geometry such as terrain.
 */
data class DrawDynamicModel(
    /** Source entity for the draw request. */
    val entityId: EntityId,
    /** Dynamic model payload to draw. */
    val model: DynamicModel,
    /** Captured transform used for rendering. */
    val transform: TransformSnapshot,
    /** Material parameters applied for rendering. */
    val material: Material,
    /** Runtime-generated textures that should be available while this command is rendered. */
    val runtimeTextures: List<RuntimeTextureData> = emptyList(),
    /** Relative ordering priority for this command. */
    override val sortKey: Int = 0,
) : RenderCommand

/**
 * Draw request for one world-space line segment.
 */
data class DrawLine(
    /** Line start point. */
    val from: Vec3,
    /** Line end point. */
    val to: Vec3,
    /** Line color. */
    val color: Color = Color.white(),
    /** Relative ordering priority for this command. */
    override val sortKey: Int = 10,
) : RenderCommand

/**
 * Draw request for the shared editor world grid.
 */
data class DrawWorldGrid(
    /** Grid extent in cells from the origin. */
    val halfExtentCells: Int = 20,
    /** World-space size of one grid cell. */
    val cellSize: Float = 1f,
    /** World-space height of the grid plane. */
    val y: Float = 0f,
    /** Grid line color. */
    val color: Color = Color(0.32f, 0.34f, 0.38f, 0.65f),
    /** Relative ordering priority for this command. */
    override val sortKey: Int = -20,
) : RenderCommand

/**
 * Draw request for the shared world-axis gizmo.
 */
data class DrawWorldAxes(
    /** Axis line length in world units. */
    val length: Float = 5f,
    /** Requested line width in screen pixels. */
    val lineWidthPixels: Float = 1f,
    /** Relative ordering priority for this command. */
    override val sortKey: Int = -10,
) : RenderCommand

/**
 * Draw request for backend-rendered screen text.
 */
data class DrawText(
    /** Text content to display. */
    val text: String,
    /** Screen-space anchor position. */
    val position: Vec2,
    /** Text color. */
    val color: Color = Color.white(),
    /** Relative ordering priority for this command. */
    override val sortKey: Int = 100,
) : RenderCommand

/**
 * Mutable collector used by systems to enqueue render commands for the frame.
 */
class RenderCommandBuffer {
    private val commands = mutableListOf<RenderCommand>()

    /** Appends one render command to the buffer. */
    fun submit(command: RenderCommand) {
        commands += command
    }

    /** Clears all buffered render commands. */
    fun clear() {
        commands.clear()
    }

    /** Returns a sorted immutable snapshot of buffered commands. */
    fun snapshot(): List<RenderCommand> = commands.sortedBy { it.sortKey }
}

/**
 * Immutable payload submitted to the renderer backend each frame.
 */
data class RenderContext(
    /** Active scene being rendered. */
    val scene: Scene,
    /** Fixed-step interpolation factor for this frame. */
    val alpha: Float,
    /** Variable frame delta in seconds. */
    val deltaSeconds: Float,
    /** Sorted draw commands collected for the frame. */
    val commands: List<RenderCommand>,
)

/**
 * Platform renderer that submits collected [RenderCommand] instances to the graphics API.
 */
interface Renderer {
    /** Renders the supplied frame context. */
    fun render(context: RenderContext)
    /** Updates backend state for a new surface size. */
    fun resize(width: Int, height: Int)
    /** Releases renderer resources. */
    fun dispose()
}
