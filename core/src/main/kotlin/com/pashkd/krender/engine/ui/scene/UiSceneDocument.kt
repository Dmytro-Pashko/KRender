package com.pashkd.krender.engine.ui.scene

/**
 * Serialized UI scene document used by KRender runtime UI and the future UI Composer.
 *
 * This shared UI-pipeline model is intentionally not a full Scene2D Actor serializer.
 * It describes a small, stable subset of widgets that KRender can build at runtime
 * and edit later in the UI Composer. The [skin] is a project-relative path to an
 * existing Scene2D Skin file; this phase does not support Skin editing or asset ids.
 */
data class UiSceneDocument(
    val schemaVersion: Int = CurrentSchemaVersion,
    val id: String,
    val skin: String,
    val bindings: List<UiSceneBindingDefinition> = emptyList(),
    val root: UiSceneNode,
) {
    companion object {
        /** Current `.krui` schema version understood by runtime and future editor code. */
        const val CurrentSchemaVersion = 1
    }
}

/**
 * Data type for one predefined UI scene binding.
 *
 * These definitions are editor-authored preview/data contracts stored in `.krui`.
 * Runtime binding remains string-key based; this type helps UiComposer present
 * expected values and default preview data without introducing expressions or a
 * gameplay data model.
 */
enum class UiSceneBindingType {
    Text,
    Number,
    Texture,
    Action,
}

/**
 * One predefined binding key for a `.krui` document.
 *
 * [defaultValue] is the editor-preview value saved with the scene so UiComposer
 * can rebuild previews consistently. Runtime systems may still provide their
 * own payload values for the same keys.
 */
data class UiSceneBindingDefinition(
    val key: String,
    val type: UiSceneBindingType,
    val defaultValue: String = "",
)

/**
 * Explicit Scene2D widget subset supported by the `.krui` MVP.
 *
 * This shared enum is used by both the runtime Scene2D builder and the future
 * UiComposerScene editor. KRender does not attempt arbitrary Actor serialization
 * in this phase; adding more widgets should be a deliberate schema change.
 */
enum class UiSceneNodeType {
    Stack,
    Table,
    Container,
    Label,
    TextButton,
    ProgressBar,
    Image,
    Space,
}

/**
 * Simple child flow direction for `.krui` Table nodes.
 *
 * This belongs to the shared `.krui` layout model. It intentionally keeps the
 * Table MVP small: no per-cell config, no colspan/rowspan, no expand/fill, no
 * wrapping, no grid/flex layout, and no absolute positioning.
 */
enum class UiSceneTableOrientation {
    Vertical,
    Horizontal,
}

/**
 * One generic `.krui` node used by runtime loading and the future UI Composer.
 *
 * The schema keeps JSON simple and editor-friendly while limiting behavior to a
 * small Scene2D subset. Styles and backgrounds are referenced by existing Skin
 * names only. Texture references are project-relative paths for now. This phase
 * has no Skin editing, no custom rendering, no editor UI, and no support for
 * arbitrary Scene2D Actor serialization.
 */
data class UiSceneNode(
    val id: String,
    val type: UiSceneNodeType,
    val visible: Boolean = true,
    val style: String? = null,
    val background: String? = null,
    val text: String? = null,
    val action: String? = null,
    val texture: String? = null,
    val scaling: UiSceneScaling = UiSceneScaling.Fit,
    val value: Float? = null,
    val valueBinding: String? = null,
    val min: Float = 0f,
    val max: Float = 1f,
    val step: Float = 0.01f,
    val width: Float? = null,
    val height: Float? = null,
    val align: UiSceneAlign? = null,
    val padding: UiSceneSpacing = UiSceneSpacing.zero(),
    val spacing: Float = 0f,
    /**
     * Child flow direction for Table nodes.
     *
     * Only used when [type] is [UiSceneNodeType.Table]. `Vertical` preserves the
     * original behavior where every child is added as a new Scene2D Table row.
     * `Horizontal` places all children in one row. Other node types ignore this
     * field.
     */
    val tableOrientation: UiSceneTableOrientation = UiSceneTableOrientation.Vertical,
    val children: List<UiSceneNode> = emptyList(),
)

/**
 * Backend-neutral alignment values stored in `.krui`.
 *
 * The GDX Scene2D runtime builder maps these values to LibGDX Align flags, while
 * the future UiComposerScene can use the same values without importing LibGDX.
 */
enum class UiSceneAlign {
    TopLeft,
    Top,
    TopRight,
    Left,
    Center,
    Right,
    BottomLeft,
    Bottom,
    BottomRight,
}

/**
 * Backend-neutral image scaling values stored in `.krui`.
 *
 * These values are shared by runtime loading and future editor preview code. The
 * GDX builder maps them to LibGDX Scaling values.
 */
enum class UiSceneScaling {
    Fit,
    Fill,
    Stretch,
    None,
}

/**
 * Backend-neutral spacing model used for `.krui` padding.
 *
 * The model exists in the shared UI pipeline so runtime and future UiComposerScene
 * code can agree on layout inputs while still letting Scene2D perform layout.
 */
data class UiSceneSpacing(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
) {
    companion object {
        /** Creates empty padding for nodes that do not specify spacing. */
        fun zero(): UiSceneSpacing = UiSceneSpacing()

        /** Creates uniform padding in the `.krui` shared spacing model. */
        fun all(value: Float): UiSceneSpacing =
            UiSceneSpacing(value, value, value, value)
    }
}
