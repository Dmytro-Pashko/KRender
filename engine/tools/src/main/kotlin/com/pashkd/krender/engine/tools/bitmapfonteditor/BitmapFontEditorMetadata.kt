package com.pashkd.krender.engine.tools.bitmapfonteditor

import com.pashkd.krender.engine.serialization.KRenderJson
import kotlinx.serialization.json.*
import java.io.File

data class BitmapFontEditorMetadata(
    val format: String = FORMAT,
    val version: Int = 1,
    val sourceFont: String = "",
    val outputFnt: String = "",
    val outputPages: List<String> = emptyList(),
    val generation: BitmapFontGenerationMetadata = BitmapFontGenerationMetadata(),
) {
    companion object {
        const val FORMAT = "krender.bitmapFont"
        const val EXTENSION = "kfont.json"
    }
}

data class BitmapFontGenerationMetadata(
    val sizePx: Int = 24,
    val charsetPreset: String = "ENGLISH_SYMBOLS_UKRAINIAN_CYRILLIC",
    val customCharacters: String = "",
    val padding: Int = 2,
    val spacing: Int = 1,
    val pageWidth: Int = 512,
    val pageHeight: Int = 512,
    val antialias: Boolean = true,
    val hinting: Boolean = true,
)

object BitmapFontEditorMetadataCodec {
    fun encode(metadata: BitmapFontEditorMetadata): String {
        val json = buildJsonObject {
            put("format", metadata.format)
            put("version", metadata.version)
            put("sourceFont", metadata.sourceFont)
            put("outputFnt", metadata.outputFnt)
            put("outputPages", buildJsonArray {
                metadata.outputPages.forEach { add(it) }
            })
            put("generation", buildJsonObject {
                put("sizePx", metadata.generation.sizePx)
                put("charsetPreset", metadata.generation.charsetPreset)
                put("customCharacters", metadata.generation.customCharacters)
                put("padding", metadata.generation.padding)
                put("spacing", metadata.generation.spacing)
                put("pageWidth", metadata.generation.pageWidth)
                put("pageHeight", metadata.generation.pageHeight)
                put("antialias", metadata.generation.antialias)
                put("hinting", metadata.generation.hinting)
            })
        }
        return KRenderJson.Pretty.encodeToString(JsonObject.serializer(), json)
    }

    fun decode(text: String): BitmapFontEditorMetadata {
        val root = KRenderJson.Pretty.parseToJsonElement(text).jsonObject
        val format = root["format"]?.jsonPrimitive?.content ?: BitmapFontEditorMetadata.FORMAT
        val version = root["version"]?.jsonPrimitive?.intOrNull ?: 1
        val sourceFont = root["sourceFont"]?.jsonPrimitive?.content ?: ""
        val outputFnt = root["outputFnt"]?.jsonPrimitive?.content ?: ""
        val outputPages = root["outputPages"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        val gen = root["generation"]?.jsonObject
        val generation = if (gen != null) {
            BitmapFontGenerationMetadata(
                sizePx = gen["sizePx"]?.jsonPrimitive?.intOrNull ?: 24,
                charsetPreset = gen["charsetPreset"]?.jsonPrimitive?.content ?: "ENGLISH_SYMBOLS_UKRAINIAN_CYRILLIC",
                customCharacters = gen["customCharacters"]?.jsonPrimitive?.content ?: "",
                padding = gen["padding"]?.jsonPrimitive?.intOrNull ?: 2,
                spacing = gen["spacing"]?.jsonPrimitive?.intOrNull ?: 1,
                pageWidth = gen["pageWidth"]?.jsonPrimitive?.intOrNull ?: 512,
                pageHeight = gen["pageHeight"]?.jsonPrimitive?.intOrNull ?: 512,
                antialias = gen["antialias"]?.jsonPrimitive?.booleanOrNull ?: true,
                hinting = gen["hinting"]?.jsonPrimitive?.booleanOrNull ?: true,
            )
        } else {
            BitmapFontGenerationMetadata()
        }
        return BitmapFontEditorMetadata(
            format = format,
            version = version,
            sourceFont = sourceFont,
            outputFnt = outputFnt,
            outputPages = outputPages,
            generation = generation,
        )
    }

    fun save(
        file: File,
        metadata: BitmapFontEditorMetadata,
    ) {
        file.parentFile?.mkdirs()
        file.writeText(encode(metadata) + "\n", Charsets.UTF_8)
    }

    fun load(file: File): BitmapFontEditorMetadata? =
        runCatching {
            decode(file.readText(Charsets.UTF_8))
        }.getOrNull()
}
