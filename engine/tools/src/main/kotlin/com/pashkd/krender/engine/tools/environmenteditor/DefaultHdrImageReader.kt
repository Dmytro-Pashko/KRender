package com.pashkd.krender.engine.tools.environmenteditor

import java.io.File
import java.util.Locale

/**
 * Default Environment Editor entry point for HDR-capable image decoding.
 *
 * The editor talks only to this facade so format-specific decoding stays isolated from UI,
 * generation orchestration, and manifest update code.
 */
class DefaultHdrImageReader(
    private val radianceReader: HdrImageReader = HdrRadianceReader(),
    private val openExrReader: HdrImageReader = OpenExrHdrReader(),
) : HdrImageReader {
    override fun read(path: File): HdrImage {
        require(path.isFile) { "HDR/EXR source file not found: ${path.path}" }
        return when (path.extension.lowercase(Locale.ROOT)) {
            "hdr" -> radianceReader.read(path)
            "exr" -> openExrReader.read(path)
            else -> throw HdrReaderException("Unsupported HDR environment source: .${path.extension}")
        }
    }
}
