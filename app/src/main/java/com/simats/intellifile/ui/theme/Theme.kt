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

// Dark Theme Scheme (Black & Blue Theme)
private val DarkColorScheme = darkColorScheme(
    primary = AppPrimaryDark,          // #3B82F6 Electric Royal Blue
    secondary = AppSecondaryDark,      // #2563EB Royal Blue
    tertiary = Color(0xFF60A5FA),       // Light Blue Accent
    background = AppBackgroundDark,    // #000000 Pure Black Background
    surface = AppSurfaceDark,          // #0F172A Dark Blue Card Surface
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = AppTextLight,       // #F8FAFC White Text
    onSurface = AppTextLight,
    surfaceVariant = Color(0xFF0F172A), // Dark Blue Container
    onSurfaceVariant = Color(0xFF60A5FA) // Light Blue Accent Text/Icon
)

// Light Theme Scheme (Red & White Theme)
private val LightColorScheme = lightColorScheme(
    primary = AppPrimary,              // #EF4444 Vibrant Red
    secondary = AppSecondary,          // #DC2626 Deep Red
    tertiary = Color(0xFFF87171),       // Soft Red Accent
    background = AppBackgroundLight,   // #FFFFFF Pure White Background
    surface = AppSurfaceLight,         // #FFFFFF Pure White Card Surface
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = AppTextDark,        // #111827 Dark Text
    onSurface = AppTextDark,
    surfaceVariant = AppContainerLight, // #FFF0F2 Soft Light Red Container
    onSurfaceVariant = AppPrimary      // #EF4444 Red Accent Text/Icon
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
