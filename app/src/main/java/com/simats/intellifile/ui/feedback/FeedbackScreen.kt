package com.simats.intellifile.ui.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class FeedbackCategory(val label: String, val icon: ImageVector) {
    BUG("Bug", Icons.Outlined.BugReport),
    IDEA("Idea", Icons.Outlined.Lightbulb),
    EXPERIENCE("Experience", Icons.Outlined.SentimentSatisfied)
}

@Composable
fun FeedbackScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onSubmitFeedbackClick: (rating: Int, category: FeedbackCategory, feedbackText: String, email: String) -> Unit = { _, _, _, _ -> }
) {
    var rating by remember { mutableIntStateOf(4) }
    var selectedCategory by remember { mutableStateOf(FeedbackCategory.BUG) }
    var feedbackText by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }

    val maxChars = 500

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Feedback",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Branding Overview Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEEF0FD)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RateReview,
                        contentDescription = "Feedback",
                        tint = Color(0xFF6C5CE7),
                        modifier = Modifier.size(42.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Help us improve IntelliFile",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Your feedback helps us build a better\nexperience for everyone.",
                    fontSize = 13.sp,
                    color = Color(0xFF6B7280),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Rate Your Experience
            Text(
                text = "Rate your experience",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Star Rating Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                (1..5).forEach { starIndex ->
                    val isSelected = starIndex <= rating
                    Icon(
                        imageVector = if (isSelected) Icons.Default.Star else Icons.Outlined.StarBorder,
                        contentDescription = "$starIndex Star",
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF9CA3AF),
                        modifier = Modifier
                            .size(36.dp)
                            .clickable { rating = starIndex }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Feedback Category Title
            Text(
                text = "Feedback Category",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Category Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FeedbackCategory.entries.forEach { category ->
                    val isSelected = category == selectedCategory
                    CategoryChip(
                        category = category,
                        isSelected = isSelected,
                        onClick = { selectedCategory = category },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Your Feedback Text Input
            Text(
                text = buildAnnotatedString {
                    append("Your Feedback ")
                    withStyle(style = SpanStyle(color = Color(0xFF9CA3AF), fontWeight = FontWeight.Normal)) {
                        append("(Optional)")
                    }
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(14.dp)
            ) {
                if (feedbackText.isEmpty()) {
                    Text(
                        text = "Tell us what you think...",
                        fontSize = 14.sp,
                        color = Color(0xFF9CA3AF)
                    )
                }

                BasicTextField(
                    value = feedbackText,
                    onValueChange = {
                        if (it.length <= maxChars) feedbackText = it
                    },
                    modifier = Modifier.fillMaxSize()
                )

                Text(
                    text = "${feedbackText.length}/$maxChars",
                    fontSize = 11.sp,
                    color = Color(0xFF9CA3AF),
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Email Input Field
            Text(
                text = buildAnnotatedString {
                    append("Email ")
                    withStyle(style = SpanStyle(color = Color(0xFF9CA3AF), fontWeight = FontWeight.Normal)) {
                        append("(Optional)")
                    }
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Mail,
                    contentDescription = "Email",
                    tint = Color(0xFF374151),
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (emailInput.isEmpty()) {
                        Text(
                            text = "your@email.com",
                            fontSize = 14.sp,
                            color = Color(0xFF9CA3AF)
                        )
                    }
                    BasicTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Submit Feedback Button
            Button(
                onClick = {
                    onSubmitFeedbackClick(rating, selectedCategory, feedbackText, emailInput)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Submit Feedback",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Submit Feedback",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun CategoryChip(
    category: FeedbackCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = category.label,
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = category.label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor
        )
    }
}

@Preview(showBackground = true)
@Composable
fun FeedbackScreenPreview() {
    FeedbackScreen()
}
