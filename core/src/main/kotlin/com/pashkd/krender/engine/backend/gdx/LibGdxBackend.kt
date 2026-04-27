package com.pashkd.krender.engine.backend.gdx

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.Attribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader
import com.badlogic.gdx.graphics.g3d.model.MeshPart
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.graphics.g3d.model.NodePart
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.Pool
import com.badlogic.gdx.utils.UBJsonReader
import com.pashkd.krender.engine.api.Action
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.AssetService
import com.pashkd.krender.engine.api.Axis
import com.pashkd.krender.engine.api.DrawDynamicModel
import com.pashkd.krender.engine.api.DrawLine
import com.pashkd.krender.engine.api.DrawModel
import com.pashkd.krender.engine.api.DrawWorldAxes
import com.pashkd.krender.engine.api.DrawWorldGrid
import com.pashkd.krender.engine.api.EngineLogService
import com.pashkd.krender.engine.api.EngineBackend
import com.pashkd.krender.engine.api.EngineRuntime
import com.pashkd.krender.engine.api.FrameProfilerService
import com.pashkd.krender.engine.api.FrameRuntimeStatsService
import com.pashkd.krender.engine.api.InputService
import com.pashkd.krender.engine.api.InputSnapshot
import com.pashkd.krender.engine.api.Key
import com.pashkd.krender.engine.api.LogService
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.MainThreadTaskQueue
import com.pashkd.krender.engine.api.ModelAsset
import com.pashkd.krender.engine.api.ModelAssetInfo
import com.pashkd.krender.engine.api.PointerPhase
import com.pashkd.krender.engine.api.PointerState
import com.pashkd.krender.engine.api.RenderContext
import com.pashkd.krender.engine.api.Renderer
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.api.ShaderAsset
import com.pashkd.krender.engine.api.TaskService
import com.pashkd.krender.engine.api.TextureAsset
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.api.ProfilerService
import com.pashkd.krender.engine.api.RuntimeStatsService
import com.pashkd.krender.engine.api.Vec2
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.ui.UiCaptureState
import com.pashkd.krender.engine.ui.UiService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.mgsx.gltf.loaders.glb.GLBAssetLoader
import net.mgsx.gltf.loaders.gltf.GLTFAssetLoader
import net.mgsx.gltf.scene3d.scene.Scene as GltfScene
import net.mgsx.gltf.scene3d.scene.SceneAsset
import net.mgsx.gltf.scene3d.utils.MaterialConverter
import kotlin.coroutines.CoroutineContext
import kotlin.math.cos
import kotlin.math.sin

/**
 * LibGDX application entry point that bootstraps the engine runtime.
 */
open class GdxEngineApplication(
    private val initialScene: () -> Scene,
) : ApplicationAdapter() {
    private lateinit var backend: LibGdxBackend
    private lateinit var runtime: EngineRuntime

    /** Creates the backend and starts the initial scene. */
    override fun create() {
        backend = LibGdxBackend()
        Gdx.input.isCursorCatched = false
        runtime = EngineRuntime(backend)
        runtime.start(initialScene())
    }

    /** Renders one engine frame using LibGDX delta time. */
    override fun render() {
        runtime.renderFrame(Gdx.graphics.deltaTime)
    }

    /** Forwards window resize events to the runtime. */
    override fun resize(width: Int, height: Int) {
        runtime.resize(width, height)
    }

    /** Disposes the runtime and all backend resources. */
    override fun dispose() {
        runtime.dispose()
    }
}

/**
 * Concrete engine backend that wires LibGDX services into the core runtime.
 */
class LibGdxBackend : EngineBackend {
    override val runtimeStats: RuntimeStatsService = FrameRuntimeStatsService()
    override val logs: EngineLogService = EngineLogService(frameProvider = runtimeStats::frame).also { service ->
        service.addSink(GdxAppLogSink())
        service.addSink(FileLogSink())
    }
    override val profiler: ProfilerService = FrameProfilerService()
    override val logger: Logger = logs
    override val input: GdxInputService = GdxInputService().also {
        Gdx.input.inputProcessor = it
    }
    override val ui: UiService = GdxImGuiService(input, runtimeStats)
    override val assets: GdxAssetService = GdxAssetService()
    override val tasks: TaskService = GdxTaskService()
    override val renderer: Renderer = GdxRenderer3D(assets, ui)

    /** Requests application shutdown through the LibGDX app instance. */
    override fun requestExit() {
        Gdx.app.exit()
    }
}

/**
 * LibGDX-backed input service that snapshots keyboard, mouse, pointer, and UI capture state.
 */
class GdxInputService : InputService, InputProcessor {
    private val keysDown = mutableSetOf<Key>()
    private val pressedThisFrame = mutableSetOf<Key>()
    private val releasedThisFrame = mutableSetOf<Key>()
    private val pointers = mutableMapOf<Int, PointerState>()
    private val processors = mutableListOf<InputProcessor>()
    private val actions = mapOf(
        Action("MoveLeft") to setOf(Key.A),
        Action("MoveRight") to setOf(Key.D),
        Action("MoveForward") to setOf(Key.W),
        Action("MoveBackward") to setOf(Key.S),
        Action("Jump") to setOf(Key.Space),
    )

    private var currentSnapshot = InputSnapshot()
    private var uiCaptureProvider: () -> UiCaptureState = { UiCaptureState() }
    private var mouseX: Int = 0
    private var mouseY: Int = 0
    private var mouseDeltaX: Float = 0f
    private var mouseDeltaY: Float = 0f
    private var scrollDelta: Float = 0f
    private var cursorCaptured: Boolean = false
    private var ignoredWarpX: Int? = null
    private var ignoredWarpY: Int? = null

    /** Captures the current raw input state into the frame snapshot. */
    override fun beginFrame() {
        val uiCapture = uiCaptureProvider()
        currentSnapshot = InputSnapshot(
            keysDown = keysDown.toSet(),
            keysPressedThisFrame = pressedThisFrame.toSet(),
            keysReleasedThisFrame = releasedThisFrame.toSet(),
            mousePosition = Vec2(mouseX.toFloat(), mouseY.toFloat()),
            mouseDelta = Vec2(mouseDeltaX, mouseDeltaY),
            scrollDelta = scrollDelta,
            pointers = pointers.values.toList(),
            viewportSize = Vec2(Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat()),
            uiCapturesMouse = uiCapture.mouse,
            uiCapturesKeyboard = uiCapture.keyboard,
        )
        keepCursorInsideWindow()
    }

    /** Returns the immutable input snapshot prepared in [beginFrame]. */
    override fun snapshot(): InputSnapshot = currentSnapshot

    /** Clears one-frame input deltas and retires finished pointer events. */
    override fun endFrame() {
        pressedThisFrame.clear()
        releasedThisFrame.clear()
        mouseDeltaX = 0f
        mouseDeltaY = 0f
        scrollDelta = 0f
        pointers.entries.removeIf { it.value.phase == PointerPhase.Up || it.value.phase == PointerPhase.Cancelled }
    }

