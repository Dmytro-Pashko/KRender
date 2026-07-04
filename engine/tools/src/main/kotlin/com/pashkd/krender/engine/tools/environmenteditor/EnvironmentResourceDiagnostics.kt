package com.pashkd.krender.engine.tools.environmenteditor

object EnvironmentResourceDiagnostics {
    fun buildSummary(model: EnvironmentResourcePreviewModel): List<String> {
        return buildList {
            addAll(model.diagnostics)
            model.selectedItem?.warnings?.forEach { warning ->
                if (warning !in this) add(warning)
            }
        }
    }
}
