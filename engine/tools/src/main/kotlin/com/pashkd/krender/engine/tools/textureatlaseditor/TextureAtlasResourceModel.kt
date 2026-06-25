package com.pashkd.krender.engine.tools.textureatlaseditor

enum class TextureAtlasResourceType {
    Image,
    NinePatch,
    Font,
    Color,
}

sealed interface TextureAtlasResource {
    val id: String
    val name: String
    val type: TextureAtlasResourceType
}

data class ImageAtlasResource(
    override val id: String,
    override val name: String,
    val sourcePath: String,
    val sourceX: Int = 0,
    val sourceY: Int = 0,
    val sourceWidth: Int? = null,
    val sourceHeight: Int? = null,
    val atlasRegionId: AtlasRegionId? = null,
    val atlasIndex: Int? = null,
) : TextureAtlasResource {
    override val type: TextureAtlasResourceType = TextureAtlasResourceType.Image
}

data class NinePatchAtlasResource(
    override val id: String,
    override val name: String,
    val sourcePath: String,
    val sourceX: Int = 0,
    val sourceY: Int = 0,
    val sourceWidth: Int? = null,
    val sourceHeight: Int? = null,
    val split: List<Int> = emptyList(),
    val pad: List<Int> = emptyList(),
    val atlasRegionId: AtlasRegionId? = null,
    val atlasIndex: Int? = null,
) : TextureAtlasResource {
    override val type: TextureAtlasResourceType = TextureAtlasResourceType.NinePatch
}

data class ColorAtlasResource(
    override val id: String,
    override val name: String,
    val rgba: Int,
    val width: Int = 1,
    val height: Int = 1,
) : TextureAtlasResource {
    override val type: TextureAtlasResourceType = TextureAtlasResourceType.Color
}

data class FontAtlasResource(
    override val id: String,
    override val name: String,
    val sourcePath: String,
    val documentPath: String = sourcePath,
    val pageTexturePaths: List<String> = emptyList(),
    val packInAtlas: Boolean = false,
    val atlasTexturePath: String? = null,
    val atlasRegionId: AtlasRegionId? = null,
    val sourceX: Int = 0,
    val sourceY: Int = 0,
    val sourceWidth: Int? = null,
    val sourceHeight: Int? = null,
    val glyphCount: Int = 0,
    val kerningCount: Int = 0,
) : TextureAtlasResource {
    override val type: TextureAtlasResourceType = TextureAtlasResourceType.Font
}

data class TextureAtlasResourceState(
    var items: List<TextureAtlasResource> = emptyList(),
    var selectedResourceId: String? = null,
)
