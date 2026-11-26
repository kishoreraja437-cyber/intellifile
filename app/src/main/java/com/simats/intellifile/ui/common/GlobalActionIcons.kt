package com.simats.intellifile.ui.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Global Animated Action Icons matching IntelliFile UI Design Reference:
 * 1. AnimatedMicrophoneButton: White circular button with dusky aqua mic, concentric soft outer ripple rings, subtle pulsing, and 2 sparkles.
 * 2. AnimatedChecklistButton: White circular button with thin dusky aqua outline, dark teal checklist symbol, and smooth pop/scale effect.
 * 3. AnimatedRefreshButton: Dusky aqua circular button with white refresh arrow and animated rotating cyan accent ring.
 */

// --- 1. ANIMATED MICROPHONE BUTTON ---

@Composable
fun AnimatedMicrophoneButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 38.dp,
    contentDescription: String = "Voice Assistant",
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Gentle continuous breathing / ripple animation
    val infiniteTransition = rememberInfiniteTransition(label = "micBreathing")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micPulseScale"
    )
    val rippleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micRippleAlpha"
    )
    val sparkleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.50f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkleAlpha"
    )

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )

    // Outer container provides space for the concentric ripple rings and sparkles
    Box(
        modifier = modifier
            .size(buttonSize + 14.dp),
        contentAlignment = Alignment.Center
    ) {
        // Concentric Soft Aqua Ripple Rings
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
        ) {
            val centerOffset = center
            val baseRadius = buttonSize.toPx() / 2f

            // Outer Ripple Ring 2
            drawCircle(
                color = Color(0xFF2DD4BF).copy(alpha = rippleAlpha * 0.45f),
                radius = baseRadius + 6.dp.toPx(),
                center = centerOffset,
                style = Stroke(width = 1.4.dp.toPx())
            )

            // Inner Ripple Ring 1
            drawCircle(
                color = Color(0xFF2DD4BF).copy(alpha = rippleAlpha * 0.75f),
                radius = baseRadius + 3.dp.toPx(),
                center = centerOffset,
                style = Stroke(width = 1.8.dp.toPx())
            )

            // Sparkle 1 (Upper Left)
            drawSparkleStar(
                center = Offset(centerOffset.x - baseRadius * 0.95f, centerOffset.y - baseRadius * 0.85f),
                outerRadius = 4.5.dp.toPx(),
                color = Color(0xFF00BFA5).copy(alpha = sparkleAlpha)
            )

            // Sparkle 2 (Smaller, top-left above)
            drawSparkleStar(
                center = Offset(centerOffset.x - baseRadius * 0.65f, centerOffset.y - baseRadius * 1.15f),
                outerRadius = 2.8.dp.toPx(),
                color = Color(0xFF00BFA5).copy(alpha = sparkleAlpha * 0.85f)
            )
        }

        // Circular White Button with Dusky Aqua Mic
        Box(
            modifier = Modifier
                .size(buttonSize)
                .graphicsLayer(scaleX = pressScale, scaleY = pressScale)
                .shadow(
                    elevation = 3.dp,
                    shape = CircleShape,
                    ambientColor = Color(0x10000000),
                    spotColor = Color(0x18000000)
                )
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, Color(0xFFE2F4F2), CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = contentDescription,
                tint = Color(0xFF00897B),
                modifier = Modifier.size(buttonSize * 0.52f)
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSparkleStar(
    center: Offset,
    outerRadius: Float,
    color: Color
) {
    val path = Path().apply {
        moveTo(center.x, center.y - outerRadius)
        quadraticBezierTo(center.x, center.y, center.x + outerRadius, center.y)
        quadraticBezierTo(center.x, center.y, center.x, center.y + outerRadius)
        quadraticBezierTo(center.x, center.y, center.x - outerRadius, center.y)
        quadraticBezierTo(center.x, center.y, center.x, center.y - outerRadius)
        close()
    }
    drawPath(path = path, color = color)
}

// --- 2. ANIMATED CHECKLIST BUTTON ---

@Composable
fun AnimatedChecklistButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 38.dp,
    isSelected: Boolean = false,
    contentDescription: String = "Checklist",
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else if (isSelected) 1.06f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "checklistScale"
    )

    Box(
        modifier = modifier
            .size(buttonSize)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .shadow(
                elevation = if (isSelected) 5.dp else 2.dp,
                shape = CircleShape,
                ambientColor = if (isSelected) Color(0x3000BFA5) else Color(0x0C000000),
                spotColor = if (isSelected) Color(0x4000BFA5) else Color(0x14000000)
            )
            .clip(CircleShape)
            .background(if (isSelected) Color(0xFFF0FAF9) else Color.White)
            .border(
                width = if (isSelected) 2.dp else 1.5.dp,
                color = if (isSelected) Color(0xFF009688) else Color(0xFF55A9AF),
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Pixel-perfect Checklist Symbol matching Reference Image:
        // 2 Checkmarks with 2 Horizontal Lines beside them
        Canvas(
            modifier = Modifier.size(buttonSize * 0.52f)
        ) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = 2.4.dp.toPx()
            val strokeColor = Color(0xFF0F3E46)

            // --- Row 1 (Top) ---
            val row1Y = h * 0.32f
            // Checkmark 1
            val check1 = Path().apply {
                moveTo(w * 0.10f, row1Y)
                lineTo(w * 0.24f, row1Y + h * 0.16f)
                lineTo(w * 0.44f, row1Y - h * 0.16f)
            }
            drawPath(
                path = check1,
                color = strokeColor,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
            // Line 1
            drawLine(
                color = strokeColor,
                start = Offset(w * 0.56f, row1Y + h * 0.04f),
                end = Offset(w * 0.90f, row1Y + h * 0.04f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )

            // --- Row 2 (Bottom) ---
            val row2Y = h * 0.68f
            // Checkmark 2
            val check2 = Path().apply {
                moveTo(w * 0.10f, row2Y)
                lineTo(w * 0.24f, row2Y + h * 0.16f)
                lineTo(w * 0.44f, row2Y - h * 0.16f)
            }
            drawPath(
                path = check2,
                color = strokeColor,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
            // Line 2
            drawLine(
                color = strokeColor,
                start = Offset(w * 0.56f, row2Y + h * 0.04f),
                end = Offset(w * 0.90f, row2Y + h * 0.04f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }
    }
}

// --- 3. ANIMATED REFRESH BUTTON ---

@Composable
fun AnimatedRefreshButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 38.dp,
    isRefreshing: Boolean = false,
    contentDescription: String = "Refresh",
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Smooth rotation animation while refreshing
    val infiniteTransition = rememberInfiniteTransition(label = "refreshAnim")
    val spinningRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinAngle"
    )

    // Manual tap rotation animatable
    var tapRotationTrigger by remember { mutableFloatStateOf(0f) }
    val tapRotation = remember { Animatable(0f) }

    LaunchedEffect(tapRotationTrigger) {
        if (tapRotationTrigger > 0f) {
            tapRotation.animateTo(
                targetValue = tapRotationTrigger,
                animationSpec = tween(650, easing = FastOutSlowInEasing)
            )
        }
    }

    val currentRotation = if (isRefreshing) spinningRotation else tapRotation.value

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "refreshPressScale"
    )

    // Outer container provides space for the segmented circular accent arc
    Box(
        modifier = modifier
            .size(buttonSize + 10.dp),
        contentAlignment = Alignment.Center
    ) {
        // Rotating Cyan/Aqua Accent Ring Arc matching Reference Image
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val centerOffset = center
            val ringRadius = (buttonSize.toPx() / 2f) + 3.dp.toPx()
            val arcStroke = 2.2.dp.toPx()

            // Segment 1 (Top-Right Arc)
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF00BFA5),
                        Color(0xFF2DD4BF),
                        Color(0xFF00E5FF)
                    )
                ),
                startAngle = currentRotation - 30f,
                sweepAngle = 75f,
                useCenter = false,
                topLeft = Offset(centerOffset.x - ringRadius, centerOffset.y - ringRadius),
                size = Size(ringRadius * 2f, ringRadius * 2f),
                style = Stroke(width = arcStroke, cap = StrokeCap.Round)
            )

            // Segment 2 (Bottom-Left Arc)
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF00BFA5),
                        Color(0xFF2DD4BF),
                        Color(0xFF00E5FF)
                    )
                ),
                startAngle = currentRotation + 150f,
                sweepAngle = 55f,
                useCenter = false,
                topLeft = Offset(centerOffset.x - ringRadius, centerOffset.y - ringRadius),
                size = Size(ringRadius * 2f, ringRadius * 2f),
                style = Stroke(width = arcStroke, cap = StrokeCap.Round)
            )
        }

        // Circular Dusky Aqua Solid Button with White Refresh Arrow
        Box(
            modifier = Modifier
                .size(buttonSize)
                .graphicsLayer(scaleX = pressScale, scaleY = pressScale)
                .shadow(
                    elevation = 4.dp,
                    shape = CircleShape,
                    ambientColor = Color(0x2000BFA5),
                    spotColor = Color(0x3500BFA5)
                )
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF00BFA5),
                            Color(0xFF009688),
                            Color(0xFF00897B)
                        )
                    )
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = {
                        tapRotationTrigger += 360f
                        onClick()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier
                    .size(buttonSize * 0.52f)
                    .graphicsLayer(rotationZ = currentRotation)
            )
        }
    }
}
