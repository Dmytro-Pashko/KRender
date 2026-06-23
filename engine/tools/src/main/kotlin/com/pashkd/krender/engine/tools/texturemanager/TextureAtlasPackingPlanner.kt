package com.pashkd.krender.engine.tools.texturemanager

class TextureAtlasPackingPlanner {
    fun plan(
        inputs: List<TextureAtlasPackingInput>,
        settings: TextureAtlasPackingSettings,
    ): TextureAtlasPackingResult {
        val diagnostics = mutableListOf<TextureAtlasPackingDiagnostic>()
        if (settings.maxPageWidth <= 0 || settings.maxPageHeight <= 0) {
            diagnostics += diagnostic(TextureManagerDiagnosticSeverity.Error, "Page size must be greater than zero.")
            return TextureAtlasPackingResult(diagnostics = diagnostics)
        }
        if (settings.padding < 0) {
            diagnostics += diagnostic(TextureManagerDiagnosticSeverity.Error, "Padding must be zero or greater.")
            return TextureAtlasPackingResult(diagnostics = diagnostics)
        }
        if (inputs.isEmpty()) {
            diagnostics += diagnostic(TextureManagerDiagnosticSeverity.Warning, "No source textures were available for packing.")
            return TextureAtlasPackingResult(
                plan = TextureAtlasPackingPlan(settings.copy(), 0, 0, 0, emptyList()),
                diagnostics = diagnostics,
            )
        }
        if (settings.allowRotation) {
            diagnostics += diagnostic(TextureManagerDiagnosticSeverity.Info, "Rotation is not applied in this dry-run MVP.")
        }

        val sortedInputs =
            inputs.sortedWith(
                compareByDescending<TextureAtlasPackingInput> { it.height }
                    .thenByDescending { it.width }
                    .thenBy { it.displayName.lowercase() },
            )

        val pages = mutableListOf<MutablePackingPage>()
        var currentPage = MutablePackingPage(index = 0)
        var cursorX = settings.padding
        var cursorY = settings.padding
        var shelfHeight = 0
        var skipped = 0

        fun startNewShelf() {
            cursorX = settings.padding
            cursorY += shelfHeight + settings.padding
            shelfHeight = 0
        }

        fun startNewPage() {
            if (currentPage.regions.isNotEmpty()) {
                pages += currentPage
            }
            currentPage = MutablePackingPage(index = pages.size)
            cursorX = settings.padding
            cursorY = settings.padding
            shelfHeight = 0
        }

        sortedInputs.forEach { input ->
            if (!settings.includeNinePatch && input.isNinePatch) {
                skipped++
                diagnostics += diagnostic(TextureManagerDiagnosticSeverity.Info, "Skipped Nine-patch texture while includeNinePatch is disabled.", input.sourcePath)
                return@forEach
            }
            if (input.width + settings.padding * 2 > settings.maxPageWidth ||
                input.height + settings.padding * 2 > settings.maxPageHeight
            ) {
                skipped++
                diagnostics += diagnostic(TextureManagerDiagnosticSeverity.Error, "Texture is larger than the selected page size.", input.sourcePath)
                return@forEach
            }

            if (cursorX + input.width + settings.padding > settings.maxPageWidth) {
                startNewShelf()
            }
            if (cursorY + input.height + settings.padding > settings.maxPageHeight) {
                startNewPage()
            }
            if (cursorX + input.width + settings.padding > settings.maxPageWidth ||
                cursorY + input.height + settings.padding > settings.maxPageHeight
            ) {
                skipped++
                diagnostics += diagnostic(TextureManagerDiagnosticSeverity.Error, "Texture could not fit into a fresh page with the selected size.", input.sourcePath)
                return@forEach
            }

            currentPage.regions +=
                TextureAtlasPackingRegion(
                    sourcePath = input.sourcePath,
                    displayName = input.displayName,
                    pageIndex = currentPage.index,
                    x = cursorX,
                    y = cursorY,
                    width = input.width,
                    height = input.height,
                    rotated = false,
                    padding = settings.padding,
                )
            cursorX += input.width + settings.padding
            shelfHeight = maxOf(shelfHeight, input.height)
        }

        if (currentPage.regions.isNotEmpty()) {
            pages += currentPage
        }

        val packedPages =
            pages.map { page ->
                val usedWidth = page.regions.maxOfOrNull { region -> region.x + region.width + settings.padding } ?: 0
                val usedHeight = page.regions.maxOfOrNull { region -> region.y + region.height + settings.padding } ?: 0
                TextureAtlasPackingPage(
                    index = page.index,
                    width = usedWidth.coerceAtLeast(settings.padding * 2),
                    height = usedHeight.coerceAtLeast(settings.padding * 2),
                    regions = page.regions.toList(),
                )
            }

        if (packedPages.isEmpty()) {
            diagnostics += diagnostic(TextureManagerDiagnosticSeverity.Warning, "Packing dry-run produced no atlas pages.")
        }
        if (packedPages.size > 1) {
            diagnostics += diagnostic(TextureManagerDiagnosticSeverity.Info, "Packing dry-run generated ${packedPages.size} pages.")
        }

        return TextureAtlasPackingResult(
            plan =
                TextureAtlasPackingPlan(
                    settings = settings.copy(),
                    inputCount = inputs.size,
                    packedRegionCount = packedPages.sumOf { page -> page.regions.size },
                    skippedCount = skipped,
                    pages = packedPages,
                ),
            diagnostics = diagnostics,
        )
    }

    private fun diagnostic(
        severity: TextureManagerDiagnosticSeverity,
        message: String,
        sourcePath: String? = null,
    ) = TextureAtlasPackingDiagnostic(severity = severity, message = message, sourcePath = sourcePath)

    private data class MutablePackingPage(
        val index: Int,
        val regions: MutableList<TextureAtlasPackingRegion> = mutableListOf(),
    )
}
