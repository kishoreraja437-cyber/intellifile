package com.simats.intellifile.ui.theme

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

// Dark Scheme
private val DarkColorScheme = darkColorScheme(
    primary = AppPrimaryDark,          // #7E52FF
    secondary = AppSecondaryDark,      // #6332E0
    tertiary = Color(0xFF9382FE),       // Light Purple
    background = AppBackgroundDark,    // #0C0E2D
    surface = AppSurfaceDark,          // #161942
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,        // Pure White Text
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1F2253), // Container
    onSurfaceVariant = Color(0xFF9382FE)
)

// Main / Light Scheme (Midnight Blue & Indigo Purple Theme)
private val LightColorScheme = lightColorScheme(
    primary = AppPrimary,              // #6332E0 Vibrant Purple/Indigo
    secondary = AppSecondary,          // #5B3CC4 Deep Indigo
    tertiary = Color(0xFF7E52FF),       // Light Indigo Accent
    background = AppBackgroundLight,   // #11133C Deep Midnight Navy Background
    surface = AppSurfaceLight,         // #1C1F4E Midnight Blue Card Surface
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,        // Pure White Text
    onSurface = Color.White,
    surfaceVariant = AppContainerLight, // #252862 Soft Midnight Blue Container
    onSurfaceVariant = Color(0xFF9382FE) // Light Purple Accent
)

@Composable
fun IntellifileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
