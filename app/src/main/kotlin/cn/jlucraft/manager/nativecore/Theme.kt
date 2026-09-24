package cn.jlucraft.manager.nativecore

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
)

@Composable
fun JluManagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) ManagerDarkColorScheme else ManagerLightColorScheme,
        content = content,
    )
}
