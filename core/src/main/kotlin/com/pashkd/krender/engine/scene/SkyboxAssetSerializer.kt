package com.pashkd.krender.engine.scene

import com.badlogic.gdx.utils.JsonReader

data class SkyboxAssetDescriptor(
    val schemaVersion: Int = CurrentSchemaVersion,
    val id: String,
    val name: String,
    val modelPath: String? = null,
    val texturePath: String,
    val intensity: Float = 1f,
) {
    companion object {
        const val CurrentSchemaVersion = 1
    }
}

object SkyboxAssetSerializer {
    fun encode(descriptor: SkyboxAssetDescriptor): String =
        buildString {
            appendLine("{")
            appendLine("  \"schemaVersion\": ${descriptor.schemaVersion},")
            appendLine("  \"id\": ${jsonString(descriptor.id)},")
            appendLine("  \"name\": ${jsonString(descriptor.name)},")
            descriptor.modelPath?.let { modelPath ->
                appendLine("  \"modelPath\": ${jsonString(modelPath)},")
            }
            appendLine("  \"texturePath\": ${jsonString(descriptor.texturePath)},")
            appendLine("  \"intensity\": ${descriptor.intensity}")
            appendLine("}")
        }

    fun decode(jsonText: String): SkyboxAssetDescriptor {
        val root = JsonReader().parse(jsonText)
        require(root.isObject) { "Skybox asset descriptor root must be a JSON object" }
        val texturePath = root.getString("texturePath", "").trim().replace('\\', '/')
        require(texturePath.isNotBlank()) { "Skybox asset texturePath must not be blank" }
        return SkyboxAssetDescriptor(
            schemaVersion = root.getInt("schemaVersion", SkyboxAssetDescriptor.CurrentSchemaVersion),
            id = root.getString("id"),
            name = root.getString("name", "Skybox"),
            modelPath = root.getString("modelPath", null)?.trim()?.replace('\\', '/')?.takeIf(String::isNotBlank),
            texturePath = texturePath,
            intensity = root.getFloat("intensity", 1f).coerceAtLeast(0f),
        )
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
                    else -> if (ch.code < 0x20) {
                        append("\\u%04x".format(ch.code))
                    } else {
                        append(ch)
                    }
                }
            }
            append('"')
        }
}
