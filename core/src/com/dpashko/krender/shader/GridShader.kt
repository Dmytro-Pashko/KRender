package com.dpashko.krender.shader

import androidx.compose.runtime.compositionLocalOf
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.graphics.glutils.VertexBufferObjectWithVAO
import com.badlogic.gdx.graphics.glutils.VertexData
import com.badlogic.gdx.utils.Disposable
import java.util.LinkedList

class GridShader() : Disposable {

    private lateinit var shader: ShaderProgram
    private var gridLineWidth: Float = 1f

    private lateinit var lineColor: FloatArray
    private lateinit var vertices: VertexData
    private var isInitialized = false


    private val fragment = """
        #version 300 es

        #ifdef GL_ES
        #extension GL_OES_standard_derivatives : enable
        precision highp float;
        precision highp int;
        #else
        #define highp
        #define mediump
        #define lowp
        #endif

        uniform vec4 lineColor;

        out vec4 fragColor;

        void main()
        {
            fragColor = lineColor;
        }
    """.trimIndent()

    private val vertex = """
        #version 300 es

        in vec3 a_position;

        uniform mat4 cameraCombinedMatrix;

        void main()
        {
            gl_Position = cameraCombinedMatrix * vec4(a_position, 1.0);
        }
    """.trimIndent()

    fun init(gridSize: Int = 64, gridLineWidth: Float = 1f, lineColor: Color = Color.GRAY) {
        this.lineColor = floatArrayOf(lineColor.r, lineColor.g, lineColor.b, 0f)
        this.gridLineWidth = gridLineWidth
        ShaderProgram.pedantic = false // LibGDX shaders might not be "strict" GLSL
        shader = ShaderProgram(vertex, fragment)
        if (!shader.isCompiled) {
            println("Shader compilation error: ${shader.log}")
        }
        vertices = createVertices(gridSize)
        isInitialized = true

    }

    private fun createVertices(gridSize: Int) = VertexBufferObjectWithVAO(true, 4 * (gridSize + 1), VertexAttribute.Position()).apply {
        val vertices = LinkedList<Float>()
        for (line in -gridSize / 2..gridSize / 2) {
            //Y lines.
            vertices.addAll(arrayOf(line.toFloat(), -gridSize / 2f, 0f, line.toFloat(), gridSize / 2f, 0f))
            //X lines.
            vertices.addAll(arrayOf(-gridSize / 2f, line.toFloat(), 0f, gridSize / 2f, line.toFloat(), 0f))
        }
        setVertices(vertices.toFloatArray(), 0, vertices.size)
    }

    fun draw(camera: Camera) {
        if (!isInitialized){
            println("Shader not initialized yet.")
        }
        shader.bind()
        shader.setUniformMatrix("cameraCombinedMatrix", camera.combined)
        shader.setUniform4fv("lineColor", lineColor, 0, lineColor.size)
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
