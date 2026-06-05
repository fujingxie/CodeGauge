package com.codegauge.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF7A4D),
    secondary = Color(0xFFFFD66E),
    background = Color(0xFF0E1118),
    surface = Color(0xFF151A24),
    onPrimary = Color(0xFF221006),
    onSecondary = Color(0xFF201600),
    onBackground = Color(0xFFE8ECF2),
    onSurface = Color(0xFFE8ECF2),
    onSurfaceVariant = Color(0xFF9EA8B8),
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
