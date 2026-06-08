package com.pashkd.krender.engine.serialization

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Reads a required string from a KRender JSON object.
 *
 * [documentName] is included in error messages so each serializer keeps
 * domain-specific diagnostics without duplicating field parsing code.
 */
internal fun JsonObject.requiredString(
    name: String,
    documentName: String,
): String =
    stringOrNull(name) ?: throw IllegalArgumentException("$documentName is missing required field '$name'")

/**
 * Reads a required long field from a KRender JSON object.
 */
internal fun JsonObject.requiredLong(
    name: String,
    documentName: String,
): Long =
    longOrNull(name) ?: throw IllegalArgumentException("$documentName field '$name' must be a number")

/**
 * Reads an optional string while treating JSON null-like text as absent.
 */
internal fun JsonObject.stringOrNull(name: String): String? {
    val primitive = this[name] as? JsonPrimitive ?: return null
    return primitive.content.takeUnless { it.equals("null", ignoreCase = true) }
}

/**
 * Reads a string field or returns [defaultValue] when absent.
 */
internal fun JsonObject.stringOrDefault(
    name: String,
    defaultValue: String,
): String =
    stringOrNull(name) ?: defaultValue

/**
 * Reads an optional long field.
 */
internal fun JsonObject.longOrNull(name: String): Long? =
    (this[name] as? JsonPrimitive)?.longOrNull

/**
 * Reads an int field or returns [defaultValue] when absent or invalid.
 */
internal fun JsonObject.intOrDefault(
    name: String,
    defaultValue: Int,
): Int =
    (this[name] as? JsonPrimitive)?.content?.toIntOrNull() ?: defaultValue

/**
 * Reads an optional float field.
 */
internal fun JsonObject.floatOrNull(name: String): Float? =
    (this[name] as? JsonPrimitive)?.floatOrNull

/**
 * Reads a float field or returns [defaultValue] when absent or invalid.
 */
internal fun JsonObject.floatOrDefault(
    name: String,
    defaultValue: Float,
): Float =
    floatOrNull(name) ?: defaultValue

/**
 * Reads a boolean field or returns [defaultValue] when absent or invalid.
 */
internal fun JsonObject.booleanOrDefault(
    name: String,
    defaultValue: Boolean,
): Boolean =
    (this[name] as? JsonPrimitive)?.booleanOrNull ?: defaultValue

/**
 * Reads a required enum by stable enum name.
 */
internal fun <T : Enum<T>> JsonObject.requiredEnum(
    name: String,
    documentName: String,
    parser: (String) -> T,
): T {
    val raw = requiredString(name, documentName)
    return runCatching { parser(raw) }
        .getOrElse { throw IllegalArgumentException("$documentName field '$name' has unsupported enum value '$raw'") }
}

/**
 * Reads an optional enum by stable enum name.
 */
internal fun <T : Enum<T>> JsonObject.enumOrNull(
    name: String,
    documentName: String,
    parser: (String) -> T,
): T? {
    val raw = stringOrNull(name) ?: return null
    return runCatching { parser(raw) }
        .getOrElse { throw IllegalArgumentException("$documentName field '$name' has unsupported enum value '$raw'") }
}

/**
 * Reads an enum by stable enum name or returns [defaultValue].
 */
internal fun <T : Enum<T>> JsonObject.enumOrDefault(
    name: String,
    documentName: String,
    defaultValue: T,
    parser: (String) -> T,
): T =
    enumOrNull(name, documentName, parser) ?: defaultValue

/**
 * Writes a string field only when it is present.
 */
internal fun JsonObjectBuilder.putIfNotNull(
    name: String,
    value: String?,
) {
    if (value != null) put(name, JsonPrimitive(value))
}

/**
 * Writes a float field only when it is present.
 */
internal fun JsonObjectBuilder.putIfNotNull(
    name: String,
    value: Float?,
) {
    if (value != null) put(name, JsonPrimitive(value))
}

/**
 * Writes a boolean field only when it differs from [defaultValue].
 */
internal fun JsonObjectBuilder.putIfNonDefault(
    name: String,
    value: Boolean,
    defaultValue: Boolean,
) {
    if (value != defaultValue) put(name, JsonPrimitive(value))
}

/**
 * Writes a string field only when it differs from [defaultValue].
 */
internal fun JsonObjectBuilder.putIfNonDefault(
    name: String,
    value: String,
    defaultValue: String,
) {
    if (value != defaultValue) put(name, JsonPrimitive(value))
}

/**
 * Writes a float field only when it differs from [defaultValue].
 */
internal fun JsonObjectBuilder.putIfNonDefault(
    name: String,
    value: Float,
    defaultValue: Float,
) {
    if (value != defaultValue) put(name, JsonPrimitive(value))
}

/**
 * Normalizes project-relative asset paths stored in KRender JSON documents.
 */
internal fun normalizedProjectPath(raw: String): String =
    raw.trim().replace('\\', '/')

/**
 * Normalizes an optional project-relative path and treats blanks/null-like values as absent.
 */
internal fun normalizedOptionalProjectPath(raw: String?): String? =
    raw
        ?.let(::normalizedProjectPath)
        ?.takeIf(String::isNotBlank)
        ?.takeUnless { value -> value.equals("null", ignoreCase = true) }
