@file:Suppress("TooGenericExceptionCaught")

package com.pashkd.krender.engine.backend.gdx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Cubemap
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.glutils.PixmapTextureData

internal fun cubemapFromSingleTexture(path: String): Cubemap {
    val source = Pixmap(Gdx.files.internal(path))
    val layout = cubemapLayoutFor(source.width, source.height)
    val faceSize = layout.faceSize
    val faces = ArrayList<Pixmap>(CUBEMAP_FACE_COUNT)
    try {
        layout.faces.forEach { faceRegion ->
            val face = Pixmap(faceSize, faceSize, source.format)
            face.drawPixmap(
                source,
                0,
                0,
                faceRegion.x * faceSize,
                faceRegion.y * faceSize,
                faceSize,
                faceSize,
            )
            faces += face
        }
        return Cubemap(
            faces[0].textureData(disposePixmap = true),
            faces[1].textureData(disposePixmap = true),
            faces[2].textureData(disposePixmap = true),
            faces[3].textureData(disposePixmap = true),
            faces[4].textureData(disposePixmap = true),
            faces[5].textureData(disposePixmap = true),
        ).also { cubemap ->
            cubemap.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            cubemap.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
        }
    } catch (error: Throwable) {
        faces.forEach { face ->
            if (!face.isDisposed) face.dispose()
        }
        throw error
    } finally {
        source.dispose()
    }
}

private data class CubemapLayout(
    val faceSize: Int,
    val faces: List<CubemapFaceRegion>,
)

private data class CubemapFaceRegion(
    val x: Int,
    val y: Int,
)

private fun cubemapLayoutFor(
    width: Int,
    height: Int,
): CubemapLayout {
    require(width > 0 && height > 0) {
        "Cubemap source dimensions must be positive, got ${width}x$height."
    }
    return when {
        width == height * CUBEMAP_FACE_COUNT -> {
            CubemapLayout(
                faceSize = height,
                faces = (0 until CUBEMAP_FACE_COUNT).map { index -> CubemapFaceRegion(index, 0) },
            )
        }

        height == width * CUBEMAP_FACE_COUNT -> {
            CubemapLayout(
                faceSize = width,
                faces = (0 until CUBEMAP_FACE_COUNT).map { index -> CubemapFaceRegion(0, index) },
            )
        }

        width % 4 == 0 && height % 3 == 0 && width / 4 == height / 3 -> {
            val faceSize = width / 4
            CubemapLayout(
                faceSize = faceSize,
                faces =
                    listOf(
                        CubemapFaceRegion(2, 1),
                        CubemapFaceRegion(0, 1),
                        CubemapFaceRegion(1, 0),
                        CubemapFaceRegion(1, 2),
                        CubemapFaceRegion(1, 1),
                        CubemapFaceRegion(3, 1),
                    ),
            )
        }

        width % 3 == 0 && height % 4 == 0 && width / 3 == height / 4 -> {
            val faceSize = width / 3
            CubemapLayout(
                faceSize = faceSize,
                faces =
                    listOf(
                        CubemapFaceRegion(2, 1),
                        CubemapFaceRegion(0, 1),
                        CubemapFaceRegion(1, 0),
                        CubemapFaceRegion(1, 2),
                        CubemapFaceRegion(1, 1),
                        CubemapFaceRegion(1, 3),
                    ),
            )
        }

        else ->
            error(
                "Unsupported cubemap texture layout ${width}x$height. " +
                    "Expected 6x1 strip, 1x6 strip, 4x3 cross, or 3x4 cross.",
            )
    }
}

private fun Pixmap.textureData(disposePixmap: Boolean): PixmapTextureData = PixmapTextureData(this, format, false, disposePixmap)

private const val CUBEMAP_FACE_COUNT = 6
