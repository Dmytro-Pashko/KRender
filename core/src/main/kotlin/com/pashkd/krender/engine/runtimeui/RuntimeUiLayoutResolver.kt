package com.pashkd.krender.engine.runtimeui

import com.pashkd.krender.engine.viewport.RuntimeViewport

/**
 * Resolves a [RuntimeUiDocument] into a tree of logical rectangles.
 *
 * The resolver is backend-neutral and works only with runtime UI data and
 * [RuntimeViewport] logical coordinates. It does not render nodes or handle input.
 *
 * MVP limitations:
 * - This is not a flexbox implementation.
 * - Fill inside rows and columns uses the full parent content axis and does not
 *   distribute remaining sibling space.
 * - Wrap uses approximate placeholder text sizing only.
 * - There is no real font measurement yet.
 * - There is no style, skin, or rendering backend yet.
 * - There is no input or hit testing yet.
 */
class RuntimeUiLayoutResolver {
    /**
     * Resolves the supplied document against the current logical viewport.
     *
     * The root node always occupies the full viewport logical rect.
     */
    fun resolve(
        document: RuntimeUiDocument,
        viewport: RuntimeViewport,
    ): RuntimeUiResolvedNode {
        val rootRect = UiRect(
            x = 0f,
            y = 0f,
            width = viewport.logicalWidth,
            height = viewport.logicalHeight,
        )
        return resolveRoot(document.root, rootRect)
    }

    private fun resolveRoot(
        root: RuntimeUiNode,
        rect: UiRect,
    ): RuntimeUiResolvedNode {
        if (!root.visible) {
            return RuntimeUiResolvedNode(
                id = root.id,
                type = root.type,
                rect = rect,
                node = root,
                children = emptyList(),
            )
        }

        return RuntimeUiResolvedNode(
            id = root.id,
            type = root.type,
            rect = rect,
            node = root,
            children = resolveChildren(root, rect),
        )
    }

    private fun resolveNode(
        node: RuntimeUiNode,
        rect: UiRect,
    ): RuntimeUiResolvedNode =
        RuntimeUiResolvedNode(
            id = node.id,
            type = node.type,
            rect = rect,
            node = node,
            children = resolveChildren(node, rect),
        )

    private fun resolveChildren(
        node: RuntimeUiNode,
        rect: UiRect,
    ): List<RuntimeUiResolvedNode> {
        if (node.children.isEmpty()) return emptyList()

        val visibleChildren = node.children.filter(RuntimeUiNode::visible)
        if (visibleChildren.isEmpty()) return emptyList()

        return when (node.type) {
            RuntimeUiNodeType.Row -> resolveRowChildren(node, rect, visibleChildren)
            RuntimeUiNodeType.Column -> resolveColumnChildren(node, rect, visibleChildren)
            RuntimeUiNodeType.Panel,
            RuntimeUiNodeType.Stack,
            -> resolveStackChildren(node, rect, visibleChildren)

            RuntimeUiNodeType.Text,
            RuntimeUiNodeType.Image,
            RuntimeUiNodeType.Button,
            RuntimeUiNodeType.ProgressBar,
            RuntimeUiNodeType.Spacer,
            -> emptyList()
        }
    }

    private fun resolveStackChildren(
        node: RuntimeUiNode,
        rect: UiRect,
        children: List<RuntimeUiNode>,
    ): List<RuntimeUiResolvedNode> {
        val parentContent = contentRect(rect, node.layout.padding)
        return children.map { child ->
            resolveNode(child, resolveAnchoredRect(child, parentContent))
        }
    }

    private fun resolveColumnChildren(
        node: RuntimeUiNode,
        rect: UiRect,
        children: List<RuntimeUiNode>,
    ): List<RuntimeUiResolvedNode> {
        val content = contentRect(rect, node.layout.padding)
        var cursorY = content.y
        return children.map { child ->
            val childRect = resolveFlowRect(
                node = child,
                parentContent = content,
                originX = content.x,
                originY = cursorY,
                flow = FlowDirection.Vertical,
            )
            cursorY = childRect.bottom + child.layout.margin.bottom + node.layout.gap
            resolveNode(child, childRect)
        }
    }

