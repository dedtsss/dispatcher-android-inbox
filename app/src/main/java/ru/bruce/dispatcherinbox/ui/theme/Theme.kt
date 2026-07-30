package ru.bruce.dispatcherinbox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8ED7FF),
    onPrimary = Color(0xFF06243A),
    primaryContainer = Color(0xFF174667),
    secondary = Color(0xFFD0BBFF),
    secondaryContainer = Color(0xFF40346F),
    tertiary = Color(0xFFC6B8FF),
    tertiaryContainer = Color(0xFF4B3D83),
    surface = Color(0xFF0B0D14),
    surfaceVariant = Color(0xFF191C27),
    surfaceContainerHigh = Color(0xFF1B1E29),
    onSurface = Color(0xFFE4E5EC),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outlineVariant = Color(0xFF444853),
)

@Composable
fun DispatcherInboxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
