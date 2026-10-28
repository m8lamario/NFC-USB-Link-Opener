package it.nfcusb.linkopener.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue = Color(0xFF0B57D0)
private val BlueDark = Color(0xFF9ECAFF)
private val Green = Color(0xFF0F6E56)
private val GreenDark = Color(0xFF7DDBBE)

private val LightColors = lightColorScheme(
    primary = Blue,
    secondary = Green,
    tertiary = Color(0xFF6B5B00),
)

private val DarkColors = darkColorScheme(
    primary = BlueDark,
    secondary = GreenDark,
    tertiary = Color(0xFFE9C400),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
