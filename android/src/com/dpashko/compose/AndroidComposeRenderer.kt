package com.dpashko.compose

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.dpashko.krender.compose.ComposeRenderer

class AndroidComposeRenderer(context: Context) : ComposeRenderer() {

    internal val composeView = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.Default)
    }

    override fun setContent(content: @Composable () -> Unit) {
        composeView.setContent {
            content()
        }
    }

    override fun render() {
        // Don't need to update each frame since Compose Scene Update automatically.
    }

    override fun dispose() {
        composeView.disposeComposition()
    }
}