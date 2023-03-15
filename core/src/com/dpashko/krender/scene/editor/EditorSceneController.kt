package com.dpashko.krender.scene.editor

import com.badlogic.gdx.math.Vector3
import com.dpashko.krender.scene.common.SceneController
import javax.inject.Inject

class EditorSceneController @Inject constructor() : SceneController<EditorSceneState> {

    private lateinit var state: EditorSceneState

    override fun init() {
        state = EditorSceneState()
    }

    override fun update(deltaTime: Float) {}

    override fun dispose() {}

    override fun getState(): EditorSceneState = state

    fun onSceneSize(selectedSize: EditorSceneState.SceneSize?) {
        if (selectedSize != null) {
            state.sceneSize = selectedSize
            state.worldBounds.set(
                Vector3(-selectedSize.size, -selectedSize.size, 0f).scl(0.5f),
                Vector3(selectedSize.size, selectedSize.size, selectedSize.size).scl(0.5f)
            )
        }
    }

    fun onDrawAxisChanged(isDrawAxis: Boolean) {
        state.drawAxis = isDrawAxis
    }

    fun onDrawGridChanged(isDrawGrid: Boolean) {
        state.drawGrid = isDrawGrid
    }
}
