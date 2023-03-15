package com.dpashko.krender.scene.editor

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.dpashko.krender.scene.common.BaseScene
import com.dpashko.krender.shader.AxisShader
import com.dpashko.krender.shader.GridShader
import com.dpashko.krender.skin.SkinProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EditorScene @Inject constructor(
    private val controller: EditorSceneController,
) : BaseScene<EditorSceneController>(controller),
    EditorSceneInterfaceWidget.EditorSceneInterfaceListener {

    private lateinit var ui: EditorSceneInterfaceWidget
    private lateinit var axisShader: AxisShader
    private lateinit var gridShader: GridShader
    private lateinit var cameraController: EditorSceneCameraController
    private lateinit var debugShapesRenderer: ShapeRenderer

    override fun create() {
        println("Editor scene initialization.")
        super.create()

        ui = EditorSceneInterfaceWidget(
            listener = this,
            state = controller.getState(),
            skin = SkinProvider.default
        )
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
        println("Editor scene created.")
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
            gridShader.draw(state.camera)
        }
        if (state.drawAxis) {
            axisShader.draw(state.camera)
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
        ui.draw(state = state)
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
        println("Editor scene resized: w=$width, h=$height")
    }

    override fun dispose() {
        ui.dispose()
        gridShader.dispose()
        axisShader.dispose()
        super.dispose()
    }

    override fun onSceneSizeChanged(size: EditorSceneState.SceneSize) {
        println("Scene size changed to: $size")
        axisShader.dispose()
        gridShader.dispose()
        axisShader = AxisShader(axisLength = size.size)
        gridShader = GridShader(gridSize = size.size.toInt())
        controller.onSceneSize(size)
    }

    override fun onDrawAxisChanged(isDrawAxis: Boolean) {
        println("Draw axis option changed to : $isDrawAxis")
        controller.onDrawAxisChanged(isDrawAxis)
    }

    override fun onDrawGridChanged(isDrawGrid: Boolean) {
        println("Draw grid option changed to : $isDrawGrid")
        controller.onDrawGridChanged(isDrawGrid)
    }
}
