package com.blendervcam.controller.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BlenderOrange = Color(0xFFF5792A)
val RecRed = Color(0xFFE5484D)
val PanelDark = Color(0xEE1B1B1D)
val HudDark = Color(0x99000000)

private val Scheme = darkColorScheme(
    primary = BlenderOrange,
    onPrimary = Color(0xFF1B1B1D),
    secondary = Color(0xFF7FB2E5),
    background = Color(0xFF151517),
    onBackground = Color(0xFFECECEC),
    surface = Color(0xFF1F1F22),
    onSurface = Color(0xFFECECEC),
    surfaceVariant = Color(0xFF2B2B2F),
    onSurfaceVariant = Color(0xFFB4B4BA),
    error = RecRed,
)

@Composable
fun VCamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
