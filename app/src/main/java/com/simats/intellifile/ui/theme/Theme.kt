package com.simats.intellifile.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

// Dark Scheme
private val DarkColorScheme = darkColorScheme(
    primary = AppPrimaryDark,          // #88C7CB Light Dusty Aqua
    secondary = AppSecondaryDark,      // #6FBEC3 Dusty Aqua
    tertiary = Color(0xFF26E07F),       // #26E07F Mint Green Accent
    background = AppBackgroundDark,    // #112A2C Dark Ocean Teal
    surface = AppSurfaceDark,          // #1B3D40 Dark Aqua Surface
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = AppTextLight,       // Light Text
    onSurface = AppTextLight,
    surfaceVariant = Color(0xFF275256), // Dark Container
    onSurfaceVariant = Color(0xFF88C7CB)
)

// Main / Light Scheme (Dusty Aqua & Mint Green Theme)
private val LightColorScheme = lightColorScheme(
    primary = AppPrimary,              // #55A9AF Dusty Aqua
    secondary = AppSecondary,          // #6FBEC3 Light Aqua
    tertiary = Color(0xFF26E07F),       // #26E07F Vibrant Mint Green Accent
    background = AppBackgroundLight,   // #EFF8F8 Soft Light Aqua Background
    surface = AppSurfaceLight,         // #FFFFFF Pure White Surface
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = AppTextDark,        // #183A3C Deep Ocean Teal Text
    onSurface = AppTextDark,
    surfaceVariant = AppContainerLight, // #D5ECEE Soft Dusty Aqua Container
    onSurfaceVariant = AppPrimary      // #55A9AF Dusty Aqua Accent Text/Icon
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

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.show(WindowInsetsCompat.Type.statusBars())
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
                @Suppress("DEPRECATION")
                window.statusBarColor = colorScheme.background.toArgb()
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
