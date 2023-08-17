package com.dpashko.krender.assets

import com.badlogic.gdx.assets.loaders.ShaderProgramLoader

object Shaders {

    /**
     * Shader that renders grid.
     */
    internal val GRID_SHADER = ShaderProgramLoader.ShaderProgramParameter().apply {

        fragmentFile = "assets/shaders/grid_f.glsl"
        vertexFile = "assets/shaders/grid_v.glsl"
    }

    internal val AXIS_SHADER = ShaderProgramLoader.ShaderProgramParameter().apply {
        fragmentFile = "assets/shaders/axis_f.glsl"
        vertexFile = "assets/shaders/axis_v.glsl"
    }
}
