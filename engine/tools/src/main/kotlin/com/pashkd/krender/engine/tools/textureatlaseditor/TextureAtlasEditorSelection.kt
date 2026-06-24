package com.pashkd.krender.engine.tools.textureatlaseditor

@JvmInline
value class TextureAssetId(
    val value: String,
)

data class AtlasRegionId(
    val atlasPath: String,
    val pageName: String,
    val regionName: String,
)

