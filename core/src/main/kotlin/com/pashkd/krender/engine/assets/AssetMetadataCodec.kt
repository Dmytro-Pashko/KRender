package com.pashkd.krender.engine.assets

/**
 * Versioned, on-disk shape of a `.krmeta` sidecar.
 *
 * The codec is JSON-based but uses a small structured parser (not regex)
 * so that the format can grow without breaking backward compatibility.
 */
data class AssetMetadataDocument(
    val schemaVersion: Int = CurrentSchemaVersion,
    val id: String,
    val type: String,
    val category: String,
    val displayName: String,
    val tags: List<String> = emptyList(),
    val importerId: String? = null,
    val importSettings: Map<String, Any?> = emptyMap(),
) {
    companion object {
        const val CurrentSchemaVersion = 1
    }
}

/**
 * Structured (non-regex) JSON codec for `.krmeta` files.
 *
 * Backward compatible with legacy v0/v1 files that omit `schemaVersion`
 * and/or `importerId`.
 */
object AssetMetadataCodec {
    /**
     * Encodes [document] into a stable, pretty-printed JSON form.
     */
    fun encode(document: AssetMetadataDocument): String =
        buildString {
            appendLine("{")
            appendLine("  \"schemaVersion\": ${document.schemaVersion},")
            appendLine("  \"id\": ${jsonString(document.id)},")
            appendLine("  \"type\": ${jsonString(document.type)},")
            appendLine("  \"category\": ${jsonString(document.category)},")
            appendLine("  \"displayName\": ${jsonString(document.displayName)},")
            appendLine("  \"tags\": ${encodeStringArray(document.tags)},")
            appendLine("  \"importerId\": ${if (document.importerId == null) "null" else jsonString(document.importerId)},")
            appendLine("  \"importSettings\": ${encodeValue(document.importSettings, indent = "  ")}")
            appendLine("}")
        }

    /**
     * Decodes a `.krmeta` JSON payload. Throws [AssetMetadataDecodeException] on invalid syntax
     * or missing required keys.
     */
    fun decode(text: String): AssetMetadataDocument {
        val parsed = JsonParser(text).parseValue()

        @Suppress("UNCHECKED_CAST")
        val root =
            parsed as? Map<String, Any?>
                ?: throw AssetMetadataDecodeException("root must be a JSON object")

        val id =
            root["id"] as? String
                ?: throw AssetMetadataDecodeException("missing required field 'id'")
        val type =
            root["type"] as? String
                ?: throw AssetMetadataDecodeException("missing required field 'type'")
        val category =
            root["category"] as? String
                ?: throw AssetMetadataDecodeException("missing required field 'category'")
        val displayName = root["displayName"] as? String ?: ""
        val tags = (root["tags"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val importerId = root["importerId"] as? String
        val schemaVersion = (root["schemaVersion"] as? Number)?.toInt() ?: 1

        @Suppress("UNCHECKED_CAST")
        val importSettings = (root["importSettings"] as? Map<String, Any?>) ?: emptyMap()

        return AssetMetadataDocument(
            schemaVersion = schemaVersion,
            id = id,
            type = type,
            category = category,
            displayName = displayName,
            tags = tags,
            importerId = importerId,
            importSettings = importSettings,
        )
    }

    private fun encodeStringArray(values: List<String>): String =
        values.joinToString(prefix = "[", postfix = "]") { jsonString(it) }

    private fun encodeValue(
        value: Any?,
        indent: String,
    ): String =
        when (value) {
            null -> "null"
            is Boolean -> value.toString()
            is Number -> value.toString()
            is String -> jsonString(value)
            is List<*> -> value.joinToString(prefix = "[", postfix = "]") { encodeValue(it, indent) }
            is Map<*, *> -> {
                if (value.isEmpty()) {
                    "{}"
                } else {
                    val nested = "$indent  "
                    value.entries.joinToString(
                        prefix = "{\n",
                        postfix = "\n$indent}",
                        separator = ",\n",
                    ) { (k, v) ->
                        "$nested${jsonString(k.toString())}: ${encodeValue(v, nested)}"
                    }
                }
            }

            else -> jsonString(value.toString())
        }

    private fun jsonString(value: String): String =
        buildString(value.length + 2) {
            append('"')
            for (ch in value) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    else ->
                        if (ch.code < 0x20) {
                            append("\\u%04x".format(ch.code))
                        } else {
                            append(ch)
                        }
                }
            }
            append('"')
        }
}

