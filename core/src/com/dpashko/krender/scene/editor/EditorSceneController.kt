package com.dpashko.krender.scene.editor

import com.dpashko.krender.scene.common.SceneController

class EditorSceneController(
    private var state: EditorSceneState
) : SceneController<EditorSceneState> {

    override fun update(deltaTime: Float) {

        // Calculate the new position of linear moving according to direction vector and velocity
        val newPosition = state.position.cpy().add(
            state.direction.x * deltaTime * state.velocity,
            state.direction.y * deltaTime * state.velocity
        )
        if (newPosition.x < 0 || newPosition.x > state.screenWidth - state.imageSize) {
            // Horizontal screen limits reached.
            state.direction.x = -state.direction.x
        } else if (newPosition.y < 0 || newPosition.y > state.screenHeight - state.imageSize) {
            // Vertical screen limits reached.
            state.direction.y = -state.direction.y
        } else {
            // Update the position vector
            state.position = newPosition
        }
    }

    override fun destroy() {
        TODO("Not yet implemented")
    }

    override fun getState(): EditorSceneState = state
}
