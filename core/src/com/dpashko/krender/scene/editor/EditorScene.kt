package com.dpashko.krender.scene.editor

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.collision.BoundingBox
import com.dpashko.krender.common.MemoryFormatter
import com.dpashko.krender.common.VectorFormatter
import com.dpashko.krender.compose.ComposeManager
import com.dpashko.krender.scene.common.BaseScene
import com.dpashko.krender.scene.editor.controller.EditorCameraController
import com.dpashko.krender.scene.editor.controller.EditorSceneController
import com.dpashko.krender.scene.editor.model.EditorResult
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EditorScene @Inject constructor(
    controller: EditorSceneController,
    private val navigator: EditorNavigator,
    private val composeManager: ComposeManager,
    private val assetsManager: EditorSceneAssetsManager
) : BaseScene<EditorSceneController, EditorResult>(controller) {

    //    private lateinit var axisShader: AxisShader
//    private lateinit var gridShader: GridShader
    private lateinit var cameraController: EditorCameraController
    private lateinit var debugShapesRenderer: ShapeRenderer
    private lateinit var modelBatch: ModelBatch

    override fun create() {
        println("Started  Editor scene initialization.")
        super.create()
//        axisShader = AxisShader(axisLength = controller.getSceneState().value.sceneSize.size)
//        gridShader = GridShader(assetsManager.getGridShader(), controller.getSceneState().value.sceneSize.size.toInt())
        debugShapesRenderer = ShapeRenderer().apply {
            color = com.badlogic.gdx.graphics.Color.GREEN
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
        modelBatch = ModelBatch().apply {

        }
        println("Editor scene initialized.")
    }

    override fun update(deltaTime: Float) {
        super.update(deltaTime)
        sceneScope.launch {
            cameraController.update(deltaTime)
        }
    }

    override fun render() {
        val state = controller.getSceneState().value
        val camera = cameraController.camera

        if (!state.isLoading) {

//            if (state.drawGrid) {
//                gridShader.draw(camera)
//            }
//            if (state.drawAxis) {
//                axisShader.draw(camera)
//            }

            if (controller.objects.isNotEmpty()) {
                // Render world objects
                controller.objects.forEach {
                    modelBatch.begin(camera)
                    val boundingBox = BoundingBox().apply {
                        it.calculateBoundingBox(this)
                    }
                    modelBatch.render(it)
                    modelBatch.end()

                    debugShapesRenderer.apply {
                        projectionMatrix = camera.combined
                        // Draw world boundaries.
                        begin(ShapeRenderer.ShapeType.Line)
                        boundingBox.apply {
                            box(min.x, min.y, max.z, width, height, depth)
                        }
                        end()
                    }
                }
            }


            // Draw world boundaries.
            debugShapesRenderer.apply {
                projectionMatrix = camera.combined
                // Draw world boundaries.
                begin(ShapeRenderer.ShapeType.Line)
                state.worldBounds.apply {
                    box(min.x, min.y, max.z, width, height, depth)
                }
                end()
            }
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
//        gridShader.dispose()
//        axisShader.dispose()
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
    return Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Red), contentAlignment = Alignment.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .border(width = Dp(2f), color = Color.DarkGray, RoundedCornerShape(size = Dp(10f)))
                .background(Color.LightGray, RoundedCornerShape(size = Dp(10f)))
                .padding(
                    Dp(20f)
                )
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(Dp(10f)))
                Text("Loading...", style = TextStyle(color = Color.Gray))
            }
        }
    }
    return BoxWithConstraints {
        Box(
            modifier = Modifier.background(color = Color.White),
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
                Text("Objects=[${sceneController.objects.size}]")

                Spacer(Modifier.height(Dp(20f)))

                Text("[Performance]")
                Text("FPS=[${performanceState.fps}]")
                Text("Used=[${MemoryFormatter.convertToMB(performanceState.usedMemory)}]")
                Text("Total=[${MemoryFormatter.convertToMB(performanceState.totalMemory)}]")

                Spacer(Modifier.height(Dp(20f)))
                Button(onClick = {
                    sceneController.addActor()
                }) {
                    Text("Add Actor")
                }

            }
        }
    }
}
