package com.pashkd.krender.engine.assets.environment

/**
 * Service abstraction for loading, saving, and validating Environment assets.
 *
 * Editor panels and tool code should use this interface rather than calling
 * [EnvironmentLoader] or [EnvironmentValidator] directly. This allows
 * future replacement with async or backend-delegating implementations.
 */
interface EnvironmentService {
    fun load(manifestPath: String): Environment

    fun save(environment: Environment)

    fun validate(environment: Environment): EnvironmentValidationReport
}