    private fun resolveRowChildren(
        node: RuntimeUiNode,
        rect: UiRect,
        children: List<RuntimeUiNode>,
    ): List<RuntimeUiResolvedNode> {
        val content = contentRect(rect, node.layout.padding)
        var cursorX = content.x
        return children.map { child ->
            val childRect = resolveFlowRect(
                node = child,
                parentContent = content,
                originX = cursorX,
                originY = content.y,
                flow = FlowDirection.Horizontal,
            )
            cursorX = childRect.right + child.layout.margin.right + node.layout.gap
            resolveNode(child, childRect)
        }
    }

    private fun resolveFlowRect(
        node: RuntimeUiNode,
        parentContent: UiRect,
        originX: Float,
        originY: Float,
        flow: FlowDirection,
    ): UiRect {
        val margin = node.layout.margin
        val availableWidth = (parentContent.width - margin.left - margin.right).coerceAtLeast(0f)
        val availableHeight = (parentContent.height - margin.top - margin.bottom).coerceAtLeast(0f)
        val width = resolveSize(node.layout.width, availableWidth, wrapWidth(node)).coerceAtLeast(0f)
        val height = resolveSize(node.layout.height, availableHeight, wrapHeight(node)).coerceAtLeast(0f)

        return when (flow) {
            FlowDirection.Vertical -> UiRect(
                x = resolveFlowX(node.layout.anchor, parentContent, margin, width),
                y = originY + margin.top,
                width = widthForFlow(node.layout.anchor, availableWidth, width, flow),
                height = height,
            )

            FlowDirection.Horizontal -> UiRect(
                x = originX + margin.left,
                y = resolveFlowY(node.layout.anchor, parentContent, margin, height),
                width = width,
                height = heightForFlow(node.layout.anchor, availableHeight, height, flow),
            )
        }
    }

    private fun widthForFlow(
        anchor: UiAnchor,
        availableWidth: Float,
        resolvedWidth: Float,
        flow: FlowDirection,
    ): Float =
        if (flow == FlowDirection.Vertical && anchor == UiAnchor.Stretch) availableWidth else resolvedWidth

    private fun heightForFlow(
        anchor: UiAnchor,
        availableHeight: Float,
        resolvedHeight: Float,
        flow: FlowDirection,
    ): Float =
        if (flow == FlowDirection.Horizontal && anchor == UiAnchor.Stretch) availableHeight else resolvedHeight

    private fun resolveFlowX(
        anchor: UiAnchor,
        parentContent: UiRect,
        margin: UiSpacing,
        width: Float,
    ): Float {
        val availableX = parentContent.x + margin.left
        val availableWidth = (parentContent.width - margin.left - margin.right).coerceAtLeast(0f)
        return when (anchor) {
            UiAnchor.TopCenter,
            UiAnchor.Center,
            UiAnchor.BottomCenter,
            -> availableX + (availableWidth - width) * 0.5f

            UiAnchor.TopRight,
            UiAnchor.CenterRight,
            UiAnchor.BottomRight,
            -> availableX + availableWidth - width

            else -> availableX
        }
    }

    private fun resolveFlowY(
        anchor: UiAnchor,
        parentContent: UiRect,
        margin: UiSpacing,
        height: Float,
    ): Float {
        val availableY = parentContent.y + margin.top
        val availableHeight = (parentContent.height - margin.top - margin.bottom).coerceAtLeast(0f)
        return when (anchor) {
            UiAnchor.CenterLeft,
            UiAnchor.Center,
            UiAnchor.CenterRight,
            -> availableY + (availableHeight - height) * 0.5f

            UiAnchor.BottomLeft,
            UiAnchor.BottomCenter,
            UiAnchor.BottomRight,
            -> availableY + availableHeight - height

            else -> availableY
        }
    }

