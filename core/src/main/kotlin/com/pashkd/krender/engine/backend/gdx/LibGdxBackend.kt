package com.pashkd.krender.engine.backend.gdx

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Cubemap
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Pixmap
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
import com.badlogic.gdx.graphics.g3d.environment.PointLight
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader
import com.badlogic.gdx.graphics.g3d.model.MeshPart
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.graphics.g3d.model.NodePart
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.glutils.FileTextureData
import com.badlogic.gdx.graphics.glutils.PixmapTextureData
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.Pool
import com.badlogic.gdx.utils.UBJsonReader
import com.pashkd.krender.engine.api.Action
import com.pashkd.krender.engine.api.ApplyEnvironment
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.AssetService
import com.pashkd.krender.engine.api.Axis
import com.pashkd.krender.engine.api.Color
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
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.MainThreadTaskQueue
import com.pashkd.krender.engine.api.MaterialDebugMode
import com.pashkd.krender.engine.api.ModelAsset
import com.pashkd.krender.engine.api.ModelAssetBounds
import com.pashkd.krender.engine.api.ModelAssetInfo
import com.pashkd.krender.engine.api.ModelMaterialInfo
import com.pashkd.krender.engine.api.ModelMeshPartInfo
import com.pashkd.krender.engine.api.ModelTextureSlotInfo
import com.pashkd.krender.engine.api.MouseButton
import com.pashkd.krender.engine.api.PointerPhase
import com.pashkd.krender.engine.api.PointerState
import com.pashkd.krender.engine.api.RenderContext
import com.pashkd.krender.engine.api.Renderer
import com.pashkd.krender.engine.api.RuntimeTextureData
import com.pashkd.krender.engine.api.RuntimeTextureFilter
import com.pashkd.krender.engine.api.RuntimeTextureWrap
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.api.ShaderAsset
import com.pashkd.krender.engine.api.TaskService
import com.pashkd.krender.engine.api.TextureAsset
import com.pashkd.krender.engine.api.TextureDebugComponent
import com.pashkd.krender.engine.api.TexturePreviewHandle
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.api.ProfilerService
import com.pashkd.krender.engine.api.RuntimeStatsService
import com.pashkd.krender.engine.api.Vec2
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.backend.gdx.scene.GdxSceneFileService
import com.pashkd.krender.engine.render3d.ActiveCameraComponent
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.scene.DefaultAmbientLightIntensity
import com.pashkd.krender.engine.scene.SceneFileService
import com.pashkd.krender.engine.scene.EditorToolLauncher
import com.pashkd.krender.engine.scene.RuntimeWindowLauncher
import com.pashkd.krender.engine.scene.UnsupportedEditorToolLauncher
import com.pashkd.krender.engine.scene.UnsupportedRuntimeWindowLauncher
import com.pashkd.krender.engine.scene.defaultAmbientLightColor
import com.pashkd.krender.engine.ui.NoOpUiService
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
import net.mgsx.gltf.scene3d.lights.DirectionalLightEx
import net.mgsx.gltf.scene3d.attributes.PBRTextureAttribute
import net.mgsx.gltf.scene3d.scene.Scene as GltfScene
import net.mgsx.gltf.scene3d.scene.SceneAsset
import net.mgsx.gltf.scene3d.scene.SceneManager as GltfSceneManager
import net.mgsx.gltf.scene3d.scene.SceneSkybox
import net.mgsx.gltf.scene3d.shaders.PBRShaderProvider
import net.mgsx.gltf.scene3d.attributes.PBRCubemapAttribute
import net.mgsx.gltf.scene3d.utils.MaterialConverter
import net.mgsx.gltf.scene3d.utils.IBLBuilder
import kotlin.coroutines.CoroutineContext
import kotlin.math.cos
import kotlin.math.sin

/**
 * LibGDX application entry point that bootstraps the engine runtime.
 */
