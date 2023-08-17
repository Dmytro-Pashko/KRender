package com.dpashko.krender.shader

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.graphics.glutils.VertexBufferObjectWithVAO
import com.badlogic.gdx.utils.Disposable

class AxisShader : Disposable {

    private lateinit var shader: ShaderProgram
    private lateinit var vertices: VertexBufferObjectWithVAO
    private var isInitialized = false
    private var axisLineWidth: Float = 3f

    private val vertex: String = """
        #version 300 es
        
        in vec3 a_position;
        in vec4 a_color;
        
        uniform mat4 cameraCombinedMatrix;
        
        out vec4 v_color;
        
        void main()
        {
            gl_Position = cameraCombinedMatrix * vec4(a_position, 1.0);
            v_color = a_color;
        }
    """

    private val fragment: String = """
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
        
        in vec4 v_color;
        
        out vec4 FragColor;
        
        void main()
        {
            FragColor = v_color;
        }
    """

    fun init(axisLength: Float = 64.0f, axisWidth: Float = 3f) {
        ShaderProgram.pedantic = false // LibGDX shaders might not be "strict" GLSL
        shader = ShaderProgram(vertex, fragment)
        if (!shader.isCompiled) {
            println("Shader compilation error: ${shader.log}")
        }
        isInitialized = true
       this.axisLineWidth = axisWidth
        vertices = createVertices(axisLength)
    }

    private fun createVertices(axisLength: Float) = VertexBufferObjectWithVAO(
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

    fun draw(camera: Camera) {
        shader.bind()
        shader.setUniformMatrix("cameraCombinedMatrix", camera.combined)
        vertices.bind(shader)
        Gdx.gl20.glLineWidth(axisLineWidth)
        Gdx.gl20.glDrawArrays(GL20.GL_LINES, 0, vertices.numVertices)
        vertices.unbind(shader)
        Gdx.gl20.glLineWidth(1f)
    }

    override fun dispose() {
        vertices.dispose()
        shader.dispose()
    }
}
