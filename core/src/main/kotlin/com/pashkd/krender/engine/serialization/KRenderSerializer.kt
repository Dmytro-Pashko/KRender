package com.pashkd.krender.engine.serialization

/**
 * Shared contract for KRender document serializers.
 *
 * It exists to keep runtime/editor asset formats consistent without forcing
 * domain serializers such as `.krscene`, `.krskybox`, or `.krui` into one package.
 * Implementations still live near their document models and may expose additional
 * domain-specific helpers.
 */
interface KRenderSerializer<T> {
    /** Decodes a KRender JSON document into its domain model. */
    fun decode(json: String): T

    /** Encodes a KRender domain model into human-readable JSON. */
    fun encode(value: T): String
}
