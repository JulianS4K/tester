package com.trakt.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** Trakt-flavoured dark theme for the 10-foot UI. */
private val TraktColors = darkColorScheme(
    primary = Color(0xFFED1C24),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF3A0E10),
    onPrimaryContainer = Color(0xFFFFDAD8),
    secondary = Color(0xFF9AA0A6),
    background = Color(0xFF0B0B0F),
    onBackground = Color(0xFFECECEE),
    surface = Color(0xFF141419),
    onSurface = Color(0xFFECECEE),
    surfaceVariant = Color(0xFF1E1E26),
    onSurfaceVariant = Color(0xFFB9BCC4),
    border = Color(0xFF2A2A33),
)

@Composable
fun TraktTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TraktColors, content = content)
}
