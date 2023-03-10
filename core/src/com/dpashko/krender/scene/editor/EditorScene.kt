/*
 * Property of Medtronic MiniMed.
 */

package com.dpashko.krender.scene.editor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Vector2
import com.dpashko.krender.scene.common.BaseScene

class EditorScene(
    private val controller: EditorSceneController
) : BaseScene<EditorSceneState, EditorSceneController>(
    controller
) {

    private lateinit var spriteBatch: SpriteBatch
    private lateinit var texture: Texture

    override fun create() {
        spriteBatch = SpriteBatch()
        texture = Texture(Gdx.files.internal("badlogic.jpg"))
    }

    override fun start() {
        println("Editor scene started.")
    }

    override fun render() {
        val state = controller.getState()
        spriteBatch.begin()
        spriteBatch.draw(
            texture,
            state.position.x,
            state.position.y,
            state.imageSize,
            state.imageSize
        )
        spriteBatch.end()
    }

    override fun pause() {
        println("Editor scene paused.")
    }

    override fun resume() {
        println("Editor scene resumed.")
    }

    override fun stop() {
        println("Editor scene stopped.")
    }

    override fun resize(width: Int, height: Int) {

        controller.getState().screenHeight = height
        controller.getState().screenWidth = width
        controller.getState().position = Vector2()
    }

    override fun destroy() {
        spriteBatch.dispose()
        super.destroy()
    }
}
