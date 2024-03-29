package com.dpashko.krender.shader

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.glutils.ShaderProgram

object ShaderProvider {

    private var axis_fragment = Gdx.files.internal("shaders/axis_f.glsl")
    private var axis_vertex = Gdx.files.internal("shaders/axis_v.glsl")

    private var grid_fragment = Gdx.files.internal("shaders/grid_f.glsl")
    private var grid_vertex = Gdx.files.internal("shaders/grid_v.glsl")

    fun axisShader(): ShaderProgram {
        val fragment = axis_fragment.readString()
        val vertex = axis_vertex.readString()
        return ShaderProgram(vertex, fragment)
    }

    fun gridShader(): ShaderProgram {
        val fragment = grid_fragment.readString()
        val vertex = grid_vertex.readString()
        return ShaderProgram(vertex, fragment)
    }
}
