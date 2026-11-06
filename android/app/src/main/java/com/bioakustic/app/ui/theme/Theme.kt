package com.bioakustic.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BioDarkColors = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF0B1F0E),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFF4DB6AC),
    tertiary = Color(0xFFFFB74D),
    background = Color(0xFF0E0E0E),
    surface = Color(0xFF161616),
    surfaceVariant = Color(0xFF222222),
    onBackground = Color(0xFFE8E8E8),
    onSurface = Color(0xFFE8E8E8),
    error = Color(0xFFE57373),
)

@Composable
fun BioAkusticTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BioDarkColors,
        content = content,
    )
}
