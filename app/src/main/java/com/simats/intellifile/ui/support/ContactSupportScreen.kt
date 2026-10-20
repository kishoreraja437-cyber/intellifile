package com.simats.intellifile.ui.support

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ContactSupportScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onEmailSupportClick: () -> Unit = {},
    onReportProblemClick: () -> Unit = {},
    onFaqClick: () -> Unit = {},
    onSendMessageClick: (name: String, email: String, subject: String, message: String) -> Unit = { _, _, _, _ -> }
) {
    var nameInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf("") }
    var messageInput by remember { mutableStateOf("") }
    var isSubjectDropdownOpen by remember { mutableStateOf(false) }

    val subjectOptions = listOf(
        "General Question",
        "Bug Report / Issue",
        "Feature Request",
        "Account & Privacy"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FE))
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
                        tint = Color(0xFF111827)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Contact Support",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // We are here to help Header
            Text(
                text = "We are here to help",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Get in touch with our support team for any\nquestions or issues.",
                fontSize = 14.sp,
                color = Color(0xFF4B5563),
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Support Option 1: Email Support
            SupportChannelCard(
                icon = Icons.Outlined.Mail,
                badgeColor = Color(0xFF6C5CE7),
                title = "Email Support",
                subtitle = "support@intellifile.app",
                onClick = onEmailSupportClick
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Support Option 2: Report a Problem
            SupportChannelCard(
                icon = Icons.Outlined.Warning,
                badgeColor = Color(0xFFEF4444),
                title = "Report a Problem",
                subtitle = "Let us know about any bugs\nor issues you face.",
                onClick = onReportProblemClick
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Support Option 3: FAQs & Help Center
            SupportChannelCard(
                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                badgeColor = Color(0xFF3B82F6),
                title = "FAQs & Help Center",
                subtitle = "Find answers to common\nquestions.",
                onClick = onFaqClick
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Form Title: Send us a message
            Text(
                text = "Send us a message",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Form Input 1: Name
            FormInputField(
                label = "Name",
                value = nameInput,
                onValueChange = { nameInput = it },
                placeholder = "Enter your name",
                icon = Icons.Outlined.Person
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Form Input 2: Email
            FormInputField(
                label = "Email",
                value = emailInput,
                onValueChange = { emailInput = it },
                placeholder = "Enter your email",
                icon = Icons.Outlined.Mail
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Form Input 3: Subject Dropdown
            Box(modifier = Modifier.fillMaxWidth()) {
                FormInputField(
                    label = "Subject",
                    value = selectedSubject,
                    onValueChange = { },
                    placeholder = "Select a subject",
                    icon = Icons.AutoMirrored.Outlined.Assignment,
                    trailingIcon = Icons.Default.KeyboardArrowDown,
                    readOnly = true,
                    onClick = { isSubjectDropdownOpen = true }
                )

                DropdownMenu(
                    expanded = isSubjectDropdownOpen,
                    onDismissRequest = { isSubjectDropdownOpen = false },
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    subjectOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(text = option) },
                            onClick = {
                                selectedSubject = option
                                isSubjectDropdownOpen = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Form Input 4: Message Text Area
            FormInputField(
                label = "Message",
                value = messageInput,
                onValueChange = { messageInput = it },
                placeholder = "Type your message here...",
                icon = Icons.Outlined.ChatBubbleOutline,
                singleLine = false,
                minHeight = 80.dp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Send Message Button
            Button(
                onClick = {
                    onSendMessageClick(nameInput, emailInput, selectedSubject, messageInput)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7)),
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
                        contentDescription = "Send Message",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Send Message",
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
fun SupportChannelCard(
    icon: ImageVector,
    badgeColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(20.dp))
            .background(Color.White)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(badgeColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827)
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color(0xFF6B7280),
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Open",
            tint = Color(0xFF9CA3AF),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun FormInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    trailingIcon: ImageVector? = null,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    minHeight: Dp = 56.dp,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val clickableModifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFEEF0FD))
            .then(clickableModifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color(0xFF374151),
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF6C5CE7)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Box(
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 14.sp,
                        color = Color(0xFF9CA3AF)
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    readOnly = readOnly,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (trailingIcon != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = trailingIcon,
                contentDescription = "Dropdown",
                tint = Color(0xFF374151),
                modifier = Modifier
                    .padding(top = 10.dp)
                    .size(20.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ContactSupportScreenPreview() {
    ContactSupportScreen()
}
