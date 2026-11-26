package com.simats.intellifile.ui.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Loading indicator dialog displayed while uploading / indexing files to IntelliSpace.
 * Matches exact UI reference design with:
 * - Teal rounded cloud icon with white upward arrow and soft glowing ground reflection
 * - "Indexing to IntelliSpace..." title
 * - "X of Y files indexed" subtitle
 * - Rounded teal progress bar capsule
 */
@Composable
fun IntelliSpaceIndexingDialog(
    currentIndex: Int,
    totalCount: Int,
    fileName: String? = null,
    onDismissRequest: () -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            IntelliSpaceIndexingCard(
                currentIndex = currentIndex,
                totalCount = totalCount,
                fileName = fileName
            )
        }
    }
}

@Composable
fun IntelliSpaceIndexingCard(
    currentIndex: Int,
    totalCount: Int,
    fileName: String? = null,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cloudFloat")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatAnim"
    )

    val shadowScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shadowAnim"
    )

    // Calculate progress ratio
    val safeTotal = if (totalCount > 0) totalCount else 1
    val safeIndex = currentIndex.coerceIn(0, safeTotal)
    val targetProgress = (safeIndex.toFloat() / safeTotal.toFloat()).coerceIn(0.08f, 1f)

    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "progressAnim"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.12f)
            )
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White)
            .border(1.5.dp, Color(0xFF55A9AF), RoundedCornerShape(26.dp))
            .padding(horizontal = 24.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Cloud Upload Icon with Glowing Ground Reflection
            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 70.dp),
                contentAlignment = Alignment.Center
            ) {
                // Soft glowing ellipse reflection underneath
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(18.dp)
                        .align(Alignment.BottomCenter)
                        .graphicsLayer(scaleX = shadowScale, scaleY = shadowScale)
                ) {
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF2DD4BF).copy(alpha = 0.55f),
                                Color(0xFF2DD4BF).copy(alpha = 0.20f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = size.width * 0.45f
                        ),
                        topLeft = Offset.Zero,
                        size = size
                    )
                }

                // Rounded Teal Cloud Shape with White Upward Arrow
                Box(
                    modifier = Modifier
                        .size(width = 62.dp, height = 48.dp)
                        .graphicsLayer(translationY = floatOffset)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF2DD4BF),
                                    Color(0xFF00BFA5),
                                    Color(0xFF00897B)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Upload",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Title: "Indexing to IntelliSpace..."
            Text(
                text = "Indexing to IntelliSpace...",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 3. Subtitle / Progress Counter: e.g. "3 of 5 files indexed"
            val subtitleText = when {
                totalCount > 1 -> "$safeIndex of $totalCount files indexed"
                totalCount == 1 && !fileName.isNullOrBlank() -> "1 of 1 file indexed"
                totalCount == 1 -> "1 of 1 file indexed"
                else -> "Indexing files..."
            }

            Text(
                text = subtitleText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Rounded Capsule Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFFE2F4F2))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF2DD4BF),
                                    Color(0xFF00BFA5),
                                    Color(0xFF00897B)
                                )
                            )
                        )
                )
            }
        }
    }
}
