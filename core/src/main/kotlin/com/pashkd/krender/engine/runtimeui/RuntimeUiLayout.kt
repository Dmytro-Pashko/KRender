package com.pashkd.krender.engine.runtimeui

/**
 * Alignment preset used to place a node inside its parent content rect.
 */
enum class UiAnchor {
    /** Places the node at the top-left corner of the available parent content rect. */
    TopLeft,

    /** Places the node at the top edge and centers it horizontally. */
    TopCenter,

    /** Places the node at the top-right corner of the available parent content rect. */
    TopRight,

    /** Places the node at the left edge and centers it vertically. */
    CenterLeft,

    /** Centers the node horizontally and vertically inside the available parent content rect. */
    Center,

    /** Places the node at the right edge and centers it vertically. */
    CenterRight,

    /** Places the node at the bottom-left corner of the available parent content rect. */
    BottomLeft,

    /** Places the node at the bottom edge and centers it horizontally. */
    BottomCenter,

    /** Places the node at the bottom-right corner of the available parent content rect. */
    BottomRight,

    /** Expands the node to occupy the full available parent content rect. */
    Stretch,
}

/**
 * Describes how a width or height value should be interpreted during layout resolution.
 */
enum class UiSizeMode {
    /** Uses the literal logical size stored in [UiSizeValue.value]. */
    Fixed,

    /** Uses [UiSizeValue.value] as a 0..1 fraction of the parent content size. */
    Percent,

    /** Uses a content-based fallback size estimated by the resolver. */
    Wrap,

    /** Expands to the full parent content size for the resolved axis. */
    Fill,
}

/**
 * JSON-friendly size descriptor for a single layout axis.
 */
data class UiSizeValue(
    /** Strategy used to resolve the final logical size. */
    val mode: UiSizeMode = UiSizeMode.Fixed,
    /** Literal size or normalized percent depending on [mode]. */
    val value: Float = 0f,
) {
    companion object {
        /** Creates a fixed logical size. */
        fun fixed(value: Float): UiSizeValue = UiSizeValue(UiSizeMode.Fixed, value)

        /** Creates a percentage-based size relative to the parent content rect. */
        fun percent(value: Float): UiSizeValue = UiSizeValue(UiSizeMode.Percent, value)

        /** Creates a wrap-content size. */
        fun wrap(): UiSizeValue = UiSizeValue(UiSizeMode.Wrap, 0f)

        /** Creates a size that fills the parent content rect on its axis. */
        fun fill(): UiSizeValue = UiSizeValue(UiSizeMode.Fill, 0f)
    }
}

/**
 * Logical spacing values used for margins and padding.
 */
data class UiSpacing(
    /** Left spacing in logical UI units. */
    val left: Float = 0f,
    /** Top spacing in logical UI units. */
    val top: Float = 0f,
    /** Right spacing in logical UI units. */
    val right: Float = 0f,
    /** Bottom spacing in logical UI units. */
    val bottom: Float = 0f,
) {
    companion object {
        /** Returns zero spacing on every side. */
        fun zero(): UiSpacing = UiSpacing()

        /** Returns uniform spacing on every side. */
        fun all(value: Float): UiSpacing = UiSpacing(value, value, value, value)
    }
}

/**
 * Layout rules attached to a [RuntimeUiNode].
 */
data class RuntimeUiLayout(
    /** Anchor used by stack-like containers when placing this node. */
    val anchor: UiAnchor = UiAnchor.TopLeft,
    /** Width resolution rule for this node. */
    val width: UiSizeValue = UiSizeValue.fixed(0f),
    /** Height resolution rule for this node. */
    val height: UiSizeValue = UiSizeValue.fixed(0f),
    /** External spacing applied between this node and its parent content rect. */
    val margin: UiSpacing = UiSpacing.zero(),
    /** Internal spacing applied before laying out this node's children. */
    val padding: UiSpacing = UiSpacing.zero(),
    /** Gap inserted between visible children in row and column containers. */
    val gap: Float = 0f,
)

/**
 * Resolved logical rectangle used by runtime UI layout.
 */
data class UiRect(
    /** Left edge in logical UI units. */
    val x: Float,
    /** Top edge in logical UI units. */
    val y: Float,
    /** Width in logical UI units. */
    val width: Float,
    /** Height in logical UI units. */
    val height: Float,
) {
    /** Right edge in logical UI units. */
    val right: Float
        get() = x + width

    /** Bottom edge in logical UI units. */
    val bottom: Float
        get() = y + height
}

/**
 * Immutable resolved node tree produced by [RuntimeUiLayoutResolver].
 */
data class RuntimeUiResolvedNode(
    /** Node identifier copied from the source document. */
    val id: String,
    /** Node type copied from the source document. */
    val type: RuntimeUiNodeType,
    /** Final resolved logical rectangle for the node. */
    val rect: UiRect,
    /** Original source node. */
    val node: RuntimeUiNode,
    /** Resolved visible children. */
    val children: List<RuntimeUiResolvedNode>,
)
