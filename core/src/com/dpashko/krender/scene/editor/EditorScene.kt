package com.dpashko.krender.scene.editor

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.dpashko.krender.compose.ComposeRenderer
import com.dpashko.krender.scene.common.BaseScene
import com.dpashko.krender.shader.AxisShader
import com.dpashko.krender.shader.GridShader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EditorScene @Inject constructor(
    controller: EditorController,
    private val navigator: EditorNavigator,
) : BaseScene<EditorController, EditorResult>(controller) {

    private lateinit var axisShader: AxisShader
    private lateinit var gridShader: GridShader
    private lateinit var cameraController: EditorCameraController
    private lateinit var debugShapesRenderer: ShapeRenderer
    private lateinit var composeRenderer: ComposeRenderer

    override fun create() {
        println("Editor scene initialization.")
        super.create()

        axisShader = AxisShader(axisLength = controller.getState().sceneSize.size)
        gridShader = GridShader(gridSize = controller.getState().sceneSize.size.toInt())
        debugShapesRenderer = ShapeRenderer().apply {
            color = Color.GREEN
        }
        cameraController = EditorCameraController()
        composeRenderer = ComposeRenderer().apply {
            init {
                createInterfaceWidget()
            }
        }
        input.apply {
            addProcessor(composeRenderer)
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
            // Draw world boundaries.
            begin(ShapeRenderer.ShapeType.Line)
            state.worldBounds.apply {
                box(min.x, min.y, max.z, width, height, depth)
            }
            end()
            // Draw Camera target point
            begin(ShapeRenderer.ShapeType.Point)
            point(
                state.target.x,
                state.target.y,
                state.target.z
            )
            end()
        }

        composeRenderer.render()
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
        composeRenderer.dispose()
        super.dispose()
    }
}

@Composable
@Preview
fun createInterfaceWidget() {
    return MaterialTheme {
        Surface(
            border = BorderStroke(
                width = Dp(1f),
                brush = SolidColor(androidx.compose.ui.graphics.Color.Black)
            )
        ) {
            var text by remember { mutableStateOf("") }
            val history by remember { mutableStateOf(mutableListOf<String>()) }

            Column {

                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = ({
                        Text("Some input data...")
                    })
                )

                Row(
                    modifier = Modifier
                        .padding(Dp(10f))
                        .align(alignment = Alignment.CenterHorizontally)
                ) {
                    Button(
                        modifier = Modifier.padding(Dp(10f)),
                        onClick = {
                            history.add(text)
                            text = ""
                        }) {
                        Text("Add")
                    }
                    Button(
                        modifier = Modifier.padding(Dp(10f)),
                        onClick = {
                            history.clear()
                        }) {
                        Text("Clear All")
                    }
                }
            }
        }
    }
}
