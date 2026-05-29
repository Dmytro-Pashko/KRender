package com.pashkd.krender.engine.runtimeui

import com.pashkd.krender.engine.api.Logger
import com.pashkd.krender.engine.api.System
import com.pashkd.krender.engine.viewport.RuntimeViewportService

/**
 * Minimal non-rendering runtime UI system.
 *
 * The system stores registered UI documents, maps active layers to document ids, and
 * resolves the active set against the latest viewport state on demand.
 */
class RuntimeUiSystem(
    private val viewport: RuntimeViewportService,
    private val logger: Logger,
    private val resolver: RuntimeUiLayoutResolver = RuntimeUiLayoutResolver(),
) : System() {
    private val documents = mutableMapOf<String, RuntimeUiDocument>()
    private val layers = linkedMapOf<String, String>()
    private val warnedMissingDocuments = mutableSetOf<String>()

    /** Registers or replaces a document by id. */
    fun register(document: RuntimeUiDocument) {
        documents[document.id] = document
        warnedMissingDocuments.remove(document.id)
    }

    /** Removes a document and clears any active layers referencing it. */
    fun unregister(documentId: String) {
        documents.remove(documentId)
        warnedMissingDocuments.remove(documentId)
        layers.entries.removeIf { it.value == documentId }
    }

    /** Activates a document in the named layer, or clears the layer when [documentId] is null. */
    fun setLayer(layer: String, documentId: String?) {
        if (documentId == null) {
            clearLayer(layer)
            return
        }
        layers[layer] = documentId
    }

    /** Removes any active document assignment for the named layer. */
    fun clearLayer(layer: String) {
        layers.remove(layer)
    }

    /**
     * Resolves all active layer documents in insertion order using [viewport.current].
     *
     * Missing documents are skipped safely and logged once until they are registered
     * again or removed from the active layer map.
     */
    fun resolveActive(): List<RuntimeUiResolvedNode> =
        layers.values.mapNotNull { documentId ->
            val document = documents[documentId]
            if (document == null) {
                if (warnedMissingDocuments.add(documentId)) {
                    logger.warn(TAG) { "Runtime UI document '$documentId' is missing for an active layer." }
                }
                null
            } else {
                warnedMissingDocuments.remove(documentId)
                resolver.resolve(document, viewport.current)
            }
        }

    companion object {
        /** Logger tag used by the runtime UI system. */
        private const val TAG = "RuntimeUiSystem"
    }
}