    /** Enables or disables cursor recentering used for captured mouse input. */
    override fun setCursorCaptured(captured: Boolean) {
        if (cursorCaptured == captured) return
        cursorCaptured = captured
        Gdx.input.isCursorCatched = false
        mouseX = Gdx.input.x
        mouseY = Gdx.input.y
        mouseDeltaX = 0f
        mouseDeltaY = 0f
        if (captured) {
            moveCursorToWindowCenter()
        } else {
            ignoredWarpX = null
            ignoredWarpY = null
        }
    }

    /** Returns whether any key bound to the action is currently held. */
    override fun isActionPressed(action: Action): Boolean =
        actions[action]?.any { it in currentSnapshot.keysDown } == true

    /** Returns whether any key bound to the action was pressed this frame. */
    override fun isActionJustPressed(action: Action): Boolean =
        actions[action]?.any { it in currentSnapshot.keysPressedThisFrame } == true

    /** Resolves named digital axes from the current snapshot. */
    override fun axis(axis: Axis): Float = when (axis.name) {
        "Horizontal" -> currentSnapshot.booleanAxis(Key.A, Key.D)
        "Vertical" -> currentSnapshot.booleanAxis(Key.S, Key.W)
        else -> 0f
    }

    /** Registers an additional LibGDX input processor for mirrored callbacks. */
    fun addProcessor(processor: InputProcessor) {
        processors += processor
    }

    /** Unregisters a mirrored LibGDX input processor. */
    fun removeProcessor(processor: InputProcessor) {
        processors -= processor
    }

    /** Supplies the current UI capture state used when building snapshots. */
    fun setUiCaptureProvider(provider: () -> UiCaptureState) {
        uiCaptureProvider = provider
    }

    /** Records a key-down event and forwards it to child processors. */
    override fun keyDown(keycode: Int): Boolean {
        val key = mapKey(keycode)
        if (key !in keysDown) {
            pressedThisFrame += key
        }
        keysDown += key
        processors.forEach { it.keyDown(keycode) }
        return false
    }

    /** Records a key-up event and forwards it to child processors. */
    override fun keyUp(keycode: Int): Boolean {
        val key = mapKey(keycode)
        keysDown -= key
        releasedThisFrame += key
        processors.forEach { it.keyUp(keycode) }
        return false
    }

    /** Updates mouse position and delta, ignoring synthetic warp events when needed. */
    override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
        if (screenX == ignoredWarpX && screenY == ignoredWarpY) {
            ignoredWarpX = null
            ignoredWarpY = null
        } else {
            ignoredWarpX = null
            ignoredWarpY = null
            mouseDeltaX += screenX - mouseX
            mouseDeltaY += screenY - mouseY
        }
        mouseX = screenX
        mouseY = screenY
        processors.forEach { it.mouseMoved(screenX, screenY) }
        return false
    }

    /** Treats a dragged pointer as both mouse movement and pointer motion. */
    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        mouseMoved(screenX, screenY)
        pointers[pointer] = PointerState(pointer, PointerPhase.Move, Vec2(screenX.toFloat(), screenY.toFloat()))
        processors.forEach { it.touchDragged(screenX, screenY, pointer) }
        return false
    }

    /** Starts a tracked pointer interaction. */
    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        mouseMoved(screenX, screenY)
        pointers[pointer] = PointerState(pointer, PointerPhase.Down, Vec2(screenX.toFloat(), screenY.toFloat()))
        processors.forEach { it.touchDown(screenX, screenY, pointer, button) }
        return false
    }

    /** Ends a tracked pointer interaction. */
    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        mouseMoved(screenX, screenY)
        pointers[pointer] = PointerState(pointer, PointerPhase.Up, Vec2(screenX.toFloat(), screenY.toFloat()))
        processors.forEach { it.touchUp(screenX, screenY, pointer, button) }
        return false
    }

    /** Marks a tracked pointer as cancelled. */
    override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        mouseMoved(screenX, screenY)
        pointers[pointer] = PointerState(pointer, PointerPhase.Cancelled, Vec2(screenX.toFloat(), screenY.toFloat()))
        processors.forEach { it.touchCancelled(screenX, screenY, pointer, button) }
        return false
    }

    /** Accumulates scroll input for the current frame. */
    override fun scrolled(amountX: Float, amountY: Float): Boolean {
        scrollDelta += amountY
        processors.forEach { it.scrolled(amountX, amountY) }
        return false
    }

    /** Forwards typed-character events to child processors. */
    override fun keyTyped(character: Char): Boolean {
        processors.forEach { it.keyTyped(character) }
        return false
    }

    /** Maps LibGDX key codes to engine key enums. */
    private fun mapKey(keycode: Int): Key = when (keycode) {
        Input.Keys.W -> Key.W
        Input.Keys.A -> Key.A
        Input.Keys.S -> Key.S
        Input.Keys.D -> Key.D
        Input.Keys.G -> Key.G
        Input.Keys.R -> Key.R
        Input.Keys.F -> Key.F
        Input.Keys.Y -> Key.Y
        Input.Keys.Z -> Key.Z
        Input.Keys.Q -> Key.Q
        Input.Keys.E -> Key.E
        Input.Keys.F1 -> Key.F1
        Input.Keys.F2 -> Key.F2
        Input.Keys.F3 -> Key.F3
        Input.Keys.F4 -> Key.F4
        Input.Keys.F5 -> Key.F5
        Input.Keys.GRAVE -> Key.Backtick
        Input.Keys.SPACE -> Key.Space
        Input.Keys.ESCAPE -> Key.Escape
        Input.Keys.TAB -> Key.Tab
        Input.Keys.SHIFT_LEFT -> Key.ShiftLeft
        Input.Keys.SHIFT_RIGHT -> Key.ShiftRight
        Input.Keys.CONTROL_LEFT -> Key.ControlLeft
        Input.Keys.CONTROL_RIGHT -> Key.ControlRight
        Input.Keys.ALT_LEFT -> Key.AltLeft
        Input.Keys.ALT_RIGHT -> Key.AltRight
        else -> Key.Unknown
    }

    /** Converts two digital keys into a -1..1 axis value. */
    private fun InputSnapshot.booleanAxis(negative: Key, positive: Key): Float {
        val left = if (isDown(negative)) 1f else 0f
        val right = if (isDown(positive)) 1f else 0f
        return right - left
    }

    /** Recenters the cursor if captured input reaches the window edge. */
    private fun keepCursorInsideWindow() {
        if (!cursorCaptured) return

        val width = Gdx.graphics.width
        val height = Gdx.graphics.height
        if (
            mouseX <= CURSOR_EDGE_MARGIN ||
            mouseY <= CURSOR_EDGE_MARGIN ||
            mouseX >= width - CURSOR_EDGE_MARGIN ||
            mouseY >= height - CURSOR_EDGE_MARGIN
        ) {
            moveCursorToWindowCenter()
        }
    }

    /** Warps the cursor to the window center and suppresses the synthetic move event. */
    private fun moveCursorToWindowCenter() {
        val centerX = Gdx.graphics.width / 2
        val centerY = Gdx.graphics.height / 2
        ignoredWarpX = centerX
        ignoredWarpY = centerY
        mouseX = centerX
        mouseY = centerY
        Gdx.input.setCursorPosition(centerX, centerY)
    }

    companion object {
        private const val CURSOR_EDGE_MARGIN = 24
    }
}

