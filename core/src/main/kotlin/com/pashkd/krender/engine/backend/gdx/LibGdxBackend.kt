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
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.UBJsonReader
import com.pashkd.krender.engine.api.Action
import com.pashkd.krender.engine.api.AssetRef
import com.pashkd.krender.engine.api.AssetService
import com.pashkd.krender.engine.api.Axis
import com.pashkd.krender.engine.api.DebugService
import com.pashkd.krender.engine.api.DrawModel
import com.pashkd.krender.engine.api.DrawWorldAxes
import com.pashkd.krender.engine.api.DrawWorldGrid
import com.pashkd.krender.engine.api.EngineBackend
import com.pashkd.krender.engine.api.EngineRuntime
import com.pashkd.krender.engine.api.FrameDebugService
import com.pashkd.krender.engine.api.InputService
import com.pashkd.krender.engine.api.InputSnapshot
import com.pashkd.krender.engine.api.Key
import com.pashkd.krender.engine.api.LogEntry
import com.pashkd.krender.engine.api.LogLevel
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.MainThreadTaskQueue
import com.pashkd.krender.engine.api.ModelAsset
import com.pashkd.krender.engine.api.PointerPhase
import com.pashkd.krender.engine.api.PointerState
import com.pashkd.krender.engine.api.RenderContext
import com.pashkd.krender.engine.api.Renderer
import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.api.ShaderAsset
import com.pashkd.krender.engine.api.TaskService
import com.pashkd.krender.engine.api.TextureAsset
import com.pashkd.krender.engine.api.TransformComponent
import com.pashkd.krender.engine.api.Vec2
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.render3d.LightComponent
import com.pashkd.krender.engine.render3d.LightType
import com.pashkd.krender.engine.render3d.PerspectiveCameraComponent
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

open class GdxEngineApplication(
    private val initialScene: () -> Scene,
) : ApplicationAdapter() {
    private lateinit var backend: LibGdxBackend
    private lateinit var runtime: EngineRuntime

    override fun create() {
        backend = LibGdxBackend()
        Gdx.input.setCursorCatched(true)
        runtime = EngineRuntime(backend)
        runtime.start(initialScene())
    }

    override fun render() {
        runtime.renderFrame(Gdx.graphics.deltaTime)
    }

    override fun resize(width: Int, height: Int) {
        runtime.resize(width, height)
    }

    override fun dispose() {
        runtime.dispose()
    }
}

class LibGdxBackend : EngineBackend {
    override val debug: DebugService = FrameDebugService()
    override val logger: Logger = GdxLogger(debug)
    override val input: GdxInputService = GdxInputService().also {
        Gdx.input.inputProcessor = it
    }
    override val assets: GdxAssetService = GdxAssetService()
    override val tasks: TaskService = GdxTaskService()
    override val renderer: Renderer = GdxRenderer3D(assets, debug)
}

class GdxInputService : InputService, InputProcessor {
    private val keysDown = mutableSetOf<Key>()
    private val pressedThisFrame = mutableSetOf<Key>()
    private val releasedThisFrame = mutableSetOf<Key>()
    private val pointers = mutableMapOf<Int, PointerState>()
    private val actions = mapOf(
        Action("MoveLeft") to setOf(Key.A),
        Action("MoveRight") to setOf(Key.D),
        Action("MoveForward") to setOf(Key.W),
        Action("MoveBackward") to setOf(Key.S),
        Action("Jump") to setOf(Key.Space),
    )

    private var currentSnapshot = InputSnapshot()
    private var mouseX: Int = 0
    private var mouseY: Int = 0
    private var mouseDeltaX: Float = 0f
    private var mouseDeltaY: Float = 0f
    private var scrollDelta: Float = 0f

    override fun beginFrame() {
        currentSnapshot = InputSnapshot(
            keysDown = keysDown.toSet(),
            keysPressedThisFrame = pressedThisFrame.toSet(),
            keysReleasedThisFrame = releasedThisFrame.toSet(),
            mousePosition = Vec2(mouseX.toFloat(), mouseY.toFloat()),
            mouseDelta = Vec2(mouseDeltaX, mouseDeltaY),
            scrollDelta = scrollDelta,
            pointers = pointers.values.toList(),
        )
    }

    override fun snapshot(): InputSnapshot = currentSnapshot

