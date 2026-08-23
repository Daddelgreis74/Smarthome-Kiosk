package com.example.smarthomekiosk.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AuroraCyan,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF003840),
    onPrimaryContainer = AuroraCyan,
    secondary = AuroraPurple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF38155A),
    onSecondaryContainer = AuroraPurple,
    tertiary = AuroraEmerald,
    onTertiary = Color.Black,
    background = AuroraDarkBg,
    onBackground = AuroraTextPrimary,
    surface = AuroraCardBg,
    onSurface = AuroraTextPrimary,
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = AuroraTextMuted,
    outline = AuroraCardBorder,
    error = AuroraError,
    onError = Color.White
)

@Composable
fun SmarthomeKioskTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
