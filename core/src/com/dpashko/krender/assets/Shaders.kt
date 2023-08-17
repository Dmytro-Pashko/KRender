package com.dpashko.krender.assets

import com.badlogic.gdx.assets.AssetDescriptor
import com.badlogic.gdx.assets.loaders.ShaderProgramLoader
import com.badlogic.gdx.graphics.glutils.ShaderProgram

/**
 * Defines all shaders assets, defines vertex and fragment shader assets path.
 */
object Shaders {

    internal val GRID_SHADER = AssetDescriptor(
        "GRID_SHADER",
        ShaderProgram::class.java,
        ShaderProgramLoader.ShaderProgramParameter().apply {
            fragmentFile = "assets/shaders/grid_f.glsl"
            vertexFile = "assets/shaders/grid_v.glsl"
        })

    internal val AXIS_SHADER = AssetDescriptor(
        "AXIS_SHADER",
        ShaderProgram::class.java,
        ShaderProgramLoader.ShaderProgramParameter().apply {
            fragmentFile = "assets/shaders/axis_f.glsl"
            vertexFile = "assets/shaders/axis_v.glsl"
        })

    internal val WIREFRAME_SHADER = AssetDescriptor(
        "WIREFRAME_SHADER",
        ShaderProgram::class.java,
        ShaderProgramLoader.ShaderProgramParameter().apply {
            fragmentFile = "assets/shaders/wireframe_f.glsl"
            vertexFile = "assets/shaders/wireframe_v.glsl"
        })
}
