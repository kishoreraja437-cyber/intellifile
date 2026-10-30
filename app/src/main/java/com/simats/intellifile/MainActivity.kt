package com.simats.intellifile

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simats.intellifile.data.api.RetrofitClient
import com.simats.intellifile.data.session.SessionManager
import com.simats.intellifile.ui.auth.ForgotPasswordScreen
import com.simats.intellifile.ui.auth.LoginScreen
import com.simats.intellifile.ui.auth.SignUpScreen
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
    SPLASH, ONBOARD, LOGIN, SIGNUP, FORGOT_PASSWORD, PERMISSION, MAIN
}

class AppNavigationViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionManager = SessionManager(application)

    private val _currentScreen = MutableStateFlow(AppScreen.SPLASH)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val initialTheme = try {
        AppThemeMode.valueOf(sessionManager.getThemeMode())
    } catch (e: Exception) {
        AppThemeMode.LIGHT
    }
    private val _themeMode = MutableStateFlow(initialTheme)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    fun onSplashFinished() {
        if (sessionManager.isLoggedIn()) {
            _currentScreen.value = AppScreen.MAIN
        } else {
            _currentScreen.value = AppScreen.ONBOARD
        }
    }

    fun onGetStartedClicked() {
        _currentScreen.value = AppScreen.LOGIN
    }

    fun onLoginSuccess() {
        _currentScreen.value = AppScreen.MAIN
    }

    fun onSignUpSuccess() {
        _currentScreen.value = AppScreen.MAIN
    }

    fun onSignUpClicked() {
        _currentScreen.value = AppScreen.SIGNUP
    }

    fun onForgotPasswordClicked() {
        _currentScreen.value = AppScreen.FORGOT_PASSWORD
    }

    fun onSignInClicked() {
        _currentScreen.value = AppScreen.LOGIN
    }

    fun onBackFromLogin() {
        _currentScreen.value = AppScreen.ONBOARD
    }

    fun onBackFromSignUp() {
        _currentScreen.value = AppScreen.LOGIN
    }

    fun onBackFromForgotPassword() {
        _currentScreen.value = AppScreen.LOGIN
    }

    fun onPermissionProceed() {
        _currentScreen.value = AppScreen.MAIN
    }

    fun onBackFromPermission() {
        _currentScreen.value = AppScreen.LOGIN
    }

    fun onLogout() {
        sessionManager.clearSession()
        RetrofitClient.resetClient()
        _currentScreen.value = AppScreen.LOGIN
    }

    fun onThemeSelected(mode: AppThemeMode) {
        sessionManager.saveThemeMode(mode.name)
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
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(200))
                        },
                        label = "rootScreenTransition"
                    ) { targetScreen ->
                        when (targetScreen) {
                            AppScreen.SPLASH -> SplashScreen(
                                onSplashFinished = { appNavViewModel.onSplashFinished() }
                            )
                            AppScreen.ONBOARD -> OnboardScreen(
                                onGetStartedClick = { appNavViewModel.onGetStartedClicked() }
                            )
                            AppScreen.LOGIN -> LoginScreen(
                                onLoginSuccess = { appNavViewModel.onLoginSuccess() },
                                onSignUpClick = { appNavViewModel.onSignUpClicked() },
                                onForgotPasswordClick = { appNavViewModel.onForgotPasswordClicked() },
                                onBackClick = { appNavViewModel.onBackFromLogin() }
                            )
                            AppScreen.SIGNUP -> SignUpScreen(
                                onSignUpSuccess = { appNavViewModel.onSignUpSuccess() },
                                onSignInClick = { appNavViewModel.onSignInClicked() },
                                onBackClick = { appNavViewModel.onBackFromSignUp() }
                            )
                            AppScreen.FORGOT_PASSWORD -> ForgotPasswordScreen(
                                onResetSuccess = { appNavViewModel.onSignInClicked() },
                                onBackToLoginClick = { appNavViewModel.onSignInClicked() },
                                onBackClick = { appNavViewModel.onBackFromForgotPassword() }
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
    }
}