    override fun endFrame() {
        pressedThisFrame.clear()
        releasedThisFrame.clear()
        mouseDeltaX = 0f
        mouseDeltaY = 0f
        scrollDelta = 0f
        pointers.entries.removeIf { it.value.phase == PointerPhase.Up || it.value.phase == PointerPhase.Cancelled }
    }

    override fun isActionPressed(action: Action): Boolean =
        actions[action]?.any { it in currentSnapshot.keysDown } == true

    override fun isActionJustPressed(action: Action): Boolean =
        actions[action]?.any { it in currentSnapshot.keysPressedThisFrame } == true

    override fun axis(axis: Axis): Float = when (axis.name) {
        "Horizontal" -> currentSnapshot.booleanAxis(Key.A, Key.D)
        "Vertical" -> currentSnapshot.booleanAxis(Key.S, Key.W)
        else -> 0f
    }

    override fun keyDown(keycode: Int): Boolean {
        val key = mapKey(keycode)
        if (key !in keysDown) {
            pressedThisFrame += key
        }
        keysDown += key
        return false
    }

    override fun keyUp(keycode: Int): Boolean {
        val key = mapKey(keycode)
        keysDown -= key
        releasedThisFrame += key
        return false
    }

    override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
        mouseDeltaX += screenX - mouseX
        mouseDeltaY += screenY - mouseY
        mouseX = screenX
        mouseY = screenY
        return false
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        mouseMoved(screenX, screenY)
        pointers[pointer] = PointerState(pointer, PointerPhase.Move, Vec2(screenX.toFloat(), screenY.toFloat()))
        return false
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        mouseMoved(screenX, screenY)
        pointers[pointer] = PointerState(pointer, PointerPhase.Down, Vec2(screenX.toFloat(), screenY.toFloat()))
        return false
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        mouseMoved(screenX, screenY)
        pointers[pointer] = PointerState(pointer, PointerPhase.Up, Vec2(screenX.toFloat(), screenY.toFloat()))
        return false
    }

    override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        mouseMoved(screenX, screenY)
        pointers[pointer] = PointerState(pointer, PointerPhase.Cancelled, Vec2(screenX.toFloat(), screenY.toFloat()))
        return false
    }

    override fun scrolled(amountX: Float, amountY: Float): Boolean {
        scrollDelta += amountY
        return false
    }

    override fun keyTyped(character: Char): Boolean = false

    private fun mapKey(keycode: Int): Key = when (keycode) {
        Input.Keys.W -> Key.W
        Input.Keys.A -> Key.A
        Input.Keys.S -> Key.S
        Input.Keys.D -> Key.D
        Input.Keys.Q -> Key.Q
        Input.Keys.E -> Key.E
        Input.Keys.F1 -> Key.F1
        Input.Keys.F2 -> Key.F2
        Input.Keys.F3 -> Key.F3
        Input.Keys.F4 -> Key.F4
        Input.Keys.SPACE -> Key.Space
        Input.Keys.ESCAPE -> Key.Escape
        Input.Keys.SHIFT_LEFT -> Key.ShiftLeft
        else -> Key.Unknown
    }

    private fun InputSnapshot.booleanAxis(negative: Key, positive: Key): Float {
        val left = if (isDown(negative)) 1f else 0f
        val right = if (isDown(positive)) 1f else 0f
        return right - left
    }
}

class GdxAssetService : AssetService {
    private val manager = AssetManager()
    private val requested = mutableSetOf<String>()
    private val missing = mutableSetOf<String>()
    private val shaderSources = mutableMapOf<String, String>()

    init {
        manager.setLoader(Model::class.java, ".g3dj", G3dModelLoader(JsonReader()))
        manager.setLoader(Model::class.java, ".g3db", G3dModelLoader(UBJsonReader()))
        manager.setLoader(Model::class.java, ".obj", ObjLoader())
        manager.setLoader(SceneAsset::class.java, ".gltf", GLTFAssetLoader())
        manager.setLoader(SceneAsset::class.java, ".glb", GLBAssetLoader())
    }

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

    override fun update(budgetMs: Int): Float {
        manager.update(budgetMs)
        return progress()
    }

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

    override fun <T : Any> get(asset: AssetRef<T>): T {
        error("Use backend-specific typed accessors for '${asset.path}'. Core code should keep AssetRef handles.")
    }

    override fun unload(asset: AssetRef<*>) {
        if (!asset.isPrimitive && manager.isLoaded(asset.path)) {
            manager.unload(asset.path)
        }
        requested -= asset.path
        missing -= asset.path
        shaderSources -= asset.path
    }

