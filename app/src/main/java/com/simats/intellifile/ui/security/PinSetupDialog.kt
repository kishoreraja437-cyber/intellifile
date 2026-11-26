package com.simats.intellifile.ui.security

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.simats.intellifile.data.security.AppLockManager

enum class PinDialogMode {
    CREATE,
    CHANGE,
    DISABLE
}

private enum class SetupStep {
    ENTER_CURRENT,
    ENTER_NEW,
    CONFIRM_NEW
}

/**
 * Modal Dialog for Creating, Changing, or Disabling App Lock PIN.
 * Features 4/6-digit length selector, confirmation matching,
 * clear dots indicator, and numeric keypad.
 */
@Composable
fun PinSetupDialog(
    mode: PinDialogMode,
    onDismissRequest: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val appLockManager = remember { AppLockManager.getInstance(context) }

    var targetLength by remember { mutableIntStateOf(appLockManager.getPinLength()) }
    var currentStep by remember {
        mutableStateOf(
            when (mode) {
                PinDialogMode.CREATE -> SetupStep.ENTER_NEW
                PinDialogMode.CHANGE -> SetupStep.ENTER_CURRENT
                PinDialogMode.DISABLE -> SetupStep.ENTER_CURRENT
            }
        )
    }

    var firstEnteredNewPin by remember { mutableStateOf("") }
    var currentPinInput by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val currentLengthRequired = if (currentStep == SetupStep.ENTER_CURRENT) {
        appLockManager.getPinLength()
    } else {
        targetLength
    }

    val titleText = when (mode) {
        PinDialogMode.CREATE -> when (currentStep) {
            SetupStep.ENTER_NEW -> "Create a PIN"
            SetupStep.CONFIRM_NEW -> "Confirm your PIN"
            else -> "App Lock PIN"
        }
        PinDialogMode.CHANGE -> when (currentStep) {
            SetupStep.ENTER_CURRENT -> "Enter Current PIN"
            SetupStep.ENTER_NEW -> "Enter New PIN"
            SetupStep.CONFIRM_NEW -> "Confirm New PIN"
        }
        PinDialogMode.DISABLE -> "Enter PIN to Disable"
    }

    val subtitleText = when (mode) {
        PinDialogMode.CREATE -> when (currentStep) {
            SetupStep.ENTER_NEW -> "Choose a $targetLength-digit PIN to secure your files"
            SetupStep.CONFIRM_NEW -> "Re-enter your $targetLength-digit PIN to confirm"
            else -> ""
        }
        PinDialogMode.CHANGE -> when (currentStep) {
            SetupStep.ENTER_CURRENT -> "Verify your current PIN before setting a new one"
            SetupStep.ENTER_NEW -> "Enter your new $targetLength-digit PIN"
            SetupStep.CONFIRM_NEW -> "Re-enter your new $targetLength-digit PIN to confirm"
        }
        PinDialogMode.DISABLE -> "Enter your current PIN to turn off App Lock"
    }

    fun handleDigit(digit: String) {
        if (currentPinInput.length < currentLengthRequired) {
            isError = false
            errorMessage = null
            val nextPin = currentPinInput + digit
            currentPinInput = nextPin

            if (nextPin.length == currentLengthRequired) {
                when (currentStep) {
                    SetupStep.ENTER_CURRENT -> {
                        if (appLockManager.verifyPin(nextPin)) {
                            if (mode == PinDialogMode.DISABLE) {
                                appLockManager.setAppLockEnabled(false)
                                Toast.makeText(context, "App Lock disabled", Toast.LENGTH_SHORT).show()
                                onSuccess()
                            } else {
                                // Proceed to enter new pin
                                currentStep = SetupStep.ENTER_NEW
                                currentPinInput = ""
                            }
                        } else {
                            isError = true
                            errorMessage = "Incorrect current PIN. Please try again."
                            currentPinInput = ""
                        }
                    }

                    SetupStep.ENTER_NEW -> {
                        firstEnteredNewPin = nextPin
                        currentStep = SetupStep.CONFIRM_NEW
                        currentPinInput = ""
                    }

                    SetupStep.CONFIRM_NEW -> {
                        if (nextPin == firstEnteredNewPin) {
                            appLockManager.setPinLength(targetLength)
                            val saved = appLockManager.savePin(nextPin)
                            if (saved) {
                                val msg = if (mode == PinDialogMode.CHANGE) "PIN changed successfully" else "App Lock PIN created"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                onSuccess()
                            } else {
                                isError = true
                                errorMessage = "Failed to save PIN. Please try again."
                                currentPinInput = ""
                            }
                        } else {
                            isError = true
                            errorMessage = "PINs do not match. Please re-enter."
                            currentPinInput = ""
                            currentStep = SetupStep.ENTER_NEW
                            firstEnteredNewPin = ""
                        }
                    }
                }
            }
        }
    }

    fun handleBackspace() {
        if (currentPinInput.isNotEmpty()) {
            isError = false
            errorMessage = null
            currentPinInput = currentPinInput.dropLast(1)
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Close Button in Top-Left
            IconButton(
                onClick = onDismissRequest,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color(0xFF64748B)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // Header Info
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE0F2F1)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = "Lock",
                            tint = Color(0xFF00897B),
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = titleText,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = subtitleText,
                        fontSize = 13.5.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center
                    )

                    // Optional 4-Digit vs 6-Digit length selector when creating or changing PIN
                    if (currentStep == SetupStep.ENTER_NEW) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LengthChip(
                                label = "4 Digits",
                                isSelected = targetLength == 4,
                                onClick = {
                                    targetLength = 4
                                    currentPinInput = ""
                                }
                            )
                            LengthChip(
                                label = "6 Digits",
                                isSelected = targetLength == 6,
                                onClick = {
                                    targetLength = 6
                                    currentPinInput = ""
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // PIN Dots
                    PinDotsIndicator(
                        pinLength = currentLengthRequired,
                        enteredCount = currentPinInput.length,
                        isError = isError,
                        dotSize = 18.dp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    AnimatedVisibility(
                        visible = errorMessage != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFEF4444),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Numeric Keypad
                Column(
                    modifier = Modifier.padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    NumericKeypad(
                        onDigitClick = { handleDigit(it) },
                        onBackspaceClick = { handleBackspace() },
                        showBiometricButton = false
                    )
                }
            }
        }
    }
}

@Composable
private fun LengthChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) Color(0xFF00897B) else Color(0xFFF1F5F9))
            .border(
                1.dp,
                if (isSelected) Color(0xFF00897B) else Color(0xFFE2E8F0),
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else Color(0xFF475569)
        )
    }
}
