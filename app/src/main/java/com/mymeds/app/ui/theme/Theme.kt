package com.mymeds.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand colors matching the web app's indigo primary scheme
val Primary = Color(0xFF4F46E5)
val OnPrimary = Color.White
val PrimaryContainer = Color(0xFFE0E7FF)
val Secondary = Color(0xFF7C3AED)
val Success = Color(0xFF22C55E)
val Warning = Color(0xFFF59E0B)
val Danger = Color(0xFFEF4444)
val Surface = Color(0xFFF8FAFC)
val Background = Color(0xFFF1F5F9)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    secondary = Secondary,
    error = Danger,
    surface = Surface,
    background = Background,
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF3730A3),
    secondary = Color(0xFFA78BFA),
    error = Color(0xFFFCA5A5),
    surface = Color(0xFF1E293B),
    background = Color(0xFF0F172A),
)

private val AppTypography = Typography()

@Composable
fun MyMedsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
