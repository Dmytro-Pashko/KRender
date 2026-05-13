package com.pashkd.krender.engine.animationviewer

import com.pashkd.krender.engine.api.ModelAssetInfo
import com.pashkd.krender.engine.api.Vec2
import com.pashkd.krender.engine.api.Vec3
import com.pashkd.krender.engine.ui.ImGuiLayoutConfig
import com.pashkd.krender.engine.ui.ImGuiLayoutRuntimeTracker
import com.pashkd.krender.engine.ui.ImGuiWindowEventLogger
import com.pashkd.krender.engine.ui.UiPanel
import com.pashkd.krender.engine.ui.beginImGuiPanel
import glm_.vec2.Vec2 as ImVec2
import imgui.ImGui
import imgui.SliderFlag
import imgui.api.slider
import imgui.dsl
import kotlin.math.max

/**
 * Top-level Animation Viewer action/status panel.
 */
class AnimationViewerToolbarPanel(
    private val state: AnimationViewerState,
    private val operations: AnimationViewerOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val expanded = beginAnimationViewerPanel(AnimationViewerPanelIds.Toolbar, layoutConfig, layoutTracker, eventLogger)
        if (!expanded) {
            ImGui.end()
            return
        }

        with(dsl) {
            button("Persist UI##animation_viewer_persist_ui") { operations.saveUiLayout() }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Reset UI to Default##animation_viewer_reset_ui") { operations.restoreUiLayout() }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Reset Camera##animation_viewer_reset_camera") { operations.resetCamera() }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Frame Model##animation_viewer_frame_model") { operations.frameModel() }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Exit##animation_viewer_exit") { state.exitRequested = true }
        }

        ImGui.separator()
        textLine("Model: ${state.modelPath}")
        textLine("Status: ${state.statusMessage}")
        state.errorMessage?.let { error -> textLine("Error: $error") }
        textLine("Asset: ${state.loadingStatus}")
        textLine("Selected Animation: ${state.selectedAnimationName ?: "None"}")
        textLine("Camera speed: ${"%.2f".format(state.camera.speed)}")
        ImGui.end()
    }
}

/**
 * Viewport status and display-options panel.
 */
class AnimationViewerViewportPanel(
    private val state: AnimationViewerState,
    private val operations: AnimationViewerOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val expanded = beginAnimationViewerPanel(AnimationViewerPanelIds.Viewport, layoutConfig, layoutTracker, eventLogger)
        if (!expanded) {
            state.viewportFocused = false
            ImGui.end()
            return
        }

        ImGui.beginChild("animation_viewer_viewport_body", ImVec2(0f, 0f), true)
        val viewportPosition = ImGui.windowPos
        val viewportSize = ImGui.windowSize
        state.viewportOrigin = Vec2(viewportPosition.x, viewportPosition.y)
        state.viewportSize = Vec2(viewportSize.x.coerceAtLeast(0f), viewportSize.y.coerceAtLeast(0f))
        state.viewportFocused = ImGui.isWindowHovered() || ImGui.isWindowFocused()

        ImGui.text("Animation view")
        ImGui.separator()
        textLine("Camera: ${formatPosition(state.camera.position)}")
        textLine("Speed: ${"%.2f".format(state.camera.speed)}${if (state.camera.navigating) " (navigating)" else ""}")
        ImGui.text("RMB look, WASD move, Q/E down/up, wheel speed, Shift faster.")
        ImGui.separator()

        drawViewModeCombo()
        ImGui.checkbox("Grid##animation_viewer_show_grid", state::showGrid)
        ImGui.sameLine()
        ImGui.checkbox("Axes##animation_viewer_show_axes", state::showAxes)
        ImGui.sameLine()
        ImGui.checkbox("Bounding Box##animation_viewer_show_bounding_box", state::showBoundingBox)
        ImGui.checkbox("Wireframe##animation_viewer_wireframe", state::wireframe)
        slider(
            "Grid extent##animation_viewer_grid_extent",
            state::gridHalfExtentCells,
            MinGridHalfExtentCells,
            MaxGridHalfExtentCells,
            "%d",
            SliderFlag.AlwaysClamp,
        )
        slider(
            "Cell size##animation_viewer_grid_cell_size",
            state::gridCellSize,
            MinGridCellSize,
            MaxGridCellSize,
            "%.2f",
            SliderFlag.AlwaysClamp,
        )
        val ambientIntensity = FloatHolder(state.ambientLightIntensity)
        if (
            slider(
                "Ambient Intensity##animation_viewer_ambient_intensity",
                ambientIntensity::value,
                MinAmbientLightIntensity,
                MaxAmbientLightIntensity,
                "%.2f",
                SliderFlag.AlwaysClamp,
            )
        ) {
            operations.setAmbientLightIntensity(ambientIntensity.value)
        }
        ImGui.sameLine()
        with(dsl) {
            button("Reset Ambient##animation_viewer_reset_ambient") { operations.resetAmbientLight() }
        }
        state.skeletonWarning?.let { warning -> textLine("Warning: $warning") }
        ImGui.endChild()
        ImGui.end()
    }

    private fun drawViewModeCombo() {
        if (!ImGui.beginCombo("View Mode##animation_viewer_view_mode", viewModeLabel(state.viewMode))) return
        AnimationViewerViewMode.entries.forEach { mode ->
            if (ImGui.selectable("${viewModeLabel(mode)}##animation_viewer_view_mode_${mode.name}", state.viewMode == mode)) {
                state.viewMode = mode
            }
        }
        ImGui.endCombo()
    }

    companion object {
        private const val MinGridHalfExtentCells = 1
        private const val MaxGridHalfExtentCells = 256
        private const val MinGridCellSize = 0.05f
        private const val MaxGridCellSize = 16f
        private const val MinAmbientLightIntensity = 0f
        private const val MaxAmbientLightIntensity = 3f
    }
}

