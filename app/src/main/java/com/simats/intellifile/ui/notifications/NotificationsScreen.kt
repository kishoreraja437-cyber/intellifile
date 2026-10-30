package com.simats.intellifile.ui.notifications

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Bedtime
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
import com.simats.intellifile.data.manager.NotificationHelper
import com.simats.intellifile.data.model.UserSettingsDto
import com.simats.intellifile.data.repository.AuthRepository
import com.simats.intellifile.data.session.SessionManager
import kotlinx.coroutines.launch

@Composable
fun NotificationsScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onSaveSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sessionManager = remember { SessionManager(context) }
    val authRepo = remember { AuthRepository(context) }

    // Initialize immediately from local session to prevent UI flicker
    val initialSettings = remember { sessionManager.getNotificationSettings() }
    var importantAlertsEnabled by remember { mutableStateOf(initialSettings.important_alerts) }
    var deadlineRemindersEnabled by remember { mutableStateOf(initialSettings.deadline_reminders) }
    var newDocumentAlertsEnabled by remember { mutableStateOf(initialSettings.new_doc_alerts) }
    var quietHoursEnabled by remember { mutableStateOf(initialSettings.quiet_hours) }

    var isLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // Fetch up-to-date user settings from backend
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

    // Helper to auto-save to SessionManager and background sync
    fun onSettingChanged(
        important: Boolean = importantAlertsEnabled,
        deadlines: Boolean = deadlineRemindersEnabled,
        newDocs: Boolean = newDocumentAlertsEnabled,
        quiet: Boolean = quietHoursEnabled
    ) {
        sessionManager.saveNotificationSettings(
            importantAlerts = important,
            deadlineReminders = deadlines,
            newDocAlerts = newDocs,
            quietHours = quiet
        )
        coroutineScope.launch {
            authRepo.updateSettings(
                UserSettingsDto(
                    important_alerts = important,
                    deadline_reminders = deadlines,
                    new_doc_alerts = newDocs,
                    quiet_hours = quiet
                )
            )
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
                onCheckedChange = { checked ->
                    importantAlertsEnabled = checked
                    onSettingChanged(important = checked)
                }
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
                onCheckedChange = { checked ->
                    deadlineRemindersEnabled = checked
                    onSettingChanged(deadlines = checked)
                }
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
                onCheckedChange = { checked ->
                    newDocumentAlertsEnabled = checked
                    onSettingChanged(newDocs = checked)
                }
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
                onCheckedChange = { checked ->
                    quietHoursEnabled = checked
                    onSettingChanged(quiet = checked)
                }
            )

            // Expandable Quiet Hours schedule card
            AnimatedVisibility(
                visible = quietHoursEnabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF3F4F6))
                        .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Bedtime,
                            contentDescription = "Quiet Hours Active",
                            tint = Color(0xFF4B5563),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Quiet Schedule: 10:00 PM – 7:00 AM",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1F2937)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Notification popups and sound alerts are automatically silenced during these hours so you can rest undisturbed.",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280),
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action: Test Notification
            OutlinedButton(
                onClick = {
                    NotificationHelper.sendTestNotification(context, "Important File")
                    Toast.makeText(context, "Test notification triggered!", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Test Notification",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Send Test Notification",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

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
                            Toast.makeText(context, "Notification preferences saved successfully", Toast.LENGTH_SHORT).show()
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (checked) Color(0xFFDEF7EC) else Color(0xFFF3F4F6))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (checked) "ON" else "OFF",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (checked) Color(0xFF03543F) else Color(0xFF6B7280)
                    )
                }
            }

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
