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
