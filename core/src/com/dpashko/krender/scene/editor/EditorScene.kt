/*
 * Property of Medtronic MiniMed.
 */

package com.dpashko.krender.scene.editor

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.dpashko.krender.scene.common.BaseScene

class EditorScene(
    private val controller: EditorSceneController
) : BaseScene<EditorSceneState, EditorSceneController>(
    controller
) {

    private lateinit var spriteBatch: SpriteBatch

    override fun create() {
        spriteBatch = SpriteBatch()
    }

    override fun load() {
        println("Editor scene loaded.")
    }

    override fun start() {
        println("Editor scene started.")
    }

    override fun render() {
        val state = controller.getState()
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

    override fun destroy() {
        spriteBatch.dispose()
        super.destroy()
    }
}
