package com.pashkd.krender.engine.tools.environmenteditor

import java.awt.image.BufferedImage
import java.awt.image.Raster

/**
 * Converts decoded image buffers into the shared [HdrImage] contract.
 */
object HdrImageBufferConverters {
    /**
     * Converts a decoded image raster to packed linear RGB floats.
     *
     * Alpha, if present, is ignored because environment generation only needs radiance color.
     */
    fun bufferedImageToHdrImage(image: BufferedImage): HdrImage =
        rasterToHdrImage(
            width = image.width,
            height = image.height,
            raster = image.raster,
        )

    /**
     * Converts a raster into top-left row-major packed RGB floats.
     */
    fun rasterToHdrImage(
        width: Int,
        height: Int,
        raster: Raster,
    ): HdrImage {
        val bandCount = raster.numBands
        require(bandCount >= 3) { "HDR image must contain at least three color bands." }

        val pixels = FloatArray(width * height * 3)
        var destinationIndex = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[destinationIndex] = raster.getSampleFloat(x, y, 0)
                pixels[destinationIndex + 1] = raster.getSampleFloat(x, y, 1)
                pixels[destinationIndex + 2] = raster.getSampleFloat(x, y, 2)
                destinationIndex += 3
            }
        }
        return HdrImage(width, height, pixels)
    }

    /**
     * Packs three single-channel float planes into one [HdrImage].
     *
     * All planes are expected to use the same top-left row-major orientation.
     */
    fun planarRgbToHdrImage(
        width: Int,
        height: Int,
        redChannel: FloatArray,
        greenChannel: FloatArray,
        blueChannel: FloatArray,
    ): HdrImage {
        val pixelCount = width * height
        require(width > 0 && height > 0) { "HDR image dimensions must be positive." }
        require(redChannel.size >= pixelCount) { "Red channel does not contain enough pixels." }
        require(greenChannel.size >= pixelCount) { "Green channel does not contain enough pixels." }
        require(blueChannel.size >= pixelCount) { "Blue channel does not contain enough pixels." }

        val packedPixels = FloatArray(pixelCount * 3)
        var destinationIndex = 0
        for (pixelIndex in 0 until pixelCount) {
            packedPixels[destinationIndex] = redChannel[pixelIndex]
            packedPixels[destinationIndex + 1] = greenChannel[pixelIndex]
            packedPixels[destinationIndex + 2] = blueChannel[pixelIndex]
            destinationIndex += 3
        }
        return HdrImage(width, height, packedPixels)
    }

    /**
     * Converts an arbitrary list of float planes into a safe RGB triplet.
     *
     * If a decoder cannot identify named RGB channels but did return at least three planes, the
     * first three are treated as RGB in file order as a documented fallback.
     */
    fun fallbackRgbPlanes(floatPlanes: List<FloatArray>): Triple<FloatArray, FloatArray, FloatArray> {
        require(floatPlanes.size >= 3) { "HDR image does not contain enough color channels." }
        return Triple(floatPlanes[0], floatPlanes[1], floatPlanes[2])
    }
}