/**
 * Asset service that queues, loads, inspects, and unloads LibGDX-backed assets.
 */
class GdxAssetService : AssetService {
    private val manager = AssetManager()
    private val requested = mutableSetOf<String>()
    private val missing = mutableSetOf<String>()
    private val shaderSources = mutableMapOf<String, String>()
    private val modelInfos = mutableMapOf<String, ModelAssetInfo>()
    private val modelTriangleCounts = mutableMapOf<String, Int>()

    init {
        manager.setLoader(Model::class.java, ".g3dj", G3dModelLoader(JsonReader()))
        manager.setLoader(Model::class.java, ".g3db", G3dModelLoader(UBJsonReader()))
        manager.setLoader(Model::class.java, ".obj", ObjLoader())
        manager.setLoader(SceneAsset::class.java, ".gltf", GLTFAssetLoader())
        manager.setLoader(SceneAsset::class.java, ".glb", GLBAssetLoader())
    }

    /** Queues a supported asset for loading if it exists and is not already tracked. */
    override fun queue(asset: AssetRef<*>) {
        if (asset.isPrimitive || asset.path in requested || asset.path in missing) return

        when (asset.type) {
            ModelAsset::class -> {
                if (!Gdx.files.internal(asset.path).exists()) {
                    missing += asset.path
                    return
                }
                requested += asset.path
                if (asset.isGltf()) {
                    manager.load(asset.path, SceneAsset::class.java)
                } else {
                    manager.load(asset.path, Model::class.java)
                }
            }

            TextureAsset::class -> {
                if (!Gdx.files.internal(asset.path).exists()) {
                    missing += asset.path
                    return
                }
                requested += asset.path
                manager.load(asset.path, Texture::class.java)
            }

            ShaderAsset::class -> {
                val file = Gdx.files.internal(asset.path)
                if (file.exists()) {
                    requested += asset.path
                    shaderSources[asset.path] = file.readString()
                } else {
                    missing += asset.path
                }
            }
        }
    }

    /** Advances asynchronous loading for up to the given time budget. */
    override fun update(budgetMs: Int): Float {
        manager.update(budgetMs)
        return progress()
    }

    /** Returns normalized loading progress for queued non-primitive assets. */
    override fun progress(): Float {
        val assetCount = requested.count { !it.startsWith("primitive:") }
        if (assetCount == 0) return 1f
        return manager.progress.coerceIn(0f, 1f)
    }

    /** Returns whether the asset is available through the underlying backend loaders. */
    override fun isLoaded(asset: AssetRef<*>): Boolean {
        if (asset.isPrimitive) return true
        return when (asset.type) {
            ModelAsset::class -> {
                if (asset.isGltf()) {
                    manager.isLoaded(asset.path, SceneAsset::class.java)
                } else {
                    manager.isLoaded(asset.path, Model::class.java)
                }
            }

            TextureAsset::class -> manager.isLoaded(asset.path)
            ShaderAsset::class -> asset.path in shaderSources
            else -> false
        }
    }

    /** Rejects untyped access because core code should keep using asset references. */
    override fun <T : Any> get(asset: AssetRef<T>): T {
        error("Use backend-specific typed accessors for '${asset.path}'. Core code should keep AssetRef handles.")
    }

    /** Returns the triangle count for a loaded backend model, caching the result. */
    override fun triangleCount(asset: AssetRef<ModelAsset>): Int? {
        if (asset.isPrimitive || !isLoaded(asset)) return null
        return modelTriangleCounts.getOrPut(asset.path) {
            when {
                asset.isGltf() -> gltfScene(asset)?.scene?.model?.let(::countTrianglesInModel) ?: 0
                else -> gdxModel(asset)?.let(::countTrianglesInModel) ?: 0
            }
        }
    }

    /**
     * Builds a stable metadata snapshot for the currently loaded model asset.
     */
    override fun modelInfo(asset: AssetRef<ModelAsset>): ModelAssetInfo? {
        if (asset.isPrimitive || !isLoaded(asset)) return null
        return modelInfos.getOrPut(asset.path) {
            when {
                asset.isGltf() -> {
                    val sceneAsset = gltfScene(asset)
                    val model = sceneAsset?.scene?.model
                    if (model == null) {
                        emptyModelInfo(asset.path, "glTF")
                    } else {
                        buildModelInfo(asset.path, model, sceneAsset)
                    }
                }

                else -> gdxModel(asset)?.let { model ->
                    buildModelInfo(asset.path, model, sceneAsset = null)
                } ?: emptyModelInfo(asset.path, modelFormat(asset.path))
            }
        }
    }

    /** Unloads a tracked asset and clears any cached metadata derived from it. */
    override fun unload(asset: AssetRef<*>) {
        if (!asset.isPrimitive && manager.isLoaded(asset.path)) {
            manager.unload(asset.path)
        }
        requested -= asset.path
        missing -= asset.path
        shaderSources -= asset.path
        modelInfos -= asset.path
        modelTriangleCounts -= asset.path
    }

    /** Returns a loaded LibGDX model for non-glTF model assets. */
    fun gdxModel(asset: AssetRef<ModelAsset>): Model? {
        if (asset.isPrimitive || asset.isGltf()) return null
        return if (manager.isLoaded(asset.path)) manager.get(asset.path, Model::class.java) else null
    }

    /** Returns a loaded glTF scene asset when the asset reference points to glTF content. */
    fun gltfScene(asset: AssetRef<ModelAsset>): SceneAsset? {
        if (!asset.isGltf()) return null
        return if (manager.isLoaded(asset.path, SceneAsset::class.java)) {
            manager.get(asset.path, SceneAsset::class.java)
        } else {
            null
        }
    }

    /** Disposes the underlying LibGDX asset manager. */
    fun dispose() {
        manager.dispose()
    }

    /**
     * Extracts render-relevant metadata from the loaded model and optional glTF scene asset.
     */
    private fun buildModelInfo(path: String, model: Model, sceneAsset: SceneAsset?): ModelAssetInfo {
        val nodes = collectNodes(model.nodes)
        val nodeParts = nodes.flatMap(::nodePartsOf)
        val bounds = BoundingBox()
        val dimensions = model.calculateBoundingBox(bounds).getDimensions(Vector3())
        val attributeSummary = collectVertexAttributeSummary(model.meshes)
        val textureChannels = linkedSetOf<String>()
        val textures = linkedSetOf<Texture>()
        var textureSlotCount = 0
        var maxBonesPerPart = 0

        model.materials.forEach { material ->
            for (attribute in material) {
                if (attribute is TextureAttribute) {
                    textureSlotCount += 1
                    textureChannels += (Attribute.getAttributeAlias(attribute.type) ?: "texture")
                    textures += attribute.textureDescription.texture
                }
            }
        }

        nodeParts.forEach { part ->
            maxBonesPerPart = maxOf(maxBonesPerPart, part.bones?.size ?: 0)
        }

        val animationNames = model.animations.mapNotNull { animation -> animation.id?.takeIf(String::isNotBlank) }
        val textureCount = sceneAsset?.textures?.size ?: textures.size
        return ModelAssetInfo(
            path = path,
            format = modelFormat(path),
            nodeCount = nodes.size,
            meshCount = model.meshes.size,
            meshPartCount = model.meshParts.size,
            materialCount = model.materials.size,
            vertexCount = model.meshes.sumOf { mesh -> mesh.numVertices },
            triangleCount = triangleCount(asset = AssetRef.model(path)) ?: countTrianglesInModel(model),
            size = Vec3(dimensions.x, dimensions.y, dimensions.z),
            vertexChannels = attributeSummary.vertexChannels.toList(),
            uvChannels = attributeSummary.uvChannels.toList(),
            textureChannels = textureChannels.toList(),
            textureCount = textureCount,
            textureSlotCount = textureSlotCount,
            hasSkeleton = maxBonesPerPart > 0,
            boneCount = maxBonesPerPart,
            boneWeightChannelCount = attributeSummary.boneWeightChannelCount,
            animationCount = model.animations.size,
            animationNames = animationNames,
        )
    }