/**
 * Shows available animations and rig metadata.
 */
class AnimationViewerAnimationsPanel(
    private val state: AnimationViewerState,
    private val operations: AnimationViewerOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val expanded = beginAnimationViewerPanel(AnimationViewerPanelIds.Animations, layoutConfig, layoutTracker, eventLogger)
        if (!expanded) {
            ImGui.end()
            return
        }

        val info = state.modelInfo
        drawRigSummary(info)
        ImGui.separator()
        ImGui.text("Animations")
        if (state.animationNames.isEmpty()) {
            ImGui.text("No animations found.")
            ImGui.end()
            return
        }

        state.animationNames.forEachIndexed { index, animationName ->
            val selected = state.selectedAnimationIndex == index
            val duration = info?.animations?.firstOrNull { animation -> animation.name == animationName }?.durationSeconds
            val suffix = duration?.let { " (${formatSeconds(it)})" }.orEmpty()
            if (ImGui.selectable("$animationName$suffix##animation_viewer_anim_$index", selected)) {
                operations.selectAnimation(index)
            }
        }
        ImGui.separator()
        textLine("Selected: ${state.selectedAnimationName ?: "None"}")
        textLine("Duration: ${state.durationSeconds?.let(::formatSeconds) ?: "unknown"}")
        state.animationWarning?.let { warning -> textLine("Warning: $warning") }
        ImGui.end()
    }

    private fun drawRigSummary(info: ModelAssetInfo?) {
        textLine("Skeleton: ${if (info?.hasSkeleton == true) "yes" else "no"}")
        textLine("Skinned bones: ${info?.boneCount ?: 0}")
        textLine("Bone weight channels: ${info?.boneWeightChannelCount ?: 0}")
        textLine("Animation preview: ${animationPreviewStatusLabel(state)}")
        textLine("Skeleton preview: ${skeletonPreviewStatusLabel(state)}")
        textLine("Skeleton metadata: ${if (state.hasSkeletonData) "available" else "unavailable"}")
        textLine("Skeleton nodes: ${state.skeletonInfo?.boneCount ?: 0}")
        if (state.viewMode == AnimationViewerViewMode.Model && state.hasSkeletonData && !state.skeletonPreviewSupported) {
            textLine("Switch to Skeleton view to validate live pose sampling.")
        }
    }
}

/**
 * Shows playback state and controls for the selected animation.
 */
