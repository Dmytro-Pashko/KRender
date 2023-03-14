package com.dpashko.krender.shader

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.glutils.VertexBufferObjectWithVAO
import com.badlogic.gdx.utils.Disposable

class AxisShader(
    private var axisLength: Float = 64.0f, private val axisWidth: Float = 3f
) : Disposable {

    private var vertices = createVertices()
    private val shader = ShaderProvider.axisShader()

    private fun createVertices() = VertexBufferObjectWithVAO(
        true, 6, VertexAttribute.Position(), VertexAttribute.ColorUnpacked()
    ).apply {
        val vertices = floatArrayOf(
            //X
            -axisLength / 2f, 0f, 0f, 1f, 0f, 0f, 0f,
            axisLength / 2f, 0f, 0f, 1f, 0f, 0f, 0f,
            //Y
            0f, -axisLength / 2f, 0f, 0f, 1f, 0f, 0f,
            0f, axisLength / 2f, 0f, 0f, 1f, 0f, 0f,
            //Z
            0f, 0f, -axisLength / 2f, 0f, 0f, 1f, 0f,
            0f, 0f, axisLength / 2f, 0f, 0f, 1f, 0f,
        )
        setVertices(vertices, 0, vertices.size)
    }

    fun draw(camera: Camera, axisLength: Float) {
        if (axisLength != this.axisLength) {
            this.axisLength = axisLength
            vertices.dispose()
            vertices = createVertices()
        }
        shader.bind()
        shader.setUniformMatrix("cameraCombinedMatrix", camera.combined)
        vertices.bind(shader)
        Gdx.gl.glLineWidth(axisWidth)
        Gdx.gl.glDrawArrays(GL20.GL_LINES, 0, vertices.numVertices)
        vertices.unbind(shader)
        Gdx.gl.glLineWidth(1f)
    }

    override fun dispose() {
        vertices.dispose()
        shader.dispose()
    }
}
