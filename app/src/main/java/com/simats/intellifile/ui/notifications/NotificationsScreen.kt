package com.simats.intellifile.ui.notifications

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.intellifile.data.model.UserSettingsDto
import com.simats.intellifile.data.repository.AuthRepository
import kotlinx.coroutines.launch

@Composable
fun NotificationsScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onSaveSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authRepo = remember { AuthRepository(context) }

    var importantAlertsEnabled by remember { mutableStateOf(true) }
    var deadlineRemindersEnabled by remember { mutableStateOf(true) }
    var newDocumentAlertsEnabled by remember { mutableStateOf(true) }
    var quietHoursEnabled by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoading = true
        val result = authRepo.getSettings()
        isLoading = false
        result.onSuccess { settings ->
            importantAlertsEnabled = settings.important_alerts
            deadlineRemindersEnabled = settings.deadline_reminders
            newDocumentAlertsEnabled = settings.new_doc_alerts
            quietHoursEnabled = settings.quiet_hours
        }
    }

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
                    text = "Notifications",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sub-heading Description
            Text(
                text = "Stay updated with important files,\ndeadlines and new documents.",
                fontSize = 14.sp,
                color = Color(0xFF4B5563),
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Option 1: Important file alerts
            NotificationToggleCard(
                icon = Icons.Default.PriorityHigh,
                iconBackgroundColor = Color(0xFFEF4444),
                badgeContainerColor = Color(0xFFFFEAEE),
                title = "Important file alerts",
                description = "Get notified when files are marked as important.",
                checked = importantAlertsEnabled,
                onCheckedChange = { importantAlertsEnabled = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Option 2: Deadline reminders
            NotificationToggleCard(
                icon = Icons.Outlined.AccessTime,
                iconBackgroundColor = Color(0xFFD97706),
                badgeContainerColor = Color(0xFFFFF6D8),
                title = "Deadline reminders",
                description = "Receive reminders for upcoming deadlines.",
                checked = deadlineRemindersEnabled,
                onCheckedChange = { deadlineRemindersEnabled = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Option 3: New document alerts
            NotificationToggleCard(
                icon = Icons.Outlined.Description,
                iconBackgroundColor = MaterialTheme.colorScheme.primary,
                badgeContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                title = "New document alerts",
                description = "Notify when new files are scanned or added.",
                checked = newDocumentAlertsEnabled,
                onCheckedChange = { newDocumentAlertsEnabled = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Option 4: Quiet hours
            NotificationToggleCard(
                icon = Icons.Outlined.DarkMode,
                iconBackgroundColor = Color(0xFF4B5563),
                badgeContainerColor = Color(0xFFF3F4F6),
                title = "Quiet hours",
                description = "Pause alerts during late hours.",
                checked = quietHoursEnabled,
                onCheckedChange = { quietHoursEnabled = it }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Save Settings Button
            Button(
                onClick = {
                    coroutineScope.launch {
                        isSaving = true
                        val newSettings = UserSettingsDto(
                            important_alerts = importantAlertsEnabled,
                            deadline_reminders = deadlineRemindersEnabled,
                            new_doc_alerts = newDocumentAlertsEnabled,
                            quiet_hours = quietHoursEnabled
                        )
                        val result = authRepo.updateSettings(newSettings)
                        isSaving = false
                        result.onSuccess {
                            Toast.makeText(context, "Notification preferences saved", Toast.LENGTH_SHORT).show()
                            onSaveSettingsClick()
                        }.onFailure { ex ->
                            Toast.makeText(context, ex.localizedMessage ?: "Failed to save settings", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save Settings",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Save Settings",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun NotificationToggleCard(
    icon: ImageVector,
    iconBackgroundColor: Color,
    badgeContainerColor: Color,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(badgeContainerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconBackgroundColor,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

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
                text = description,
                fontSize = 12.sp,
                color = Color(0xFF6B7280),
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFE5E7EB)
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun NotificationsScreenPreview() {
    NotificationsScreen()
}
