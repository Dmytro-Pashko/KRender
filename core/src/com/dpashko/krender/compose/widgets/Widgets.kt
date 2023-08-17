package com.dpashko.krender.compose.widgets

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp

object Widgets {

    @Composable
    @Preview
    fun loadingWidget() {
        return Box(
            modifier = Modifier
                .fillMaxSize(), contentAlignment = Alignment.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .border(
                        width = Dp(2f),
                        color = Color.DarkGray,
                        RoundedCornerShape(size = Dp(10f))
                    )
                    .background(Color.LightGray, RoundedCornerShape(size = Dp(10f)))
                    .padding(
                        Dp(20f)
                    )
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(Dp(10f)))
                    Text("Loading...", style = TextStyle(color = Color.Gray))
                }
            }
        }
    }
}

