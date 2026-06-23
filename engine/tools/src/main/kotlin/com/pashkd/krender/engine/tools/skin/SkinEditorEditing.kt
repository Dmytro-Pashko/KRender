package com.pashkd.krender.engine.tools.skin

/**
 * In-memory editing layer for Skin Editor.
 *
 * Loaded/indexed model:
 * [SkinLoadResult], [SkinStyleIndex], [SkinResourceIndex]
 *
 * In-memory edit model:
 * [SkinEditSession], [EditableStyle], [EditableResource], [SkinEditChange]
 *
 * Preview state:
 * [SkinPreviewSettings], [SkinResourceVisualPreviewState]
 *
 * ImGui panels and GDX adapters may observe this model, but a future JSON
 * writer must consume [SkinEditSession.toEditedSnapshot] only. The writer must
 * not depend on ImGui panels, GDX preview adapters, runtime texture handles,
 * or UI buffers.
 */

/**
 * In-memory edit projection built from one loaded/indexed skin.
 *
 * [baseStyleIndex] and [baseResourceIndex] preserve the loaded state, while
 * [styles] and [resources] are mutable editor projections used by preview and
 * a future JSON writer. This session never writes files itself.
 */
data class SkinEditSession(
    val baseStyleIndex: SkinStyleIndex = SkinStyleIndex(),
    val baseResourceIndex: SkinResourceIndex = SkinResourceIndex(),
    val styles: MutableMap<StyleKey, EditableStyle> = linkedMapOf(),
    val resources: MutableMap<SkinResourceKey, EditableResource> = linkedMapOf(),
    var dirty: Boolean = false,
    val changes: MutableList<SkinEditChange> = mutableListOf(),
)

/** Editable style projection with source identity and field-level change state. */
data class EditableStyle(
    var key: StyleKey,
    var displayName: String,
    val fields: MutableMap<String, EditableStyleField> = linkedMapOf(),
    var sourceKey: StyleKey? = key,
    var createdInEditor: Boolean = false,
    var renamedInEditor: Boolean = false,
    var deleted: Boolean = false,
    val modifiedFields: MutableSet<String> = linkedSetOf(),
    val removedFields: MutableSet<String> = linkedSetOf(),
)

/** Editable scalar or resource-reference field belonging to an [EditableStyle]. */
data class EditableStyleField(
    val name: String,
    var value: String,
    var valueType: String,
    var referenceCategory: SkinResourceCategory? = null,
    var isReference: Boolean = false,
    val originalValue: String? = value,
)

/** Editable resource projection; currently used for top-level color values. */
data class EditableResource(
    val key: SkinResourceKey,
    val values: MutableMap<String, String> = linkedMapOf(),
    val originalValues: Map<String, String> = values.toMap(),
    var resolved: Boolean = true,
    var createdInEditor: Boolean = false,
    var deleted: Boolean = false,
    val modifiedFields: MutableSet<String> = linkedSetOf(),
)

/** Structured kind of in-memory mutation recorded for UI and future writer work. */
enum class SkinEditChangeType {
    StyleFieldChanged,
    StyleFieldAdded,
    StyleFieldRemoved,
    StyleCreated,
    StyleDuplicated,
    StyleRenamed,
    StyleDeleted,
    ResourceFieldChanged,
}

/**
 * One UI-oriented edit record.
 *
 * This is not yet a persistence diff or undo command; structured values are
 * retained so a future writer can reason about changes without parsing text.
 */
data class SkinEditChange(
    val type: SkinEditChangeType,
    val description: String,
    val styleKey: StyleKey? = null,
    val resourceKey: SkinResourceKey? = null,
    val fieldName: String? = null,
    val oldValue: String? = null,
    val newValue: String? = null,
)

/**
 * Stable writer-ready projection of the active edit state.
 *
 * Deleted entries are excluded; lists are sorted by stable keys and contain no
 * ImGui buffers, GDX objects, or file-writing behavior.
 */
data class SkinEditedSnapshot(
    val styles: List<EditableStyle>,
    val deletedStyles: List<EditableStyle>,
    val resources: List<EditableResource>,
    val deletedResources: List<EditableResource>,
    val changes: List<SkinEditChange>,
    val dirty: Boolean,
)

/** Creates a fresh edit session from an immutable [SkinLoadResult]. */
object SkinEditSessionFactory {
    fun create(loadResult: SkinLoadResult): SkinEditSession =
        SkinEditSession(
            baseStyleIndex = loadResult.styleIndex,
            baseResourceIndex = loadResult.resourceIndex,
            styles =
                loadResult.styleIndex.styles
                    .associateTo(linkedMapOf()) { style ->
                        style.key to
                            EditableStyle(
                                key = style.key,
                                displayName = style.displayName,
                                fields =
                                    style.fields.associateTo(linkedMapOf()) { field ->
                                        field.name to
                                            EditableStyleField(
                                                name = field.name,
                                                value = field.rawValue.orEmpty(),
                                                valueType = field.valueType,
                                                referenceCategory = field.reference?.category,
                                                isReference = field.reference != null,
                                            )
                                    },
                            )
                    },
            resources =
                loadResult.resourceIndex.resources
                    .associateTo(linkedMapOf()) { resource ->
                        resource.key to
                            EditableResource(
                                key = resource.key,
                                values = editableResourceValues(resource).toMutableMap(),
                                originalValues = editableResourceValues(resource),
                                resolved = resource.resolved,
                            )
                    },
        )

    private fun editableResourceValues(resource: SkinResourceInfo): Map<String, String> =
        when (resource.category) {
            SkinResourceCategory.Color ->
                buildMap {
                    resource.details["value"]?.let { put("value", it) }
                    resource.details["hex"]?.let { put("hex", it) }
                    listOf("r", "g", "b", "a").forEach { channel ->
                        resource.details[channel]?.let { put(channel, it) }
                    }
                }

            else -> emptyMap()
        }
}