    /**
     * Returns a safe empty metadata snapshot when a backend model cannot be inspected.
     */
    private fun emptyModelInfo(path: String, format: String): ModelAssetInfo = ModelAssetInfo(
        path = path,
        format = format,
        nodeCount = 0,
        meshCount = 0,
        meshPartCount = 0,
        materialCount = 0,
        vertexCount = 0,
        triangleCount = 0,
        size = null,
        vertexChannels = emptyList(),
        uvChannels = emptyList(),
        textureChannels = emptyList(),
        textureCount = 0,
        textureSlotCount = 0,
        hasSkeleton = false,
        boneCount = 0,
        boneWeightChannelCount = 0,
        animationCount = 0,
        animationNames = emptyList(),
    )
}

/**
 * Describes the collected mesh vertex channels for one loaded model.
 */
private data class VertexAttributeSummary(
    /** Unique vertex channel labels across every mesh. */
    val vertexChannels: LinkedHashSet<String>,
    /** Unique UV channel labels across every mesh. */
    val uvChannels: LinkedHashSet<String>,
    /** Highest number of bone-weight channels available per vertex. */
    val boneWeightChannelCount: Int,
)

/**
 * Flattens the full node hierarchy into one list for logging and inspection.
 */
private fun collectNodes(nodes: Array<Node>): List<Node> {
    val collected = mutableListOf<Node>()

    fun visit(node: Node) {
        collected += node
        for (child in node.children) {
            visit(child)
        }
    }

    for (node in nodes) {
        visit(node)
    }
    return collected
}

/**
 * Returns every node part attached to the given node.
 */
private fun nodePartsOf(node: Node): List<NodePart> = buildList {
    for (part in node.parts) {
        add(part)
    }
}

/**
 * Extracts vertex-channel, UV, and skin-weight information from every mesh.
 */
private fun collectVertexAttributeSummary(meshes: Array<Mesh>): VertexAttributeSummary {
    val vertexChannels = linkedSetOf<String>()
    val uvChannels = linkedSetOf<String>()
    var boneWeightChannelCount = 0

    meshes.forEach { mesh ->
        for (attribute in mesh.vertexAttributes) {
            when (attribute.usage) {
                VertexAttributes.Usage.Position -> vertexChannels += "Position"
                VertexAttributes.Usage.Normal -> vertexChannels += "Normal"
                VertexAttributes.Usage.ColorPacked,
                VertexAttributes.Usage.ColorUnpacked,
                -> vertexChannels += "Color"
                VertexAttributes.Usage.Tangent -> vertexChannels += "Tangent"
                VertexAttributes.Usage.BiNormal -> vertexChannels += "Binormal"
                VertexAttributes.Usage.TextureCoordinates -> {
                    val channel = "UV${attribute.unit}"
                    uvChannels += channel
                    vertexChannels += channel
                }

                VertexAttributes.Usage.BoneWeight -> {
                    val channel = "BoneWeight${attribute.unit}"
                    vertexChannels += channel
                    boneWeightChannelCount = maxOf(boneWeightChannelCount, attribute.unit + 1)
                }

                else -> vertexChannels += attribute.alias.ifBlank { "Usage${attribute.usage}" }
            }
        }
    }

    return VertexAttributeSummary(vertexChannels, uvChannels, boneWeightChannelCount)
}

/**
 * Formats the asset path extension as a readable model-format label.
 */
private fun modelFormat(path: String): String = when {
    path.endsWith(".glb", ignoreCase = true) || path.endsWith(".gltf", ignoreCase = true) -> "glTF"
    path.endsWith(".obj", ignoreCase = true) -> "OBJ"
    path.endsWith(".g3dj", ignoreCase = true) -> "G3DJ"
    path.endsWith(".g3db", ignoreCase = true) -> "G3DB"
    else -> "Model"
}

/** Returns the total triangle count across all mesh parts in the model. */
private fun countTrianglesInModel(model: Model): Int =
    model.meshParts.sumOf(::countTrianglesInMeshPart)

/** Converts a mesh part's primitive topology into triangle count. */
private fun countTrianglesInMeshPart(meshPart: MeshPart): Int = when (meshPart.primitiveType) {
    GL20.GL_TRIANGLES -> meshPart.size / 3
    GL20.GL_TRIANGLE_STRIP, GL20.GL_TRIANGLE_FAN -> (meshPart.size - 2).coerceAtLeast(0)
    else -> 0
}

/**
 * Coroutine-backed task service with explicit background, IO, and render-thread dispatch.
 */
class GdxTaskService : TaskService {
    private val job = SupervisorJob()
    private val backgroundScope = CoroutineScope(job + Dispatchers.Default)
    private val mainQueue = MainThreadTaskQueue()
    private val mainDispatcher = RenderThreadDispatcher()
    private val jobs = mutableSetOf<Job>()

    override val inFlightJobs: Int
        get() = jobs.count { it.isActive }

    /** Launches a tracked background coroutine. */
    override fun launchBackground(name: String, block: suspend CoroutineScope.() -> Unit): Job {
        val launched = backgroundScope.launch(block = block)
        jobs += launched
        launched.invokeOnCompletion { jobs -= launched }
        return launched
    }

    /** Runs the block on the default background dispatcher. */
    override suspend fun <T> onBackground(block: suspend () -> T): T = withContext(Dispatchers.Default) { block() }

    /** Runs the block on the IO dispatcher. */
    override suspend fun <T> onIo(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    /** Runs the block on the render thread dispatcher. */
    override suspend fun <T> onMain(block: suspend () -> T): T = withContext(mainDispatcher) { block() }

    /** Queues a task for the main-thread task queue. */
    override fun postToMain(block: () -> Unit) {
        mainQueue.post(block)
    }

    /** Executes all queued main-thread tasks immediately. */
    override fun flushMainThreadQueue() {
        mainQueue.flush()
    }

    /** Cancels all background work owned by the service. */
    override fun dispose() {
        backgroundScope.cancel()
        job.cancel()
    }
}

/** Coroutine dispatcher that posts work onto the LibGDX application thread. */
class RenderThreadDispatcher : CoroutineDispatcher() {
    /** Schedules the runnable through LibGDX's `postRunnable` callback queue. */
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        Gdx.app.postRunnable(block)
    }
}

