package com.simats.intellifile.ui.security

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Reusable PIN Dots Indicator showing entered digits securely.
 */
@Composable
fun PinDotsIndicator(
    pinLength: Int,
    enteredCount: Int,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    dotSize: Dp = 16.dp
) {
    // Shake animation on error
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(isError) {
        if (isError) {
            for (i in 0..2) {
                shakeOffset.animateTo(12f, tween(50, easing = LinearEasing))
                shakeOffset.animateTo(-12f, tween(50, easing = LinearEasing))
            }
            shakeOffset.animateTo(0f, tween(50, easing = LinearEasing))
        }
    }

    Row(
        modifier = modifier.offset(x = shakeOffset.value.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until pinLength) {
            val isFilled = i < enteredCount
            val dotColor = when {
                isError -> Color(0xFFEF4444)
                isFilled -> Color(0xFF00897B)
                else -> Color.Transparent
            }
            val borderColor = when {
                isError -> Color(0xFFEF4444)
                isFilled -> Color(0xFF00897B)
                else -> Color(0xFFCBD5E1)
            }

            val scale by animateFloatAsState(
                targetValue = if (isFilled) 1.15f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "dotScale_$i"
            )

            Box(
                modifier = Modifier
                    .size(dotSize)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(dotColor)
                    .border(2.dp, borderColor, CircleShape)
            )
        }
    }
}

/**
 * Clean numeric keypad with 0-9 digits, backspace, and optional biometric button.
 */
@Composable
fun NumericKeypad(
    onDigitClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    modifier: Modifier = Modifier,
    onBiometricClick: (() -> Unit)? = null,
    showBiometricButton: Boolean = false
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Rows 1-3, 4-6, 7-9
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9")
        )

        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(0.85f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (digit in row) {
                    KeypadDigitButton(
                        digit = digit,
                        onClick = { onDigitClick(digit) }
                    )
                }
            }
        }

        // Bottom row: Biometric / empty, "0", Backspace
        Row(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left slot: Biometric fingerprint button if enabled
            if (showBiometricButton && onBiometricClick != null) {
                KeypadIconButton(
                    icon = Icons.Default.Fingerprint,
                    contentDescription = "Fingerprint",
                    tint = Color(0xFF00897B),
                    onClick = onBiometricClick
                )
            } else {
                Spacer(modifier = Modifier.size(68.dp))
            }

            // Center slot: "0"
            KeypadDigitButton(
                digit = "0",
                onClick = { onDigitClick("0") }
            )

            // Right slot: Backspace
            KeypadIconButton(
                icon = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "Backspace",
                tint = Color(0xFF64748B),
                onClick = onBackspaceClick
            )
        }
    }
}

@Composable
fun KeypadDigitButton(
    digit: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 68.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "keyScale"
    )

    Box(
        modifier = modifier
            .size(buttonSize)
            .scale(scale)
            .clip(CircleShape)
            .background(if (isPressed) Color(0xFFE0F2F1) else Color(0xFFF8FAFC))
            .border(1.dp, if (isPressed) Color(0xFF00897B) else Color(0xFFE2E8F0), CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF0F172A),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun KeypadIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 68.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "keyIconScale"
    )

    Box(
        modifier = modifier
            .size(buttonSize)
            .scale(scale)
            .clip(CircleShape)
            .background(if (isPressed) Color(0xFFE0F2F1) else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(28.dp)
        )
    }
}
