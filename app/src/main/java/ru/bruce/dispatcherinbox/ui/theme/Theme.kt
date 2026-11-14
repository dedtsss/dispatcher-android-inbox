package ru.bruce.dispatcherinbox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DispatcherBackground = Color(0xFF080B12)
val DispatcherSurface = Color(0xFF10151F)
val DispatcherSurfaceRaised = Color(0xFF171D29)
val DispatcherOutline = Color(0xFF34445F)
val DispatcherBlue = Color(0xFF6AA7FF)
val DispatcherSuccess = Color(0xFF55C98A)
val DispatcherWarning = Color(0xFFF0C45A)
val DispatcherError = Color(0xFFFF6B78)

private val DispatcherDarkColors = darkColorScheme(
    primary = DispatcherBlue,
    onPrimary = Color(0xFF031A33),
    primaryContainer = Color(0xFF17375D),
    onPrimaryContainer = Color(0xFFD8E9FF),
    secondary = Color(0xFFB8C7DD),
    onSecondary = Color(0xFF223143),
    secondaryContainer = Color(0xFF293747),
    onSecondaryContainer = Color(0xFFD4E3F9),
    tertiary = DispatcherWarning,
    onTertiary = Color(0xFF3C2F00),
    tertiaryContainer = Color(0xFF554500),
    onTertiaryContainer = Color(0xFFFFE17D),
    error = DispatcherError,
    onError = Color(0xFF3F0010),
    errorContainer = Color(0xFF5B1722),
    onErrorContainer = Color(0xFFFFDADD),
    background = DispatcherBackground,
    onBackground = Color(0xFFF2F5FA),
    surface = DispatcherSurface,
    onSurface = Color(0xFFF2F5FA),
    surfaceVariant = DispatcherSurfaceRaised,
    onSurfaceVariant = Color(0xFFAEB9C9),
    outline = DispatcherOutline,
    outlineVariant = Color(0xFF252F40),
    scrim = Color.Black,
)

@Composable
fun DispatcherInboxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DispatcherDarkColors,
        content = content,
    )
}
