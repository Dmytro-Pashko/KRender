package com.pashkd.krender.engine.ui.runtime

/**
 * Thrown when a runtime `.krui` payload does not satisfy the binding contract
 * required by the UI scene document.
 *
 * Runtime payloads must provide actual values explicitly. `.krui`
 * `bindings.defaultValue` entries are editor preview defaults and are not used
 * as runtime fallback values.
 */
class RuntimeUiBindingException(
    message: String,
) : IllegalStateException(message)
