package com.dpashko.krender.scene.editor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.dpashko.krender.scene.common.BaseScene
import com.dpashko.krender.shader.AxisShader
import com.dpashko.krender.shader.GridShader
import com.dpashko.krender.skin.SkinProvider

class EditorScene(
    private val controller: EditorSceneController,
) : BaseScene<EditorSceneState, EditorSceneController>(
    controller
) {

    private lateinit var ui: EditorSceneInterface
    private lateinit var axisShader: AxisShader
    private lateinit var gridShader: GridShader
    private lateinit var cameraController: EditorSceneCameraController
    private lateinit var debugShapesRenderer: ShapeRenderer

    override fun create() {
        ui = EditorSceneInterface(controller, SkinProvider.default)

        axisShader = AxisShader(axisLength = controller.getState().sceneSize.size)
        gridShader = GridShader(gridSize = controller.getState().sceneSize.size.toInt())
        debugShapesRenderer = ShapeRenderer().apply {
            color = Color.GREEN
        }
        cameraController = EditorSceneCameraController()

        Gdx.input.inputProcessor = InputMultiplexer().apply {
            addProcessor(ui)
            addProcessor(cameraController)
        }
    }

    override fun start() {
        println("Editor scene started.")
    }

    override fun update(deltaTime: Float) {
        super.update(deltaTime)
        ui.act(deltaTime)
        cameraController.update(controller.getState(), deltaTime)
    }

    override fun render() {
        val state = controller.getState()
        if (state.drawGrid) {
            gridShader.draw(state.camera, state.sceneSize.size.toInt())
        }
        if (state.drawAxis) {
            axisShader.draw(state.camera, state.sceneSize.size)
        }

        debugShapesRenderer.apply {
            projectionMatrix = state.camera.combined
            begin(ShapeRenderer.ShapeType.Line)
            state.worldBounds.apply {
                box(min.x, min.y, max.z, width, height, depth)
            }
            end()
            begin(ShapeRenderer.ShapeType.Point)
            point(
                state.target.x,
                state.target.y,
                state.target.z
            )
            end()
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
        axisShader.dispose()
        super.destroy()
    }
}
