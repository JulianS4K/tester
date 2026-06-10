package com.helm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue = Color(0xFF5FA8FF)
private val BlueDark = Color(0xFF2E6FD6)
private val Teal = Color(0xFF18C29C)
private val Amber = Color(0xFFF6B73C)
private val Danger = Color(0xFFFF5470)

private val DarkColors = darkColorScheme(
    primary = Blue,
    onPrimary = Color(0xFF06121F),
    secondary = Teal,
    onSecondary = Color(0xFF002019),
    tertiary = Amber,
    error = Danger,
    background = Color(0xFF0E1220),
    onBackground = Color(0xFFE7ECF7),
    surface = Color(0xFF161C2E),
    onSurface = Color(0xFFE7ECF7),
    surfaceVariant = Color(0xFF222B42),
    onSurfaceVariant = Color(0xFF9FACC9),
    outline = Color(0xFF34405E),
)

private val LightColors = lightColorScheme(
    primary = BlueDark,
    secondary = Teal,
    tertiary = Amber,
    error = Danger,
    background = Color(0xFFF6F8FC),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun HelmTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
