package com.pashkd.krender.engine.tools.environmenteditor

import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil.memByteBufferNT1
import org.lwjgl.system.MemoryUtil.memFloatBuffer
import org.lwjgl.system.MemoryUtil.memUTF8
import org.lwjgl.util.tinyexr.EXRHeader
import org.lwjgl.util.tinyexr.EXRImage
import org.lwjgl.util.tinyexr.EXRVersion
import org.lwjgl.util.tinyexr.TinyEXR
import java.io.File
import java.util.Locale

/**
 * Decodes single-part OpenEXR images through LWJGL TinyEXR bindings.
 *
 * The reader intentionally keeps all native interop inside this class so the rest of the
 * Environment Editor only deals with [HdrImage] and regular Kotlin exceptions.
 */
class OpenExrHdrReader : HdrImageReader {
    override fun read(path: File): HdrImage {
        require(path.isFile) { "EXR source file not found: ${path.path}" }

        MemoryStack.stackPush().use { stack ->
            val version = EXRVersion.calloc(stack)
            val errorPointer = stack.mallocPointer(1)
            errorPointer.put(0, 0L)
            val versionResult = TinyEXR.ParseEXRVersionFromFile(version, path.absolutePath)
            if (versionResult != TinyEXR.TINYEXR_SUCCESS) {
                throw HdrReaderException("Failed to parse EXR version for ${path.name}.")
            }

            if (version.multipart()) {
                throw HdrReaderException("Multipart OpenEXR files are not supported yet.")
            }
            if (version.non_image()) {
                throw HdrReaderException("Non-image OpenEXR files are not supported.")
            }

            val header = EXRHeader.calloc(stack)
            val image = EXRImage.calloc(stack)
            TinyEXR.InitEXRHeader(header)
            TinyEXR.InitEXRImage(image)

            try {
                errorPointer.put(0, 0L)
                checkTinyExrResult(
                    TinyEXR.ParseEXRHeaderFromFile(header, version, path.absolutePath, errorPointer),
                    errorPointer,
                    "Failed to read EXR channel layout for ${path.name}",
                )
                prepareRequestedPixelTypes(header)

                errorPointer.put(0, 0L)
                checkTinyExrResult(
                    TinyEXR.LoadEXRImageFromFile(image, header, path.absolutePath, errorPointer),
                    errorPointer,
                    "Failed to decode EXR image data for ${path.name}",
                )

                val width = image.width()
                val height = image.height()
                if (width <= 0 || height <= 0) {
                    throw HdrReaderException("EXR image has invalid dimensions: ${width}x$height")
                }

                val channelPlanes = extractChannelPlanes(header, image, width, height)
                val redChannel = channelPlanes["R"]
                val greenChannel = channelPlanes["G"]
                val blueChannel = channelPlanes["B"]
                return if (redChannel != null && greenChannel != null && blueChannel != null) {
                    HdrImageBufferConverters.planarRgbToHdrImage(width, height, redChannel, greenChannel, blueChannel)
                } else {
                    val fallbackPlanes = HdrImageBufferConverters.fallbackRgbPlanes(channelPlanes.values.toList())
                    HdrImageBufferConverters.planarRgbToHdrImage(width, height, fallbackPlanes.first, fallbackPlanes.second, fallbackPlanes.third)
                }
            } finally {
                TinyEXR.FreeEXRImage(image)
                TinyEXR.FreeEXRHeader(header)
            }
        }
    }

    private fun prepareRequestedPixelTypes(header: EXRHeader) {
        val pixelTypes = header.pixel_types()
            ?: throw HdrReaderException("EXR image does not expose pixel type information.")
        val requestedPixelTypes = header.requested_pixel_types()
            ?: throw HdrReaderException("EXR image does not expose requested pixel type information.")

        for (channelIndex in 0 until header.num_channels()) {
            // TinyEXR may decode HALF and UINT source channels, but the generator pipeline wants a
            // consistent float buffer regardless of how the file stored the values on disk.
            requestedPixelTypes.put(channelIndex, TinyEXR.TINYEXR_PIXELTYPE_FLOAT)
            pixelTypes.get(channelIndex)
        }
    }

    private fun extractChannelPlanes(
        header: EXRHeader,
        image: EXRImage,
        width: Int,
        height: Int,
    ): LinkedHashMap<String, FloatArray> {
        val numChannels = image.num_channels()
        if (numChannels <= 0) {
            throw HdrReaderException("EXR image does not contain any channels.")
        }

        val imagePointers = image.images()
            ?: throw HdrReaderException("EXR image channel buffer is empty.")
        val pixelCount = width * height
        val channels = header.channels()
            ?: throw HdrReaderException("EXR image channel metadata is missing.")

        val channelPlanes = linkedMapOf<String, FloatArray>()
        for (channelIndex in 0 until numChannels) {
            val channelPointer = imagePointers.get(channelIndex)
            if (channelPointer == 0L) {
                throw HdrReaderException("EXR channel buffer is missing at index $channelIndex.")
            }

            val channelName = normalizeChannelName(channels[channelIndex].nameString())
            val channelBuffer = memFloatBuffer(channelPointer, pixelCount)
            val channelPlane = FloatArray(pixelCount)
            channelBuffer.get(channelPlane)
            channelPlanes[channelName] = channelPlane
        }

        if (!channelPlanes.containsKey("R") || !channelPlanes.containsKey("G") || !channelPlanes.containsKey("B")) {
            if (channelPlanes.size < 3) {
                throw HdrReaderException("EXR image must contain RGB channels.")
            }
        }
        return channelPlanes
    }

    private fun normalizeChannelName(name: String): String =
        name
            .substringAfterLast('.')
            .uppercase(Locale.ROOT)

    private fun checkTinyExrResult(
        resultCode: Int,
        errorPointer: PointerBuffer,
        contextMessage: String,
    ) {
        if (resultCode == TinyEXR.TINYEXR_SUCCESS) {
            releaseTinyExrError(errorPointer)
            return
        }
        val detail = releaseTinyExrError(errorPointer)
        val suffix = detail?.takeIf(String::isNotBlank)?.let { ": $it" } ?: "."
        throw HdrReaderException("$contextMessage$suffix")
    }

    private fun releaseTinyExrError(errorPointer: PointerBuffer): String? {
        val pointer = errorPointer.get(0)
        if (pointer == 0L) return null

        val messageBuffer = memByteBufferNT1(pointer)
        val message = memUTF8(messageBuffer)
        TinyEXR.FreeEXRErrorMessage(messageBuffer)
        errorPointer.put(0, 0L)
        return message
    }
}
