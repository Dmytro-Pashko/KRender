package com.pashkd.krender.engine.backend.gdx

import com.badlogic.gdx.Gdx

internal object GdxShaderSources {
    fun read(path: String): String = Gdx.files.internal(path).readString()

    fun readTemplate(
        path: String,
        values: Map<String, String>,
    ): String =
        values.entries.fold(read(path)) { source, (key, value) ->
            source.replace("{{$key}}", value)
        }
}
