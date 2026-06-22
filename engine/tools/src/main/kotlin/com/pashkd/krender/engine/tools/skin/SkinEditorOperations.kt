package com.pashkd.krender.engine.tools.skin

import com.pashkd.krender.engine.api.EngineContext
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutConfigCodec
import com.pashkd.krender.engine.ui.editor.ImGuiLayoutRuntimeTracker

class SkinEditorOperations(
    private val state: SkinEditorState,
    private val engine: EngineContext,
    private val layoutTracker: ImGuiLayoutRuntimeTracker,
) {
    fun requestReload() {
        state.reloadRequested = true
    }

    fun openPath(path: String) {
        state.currentInputPath = path.trim().replace('\\', '/').ifBlank { null }
        state.pendingPathInput = state.currentInputPath.orEmpty()
        state.reloadRequested = true
    }

    fun selectStyle(styleKey: StyleKey) {
        state.selectedStyleKey = styleKey
        state.selectedResourceKey = null
        state.selectedProblemIndex = null
        state.previewDirty = true
    }

    fun selectResource(resource: SkinResourceInfo) {
        state.selectedResourceKey = resource.key
        state.selectedStyleKey = null
        state.selectedProblemIndex = null
        state.resourceVisualPreview.selectedAtlasRegionName =
            if (resource.category == SkinResourceCategory.AtlasRegion) {
                resource.name
            } else {
                null
            }
    }

    fun selectProblem(
        index: Int,
        problem: SkinProblem,
    ) {
        state.selectedProblemIndex = index
        state.selectedStyleKey = problem.styleKey
        state.selectedResourceKey = problem.resourceKey
        state.resourceVisualPreview.selectedAtlasRegionName =
            problem.resourceKey
                ?.takeIf { key -> key.category == SkinResourceCategory.AtlasRegion }
                ?.name
    }

    fun discardInMemoryEdits() {
        state.editSession = SkinEditSessionFactory.create(state.loadResult)
        state.selectedEditFieldName = null
        state.previewDirty = true
        state.statusMessage = "In-memory edits discarded."
    }

    fun updateStyleField(
        styleKey: StyleKey,
        fieldName: String,
        value: String,
    ) {
        val style = state.editSession.findEditableStyle(styleKey) ?: return
        val field = style.fields[fieldName] ?: return
        if (field.value == value) return
        field.value = value
        if (field.originalValue == value) {
            style.modifiedFields.remove(fieldName)
            removeFieldChange(styleKey = style.key, fieldName = fieldName)
            updateEditStatus("Reset ${style.key.type}.${style.key.name}.$fieldName")
            return
        } else {
            style.modifiedFields += fieldName
        }
        recordEdit(
            SkinEditChange(
                description = "Changed ${style.key.type}.${style.key.name}.$fieldName",
                styleKey = style.key,
                fieldName = fieldName,
            ),
        )
    }

    fun resetStyleField(
        styleKey: StyleKey,
        fieldName: String,
    ) {
        val style = state.editSession.findEditableStyle(styleKey) ?: return
        val field = style.fields[fieldName] ?: return
        val originalValue = field.originalValue ?: return
        if (field.value == originalValue) return
        updateStyleField(styleKey, fieldName, originalValue)
    }

    fun addStyleField(
        styleKey: StyleKey,
        knownField: KnownStyleField,
    ) {
        val style = state.editSession.findEditableStyle(styleKey) ?: return
        if (style.fields.keys.any { name -> name.equals(knownField.name, ignoreCase = true) }) {
            state.statusMessage = "Field '${knownField.name}' already exists."
            return
        }
        style.fields[knownField.name] =
            EditableStyleField(
                name = knownField.name,
                value = "",
                valueType = "string",
                referenceCategory = knownField.referenceCategory,
                isReference = knownField.referenceCategory != null,
                originalValue = null,
            )
        style.modifiedFields += knownField.name
        style.removedFields.remove(knownField.name)
        state.selectedEditFieldName = knownField.name
        recordEdit(
            SkinEditChange(
                description = "Added ${style.key.type}.${style.key.name}.${knownField.name}",
                styleKey = style.key,
                fieldName = knownField.name,
            ),
        )
    }

    fun removeStyleField(
        styleKey: StyleKey,
        fieldName: String,
    ) {
        val style = state.editSession.findEditableStyle(styleKey) ?: return
        val removedField = style.fields.remove(fieldName) ?: return
        style.modifiedFields.remove(fieldName)
        if (removedField.originalValue == null) {
            style.removedFields.remove(fieldName)
            removeFieldChange(styleKey = style.key, fieldName = fieldName)
            updateEditStatus("Removed newly added ${style.key.type}.${style.key.name}.$fieldName")
        } else {
            style.removedFields += fieldName
            recordEdit(
                SkinEditChange(
                    description = "Removed ${style.key.type}.${style.key.name}.$fieldName",
                    styleKey = style.key,
                    fieldName = fieldName,
                ),
            )
        }
        if (state.selectedEditFieldName == fieldName) {
            state.selectedEditFieldName = null
        }
    }

    fun duplicateStyle(
        sourceKey: StyleKey,
        newName: String,
    ): Boolean {
        val source = state.editSession.findEditableStyle(sourceKey) ?: return false
        val targetKey = StyleKey(source.key.type, newName.trim())
        if (!validateNewStyleKey(targetKey)) return false
        state.editSession.styles[targetKey] =
            source.copy(
                key = targetKey,
                displayName = "${targetKey.type}.${targetKey.name}",
                fields = source.fields.mapValuesTo(linkedMapOf()) { (_, field) -> field.copy() },
                sourceKey = source.sourceKey ?: source.key,
                createdInEditor = true,
                renamedInEditor = false,
                deleted = false,
                modifiedFields = source.fields.keys.toMutableSet(),
                removedFields = source.removedFields.toMutableSet(),
            )
        state.selectedStyleKey = targetKey
        state.selectedResourceKey = null
        state.selectedProblemIndex = null
        recordEdit(SkinEditChange("Duplicated ${source.key.type}.${source.key.name} as ${targetKey.name}", styleKey = targetKey))
        return true
    }

    fun renameStyle(
        sourceKey: StyleKey,
        newName: String,
    ): Boolean {
        val source = state.editSession.findEditableStyle(sourceKey) ?: return false
        val targetKey = StyleKey(source.key.type, newName.trim())
        if (targetKey == source.key) return true
        if (!validateNewStyleKey(targetKey)) return false
        state.editSession.styles.remove(source.key)
        source.key = targetKey
        source.displayName = "${targetKey.type}.${targetKey.name}"
        source.renamedInEditor = true
        state.editSession.styles[targetKey] = source
        state.editSession.changes.indices.forEach { index ->
            val change = state.editSession.changes[index]
            if (change.styleKey == sourceKey) {
                state.editSession.changes[index] = change.copy(styleKey = targetKey)
            }
        }
        state.selectedStyleKey = targetKey
        recordEdit(SkinEditChange("Renamed ${sourceKey.type}.${sourceKey.name} to ${targetKey.name}", styleKey = targetKey))
        return true
    }

    fun createStyle(
        type: String,
        name: String,
    ): Boolean {
        val key = StyleKey(type, name.trim())
        if (!validateNewStyleKey(key)) return false
        val fields =
            SkinStyleTemplates.fieldsFor(type)
                .associateTo(linkedMapOf()) { template ->
                    template.name to
                        EditableStyleField(
                            name = template.name,
                            value = "",
                            valueType = "string",
                            referenceCategory = template.referenceCategory,
                            isReference = template.referenceCategory != null,
                            originalValue = null,
                        )
                }
        state.editSession.styles[key] =
            EditableStyle(
                key = key,
                displayName = "${key.type}.${key.name}",
                fields = fields,
                sourceKey = null,
                createdInEditor = true,
                modifiedFields = fields.keys.toMutableSet(),
            )
        state.selectedStyleKey = key
        state.selectedResourceKey = null
        state.selectedProblemIndex = null
        recordEdit(SkinEditChange("Created ${key.type}.${key.name}", styleKey = key))
        return true
    }

    fun deleteStyle(styleKey: StyleKey) {
        val style = state.editSession.findEditableStyle(styleKey) ?: return
        style.deleted = true
        state.selectedStyleKey = null
        state.selectedEditFieldName = null
        recordEdit(SkinEditChange("Deleted ${style.key.type}.${style.key.name}", styleKey = style.key))
    }

    fun updateColorResource(
        resourceKey: SkinResourceKey,
        fieldName: String,
        value: String,
    ) {
        val resource = state.editSession.resources[resourceKey] ?: return
        if (resource.key.category != SkinResourceCategory.Color || resource.values[fieldName] == value) return
        resource.values[fieldName] = value
        if (resource.originalValues[fieldName] == value) {
            resource.modifiedFields.remove(fieldName)
            removeFieldChange(resourceKey = resource.key, fieldName = fieldName)
            updateEditStatus("Reset ${resource.key.category}.${resource.key.name}.$fieldName")
            return
        }
        resource.modifiedFields += fieldName
        recordEdit(
            SkinEditChange(
                description = "Changed ${resource.key.category}.${resource.key.name}.$fieldName",
                resourceKey = resource.key,
                fieldName = fieldName,
            ),
        )
    }

    fun selectEditChange(change: SkinEditChange) {
        state.selectedStyleKey = change.styleKey
        state.selectedResourceKey = change.resourceKey
        state.selectedProblemIndex = null
        state.previewDirty = change.styleKey != null
    }

    fun selectLayout(layoutId: String) {
        state.previewLayoutId = layoutId
        updatePreviewStatus("Preview layout set to '$layoutId'.")
    }

    fun selectScreenPreset(presetId: String) {
        val preset = SkinPreviewScreenPresets.presetOrDefault(presetId)
        state.previewSettings.screenPresetId = preset.id
        updatePreviewStatus("Preview screen set to ${preset.displayName} (${preset.width} x ${preset.height}).")
    }

    fun setPreviewScale(scale: Float) {
        state.previewSettings.scale = scale.coerceIn(0.5f, 1.5f)
        updatePreviewStatus("Preview scale set to ${formatScale(state.previewSettings.scale)}.")
    }

    fun setShowBounds(showBounds: Boolean) {
        state.previewSettings.showBounds = showBounds
        updatePreviewStatus(if (showBounds) "Preview bounds enabled." else "Preview bounds hidden.")
    }

    fun setShowFallbackWarnings(showWarnings: Boolean) {
        state.previewSettings.showFallbackWarnings = showWarnings
        updatePreviewStatus(if (showWarnings) "Fallback warnings shown in Problems." else "Fallback warnings hidden in Problems.")
    }

    fun resetPreviewScale() {
        state.previewSettings.scale = 1f
        updatePreviewStatus("Preview scale reset to 100%.")
    }

    fun resetPreviewScreenPreset() {
        val preset = SkinPreviewScreenPresets.presetOrDefault(SkinPreviewScreenPresets.DesktopId)
        state.previewSettings.screenPresetId = preset.id
        updatePreviewStatus("Preview screen reset to ${preset.displayName}.")
    }

    fun resetPreviewLayout() {
        state.previewLayoutId = DefaultWidgetPreviewLayout.Id
        updatePreviewStatus("Preview layout reset to '${DefaultWidgetPreviewLayout.Id}'.")
    }

    fun resetPreviewSettings() {
        state.previewSettings = SkinPreviewSettings()
        state.previewLayoutId = DefaultWidgetPreviewLayout.Id
        updatePreviewStatus("Preview settings reset.")
    }

    fun previewSelectedStyle() {
        val selectedStyle = state.selectedStyleKey
        if (selectedStyle == null) {
            state.statusMessage = "Select a style before using selected-style preview."
            return
        }
        state.previewLayoutId = SelectedStylePreviewLayout.Id
        updatePreviewStatus("Previewing selected style '${selectedStyle.type}.${selectedStyle.name}'.")
    }

    fun setResourcePreviewZoomMode(zoomMode: SkinResourceVisualPreviewZoomMode) {
        state.resourceVisualPreview.zoomMode = zoomMode
        state.statusMessage = "Resource preview zoom set to ${formatResourceZoom(zoomMode)}."
    }

    fun resetResourcePreviewZoom() {
        state.resourceVisualPreview.zoomMode = SkinResourceVisualPreviewZoomMode.Fit
        state.statusMessage = "Resource preview zoom reset to Fit."
    }

    fun setShowResourceRegionBounds(showBounds: Boolean) {
        state.resourceVisualPreview.showRegionBounds = showBounds
        state.statusMessage = if (showBounds) "Resource preview bounds enabled." else "Resource preview bounds hidden."
    }

    fun setShowResourceRegionLabels(showLabels: Boolean) {
        state.resourceVisualPreview.showRegionLabels = showLabels
        state.statusMessage = if (showLabels) "Resource preview labels enabled." else "Resource preview labels hidden."
    }

    fun selectAtlasRegionPreview(regionName: String?) {
        state.resourceVisualPreview.selectedAtlasRegionName = regionName?.takeIf(String::isNotBlank)
        state.statusMessage =
            state.resourceVisualPreview.selectedAtlasRegionName?.let { name ->
                "Atlas preview region set to '$name'."
            } ?: "Atlas preview region cleared."
    }

    fun setFontPreviewScale(scale: Float) {
        state.resourceVisualPreview.fontPreview.fontScale = scale.coerceIn(0.5f, 2f)
        state.statusMessage = "Font preview scale set to ${formatScale(state.resourceVisualPreview.fontPreview.fontScale)}."
    }

    fun setFontPreviewSampleText(sampleText: String) {
        state.resourceVisualPreview.fontPreview.sampleText = sampleText
    }

    fun setShowUkrainianFontSample(show: Boolean) {
        state.resourceVisualPreview.fontPreview.showUkrainianSample = show
        state.statusMessage = if (show) "Ukrainian font sample enabled." else "Ukrainian font sample hidden."
    }

    fun setShowAsciiFontSample(show: Boolean) {
        state.resourceVisualPreview.fontPreview.showAsciiSample = show
        state.statusMessage = if (show) "ASCII font sample enabled." else "ASCII font sample hidden."
    }

    fun saveUiLayout() {
        ImGuiLayoutConfigCodec.save(SkinEditorUiLayoutDefaults.assetPath, layoutTracker.currentConfig(), engine.sceneFiles)
        state.statusMessage = "Panel layout saved."
    }

    fun restoreUiLayout() {
        layoutTracker.requestRestore(SkinEditorUiLayoutDefaults.config)
        state.statusMessage = "Panel layout restored."
    }

    private fun updatePreviewStatus(message: String) {
        state.previewDirty = true
        state.statusMessage = message
    }

    private fun validateNewStyleKey(key: StyleKey): Boolean {
        if (key.name.isBlank()) {
            state.statusMessage = "Style name cannot be blank."
            return false
        }
        if (state.editSession.styles[key]?.deleted == false) {
            state.statusMessage = "Style '${key.type}.${key.name}' already exists."
            return false
        }
        return true
    }

    private fun recordEdit(change: SkinEditChange) {
        if (change.fieldName != null) {
            state.editSession.changes.removeAll { existing ->
                existing.fieldName == change.fieldName &&
                    existing.styleKey == change.styleKey &&
                    existing.resourceKey == change.resourceKey
            }
        }
        state.editSession.changes += change
        state.editSession.dirty = state.editSession.changes.isNotEmpty()
        state.previewDirty = true
        state.statusMessage = "${change.description}. Edits are in-memory only."
    }

    private fun removeFieldChange(
        styleKey: StyleKey? = null,
        resourceKey: SkinResourceKey? = null,
        fieldName: String,
    ) {
        state.editSession.changes.removeAll { change ->
            change.fieldName == fieldName &&
                change.styleKey == styleKey &&
                change.resourceKey == resourceKey
        }
        state.editSession.dirty = state.editSession.changes.isNotEmpty()
    }

    private fun updateEditStatus(description: String) {
        state.previewDirty = true
        state.statusMessage = "$description. Edits are in-memory only."
    }

    private fun formatScale(scale: Float): String = "${(scale * 100f).toInt()}%"

    private fun formatResourceZoom(zoomMode: SkinResourceVisualPreviewZoomMode): String =
        when (zoomMode) {
            SkinResourceVisualPreviewZoomMode.Fit -> "Fit"
            SkinResourceVisualPreviewZoomMode.Percent50 -> "50%"
            SkinResourceVisualPreviewZoomMode.Percent100 -> "100%"
            SkinResourceVisualPreviewZoomMode.Percent200 -> "200%"
        }
}
