package com.simats.intellifile.ui.splash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
    LaunchedEffect(Unit) {
        delay(2000L)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0C24),
                        Color(0xFF0F1235),
                        Color(0xFF141846)
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
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "App Logo",
                modifier = Modifier.size(130.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = Color.White)) {
                        append("Intelli")
                    }
                    withStyle(style = SpanStyle(color = Color(0xFF9B51E5))) {
                        append("File")
                    }
                },
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Find. Prioritize. Access.",
                color = Color(0xFFB0B3C7),
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal
            )

            Spacer(modifier = Modifier.height(48.dp))

            CircularProgressIndicator(
                color = Color(0xFF9B51E5),
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
                    Color(0xFF7B2CBF).copy(alpha = 0.3f),
                    Color(0xFF5A189A).copy(alpha = 0.6f),
                    Color(0xFF3C096C).copy(alpha = 0.9f)
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
            color = Color(0xFF1B1145)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0C24)
@Composable
fun SplashScreenPreview() {
    IntellifileTheme {
        SplashScreen()
    }
}
