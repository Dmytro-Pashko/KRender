package com.dpashko.krender.shader

import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.glutils.ShaderProgram

class WireframeShader {

    private lateinit var shader: ShaderProgram
    private var isInitialized = false

    private val vertex: String = """
        attribute vec3 a_position;
        uniform mat4 u_modelViewProjection;

        void main() {
        gl_Position = u_modelViewProjection * vec4(a_position, 1.0);
        }
    """

    private val fragment: String = """
        #ifdef GL_ES
        precision mediump float;
        #endif

        uniform vec3 u_wireframeColor;
        void main() {
        gl_FragColor = vec4(u_wireframeColor, 1.0);
        }
    """

    fun init() {
        ShaderProgram.pedantic = false // LibGDX shaders might not be "strict" GLSL
        shader = ShaderProgram(vertex, fragment)
        if (!shader.isCompiled) {
            println("Shader compilation error: ${shader.log}")
        }
        isInitialized = true
    }

    fun draw(camera: Camera, model: ModelInstance) {
        if (!isInitialized) {
            println("Shader not initialized yet.")
            return
        }
        shader.bind()
        shader.setUniformMatrix("u_modelViewProjection", camera.combined)
        shader.setUniformf("u_wireframeColor", 1f, 1f, 0f)
        model.model.meshes.forEach { mesh ->
            mesh.render(shader, GL20.GL_LINES)
        }
    }
}