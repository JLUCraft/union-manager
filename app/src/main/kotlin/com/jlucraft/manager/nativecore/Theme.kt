package com.jlucraft.manager.nativecore

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JluBlue = Color(0xFF16457E)
private val JluBlueContainer = Color(0xFFD7E4F5)
private val JluGreen = Color(0xFF2E6E52)
private val JluGreenContainer = Color(0xFFD9EBE1)

private val ManagerLightColorScheme = lightColorScheme(
    primary = JluBlue,
    onPrimary = Color.White,
    primaryContainer = JluBlueContainer,
    onPrimaryContainer = Color(0xFF001B36),
    secondary = JluGreen,
    onSecondary = Color.White,
    secondaryContainer = JluGreenContainer,
    onSecondaryContainer = Color(0xFF0B3B28),
    tertiary = Color(0xFF3A6B8F),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF1A1F29),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF1A1F29),
    surfaceVariant = Color(0xFFE2E7F0),
    onSurfaceVariant = Color(0xFF475061),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEFF2F7),
    surfaceContainerHigh = Color(0xFFE9EDF4),
    outline = Color(0xFF8A93A6),
    outlineVariant = Color(0xFFC9D1DE),
)

private val ManagerDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062B52),
    primaryContainer = Color(0xFF1E4670),
    onPrimaryContainer = Color(0xFFD7E4F5),
    secondary = Color(0xFF94D6B4),
    onSecondary = Color(0xFF0B3B28),
    secondaryContainer = Color(0xFF23513E),
    onSecondaryContainer = Color(0xFFD9EBE1),
    tertiary = Color(0xFF9CC3E0),
    background = Color(0xFF10141B),
    onBackground = Color(0xFFE0E5ED),
    surface = Color(0xFF10141B),
    onSurface = Color(0xFFE0E5ED),
    surfaceVariant = Color(0xFF39424F),
    onSurfaceVariant = Color(0xFFC2CAD7),
    surfaceContainerLowest = Color(0xFF0B0F14),
    surfaceContainerLow = Color(0xFF181D26),
    surfaceContainer = Color(0xFF1D232D),
    surfaceContainerHigh = Color(0xFF272E39),
    outline = Color(0xFF8B95A6),
    outlineVariant = Color(0xFF3A4351),
)

@Composable
fun JluManagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) ManagerDarkColorScheme else ManagerLightColorScheme,
        content = content,
    )
}
