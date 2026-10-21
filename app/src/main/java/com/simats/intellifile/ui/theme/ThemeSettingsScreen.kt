package com.simats.intellifile.ui.theme

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
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AppThemeMode(val title: String, val description: String) {
    LIGHT("Light Theme", "Clean and bright interface for daytime use."),
    DARK("Dark Theme", "Darker interface for low-light environments."),
    SYSTEM("System Default", "Follows your device's system theme.")
}

@Composable
fun ThemeSettingsScreen(
    modifier: Modifier = Modifier,
    initialThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    onBackClick: () -> Unit = {},
    onSaveTheme: (AppThemeMode) -> Unit = {}
) {
    var selectedTheme by remember { mutableStateOf(initialThemeMode) }

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
                    text = "Theme",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sub-heading Description
            Text(
                text = "Choose your preferred app appearance.",
                fontSize = 14.sp,
                color = Color(0xFF4B5563)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Theme Option 1: Light Theme
            ThemeCardItem(
                themeMode = AppThemeMode.LIGHT,
                icon = Icons.Outlined.LightMode,
                isSelected = selectedTheme == AppThemeMode.LIGHT,
                onSelect = { selectedTheme = AppThemeMode.LIGHT },
                previewContent = { LightThemePreviewMock() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Theme Option 2: Dark Theme
            ThemeCardItem(
                themeMode = AppThemeMode.DARK,
                icon = Icons.Outlined.DarkMode,
                isSelected = selectedTheme == AppThemeMode.DARK,
                onSelect = { selectedTheme = AppThemeMode.DARK },
                previewContent = { DarkThemePreviewMock() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Theme Option 3: System Default
            ThemeCardItem(
                themeMode = AppThemeMode.SYSTEM,
                icon = Icons.Outlined.Smartphone,
                isSelected = selectedTheme == AppThemeMode.SYSTEM,
                onSelect = { selectedTheme = AppThemeMode.SYSTEM },
                previewContent = { SystemThemePreviewMock() }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Save Button
            Button(
                onClick = { onSaveTheme(selectedTheme) },
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
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Save",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ThemeCardItem(
    themeMode: AppThemeMode,
    icon: ImageVector,
    isSelected: Boolean,
    onSelect: () -> Unit,
    previewContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderModifier = if (isSelected) {
        Modifier.border(
            width = 1.5.dp,
            color = Color(0xFF6C5CE7),
            shape = RoundedCornerShape(20.dp)
        )
    } else Modifier

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .then(borderModifier)
            .clickable { onSelect() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEEF0FD)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = themeMode.title,
                    tint = Color(0xFF6C5CE7),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = themeMode.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = themeMode.description,
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280),
                    lineHeight = 16.sp
                )
            }

            RadioButton(
                selected = isSelected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF6C5CE7))
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Mini Theme UI Mockup Graphic
        previewContent()
    }
}

@Composable
fun LightThemePreviewMock() {
    Box(
        modifier = Modifier
            .width(68.dp)
            .height(54.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFEEF2FF))
            .padding(6.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF6C5CE7))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFC7D2FE))
            )
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF4338CA))
            )
        }
    }
}

@Composable
fun DarkThemePreviewMock() {
    Box(
        modifier = Modifier
            .width(68.dp)
            .height(54.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0F172A))
            .padding(6.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF6C5CE7))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1E293B))
            )
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF4338CA))
            )
        }
    }
}

@Composable
fun SystemThemePreviewMock() {
    Row(
        modifier = Modifier
            .width(68.dp)
            .height(54.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFEEF2FF))
    ) {
        // Light half
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xFFEEF2FF))
                .padding(4.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF6C5CE7))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFFC7D2FE))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF4338CA))
                )
            }
        }

        // Dark half
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xFF0F172A))
                .padding(4.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF6C5CE7))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF1E293B))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF4338CA))
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ThemeSettingsScreenPreview() {
    ThemeSettingsScreen()
}
