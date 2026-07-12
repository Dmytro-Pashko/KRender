package com.pashkd.krender.engine.tools.environmenteditor

object EnvironmentResourceDiagnostics {
    fun buildSummary(model: EnvironmentResourcePreviewModel): List<String> =
        buildList {
            addAll(model.diagnostics)
            model.selectedItem?.warnings?.forEach { warning ->
                if (warning !in this) add(warning)
            }
        }

    @Suppress("CyclomaticComplexMethod")
    fun buildSelectedPreviewWarnings(model: EnvironmentResourcePreviewModel): List<String> {
        val selected = model.selectedItem ?: return model.diagnostics
        return buildSet {
            addAll(model.diagnostics)
            addAll(selected.warnings)
            if (!selected.exists) {
                add("Missing file.")
            }
            val isCubemapResource =
                selected.resourceMode == EnvironmentResourceMode.Skybox ||
                    selected.resourceMode == EnvironmentResourceMode.Irradiance ||
                    selected.resourceMode == EnvironmentResourceMode.Radiance
            if (isCubemapResource) {
                val width = selected.width
                val height = selected.height
                if (width != null && height != null && width != height) {
                    add("Non-square cubemap face.")
                }
                val referenceSize =
                    model.items
                        .mapNotNull { item -> item.width?.let { w -> item.height?.let { h -> w to h } } }
                        .firstOrNull()
                val hasComparableFaceSize = referenceSize != null && width != null && height != null
                val hasMismatchedFaceSize =
                    hasComparableFaceSize &&
                        (width != referenceSize.first || height != referenceSize.second)
                if (hasMismatchedFaceSize) {
                    add("Face size mismatch.")
                }
            }
            selected.sourceRegion?.let { region ->
                val width = selected.width
                val height = selected.height
                if (width != null && height != null) {
                    val outside =
                        region.x < 0 ||
                            region.y < 0 ||
                            region.x + region.width > width ||
                            region.y + region.height > height
                    if (outside) {
                        add("Region outside bounds.")
                    }
                }
            }
        }.toList()
    }
}
