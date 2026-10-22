package com.simats.intellifile.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit = {}
) {
    val rotationAngle = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        rotationAngle.animateTo(
            targetValue = 720f,
            animationSpec = tween(
                durationMillis = 2000,
                easing = FastOutSlowInEasing
            )
        )
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

            Image(
                painter = painterResource(id = R.drawable.mainlogo),
                contentDescription = "App Main Logo",
                modifier = Modifier
                    .size(140.dp)
                    .graphicsLayer(rotationZ = rotationAngle.value)
            )

            Spacer(modifier = Modifier.height(24.dp))

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

            Spacer(modifier = Modifier.height(48.dp))

            CircularProgressIndicator(
                color = Color(0xFF26E07F),
                modifier = Modifier.size(44.dp),
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
