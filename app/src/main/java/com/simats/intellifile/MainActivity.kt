package com.simats.intellifile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simats.intellifile.ui.main.MainScreen
import com.simats.intellifile.ui.onboard.OnboardScreen
import com.simats.intellifile.ui.permission.PermissionScreen
import com.simats.intellifile.ui.splash.SplashScreen
import com.simats.intellifile.ui.theme.AppThemeMode
import com.simats.intellifile.ui.theme.IntellifileTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppScreen {
    SPLASH, ONBOARD, PERMISSION, MAIN
}

class AppNavigationViewModel : ViewModel() {
    private val _currentScreen = MutableStateFlow(AppScreen.SPLASH)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _themeMode = MutableStateFlow(AppThemeMode.LIGHT)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    fun onSplashFinished() {
        _currentScreen.value = AppScreen.ONBOARD
    }

    fun onGetStartedClicked() {
        _currentScreen.value = AppScreen.PERMISSION
    }

    fun onPermissionProceed() {
        _currentScreen.value = AppScreen.MAIN
    }

    fun onBackFromPermission() {
        _currentScreen.value = AppScreen.ONBOARD
    }

    fun onLogout() {
        _currentScreen.value = AppScreen.ONBOARD
    }

    fun onThemeSelected(mode: AppThemeMode) {
        _themeMode.value = mode
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appNavViewModel: AppNavigationViewModel = viewModel()
            val currentScreen by appNavViewModel.currentScreen.collectAsState()
            val themeMode by appNavViewModel.themeMode.collectAsState()

            val isSystemDark = isSystemInDarkTheme()
            val isDarkTheme = when (themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> isSystemDark
            }

            IntellifileTheme(darkTheme = isDarkTheme) {
                when (currentScreen) {
                    AppScreen.SPLASH -> SplashScreen(
                        onSplashFinished = { appNavViewModel.onSplashFinished() }
                    )
                    AppScreen.ONBOARD -> OnboardScreen(
                        onGetStartedClick = { appNavViewModel.onGetStartedClicked() }
                    )
                    AppScreen.PERMISSION -> PermissionScreen(
                        onSelectFilesClick = { appNavViewModel.onPermissionProceed() },
                        onSelectFolderClick = { appNavViewModel.onPermissionProceed() },
                        onMaybeLaterClick = { appNavViewModel.onPermissionProceed() },
                        onBackClick = { appNavViewModel.onBackFromPermission() }
                    )
                    AppScreen.MAIN -> MainScreen(
                        onLogout = { appNavViewModel.onLogout() },
                        currentThemeMode = themeMode,
                        onThemeChanged = { mode -> appNavViewModel.onThemeSelected(mode) }
                    )
                }
            }
        }
    }
}
