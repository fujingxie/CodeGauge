package com.codegauge.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE97857),
    secondary = Color(0xFF17C398),
    background = Color(0xFF090D13),
    surface = Color(0xFF121821),
    onPrimary = Color(0xFF221006),
    onSecondary = Color(0xFF041B14),
    onBackground = Color(0xFFE9EEF7),
    onSurface = Color(0xFFE9EEF7),
    onSurfaceVariant = Color(0xFF8A93A3),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFC84E2D),
    secondary = Color(0xFFAD7A00),
    background = Color(0xFFF4F5F7),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF161A22),
    onSurface = Color(0xFF161A22),
    onSurfaceVariant = Color(0xFF5A6270),
)

@Composable
fun CodeGaugeTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