/**
 * LibGDX renderer that draws static models, dynamic meshes, wireframes, lines, and UI.
 */
class GdxRenderer3D(
    private val assets: GdxAssetService,
    private val ui: UiService,
) : Renderer {
    private val modelBatch = ModelBatch()
    private val lineRenderer = GdxLineShaderRenderer()
    private val instances = mutableMapOf<ModelCacheKey, ModelInstance>()
    private val gltfScenes = mutableMapOf<ModelCacheKey, GltfScene>()
    private val primitives = mutableMapOf<String, Model>()
    private val dynamicModels = mutableMapOf<String, DynamicModelCacheEntry>()
    private val wireframeRenderables = Array<Renderable>()
    private val wireframeRenderablePool = object : Pool<Renderable>() {
        override fun newObject(): Renderable = Renderable()
    }
    private val wireframeTmpVertex = Vector3()

    private var width: Int = Gdx.graphics.width
    private var height: Int = Gdx.graphics.height

    /** Renders the full frame for the provided render context. */
    override fun render(context: RenderContext) {
        Gdx.gl.glViewport(0, 0, width, height)
        Gdx.gl.glClearColor(0.08f, 0.09f, 0.11f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        val camera = cameraFor(context)
        val environment = environmentFor(context)
        val wireframeCommands = mutableListOf<DrawModel>()
        val wireframeDynamicCommands = mutableListOf<DrawDynamicModel>()

        lineRenderer.render(context.commands, camera)

        modelBatch.begin(camera)
        context.commands.forEach { command ->
            when (command) {
                is DrawModel -> {
                    if (command.material.wireframe) {
                        wireframeCommands += command
                    } else {
                        renderModel(command, environment, camera)
                    }
                }
                is DrawDynamicModel -> {
                    if (command.material.wireframe) {
                        wireframeDynamicCommands += command
                    } else {
                        renderDynamicModel(command, environment)
                    }
                }
                else -> Unit
            }
        }
        modelBatch.end()
        wireframeCommands.forEach { renderWireframeModel(it, camera) }
        wireframeDynamicCommands.forEach { renderWireframeDynamicModel(it, camera) }
        lineRenderer.renderOverlayLines(context.commands, camera)

        ui.render()
    }

    /** Updates the cached viewport size used for camera creation. */
    override fun resize(width: Int, height: Int) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
    }

    /** Disposes renderer-owned GPU resources and cached backend assets. */
    override fun dispose() {
        modelBatch.dispose()
        lineRenderer.dispose()
        primitives.values.forEach { it.dispose() }
        dynamicModels.values.forEach { it.model.dispose() }
        assets.dispose()
    }

    /** Renders one static model command, including primitive and glTF handling. */
    private fun renderModel(command: DrawModel, environment: Environment, camera: Camera) {
        command.material.shader.assets().forEach(assets::queue)
        assets.queue(command.model)

        val model = if (command.model.isPrimitive) {
            primitive(command.model.path)
        } else if (command.model.isGltf()) {
            renderGltfScene(command, environment, camera)
            return
        } else {
            assets.gdxModel(command.model)
        } ?: return

        val cacheKey = ModelCacheKey(command.entityId, command.model.path)
        val existing = instances[cacheKey]
        val instance = if (existing?.model === model) {
            existing
        } else {
            ModelInstance(model).also { created ->
                instances[cacheKey] = created
            }
        }

        instance.materials.forEach { applyMaterialAttributes(it, command.material) }

        val transform = command.transform
        instance.transform.idt()
        instance.transform.translate(transform.position.x, transform.position.y, transform.position.z)
        instance.transform.rotate(Vector3.X, transform.eulerDegrees.x)
        instance.transform.rotate(Vector3.Y, transform.eulerDegrees.y)
        instance.transform.rotate(Vector3.Z, transform.eulerDegrees.z)
        instance.transform.scale(transform.scale.x, transform.scale.y, transform.scale.z)

        modelBatch.render(instance, environment)
    }

    /** Renders one dynamic mesh-backed model command. */
    private fun renderDynamicModel(command: DrawDynamicModel, environment: Environment) {
        val model = dynamicGdxModel(command.model)
        val cacheKey = ModelCacheKey(command.entityId, command.model.id)
        val existing = instances[cacheKey]
        val instance = if (existing?.model === model) {
            existing
        } else {
            ModelInstance(model).also { created ->
                instances[cacheKey] = created
            }
        }

        instance.materials.forEach { applyMaterialAttributes(it, command.material) }

        applyTransform(instance, command.transform)
        modelBatch.render(instance, environment)
    }

    /** Applies engine material color and optional diffuse texture to a LibGDX material. */
    private fun applyMaterialAttributes(
        gdxMaterial: com.badlogic.gdx.graphics.g3d.Material,
        material: com.pashkd.krender.engine.render3d.Material,
    ) {
        gdxMaterial.set(
            ColorAttribute.createDiffuse(
                material.baseColor.r,
                material.baseColor.g,
                material.baseColor.b,
                material.baseColor.a,
            ),
        )
        val diffuseTexture = material.diffuseTexture
        if (diffuseTexture != null) {
            gdxMaterial.set(TextureAttribute.createDiffuse(diffuseTexture))
        } else {
            gdxMaterial.remove(TextureAttribute.Diffuse)
        }
    }

    /** Renders a cached glTF scene instance. */
    private fun renderGltfScene(command: DrawModel, environment: Environment, camera: Camera) {
        assets.queue(command.model)
        val sceneAsset = assets.gltfScene(command.model) ?: return
        val cacheKey = ModelCacheKey(command.entityId, command.model.path)
        val scene = gltfScenes.getOrPut(cacheKey) {
            GltfScene(sceneAsset.scene).also(MaterialConverter::makeCompatible)
        }

        val transform = command.transform
        scene.modelInstance.transform.idt()
        scene.modelInstance.transform.translate(transform.position.x, transform.position.y, transform.position.z)
        scene.modelInstance.transform.rotate(Vector3.X, transform.eulerDegrees.x)
        scene.modelInstance.transform.rotate(Vector3.Y, transform.eulerDegrees.y)
        scene.modelInstance.transform.rotate(Vector3.Z, transform.eulerDegrees.z)
        scene.modelInstance.transform.scale(transform.scale.x, transform.scale.y, transform.scale.z)
        scene.update(camera, Gdx.graphics.deltaTime)
        modelBatch.render(scene, environment)
    }

    /** Converts a static model command into line vertices and draws it as wireframe. */
    private fun renderWireframeModel(command: DrawModel, camera: Camera) {
        command.material.shader.assets().forEach(assets::queue)
        assets.queue(command.model)

        val instance = if (command.model.isGltf()) {
            val scene = wireframeGltfScene(command, camera) ?: return
            scene.modelInstance
        } else {
            val model = if (command.model.isPrimitive) {
                primitive(command.model.path)
            } else {
                assets.gdxModel(command.model)
            } ?: return

            val cacheKey = ModelCacheKey(command.entityId, command.model.path)
            val existing = instances[cacheKey]
            if (existing?.model === model) {
                existing
            } else {
                ModelInstance(model).also { created ->
                    instances[cacheKey] = created
                }
            }.also { applyTransform(it, command) }
        }

        val vertices = wireframeVerticesFor(instance, command.material.baseColor)
        lineRenderer.renderVertices(vertices, camera)
    }

    /** Converts a dynamic model command into line vertices and draws it as wireframe. */
    private fun renderWireframeDynamicModel(command: DrawDynamicModel, camera: Camera) {
        val model = dynamicGdxModel(command.model)
        val cacheKey = ModelCacheKey(command.entityId, command.model.id)
        val existing = instances[cacheKey]
        val instance = if (existing?.model === model) {
            existing
        } else {
            ModelInstance(model).also { created ->
                instances[cacheKey] = created
            }
        }

        applyTransform(instance, command.transform)
        val vertices = wireframeVerticesFor(instance, command.material.baseColor)
        lineRenderer.renderVertices(vertices, camera)
    }

    /** Returns a transformed glTF scene instance for wireframe extraction. */
    private fun wireframeGltfScene(command: DrawModel, camera: Camera): GltfScene? {
        val sceneAsset = assets.gltfScene(command.model) ?: return null
        val cacheKey = ModelCacheKey(command.entityId, command.model.path)
        val scene = gltfScenes.getOrPut(cacheKey) {
            GltfScene(sceneAsset.scene).also(MaterialConverter::makeCompatible)
        }
        applyTransform(scene.modelInstance, command)
        scene.update(camera, Gdx.graphics.deltaTime)
        return scene
    }

    /** Applies a draw command's transform to the instance. */
    private fun applyTransform(instance: ModelInstance, command: DrawModel) {
        applyTransform(instance, command.transform)
    }

    /** Applies an engine transform snapshot to the LibGDX model instance. */
    private fun applyTransform(
        instance: ModelInstance,
        transform: com.pashkd.krender.engine.api.TransformSnapshot,
    ) {
        instance.transform.idt()
        instance.transform.translate(transform.position.x, transform.position.y, transform.position.z)
        instance.transform.rotate(Vector3.X, transform.eulerDegrees.x)
        instance.transform.rotate(Vector3.Y, transform.eulerDegrees.y)
        instance.transform.rotate(Vector3.Z, transform.eulerDegrees.z)
        instance.transform.scale(transform.scale.x, transform.scale.y, transform.scale.z)
    }

    /** Returns a cached LibGDX model for the dynamic mesh revision, rebuilding when needed. */
    private fun dynamicGdxModel(dynamicModel: com.pashkd.krender.engine.api.DynamicModel): Model {
        val cached = dynamicModels[dynamicModel.id]
        if (cached != null && cached.revision == dynamicModel.revision) {
            return cached.model
        }

        cached?.model?.dispose()
        val built = buildDynamicModel(dynamicModel)
        dynamicModels[dynamicModel.id] = DynamicModelCacheEntry(dynamicModel.revision, built)
        return built
    }

    /** Builds a LibGDX [Model] from engine dynamic mesh data. */
    private fun buildDynamicModel(dynamicModel: com.pashkd.krender.engine.api.DynamicModel): Model {
        val meshData = dynamicModel.mesh
        val positions = meshData.positions
        val normals = meshData.normals
        val uvs = meshData.uvs
        val hasColors = meshData.hasVertexColors()
        val maxIndex = meshData.indices.maxOrNull() ?: -1
        val canUseIndices = meshData.indices.isNotEmpty() &&
            meshData.vertexCount <= UNSIGNED_SHORT_MASK &&
            maxIndex <= UNSIGNED_SHORT_MASK

        val attributes = mutableListOf(
            VertexAttribute.Position(),
            VertexAttribute.Normal(),
            VertexAttribute.TexCoords(0),
        )
        if (hasColors) {
            attributes += VertexAttribute.ColorUnpacked()
        }

        val vertexBuffer = if (canUseIndices) {
            interleaveVertices(positions, normals, uvs, meshData.colors)
        } else {
            expandVertices(meshData)
        }
        val vertexStride = dynamicVertexFloatCount(hasColors)

        val mesh = Mesh(
            true,
            vertexBuffer.size / vertexStride,
            if (canUseIndices) meshData.indices.size else 0,
            *attributes.toTypedArray(),
        )
        mesh.setVertices(vertexBuffer)
        if (canUseIndices) {
            mesh.setIndices(meshData.indices.map(Int::toShort).toShortArray())
        }

        val material = com.badlogic.gdx.graphics.g3d.Material(
            ColorAttribute.createDiffuse(1f, 1f, 1f, 1f),
        )
        val partSize = if (canUseIndices) meshData.indices.size else vertexBuffer.size / vertexStride
        val meshPart = MeshPart(dynamicModel.id, mesh, 0, partSize, GL20.GL_TRIANGLES)
        val nodePart = NodePart(meshPart, material)
        val node = Node().apply {
            id = dynamicModel.id
            parts.add(nodePart)
        }

        return Model().also { model ->
            model.meshes.add(mesh)
            model.meshParts.add(meshPart)
            model.materials.add(material)
            model.nodes.add(node)
            model.manageDisposable(mesh)
            model.calculateTransforms()
        }
    }

    /** Interleaves indexed vertex attributes into one packed float buffer. */
    private fun interleaveVertices(
        positions: FloatArray,
        normals: FloatArray,
        uvs: FloatArray,
        colors: FloatArray?,
    ): FloatArray {
        val vertexCount = positions.size / 3
        val hasColors = colors != null && colors.size == vertexCount * 4
        val vertices = FloatArray(vertexCount * dynamicVertexFloatCount(hasColors))
        var offset = 0
        for (vertex in 0 until vertexCount) {
            val positionBase = vertex * 3
            val uvBase = vertex * 2
            val colorBase = vertex * 4
            vertices[offset++] = positions[positionBase]
            vertices[offset++] = positions[positionBase + 1]
            vertices[offset++] = positions[positionBase + 2]
            vertices[offset++] = normals[positionBase]
            vertices[offset++] = normals[positionBase + 1]
            vertices[offset++] = normals[positionBase + 2]
            vertices[offset++] = uvs[uvBase]
            vertices[offset++] = uvs[uvBase + 1]
            if (hasColors) {
                vertices[offset++] = colors[colorBase]
                vertices[offset++] = colors[colorBase + 1]
                vertices[offset++] = colors[colorBase + 2]
                vertices[offset++] = colors[colorBase + 3]
            }
        }
        return vertices
    }

    /** Expands indexed mesh data into a non-indexed vertex buffer when required. */
    private fun expandVertices(meshData: com.pashkd.krender.engine.api.DynamicMesh): FloatArray {
        if (meshData.indices.isEmpty()) {
            return interleaveVertices(meshData.positions, meshData.normals, meshData.uvs, meshData.colors)
        }

        val hasColors = meshData.hasVertexColors()
        val vertices = FloatArray(meshData.indices.size * dynamicVertexFloatCount(hasColors))
        var offset = 0
        meshData.indices.forEach { index ->
            val positionBase = index * 3
            val uvBase = index * 2
            val colorBase = index * 4
            vertices[offset++] = meshData.positions[positionBase]
            vertices[offset++] = meshData.positions[positionBase + 1]
            vertices[offset++] = meshData.positions[positionBase + 2]
            vertices[offset++] = meshData.normals[positionBase]
            vertices[offset++] = meshData.normals[positionBase + 1]
            vertices[offset++] = meshData.normals[positionBase + 2]
            vertices[offset++] = meshData.uvs[uvBase]
            vertices[offset++] = meshData.uvs[uvBase + 1]
            if (hasColors) {
                val colors = meshData.colors ?: return@forEach
                vertices[offset++] = colors[colorBase]
                vertices[offset++] = colors[colorBase + 1]
                vertices[offset++] = colors[colorBase + 2]
                vertices[offset++] = colors[colorBase + 3]
            }
        }
        return vertices
    }

    /** Returns whether the dynamic mesh contains one RGBA color per vertex. */
    private fun com.pashkd.krender.engine.api.DynamicMesh.hasVertexColors(): Boolean =
        colors != null && colors.size == vertexCount * 4

    /** Returns the packed float count for one dynamic vertex. */
    private fun dynamicVertexFloatCount(hasColors: Boolean): Int =
        if (hasColors) FLOATS_PER_COLORED_DYNAMIC_VERTEX else FLOATS_PER_DYNAMIC_VERTEX

    /** Extracts world-space wireframe vertices from a model instance. */
    private fun wireframeVerticesFor(
        instance: ModelInstance,
        color: com.pashkd.krender.engine.api.Color,
    ): List<Float> {
        val vertices = mutableListOf<Float>()
        wireframeRenderables.clear()
        instance.getRenderables(wireframeRenderables, wireframeRenderablePool)
        wireframeRenderables.forEach { renderable ->
            appendWireframeMeshPart(vertices, renderable.meshPart, renderable.worldTransform, color)
        }
        wireframeRenderablePool.freeAll(wireframeRenderables)
        wireframeRenderables.clear()
        return vertices
    }

    /** Appends the line edges needed to visualize one mesh part as wireframe. */
    private fun appendWireframeMeshPart(
        vertices: MutableList<Float>,
        meshPart: MeshPart,
        transform: Matrix4,
        color: com.pashkd.krender.engine.api.Color,
    ) {
        val mesh = meshPart.mesh
        val positionAttribute = mesh.getVertexAttribute(VertexAttributes.Usage.Position) ?: return
        val vertexStride = mesh.vertexSize / FLOAT_BYTES
        val positionOffset = positionAttribute.offset / FLOAT_BYTES
        val vertexData = FloatArray(mesh.numVertices * vertexStride)
        mesh.getVertices(vertexData)
        val indexData = if (mesh.numIndices > 0) {
            ShortArray(mesh.numIndices).also(mesh::getIndices)
        } else {
            null
        }

        fun vertexIndex(localIndex: Int): Int {
            val absoluteIndex = meshPart.offset + localIndex
            return indexData?.getOrNull(absoluteIndex)?.toInt()?.and(UNSIGNED_SHORT_MASK) ?: absoluteIndex
        }

        fun appendEdge(localA: Int, localB: Int) {
            appendWireframeVertex(vertices, vertexData, vertexStride, positionOffset, vertexIndex(localA), transform, color)
            appendWireframeVertex(vertices, vertexData, vertexStride, positionOffset, vertexIndex(localB), transform, color)
        }

        when (meshPart.primitiveType) {
            GL20.GL_TRIANGLES -> {
                var i = 0
                while (i + 2 < meshPart.size) {
                    appendEdge(i, i + 1)
                    appendEdge(i + 1, i + 2)
                    appendEdge(i + 2, i)
                    i += 3
                }
            }

            GL20.GL_TRIANGLE_STRIP -> {
                for (i in 0 until meshPart.size - 2) {
                    appendEdge(i, i + 1)
                    appendEdge(i + 1, i + 2)
                    appendEdge(i + 2, i)
                }
            }

            GL20.GL_TRIANGLE_FAN -> {
                for (i in 1 until meshPart.size - 1) {
                    appendEdge(0, i)
                    appendEdge(i, i + 1)
                    appendEdge(i + 1, 0)
                }
            }

            GL20.GL_LINES -> {
                var i = 0
                while (i + 1 < meshPart.size) {
                    appendEdge(i, i + 1)
                    i += 2
                }
            }
        }
    }

    /** Appends one transformed wireframe vertex with color. */
    private fun appendWireframeVertex(
        vertices: MutableList<Float>,
        vertexData: FloatArray,
        vertexStride: Int,
        positionOffset: Int,
        vertexIndex: Int,
        transform: Matrix4,
        color: com.pashkd.krender.engine.api.Color,
    ) {
        val base = vertexIndex * vertexStride + positionOffset
        if (base + 2 >= vertexData.size) return
        wireframeTmpVertex.set(vertexData[base], vertexData[base + 1], vertexData[base + 2]).mul(transform)
        vertices += wireframeTmpVertex.x
        vertices += wireframeTmpVertex.y
        vertices += wireframeTmpVertex.z
        vertices += color.r
        vertices += color.g
        vertices += color.b
        vertices += color.a
    }

    /** Builds a LibGDX perspective camera from the active scene camera components. */
    private fun cameraFor(context: RenderContext): PerspectiveCamera {
        val cameraEntity = context.scene.world.query<TransformComponent, PerspectiveCameraComponent>().firstOrNull()
        val cameraTransform = cameraEntity?.get<TransformComponent>()
        val cameraComponent = cameraEntity?.get<PerspectiveCameraComponent>()
        val camera = PerspectiveCamera(
            cameraComponent?.fieldOfViewDegrees ?: 67f,
            width.toFloat(),
            height.toFloat(),
        )
        val position = cameraTransform?.position
        camera.position.set(position?.x ?: 0f, position?.y ?: 2.5f, position?.z ?: 6f)
        camera.near = cameraComponent?.near ?: 0.1f
        camera.far = cameraComponent?.far ?: 100f
        val lookAt = cameraComponent?.lookAt
        if (lookAt != null) {
            camera.lookAt(lookAt.x, lookAt.y, lookAt.z)
        } else {
            val euler = cameraTransform?.eulerDegrees
            val pitch = Math.toRadians((euler?.x ?: 0f).toDouble())
            val yaw = Math.toRadians((euler?.y ?: 0f).toDouble())
            camera.direction.set(
                (sin(yaw) * cos(pitch)).toFloat(),
                sin(pitch).toFloat(),
                (cos(yaw) * cos(pitch)).toFloat(),
            ).nor()
        }
        camera.update()
        return camera
    }

    /** Builds a LibGDX lighting environment from scene light components. */
    private fun environmentFor(context: RenderContext): Environment {
        val environment = Environment()
        context.scene.world.query<LightComponent>()
            .mapNotNull { it.get<LightComponent>() }
            .forEach { light ->
                when (light.type) {
                    LightType.Ambient -> environment.set(
                        ColorAttribute(
                            ColorAttribute.AmbientLight,
                            light.color.r * light.intensity,
                            light.color.g * light.intensity,
                            light.color.b * light.intensity,
                            light.color.a,
                        ),
                    )

                    LightType.Directional -> environment.add(
                        DirectionalLight().set(
                            light.color.r * light.intensity,
                            light.color.g * light.intensity,
                            light.color.b * light.intensity,
                            light.direction.x,
                            light.direction.y,
                            light.direction.z,
                        ),
                    )

                    LightType.Point -> Unit
                }
            }
        return environment
    }

    /** Returns a cached primitive model for built-in placeholder assets. */
    private fun primitive(path: String): Model = primitives.getOrPut(path) {
        ModelBuilder().createBox(
            1f,
            1f,
            1f,
            com.badlogic.gdx.graphics.g3d.Material(ColorAttribute.createDiffuse(0.1f, 0.62f, 0.82f, 1f)),
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong(),
        )
    }

    companion object {
        private const val FLOATS_PER_DYNAMIC_VERTEX = 8
        private const val FLOATS_PER_COLORED_DYNAMIC_VERTEX = 12
        private const val FLOAT_BYTES = 4
        private const val UNSIGNED_SHORT_MASK = 0xFFFF
    }
}

