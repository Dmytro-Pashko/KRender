package com.dpashko.krender.scene.editor

import com.dpashko.krender.scene.common.BaseScene
import com.dpashko.krender.shader.AxisShader
import com.dpashko.krender.shader.GridShader
import com.dpashko.krender.skin.SkinProvider

class EditorScene(
    private val controller: EditorSceneController
) : BaseScene<EditorSceneState, EditorSceneController>(
    controller
) {

    private lateinit var ui: EditorSceneUi
//    private lateinit var axisShader: AxisShader
    private lateinit var gridShader: GridShader

    override fun create() {
        ui = EditorSceneUi(controller.getState(), SkinProvider.default)

//        axisShader = AxisShader(axisLength = controller.getState().gridSize.size)
        gridShader = GridShader(gridSize = controller.getState().gridSize.size.toInt())
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
        if (state.drawGrid) {
            gridShader.draw(state.camera)
        }
        if (state.drawAxis) {
//            axisShader.draw(state.camera)
        }
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
    }

    override fun destroy() {
        ui.dispose()
        gridShader.dispose()
//        axisShader.dispose()
        super.destroy()
    }
}
