package com.dpashko.krender.shader

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.glutils.VertexBufferObjectWithVAO
import com.badlogic.gdx.utils.Disposable
import java.util.LinkedList

class GridShader(
    private var gridSize: Int = 64,
    private val gridLineWidth: Float = 1f,
    lineColor: Color = Color.GRAY
) : Disposable {

    private val colorVector = floatArrayOf(lineColor.r, lineColor.g, lineColor.b, 0f)
    private var vertices = createVertices()
    private val shader = ShaderProvider.gridShader()

    private fun createVertices() =
        VertexBufferObjectWithVAO(true, 4 * (gridSize + 1), VertexAttribute.Position()).apply {
            val vertices = LinkedList<Float>()
            for (line in -gridSize / 2..gridSize / 2) {
                //Y lines.
                vertices.addAll(
                    arrayOf(
                        line.toFloat(), -gridSize / 2f, 0f,
                        line.toFloat(), gridSize / 2f, 0f
                    )
                )
                //X lines.
                vertices.addAll(
                    arrayOf(
                        -gridSize / 2f, line.toFloat(), 0f,
                        gridSize / 2f, line.toFloat(), 0f
                    )
                )
            }
            setVertices(vertices.toFloatArray(), 0, vertices.size)
        }

    fun draw(camera: Camera, gridSize: Int) {
        if (gridSize != this.gridSize) {
            this.gridSize = gridSize
            vertices.dispose()
            vertices = createVertices()
        }
        shader.bind()
        shader.setUniformMatrix("cameraCombinedMatrix", camera.combined)
        shader.setUniform4fv("lineColor", colorVector, 0, colorVector.size)
        vertices.bind(shader)
        Gdx.gl.glLineWidth(gridLineWidth)
        Gdx.gl.glDrawArrays(GL20.GL_LINES, 0, vertices.numVertices)
        vertices.unbind(shader)
    }

    override fun dispose() {
        vertices.dispose()
        shader.dispose()
    }
}
