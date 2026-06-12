package com.pashkd.krender.engine.backend.gdx

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Application.ApplicationType
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
import com.badlogic.gdx.graphics.g3d.model.Animation
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
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.graphics.g3d.utils.AnimationController
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider
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
import com.pashkd.krender.engine.api.AnimationPlaybackView
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
import com.pashkd.krender.engine.api.ModelAnimationInfo
import com.pashkd.krender.engine.api.ModelAssetBounds
import com.pashkd.krender.engine.api.ModelAssetInfo
import com.pashkd.krender.engine.api.ModelBoneInfo
import com.pashkd.krender.engine.api.ModelBonePose
import com.pashkd.krender.engine.api.ModelMaterialInfo
import com.pashkd.krender.engine.api.ModelMeshPartInfo
import com.pashkd.krender.engine.api.ModelSkeletonInfo
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
import com.pashkd.krender.engine.api.TerrainAsset
import com.pashkd.krender.engine.api.TextureAsset
import com.pashkd.krender.engine.api.TextureDebugComponent
import com.pashkd.krender.engine.api.TexturePreviewHandle
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.api.ProfilerService
import com.pashkd.krender.engine.api.RuntimeStatsService
import com.pashkd.krender.engine.api.Vec2
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.backend.gdx.scene.GdxSceneFileService
import com.pashkd.krender.engine.backend.gdx.ui.runtime.GdxRuntimeUiBackend
import com.pashkd.krender.engine.backend.gdx.ui.runtime.WoolboyRuntimeUiFactory
import com.pashkd.krender.engine.render3d.ActiveCameraComponent
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
import com.pashkd.krender.engine.ui.runtime.RuntimeUiBackend
import com.pashkd.krender.engine.scene.DefaultAmbientLightIntensity
import com.pashkd.krender.engine.scene.SceneFileService
import com.pashkd.krender.engine.scene.EditorToolLauncher
import com.pashkd.krender.engine.scene.RuntimeWindowLauncher
import com.pashkd.krender.engine.scene.UnsupportedEditorToolLauncher
import com.pashkd.krender.engine.scene.UnsupportedRuntimeWindowLauncher
import com.pashkd.krender.engine.scene.defaultAmbientLightColor
import com.pashkd.krender.engine.terrain.TerrainMaterialTextureSamplerFactory
import com.pashkd.krender.engine.ui.editor.NoOpUiService
import com.pashkd.krender.engine.ui.editor.UiCaptureState
import com.pashkd.krender.engine.ui.editor.UiService
import com.pashkd.krender.engine.window.RuntimeWindowConfig
import com.pashkd.krender.engine.window.WindowMode
import com.pashkd.krender.engine.window.WindowService
import com.pashkd.krender.engine.window.WindowState
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
