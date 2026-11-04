package com.simats.intellifile

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.foundation.layout.Box
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.messaging.FirebaseMessaging
import com.simats.intellifile.data.api.RetrofitClient
import com.simats.intellifile.data.manager.DocumentDownloadObserver
import com.simats.intellifile.data.manager.LocalFileManager
import com.simats.intellifile.data.manager.NotificationHelper
import com.simats.intellifile.data.repository.AuthRepository
import com.simats.intellifile.data.service.DocumentJobService
import com.simats.intellifile.data.service.DocumentMonitorService
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
            if (!LocalFileManager.hasStoragePermission(getApplication())) {
                _currentScreen.value = AppScreen.PERMISSION
            } else {
                _currentScreen.value = AppScreen.MAIN
            }
        } else {
            _currentScreen.value = AppScreen.ONBOARD
        }
    }

    fun onGetStartedClicked() {
        _currentScreen.value = AppScreen.LOGIN
    }

    fun onLoginSuccess() {
        if (!LocalFileManager.hasStoragePermission(getApplication())) {
            _currentScreen.value = AppScreen.PERMISSION
        } else {
            _currentScreen.value = AppScreen.MAIN
        }
    }

    fun onSignUpSuccess() {
        if (!LocalFileManager.hasStoragePermission(getApplication())) {
            _currentScreen.value = AppScreen.PERMISSION
        } else {
            _currentScreen.value = AppScreen.MAIN
        }
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

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            NotificationHelper.createNotificationChannels(this)
            DocumentDownloadObserver.startWatching(this)
            DocumentMonitorService.start(this)
            DocumentJobService.schedule(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ensure status bar is always shown and configured
        val initInsets = WindowCompat.getInsetsController(window, window.decorView)
        initInsets.show(WindowInsetsCompat.Type.statusBars())
        initInsets.isAppearanceLightStatusBars = true

        // 1. Prompt for Notification permission on Android 13+ (API 33+) if not yet granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 2. Initialize notification channels
        NotificationHelper.createNotificationChannels(this)

        // 3. Start real-time monitoring of new downloads from Browser, WhatsApp, and storage
        DocumentDownloadObserver.startWatching(this)
        DocumentMonitorService.start(this)
        DocumentJobService.schedule(this)

        // 4. Register & sync Firebase Cloud Messaging (FCM) push token with backend
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful && !task.result.isNullOrBlank()) {
                    val token = task.result
                    val sessionManager = SessionManager(this)
                    sessionManager.saveFcmToken(token)
                    if (sessionManager.isLoggedIn()) {
                        val authRepo = AuthRepository(this)
                        lifecycleScope.launch(Dispatchers.IO) {
                            authRepo.updateFcmToken(token)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

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

            DisposableEffect(isDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.statusBars())
                insetsController.isAppearanceLightStatusBars = !isDarkTheme
                insetsController.isAppearanceLightNavigationBars = !isDarkTheme
                onDispose {}
            }

            IntellifileTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                    ) {
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            val isForward = targetState.ordinal > initialState.ordinal
                            if (initialState == AppScreen.SPLASH) {
                                (fadeIn(animationSpec = tween(400, easing = FastOutSlowInEasing)) +
                                    scaleIn(initialScale = 0.92f, animationSpec = tween(400, easing = FastOutSlowInEasing)))
                                    .togetherWith(fadeOut(animationSpec = tween(250)))
                            } else if (isForward) {
                                (slideInHorizontally(
                                    initialOffsetX = { fullWidth -> (fullWidth * 0.35f).toInt() },
                                    animationSpec = tween(380, easing = FastOutSlowInEasing)
                                ) + fadeIn(animationSpec = tween(300)))
                                    .togetherWith(
                                        slideOutHorizontally(
                                            targetOffsetX = { fullWidth -> -(fullWidth * 0.25f).toInt() },
                                            animationSpec = tween(350, easing = FastOutSlowInEasing)
                                        ) + fadeOut(animationSpec = tween(250)) +
                                        scaleOut(targetScale = 0.95f, animationSpec = tween(350, easing = FastOutSlowInEasing))
                                    )
                            } else {
                                (slideInHorizontally(
                                    initialOffsetX = { fullWidth -> -(fullWidth * 0.35f).toInt() },
                                    animationSpec = tween(380, easing = FastOutSlowInEasing)
                                ) + fadeIn(animationSpec = tween(300)) +
                                scaleIn(initialScale = 0.95f, animationSpec = tween(380, easing = FastOutSlowInEasing)))
                                    .togetherWith(
                                        slideOutHorizontally(
                                            targetOffsetX = { fullWidth -> (fullWidth * 0.35f).toInt() },
                                            animationSpec = tween(350, easing = FastOutSlowInEasing)
                                        ) + fadeOut(animationSpec = tween(250))
                                    )
                            }
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

    override fun onResume() {
        super.onResume()
        DocumentDownloadObserver.getInstance(this).checkForNewDocuments()
        DocumentMonitorService.start(this)
        DocumentJobService.schedule(this)
    }
}
