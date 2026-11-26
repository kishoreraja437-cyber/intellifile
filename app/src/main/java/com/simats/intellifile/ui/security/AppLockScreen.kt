package com.simats.intellifile.ui.security

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.simats.intellifile.data.security.AppLockManager

/**
 * Startup App Lock Screen for IntelliFile.
 * Prompts the user to enter their PIN or authenticate via fingerprint
 * whenever the app is opened with App Lock enabled.
 */
@Composable
fun AppLockScreen(
    onUnlockSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appLockManager = remember { AppLockManager.getInstance(context) }
    val pinLength = remember { appLockManager.getPinLength() }
    val isFingerprintEnabled = remember { appLockManager.isFingerprintEnabled() }

    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun triggerBiometricPrompt() {
        if (!isFingerprintEnabled) return
        val activity = context as? FragmentActivity ?: return
        appLockManager.showBiometricPrompt(
            activity = activity,
            title = "IntelliFile App Lock",
            subtitle = "Touch the fingerprint sensor to unlock",
            negativeButtonText = "Use PIN",
            onSuccess = {
                onUnlockSuccess()
            },
            onError = { err ->
                errorMessage = err
            },
            onCancel = {
                // User canceled biometric prompt; allow manual PIN entry
            }
        )
    }

    // Auto-prompt fingerprint once on launch if enabled
    LaunchedEffect(Unit) {
        if (isFingerprintEnabled) {
            triggerBiometricPrompt()
        }
    }

    fun handleDigit(digit: String) {
        if (enteredPin.length < pinLength) {
            isError = false
            errorMessage = null
            val nextPin = enteredPin + digit
            enteredPin = nextPin

            // Once user enters the full length, verify immediately
            if (nextPin.length == pinLength) {
                if (appLockManager.verifyPin(nextPin)) {
                    AppLockManager.isAppUnlocked = true
                    onUnlockSuccess()
                } else {
                    isError = true
                    errorMessage = "Incorrect PIN. Please try again."
                    enteredPin = ""
                }
            }
        }
    }

    fun handleBackspace() {
        if (enteredPin.isNotEmpty()) {
            isError = false
            errorMessage = null
            enteredPin = enteredPin.dropLast(1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(30.dp))

            // Header Section: Lock Icon, Title, Subtitle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE0F2F1)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Lock",
                        tint = Color(0xFF00897B),
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "IntelliFile Locked",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isFingerprintEnabled) {
                        "Enter your $pinLength-digit PIN or use fingerprint"
                    } else {
                        "Enter your $pinLength-digit PIN to unlock"
                    },
                    fontSize = 14.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                // PIN Dots
                PinDotsIndicator(
                    pinLength = pinLength,
                    enteredCount = enteredPin.length,
                    isError = isError,
                    dotSize = 18.dp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Error Feedback Text
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFEF4444),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Keypad Section
            Column(
                modifier = Modifier.padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NumericKeypad(
                    onDigitClick = { handleDigit(it) },
                    onBackspaceClick = { handleBackspace() },
                    showBiometricButton = isFingerprintEnabled,
                    onBiometricClick = { triggerBiometricPrompt() }
                )
            }
        }
    }
}
