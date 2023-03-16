package com.dpashko.krender.scene.editor

import com.badlogic.gdx.math.Vector3
import com.dpashko.krender.scene.common.SceneController
import javax.inject.Inject

class EditorController @Inject constructor() : SceneController<EditorState> {

    private lateinit var state: EditorState

    override fun init() {
        state = EditorState()
    }

    override fun update(deltaTime: Float) {}

    override fun dispose() {}

    override fun getState(): EditorState = state

    fun onSceneSize(selectedSize: EditorState.SceneSize?) {
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
