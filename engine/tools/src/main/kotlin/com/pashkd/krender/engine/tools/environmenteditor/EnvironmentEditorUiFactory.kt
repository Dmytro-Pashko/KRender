package com.pashkd.krender.engine.tools.environmenteditor

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.assets.environment.EnvironmentService
import com.pashkd.krender.engine.assets.importing.FileDialogService
import com.pashkd.krender.engine.tools.environmenteditor.preview.EnvironmentPreviewController
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.LogsPanel
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.UiSystem

/**
 * Builds Environment Editor panels with shared layout and error-reporting policy.
 *
 * Panel construction lives outside the Scene so lifecycle setup remains readable
 * and every panel receives the same layout snapshot, tracker, and event logger.
 */
@Suppress("LongParameterList")
class EnvironmentEditorUiFactory(
    private val state: EnvironmentEditorState,
    private val controller: EnvironmentEditorController,
    private val previewController: EnvironmentPreviewController,
    private val resourcePreviewController: EnvironmentResourcePreviewController,
    private val selectedResourcePreviewController: EnvironmentSelectedResourcePreviewController,
    private val skyboxImportController: SkyboxAtlasImportController,
    private val hdrGenerationController: HdrEnvironmentGenerationController,
    private val environmentService: EnvironmentService,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val engine: EngineContext,
    private val fileDialogService: FileDialogService,
) {
    @Suppress("LongMethod")
    fun create(): UiSystem {
        val layout = layoutTracker.currentConfig()
        val eventLogger = ImGuiWindowEventLogger(engine.logger, "EnvironmentEditorUi")
        return UiSystem(engine.ui).also { ui ->
            ui.addSafePanel("Control", EnvironmentEditorControlPanel(state, controller, layout, layoutTracker, eventLogger))
            ui.addSafePanel("Inspector", EnvironmentInspectorPanel(state, layout, layoutTracker, eventLogger))
            ui.addSafePanel("Settings", EnvironmentSettingsPanel(state, engine.logger, layout, layoutTracker, eventLogger))
            ui.addSafePanel(
                "Tools",
                EnvironmentToolsPanel(
                    state,
                    resourcePreviewController,
                    skyboxImportController,
                    hdrGenerationController,
                    fileDialogService,
                    layout,
                    layoutTracker,
                    eventLogger,
                ),
            )
            ui.addSafePanel(
                "Selected Resource Preview",
                EnvironmentSelectedResourcePreviewPanel(
                    state,
                    resourcePreviewController,
                    selectedResourcePreviewController,
                    layout,
                    layoutTracker,
                    eventLogger,
                ),
            )
            ui.addSafePanel(
                "Cubemap Preview",
                EnvironmentResourceInspectorPanel(
                    state,
                    resourcePreviewController,
                    layout,
                    layoutTracker,
                    eventLogger,
                ),
            )
            ui.addSafePanel(
                "Diagnostics",
                EnvironmentDiagnosticsPanel(state, environmentService, layout, layoutTracker, eventLogger),
            )
            ui.addSafePanel(
                "Preview",
                EnvironmentPreviewPanel(state, previewController, layout, layoutTracker, eventLogger),
            )
            ui.addSafePanel(
                "Logs",
                LogsPanel(
                    engine.logs,
                    layout,
                    eventLogger,
                    panelId = EnvironmentEditorPanelIds.Logs,
                    layoutTracker = layoutTracker,
                    initialAutoScrollToLatest = true,
                ),
            )
        }
    }

    private fun UiSystem.addSafePanel(
        name: String,
        panel: UiPanel,
    ) {
        addPanel(
            UiPanel {
                try {
                    panel.draw()
                } catch (error: Exception) {
                    engine.logger.error(TAG, error) {
                        "Environment Editor panel draw failed panel='$name': ${error.message}"
                    }
                    throw error
                }
            },
        )
    }

    companion object {
        private const val TAG = "EnvironmentEditorUi"
    }
}
