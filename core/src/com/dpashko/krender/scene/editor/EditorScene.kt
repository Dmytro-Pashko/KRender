package com.dpashko.krender.scene.editor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Vector2
import com.dpashko.krender.scene.common.BaseScene
import com.dpashko.krender.skin.SkinProvider

class EditorScene(
    private val controller: EditorSceneController
) : BaseScene<EditorSceneState, EditorSceneController>(
    controller
) {

    private lateinit var ui: EditorSceneUi
    private lateinit var spriteBatch: SpriteBatch
    private lateinit var texture: Texture

    override fun create() {
        ui = EditorSceneUi(controller.getState(), SkinProvider.default)
        spriteBatch = SpriteBatch()
        texture = Texture(Gdx.files.internal("textures/badlogic.jpg"))
    }

    override fun start() {
        println("Editor scene started.")
    }

    override fun update(deltaTime: Float) {
        super.update(deltaTime)
        ui.act(deltaTime)
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
        ui.draw()
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
        controller.getState().apply {
            // Image size ~ 10% of screen width.
            imageSize = controller.getState().screenWidth * 0.2f
            screenHeight = height
            screenWidth = width
            position = Vector2()
        }
    }

    override fun destroy() {
        spriteBatch.dispose()
        ui.dispose()
        super.destroy()
    }
}