/** Cached LibGDX model entry keyed by the engine dynamic mesh revision. */
private data class DynamicModelCacheEntry(
    val revision: Long,
    val model: Model,
)

/** Composite cache key that distinguishes model instances by entity and asset id. */
private data class ModelCacheKey(
    val entityId: Long,
    val modelPath: String,
)

/**
 * Minimal shader-based line renderer used for debug grids, axes, and wireframes.
 */
class GdxLineShaderRenderer {
    private val shader = ShaderProgram(
        """
        attribute vec3 a_position;
        attribute vec4 a_color;
        uniform mat4 u_projViewTrans;
        varying vec4 v_color;

        void main() {
            v_color = a_color;
            gl_Position = u_projViewTrans * vec4(a_position, 1.0);
        }
        """.trimIndent(),
        """
        #ifdef GL_ES
        precision mediump float;
        #endif

        varying vec4 v_color;

        void main() {
            gl_FragColor = v_color;
        }
        """.trimIndent(),
    )

    private var mesh: Mesh? = null
    private var vertexCapacity: Int = 0

    init {
        check(shader.isCompiled) { shader.log }
    }

    /** Renders non-overlay world debug lines such as grids and axes. */
    fun render(commands: List<com.pashkd.krender.engine.api.RenderCommand>, camera: Camera) {
        val vertices = mutableListOf<Float>()
        commands.forEach { command ->
            when (command) {
                is DrawWorldGrid -> appendGrid(vertices, command)
                is DrawWorldAxes -> appendAxes(vertices, command)
                else -> Unit
            }
        }

        renderVertices(vertices, camera)
    }

