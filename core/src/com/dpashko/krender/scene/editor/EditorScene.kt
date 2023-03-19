package com.dpashko.krender.scene.editor

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.dpashko.krender.scene.common.BaseScene
import com.dpashko.krender.shader.AxisShader
import com.dpashko.krender.shader.GridShader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EditorScene @Inject constructor(
    controller: EditorController,
    private val navigator: EditorNavigator,
) : BaseScene<EditorController, EditorResult>(controller),
    EditorUiStage.EditorSceneInterfaceListener {

    private lateinit var axisShader: AxisShader
    private lateinit var gridShader: GridShader
    private lateinit var cameraController: EditorCameraController
    private lateinit var debugShapesRenderer: ShapeRenderer

    override fun create() {
        println("Editor scene initialization.")
        super.create()

        axisShader = AxisShader(axisLength = controller.getState().sceneSize.size)
        gridShader = GridShader(gridSize = controller.getState().sceneSize.size.toInt())
        debugShapesRenderer = ShapeRenderer().apply {
            color = Color.GREEN
        }
        cameraController = EditorCameraController()

        input.apply {
            addProcessor(cameraController)
        }
        println("Editor scene initialized.")
    }

    override fun update(deltaTime: Float) {
        super.update(deltaTime)
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
    }

    override fun pause() {
        super.pause()
        println("Editor scene paused.")
    }

    override fun resume() {
        super.resume()
        println("Editor scene resumed.")
    }

    override fun resize(width: Int, height: Int) {
        println("Editor scene resized: w=$width, h=$height")
    }

    override fun dispose() {
        gridShader.dispose()
        axisShader.dispose()
        super.dispose()
    }

    override fun onSceneSizeChanged(size: EditorState.SceneSize) {
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

    override fun onGenerateTerrainClicked() {
        navigator.generateTerrain()
    }
}
