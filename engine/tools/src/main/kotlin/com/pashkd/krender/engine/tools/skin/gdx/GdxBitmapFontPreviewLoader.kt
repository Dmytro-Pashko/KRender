package com.pashkd.krender.engine.tools.skin.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Array
import java.io.File

/** Loads text BMFont files with page textures resolved relative to the font or project root. */
internal object GdxBitmapFontPreviewLoader {
    fun load(
        fontFile: File,
        projectRoot: File?,
    ): BitmapFont {
        val fontHandle = Gdx.files.absolute(fontFile.absolutePath.replace('\\', '/'))
        val data = BitmapFont.BitmapFontData(fontHandle, false)
        val pageNames =
            parsePageNames(fontFile)
                .takeIf(List<String>::isNotEmpty)
                ?: data.imagePaths
                    ?.map { imagePath -> File(imagePath).name }
                    ?.takeIf(List<String>::isNotEmpty)
                ?: emptyList()
        require(pageNames.isNotEmpty()) { "No page images declared in ${fontFile.name}." }

        val textures = mutableListOf<Texture>()
        return try {
            val regions = Array<TextureRegion>()
            pageNames.forEach { pageName ->
                val pageFile =
                    resolvePageFile(fontFile, projectRoot, pageName)
                        ?: error("Could not resolve BMFont page '$pageName' for ${fontFile.name}.")
                val texture = Texture(Gdx.files.absolute(pageFile.absolutePath.replace('\\', '/')))
                textures += texture
                regions.add(TextureRegion(texture))
            }
            BitmapFont(data, regions, true)
        } catch (error: Exception) {
            textures.forEach(Texture::dispose)
            throw error
        }
    }

    private fun parsePageNames(fontFile: File): List<String> =
        fontFile.readLines()
            .mapNotNull { line -> FontPageRegex.find(line)?.groupValues?.getOrNull(1) }

    private fun resolvePageFile(
        fontFile: File,
        projectRoot: File?,
        pageName: String,
    ): File? {
        val normalizedPage = pageName.replace('\\', '/')
        val pageFile = File(normalizedPage)
        val candidates =
            buildList {
                if (pageFile.isAbsolute) add(pageFile)
                fontFile.parentFile?.let { parent -> add(File(parent, normalizedPage)) }
                projectRoot?.let { root -> add(File(root, normalizedPage)) }
                projectRoot?.let { root -> add(File(root, pageFile.name)) }
            }
        return candidates.firstOrNull(File::isFile)
    }

    private val FontPageRegex = Regex("""^\s*page\s+.*file="([^"]+)"""")
}
