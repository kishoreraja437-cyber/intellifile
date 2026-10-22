package com.simats.intellifile.ui.onboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.intellifile.R
import com.simats.intellifile.ui.theme.IntellifileTheme

@Composable
fun OnboardScreen(
    onGetStartedClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEFF8F8))
    ) {
        // Left background accent shape
        Box(
            modifier = Modifier
                .width(120.dp)
                .fillMaxHeight(0.6f)
                .align(Alignment.TopStart)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(width * 0.7f, 0f)
                    cubicTo(width * 1.2f, height * 0.4f, width * 0.2f, height * 0.7f, width * 0.5f, height)
                    lineTo(0f, height)
                    close()
                }
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF88C7CB).copy(alpha = 0.5f),
                            Color(0xFFD5ECEE).copy(alpha = 0.4f),
                            Color(0xFFEFF8F8).copy(alpha = 0.2f)
                        )
                    )
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Top Header: Logo + Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    painter = painterResource(id = R.drawable.mainlogo),
                    contentDescription = "Main Logo",
                    modifier = Modifier.size(38.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(color = Color(0xFF183A3C))) {
                            append("Intelli")
                        }
                        withStyle(style = SpanStyle(color = Color(0xFF55A9AF))) {
                            append("File")
                        }
                    },
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Subheading
            Text(
                text = "Your AI-Powered\nDocument Assistant",
                textAlign = TextAlign.Center,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF183A3C),
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Center Illustration Area with Human and Floating Document Icons
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background circle for human illustration
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD5ECEE))
                )

                // Human Illustration
                Image(
                    painter = painterResource(id = R.drawable.human),
                    contentDescription = "Human Assistant",
                    modifier = Modifier.size(150.dp),
                    contentScale = ContentScale.Fit
                )

                // Floating PDF icon (Top Left)
                Image(
                    painter = painterResource(id = R.drawable.pdf),
                    contentDescription = "PDF Icon",
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.TopStart)
                        .offset(x = 40.dp, y = 10.dp)
                )

                // Floating DOC icon (Top Right)
                Image(
                    painter = painterResource(id = R.drawable.doc),
                    contentDescription = "DOC Icon",
                    modifier = Modifier
                        .size(38.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = (-40).dp, y = 20.dp)
                )

                // Floating PPT icon (Bottom Right)
                Image(
                    painter = painterResource(id = R.drawable.ppt),
                    contentDescription = "PPT Icon",
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = (-25).dp, y = (-20).dp)
                )

                // Normal file icon decoration on the left
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.CenterStart)
                        .offset(x = 10.dp, y = 20.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.normal),
                        contentDescription = "File Normal",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Feature Card 1: Ask AI
            FeatureCard(
                icon = Icons.Default.Search,
                text = "Ask AI to find your files\nusing natural language"
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Feature Card 2: Access and open
            FeatureCard(
                icon = Icons.Default.Description,
                text = "Access and open\nyour documents easily"
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Feature Card 3: Set file priority
            FeatureCard(
                icon = Icons.Default.Star,
                text = "Set file priority and\nstay organised"
            )

            Spacer(modifier = Modifier.weight(1f))

            // Get Started Button
            Button(
                onClick = onGetStartedClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF55A9AF)
                )
            ) {
                Text(
                    text = "Get Started",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun FeatureCard(
    icon: ImageVector,
    text: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFD5ECEE)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF55A9AF),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF183A3C),
                lineHeight = 17.sp
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun OnboardScreenPreview() {
    IntellifileTheme {
        OnboardScreen()
    }
}
