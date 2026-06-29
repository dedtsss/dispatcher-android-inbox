package ru.bruce.dispatcherinbox.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF4058A8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE1FF),
    surface = Color(0xFFF9F9FC),
    surfaceVariant = Color(0xFFE3E3E9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB7C4FF),
    primaryContainer = Color(0xFF293F8B),
    surface = Color(0xFF121318),
)

@Composable
fun DispatcherInboxTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
