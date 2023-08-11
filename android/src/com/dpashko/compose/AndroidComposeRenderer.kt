package com.dpashko.compose

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Constraints
import com.dpashko.krender.compose.ComposeRenderer

class AndroidComposeRenderer(context: Context) : ComposeRenderer() {

    internal val composeView = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.Default)
    }

    override fun setContent(content: @Composable () -> Unit) {
        composeView.setContent {
            MaterialTheme {
                Layout(
                    content = content,
                    measurePolicy = { measurables, constraints ->
                        measure(
                            measurables,
                            constraints
                        )
                    })
            }
        }
    }

    override fun render() {
        // Don't need to update each frame since Compose Scene Update automatically.
    }

    override fun dispose() {
        composeView.disposeComposition()
    }

    private fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints
    ): MeasureResult {
        return when {
            measurables.isEmpty() -> {
                layout(constraints.minWidth, constraints.minHeight) {}
            }

            measurables.size == 1 -> {
                val placeable = measurables[0].measure(constraints)
                layout((constraints.maxWidth), (constraints.maxHeight)) {
                    placeable.placeRelativeWithLayer(0, 0)
                }
            }

            else -> {
                val placeables = measurables.map {
                    it.measure(constraints)
                }
                layout(
                    (constraints.maxWidth),
                    (constraints.maxHeight)
                ) {
                    placeables.forEach { placeable ->
                        placeable.placeRelativeWithLayer(0, 0)
                    }
                }
            }
        }
    }
}