package com.pashkd.krender.engine.tools.skin

import com.pashkd.krender.engine.api.Logger

/**
 * Owns all in-memory Skin Editor mutations.
 *
 * The service updates [SkinEditorState.editSession], preview invalidation, and
 * UI status only. It intentionally performs no JSON serialization or file I/O.
 */
class SkinEditService(
    private val state: SkinEditorState,
    private val logger: Logger,
) {
    fun discardEdits() {
        val discardedChanges = state.editSession.changes.size
        val dirty = state.editSession.dirty
        state.editSession = SkinEditSessionFactory.create(state.loadResult)
        state.selectedEditFieldName = null
        state.previewDirty = true
        state.statusMessage = "In-memory edits discarded."
        logger.info(TAG) { "Discarded Skin Editor in-memory edits dirty=$dirty discardedChanges=$discardedChanges" }
    }

    @Suppress("ReturnCount")
    fun updateStyleField(
        styleKey: StyleKey,
        fieldName: String,
        value: String,
    ) {
        val style = state.editSession.findEditableStyle(styleKey) ?: return
        val field = style.fields[fieldName] ?: return
        if (field.value == value) return
        val oldValue = field.value
        field.value = value
        if (field.originalValue == value) {
            style.modifiedFields.remove(fieldName)
            removeFieldChange(styleKey = style.key, fieldName = fieldName)
            updateStatus("Reset ${style.key.type}.${style.key.name}.$fieldName")
            logger.info(TAG) {
                "Reset Skin Editor style field style='${style.key.type}.${style.key.name}' field='$fieldName' restoredValue='${field.originalValue.orEmpty()}'"
            }
            return
        }
        style.modifiedFields += fieldName
        record(
            SkinEditChange(
                type = SkinEditChangeType.StyleFieldChanged,
                description = "Changed ${style.key.type}.${style.key.name}.$fieldName",
                styleKey = style.key,
                fieldName = fieldName,
                oldValue = oldValue,
                newValue = value,
            ),
        )
        logger.info(TAG) {
            "Updated Skin Editor style field style='${style.key.type}.${style.key.name}' field='$fieldName' old='$oldValue' new='$value' reference=${field.isReference}"
        }
    }

    fun resetStyleField(
        styleKey: StyleKey,
        fieldName: String,
    ) {
        val field =
            state.editSession
                .findEditableStyle(styleKey)
                ?.fields
                ?.get(fieldName) ?: return
        val originalValue = field.originalValue ?: return
        updateStyleField(styleKey, fieldName, originalValue)
    }

    fun addStyleField(
        styleKey: StyleKey,
        knownField: KnownStyleField,
    ) {
        val style = state.editSession.findEditableStyle(styleKey) ?: return
        if (style.fields.keys.any { name -> name.equals(knownField.name, ignoreCase = true) }) {
            state.statusMessage = "Field '${knownField.name}' already exists."
            logger.warn(TAG) { "Rejected Skin Editor add field because it already exists style='${style.key.type}.${style.key.name}' field='${knownField.name}'" }
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
        record(
            SkinEditChange(
                type = SkinEditChangeType.StyleFieldAdded,
                description = "Added ${style.key.type}.${style.key.name}.${knownField.name}",
                styleKey = style.key,
                fieldName = knownField.name,
                newValue = "",
            ),
        )
        logger.info(TAG) {
            "Added Skin Editor style field style='${style.key.type}.${style.key.name}' field='${knownField.name}' referenceCategory=${knownField.referenceCategory}"
        }
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
            updateStatus("Removed newly added ${style.key.type}.${style.key.name}.$fieldName")
            logger.info(TAG) {
                "Removed newly added Skin Editor style field style='${style.key.type}.${style.key.name}' field='$fieldName'"
            }
        } else {
            style.removedFields += fieldName
            record(
                SkinEditChange(
                    type = SkinEditChangeType.StyleFieldRemoved,
                    description = "Removed ${style.key.type}.${style.key.name}.$fieldName",
                    styleKey = style.key,
                    fieldName = fieldName,
                    oldValue = removedField.value,
                ),
            )
            logger.info(TAG) {
                "Removed Skin Editor style field style='${style.key.type}.${style.key.name}' field='$fieldName' previousValue='${removedField.value}'"
            }
        }
        if (state.selectedEditFieldName == fieldName) state.selectedEditFieldName = null
    }

    @Suppress("ReturnCount")
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
                sourceKey = null,
                createdInEditor = true,
                renamedInEditor = false,
                deleted = false,
                modifiedFields = source.fields.keys.toMutableSet(),
                removedFields = source.removedFields.toMutableSet(),
            )
        selectStyle(targetKey)
        record(
            SkinEditChange(
                type = SkinEditChangeType.StyleDuplicated,
                description = "Duplicated ${source.key.type}.${source.key.name} as ${targetKey.name}",
                styleKey = targetKey,
                oldValue = source.key.name,
                newValue = targetKey.name,
            ),
        )
        logger.info(TAG) {
            "Duplicated Skin Editor style source='${source.key.type}.${source.key.name}' target='${targetKey.type}.${targetKey.name}' fieldCount=${source.fields.size}"
        }
        return true
    }

    @Suppress("ReturnCount")
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
            if (change.styleKey == sourceKey) state.editSession.changes[index] = change.copy(styleKey = targetKey)
        }
        state.selectedStyleKey = targetKey
        record(
            SkinEditChange(
                type = SkinEditChangeType.StyleRenamed,
                description = "Renamed ${sourceKey.type}.${sourceKey.name} to ${targetKey.name}",
                styleKey = targetKey,
                oldValue = sourceKey.name,
                newValue = targetKey.name,
            ),
        )
        logger.info(TAG) {
            "Renamed Skin Editor style source='${sourceKey.type}.${sourceKey.name}' target='${targetKey.type}.${targetKey.name}'"
        }
        return true
    }

    fun createStyle(
        type: String,
        name: String,
    ): Boolean {
        val key = StyleKey(type, name.trim())
        if (!validateNewStyleKey(key)) return false
        val fields =
            SkinStyleTemplates.fieldsFor(type).associateTo(linkedMapOf()) { template ->
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
        selectStyle(key)
        record(
            SkinEditChange(
                type = SkinEditChangeType.StyleCreated,
                description = "Created ${key.type}.${key.name}",
                styleKey = key,
                newValue = key.name,
            ),
        )
        logger.info(TAG) {
            "Created Skin Editor style style='${key.type}.${key.name}' templateFieldCount=${fields.size}"
        }
        return true
    }

    fun deleteStyle(styleKey: StyleKey) {
        val style = state.editSession.findEditableStyle(styleKey) ?: return
        style.deleted = true
        state.selectedStyleKey = null
        state.selectedEditFieldName = null
        record(
            SkinEditChange(
                type = SkinEditChangeType.StyleDeleted,
                description = "Deleted ${style.key.type}.${style.key.name}",
                styleKey = style.key,
                oldValue = style.key.name,
            ),
        )
        logger.info(TAG) {
            "Deleted Skin Editor style style='${style.key.type}.${style.key.name}' createdInEditor=${style.createdInEditor} modifiedFields=${style.modifiedFields.size}"
        }
    }

    @Suppress("ReturnCount")
    fun updateColorResource(
        resourceKey: SkinResourceKey,
        fieldName: String,
        value: String,
    ) {
        val resource = state.editSession.resources[resourceKey] ?: return
        if (resource.key.category != SkinResourceCategory.Color || resource.values[fieldName] == value) return
        val oldValue = resource.values[fieldName]
        resource.values[fieldName] = value
        if (resource.originalValues[fieldName] == value) {
            resource.modifiedFields.remove(fieldName)
            removeFieldChange(resourceKey = resource.key, fieldName = fieldName)
            updateStatus("Reset ${resource.key.category}.${resource.key.name}.$fieldName")
            logger.info(TAG) {
                "Reset Skin Editor resource field resource='${resource.key.category}.${resource.key.name}' field='$fieldName' restoredValue='${resource.originalValues[fieldName].orEmpty()}'"
            }
            return
        }
        resource.modifiedFields += fieldName
        record(
            SkinEditChange(
                type = SkinEditChangeType.ResourceFieldChanged,
                description = "Changed ${resource.key.category}.${resource.key.name}.$fieldName",
                resourceKey = resource.key,
                fieldName = fieldName,
                oldValue = oldValue,
                newValue = value,
            ),
        )
        logger.info(TAG) {
            "Updated Skin Editor resource field resource='${resource.key.category}.${resource.key.name}' field='$fieldName' old='${oldValue.orEmpty()}' new='$value'"
        }
    }

    fun selectChange(change: SkinEditChange) {
        state.selectedStyleKey = change.styleKey
        state.selectedResourceKey = change.resourceKey
        state.selectedProblemIndex = null
        state.previewDirty = change.styleKey != null
    }

    private fun selectStyle(key: StyleKey) {
        state.selectedStyleKey = key
        state.selectedResourceKey = null
        state.selectedProblemIndex = null
    }

    @Suppress("ReturnCount")
    private fun validateNewStyleKey(key: StyleKey): Boolean {
        if (key.name.isBlank()) {
            state.statusMessage = "Style name cannot be blank."
            logger.warn(TAG) { "Rejected Skin Editor style key because name was blank type='${key.type}'" }
            return false
        }
        if (state.editSession.styles[key]?.deleted == false) {
            state.statusMessage = "Style '${key.type}.${key.name}' already exists."
            logger.warn(TAG) { "Rejected Skin Editor style key because it already exists style='${key.type}.${key.name}'" }
            return false
        }
        return true
    }

    private fun record(change: SkinEditChange) {
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

    private fun updateStatus(description: String) {
        state.previewDirty = true
        state.statusMessage = "$description. Edits are in-memory only."
    }

    private companion object {
        private const val TAG = "SkinEditService"
    }
}