open class GdxEngineApplication(
    private val initialScene: () -> Scene,
    private val runtimeWindowLauncherFactory: (Logger) -> RuntimeWindowLauncher = { UnsupportedRuntimeWindowLauncher },
    private val editorToolLauncherFactory: (Logger) -> EditorToolLauncher = { UnsupportedEditorToolLauncher },
) : ApplicationAdapter() {
    private lateinit var backend: LibGdxBackend
    private lateinit var runtime: EngineRuntime

    /** Creates the backend and starts the initial scene. */
    override fun create() {
        backend = LibGdxBackend(runtimeWindowLauncherFactory, editorToolLauncherFactory)
        backend.logger.info(TAG) { "OpenGL context: ${Gdx.graphics.glVersion.debugVersionString}" }
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

    companion object {
        private const val TAG = "GdxEngineApplication"
    }
}

/**
 * Concrete engine backend that wires LibGDX services into the core runtime.
 */
class LibGdxBackend(
    private val runtimeWindowLauncherFactory: (Logger) -> RuntimeWindowLauncher = { UnsupportedRuntimeWindowLauncher },
    private val editorToolLauncherFactory: (Logger) -> EditorToolLauncher = { UnsupportedEditorToolLauncher },
) : EngineBackend {
    override val runtimeStats: RuntimeStatsService = FrameRuntimeStatsService()
    override val logs: EngineLogService = EngineLogService(frameProvider = runtimeStats::frame).also { service ->
        service.addSink(GdxAppLogSink())
        runCatching { FileLogSink() }
            .onSuccess(service::addSink)
            .onFailure { error ->
                Gdx.app.error(TAG, "File logging disabled: ${error.message}", error)
            }
    }
    override val profiler: ProfilerService = FrameProfilerService()
    override val logger: Logger = logs
    override val input: GdxInputService = GdxInputService().also {
        Gdx.input.inputProcessor = it
    }
    override val ui: UiService = if (Gdx.app.type == com.badlogic.gdx.Application.ApplicationType.Android) {
        NoOpUiService()
    } else {
        GdxImGuiService(input, runtimeStats)
    }
    override val assets: GdxAssetService = GdxAssetService(logger)
    override val sceneFiles: SceneFileService = GdxSceneFileService()
    override val runtimeLauncher: RuntimeWindowLauncher = runtimeWindowLauncherFactory(logger)
    override val editorToolLauncher: EditorToolLauncher = editorToolLauncherFactory(logger)
    override val tasks: TaskService = GdxTaskService()
    override val renderer: Renderer = GdxRenderer3D(assets, ui, logger)

    /** Requests application shutdown through the LibGDX app instance. */
    override fun requestExit() {
        Gdx.app.exit()
    }

    companion object {
        private const val TAG = "LibGdxBackend"
    }
}

/**
 * LibGDX-backed input service that snapshots keyboard, mouse, pointer, and UI capture state.
 */
class GdxInputService : InputService, InputProcessor {
    private val keysDown = mutableSetOf<Key>()
    private val pressedThisFrame = mutableSetOf<Key>()
    private val releasedThisFrame = mutableSetOf<Key>()
    private val mouseButtonsDown = mutableSetOf<MouseButton>()
    private val mouseButtonsPressedThisFrame = mutableSetOf<MouseButton>()
    private val mouseButtonsReleasedThisFrame = mutableSetOf<MouseButton>()
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
            mouseButtonsDown = mouseButtonsDown.toSet(),
            mouseButtonsPressedThisFrame = mouseButtonsPressedThisFrame.toSet(),
            mouseButtonsReleasedThisFrame = mouseButtonsReleasedThisFrame.toSet(),
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
        mouseButtonsPressedThisFrame.clear()
        mouseButtonsReleasedThisFrame.clear()
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
        val mouseButton = mapMouseButton(button)
        if (mouseButton !in mouseButtonsDown) {
            mouseButtonsPressedThisFrame += mouseButton
        }
        mouseButtonsDown += mouseButton
        pointers[pointer] = PointerState(pointer, PointerPhase.Down, Vec2(screenX.toFloat(), screenY.toFloat()))
        processors.forEach { it.touchDown(screenX, screenY, pointer, button) }
        return false
    }

    /** Ends a tracked pointer interaction. */
    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        mouseMoved(screenX, screenY)
        val mouseButton = mapMouseButton(button)
        mouseButtonsDown -= mouseButton
        mouseButtonsReleasedThisFrame += mouseButton
        pointers[pointer] = PointerState(pointer, PointerPhase.Up, Vec2(screenX.toFloat(), screenY.toFloat()))
        processors.forEach { it.touchUp(screenX, screenY, pointer, button) }
        return false
    }

    /** Marks a tracked pointer as cancelled. */
    override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        mouseMoved(screenX, screenY)
        val mouseButton = mapMouseButton(button)
        mouseButtonsDown -= mouseButton
        mouseButtonsReleasedThisFrame += mouseButton
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

    /** Maps LibGDX mouse button codes into normalized engine buttons. */
    private fun mapMouseButton(button: Int): MouseButton = when (button) {
        Input.Buttons.LEFT -> MouseButton.Left
        Input.Buttons.RIGHT -> MouseButton.Right
        Input.Buttons.MIDDLE -> MouseButton.Middle
        Input.Buttons.BACK -> MouseButton.Back
        Input.Buttons.FORWARD -> MouseButton.Forward
        else -> MouseButton.Unknown
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
class GdxAssetService(
    private val logger: Logger? = null,
) : AssetService {
    private val manager = AssetManager()
    private val requested = mutableSetOf<String>()
    private val missing = mutableSetOf<String>()
    private val loggedLoaded = mutableSetOf<String>()
    private val warnedTextureBindings = mutableSetOf<String>()
    private val shaderSources = mutableMapOf<String, String>()
    private val modelInfos = mutableMapOf<String, ModelAssetInfo>()
    private val modelBoundsCache = mutableMapOf<String, ModelAssetBounds>()
    private val modelTriangleCounts = mutableMapOf<String, Int>()
    private val texturePreviewRegistry = mutableMapOf<String, Texture>()
    private val runtimeTextures = mutableMapOf<String, RuntimeTextureEntry>()
    private val modelTexturePreviewKeys = mutableMapOf<String, Set<String>>()

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
                    throw missingAsset("model", asset.path)
                }
                requested += asset.path
                logger?.info(GDX_ASSET_SERVICE_TAG) { "Queue asset type=model path='${asset.path}' gltf=${asset.isGltf()}" }
                if (asset.isGltf()) {
                    manager.load(asset.path, SceneAsset::class.java)
                } else {
                    manager.load(asset.path, Model::class.java)
                }
            }

            TextureAsset::class -> {
                if (!Gdx.files.internal(asset.path).exists()) {
                    missing += asset.path
                    throw missingAsset("texture", asset.path)
                }
                requested += asset.path
                logger?.info(GDX_ASSET_SERVICE_TAG) { "Queue asset type=texture path='${asset.path}'" }
                manager.load(asset.path, Texture::class.java)
            }

            ShaderAsset::class -> {
                val file = Gdx.files.internal(asset.path)
                if (file.exists()) {
                    requested += asset.path
                    shaderSources[asset.path] = file.readString()
                    logger?.info(GDX_ASSET_SERVICE_TAG) { "Loaded shader source path='${asset.path}'" }
                } else {
                    missing += asset.path
                    throw missingAsset("shader", asset.path)
                }
            }
        }
    }

    private fun missingAsset(type: String, path: String): IllegalArgumentException {
        val message = "Asset not found type=$type path='$path'"
        logger?.error(GDX_ASSET_SERVICE_TAG) { message }
        return IllegalArgumentException(message)
    }

    /** Advances asynchronous loading for up to the given time budget. */
    override fun update(budgetMs: Int): Float {
        manager.update(budgetMs)
        logLoadedAssets()
        cacheLoadedModelBounds()
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

    /** Returns an opaque handle for a loaded model texture or standalone texture asset. */
    override fun texturePreviewHandle(texturePathOrId: String): TexturePreviewHandle? {
        val key = texturePathOrId.takeIf(String::isNotBlank) ?: return null
        val texture = texturePreviewRegistry[key]
            ?: loadedTextureAsset(key)
            ?: return null
        return TexturePreviewHandle(
            id = texture.textureObjectHandle,
            width = texture.width,
            height = texture.height,
        )
    }

    /**
     * Returns cached local-space model bounds extracted from an already loaded backend model.
     */
    override fun modelBounds(asset: AssetRef<ModelAsset>): ModelAssetBounds? {
        if (asset.isPrimitive || !isLoaded(asset)) return null
        modelBoundsCache[asset.path]?.let { return it }

        val model = when {
            asset.isGltf() -> gltfScene(asset)?.scene?.model
            else -> gdxModel(asset)
        } ?: return null

        val bounds = calculateModelBounds(asset.path, model) ?: return null
        modelBoundsCache[asset.path] = bounds
        return bounds
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
        modelBoundsCache -= asset.path
        modelTriangleCounts -= asset.path
        texturePreviewRegistry -= asset.path
        modelTexturePreviewKeys.remove(asset.path)?.forEach { key -> texturePreviewRegistry -= key }
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

    /** Returns a loaded standalone LibGDX texture asset. */
    fun gdxTexture(asset: AssetRef<TextureAsset>): Texture? =
        loadedTextureAsset(asset.path)

    /** Returns a loaded standalone or model-registered LibGDX texture by path or stable id. */
    fun textureByPathOrId(pathOrId: String): Texture? {
        val texture = runtimeTextures[pathOrId]?.texture
            ?: texturePreviewRegistry[pathOrId]
            ?: loadedTextureAsset(pathOrId)
        if (texture == null && warnedTextureBindings.add(pathOrId)) {
            logger?.warn(GDX_ASSET_SERVICE_TAG) {
                "Texture binding unresolved id='$pathOrId' runtime=${pathOrId in runtimeTextures} " +
                    "preview=${pathOrId in texturePreviewRegistry} assetLoaded=${manager.isLoaded(pathOrId, Texture::class.java)}"
            }
        } else if (texture != null && warnedTextureBindings.add("resolved:$pathOrId")) {
            logger?.debug(GDX_ASSET_SERVICE_TAG) {
                "Texture binding resolved id='$pathOrId' source=${textureSource(pathOrId)} size=${texture.width}x${texture.height}"
            }
        }
        return texture
    }

    /** Uploads or refreshes a runtime-generated texture payload. */
    fun upsertRuntimeTexture(texture: RuntimeTextureData) {
        val existing = runtimeTextures[texture.id]
        if (existing != null && existing.revision == texture.revision) return

        val pixmap = Pixmap(texture.width, texture.height, Pixmap.Format.RGBA8888)
        try {
            var offset = 0
            for (y in 0 until texture.height) {
                for (x in 0 until texture.width) {
                    pixmap.drawPixel(x, y, texture.rgba8888[offset++])
                }
            }
            val uploaded = Texture(pixmap).also { gdxTexture ->
                gdxTexture.setFilter(texture.minFilter.gdx(), texture.magFilter.gdx())
                gdxTexture.setWrap(texture.uWrap.gdx(), texture.vWrap.gdx())
            }
            existing?.texture?.dispose()
            runtimeTextures[texture.id] = RuntimeTextureEntry(texture.revision, uploaded)
            texturePreviewRegistry[texture.id] = uploaded
            logger?.info(GDX_ASSET_SERVICE_TAG) {
                "Uploaded runtime texture id='${texture.id}' revision=${texture.revision} size=${texture.width}x${texture.height} " +
                    "filter=${texture.minFilter}/${texture.magFilter} wrap=${texture.uWrap}/${texture.vWrap}"
            }
        } finally {
            pixmap.dispose()
        }
    }

    /** Returns a loaded standalone LibGDX texture asset. */
    private fun loadedTextureAsset(path: String): Texture? =
        if (manager.isLoaded(path, Texture::class.java)) manager.get(path, Texture::class.java) else null

    private fun textureSource(pathOrId: String): String =
        when {
            pathOrId in runtimeTextures -> "runtime"
            pathOrId in texturePreviewRegistry -> "previewRegistry"
            manager.isLoaded(pathOrId, Texture::class.java) -> "asset"
            else -> "missing"
        }

    private fun logLoadedAssets() {
        requested.forEach { path ->
            if (path in loggedLoaded) return@forEach
            val loaded = when {
                manager.isLoaded(path, SceneAsset::class.java) -> "gltf-scene"
                manager.isLoaded(path, Model::class.java) -> "model"
                manager.isLoaded(path, Texture::class.java) -> "texture"
                path in shaderSources -> "shader"
                else -> null
            }
            if (loaded != null) {
                loggedLoaded += path
                logger?.info(GDX_ASSET_SERVICE_TAG) { "Asset loaded type=$loaded path='$path'" }
            }
        }
    }

    /** Disposes the underlying LibGDX asset manager. */
    fun dispose() {
        runtimeTextures.values.forEach { entry -> entry.texture.dispose() }
        runtimeTextures.clear()
        loggedLoaded.clear()
        warnedTextureBindings.clear()
        manager.dispose()
    }

    /**
     * Precomputes bounds for newly loaded models during the asset update phase.
     */
    private fun cacheLoadedModelBounds() {
        requested.forEach { path ->
            if (path in modelBoundsCache) return@forEach
            val asset = AssetRef.model(path)
            if (!isLoaded(asset)) return@forEach
            val model = when {
                asset.isGltf() -> gltfScene(asset)?.scene?.model
                else -> gdxModel(asset)
            } ?: return@forEach
            calculateModelBounds(path, model)?.let { bounds -> modelBoundsCache[path] = bounds }
        }
    }

    /**
     * Extracts render-relevant metadata from the loaded model and optional glTF scene asset.
     */
    private fun buildModelInfo(path: String, model: Model, sceneAsset: SceneAsset?): ModelAssetInfo {
        val nodes = collectNodes(model.nodes)
        val nodeParts = nodes.flatMap(::nodePartsOf)
        val bounds = modelBoundsCache[path] ?: calculateModelBounds(path, model)?.also { modelBoundsCache[path] = it }
        val attributeSummary = collectVertexAttributeSummary(model.meshes)
        val registeredTexturePreviewKeys = linkedSetOf<String>()
        val materialInfos = buildMaterialInfos(model.materials) { key, texture ->
            texturePreviewRegistry[key] = texture
            registeredTexturePreviewKeys += key
        }
        modelTexturePreviewKeys[path] = registeredTexturePreviewKeys
        val textureSlots = materialInfos.flatMap { material -> material.textureSlots }
        val textureChannels = textureSlots
            .mapTo(linkedSetOf()) { slot -> slot.channel }
        val textures = linkedSetOf<Texture>()
        var maxBonesPerPart = 0

        model.materials.forEach { material ->
            for (attribute in material) {
                if (attribute is TextureAttribute) {
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
            size = bounds?.size(),
            boundsMin = bounds?.min,
            boundsMax = bounds?.max,
            vertexChannels = attributeSummary.vertexChannels.toList(),
            uvChannels = attributeSummary.uvChannels.toList(),
            textureChannels = textureChannels.toList(),
            textureCount = textureCount,
            textureSlotCount = textureSlots.size,
            hasSkeleton = maxBonesPerPart > 0,
            boneCount = maxBonesPerPart,
            boneWeightChannelCount = attributeSummary.boneWeightChannelCount,
            animationCount = model.animations.size,
            animationNames = animationNames,
            meshParts = buildMeshPartInfos(nodes, model.materials),
            materials = materialInfos,
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
        boundsMin = null,
        boundsMax = null,
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

    /**
     * Calculates the asset-local model bounds once from LibGDX model data.
     */
    private fun calculateModelBounds(path: String, model: Model): ModelAssetBounds? {
        return try {
            val bounds = BoundingBox()
            ModelInstance(model).calculateBoundingBox(bounds)
            if (!bounds.isValid) {
                null
            } else {
                ModelAssetBounds(
                    min = Vec3(bounds.min.x, bounds.min.y, bounds.min.z),
                    max = Vec3(bounds.max.x, bounds.max.y, bounds.max.z),
                )
            }
        } catch (error: Throwable) {
            Gdx.app.error("GdxAssetService", "Failed to calculate model bounds for '$path'", error)
            null
        }
    }

    /** Returns the dimensions of a cached model bounds box. */
    private fun ModelAssetBounds.size(): Vec3 =
        Vec3(max.x - min.x, max.y - min.y, max.z - min.z)
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
 * Builds read-only mesh-part metadata for model inspection UI.
 */
private fun buildMeshPartInfos(
    nodes: List<Node>,
    materials: Array<com.badlogic.gdx.graphics.g3d.Material>,
): List<ModelMeshPartInfo> = buildList {
    var index = 0
    nodes.forEach { node ->
        for (part in node.parts) {
            val meshPart = part.meshPart
            val materialIndex = part.material?.let { material -> materialIndexOf(materials, material) }
            add(
                ModelMeshPartInfo(
                    index = index,
                    nodeName = node.id?.takeIf(String::isNotBlank),
                    meshId = meshPart?.mesh?.toString(),
                    partId = meshPart?.id?.takeIf(String::isNotBlank),
                    materialId = part.material?.id?.takeIf(String::isNotBlank),
                    materialIndex = materialIndex,
                    primitiveType = meshPart?.primitiveType?.let(::primitiveTypeLabel),
                    vertexCount = meshPart?.mesh?.numVertices,
                    triangleCount = meshPart?.let(::countTrianglesInMeshPart),
                ),
            )
            index += 1
        }
    }
}

/**
 * Builds read-only material metadata for model inspection UI.
 */
private fun buildMaterialInfos(
    materials: Array<com.badlogic.gdx.graphics.g3d.Material>,
    registerTexturePreview: (String, Texture) -> Unit = { _, _ -> },
): List<ModelMaterialInfo> = buildList {
    materials.forEachIndexed { index, material ->
        val diffuseColor = (material.get(ColorAttribute.Diffuse) as? ColorAttribute)?.color
        val materialId = material.id?.takeIf(String::isNotBlank)
        add(
            ModelMaterialInfo(
                index = index,
                id = materialId,
                diffuseTexture = material.textureLabel(TextureAttribute.Diffuse),
                normalTexture = material.textureLabel(TextureAttribute.Normal),
                emissiveTexture = material.textureLabel(TextureAttribute.Emissive),
                textureSlots = materialTextureSlots(material, index, materialId, registerTexturePreview),
                baseColor = diffuseColor?.let { color -> Color(color.r, color.g, color.b, color.a) },
                opacity = diffuseColor?.a,
            ),
        )
    }
}

private fun com.badlogic.gdx.graphics.g3d.Material.textureLabel(type: Long): String? =
    ((get(type) as? TextureAttribute)?.textureDescription?.texture)?.let(::textureIdentifier)

private fun materialTextureSlots(
    material: com.badlogic.gdx.graphics.g3d.Material,
    materialIndex: Int,
    materialId: String?,
    registerTexturePreview: (String, Texture) -> Unit,
): List<ModelTextureSlotInfo> = buildList {
    for (attribute in material) {
        if (attribute is TextureAttribute) {
            val texture = attribute.textureDescription.texture
            val texturePath = textureIdentifier(texture)
            if (texturePath != null) {
                registerTexturePreview(texturePath, texture)
            }
            add(
                ModelTextureSlotInfo(
                    channel = normalizeTextureChannel(attribute),
                    texturePath = texturePath,
                    uvChannel = attribute.uvIndex.takeIf { uvIndex -> uvIndex >= 0 }?.let { uvIndex -> "UV$uvIndex" },
                    materialIndex = materialIndex,
                    materialId = materialId,
                ),
            )
        }
    }
}

private fun normalizeTextureChannel(attribute: TextureAttribute): String = when (attribute.type) {
    PBRTextureAttribute.BaseColorTexture -> "baseColor"
    TextureAttribute.Diffuse -> "diffuse"
    PBRTextureAttribute.NormalTexture,
    TextureAttribute.Normal,
    TextureAttribute.Bump,
    -> "normal"
    PBRTextureAttribute.EmissiveTexture,
    TextureAttribute.Emissive,
    -> "emissive"
    PBRTextureAttribute.OcclusionTexture -> "occlusion"
    PBRTextureAttribute.MetallicRoughnessTexture -> "metallicRoughness"
    else -> {
        val alias = Attribute.getAttributeAlias(attribute.type)?.takeIf(String::isNotBlank)
        normalizeTextureAlias(alias)
    }
}

private fun normalizeTextureAlias(alias: String?): String = when (alias?.lowercase()) {
    "basecolortexture", "basecolor", "diffusetexture" -> "baseColor"
    "diffuse" -> "diffuse"
    "normaltexture", "normal", "bump" -> "normal"
    "emissivetexture", "emissive" -> "emissive"
    "occlusiontexture", "occlusion", "ambient" -> "occlusion"
    "metallicroughnesstexture", "metallicroughness" -> "metallicRoughness"
    "alphatexture", "alpha", "opacity" -> "alpha"
    null -> "unknown"
    else -> alias
}

private fun textureIdentifier(texture: Texture): String? {
    val fileTextureData = texture.textureData as? FileTextureData
    val filePath = fileTextureData?.fileHandle?.path()?.takeIf(String::isNotBlank)
    return filePath ?: texture.textureObjectHandle
        .takeIf { handle -> handle > 0 }
        ?.let { handle -> "texture:$handle" }
}

private fun materialIndexOf(
    materials: Array<com.badlogic.gdx.graphics.g3d.Material>,
    material: com.badlogic.gdx.graphics.g3d.Material,
): Int? {
    materials.forEachIndexed { index, candidate ->
        if (candidate === material) return index
    }
    val materialId = material.id?.takeIf(String::isNotBlank) ?: return null
    return materials.indexOfFirst { candidate -> candidate.id == materialId }
        .takeIf { index -> index >= 0 }
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

private const val GDX_ASSET_SERVICE_TAG = "GdxAssetService"

/** Returns the total triangle count across all mesh parts in the model. */
private fun countTrianglesInModel(model: Model): Int =
    model.meshParts.sumOf(::countTrianglesInMeshPart)

/** Converts a mesh part's primitive topology into triangle count. */
private fun countTrianglesInMeshPart(meshPart: MeshPart): Int = when (meshPart.primitiveType) {
    GL20.GL_TRIANGLES -> meshPart.size / 3
    GL20.GL_TRIANGLE_STRIP, GL20.GL_TRIANGLE_FAN -> (meshPart.size - 2).coerceAtLeast(0)
    else -> 0
}

private fun primitiveTypeLabel(primitiveType: Int): String = when (primitiveType) {
    GL20.GL_TRIANGLES -> "TRIANGLES"
    GL20.GL_TRIANGLE_STRIP -> "TRIANGLE_STRIP"
    GL20.GL_TRIANGLE_FAN -> "TRIANGLE_FAN"
    GL20.GL_LINES -> "LINES"
    GL20.GL_LINE_STRIP -> "LINE_STRIP"
    GL20.GL_POINTS -> "POINTS"
    else -> "GL_$primitiveType"
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
    private val logger: Logger,
) : Renderer {
    private val modelBatch = ModelBatch()
    private val lineRenderer = GdxLineShaderRenderer()
    private val modelViewerDebugRenderer = GdxModelViewerDebugRenderer(assets, logger)
    private val pbrPreviewRenderer = GdxGltfPbrPreviewRenderer(assets, logger)
    private val skyboxRenderer = GdxSkyboxRenderer(assets, logger)
    private val instances = mutableMapOf<ModelCacheKey, ModelInstance>()
    private val gltfScenes = mutableMapOf<ModelCacheKey, GltfScene>()
    private val primitives = mutableMapOf<String, Model>()
    private val dynamicModels = mutableMapOf<String, DynamicModelCacheEntry>()
    private val wireframeRenderables = Array<Renderable>()
    private val wireframeRenderablePool = object : Pool<Renderable>() {
        override fun newObject(): Renderable = Renderable()
    }
    private val wireframeTmpVertex = Vector3()
    private val forceBackBufferAlphaOpaque = systemBoolean("krender.gl.forceOpaqueAlpha", default = false)

    private var width: Int = Gdx.graphics.width
    private var height: Int = Gdx.graphics.height

    /** Renders the full frame for the provided render context. */
    override fun render(context: RenderContext) {
        prepareSceneFrame()
        Gdx.gl.glViewport(0, 0, width, height)
        Gdx.gl.glClearColor(0.08f, 0.09f, 0.11f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        val camera = cameraFor(context)
        val environment = environmentFor(context)
        context.commands.filterIsInstance<ApplyEnvironment>().firstOrNull()?.let { command ->
            skyboxRenderer.render(command, camera, modelBatch)
        }
        val wireframeCommands = mutableListOf<DrawModel>()
        val wireframeDynamicCommands = mutableListOf<DrawDynamicModel>()
        val debugModelCommands = mutableListOf<DrawModel>()
        val pbrModelCommands = mutableListOf<DrawModel>()

        lineRenderer.render(context.commands, camera)

        modelBatch.begin(camera)
        context.commands.forEach { command ->
            when (command) {
                is DrawModel -> {
                    if (command.material.wireframe) {
                        wireframeCommands += command
                    } else if (command.debugView?.active == true) {
                        debugModelCommands += command
                        if (command.material.wireframeOverlay) {
                            wireframeCommands += command
                        }
                    } else if (command.pbrPreview?.enabled == true) {
                        pbrModelCommands += command
                        if (command.material.wireframeOverlay) {
                            wireframeCommands += command
                        }
                    } else {
                        renderModel(command, environment, camera)
                        if (command.material.wireframeOverlay) {
                            wireframeCommands += command
                        }
                    }
                }
                is DrawDynamicModel -> {
                    if (command.material.wireframe) {
                        wireframeDynamicCommands += command
                    } else {
                        renderDynamicModel(command, environment)
                        if (command.material.wireframeOverlay) {
                            wireframeDynamicCommands += command
                        }
                    }
                }
                else -> Unit
            }
        }
        modelBatch.end()
        debugModelCommands.forEach { modelViewerDebugRenderer.render(it, camera, ::modelInstanceForDebug) }
        pbrModelCommands.forEach { command ->
            if (!pbrPreviewRenderer.render(command, camera, ::applyVisibleMeshPartFilter)) {
                modelBatch.begin(camera)
                renderModel(command, environment, camera)
                modelBatch.end()
            }
        }
        wireframeCommands.forEach { renderWireframeModel(it, camera) }
        wireframeDynamicCommands.forEach { renderWireframeDynamicModel(it, camera) }
        lineRenderer.renderOverlayLines(context.commands, camera)

        prepareUiPass()
        ui.render()
        if (forceBackBufferAlphaOpaque) {
            forceOpaqueBackBufferAlpha()
        }
    }

    /**
     * Starts the 3D pass from a known GL state.
     *
     * ImGui uses scissor rectangles and blend state internally. If those flags
     * leak into the following frame, the backbuffer clear or scene pass can be
     * clipped, which shows up as intermittent full-UI blinking.
     */
    private fun prepareSceneFrame() {
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glColorMask(true, true, true, true)
    }

    /**
     * Isolates ImGui from the scene render pass.
     *
     * ModelBatch and debug-line rendering leave depth testing enabled. Some GL
     * backends do not fully normalize that state before drawing ImGui, so make
     * the overlay requirements explicit before submitting UI draw data.
     */
    private fun prepareUiPass() {
        Gdx.gl.glViewport(0, 0, width, height)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(false)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        Gdx.gl.glColorMask(true, true, true, true)
    }

    /**
     * Keeps the presented window fully opaque even after transparent UI draws.
     *
     * The desktop launcher requests an RGB backbuffer, but some drivers still
     * expose an alpha channel. Leave the rendered colors untouched and clear
     * only alpha so DWM/capture paths cannot treat blended ImGui or grid pixels
     * as partially transparent checkerboard tiles.
     */
    private fun forceOpaqueBackBufferAlpha() {
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST)
        Gdx.gl.glColorMask(false, false, false, true)
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        Gdx.gl.glColorMask(true, true, true, true)
        Gdx.gl.glDepthMask(true)
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
        modelViewerDebugRenderer.dispose()
        pbrPreviewRenderer.dispose()
        skyboxRenderer.dispose()
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
        applyVisibleMeshPartFilter(instance, command.visibleMeshPartIndices)

        modelBatch.render(instance, environment)
    }

    /** Returns a prepared model instance for shader-based debug rendering. */
    private fun modelInstanceForDebug(command: DrawModel, camera: Camera): ModelInstance? {
        command.material.shader.assets().forEach(assets::queue)
        assets.queue(command.model)
        val instance = if (command.model.isGltf()) {
            val sceneAsset = assets.gltfScene(command.model) ?: return null
            val cacheKey = ModelCacheKey(command.entityId, command.model.path)
            val scene = gltfScenes.getOrPut(cacheKey) {
                GltfScene(sceneAsset.scene).also(MaterialConverter::makeCompatible)
            }
            applyTransform(scene.modelInstance, command)
            applyVisibleMeshPartFilter(scene.modelInstance, command.visibleMeshPartIndices)
            scene.update(camera, Gdx.graphics.deltaTime)
            scene.modelInstance
        } else {
            val model = if (command.model.isPrimitive) {
                primitive(command.model.path)
            } else {
                assets.gdxModel(command.model)
            } ?: return null

            val cacheKey = ModelCacheKey(command.entityId, command.model.path)
            val existing = instances[cacheKey]
            if (existing?.model === model) {
                existing
            } else {
                ModelInstance(model).also { created ->
                    instances[cacheKey] = created
                }
            }.also { prepared ->
                prepared.materials.forEach { applyMaterialAttributes(it, command.material) }
                applyTransform(prepared, command)
                applyVisibleMeshPartFilter(prepared, command.visibleMeshPartIndices)
            }
        }
        return instance
    }

    /** Renders one dynamic mesh-backed model command. */
    private fun renderDynamicModel(command: DrawDynamicModel, environment: Environment) {
        command.runtimeTextures.forEach(assets::upsertRuntimeTexture)
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
        val resolvedDiffuseTexture = material.diffuseTextureRef
            ?.let { ref -> assets.textureByPathOrId(ref.id) }
        if (resolvedDiffuseTexture != null) {
            gdxMaterial.set(TextureAttribute.createDiffuse(resolvedDiffuseTexture))
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
        applyVisibleMeshPartFilter(scene.modelInstance, command.visibleMeshPartIndices)
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
            }.also { instance ->
                applyTransform(instance, command)
                applyVisibleMeshPartFilter(instance, command.visibleMeshPartIndices)
            }
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
        applyVisibleMeshPartFilter(scene.modelInstance, command.visibleMeshPartIndices)
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

    /**
     * Applies a backend-neutral node-part index filter using the same flattened node order as model metadata.
     */
    private fun applyVisibleMeshPartFilter(
        instance: ModelInstance,
        visibleMeshPartIndices: Set<Int>?,
    ) {
        var index = 0
        collectNodes(instance.nodes).forEach { node ->
            for (part in node.parts) {
                part.enabled = visibleMeshPartIndices == null || index in visibleMeshPartIndices
                index += 1
            }
        }
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
        val activeCameraEntity = context.scene.world.query<TransformComponent, PerspectiveCameraComponent, ActiveCameraComponent>()
            .firstOrNull()
        val cameraEntity = activeCameraEntity ?: context.scene.world.query<TransformComponent, PerspectiveCameraComponent>().firstOrNull()
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
        var ambientApplied = false
        context.scene.world.query<TransformComponent, LightComponent>().forEach { entity ->
            val transform = entity.get<TransformComponent>() ?: return@forEach
            val light = entity.get<LightComponent>() ?: return@forEach
            when (light.type) {
                LightType.Ambient -> {
                    environment.set(
                        ColorAttribute(
                            ColorAttribute.AmbientLight,
                            light.color.r * light.intensity,
                            light.color.g * light.intensity,
                            light.color.b * light.intensity,
                            light.color.a,
                        ),
                    )
                    ambientApplied = true
                }

                LightType.Directional -> {
                    val direction = Vector3(light.direction.x, light.direction.y, light.direction.z)
                    if (!direction.isZero) {
                        direction.nor()
                        environment.add(
                            DirectionalLight().set(
                                light.color.r * light.intensity,
                                light.color.g * light.intensity,
                                light.color.b * light.intensity,
                                direction.x,
                                direction.y,
                                direction.z,
                            ),
                        )
                    }
                }

                LightType.Point -> {
                    environment.add(
                        PointLight().set(
                            light.color.r,
                            light.color.g,
                            light.color.b,
                            transform.position.x,
                            transform.position.y,
                            transform.position.z,
                            light.intensity,
                        ),
                    )
                }
            }
        }
        if (!ambientApplied) {
            val environmentCommand = context.commands.filterIsInstance<ApplyEnvironment>().firstOrNull()
            val ambient = environmentCommand?.ambientColor ?: defaultAmbientLightColor()
            val intensity = environmentCommand?.ambientIntensity ?: DefaultAmbientLightIntensity
            environment.set(
                ColorAttribute(
                    ColorAttribute.AmbientLight,
                    ambient.r * intensity,
                    ambient.g * intensity,
                    ambient.b * intensity,
                    ambient.a,
                ),
            )
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

private data class RuntimeTextureEntry(
    val revision: Long,
    val texture: Texture,
)

/** Composite cache key that distinguishes model instances by entity and asset id. */
private data class ModelCacheKey(
    val entityId: Long,
    val modelPath: String,
)

private fun RuntimeTextureFilter.gdx(): Texture.TextureFilter = when (this) {
    RuntimeTextureFilter.Nearest -> Texture.TextureFilter.Nearest
    RuntimeTextureFilter.Linear -> Texture.TextureFilter.Linear
}

private fun RuntimeTextureWrap.gdx(): Texture.TextureWrap = when (this) {
    RuntimeTextureWrap.ClampToEdge -> Texture.TextureWrap.ClampToEdge
    RuntimeTextureWrap.Repeat -> Texture.TextureWrap.Repeat
}

/**
 * Shared runtime skybox renderer for backend-neutral environment commands.
 */
private class GdxSkyboxRenderer(
    private val assets: GdxAssetService,
    private val logger: Logger,
) {
    private val entries = mutableMapOf<String, RuntimeSkyboxEntry>()
    private val warnedKeys = mutableSetOf<String>()

    fun render(command: ApplyEnvironment, camera: Camera, modelBatch: ModelBatch) {
        val texturePath = command.skyboxTexture
            ?.id
            ?.takeIf { command.showSkybox && it.isNotBlank() }
            ?: return
        val entry = entryFor(texturePath) ?: return
        val intensity = command.environmentIntensity.coerceAtLeast(0f)
        entry.skybox.getColor().set(intensity, intensity, intensity, 1f)
        entry.skybox.update(camera, Gdx.graphics.deltaTime)

        Gdx.gl.glDepthMask(false)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        try {
            modelBatch.begin(camera)
            try {
                modelBatch.render(entry.skybox)
            } finally {
                modelBatch.end()
            }
        } catch (error: Throwable) {
            warnOnce("render-$texturePath-${error::class.qualifiedName}") {
                "Runtime skybox render failed texture='$texturePath': ${error.message ?: error::class.simpleName}"
            }
        } finally {
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
            Gdx.gl.glDepthMask(true)
            Gdx.gl.glDisable(GL20.GL_CULL_FACE)
        }
    }

    fun dispose() {
        entries.values.forEach(RuntimeSkyboxEntry::dispose)
        entries.clear()
        warnedKeys.clear()
    }

    private fun entryFor(path: String): RuntimeSkyboxEntry? {
        entries[path]?.let { return it }
        assets.queue(AssetRef.texture(path))
        return try {
            val cubemap = cubemapFromSingleTexture(path)
            try {
                SceneSkybox.enableMipmaps(cubemap)
            } catch (error: Throwable) {
                warnOnce("mipmaps-$path-${error::class.qualifiedName}") {
                    "Runtime skybox mipmaps unavailable texture='$path': ${error.message ?: error::class.simpleName}"
                }
            }
            RuntimeSkyboxEntry(
                cubemap = cubemap,
                skybox = SceneSkybox(cubemap),
            ).also { entry ->
                entries[path] = entry
                logger.info(TAG) { "Runtime skybox loaded texture='$path'" }
            }
        } catch (error: Throwable) {
            warnOnce("texture-$path-${error::class.qualifiedName}") {
                "Runtime skybox texture '$path' unavailable; continuing without skybox: ${error.message ?: error::class.simpleName}"
            }
            null
        }
    }

    private fun warnOnce(key: String, message: () -> String) {
        if (warnedKeys.add(key)) {
            logger.warn(TAG, message = message)
        }
    }

    private companion object {
        private const val TAG = "GdxSkyboxRenderer"
    }
}

private data class RuntimeSkyboxEntry(
    val cubemap: Cubemap,
    val skybox: SceneSkybox,
) {
    fun dispose() {
        skybox.dispose()
        cubemap.dispose()
    }
}

/**
 * glTF PBR preview renderer backed by gdx-gltf SceneManager.
 */
private class GdxGltfPbrPreviewRenderer(
    private val assets: GdxAssetService,
    private val logger: Logger,
) {
    private val entries = mutableMapOf<ModelCacheKey, PbrSceneEntry>()
    private val warnedKeys = mutableSetOf<String>()

    fun render(
        command: DrawModel,
        camera: Camera,
        meshPartFilter: (ModelInstance, Set<Int>?) -> Unit,
    ): Boolean {
        val settings = command.pbrPreview?.takeIf { it.enabled } ?: return false
        if (!command.model.isGltf()) {
            warnOnce("not-gltf-${command.model.path}") {
                "PBR preview unavailable: '${command.model.path}' is not a glTF/glb model."
            }
            return false
        }

        assets.queue(command.model)
        val sceneAsset = assets.gltfScene(command.model)
        if (sceneAsset == null) {
            warnOnce("not-loaded-${command.model.path}") {
                "PBR preview unavailable: glTF asset '${command.model.path}' is not loaded yet."
            }
            return false
        }

        return try {
            settings.skyboxTexture?.let { ref -> assets.queue(AssetRef.texture(ref.id)) }
            val cacheKey = ModelCacheKey(command.entityId, command.model.path)
            val entry = entries.getOrPut(cacheKey) {
                val scene = GltfScene(sceneAsset.scene)
                val manager = GltfSceneManager(
                    PBRShaderProvider.createDefault(sceneAsset.maxBones.coerceAtLeast(1)),
                    PBRShaderProvider.createDefaultDepth(sceneAsset.maxBones.coerceAtLeast(1)),
                )
                manager.addScene(scene, false)
                logger.info(TAG) { "Created PBR preview scene for '${command.model.path}'." }
                PbrSceneEntry(scene = scene, manager = manager)
            }

            applyTransform(entry.scene.modelInstance, command)
            meshPartFilter(entry.scene.modelInstance, command.visibleMeshPartIndices)
            configureEnvironment(entry, settings)
            entry.manager.setCamera(camera)
            entry.manager.update(Gdx.graphics.deltaTime)
            entry.manager.render()
            true
        } catch (error: Throwable) {
            warnOnce("error-${command.model.path}-${error::class.qualifiedName}") {
                "PBR preview unavailable for '${command.model.path}': ${error.message ?: error::class.simpleName}"
            }
            false
        }
    }

    fun dispose() {
        entries.values.forEach(PbrSceneEntry::dispose)
        entries.clear()
    }

    private fun configureEnvironment(entry: PbrSceneEntry, settings: com.pashkd.krender.engine.api.PbrPreviewView) {
        val direction = pbrLightDirection(settings.directionalLightYawDegrees, settings.directionalLightPitchDegrees)
        val intensity = (settings.environmentIntensity * settings.exposure).coerceAtLeast(0f)
        entry.manager.environment.clear()
        entry.manager.environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.08f * intensity, 0.09f * intensity, 0.1f * intensity, 1f))
        if (settings.directionalLightEnabled) {
            entry.manager.environment.add(
                DirectionalLightEx().set(
                    com.badlogic.gdx.graphics.Color.WHITE,
                    direction,
                    intensity.coerceAtLeast(0.01f),
                ),
            )
        }
        val skyboxCubemap = settings.skyboxTexture
            ?.id
            ?.takeIf { path -> settings.showSkybox && path.isNotBlank() }
            ?.let { path -> entry.ensureSkyboxCubemap(path) }
        if (skyboxCubemap != null) {
            entry.manager.environment.set(PBRCubemapAttribute.createDiffuseEnv(skyboxCubemap))
            entry.manager.environment.set(PBRCubemapAttribute.createSpecularEnv(skyboxCubemap))
            entry.manager.skyBox = entry.skybox
            return
        } else {
            entry.manager.skyBox = null
        }

        if (settings.showSkybox) {
            val iblAvailable = entry.ensureIbl(direction, intensity.coerceAtLeast(0.01f))
            if (iblAvailable) {
                entry.irradianceMap?.let { map -> entry.manager.environment.set(PBRCubemapAttribute.createDiffuseEnv(map)) }
                entry.radianceMap?.let { map -> entry.manager.environment.set(PBRCubemapAttribute.createSpecularEnv(map)) }
                entry.manager.setSkyBox(entry.skybox)
            }
        }
    }

    private fun PbrSceneEntry.ensureSkyboxCubemap(path: String): Cubemap? {
        if (skyboxTexturePath == path && skyboxCubemap != null) return skyboxCubemap
        disposeSkyboxCubemap()
        return try {
            val cubemap = cubemapFromSingleTexture(path)
            try {
                SceneSkybox.enableMipmaps(cubemap)
            } catch (error: Throwable) {
                warnOnce("skybox-mipmaps-$path-${error::class.qualifiedName}") {
                    "PBR preview skybox mipmaps unavailable; roughness reflections may be less accurate: " +
                        (error.message ?: error::class.simpleName)
                }
            }
            skyboxTexturePath = path
            skyboxCubemap = cubemap
            skybox = SceneSkybox(cubemap)
            cubemap
        } catch (error: Throwable) {
            disposeSkyboxCubemap()
            warnOnce("skybox-texture-$path-${error::class.qualifiedName}") {
                "PBR preview skybox texture '$path' unavailable; continuing without asset skybox: " +
                    (error.message ?: error::class.simpleName)
            }
            null
        }
    }

    private fun PbrSceneEntry.ensureIbl(direction: Vector3, intensity: Float): Boolean {
        val nextKey = PbrEnvironmentKey(
            intensity = "%.3f".format(intensity),
            directionX = "%.3f".format(direction.x),
            directionY = "%.3f".format(direction.y),
            directionZ = "%.3f".format(direction.z),
        )
        if (environmentKey == nextKey) return iblAvailable
        disposeEnvironment()
        val light = DirectionalLightEx().set(com.badlogic.gdx.graphics.Color.WHITE, direction, intensity)
        val builder = IBLBuilder.createOutdoor(light)
        try {
            envMap = builder.buildEnvMap(64)
            irradianceMap = builder.buildIrradianceMap(16)
            radianceMap = builder.buildRadianceMap(64)
            skybox = envMap?.let(::SceneSkybox)
            environmentKey = nextKey
            iblAvailable = true
            return true
        } catch (error: Throwable) {
            disposeEnvironment()
            environmentKey = nextKey
            iblAvailable = false
            warnOnce("ibl-unavailable-${error::class.qualifiedName}") {
                "PBR preview IBL/skybox unavailable; continuing with direct lighting only: " +
                    (error.message ?: error::class.simpleName)
            }
            return false
        } finally {
            builder.dispose()
        }
    }

    private fun warnOnce(key: String, message: () -> String) {
        if (warnedKeys.add(key)) {
            logger.warn(TAG, message = message)
        }
    }

    companion object {
        private const val TAG = "GdxGltfPbrPreviewRenderer"
    }
}

private data class PbrSceneEntry(
    val scene: GltfScene,
    val manager: GltfSceneManager,
    var environmentKey: PbrEnvironmentKey? = null,
    var envMap: Cubemap? = null,
    var irradianceMap: Cubemap? = null,
    var radianceMap: Cubemap? = null,
    var skyboxTexturePath: String? = null,
    var skyboxCubemap: Cubemap? = null,
    var skybox: SceneSkybox? = null,
    var iblAvailable: Boolean = false,
) {
    fun dispose() {
        disposeEnvironment()
        disposeSkyboxCubemap()
        manager.dispose()
    }

    fun disposeEnvironment() {
        if (skyboxCubemap == null) {
            skybox?.dispose()
            skybox = null
        }
        envMap?.dispose()
        envMap = null
        irradianceMap?.dispose()
        irradianceMap = null
        radianceMap?.dispose()
        radianceMap = null
        iblAvailable = false
    }

    fun disposeSkyboxCubemap() {
        skybox?.dispose()
        skybox = null
        skyboxCubemap?.dispose()
        skyboxCubemap = null
        skyboxTexturePath = null
    }
}

private data class PbrEnvironmentKey(
    val intensity: String,
    val directionX: String,
    val directionY: String,
    val directionZ: String,
)

private fun pbrLightDirection(yawDegrees: Float, pitchDegrees: Float): Vector3 {
    val yaw = Math.toRadians(yawDegrees.toDouble())
    val pitch = Math.toRadians(pitchDegrees.toDouble())
    val x = (cos(pitch) * cos(yaw)).toFloat()
    val y = sin(pitch).toFloat()
    val z = (cos(pitch) * sin(yaw)).toFloat()
    return Vector3(-x, -y, -z).nor()
}

private fun cubemapFromSingleTexture(path: String): Cubemap {
    val source = Pixmap(Gdx.files.internal(path))
    val layout = cubemapLayoutFor(source.width, source.height)
    val faceSize = layout.faceSize
    val faces = ArrayList<Pixmap>(CUBEMAP_FACE_COUNT)
    try {
        layout.faces.forEach { faceRegion ->
            val face = Pixmap(faceSize, faceSize, source.format)
            face.drawPixmap(
                source,
                0,
                0,
                faceRegion.x * faceSize,
                faceRegion.y * faceSize,
                faceSize,
                faceSize,
            )
            faces += face
        }
        return Cubemap(
            faces[0].textureData(disposePixmap = true),
            faces[1].textureData(disposePixmap = true),
            faces[2].textureData(disposePixmap = true),
            faces[3].textureData(disposePixmap = true),
            faces[4].textureData(disposePixmap = true),
            faces[5].textureData(disposePixmap = true),
        ).also { cubemap ->
            cubemap.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            cubemap.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
        }
    } catch (error: Throwable) {
        faces.forEach { face ->
            if (!face.isDisposed) face.dispose()
        }
        throw error
    } finally {
        source.dispose()
    }
}

private data class CubemapLayout(
    val faceSize: Int,
    val faces: List<CubemapFaceRegion>,
)

private data class CubemapFaceRegion(
    val x: Int,
    val y: Int,
)

private fun cubemapLayoutFor(width: Int, height: Int): CubemapLayout {
    require(width > 0 && height > 0) {
        "Cubemap source dimensions must be positive, got ${width}x$height."
    }
    return when {
        width == height * CUBEMAP_FACE_COUNT -> {
            CubemapLayout(
                faceSize = height,
                faces = (0 until CUBEMAP_FACE_COUNT).map { index -> CubemapFaceRegion(index, 0) },
            )
        }
        height == width * CUBEMAP_FACE_COUNT -> {
            CubemapLayout(
                faceSize = width,
                faces = (0 until CUBEMAP_FACE_COUNT).map { index -> CubemapFaceRegion(0, index) },
            )
        }
        width % 4 == 0 && height % 3 == 0 && width / 4 == height / 3 -> {
            // Standard horizontal cross:
            //       +Y
            // -X +Z +X -Z
            //       -Y
            val faceSize = width / 4
            CubemapLayout(
                faceSize = faceSize,
                faces = listOf(
                    CubemapFaceRegion(2, 1), // +X
                    CubemapFaceRegion(0, 1), // -X
                    CubemapFaceRegion(1, 0), // +Y
                    CubemapFaceRegion(1, 2), // -Y
                    CubemapFaceRegion(1, 1), // +Z
                    CubemapFaceRegion(3, 1), // -Z
                ),
            )
        }
        width % 3 == 0 && height % 4 == 0 && width / 3 == height / 4 -> {
            // Standard vertical cross:
            //    +Y
            // -X +Z +X
            //    -Y
            //    -Z
            val faceSize = width / 3
            CubemapLayout(
                faceSize = faceSize,
                faces = listOf(
                    CubemapFaceRegion(2, 1), // +X
                    CubemapFaceRegion(0, 1), // -X
                    CubemapFaceRegion(1, 0), // +Y
                    CubemapFaceRegion(1, 2), // -Y
                    CubemapFaceRegion(1, 1), // +Z
                    CubemapFaceRegion(1, 3), // -Z
                ),
            )
        }
        else -> error(
            "Unsupported cubemap texture layout ${width}x$height. " +
                "Expected 6x1 strip, 1x6 strip, 4x3 cross, or 3x4 cross.",
        )
    }
}

private fun Pixmap.textureData(disposePixmap: Boolean): PixmapTextureData =
    PixmapTextureData(this, format, false, disposePixmap)

private const val CUBEMAP_FACE_COUNT = 6

private fun applyTransform(instance: ModelInstance, command: DrawModel) {
    val transform = command.transform
    instance.transform.idt()
    instance.transform.translate(transform.position.x, transform.position.y, transform.position.z)
    instance.transform.rotate(Vector3.X, transform.eulerDegrees.x)
    instance.transform.rotate(Vector3.Y, transform.eulerDegrees.y)
    instance.transform.rotate(Vector3.Z, transform.eulerDegrees.z)
    instance.transform.scale(transform.scale.x, transform.scale.y, transform.scale.z)
}

/**
 * Shader-based ModelViewer material debug renderer.
 */
private class GdxModelViewerDebugRenderer(
    private val assets: GdxAssetService,
    private val logger: Logger,
) {
    private val uvShaders = mutableMapOf<Int, ShaderProgram?>()
    private val fallbackShader: ShaderProgram? = compileShader(
        name = "model-viewer-debug-fallback",
        vertex = """
            attribute vec3 a_position;
            uniform mat4 u_projViewTrans;
            uniform mat4 u_worldTrans;

            void main() {
                gl_Position = u_projViewTrans * u_worldTrans * vec4(a_position, 1.0);
            }
        """.trimIndent(),
        fragment = """
            #ifdef GL_ES
            precision mediump float;
            #endif

            uniform vec4 u_FallbackColor;

            void main() {
                gl_FragColor = u_FallbackColor;
            }
        """.trimIndent(),
    )
    private val renderables = Array<Renderable>()
    private val renderablePool = object : Pool<Renderable>() {
        override fun newObject(): Renderable = Renderable()
    }
    private val warnedKeys = mutableSetOf<String>()

    fun render(
        command: DrawModel,
        camera: Camera,
        instanceProvider: (DrawModel, Camera) -> ModelInstance?,
    ) {
        val debugView = command.debugView ?: return
        val instance = instanceProvider(command, camera) ?: return

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        when (debugView.culling) {
            com.pashkd.krender.engine.api.DebugCullingMode.Backface -> {
                Gdx.gl.glEnable(GL20.GL_CULL_FACE)
                Gdx.gl.glCullFace(GL20.GL_BACK)
            }
            com.pashkd.krender.engine.api.DebugCullingMode.DoubleSided -> {
                Gdx.gl.glDisable(GL20.GL_CULL_FACE)
            }
        }

        renderables.clear()
        instance.getRenderables(renderables, renderablePool)
        for (renderable in renderables) {
            renderRenderable(renderable, instance, debugView, camera)
        }
        renderablePool.freeAll(renderables)
        renderables.clear()
    }

    fun dispose() {
        uvShaders.values.filterNotNull().toSet().forEach(ShaderProgram::dispose)
        fallbackShader?.dispose()
    }

    private fun renderRenderable(
        renderable: Renderable,
        instance: ModelInstance,
        debugView: com.pashkd.krender.engine.api.MaterialDebugView,
        camera: Camera,
    ) {
        val materialIndex = materialIndexOf(instance.materials, renderable.material)
        val selectedMaterialIndex = debugView.selectedMaterialIndex
        if (selectedMaterialIndex != null && materialIndex != selectedMaterialIndex) {
            renderFallback(renderable, camera, FallbackUnselectedMaterial)
            return
        }

        val mode = debugView.mode
        val textureRef = textureRefFor(renderable.material, materialIndex, debugView)
        val uvChannel = if (mode == MaterialDebugMode.UvChecker) {
            debugView.uvChannel.coerceAtLeast(0)
        } else {
            textureRef?.texture?.uvChannel?.coerceAtLeast(0) ?: 0
        }
        if (!renderable.meshPart.hasUvChannel(uvChannel)) {
            warnOnce("missing-uv-$mode-$uvChannel") {
                "ModelViewer debug shader fallback: mesh part '${renderable.meshPart.id}' has no UV$uvChannel for mode=$mode"
            }
            renderFallback(renderable, camera, FallbackMissingUv)
            return
        }

        val texture = if (mode == MaterialDebugMode.UvChecker) {
            val checkerRef = debugView.uvCheckerTexture
            checkerRef?.let { ref ->
                assets.queue(AssetRef.texture(ref.id))
                assets.gdxTexture(AssetRef.texture(ref.id))
            }
        } else {
            textureRef?.texture?.let { ref -> assets.textureByPathOrId(ref.id) }
        }

        if (texture == null) {
            warnOnce("missing-texture-$mode-${materialIndex ?: "unknown"}") {
                "ModelViewer debug shader fallback: texture for mode=$mode is unavailable materialIndex=${materialIndex ?: "unknown"}"
            }
            renderFallback(renderable, camera, FallbackMissingTexture)
            return
        }

        val shader = uvShader(uvChannel) ?: run {
            renderFallback(renderable, camera, FallbackShaderError)
            return
        }

        texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat)
        texture.bind(0)
        shader.bind()
        // TODO: carry semantic texture color space through asset loading; scalar debug channels are sampled raw here.
        shader.setUniformMatrix("u_projViewTrans", camera.combined)
        shader.setUniformMatrix("u_worldTrans", renderable.worldTransform)
        shader.setUniformi("u_DebugTexture", 0)
        shader.setUniformi("u_DebugMode", debugModeCode(mode))
        shader.setUniformi(
            "u_DebugComponent",
            textureDebugComponentCode(
                if (mode == MaterialDebugMode.UvChecker) TextureDebugComponent.RGB
                else textureRef?.component ?: TextureDebugComponent.RGB,
            ),
        )
        shader.setUniformf("u_UvCheckerScale", debugView.uvScale.coerceAtLeast(MIN_UV_SCALE))
        renderable.meshPart.render(shader)
    }

    private fun renderFallback(
        renderable: Renderable,
        camera: Camera,
        color: com.badlogic.gdx.graphics.Color,
    ) {
        val shader = fallbackShader ?: return
        shader.bind()
        shader.setUniformMatrix("u_projViewTrans", camera.combined)
        shader.setUniformMatrix("u_worldTrans", renderable.worldTransform)
        shader.setUniformf("u_FallbackColor", color)
        renderable.meshPart.render(shader)
    }

    private fun textureRefFor(
        material: com.badlogic.gdx.graphics.g3d.Material,
        materialIndex: Int?,
        debugView: com.pashkd.krender.engine.api.MaterialDebugView,
    ): com.pashkd.krender.engine.api.MaterialDebugTextureRef? =
        debugView.textureRefs.firstOrNull { ref -> materialIndex != null && ref.materialIndex == materialIndex }
            ?: debugView.textureRefs.firstOrNull { ref ->
                ref.materialId != null && material.id != null && ref.materialId == material.id
            }
            ?: debugView.textureRefs.firstOrNull { ref -> ref.materialIndex == null && ref.materialId == null }

    private fun uvShader(uvChannel: Int): ShaderProgram? =
        uvShaders.getOrPut(uvChannel) {
            compileShader(
                name = "model-viewer-debug-uv$uvChannel",
                vertex = """
                    attribute vec3 a_position;
                    attribute vec2 a_texCoord$uvChannel;
                    uniform mat4 u_projViewTrans;
                    uniform mat4 u_worldTrans;
                    varying vec2 v_uv;

                    void main() {
                        v_uv = a_texCoord$uvChannel;
                        gl_Position = u_projViewTrans * u_worldTrans * vec4(a_position, 1.0);
                    }
                """.trimIndent(),
                fragment = """
                    #ifdef GL_ES
                    precision mediump float;
                    #endif

                    varying vec2 v_uv;
                    uniform int u_DebugMode;
                    uniform int u_DebugComponent;
                    uniform sampler2D u_DebugTexture;
                    uniform float u_UvCheckerScale;

                    void main() {
                        vec2 uv = v_uv;
                        if (u_DebugMode == ${debugModeCode(MaterialDebugMode.UvChecker)}) {
                            uv = v_uv * u_UvCheckerScale;
                        }
                        vec4 texel = texture2D(u_DebugTexture, uv);
                        if (u_DebugComponent == ${textureDebugComponentCode(TextureDebugComponent.R)}) {
                            gl_FragColor = vec4(vec3(texel.r), 1.0);
                        } else if (u_DebugComponent == ${textureDebugComponentCode(TextureDebugComponent.G)}) {
                            gl_FragColor = vec4(vec3(texel.g), 1.0);
                        } else if (u_DebugComponent == ${textureDebugComponentCode(TextureDebugComponent.B)}) {
                            gl_FragColor = vec4(vec3(texel.b), 1.0);
                        } else if (u_DebugComponent == ${textureDebugComponentCode(TextureDebugComponent.A)}) {
                            gl_FragColor = vec4(vec3(texel.a), 1.0);
                        } else if (u_DebugComponent == ${textureDebugComponentCode(TextureDebugComponent.RGBA)}) {
                            gl_FragColor = texel;
                        } else {
                            gl_FragColor = vec4(texel.rgb, 1.0);
                        }
                    }
                """.trimIndent(),
            )
        }

    private fun compileShader(name: String, vertex: String, fragment: String): ShaderProgram? {
        val shader = ShaderProgram(vertex, fragment)
        if (!shader.isCompiled) {
            logger.error(TAG) { "ModelViewer debug shader compile failed name='$name': ${shader.log}" }
            shader.dispose()
            return null
        }
        logger.info(TAG) { "ModelViewer debug shader compiled name='$name'" }
        return shader
    }

    private fun warnOnce(key: String, message: () -> String) {
        if (warnedKeys.add(key)) {
            logger.warn(TAG, message = message)
        }
    }

    private fun MeshPart.hasUvChannel(uvChannel: Int): Boolean =
        mesh.vertexAttributes.any { attribute ->
            attribute.usage == VertexAttributes.Usage.TextureCoordinates && attribute.unit == uvChannel
        }

    companion object {
        private const val TAG = "GdxModelViewerDebugRenderer"
        private const val MIN_UV_SCALE = 0.01f
        private val FallbackMissingTexture = com.badlogic.gdx.graphics.Color(1f, 0.15f, 0.65f, 1f)
        private val FallbackMissingUv = com.badlogic.gdx.graphics.Color(1f, 0.72f, 0.1f, 1f)
        private val FallbackShaderError = com.badlogic.gdx.graphics.Color(1f, 0f, 0f, 1f)
        private val FallbackUnselectedMaterial = com.badlogic.gdx.graphics.Color(0.28f, 0.28f, 0.3f, 1f)
    }
}

private fun debugModeCode(mode: MaterialDebugMode): Int = when (mode) {
    MaterialDebugMode.Alpha -> 6
    MaterialDebugMode.UvChecker -> 7
    else -> 1
}

private fun textureDebugComponentCode(component: TextureDebugComponent): Int = when (component) {
    TextureDebugComponent.RGB -> 0
    TextureDebugComponent.R -> 1
    TextureDebugComponent.G -> 2
    TextureDebugComponent.B -> 3
    TextureDebugComponent.A -> 4
    TextureDebugComponent.RGBA -> 5
}

private fun normalizeTextureChannel(channel: String?): String =
    channel.orEmpty()
        .lowercase()
        .filter { char -> char.isLetterOrDigit() }

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

private fun systemBoolean(name: String, default: Boolean): Boolean {
    val envName = name.replace('.', '_').uppercase()
    val value = System.getProperty(name)
        ?: System.getenv(envName)
        ?: return default
    return when (value.trim().lowercase()) {
        "1", "true", "yes", "on" -> true
        "0", "false", "no", "off" -> false
        else -> default
    }
}
