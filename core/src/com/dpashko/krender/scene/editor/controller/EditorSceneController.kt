package com.dpashko.krender.scene.editor.controller

import com.badlogic.gdx.Gdx
import com.dpashko.krender.scene.common.SceneController
import com.dpashko.krender.scene.editor.model.EditorSceneState
import com.dpashko.krender.scene.editor.model.PerformanceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Controller that manages the logic and state of the Editor scene.
 */
class EditorSceneController @Inject constructor() : SceneController<EditorSceneState> {

    private var _sceneState = MutableStateFlow(EditorSceneState())
    private var _performanceState = MutableStateFlow(PerformanceState())

    /**
     * Initializes the Editor scene controller.
     * This method is called during the initialization of the controller.
     */
    override suspend fun init() {
        println("Editor scene controller initialized.")
    }

    /**
     * Updates the state of the controller and the scene.
     * This method is called at regular intervals to update the scene state.
     *
     * @param deltaTime The time elapsed since the last update.
     */
    override suspend fun update(deltaTime: Float) {
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
        println("Editor Scene controller disposed.")
    }

    fun getSceneState(): StateFlow<EditorSceneState> = _sceneState.asStateFlow()

    fun getPerformanceState(): StateFlow<PerformanceState> = _performanceState.asStateFlow()

}
