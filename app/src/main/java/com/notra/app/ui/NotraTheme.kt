package com.notra.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object NotraColors {
    val Background = Color(0xFF171716)
    val Canvas = Color(0xFF1D1D1B)
    val Surface = Color(0xFF262624)
    val Elevated = Color(0xFF30302E)
    val Border = Color(0xFF464643)
    val Accent = Color(0xFFA6ADD9)
    val Text = Color(0xFFF1F0E9)
    val Muted = Color(0xFFAAA9A2)
}

private val palette = darkColorScheme(
    primary = NotraColors.Accent,
    onPrimary = NotraColors.Background,
    background = NotraColors.Background,
    onBackground = NotraColors.Text,
    surface = NotraColors.Surface,
    onSurface = NotraColors.Text,
    surfaceVariant = NotraColors.Elevated,
    onSurfaceVariant = NotraColors.Muted,
    outline = NotraColors.Border
)

@Composable
fun NotraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = palette, content = content)
}
