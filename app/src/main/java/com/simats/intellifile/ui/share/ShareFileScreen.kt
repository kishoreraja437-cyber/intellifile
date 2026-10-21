package com.simats.intellifile.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class AppShareTarget(
    val name: String,
    val iconBgColor: Color,
    val icon: ImageVector
)

data class ShareContactItem(
    val id: String,
    val name: String,
    val email: String,
    val avatarBgColor: Color,
    val avatarInitial: String,
    val permission: String = "View Only"
)

@Composable
fun ShareFileScreen(
    fileName: String = "AML Assignment.pdf",
    onBackClick: () -> Unit = {},
    onShareOptionClick: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    val selectedContactIds = remember { mutableStateListOf("1", "2", "3") }

    val appTargets = remember {
        listOf(
            AppShareTarget("WhatsApp", Color(0xFF25D366), Icons.Outlined.Share),
            AppShareTarget("Instagram", Color(0xFFE1306C), Icons.Outlined.Share),
            AppShareTarget("Gmail", Color(0xFFEA4335), Icons.Outlined.Mail),
            AppShareTarget("Telegram", Color(0xFF229ED9), Icons.Outlined.Share),
            AppShareTarget("Bluetooth", Color(0xFF0082FC), Icons.Outlined.Bluetooth),
            AppShareTarget("More", Color(0xFFEEF0FD), Icons.Default.MoreHoriz)
        )
    }

    val contacts = remember {
        listOf(
            ShareContactItem("1", "Arun Kumar", "arun@example.com", Color(0xFFC7D2FE), "A"),
            ShareContactItem("2", "Sneha", "sneha@example.com", Color(0xFFBFDBFE), "S"),
            ShareContactItem("3", "Vignesh", "vignesh@example.com", Color(0xFFBBF7D0), "V")
        )
    }

    val filteredContacts = contacts.filter {
        it.name.contains(searchQuery, ignoreCase = true) || it.email.contains(searchQuery, ignoreCase = true)
    }

    Box(
        modifier = Modifier
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
                    .padding(top = 12.dp, bottom = 12.dp),
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
                    text = "Share File",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Share Cards 2x2 Grid / Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickShareCard(
                    title = "Share with\nPeople",
                    icon = Icons.Outlined.GroupAdd,
                    onClick = { onShareOptionClick("Share with People") },
                    modifier = Modifier.weight(1f)
                )
                QuickShareCard(
                    title = "Copy Link",
                    icon = Icons.Outlined.ContentCopy,
                    onClick = { onShareOptionClick("Copy Link") },
                    modifier = Modifier.weight(1f)
                )
                QuickShareCard(
                    title = "QR Code",
                    icon = Icons.Outlined.QrCode,
                    onClick = { onShareOptionClick("QR Code") },
                    modifier = Modifier.weight(1f)
                )
                QuickShareCard(
                    title = "Share via\nApps",
                    icon = Icons.Outlined.Share,
                    onClick = { onShareOptionClick("Share via Apps") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Share via Apps Sub-header
            Text(
                text = "Share via Apps",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF6B7280)
            )

            HorizontalDivider(
                color = Color(0xFFF1F5F9),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // App Icons Scroll Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(appTargets) { target ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onShareOptionClick(target.name) }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(target.iconBgColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = target.icon,
                                contentDescription = target.name,
                                tint = if (target.name == "More") Color(0xFF6C5CE7) else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = target.name,
                            fontSize = 11.sp,
                            color = Color(0xFF374151),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Add People Header
            Text(
                text = "Add People",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Search Input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEEF0FD))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Color(0xFF6C5CE7),
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search by name or email...",
                            fontSize = 14.sp,
                            color = Color(0xFF9CA3AF)
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Recent Contacts Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Contacts",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )

                Text(
                    text = "See All",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF6C5CE7),
                    modifier = Modifier.clickable { }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Contacts List
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                filteredContacts.forEach { contact ->
                    val isChecked = selectedContactIds.contains(contact.id)
                    ContactShareRow(
                        contact = contact,
                        isChecked = isChecked,
                        onCheckedChange = { checked ->
                            if (checked) {
                                if (!selectedContactIds.contains(contact.id)) selectedContactIds.add(contact.id)
                            } else {
                                selectedContactIds.remove(contact.id)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun QuickShareCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFEEF0FD))
            .clickable { onClick() }
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = Color(0xFF6C5CE7),
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6C5CE7),
            textAlign = TextAlign.Center,
            lineHeight = 14.sp
        )
    }
}

@Composable
fun ContactShareRow(
    contact: ShareContactItem,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPermission by remember { mutableStateOf(contact.permission) }
    var isPermissionDropdownOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(contact.avatarBgColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.avatarInitial,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E1B4B)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = contact.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = contact.email,
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Permission Dropdown Pill
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFEEF0FD))
                    .clickable { isPermissionDropdownOpen = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentPermission,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF6C5CE7)
                )

                Spacer(modifier = Modifier.width(4.dp))

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Permission Options",
                    tint = Color(0xFF6C5CE7),
                    modifier = Modifier.size(14.dp)
                )
            }

            DropdownMenu(
                expanded = isPermissionDropdownOpen,
                onDismissRequest = { isPermissionDropdownOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text("View Only") },
                    onClick = {
                        currentPermission = "View Only"
                        isPermissionDropdownOpen = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Can Edit") },
                    onClick = {
                        currentPermission = "Can Edit"
                        isPermissionDropdownOpen = false
                    }
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Checkbox
        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF6C5CE7),
                uncheckedColor = Color(0xFFD1D5DB)
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ShareFileScreenPreview() {
    ShareFileScreen()
}
