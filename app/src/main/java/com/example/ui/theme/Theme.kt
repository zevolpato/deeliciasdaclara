package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = ChocolateBrown,
    onPrimary = Color.White,
    secondary = CaramelAmber,
    onSecondary = Color.White,
    tertiary = SweetPink,
    onTertiary = Color.White,
    background = SoftCreamBg,
    onBackground = TextDarkCocoa,
    surface = CreamySurface,
    onSurface = TextDarkCocoa,
    error = AlertRed,
    onError = Color.White,
    surfaceVariant = WarmVanilla,
    onSurfaceVariant = TextDarkCocoa,
    outline = TextLightBrown
)

private val DarkColorScheme = darkColorScheme(
    primary = CaramelAmber,
    onPrimary = TextDarkCocoa,
    secondary = ChocolateBrown,
    onSecondary = Color.White,
    tertiary = SweetPink,
    onTertiary = Color.White,
    background = Color(0xFF1E110A), // Espresso escuro
    onBackground = SoftCreamBg,
    surface = Color(0xFF2D1B13),     // Cacau escuro
    onSurface = SoftCreamBg,
    error = AlertRed,
    onError = Color.White,
    surfaceVariant = ChocolateBrown,
    onSurfaceVariant = SoftCreamBg,
    outline = TextLightBrown
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
