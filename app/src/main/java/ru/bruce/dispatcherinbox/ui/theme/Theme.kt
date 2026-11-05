package ru.bruce.dispatcherinbox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val CatchingColors = darkColorScheme(
    primary = Color(0xFFC4B5E4),
    onPrimary = Color(0xFF292334),
    primaryContainer = Color(0xFF393044),
    onPrimaryContainer = Color(0xFFE7DBFA),
    secondary = Color(0xFFC7C1D1),
    secondaryContainer = Color(0xFF302E36),
    onSecondaryContainer = Color(0xFFE4DFEC),
    background = Color(0xFF15151A),
    surface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFF292930),
    surfaceContainer = Color(0xFF222229),
    onSurface = Color(0xFFF1EDF6),
    onSurfaceVariant = Color(0xFFCBC5D2),
    outline = Color(0xFF948D9D),
    outlineVariant = Color(0xFF45404C),
)

@Composable
fun DispatcherInboxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CatchingColors,
        shapes = Shapes(medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)),
        content = content,
    )
}
