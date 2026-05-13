package com.pashkd.krender.engine.animationviewer

import com.pashkd.krender.engine.api.ModelAssetInfo
import com.pashkd.krender.engine.api.ModelBoneInfo
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
        textLine("Animation metadata: ${availabilityLabel(state.animationMetadataAvailable)}")
        textLine("Animation preview: ${animationPreviewStatusLabel(state)}")
        if (state.animationPreviewStatus == AnimationPreviewStatus.PreviewRequested) {
            textLine("Animation preview confirmation: backend confirmation unavailable")
        }
        textLine("Skeleton metadata: ${availabilityLabel(state.hasSkeletonData)}")
        textLine("Skeleton preview: ${skeletonPreviewStatusLabel(state)}")
        textLine("Skeleton nodes: ${state.skeletonInfo?.boneCount ?: 0}")
        if (state.skeletonPreviewStatus == SkeletonPreviewStatus.Inactive && state.hasSkeletonData) {
            textLine("Switch to Skeleton view to validate live pose sampling.")
        }
    }
}

/**
 * Shows read-only skeleton hierarchy, inspection details, and debug rendering toggles.
 */
class AnimationViewerSkeletonPanel(
    private val state: AnimationViewerState,
    private val operations: AnimationViewerOperations,
    private val layoutConfig: ImGuiLayoutConfig,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
    private val eventLogger: ImGuiWindowEventLogger,
) : UiPanel {
    override fun draw() {
        val expanded = beginAnimationViewerPanel(AnimationViewerPanelIds.Skeleton, layoutConfig, layoutTracker, eventLogger)
        if (!expanded) {
            state.hoveredBoneIndex = null
            ImGui.end()
            return
        }

        val bones = state.skeletonInfo?.bones.orEmpty()
        state.hoveredBoneIndex = null

        textLine("Skeleton metadata: ${availabilityLabel(state.hasSkeletonData)}")
        textLine("Skeleton preview: ${skeletonPreviewStatusLabel(state)}")
        textLine("Skeleton nodes: ${bones.size}")
        textLine("Selected bone: ${state.selectedBoneName ?: state.selectedBone?.let(::boneDisplayName) ?: "None"}")
        state.skeletonWarning?.let { warning -> textLine("Warning: $warning") }

        ImGui.separator()
        ImGui.checkbox("Show joints##animation_viewer_show_skeleton_joints", state::showSkeletonJoints)
        ImGui.checkbox("Highlight connected bones##animation_viewer_highlight_connected_bones", state::highlightConnectedBones)
        slider(
            "Joint size##animation_viewer_skeleton_joint_size",
            state::skeletonJointSize,
            MinJointSize,
            MaxJointSize,
            "%.3f",
            SliderFlag.AlwaysClamp,
        )
        with(dsl) {
            button("Clear Selection##animation_viewer_clear_bone_selection") { operations.clearBoneSelection() }
        }

        ImGui.separator()
        drawSelectedBoneDetails(bones)
        ImGui.separator()
        ImGui.text("Bones")
        if (bones.isEmpty()) {
            ImGui.text("No skeleton nodes found.")
            ImGui.end()
            return
        }

        ImGui.beginChild("animation_viewer_skeleton_bone_list", ImVec2(0f, 0f), true)
        drawBoneList(bones)
        ImGui.endChild()
        ImGui.end()
    }

    private fun drawSelectedBoneDetails(bones: List<ModelBoneInfo>) {
        ImGui.text("Selected Bone")
        val selectedBone = state.selectedBone
        if (selectedBone == null) {
            textLine("None")
            return
        }

        val parent = bones.firstOrNull { bone -> bone.index == selectedBone.parentIndex }
        val children = bones.filter { bone -> bone.parentIndex == selectedBone.index }
        val pose = state.selectedBonePose
        textLine("Index: ${selectedBone.index}")
        textLine("Name: ${boneDisplayName(selectedBone)}")
        textLine("Parent: ${parent?.let(::boneDebugSummary) ?: "none"}")
        textLine(
            "Children: ${children.takeIf(List<ModelBoneInfo>::isNotEmpty)?.joinToString { child -> boneDebugSummary(child) } ?: "none"}",
        )
        textLine("Pose: ${pose?.worldPosition?.let(::formatPosition) ?: "unavailable"}")
    }

    private fun drawBoneList(bones: List<ModelBoneInfo>) {
        val childrenByParent = bones.groupBy(ModelBoneInfo::parentIndex)
        val visited = linkedSetOf<Int>()
        childrenByParent[null].orEmpty().forEach { root ->
            drawBoneNode(root, childrenByParent, visited, depth = 0)
        }
        bones.filterNot { bone -> bone.index in visited }.forEach { bone ->
            drawBoneNode(bone, childrenByParent, visited, depth = 0)
        }
    }

    private fun drawBoneNode(
        bone: ModelBoneInfo,
        childrenByParent: Map<Int?, List<ModelBoneInfo>>,
        visited: MutableSet<Int>,
        depth: Int,
    ) {
        if (!visited.add(bone.index)) return

        val label = "${"  ".repeat(depth.coerceAtLeast(0))}${boneDisplayName(bone)}"
        if (ImGui.selectable("$label##animation_viewer_bone_${bone.index}", state.selectedBoneIndex == bone.index)) {
            operations.selectBone(bone.index)
        }
        if (ImGui.isItemHovered()) {
            state.hoveredBoneIndex = bone.index
        }

        childrenByParent[bone.index].orEmpty().forEach { child ->
            drawBoneNode(child, childrenByParent, visited, depth + 1)
        }
    }

    companion object {
        private const val MinJointSize = 0.01f
        private const val MaxJointSize = 0.5f
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

        val duration = state.durationSeconds
        if (duration != null && duration > 0f) {
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
        textLine("Time: ${formatSeconds(state.currentTimeSeconds)} / ${duration?.let(::formatSeconds) ?: "unknown"}")

        if (!hasSelection) {
            ImGui.text("Select an animation to control playback.")
            ImGui.end()
            return
        }

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
            ImGui.text("Unknown duration preview window: ${formatSeconds(state.unknownDurationPreviewWindowSeconds)}")
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

private fun availabilityLabel(available: Boolean): String = if (available) "available" else "unavailable"

private fun boneDisplayName(bone: ModelBoneInfo): String = bone.name?.takeIf(String::isNotBlank) ?: "Bone #${bone.index}"

private fun boneDebugSummary(bone: ModelBoneInfo): String = "${boneDisplayName(bone)} / index ${bone.index}"

private fun animationPreviewStatusLabel(state: AnimationViewerState): String = when (state.animationPreviewStatus) {
    AnimationPreviewStatus.Unsupported -> "unsupported"
    AnimationPreviewStatus.MetadataOnly -> "metadata only"
    AnimationPreviewStatus.PreviewRequested -> "requested"
    AnimationPreviewStatus.PreviewAvailable -> "available"
}

private fun skeletonPreviewStatusLabel(state: AnimationViewerState): String = when (state.skeletonPreviewStatus) {
    SkeletonPreviewStatus.Inactive -> "inactive"
    SkeletonPreviewStatus.Unsupported -> "unsupported"
    SkeletonPreviewStatus.MetadataOnly -> "metadata only"
    SkeletonPreviewStatus.PreviewAvailable -> "available"
}

private fun textLine(text: String) {
    ImGui.textUnformatted(text)
}

private class FloatHolder(var value: Float)

