package com.dpashko.krender.scene.editor.controller

import com.dpashko.krender.scene.common.SceneController
import com.dpashko.krender.scene.editor.EditorState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class EditorSceneController @Inject constructor() : SceneController<EditorState> {

    private var _state = MutableStateFlow(EditorState())

    override suspend fun init() {
        println("Editor scene controller initialized.")
    }

    override suspend fun update(deltaTime: Float) {
    }

    override suspend fun dispose() {
        println("Editor Scene controller disposed.")
    }

    override fun getState(): StateFlow<EditorState> = _state

}