    fun gdxModel(asset: AssetRef<ModelAsset>): Model? {
        if (asset.isPrimitive || asset.isGltf()) return null
        return if (manager.isLoaded(asset.path)) manager.get(asset.path, Model::class.java) else null
    }

    fun gltfScene(asset: AssetRef<ModelAsset>): SceneAsset? {
        if (!asset.isGltf()) return null
        return if (manager.isLoaded(asset.path, SceneAsset::class.java)) {
            manager.get(asset.path, SceneAsset::class.java)
        } else {
            null
        }
    }

    fun progress(): Float {
        val assetCount = requested.count { !it.startsWith("primitive:") }
        if (assetCount == 0) return 1f
        return manager.progress.coerceIn(0f, 1f)
    }

    fun dispose() {
        manager.dispose()
    }
}

class GdxTaskService : TaskService {
    private val job = SupervisorJob()
    private val backgroundScope = CoroutineScope(job + Dispatchers.Default)
    private val mainQueue = MainThreadTaskQueue()
    private val mainDispatcher = RenderThreadDispatcher()
    private val jobs = mutableSetOf<Job>()

    override val inFlightJobs: Int
        get() = jobs.count { it.isActive }

    override fun launchBackground(name: String, block: suspend CoroutineScope.() -> Unit): Job {
        val launched = backgroundScope.launch(block = block)
        jobs += launched
        launched.invokeOnCompletion { jobs -= launched }
        return launched
    }

    override suspend fun <T> onBackground(block: suspend () -> T): T = withContext(Dispatchers.Default) { block() }
    override suspend fun <T> onIo(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }
    override suspend fun <T> onMain(block: suspend () -> T): T = withContext(mainDispatcher) { block() }

    override fun postToMain(block: () -> Unit) {
        mainQueue.post(block)
    }

    override fun flushMainThreadQueue() {
        mainQueue.flush()
    }

    override fun dispose() {
        backgroundScope.cancel()
        job.cancel()
    }
}

class RenderThreadDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        Gdx.app.postRunnable(block)
    }
}

