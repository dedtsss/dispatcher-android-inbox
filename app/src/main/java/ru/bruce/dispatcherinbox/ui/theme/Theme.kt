package ru.bruce.dispatcherinbox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val CatchingColors = darkColorScheme(
    primary = Color(0xFF705C8A),
    onPrimary = Color(0xFFF4F0F8),
    primaryContainer = Color(0xFF2D263B),
    onPrimaryContainer = Color(0xFFD6CFDF),
    onSecondary = Color(0xFF191A20),
    tertiary = Color(0xFFB7B7C0),
    secondary = Color(0xFFAAA4B1),
    secondaryContainer = Color(0xFF2A2831),
    onSecondaryContainer = Color(0xFFE4E0EB),
    background = Color(0xFF121318),
    surface = Color(0xFF191A20),
    surfaceVariant = Color(0xFF24252C),
    surfaceContainer = Color(0xFF202128),
    onSurface = Color(0xFFECECEF),
    onSurfaceVariant = Color(0xFFB7B7C0),
    outline = Color(0xFF73727E),
    outlineVariant = Color(0xFF393943),
)

@Composable
fun DispatcherInboxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CatchingColors,
        shapes = Shapes(medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)),
        content = content,
    )
}

// Акценты утверждённого полноэкранного экрана записи; компактный ассистент использует прежнюю тему.
internal val CaptureAccent = Color(0xFFB68AF6)
internal val CaptureButtonTop = Color(0xFF8754D4)
internal val CaptureButtonBottom = Color(0xFF35215E)
