package com.pashkd.krender.engine.assets

import java.io.File
import java.io.RandomAccessFile

/**
 * Lightweight texture header metadata used by the asset browser.
 */
data class TextureMetadata(
    val width: Int,
    val height: Int,
    val hasAlpha: Boolean,
) {
    val colorFormat: String
        get() = if (hasAlpha) "RGBA" else "RGB"
}

/**
 * Reads dimensions and alpha presence from common texture headers without backend dependencies.
 */
object TextureMetadataReader {
    fun read(file: File): TextureMetadata? =
        when (file.extension.lowercase()) {
            "png" -> readPng(file)
            "jpg", "jpeg" -> readJpeg(file)
            "webp" -> readWebp(file)
            else -> null
        }

    private fun readPng(file: File): TextureMetadata? {
        RandomAccessFile(file, "r").use { input ->
            if (input.length() < 33L) return null
            val signature = ByteArray(PngSignature.size)
            input.readFully(signature)
            if (!signature.contentEquals(PngSignature)) return null
            val ihdrLength = input.readInt()
            val chunkType = input.readAscii(4)
            if (ihdrLength < 13 || chunkType != "IHDR") return null
            val width = input.readInt()
            val height = input.readInt()
            input.skipBytes(1) // bit depth
            val colorType = input.readUnsignedByte()
            var hasAlpha = colorType == 4 || colorType == 6
            input.skipBytes(3) // compression, filter, interlace
            input.skipBytes(4) // IHDR crc

            if (colorType == 3) {
                hasAlpha = hasPngTransparencyChunk(input)
            }
            return TextureMetadata(width, height, hasAlpha)
        }
    }

    private fun hasPngTransparencyChunk(input: RandomAccessFile): Boolean {
        while (input.filePointer + 12L <= input.length()) {
            val length = input.readInt()
            val chunkType = input.readAscii(4)
            if (length < 0 || input.filePointer + length + 4L > input.length()) return false
            if (chunkType == "tRNS") return true
            if (chunkType == "IDAT" || chunkType == "IEND") return false
            input.skipBytes(length)
            input.skipBytes(4)
        }
        return false
    }

    private fun readJpeg(file: File): TextureMetadata? {
        RandomAccessFile(file, "r").use { input ->
            if (input.length() < 4L || input.readUnsignedShort() != JpegSoi) return null
            while (input.filePointer + 4L <= input.length()) {
                var markerPrefix = input.readUnsignedByte()
                while (markerPrefix != JpegMarkerPrefix && input.filePointer < input.length()) {
                    markerPrefix = input.readUnsignedByte()
                }
                if (input.filePointer >= input.length()) return null

                var marker = input.readUnsignedByte()
                while (marker == JpegMarkerPrefix && input.filePointer < input.length()) {
                    marker = input.readUnsignedByte()
                }
                if (marker == JpegEoi || marker == JpegSos) return null
                if (marker in JpegStandaloneMarkers) continue
                if (input.filePointer + 2L > input.length()) return null

                val segmentLength = input.readUnsignedShort()
                if (segmentLength < 2 || input.filePointer + segmentLength - 2L > input.length()) return null
                if (marker in JpegStartOfFrameMarkers) {
                    input.skipBytes(1) // precision
                    val height = input.readUnsignedShort()
                    val width = input.readUnsignedShort()
                    return TextureMetadata(width, height, hasAlpha = false)
                }
                input.skipBytes(segmentLength - 2)
            }
            return null
        }
    }

