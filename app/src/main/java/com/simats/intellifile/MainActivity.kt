package com.simats.intellifile

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import com.simats.intellifile.ui.viewer.DocumentViewerScreen
import com.simats.intellifile.ui.viewer.ppt.PPTViewerScreen

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.pm.ActivityInfo
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
        val mode = AppThemeMode.valueOf(sessionManager.getThemeMode())
        if (mode == AppThemeMode.SYSTEM) AppThemeMode.LIGHT else mode
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

data class ExternalDocument(
    val uri: Uri,
    val fileName: String,
    val fileSize: String,
    val fileFormat: String,
    val mimeType: String
)

class MainActivity : ComponentActivity() {

    private val externalDocumentState = MutableStateFlow<ExternalDocument?>(null)
    private var isOpenedFromExternalIntent = false

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent, isInitial = false)
    }

    private fun handleIncomingIntent(intent: Intent?, isInitial: Boolean) {
        if (intent == null) return
        val action = intent.action
        val uri = intent.data

        if ((action == Intent.ACTION_VIEW || action == Intent.ACTION_EDIT) && uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (ignored: Exception) {}

            val resolved = resolveExternalDocument(uri, intent.type)
            if (resolved != null) {
                if (isInitial) {
                    isOpenedFromExternalIntent = true
                }
                externalDocumentState.value = resolved
            }
        }
    }

    private fun resolveExternalDocument(uri: Uri, incomingMime: String?): ExternalDocument {
        var name: String? = null
        var sizeBytes: Long = -1L

        // Query OpenableColumns via ContentResolver
        if (uri.scheme?.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true) == true) {
            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && !cursor.isNull(nameIndex)) {
                            name = cursor.getString(nameIndex)
                        }
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                            sizeBytes = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "Failed querying OpenableColumns for " + uri + ": " + e.message)
            }
        }

        // Check file scheme fallback
        if (name.isNullOrBlank() && uri.scheme?.equals(ContentResolver.SCHEME_FILE, ignoreCase = true) == true) {
            try {
                val file = java.io.File(uri.path ?: "")
                name = file.name
                if (file.exists()) {
                    sizeBytes = file.length()
                }
            } catch (ignored: Exception) {}
        }

        // Fallback to URI last path segment
        if (name.isNullOrBlank()) {
            name = uri.lastPathSegment?.substringAfterLast('/')
        }

        // Resolve MIME type
        val resolvedMime = incomingMime ?: try {
            contentResolver.getType(uri)
        } catch (ignored: Exception) {
            null
        } ?: ""

        // Resolve extension and format
        var ext = name?.substringAfterLast('.', "")?.uppercase()?.trim() ?: ""
        if (ext.isBlank() || ext.length > 5) {
            ext = when {
                resolvedMime.contains("pdf", ignoreCase = true) -> "PDF"
                resolvedMime.contains("word", ignoreCase = true) || resolvedMime.contains("msword", ignoreCase = true) -> "DOCX"
                resolvedMime.contains("excel", ignoreCase = true) || resolvedMime.contains("spreadsheet", ignoreCase = true) -> "XLSX"
                resolvedMime.contains("powerpoint", ignoreCase = true) || resolvedMime.contains("presentation", ignoreCase = true) -> "PPTX"
                resolvedMime.contains("csv", ignoreCase = true) -> "CSV"
                resolvedMime.contains("text", ignoreCase = true) -> "TXT"
                else -> "PDF"
            }
        }

        val effectiveFileName = when {
            name.isNullOrBlank() -> "Document." + ext
            !name!!.contains('.') -> name + "." + ext
            else -> name!!
        }

        val formattedSize = if (sizeBytes > 0) {
            android.text.format.Formatter.formatFileSize(this, sizeBytes)
        } else {
            "Unknown size"
        }

        val effectiveMime = resolvedMime.ifBlank {
            when (ext) {
                "PDF" -> "application/pdf"
                "DOCX" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "DOC" -> "application/msword"
                "XLSX" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                "XLS" -> "application/vnd.ms-excel"
                "PPTX" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                "PPT" -> "application/vnd.ms-powerpoint"
                "CSV" -> "text/csv"
                "TXT" -> "text/plain"
                else -> "application/octet-stream"
            }
        }

        return ExternalDocument(
            uri = uri,
            fileName = effectiveFileName,
            fileSize = formattedSize,
            fileFormat = ext,
            mimeType = effectiveMime
        )
    }


    @Suppress("InvalidFragmentVersionForActivityResult")
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
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        handleIncomingIntent(intent, isInitial = true)
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
        } catch (ignored: Exception) {}

        setContent {
            val appNavViewModel: AppNavigationViewModel = viewModel()
            val currentScreen by appNavViewModel.currentScreen.collectAsState()
            val themeMode by appNavViewModel.themeMode.collectAsState()

            val isDarkTheme = when (themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> false
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
                        val externalDoc by externalDocumentState.collectAsState()

                        LaunchedEffect(externalDoc) {
                            if (externalDoc == null) {
                                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            }
                        }

                        if (externalDoc != null) {
                            val doc = externalDoc!!
                            BackHandler {
                                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                val shouldFinish = isOpenedFromExternalIntent && isTaskRoot
                                externalDocumentState.value = null
                                isOpenedFromExternalIntent = false
                                if (shouldFinish) {
                                    finish()
                                }
                            }

                            val isPpt = doc.fileFormat.uppercase() in listOf("PPT", "PPTX", "PPS", "PPSX", "POT", "POTX") ||
                                    doc.fileName.endsWith(".pptx", ignoreCase = true) ||
                                    doc.fileName.endsWith(".ppt", ignoreCase = true)

                            if (isPpt) {
                                PPTViewerScreen(
                                    fileName = doc.fileName,
                                    filePath = if (doc.uri.scheme?.equals(ContentResolver.SCHEME_FILE, ignoreCase = true) == true) doc.uri.path ?: "" else "",
                                    fileUriString = doc.uri.toString(),
                                    onBackClick = {
                                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        val shouldFinish = isOpenedFromExternalIntent && isTaskRoot
                                        externalDocumentState.value = null
                                        isOpenedFromExternalIntent = false
                                        if (shouldFinish) {
                                            finish()
                                        }
                                    },
                                    onShareClick = {
                                        try {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = doc.mimeType.ifBlank { "application/vnd.openxmlformats-officedocument.presentationml.presentation" }
                                                putExtra(Intent.EXTRA_STREAM, doc.uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            startActivity(Intent.createChooser(shareIntent, "Share " + doc.fileName))
                                        } catch (e: Exception) {
                                            Toast.makeText(this@MainActivity, "Failed to share document", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            } else {
                                DocumentViewerScreen(
                                    fileName = doc.fileName,
                                    filePath = if (doc.uri.scheme?.equals(ContentResolver.SCHEME_FILE, ignoreCase = true) == true) doc.uri.path ?: "" else "",
                                    fileUriString = doc.uri.toString(),
                                    fileFormat = doc.fileFormat,
                                    totalPages = 1,
                                    onBackClick = {
                                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        val shouldFinish = isOpenedFromExternalIntent && isTaskRoot
                                        externalDocumentState.value = null
                                        isOpenedFromExternalIntent = false
                                        if (shouldFinish) {
                                            finish()
                                        }
                                    },
                                    onShareClick = {
                                        try {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = doc.mimeType.ifBlank { "application/octet-stream" }
                                                putExtra(Intent.EXTRA_STREAM, doc.uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            startActivity(Intent.createChooser(shareIntent, "Share " + doc.fileName))
                                        } catch (e: Exception) {
                                            Toast.makeText(this@MainActivity, "Failed to share document", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        } else {
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
    }

    override fun onResume() {
        super.onResume()
        DocumentDownloadObserver.getInstance(this).checkForNewDocuments()
        DocumentMonitorService.start(this)
        DocumentJobService.schedule(this)
    }
}
