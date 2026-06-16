package com.pashkd.krender.woolboy.app

import com.pashkd.krender.engine.api.LogLevel
import com.pashkd.krender.engine.api.Logger

/**
 * Small standalone logger used by the Woolboy desktop app runtime UI bootstrap
 * before the engine logger is available through dependency injection.
 */
internal class AppRuntimeLogger : Logger {
    override fun log(
        level: LogLevel,
        tag: String,
        error: Throwable?,
        message: () -> String,
    ) {
        val rendered = "[${level.name}] [$tag] ${message()}"
        when (level) {
            LogLevel.Error -> System.err.println(rendered)
            else -> println(rendered)
        }
        error?.printStackTrace(if (level == LogLevel.Error) System.err else System.out)
    }
}
