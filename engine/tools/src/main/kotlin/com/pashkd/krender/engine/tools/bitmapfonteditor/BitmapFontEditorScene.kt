package com.pashkd.krender.engine.tools.bitmapfonteditor

import com.pashkd.krender.engine.api.Scene
import com.pashkd.krender.engine.scene.SceneConfig
import com.pashkd.krender.engine.scene.SceneConfigPresets
import com.pashkd.krender.engine.tools.bitmapfonteditor.panels.BitmapFontToolbarPanel
import com.pashkd.krender.engine.tools.bitmapfonteditor.panels.FontDiagnosticsPanel
import com.pashkd.krender.engine.tools.bitmapfonteditor.panels.FontGenerationPanel
import com.pashkd.krender.engine.tools.bitmapfonteditor.panels.FontPageCanvasPanel
import com.pashkd.krender.engine.tools.bitmapfonteditor.panels.GlyphInspectorPanel
import com.pashkd.krender.engine.tools.bitmapfonteditor.panels.GlyphListPanel
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigLoader
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.editor.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.editor.LogsPanel
import com.pashkd.krender.engine.ui.editor.UiPanel
import com.pashkd.krender.engine.ui.editor.UiSystem

class BitmapFontEditorScene(
    initialFontPath: String? = null,
) : Scene("bitmap_font_editor") {
    override val config: SceneConfig = SceneConfigPresets.BitmapFontEditor

    private val editorState = BitmapFontEditorState(inputPath = initialFontPath?.trim()?.replace('\\', '/'))
    private lateinit var controller: BitmapFontEditorController
    private lateinit var layoutTracker: ImGuiLayoutRuntimeTracker

    override fun show() {
        engine.logger.info(TAG) { "Showing Bitmap Font Editor path='${editorState.inputPath ?: "<none>"}'" }
        val layoutConfig =
            ImGuiLayoutConfigLoader(
                assetPath = BitmapFontEditorUiLayoutDefaults.assetPath,
                fallback = BitmapFontEditorUiLayoutDefaults.config,
            ).load(engine.logger, engine.sceneFiles)
        layoutTracker = ImGuiLayoutRuntimeTracker(layoutConfig)
        controller = BitmapFontEditorController(editorState, engine)
        world.systems.add(createUiSystem())
    }

    private fun createUiSystem(): UiSystem {
        val layoutConfig = layoutTracker.currentConfig()
        val eventLogger = ImGuiWindowEventLogger(engine.logger, "BitmapFontEditorUi")
        return UiSystem(engine.ui).also { uiSystem ->
            addPanel(uiSystem, "Toolbar", BitmapFontToolbarPanel(editorState, controller, layoutConfig, layoutTracker, eventLogger))
            addPanel(uiSystem, "Preview", FontPageCanvasPanel(editorState, controller, layoutConfig, layoutTracker, eventLogger))
            addPanel(uiSystem, "GlyphList", GlyphListPanel(editorState, controller, layoutConfig, layoutTracker, eventLogger))
            addPanel(uiSystem, "Inspector", GlyphInspectorPanel(editorState, controller, layoutConfig, layoutTracker, eventLogger))
            addPanel(uiSystem, "Generation", FontGenerationPanel(editorState, controller, layoutConfig, layoutTracker, eventLogger))
            addPanel(uiSystem, "Diagnostics", FontDiagnosticsPanel(editorState, layoutConfig, layoutTracker, eventLogger))
            addPanel(
                uiSystem,
                "Logs",
                LogsPanel(
                    engine.logs,
                    layoutConfig,
                    eventLogger,
                    panelId = BitmapFontEditorPanelIds.Logs,
                    layoutTracker = layoutTracker,
                    initialAutoScrollToLatest = true,
                ),
            )
        }
    }

    private fun addPanel(
        uiSystem: UiSystem,
        name: String,
        panel: UiPanel,
    ) {
        uiSystem.addPanel(
            UiPanel {
                try {
                    panel.draw()
                } catch (error: Exception) {
                    engine.logger.error(TAG, error) { "Bitmap Font Editor panel draw failed panel='$name': ${error.message}" }
                    throw error
                }
            },
        )
    }

    companion object {
        private const val TAG = "BitmapFontEditorScene"
    }
}