    /** Renders explicit overlay lines without depth testing. */
    fun renderOverlayLines(commands: List<com.pashkd.krender.engine.api.RenderCommand>, camera: Camera) {
        val vertices = mutableListOf<Float>()
        commands.filterIsInstance<DrawLine>().forEach { command ->
            appendLine(vertices, command.from, command.to, command.color)
        }

        renderVertices(vertices, camera, depthTest = false)
    }

    /** Uploads line vertices and renders them with the internal shader. */
    fun renderVertices(
        vertices: List<Float>,
        camera: Camera,
        depthTest: Boolean = true,
    ) {
        val vertexCount = vertices.size / FLOATS_PER_VERTEX
        if (vertexCount == 0) return

        val lineMesh = meshFor(vertexCount)
        lineMesh.setVertices(vertices.toFloatArray())

        if (depthTest) {
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        } else {
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        }
        Gdx.gl.glDepthMask(false)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glLineWidth(1f)

        shader.bind()
        shader.setUniformMatrix("u_projViewTrans", camera.combined)
        lineMesh.render(shader, GL20.GL_LINES, 0, vertexCount)

        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(true)
    }

    /** Disposes the reusable mesh and shader program. */
    fun dispose() {
        mesh?.dispose()
        shader.dispose()
    }

    /** Appends a world grid to the vertex list. */
    private fun appendGrid(vertices: MutableList<Float>, command: DrawWorldGrid) {
        val half = command.halfExtentCells.coerceAtLeast(1)
        val min = -half * command.cellSize
        val max = half * command.cellSize

        for (i in -half..half) {
            val offset = i * command.cellSize
            appendLine(
                vertices,
                from = Vec3(offset, command.y, min),
                to = Vec3(offset, command.y, max),
                color = command.color,
            )
            appendLine(
                vertices,
                from = Vec3(min, command.y, offset),
                to = Vec3(max, command.y, offset),
                color = command.color,
            )
        }
    }

