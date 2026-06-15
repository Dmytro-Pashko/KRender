package com.pashkd.krender.test

import com.pashkd.krender.engine.api.LogLevel
import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.ui.runtime.RuntimeUiActionHandler
import com.pashkd.krender.engine.ui.runtime.RuntimeUiBackend
import com.pashkd.krender.engine.ui.runtime.RuntimeUiLayerState
import com.pashkd.krender.engine.ui.runtime.RuntimeUiService

object NoOpTestLogger : Logger {
    override fun log(
        level: LogLevel,
        tag: String,
        error: Throwable?,
        message: () -> String,
    ) = Unit
}

class NoOpRuntimeUiBackend : RuntimeUiBackend {
    override fun setActionHandler(handler: RuntimeUiActionHandler?) = Unit

    override fun syncLayers(layers: List<RuntimeUiLayerState>) = Unit

    override fun update(dt: Float) = Unit

    override fun render() = Unit

    override fun resize(
        width: Int,
        height: Int,
    ) = Unit

    override fun clear() = Unit

    override fun dispose() = Unit
}

fun newTestRuntimeUiService(logger: Logger = NoOpTestLogger): RuntimeUiService =
    RuntimeUiService(NoOpRuntimeUiBackend(), logger)
