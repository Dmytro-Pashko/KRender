package com.pashkd.krender.engine.assets

import com.pashkd.krender.engine.serialization.KRenderJson
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Lightweight Asset Browser metadata extracted from a LibGDX Scene2D Skin JSON descriptor.
 *
 * This reader is intentionally backend-neutral: it parses JSON and counts known Skin sections without
 * constructing a LibGDX `Skin`, loading textures, or requiring an OpenGL context during asset scans.
 */
data class Scene2DSkinAssetMetadata(
    val status: String,
    val parseError: String? = null,
    val colorCount: Int = 0,
    val drawableCount: Int = 0,
    val textureRegionCount: Int = 0,
    val labelStyleCount: Int = 0,
    val textButtonStyleCount: Int = 0,
    val progressBarStyleCount: Int = 0,
    val imageButtonStyleCount: Int = 0,
    val checkBoxStyleCount: Int = 0,
    val textFieldStyleCount: Int = 0,
    val scrollPaneStyleCount: Int = 0,
    val selectBoxStyleCount: Int = 0,
    val windowStyleCount: Int = 0,
    val rawStyleClassCount: Int = 0,
) {
    fun toMetadataMap(): Map<String, String> = buildMap {
        put("skinStatus", status)
        parseError?.let { put("skinParseError", it) }
        put("skinColorCount", colorCount.toString())
        put("skinDrawableCount", drawableCount.toString())
        put("skinTextureRegionCount", textureRegionCount.toString())
        put("skinLabelStyleCount", labelStyleCount.toString())
        put("skinTextButtonStyleCount", textButtonStyleCount.toString())
        put("skinProgressBarStyleCount", progressBarStyleCount.toString())
        put("skinImageButtonStyleCount", imageButtonStyleCount.toString())
        put("skinCheckBoxStyleCount", checkBoxStyleCount.toString())
        put("skinTextFieldStyleCount", textFieldStyleCount.toString())
        put("skinScrollPaneStyleCount", scrollPaneStyleCount.toString())
        put("skinSelectBoxStyleCount", selectBoxStyleCount.toString())
        put("skinWindowStyleCount", windowStyleCount.toString())
        put("skinStyleClassCount", rawStyleClassCount.toString())
        put(
            "skinPreview",
            "colors=$colorCount, drawables=$drawableCount, " +
                "labelStyles=$labelStyleCount, textButtonStyles=$textButtonStyleCount",
        )
    }
}

/**
 * Parses Scene2D Skin JSON descriptors for indexing metadata.
 */
object Scene2DSkinAssetMetadataReader {
    fun read(file: File): Scene2DSkinAssetMetadata =
        try {
            val root = KRenderJson.Pretty.parseToJsonElement(file.readText(StandardCharsets.UTF_8)) as? JsonObject
                ?: return Scene2DSkinAssetMetadata(
                    status = ParseErrorStatus,
                    parseError = "Scene2D Skin root must be a JSON object",
                )

            Scene2DSkinAssetMetadata(
                status = OkStatus,
                colorCount = root.countEntries(SkinClassAliases.color),
                drawableCount = root.countEntries(SkinClassAliases.drawable),
                textureRegionCount = root.countEntries(SkinClassAliases.textureRegion),
                labelStyleCount = root.countEntries(SkinClassAliases.labelStyle),
                textButtonStyleCount = root.countEntries(SkinClassAliases.textButtonStyle),
                progressBarStyleCount = root.countEntries(SkinClassAliases.progressBarStyle),
                imageButtonStyleCount = root.countEntries(SkinClassAliases.imageButtonStyle),
                checkBoxStyleCount = root.countEntries(SkinClassAliases.checkBoxStyle),
                textFieldStyleCount = root.countEntries(SkinClassAliases.textFieldStyle),
                scrollPaneStyleCount = root.countEntries(SkinClassAliases.scrollPaneStyle),
                selectBoxStyleCount = root.countEntries(SkinClassAliases.selectBoxStyle),
                windowStyleCount = root.countEntries(SkinClassAliases.windowStyle),
                rawStyleClassCount = root.values.count { value -> value is JsonObject },
            )
        } catch (error: Exception) {
            Scene2DSkinAssetMetadata(
                status = ParseErrorStatus,
                parseError = error.message ?: error.javaClass.simpleName,
            )
        }

    private fun JsonObject.countEntries(keys: Set<String>): Int =
        entries.sumOf { (key, value) ->
            if (key in keys && value is JsonObject) value.size else 0
        }

    private const val OkStatus = "ok"
    private const val ParseErrorStatus = "parse_error"
}

private object SkinClassAliases {
    val color = setOf(
        "Color",
        "com.badlogic.gdx.graphics.Color",
    )
    val drawable = setOf(
        "Drawable",
        "TintedDrawable",
        "com.badlogic.gdx.scenes.scene2d.utils.Drawable",
        "com.badlogic.gdx.scenes.scene2d.ui.Skin\$TintedDrawable",
    )
    val textureRegion = setOf(
        "TextureRegion",
        "com.badlogic.gdx.graphics.g2d.TextureRegion",
    )
    val labelStyle = setOf(
        "LabelStyle",
        "com.badlogic.gdx.scenes.scene2d.ui.Label\$LabelStyle",
    )
    val textButtonStyle = setOf(
        "TextButtonStyle",
        "com.badlogic.gdx.scenes.scene2d.ui.TextButton\$TextButtonStyle",
    )
    val progressBarStyle = setOf(
        "ProgressBarStyle",
        "com.badlogic.gdx.scenes.scene2d.ui.ProgressBar\$ProgressBarStyle",
    )
    val imageButtonStyle = setOf(
        "ImageButtonStyle",
        "com.badlogic.gdx.scenes.scene2d.ui.ImageButton\$ImageButtonStyle",
    )
    val checkBoxStyle = setOf(
        "CheckBoxStyle",
        "com.badlogic.gdx.scenes.scene2d.ui.CheckBox\$CheckBoxStyle",
    )
    val textFieldStyle = setOf(
        "TextFieldStyle",
        "com.badlogic.gdx.scenes.scene2d.ui.TextField\$TextFieldStyle",
    )
    val scrollPaneStyle = setOf(
        "ScrollPaneStyle",
        "com.badlogic.gdx.scenes.scene2d.ui.ScrollPane\$ScrollPaneStyle",
    )
    val selectBoxStyle = setOf(
        "SelectBoxStyle",
        "com.badlogic.gdx.scenes.scene2d.ui.SelectBox\$SelectBoxStyle",
    )
    val windowStyle = setOf(
        "WindowStyle",
        "com.badlogic.gdx.scenes.scene2d.ui.Window\$WindowStyle",
    )
}
