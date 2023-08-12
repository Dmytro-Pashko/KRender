package com.dpashko.krender.scene.editor

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.dpashko.krender.common.MemoryFormatter
import com.dpashko.krender.common.VectorFormatter
import com.dpashko.krender.compose.ComposeManager
import com.dpashko.krender.scene.common.BaseScene
import com.dpashko.krender.scene.editor.controller.EditorCameraController
import com.dpashko.krender.scene.editor.controller.EditorSceneController
import com.dpashko.krender.scene.editor.model.EditorResult
import com.dpashko.krender.shader.AxisShader
import com.dpashko.krender.shader.GridShader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EditorScene @Inject constructor(
    controller: EditorSceneController,
    private val navigator: EditorNavigator,
    private val composeManager: ComposeManager,
) : BaseScene<EditorSceneController, EditorResult>(controller) {

    private lateinit var axisShader: AxisShader
    private lateinit var gridShader: GridShader
    private lateinit var cameraController: EditorCameraController
    private lateinit var debugShapesRenderer: ShapeRenderer

    override fun create() {
        println("Started  Editor scene initialization.")
        super.create()
        axisShader = AxisShader(axisLength = controller.getSceneState().value.sceneSize.size)
        gridShader = GridShader(gridSize = controller.getSceneState().value.sceneSize.size.toInt())
        debugShapesRenderer = ShapeRenderer().apply {
            color = Color.GREEN
        }
        cameraController = EditorCameraController(controller).apply {
            init()
        }
        if (!composeManager.isInitialized) {
            composeManager.init()
        }
        composeManager.getRenderer().apply {
            setContent {
                createInterfaceWidget(controller, cameraController)
            }
        }
        input.apply {
            addProcessor(composeManager.inputProcessor())
            addProcessor(cameraController)
        }
//        trackStateChanges()
        println("Editor scene initialized.")
    }

    /**
     * For debug purposes only.
     */
    private fun trackStateChanges() {
        val context = CoroutineScope(Dispatchers.IO)
        cameraController.getState().onEach {
            println("${Thread.currentThread()}: Camera state changed: $it")
        }.catch {
            println("Error: $it")
        }.launchIn(context)

        controller.getSceneState().onEach {
            println("${Thread.currentThread()}: Scene state changed: $it")
        }.catch {
            println("Error: $it")
        }.launchIn(context)
        controller.getPerformanceState().onEach {
            println("${Thread.currentThread()}: Performance state changed: $it")
        }.catch {
            println("Error: $it")
        }.launchIn(context)
    }

    override fun update(deltaTime: Float) {
        sceneScope.launch {
            cameraController.update(deltaTime)
        }
        super.update(deltaTime)
    }

    override fun render() {
        val state = controller.getSceneState().value
        val camera = cameraController.camera

        if (state.drawGrid) {
            gridShader.draw(camera)
        }
        if (state.drawAxis) {
            axisShader.draw(camera)
        }

        debugShapesRenderer.apply {
            projectionMatrix = camera.combined
            // Draw world boundaries.
            begin(ShapeRenderer.ShapeType.Line)
            state.worldBounds.apply {
                box(min.x, min.y, max.z, width, height, depth)
            }
            end()
        }
        composeManager.getRenderer().render()
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
        composeManager.getRenderer().dispose()
        super.dispose()
    }
}

@Composable
@Preview
fun createInterfaceWidget(
    sceneController: EditorSceneController,
    cameraController: EditorCameraController
) {

    val sceneState by sceneController.getSceneState().collectAsState()
    val cameraState by cameraController.getState().collectAsState()
    val performanceState by sceneController.getPerformanceState().collectAsState()

    return BoxWithConstraints {
        Box(
            modifier = Modifier.background(color = androidx.compose.ui.graphics.Color.White),
        ) {
            Column(
                modifier = Modifier.padding(all = Dp(8f)),
            ) {
                Text("[Camera]")
                Text("Pos=[${VectorFormatter.formatVector3(cameraState.position)}]")
                Text("Dir=[${VectorFormatter.formatVector3(cameraState.direction)}]")
                Text("ViewPortWidth=[${cameraState.viewportWidth}]")
                Text("ViewPortHeight=[${cameraState.viewportHeight}]")
                Text("near=[${cameraState.near}]")
                Text("far=[${cameraState.far}]")

                Spacer(Modifier.height(Dp(20f)))

                Text("[Scene]")
                Text("Size=[${sceneState.sceneSize.size}]")
                Text("Grid=[${sceneState.drawGrid}]")
                Text("Axis=[${sceneState.drawAxis}]")

                Spacer(Modifier.height(Dp(20f)))

                Text("[Performance]")
                Text("FPS=[${performanceState.fps}]")
                Text("Used=[${MemoryFormatter.convertToMB(performanceState.usedMemory)}]")
                Text("Total=[${MemoryFormatter.convertToMB(performanceState.totalMemory)}]")

            }
        }
    }
}
