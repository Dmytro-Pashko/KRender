package com.dpashko.compose

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.dpashko.krender.compose.ComposeRenderer

class AndroidComposeRenderer(context: Context) : ComposeRenderer() {

    internal val composeView = ComposeView(context)

    override fun setContent(content: @Composable () -> Unit) {
        composeView.setContent {
            Surface(color = Color.Cyan) {
                Column {
                    Box(
                        modifier = Modifier
                            .size(200.dp, 100.dp) // Встановлює розмір 200x100
                            .background(Color.Blue)
                    ) {
                        Text(
                            text = "Hello Compose!",
                            color = Color.White,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }

    override fun render() {
    }

    override fun dispose() {
        composeView.disposeComposition()
    }
}