class GdxRenderer3D(
    private val assets: GdxAssetService,
    private val debug: DebugService,
) : Renderer {
    private val modelBatch = ModelBatch()
    private val lineRenderer = GdxLineShaderRenderer()
    private val spriteBatch = SpriteBatch()
    private val font = BitmapFont()
    private val instances = mutableMapOf<Long, ModelInstance>()
    private val gltfScenes = mutableMapOf<Long, GltfScene>()
    private val primitives = mutableMapOf<String, Model>()

    private var width: Int = Gdx.graphics.width
    private var height: Int = Gdx.graphics.height

    override fun render(context: RenderContext) {
        Gdx.gl.glViewport(0, 0, width, height)
        Gdx.gl.glClearColor(0.08f, 0.09f, 0.11f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        val camera = cameraFor(context)
        val environment = environmentFor(context)

        lineRenderer.render(context.commands, camera)

        modelBatch.begin(camera)
        context.commands.forEach { command ->
            when (command) {
                is DrawModel -> renderModel(command, environment, camera)
                else -> Unit
            }
        }
        modelBatch.end()

        drawDebugOverlay(context)
    }

    override fun resize(width: Int, height: Int) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
    }

    override fun dispose() {
        modelBatch.dispose()
        lineRenderer.dispose()
        spriteBatch.dispose()
        font.dispose()
        primitives.values.forEach { it.dispose() }
        assets.dispose()
    }

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

        val existing = instances[command.entityId]
        val instance = if (existing?.model === model) {
            existing
        } else {
            ModelInstance(model).also { created ->
                instances[command.entityId] = created
            }
        }

        val material = command.material
        instance.materials.forEach {
            it.set(
                ColorAttribute.createDiffuse(
                    material.baseColor.r,
                    material.baseColor.g,
                    material.baseColor.b,
                    material.baseColor.a,
                ),
            )
        }

        val transform = command.transform
        instance.transform.idt()
        instance.transform.translate(transform.position.x, transform.position.y, transform.position.z)
        instance.transform.rotate(Vector3.X, transform.eulerDegrees.x)
        instance.transform.rotate(Vector3.Y, transform.eulerDegrees.y)
        instance.transform.rotate(Vector3.Z, transform.eulerDegrees.z)
        instance.transform.scale(transform.scale.x, transform.scale.y, transform.scale.z)

        modelBatch.render(instance, environment)
    }

    private fun renderGltfScene(command: DrawModel, environment: Environment, camera: Camera) {
        assets.queue(command.model)
        val sceneAsset = assets.gltfScene(command.model) ?: return
        val scene = gltfScenes.getOrPut(command.entityId) {
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

    private fun primitive(path: String): Model = primitives.getOrPut(path) {
        ModelBuilder().createBox(
            1f,
            1f,
            1f,
            com.badlogic.gdx.graphics.g3d.Material(ColorAttribute.createDiffuse(0.1f, 0.62f, 0.82f, 1f)),
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong(),
        )
    }

    private fun drawDebugOverlay(context: RenderContext) {
        debug.put("FPS", Gdx.graphics.framesPerSecond)
        debug.put("Render commands", context.commands.size)
        debug.put("Asset progress", "${"%.0f".format(assets.progress() * 100f)}%")

        if (!debug.enabled) return

        spriteBatch.begin()
        var y = height - 12f
        debug.entries.forEach { line ->
            font.draw(spriteBatch, line, 12f, y)
            y -= 18f
        }
        spriteBatch.end()
    }
}

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

    fun render(commands: List<com.pashkd.krender.engine.api.RenderCommand>, camera: Camera) {
        val vertices = mutableListOf<Float>()
        commands.forEach { command ->
            when (command) {
                is DrawWorldGrid -> appendGrid(vertices, command)
                is DrawWorldAxes -> appendAxes(vertices, command)
                else -> Unit
            }
        }

        val vertexCount = vertices.size / FLOATS_PER_VERTEX
        if (vertexCount == 0) return

        val lineMesh = meshFor(vertexCount)
        lineMesh.setVertices(vertices.toFloatArray())

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(false)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glLineWidth(1f)

        shader.bind()
        shader.setUniformMatrix("u_projViewTrans", camera.combined)
        lineMesh.render(shader, GL20.GL_LINES, 0, vertexCount)

        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glDepthMask(true)
    }

    fun dispose() {
        mesh?.dispose()
        shader.dispose()
    }

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

    private fun appendAxes(vertices: MutableList<Float>, command: DrawWorldAxes) {
        val length = command.length.coerceAtLeast(1f)
        appendLine(vertices, Vec3(-length, 0f, 0f), Vec3(length, 0f, 0f), com.pashkd.krender.engine.api.Color(0f, 1f, 0f, 1f))
        appendLine(vertices, Vec3(0f, -length, 0f), Vec3(0f, length, 0f), com.pashkd.krender.engine.api.Color(1f, 0f, 0f, 1f))
        appendLine(vertices, Vec3(0f, 0f, -length), Vec3(0f, 0f, length), com.pashkd.krender.engine.api.Color(0f, 0.35f, 1f, 1f))
    }

    private fun appendLine(
        vertices: MutableList<Float>,
        from: Vec3,
        to: Vec3,
        color: com.pashkd.krender.engine.api.Color,
    ) {
        appendVertex(vertices, from, color)
        appendVertex(vertices, to, color)
    }

    private fun appendVertex(vertices: MutableList<Float>, position: Vec3, color: com.pashkd.krender.engine.api.Color) {
        vertices += position.x
        vertices += position.y
        vertices += position.z
        vertices += color.r
        vertices += color.g
        vertices += color.b
        vertices += color.a
    }

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

class GdxLogger(
    private val debug: DebugService,
) : Logger {
    override fun log(level: LogLevel, tag: String, error: Throwable?, message: () -> String) {
        if (!isEnabled(level)) return
        val text = message()
        debug.recordLog(
            LogEntry(
                level = level,
                tag = tag,
                message = text,
                frame = debug.frame,
                threadName = Thread.currentThread().name,
                error = error,
            ),
        )
        when (level) {
            LogLevel.Trace, LogLevel.Debug -> Gdx.app.debug(tag, text)
            LogLevel.Info -> Gdx.app.log(tag, text)
            LogLevel.Warn -> Gdx.app.log(tag, "WARN: $text")
            LogLevel.Error -> Gdx.app.error(tag, text, error)
        }
    }
}

private fun AssetRef<*>.isGltf(): Boolean =
    path.endsWith(".glb", ignoreCase = true) || path.endsWith(".gltf", ignoreCase = true)
