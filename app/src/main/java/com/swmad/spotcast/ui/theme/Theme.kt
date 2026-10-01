package com.swmad.spotcast.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Green80,
    secondary = Sand80,
    tertiary = Sky80
)

private val LightColorScheme = lightColorScheme(
    primary = Green40,
    primaryContainer = GreenContainer,
    secondary = Sand40,
    secondaryContainer = SandContainer,
    tertiary = Sky40
)

// Dynamic colouring is left out on purpose so the app keeps its own colours (lesson 1).
@Composable
fun SpotCastTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
