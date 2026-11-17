package com.cabeceira.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Paleta "papel e couro" — usada quando o aparelho não tem cores dinâmicas (Android < 12)
private val Light = lightColorScheme(
    primary = Color(0xFF7A4A24),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC2),
    onPrimaryContainer = Color(0xFF2E1500),
    secondary = Color(0xFF735945),
    secondaryContainer = Color(0xFFFFDCC4),
    onSecondaryContainer = Color(0xFF2A1708),
    background = Color(0xFFFFF8F4),
    surface = Color(0xFFFFF8F4),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB77C),
    onPrimary = Color(0xFF4A2800),
    primaryContainer = Color(0xFF5F3A16),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFFE2C0A7),
    secondaryContainer = Color(0xFF5A4230),
    onSecondaryContainer = Color(0xFFFFDCC4),
    background = Color(0xFF1A120D),
    surface = Color(0xFF1A120D),
)

@Composable
fun CabeceiraTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = colors, content = content)
}
