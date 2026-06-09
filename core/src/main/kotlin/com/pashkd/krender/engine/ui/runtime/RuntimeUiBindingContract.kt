package com.pashkd.krender.engine.ui.runtime

import com.pashkd.krender.engine.ui.scene.UiSceneDocument
import com.pashkd.krender.engine.ui.scene.validation.collectBindingReferences

/**
 * Verifies that a runtime payload contains all binding keys referenced by a
 * `.krui` document.
 *
 * This intentionally does not read or merge `document.bindings.defaultValue`.
 * Default values are editor preview data only.
 */
object RuntimeUiBindingContract {
    fun requirePayload(
        document: UiSceneDocument,
        payload: Map<String, String>,
    ) {
        val missing = collectBindingReferences(document)
            .filter { reference -> reference.key !in payload }
            .groupBy { reference -> reference.key }

        if (missing.isEmpty()) return

        val details = missing.entries
            .sortedBy { (key, _) -> key }
            .joinToString(separator = "; ") { (key, references) ->
                val locations = references
                    .joinToString(separator = ", ") { reference ->
                        "${reference.nodeId}.${reference.fieldName}"
                    }
                "$key used by $locations"
            }

        throw RuntimeUiBindingException(
            "Runtime UI payload is missing required binding values for scene '${document.id}': $details",
        )
    }
}
