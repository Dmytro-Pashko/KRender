package com.dpashko.krender.scene.editor.controller

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3
import com.dpashko.krender.scene.common.SceneController
import com.dpashko.krender.scene.editor.EditorSceneAssetsManager
import com.dpashko.krender.scene.editor.model.EditorSceneState
import com.dpashko.krender.scene.editor.model.PerformanceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import net.mgsx.gltf.scene3d.scene.Scene
import net.mgsx.gltf.scene3d.scene.SceneManager
import net.mgsx.gltf.scene3d.shaders.PBRDepthShaderProvider
import net.mgsx.gltf.scene3d.shaders.PBRShaderConfig
import net.mgsx.gltf.scene3d.shaders.PBRShaderProvider
import javax.inject.Inject

/**
 * Controller that manages the logic and state of the Editor scene.
 */
class EditorSceneController @Inject constructor(
    private val assetManager: EditorSceneAssetsManager,
) : SceneController<EditorSceneState> {

    private var _sceneState = MutableStateFlow(EditorSceneState())
    private var _performanceState = MutableStateFlow(PerformanceState())

    var objects: MutableList<ModelInstance> = mutableListOf()
    var shaderConfig = PBRShaderConfig().apply {
        numBones = 60
        numDirectionalLights = 1
        numPointLights = 0
    }
    var depthConfig = PBRShaderProvider.createDefaultDepthConfig().apply {
        numBones = 60
    }
    var sceneManager =
        SceneManager(PBRShaderProvider(shaderConfig), PBRDepthShaderProvider(depthConfig))

    /**
     * Initializes the Editor scene controller.
     * This method is called during the initialization of the controller.
     */
    override suspend fun init() {
        println("Editor scene controller initialized.")
        assetManager.loadAssets()
        _sceneState.value = EditorSceneState(
            isLoading = true
        )
    }

    /**
     * Updates the state of the controller and the scene.
     * This method is called at regular intervals to update the scene state.
     *
     * @param deltaTime The time elapsed since the last update.
     */
    override suspend fun update(deltaTime: Float) {
        if (assetManager.update()) {
            if (_sceneState.value.isLoading) {
                _sceneState.value = EditorSceneState(
                    isLoading = false
                )
            }
        }
        sceneManager.update(deltaTime)
        // Collect performance & usage data.
        _performanceState.value = PerformanceState(
            fps = Gdx.graphics.framesPerSecond,
            usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(),
            totalMemory = Runtime.getRuntime().totalMemory()
        )
    }

    /**
     * Disposes of the Editor scene controller.
     * This method is called when the controller is being disposed of or cleaned up.
     */
    override suspend fun dispose() {
        assetManager.dispose()
        sceneManager.dispose()
        println("Editor Scene controller disposed.")
    }

    fun getSceneState(): StateFlow<EditorSceneState> = _sceneState.asStateFlow()

    fun getPerformanceState(): StateFlow<PerformanceState> = _performanceState.asStateFlow()

    fun addActor() {
        val actor = assetManager.getActorModel()
        val actorModel = actor.scene.model
        val instance = ModelInstance(actorModel).apply {
            this.transform.setTranslation(Vector3.Zero)
        }
        instance.calculateTransforms()
        objects.add(instance)
        sceneManager.addScene(Scene(actor.scene))
    }
}
