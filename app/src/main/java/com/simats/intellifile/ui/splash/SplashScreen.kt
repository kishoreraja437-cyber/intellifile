package com.simats.intellifile.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.intellifile.R
import com.simats.intellifile.ui.theme.IntellifileTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit = {}
) {
    val rotationAngle = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.45f) }
    val logoAlpha = remember { Animatable(0f) }
    val textOffsetY = remember { Animatable(28f) }
    val textAlpha = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "splashPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    LaunchedEffect(Unit) {
        coroutineScope {
            val rotJob = async {
                rotationAngle.animateTo(
                    targetValue = 720f,
                    animationSpec = tween(durationMillis = 2000, easing = FastOutSlowInEasing)
                )
            }
            val scaleJob = async {
                logoScale.animateTo(
                    targetValue = 1.0f,
                    animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing)
                )
            }
            val alphaJob = async {
                logoAlpha.animateTo(
                    targetValue = 1.0f,
                    animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
                )
            }
            val textYJob = async {
                textOffsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 1000, delayMillis = 350, easing = FastOutSlowInEasing)
                )
            }
            val textAlphaJob = async {
                textAlpha.animateTo(
                    targetValue = 1.0f,
                    animationSpec = tween(durationMillis = 900, delayMillis = 350, easing = FastOutSlowInEasing)
                )
            }
            rotJob.await()
            scaleJob.await()
            alphaJob.await()
            textYJob.await()
            textAlphaJob.await()
        }
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF88C7CB),
                        Color(0xFFB5E2E5),
                        Color(0xFFEFF8F8)
                    )
                )
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Animated Logo Container with Glow Pulse Aura
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(170.dp)
            ) {
                // Expanding ambient aura
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .graphicsLayer(
                            scaleX = pulseScale,
                            scaleY = pulseScale,
                            alpha = pulseAlpha
                        )
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF26E07F).copy(alpha = 0.5f),
                                    Color(0xFF55A9AF).copy(alpha = 0.25f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Image(
                    painter = painterResource(id = R.drawable.mainlogo),
                    contentDescription = "App Main Logo",
                    modifier = Modifier
                        .size(140.dp)
                        .graphicsLayer(
                            rotationZ = rotationAngle.value,
                            scaleX = logoScale.value,
                            scaleY = logoScale.value,
                            alpha = logoAlpha.value
                        )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Animated IntelliFile Title & Subtitle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer(
                    translationY = textOffsetY.value,
                    alpha = textAlpha.value
                )
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(color = Color(0xFF183A3C))) {
                            append("Intelli")
                        }
                        withStyle(style = SpanStyle(color = Color(0xFF55A9AF))) {
                            append("File")
                        }
                    },
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Find. Prioritize. Access.",
                    color = Color(0xFF537779),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            CircularProgressIndicator(
                color = Color(0xFF26E07F),
                modifier = Modifier
                    .size(44.dp)
                    .graphicsLayer(alpha = textAlpha.value),
                strokeWidth = 3.dp
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            SplashBottomWaves()
        }
    }
}

@Composable
fun SplashBottomWaves() {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        val width = size.width
        val height = size.height

        val path1 = Path().apply {
            moveTo(0f, height * 0.5f)
            cubicTo(
                width * 0.3f, height * 0.1f,
                width * 0.7f, height * 0.9f,
                width, height * 0.3f
            )
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(
            path = path1,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF88C7CB).copy(alpha = 0.5f),
                    Color(0xFF6FBEC3).copy(alpha = 0.6f),
                    Color(0xFFD5ECEE)
                )
            )
        )

        val path2 = Path().apply {
            moveTo(0f, height * 0.75f)
            cubicTo(
                width * 0.35f, height * 0.4f,
                width * 0.65f, height * 1.1f,
                width, height * 0.55f
            )
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(
            path = path2,
            color = Color(0xFFD5ECEE).copy(alpha = 0.6f)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    IntellifileTheme {
        SplashScreen()
    }
}