    private fun resolveAnchoredRect(
        node: RuntimeUiNode,
        parentContent: UiRect,
    ): UiRect {
        val margin = node.layout.margin
        val availableX = parentContent.x + margin.left
        val availableY = parentContent.y + margin.top
        val availableWidth = (parentContent.width - margin.left - margin.right).coerceAtLeast(0f)
        val availableHeight = (parentContent.height - margin.top - margin.bottom).coerceAtLeast(0f)
        val width = resolveSize(node.layout.width, availableWidth, wrapWidth(node)).coerceAtLeast(0f)
        val height = resolveSize(node.layout.height, availableHeight, wrapHeight(node)).coerceAtLeast(0f)

        return when (node.layout.anchor) {
            UiAnchor.Stretch -> UiRect(
                x = availableX,
                y = availableY,
                width = availableWidth,
                height = availableHeight,
            )

            UiAnchor.TopLeft -> UiRect(availableX, availableY, width, height)
            UiAnchor.TopCenter -> UiRect(availableX + (availableWidth - width) * 0.5f, availableY, width, height)
            UiAnchor.TopRight -> UiRect(availableX + availableWidth - width, availableY, width, height)
            UiAnchor.CenterLeft -> UiRect(availableX, availableY + (availableHeight - height) * 0.5f, width, height)
            UiAnchor.Center -> UiRect(
                availableX + (availableWidth - width) * 0.5f,
                availableY + (availableHeight - height) * 0.5f,
                width,
                height,
            )

            UiAnchor.CenterRight -> UiRect(
                availableX + availableWidth - width,
                availableY + (availableHeight - height) * 0.5f,
                width,
                height,
            )

            UiAnchor.BottomLeft -> UiRect(availableX, availableY + availableHeight - height, width, height)
            UiAnchor.BottomCenter -> UiRect(
                availableX + (availableWidth - width) * 0.5f,
                availableY + availableHeight - height,
                width,
                height,
            )

            UiAnchor.BottomRight -> UiRect(
                availableX + availableWidth - width,
                availableY + availableHeight - height,
                width,
                height,
            )
        }
    }

    private fun contentRect(
        rect: UiRect,
        padding: UiSpacing,
    ): UiRect =
        UiRect(
            x = rect.x + padding.left,
            y = rect.y + padding.top,
            width = (rect.width - padding.left - padding.right).coerceAtLeast(0f),
            height = (rect.height - padding.top - padding.bottom).coerceAtLeast(0f),
        )

    private fun resolveSize(
        size: UiSizeValue,
        parentSize: Float,
        fallbackWrapSize: Float = 0f,
    ): Float =
        // MVP note:
        // Fill in Row/Column currently means "use the full parent content axis",
        // not "take remaining flex space". Flex distribution can be added later.
        when (size.mode) {
            UiSizeMode.Fixed -> size.value.coerceAtLeast(0f)
            UiSizeMode.Percent -> parentSize * size.value.coerceIn(0f, 1f)
            UiSizeMode.Wrap -> fallbackWrapSize.coerceAtLeast(0f)
            UiSizeMode.Fill -> parentSize.coerceAtLeast(0f)
        }

    private fun wrapWidth(node: RuntimeUiNode): Float =
        // MVP note: wrap uses a simple text-length heuristic and does not measure
        // actual fonts, glyphs, or image dimensions yet.
        if (node.type == RuntimeUiNodeType.Text) (node.text?.length ?: 0) * TextWrapCharWidth else 0f

    private fun wrapHeight(node: RuntimeUiNode): Float =
        if (node.type == RuntimeUiNodeType.Text) TextWrapHeight else 0f

    private enum class FlowDirection {
        Horizontal,
        Vertical,
    }

    companion object {
        /** Approximate per-character width used by the MVP wrap-content text heuristic. */
        private const val TextWrapCharWidth = 8f

        /** Approximate line height used by the MVP wrap-content text heuristic. */
        private const val TextWrapHeight = 24f
    }
}
