/*
 * Property of Medtronic MiniMed.
 */

package com.dpashko.krender.scene.editor

import com.dpashko.krender.scene.common.SceneController

class EditorSceneController(
    private var state: EditorSceneState = EditorSceneState()
) : SceneController<EditorSceneState> {

    override fun update(deltaTime: Float) {
        TODO("Not yet implemented")
    }

    override fun destroy() {
        TODO("Not yet implemented")
    }

    override fun getState(): EditorSceneState = state
}