/**
 * Raised when a `.krmeta` payload cannot be parsed structurally.
 */
class AssetMetadataDecodeException(
    message: String,
) : RuntimeException(message)

/**
 * Minimal recursive-descent JSON parser.
 *
 * Supports objects, arrays, strings, numbers, booleans and null.
 * Comments are not supported.
 */
private class JsonParser(
    private val text: String,
) {
    private var index = 0

    fun parseValue(): Any? {
        skipWhitespace()
        if (index >= text.length) throw AssetMetadataDecodeException("unexpected end of input")
        return when (val ch = text[index]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't', 'f' -> parseBoolean()
            'n' -> parseNull()
            else -> if (ch == '-' || ch.isDigit()) parseNumber() else fail("unexpected character '$ch'")
        }
    }

    private fun parseObject(): Map<String, Any?> {
        expect('{')
        val map = LinkedHashMap<String, Any?>()
        skipWhitespace()
        if (peek() == '}') {
            index += 1
            return map
        }
        while (true) {
            skipWhitespace()
            val key = parseString()
            skipWhitespace()
            expect(':')
            val value = parseValue()
            map[key] = value
            skipWhitespace()
            when (peek()) {
                ',' -> {
                    index += 1
                    continue
                }

                '}' -> {
                    index += 1
                    return map
                }

                else -> fail("expected ',' or '}' in object")
            }
        }
    }

    private fun parseArray(): List<Any?> {
        expect('[')
        val list = ArrayList<Any?>()
        skipWhitespace()
        if (peek() == ']') {
            index += 1
            return list
        }
        while (true) {
            list += parseValue()
            skipWhitespace()
            when (peek()) {
                ',' -> {
                    index += 1
                    continue
                }

                ']' -> {
                    index += 1
                    return list
                }

                else -> fail("expected ',' or ']' in array")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val sb = StringBuilder()
        while (index < text.length) {
            val ch = text[index++]
            if (ch == '"') return sb.toString()
            if (ch == '\\') {
                if (index >= text.length) fail("unterminated escape")
                when (val esc = text[index++]) {
                    '"', '\\', '/' -> sb.append(esc)
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'u' -> {
                        if (index + 4 > text.length) fail("bad unicode escape")
                        val hex = text.substring(index, index + 4)
                        index += 4
                        sb.append(hex.toInt(16).toChar())
                    }

                    else -> fail("bad escape '\\$esc'")
                }
            } else {
                sb.append(ch)
            }
        }
        fail("unterminated string")
    }

    private fun parseNumber(): Number {
        val start = index
        if (peek() == '-') index += 1
        while (index < text.length && text[index].isDigit()) index += 1
        var isDouble = false
        if (index < text.length && text[index] == '.') {
            isDouble = true
            index += 1
            while (index < text.length && text[index].isDigit()) index += 1
        }
        if (index < text.length && (text[index] == 'e' || text[index] == 'E')) {
            isDouble = true
            index += 1
            if (index < text.length && (text[index] == '+' || text[index] == '-')) index += 1
            while (index < text.length && text[index].isDigit()) index += 1
        }
        val raw = text.substring(start, index)
        return if (isDouble) raw.toDouble() else raw.toLongOrNull() ?: raw.toDouble()
    }

    private fun parseBoolean(): Boolean {
        if (text.startsWith("true", index)) {
            index += 4
            return true
        }
        if (text.startsWith("false", index)) {
            index += 5
            return false
        }
        fail("expected boolean")
    }

    private fun parseNull(): Any? {
        if (text.startsWith("null", index)) {
            index += 4
            return null
        }
        fail("expected null")
    }

    private fun expect(ch: Char) {
        skipWhitespace()
        if (index >= text.length || text[index] != ch) fail("expected '$ch'")
        index += 1
    }

    private fun peek(): Char = if (index < text.length) text[index] else '\u0000'

    private fun skipWhitespace() {
        while (index < text.length && text[index].isWhitespace()) index += 1
    }

    private fun fail(message: String): Nothing = throw AssetMetadataDecodeException("$message at position $index")
}
