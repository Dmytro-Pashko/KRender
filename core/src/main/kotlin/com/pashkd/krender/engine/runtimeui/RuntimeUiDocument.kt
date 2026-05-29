package com.pashkd.krender.engine.runtimeui

/**
 * Immutable runtime UI document resolved as a single logical tree.
 *
 * A document is the unit registered in [RuntimeUiSystem]. It contains one root node
 * and may later be loaded from assets or constructed directly in Kotlin.
 */
data class RuntimeUiDocument(
    /** Stable document identifier used for registration and layer activation. */
    val id: String,
    /** Root node of the runtime UI hierarchy. */
    val root: RuntimeUiNode,
)

/**
 * Generic runtime UI node used by the MVP layout system.
 *
 * The model stays intentionally data-oriented: rendering, hit testing, and styling are
 * out of scope here, so textual, image, and action fields are only metadata for later
 * systems.
 */
data class RuntimeUiNode(
    /** Stable node identifier inside the document. */
    val id: String,
    /** High-level semantic node type that drives layout behavior. */
    val type: RuntimeUiNodeType = RuntimeUiNodeType.Panel,
    /** Whether the node participates in resolved layout output. */
    val visible: Boolean = true,
    /** Layout rules used when resolving this node inside its parent. */
    val layout: RuntimeUiLayout = RuntimeUiLayout(),
    /** Optional text payload for text-like nodes. */
    val text: String? = null,
    /** Optional image reference for image-like nodes. */
    val image: String? = null,
    /** Optional action identifier for interactive nodes. */
    val action: String? = null,
    /** Child nodes resolved inside this node according to [type]. */
    val children: List<RuntimeUiNode> = emptyList(),
)

/**
 * Semantic runtime UI node categories understood by the layout resolver.
 */
enum class RuntimeUiNodeType {
    /** Generic framed container that resolves children like a stack. */
    Panel,

    /** Container that resolves each visible child independently inside the same content rect. */
    Stack,

    /** Horizontal flow container that places visible children from left to right. */
    Row,

    /** Vertical flow container that places visible children from top to bottom. */
    Column,

    /** Leaf node representing text content. */
    Text,

    /** Leaf node representing image content. */
    Image,

    /** Leaf node representing button intent without input handling yet. */
    Button,

    /** Leaf node representing progress-like content. */
    ProgressBar,

    /** Leaf node used for empty space in flow layouts. */
    Spacer,
}
