package com.dpashko.compose

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.badlogic.gdx.graphics.Color
import com.dpashko.krender.compose.ComposeRenderer

class AndroidComposeRenderer(context: Context) : ComposeRenderer() {

    companion object{
        internal const val uiScaleFactor = 0.6f
    }
    internal val composeView = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.Default)
    }

    override fun setContent(content: @Composable () -> Unit) {
        composeView.setContent {
            Box(
                modifier = Modifier
                    .background(color = androidx.compose.ui.graphics.Color.Red)
                    .fillMaxSize(),
                Alignment.TopStart
            ) {
                Box(

                    modifier = Modifier
                        .background(color = androidx.compose.ui.graphics.Color.Cyan)
                        .graphicsLayer(
                            scaleX = uiScaleFactor,
                            scaleY = uiScaleFactor,
                            transformOrigin = TransformOrigin(0.0f, 0.0f),
                        )
                        .align(Alignment.TopStart)
                ) {
                    content()
                }
            }
        }
    }

    override fun render() {
        // Don't need to update each frame since Compose Scene Update automatically.
    }

    override fun dispose() {
        composeView.disposeComposition()
    }
}