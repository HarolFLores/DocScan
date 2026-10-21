package com.example.docscan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = MintGreen,
    onPrimary = NavyDark,
    primaryContainer = NavyLight,
    onPrimaryContainer = Color.White,
    secondary = MintGreen,
    onSecondary = Color.White,
    background = NavyDark,
    surface = NavySurface,
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = NavyPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = Color.White,
    primaryContainer = NavyDark,
    onPrimaryContainer = Color.White,
    secondary = MintDark,
    onSecondary = Color.White,
    secondaryContainer = MintLight,
    onSecondaryContainer = MintText,
    tertiary = CardBlueText,
    tertiaryContainer = CardBlue,
    onTertiaryContainer = CardBlueText,
    background = LightBackground,
    surface = LightSurface,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = BorderStroke,
    error = RedDanger,
    errorContainer = RedSoft,
    onErrorContainer = RedText
)

@Composable
fun DocScanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // We intentionally enforce the brand styling from the design mockups
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}