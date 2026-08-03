package com.pashkd.krender.engine.backend.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Cubemap
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.glutils.PixmapTextureData
import com.pashkd.krender.engine.assets.environment.radianceMipResolution
import com.pashkd.krender.engine.assets.environment.requiredRadianceMipCount
import java.util.SortedMap
import kotlin.math.pow

internal object GdxHdrCubemapLoader {
    fun loadFaces(
        facePaths: Map<String, String>,
        intensity: Float = 1f,
    ): Cubemap {
        val pixmaps = orderedFaceNames.map { face -> loadPixmap(facePaths, face, intensity) }
        return createBaseCubemap(pixmaps).also { cubemap ->
            cubemap.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            cubemap.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
        }
    }

    fun loadMipFaces(
        mipFacePaths: Map<Int, Map<String, String>>,
        intensity: Float = 1f,
    ): Cubemap {
        require(mipFacePaths.isNotEmpty()) { "Radiance cubemap must contain at least one mip level." }
        val mipPixmaps = loadMipPixmaps(mipFacePaths, intensity)
        var cubemap: Cubemap? = null
        try {
            validateMipPixmaps(mipPixmaps)
            val loadedCubemap = createBaseCubemap(mipPixmaps.getValue(0))
            cubemap = loadedCubemap
            loadedCubemap.bind()
            mipPixmaps.forEach { (mip, pixmaps) ->
                if (mip > 0) {
                    pixmaps.forEachIndexed { faceIndex, pixmap ->
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
            loadedCubemap.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
            loadedCubemap.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
            return loadedCubemap
        } catch (error: Throwable) {
            cubemap?.dispose()
            throw error
        } finally {
            mipPixmaps.values.flatten().forEach { pixmap ->
                if (!pixmap.isDisposed) pixmap.dispose()
            }
            Gdx.gl.glBindTexture(GL20.GL_TEXTURE_CUBE_MAP, 0)
        }
    }

    private fun loadMipPixmaps(
        mipFacePaths: Map<Int, Map<String, String>>,
        intensity: Float,
    ): SortedMap<Int, List<Pixmap>> =
        sortedMapOf<Int, List<Pixmap>>().also { mipPixmaps ->
            try {
                mipFacePaths.toSortedMap().forEach { (mip, facePaths) ->
                    mipPixmaps[mip] = orderedFaceNames.map { face -> loadPixmap(facePaths, face, intensity) }
                }
            } catch (error: Throwable) {
                mipPixmaps.values.flatten().forEach { pixmap ->
                    if (!pixmap.isDisposed) pixmap.dispose()
                }
                throw error
            }
        }

    private fun validateMipPixmaps(mipPixmaps: SortedMap<Int, List<Pixmap>>) {
        val basePixmaps = mipPixmaps[0] ?: error("Radiance cubemap is missing mip 0.")
        val baseResolution = basePixmaps.firstOrNull()?.width ?: error("Radiance cubemap mip 0 has no faces.")
        val requiredMipCount = requiredRadianceMipCount(baseResolution)
        val expectedLevels = (0 until requiredMipCount).toList()
        require(mipPixmaps.keys.toList() == expectedLevels) {
            "Radiance cubemap must contain sequential mip levels ${expectedLevels.first()}..${expectedLevels.last()}."
        }
        mipPixmaps.forEach { (mip, pixmaps) ->
            val expectedResolution = radianceMipResolution(baseResolution, mip)
            require(pixmaps.size == orderedFaceNames.size) {
                "Radiance cubemap mip $mip must contain $CUBEMAP_FACE_COUNT faces."
            }
            require(pixmaps.all { it.width == expectedResolution && it.height == expectedResolution }) {
                "Radiance cubemap mip $mip must contain ${expectedResolution}x$expectedResolution faces."
            }
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
        intensity: Float = 1f,
    ): Pixmap {
        val path = facePaths[face] ?: error("Cubemap is missing face '$face'.")
        val file = Gdx.files.internal(path)
        require(file.exists()) { "Cubemap face is missing: '$path'." }
        return Pixmap(file).withLinearIntensity(intensity.coerceAtLeast(0f))
    }

    private fun Pixmap.withLinearIntensity(intensity: Float): Pixmap {
        if (intensity == 1f) return this
        val adjusted = Pixmap(width, height, Pixmap.Format.RGBA8888)
        val color = Color()
        for (y in 0 until height) {
            for (x in 0 until width) {
                Color.rgba8888ToColor(color, getPixel(x, y))
                adjusted.drawPixel(
                    x,
                    y,
                    Color.rgba8888(
                        linearToSrgb(srgbToLinear(color.r) * intensity),
                        linearToSrgb(srgbToLinear(color.g) * intensity),
                        linearToSrgb(srgbToLinear(color.b) * intensity),
                        color.a,
                    ),
                )
            }
        }
        dispose()
        return adjusted
    }

    private fun srgbToLinear(value: Float): Float = if (value <= 0.04045f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)

    private fun linearToSrgb(value: Float): Float {
        val clamped = value.coerceIn(0f, 1f)
        return if (clamped <= 0.0031308f) clamped * 12.92f else 1.055f * clamped.pow(1f / 2.4f) - 0.055f
    }

    private fun Pixmap.textureData(): PixmapTextureData = PixmapTextureData(this, format, false, true)

    private val orderedFaceNames = listOf("posx", "negx", "posy", "negy", "posz", "negz")
    private const val CUBEMAP_FACE_COUNT = 6
}