    private fun readWebp(file: File): TextureMetadata? {
        RandomAccessFile(file, "r").use { input ->
            if (input.length() < 16L) return null
            if (input.readAscii(4) != "RIFF") return null
            input.skipBytes(4)
            if (input.readAscii(4) != "WEBP") return null

            while (input.filePointer + 8L <= input.length()) {
                val chunkType = input.readAscii(4)
                val chunkSize = input.readLittleEndianInt()
                if (chunkSize < 0 || input.filePointer + chunkSize > input.length()) return null
                val chunkDataStart = input.filePointer
                val metadata = when (chunkType) {
                    "VP8X" -> readWebpExtended(input, chunkSize)
                    "VP8 " -> readWebpLossy(input, chunkSize)
                    "VP8L" -> readWebpLossless(input, chunkSize)
                    else -> null
                }
                if (metadata != null) return metadata
                input.seek(chunkDataStart + chunkSize + (chunkSize % 2))
            }
            return null
        }
    }

    private fun readWebpExtended(input: RandomAccessFile, chunkSize: Int): TextureMetadata? {
        if (chunkSize < 10) return null
        val flags = input.readUnsignedByte()
        input.skipBytes(3)
        val width = 1 + input.readLittleEndian24()
        val height = 1 + input.readLittleEndian24()
        input.skipBytes(chunkSize - 10)
        return TextureMetadata(width, height, hasAlpha = flags and WebpAlphaFlag != 0)
    }

    private fun readWebpLossy(input: RandomAccessFile, chunkSize: Int): TextureMetadata? {
        if (chunkSize < 10) return null
        input.skipBytes(3)
        if (input.readUnsignedByte() != 0x9d || input.readUnsignedByte() != 0x01 || input.readUnsignedByte() != 0x2a) {
            input.skipBytes(chunkSize - 6)
            return null
        }
        val width = input.readLittleEndianShort() and 0x3fff
        val height = input.readLittleEndianShort() and 0x3fff
        input.skipBytes(chunkSize - 10)
        return TextureMetadata(width, height, hasAlpha = false)
    }

    private fun readWebpLossless(input: RandomAccessFile, chunkSize: Int): TextureMetadata? {
        if (chunkSize < 5) return null
        if (input.readUnsignedByte() != 0x2f) {
            input.skipBytes(chunkSize - 1)
            return null
        }
        val b0 = input.readUnsignedByte()
        val b1 = input.readUnsignedByte()
        val b2 = input.readUnsignedByte()
        val b3 = input.readUnsignedByte()
        val width = 1 + (b0 or ((b1 and 0x3f) shl 8))
        val height = 1 + (((b1 and 0xc0) shr 6) or (b2 shl 2) or ((b3 and 0x0f) shl 10))
        input.skipBytes(chunkSize - 5)
        return TextureMetadata(width, height, hasAlpha = true)
    }

    private fun RandomAccessFile.readAscii(length: Int): String {
        val bytes = ByteArray(length)
        readFully(bytes)
        return bytes.toString(Charsets.US_ASCII)
    }

    private fun RandomAccessFile.readLittleEndianInt(): Int {
        val b0 = readUnsignedByte()
        val b1 = readUnsignedByte()
        val b2 = readUnsignedByte()
        val b3 = readUnsignedByte()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun RandomAccessFile.readLittleEndianShort(): Int {
        val b0 = readUnsignedByte()
        val b1 = readUnsignedByte()
        return b0 or (b1 shl 8)
    }

    private fun RandomAccessFile.readLittleEndian24(): Int {
        val b0 = readUnsignedByte()
        val b1 = readUnsignedByte()
        val b2 = readUnsignedByte()
        return b0 or (b1 shl 8) or (b2 shl 16)
    }

    private val PngSignature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
    private const val JpegSoi = 0xffd8
    private const val JpegMarkerPrefix = 0xff
    private const val JpegEoi = 0xd9
    private const val JpegSos = 0xda
    private const val WebpAlphaFlag = 0x10
    private val JpegStandaloneMarkers = setOf(0x01) + (0xd0..0xd7)
    private val JpegStartOfFrameMarkers = setOf(
        0xc0,
        0xc1,
        0xc2,
        0xc3,
        0xc5,
        0xc6,
        0xc7,
        0xc9,
        0xca,
        0xcb,
        0xcd,
        0xce,
        0xcf,
    )
}
