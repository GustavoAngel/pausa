package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = FocusGreen,
    onPrimary = Color.Black,
    primaryContainer = FocusGreen,
    onPrimaryContainer = Color.Black,
    secondary = FocusGreenDark,
    onSecondary = Color.Black,
    tertiary = FocusGreen,
    onTertiary = Color.Black,
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = FocusGreen,
    onPrimary = Color.Black,
    primaryContainer = FocusGreen,
    onPrimaryContainer = Color.Black,
    secondary = FocusGreenDark,
    onSecondary = Color.Black,
    tertiary = FocusGreen,
    onTertiary = Color.Black
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