data class KnownStyleField(
    val name: String,
    val referenceCategory: SkinResourceCategory? = null,
)

/** Known Scene2D style fields used only to assist style/field creation. */
object SkinStyleTemplates {
    val types: List<String> =
        listOf(
            "LabelStyle",
            "TextButtonStyle",
            "ButtonStyle",
            "CheckBoxStyle",
            "TextFieldStyle",
            "TextAreaStyle",
            "ListStyle",
            "SelectBoxStyle",
            "ScrollPaneStyle",
            "SliderStyle",
            "ProgressBarStyle",
            "WindowStyle",
        )

    private val fieldsByType: Map<String, List<KnownStyleField>> =
        mapOf(
            "LabelStyle" to listOf(font("font"), color("fontColor")),
            "TextButtonStyle" to
                listOf(
                    font("font"),
                    color("fontColor"),
                    drawable("up"),
                    drawable("down"),
                    drawable("over"),
                    drawable("disabled"),
                ),
            "ButtonStyle" to listOf(drawable("up"), drawable("down"), drawable("over"), drawable("disabled")),
            "CheckBoxStyle" to
                listOf(
                    drawable("checkboxOn"),
                    drawable("checkboxOff"),
                    font("font"),
                    color("fontColor"),
                ),
            "TextFieldStyle" to textFieldFields(),
            "TextAreaStyle" to textFieldFields(),
            "ListStyle" to
                listOf(
                    font("font"),
                    color("fontColorSelected"),
                    color("fontColorUnselected"),
                    drawable("selection"),
                ),
            "SelectBoxStyle" to
                listOf(
                    font("font"),
                    color("fontColor"),
                    drawable("background"),
                    KnownStyleField("scrollStyle"),
                    KnownStyleField("listStyle"),
                ),
            "ScrollPaneStyle" to
                listOf(
                    drawable("background"),
                    drawable("hScroll"),
                    drawable("hScrollKnob"),
                    drawable("vScroll"),
                    drawable("vScrollKnob"),
                ),
            "SliderStyle" to
                listOf(
                    drawable("background"),
                    drawable("knob"),
                    drawable("knobBefore"),
                    drawable("knobAfter"),
                ),
            "ProgressBarStyle" to
                listOf(
                    drawable("background"),
                    drawable("knob"),
                    drawable("knobBefore"),
                    drawable("knobAfter"),
                ),
            "WindowStyle" to listOf(font("titleFont"), color("titleFontColor"), drawable("background")),
        )

    fun fieldsFor(type: String): List<KnownStyleField> = fieldsByType[type].orEmpty()

    fun field(
        type: String,
        name: String,
    ): KnownStyleField? = fieldsFor(type).firstOrNull { field -> field.name.equals(name, ignoreCase = true) }

    private fun textFieldFields(): List<KnownStyleField> =
        listOf(
            font("font"),
            color("fontColor"),
            drawable("cursor"),
            drawable("selection"),
            drawable("background"),
        )

    private fun font(name: String) = KnownStyleField(name, SkinResourceCategory.Font)

    private fun color(name: String) = KnownStyleField(name, SkinResourceCategory.Color)

    private fun drawable(name: String) = KnownStyleField(name, SkinResourceCategory.Drawable)
}

fun SkinEditSession.activeStyles(): List<EditableStyle> =
    styles.values
        .filterNot(EditableStyle::deleted)
        .sortedWith(compareBy({ style -> style.key.type }, { style -> style.key.name }))

fun SkinEditSession.findEditableStyle(key: StyleKey?): EditableStyle? = key?.let(styles::get)?.takeUnless(EditableStyle::deleted)

/** Returns an immutable deep copy suitable for a future JSON writer or diff builder. */
/**
 * Returns the writer-ready edit projection.
 *
 * This snapshot is the intended input for JSON writer/diff generation.
 */
fun SkinEditSession.toEditedSnapshot(): SkinEditedSnapshot =
    SkinEditedSnapshot(
        styles =
            activeStyles().map { style ->
                style.copy(
                    fields = style.fields.mapValuesTo(linkedMapOf()) { (_, field) -> field.copy() },
                    modifiedFields = style.modifiedFields.toMutableSet(),
                    removedFields = style.removedFields.toMutableSet(),
                )
            },
        deletedStyles =
            styles.values
                .filter(EditableStyle::deleted)
                .sortedWith(compareBy({ style -> style.key.type }, { style -> style.key.name }))
                .map { style ->
                    style.copy(
                        fields = style.fields.mapValuesTo(linkedMapOf()) { (_, field) -> field.copy() },
                        modifiedFields = style.modifiedFields.toMutableSet(),
                        removedFields = style.removedFields.toMutableSet(),
                    )
                },
        resources =
            resources.values
                .filterNot(EditableResource::deleted)
                .sortedWith(compareBy({ resource -> resource.key.category.name }, { resource -> resource.key.name }))
                .map { resource ->
                    resource.copy(
                        values = resource.values.toMutableMap(),
                        originalValues = resource.originalValues.toMap(),
                        modifiedFields = resource.modifiedFields.toMutableSet(),
                    )
                },
        deletedResources =
            resources.values
                .filter(EditableResource::deleted)
                .sortedWith(compareBy({ resource -> resource.key.category.name }, { resource -> resource.key.name }))
                .map { resource ->
                    resource.copy(
                        values = resource.values.toMutableMap(),
                        originalValues = resource.originalValues.toMap(),
                        modifiedFields = resource.modifiedFields.toMutableSet(),
                    )
                },
        changes = changes.toList(),
        dirty = dirty,
    )