class AnimationViewerPlaybackPanel(
    private val state: AnimationViewerState,
    private val operations: AnimationViewerOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val expanded = beginAnimationViewerPanel(AnimationViewerPanelIds.Playback, layoutConfig, layoutTracker, eventLogger)
        if (!expanded) {
            ImGui.end()
            return
        }

        val hasSelection = state.selectedAnimationName != null
        with(dsl) {
            button(if (state.isPlaying) "Pause##animation_viewer_pause" else "Play##animation_viewer_play") {
                if (state.isPlaying) operations.pause() else operations.play()
            }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Stop##animation_viewer_stop") { operations.stop() }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Step -##animation_viewer_step_back") { operations.stepBy(-FrameStepSeconds) }
        }
        ImGui.sameLine()
        with(dsl) {
            button("Step +##animation_viewer_step_forward") { operations.stepBy(FrameStepSeconds) }
        }

        if (state.durationSeconds != null && state.durationSeconds!! > 0f) {
            val loopValue = booleanArrayOf(state.loop)
            if (ImGui.checkbox("Loop##animation_viewer_loop", loopValue)) {
                operations.toggleLoop()
            }
        } else {
            textLine("Loop: unavailable while clip duration is unknown.")
        }

        slider(
            "Playback Speed##animation_viewer_speed",
            state::playbackSpeed,
            MinPlaybackSpeed,
            MaxPlaybackSpeed,
            "%.2f",
            SliderFlag.AlwaysClamp,
        )

        textLine("Animation: ${state.selectedAnimationName ?: "None"}")
        textLine("Animation preview: ${animationPreviewStatusLabel(state)}")
        textLine("Skeleton preview: ${skeletonPreviewStatusLabel(state)}")
        textLine("Time: ${formatSeconds(state.currentTimeSeconds)} / ${state.durationSeconds?.let(::formatSeconds) ?: "unknown"}")

        if (!hasSelection) {
            ImGui.text("Select an animation to control playback.")
            ImGui.end()
            return
        }

        val duration = state.durationSeconds
        if (duration != null && duration > 0f) {
            val scrub = max(0f, state.currentTimeSeconds.coerceIn(0f, duration))
            val holder = FloatHolder(scrub)
            if (
                slider(
                    "Time##animation_viewer_time",
                    holder::value,
                    0f,
                    duration,
                    "%.3f s",
                    SliderFlag.AlwaysClamp,
                )
            ) {
                operations.scrubTo(holder.value)
            }
        } else {
            ImGui.text("Scrubbing is unavailable because clip duration is unknown.")
            ImGui.text("Playback can still advance, but looping is limited until duration metadata is available.")
        }
        ImGui.end()
    }

    companion object {
        private const val MinPlaybackSpeed = 0f
        private const val MaxPlaybackSpeed = 4f
        private const val FrameStepSeconds = 1f / 30f
    }
}

/**
 * Displays loading progress while the active model asset is pending.
 */
class AnimationViewerLoadingPanel(
    private val state: AnimationViewerState,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        if (!state.isLoadingModel) return

        val expanded = beginAnimationViewerPanel(AnimationViewerPanelIds.Loading, layoutConfig, layoutTracker, eventLogger)
        if (!expanded) {
            ImGui.end()
            return
        }

        ImGui.text("Loading model asset")
        textLine(state.modelPath)
        ImGui.separator()
        textLine("Progress: ${"%.0f".format(state.assetProgress * 100f)}%")
        textLine(state.loadingStatus)
        ImGui.end()
    }
}

internal fun beginAnimationViewerPanel(
    panelId: String,
    layoutConfig: ImGuiLayoutConfig,
    layoutTracker: ImGuiLayoutRuntimeTracker,
    eventLogger: ImGuiWindowEventLogger,
): Boolean {
    val layout = layoutConfig.panels.getValue(panelId)
    val expanded = beginImGuiPanel(panelId, layout, layoutTracker)
    eventLogger.observe(panelId, layout.title)
    return expanded
}

private fun viewModeLabel(mode: AnimationViewerViewMode): String = when (mode) {
    AnimationViewerViewMode.Model -> "Model"
    AnimationViewerViewMode.Skeleton -> "Skeleton"
    AnimationViewerViewMode.ModelAndSkeleton -> "Model + Skeleton"
}

private fun formatPosition(position: Vec3): String =
    "%.2f, %.2f, %.2f".format(position.x, position.y, position.z)

private fun formatSeconds(value: Float): String = "%.3f s".format(value)

private fun animationPreviewStatusLabel(state: AnimationViewerState): String = when {
    !state.assetLoaded || !state.hasAnimations -> "unsupported"
    state.animationPreviewSupported -> "supported"
    else -> "metadata only"
}

private fun skeletonPreviewStatusLabel(state: AnimationViewerState): String =
    if (state.skeletonPreviewSupported) "supported" else "unsupported"

private fun textLine(text: String) {
    ImGui.textUnformatted(text)
}

private class FloatHolder(var value: Float)

