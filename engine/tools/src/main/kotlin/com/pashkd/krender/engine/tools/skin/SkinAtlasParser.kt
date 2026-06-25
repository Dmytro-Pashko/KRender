package com.pashkd.krender.engine.tools.skin

import java.io.File
import java.nio.charset.StandardCharsets

data class SkinAtlasInfo(
    val file: File,
    val pages: List<SkinAtlasPageInfo> = emptyList(),
    val regions: List<SkinAtlasRegionInfo> = emptyList(),
    val readable: Boolean = true,
)

data class SkinAtlasPageInfo(
    val name: String,
    val details: Map<String, String> = emptyMap(),
)

data class SkinAtlasRegionInfo(
    val name: String,
    val page: String? = null,
    val details: Map<String, String> = emptyMap(),
)

class SkinAtlasParser {
    fun parse(file: File): SkinAtlasInfo =
        runCatching {
            val pages = mutableListOf<MutableAtlasPage>()
            var currentPage: MutableAtlasPage? = null
            var currentRegion: MutableAtlasRegion? = null
            var expectPage = true

            file.readLines(StandardCharsets.UTF_8).forEach { rawLine ->
                val line = rawLine.trim()
                when {
                    line.isEmpty() -> {
                        currentRegion = null
                        currentPage = null
                        expectPage = true
                    }

                    !rawLine.first().isWhitespace() && ':' !in line -> {
                        if (expectPage || currentPage == null) {
                            currentPage = MutableAtlasPage(name = line)
                            pages += currentPage
                            currentRegion = null
                            expectPage = false
                        } else {
                            currentRegion = MutableAtlasRegion(name = line)
                            currentPage.regions += currentRegion
                        }
                    }

                    ':' in line -> {
                        val key = line.substringBefore(':').trim()
                        val value = line.substringAfter(':').trim()
                        if (key.isNotEmpty()) {
                            if (currentRegion != null) {
                                currentRegion.details[key] = value
                            } else {
                                currentPage?.details?.set(key, value)
                            }
                        }
                    }
                }
            }

            SkinAtlasInfo(
                file = file,
                pages =
                    pages.map { page ->
                        SkinAtlasPageInfo(name = page.name, details = page.details.toSortedMap())
                    },
                regions =
                    pages.flatMap { page ->
                        page.regions.map { region ->
                            SkinAtlasRegionInfo(
                                name = region.name,
                                page = page.name,
                                details = region.details.toSortedMap(),
                            )
                        }
                    },
            )
        }.getOrElse {
            SkinAtlasInfo(file = file, readable = false)
        }

    private data class MutableAtlasPage(
        val name: String,
        val details: MutableMap<String, String> = linkedMapOf(),
        val regions: MutableList<MutableAtlasRegion> = mutableListOf(),
    )

    private data class MutableAtlasRegion(
        val name: String,
        val details: MutableMap<String, String> = linkedMapOf(),
    )
}