    /** Appends RGB world axes centered at the origin. */
    private fun appendAxes(vertices: MutableList<Float>, command: DrawWorldAxes) {
        val length = command.length.coerceAtLeast(1f)
        appendLine(vertices, Vec3(-length, 0f, 0f), Vec3(length, 0f, 0f), com.pashkd.krender.engine.api.Color(1f, 0f, 0f, 1f))
        appendLine(vertices, Vec3(0f, -length, 0f), Vec3(0f, length, 0f), com.pashkd.krender.engine.api.Color(0f, 1f, 0f, 1f))
        appendLine(vertices, Vec3(0f, 0f, -length), Vec3(0f, 0f, length), com.pashkd.krender.engine.api.Color(0f, 0.35f, 1f, 1f))
    }

    /** Appends one colored line segment. */
    private fun appendLine(
        vertices: MutableList<Float>,
        from: Vec3,
        to: Vec3,
        color: com.pashkd.krender.engine.api.Color,
    ) {
        appendVertex(vertices, from, color)
        appendVertex(vertices, to, color)
    }

    /** Appends one colored line vertex. */
    private fun appendVertex(vertices: MutableList<Float>, position: Vec3, color: com.pashkd.krender.engine.api.Color) {
        vertices += position.x
        vertices += position.y
        vertices += position.z
        vertices += color.r
        vertices += color.g
        vertices += color.b
        vertices += color.a
    }

    /** Returns a reusable mesh with capacity for the requested vertex count. */
    private fun meshFor(vertexCount: Int): Mesh {
        if (mesh == null || vertexCount > vertexCapacity) {
            mesh?.dispose()
            vertexCapacity = vertexCount
            mesh = Mesh(
                false,
                vertexCapacity,
                0,
                VertexAttribute.Position(),
                VertexAttribute.ColorUnpacked(),
            )
        }
        return mesh ?: error("Line mesh was not created")
    }

    companion object {
        private const val FLOATS_PER_VERTEX = 7
    }
}

/** Returns whether the asset reference points to a glTF or GLB model. */
private fun AssetRef<*>.isGltf(): Boolean =
    path.endsWith(".glb", ignoreCase = true) || path.endsWith(".gltf", ignoreCase = true)
