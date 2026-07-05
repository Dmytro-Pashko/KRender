package com.pashkd.krender.engine.tools.environmenteditor

import com.twelvemonkeys.imageio.plugins.hdr.HDRImageReadParam
import com.twelvemonkeys.imageio.plugins.hdr.tonemap.NullToneMapper
import java.io.File
import javax.imageio.ImageIO
import javax.imageio.ImageReader

/**
 * Decodes Radiance RGBE `.hdr` files through the TwelveMonkeys ImageIO plugin.
 */
class HdrRadianceReader : HdrImageReader {
    override fun read(path: File): HdrImage {
        ensurePluginsLoaded()
        require(path.isFile) { "HDR source file not found: ${path.path}" }

        val stream =
            ImageIO.createImageInputStream(path)
                ?: throw HdrReaderException("Unable to open HDR source file: ${path.path}")
        stream.use { input ->
            val reader =
                ImageIO
                    .getImageReaders(input)
                    .asSequence()
                    .firstOrNull { candidate -> candidate.formatName.equals("hdr", ignoreCase = true) }
                    ?: ImageIO
                        .getImageReadersBySuffix("hdr")
                        .asSequence()
                        .firstOrNull()
                    ?: throw HdrReaderException("Radiance HDR reader plugin is not available.")

            return reader.useReader {
                setInput(input, true, true)
                val readParam =
                    HDRImageReadParam().apply {
                        // The environment pipeline expects untouched linear HDR values and applies
                        // exposure/tone mapping later when writing runtime PNG faces.
                        toneMapper = NullToneMapper()
                    }
                val image =
                    try {
                        read(0, readParam)
                    } catch (error: Exception) {
                        throw HdrReaderException("Failed to decode Radiance HDR file: ${path.name}", error)
                    }
                HdrImageBufferConverters.bufferedImageToHdrImage(image)
            }
        }
    }

    private inline fun <T> ImageReader.useReader(block: ImageReader.() -> T): T =
        try {
            block()
        } finally {
            dispose()
        }

    companion object {
        private val pluginsInitialized: Unit by lazy {
            ImageIO.scanForPlugins()
        }

        private fun ensurePluginsLoaded() {
            pluginsInitialized
        }
    }
}
