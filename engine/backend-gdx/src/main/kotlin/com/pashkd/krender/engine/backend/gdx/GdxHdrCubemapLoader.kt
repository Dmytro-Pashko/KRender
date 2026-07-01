package com.pashkd.krender.engine.backend.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Cubemap
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.glutils.PixmapTextureData

internal object GdxHdrCubemapLoader {
    fun loadFaces(facePaths: Map<String, String>): Cubemap {
        val pixmaps = orderedFaceNames.map { face -> loadPixmap(facePaths, face) }
        return createBaseCubemap(pixmaps).also { cubemap ->
            cubemap.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            cubemap.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
        }
    }

    fun loadMipFaces(mipFacePaths: Map<Int, Map<String, String>>): Cubemap {
        require(mipFacePaths.isNotEmpty()) { "Radiance cubemap must contain at least one mip level." }
        val mipZero = mipFacePaths[0] ?: error("Radiance cubemap is missing mip 0.")
        val basePixmaps = orderedFaceNames.map { face -> loadPixmap(mipZero, face) }
        val cubemap = createBaseCubemap(basePixmaps)
        try {
            cubemap.bind()
            mipFacePaths.toSortedMap().forEach { (mip, facePaths) ->
                if (mip > 0) {
                    orderedFaceNames.forEachIndexed { faceIndex, face ->
                        val pixmap = loadPixmap(facePaths, face)
                        try {
                            Gdx.gl.glTexImage2D(
                                GL20.GL_TEXTURE_CUBE_MAP_POSITIVE_X + faceIndex,
                                mip,
                                pixmap.glInternalFormat,
                                pixmap.width,
                                pixmap.height,
                                0,
                                pixmap.glFormat,
                                pixmap.glType,
                                pixmap.pixels,
                            )
                        } finally {
                            pixmap.dispose()
                        }
                    }
                }
            }
            cubemap.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
            cubemap.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
            return cubemap
        } catch (error: Throwable) {
            cubemap.dispose()
            throw error
        } finally {
            Gdx.gl.glBindTexture(GL20.GL_TEXTURE_CUBE_MAP, 0)
        }
    }

    private fun createBaseCubemap(pixmaps: List<Pixmap>): Cubemap =
        try {
            Cubemap(
                pixmaps[0].textureData(),
                pixmaps[1].textureData(),
                pixmaps[2].textureData(),
                pixmaps[3].textureData(),
                pixmaps[4].textureData(),
                pixmaps[5].textureData(),
            )
        } catch (error: Throwable) {
            pixmaps.forEach { pixmap ->
                if (!pixmap.isDisposed) pixmap.dispose()
            }
            throw error
        }

    private fun loadPixmap(
        facePaths: Map<String, String>,
        face: String,
    ): Pixmap {
        val path = facePaths[face] ?: error("Cubemap is missing face '$face'.")
        val file = Gdx.files.internal(path)
        require(file.exists()) { "Cubemap face is missing: '$path'." }
        return Pixmap(file)
    }

    private fun Pixmap.textureData(): PixmapTextureData = PixmapTextureData(this, format, false, true)

    private val orderedFaceNames = listOf("posx", "negx", "posy", "negy", "posz", "negz")
}
