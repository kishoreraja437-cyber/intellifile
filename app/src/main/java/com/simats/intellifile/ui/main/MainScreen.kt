package com.simats.intellifile.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.intellifile.ui.about.AboutScreen
import com.simats.intellifile.ui.ai.AiChatHistoryScreen
import com.simats.intellifile.ui.ai.AiScreen
import com.simats.intellifile.ui.detail.FileDetailData
import com.simats.intellifile.ui.detail.FileDetailScreen
import com.simats.intellifile.ui.feedback.FeedbackScreen
import com.simats.intellifile.ui.feedback.FeedbackSuccessScreen
import com.simats.intellifile.ui.files.MyFilesScreen
import com.simats.intellifile.ui.home.HomeScreen
import com.simats.intellifile.ui.home.RecentFilesScreen
import com.simats.intellifile.ui.notifications.NotificationsScreen
import com.simats.intellifile.ui.permission.FilePermissionsDetailScreen
import com.simats.intellifile.ui.priority.SetPriorityScreen
import com.simats.intellifile.ui.profile.ProfileScreen
import com.simats.intellifile.ui.saved.SavedScreen
import com.simats.intellifile.ui.share.ShareFileScreen
import com.simats.intellifile.ui.support.ContactSupportScreen
import com.simats.intellifile.ui.support.ContactUsSuccessScreen
import com.simats.intellifile.ui.support.HelpSupportScreen
import com.simats.intellifile.ui.theme.AppThemeMode
import com.simats.intellifile.ui.theme.ThemeSettingsScreen
import com.simats.intellifile.ui.viewer.DocumentViewerScreen

enum class BottomNavItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    AI("AI", Icons.Filled.AccountTree, Icons.Outlined.AccountTree),
    FILES("Files", Icons.Filled.Folder, Icons.Outlined.Folder),
    SAVED("Saved", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
    PROFILE("Profile", Icons.Filled.Person, Icons.Outlined.Person)
}

@Composable
fun MainScreen(
    onLogout: () -> Unit = {},
    currentThemeMode: AppThemeMode = AppThemeMode.LIGHT,
    onThemeChanged: (AppThemeMode) -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(BottomNavItem.HOME) }
    var selectedDetailFile by remember { mutableStateOf<FileDetailData?>(null) }
    var isSetPriorityOpen by remember { mutableStateOf(false) }
    var isViewerOpen by remember { mutableStateOf(false) }
    var showSaveConfirmation by remember { mutableStateOf(false) }
    var isFilePermissionsOpen by remember { mutableStateOf(false) }
    var isNotificationsOpen by remember { mutableStateOf(false) }
    var isThemeOpen by remember { mutableStateOf(false) }
    var isAboutOpen by remember { mutableStateOf(false) }
    var isHelpSupportOpen by remember { mutableStateOf(false) }
    var isContactSupportOpen by remember { mutableStateOf(false) }
    var isFeedbackOpen by remember { mutableStateOf(false) }
    var isAiHistoryOpen by remember { mutableStateOf(false) }
    var isShareOpen by remember { mutableStateOf(false) }
    var isContactSuccessOpen by remember { mutableStateOf(false) }
    var isFeedbackSuccessOpen by remember { mutableStateOf(false) }
    var isRecentFilesOpen by remember { mutableStateOf(false) }

    if (isRecentFilesOpen) {
        BackHandler { isRecentFilesOpen = false }
        RecentFilesScreen(
            onBackClick = { isRecentFilesOpen = false },
            onFileClick = { file ->
                selectedDetailFile = FileDetailData(
                    fileName = file.title,
                    fileSize = file.size,
                    fileType = "${file.format} Document",
                    createdDate = "Oct 5, 2024",
                    modifiedDate = "Oct 5, 2024",
                    category = "Assignment",
                    location = "Internal Storage",
                    priority = file.priority
                )
            },
            onShareFileClick = { file ->
                selectedDetailFile = FileDetailData(
                    fileName = file.title,
                    fileSize = file.size,
                    fileType = "${file.format} Document",
                    priority = file.priority
                )
                isShareOpen = true
            }
        )
    } else if (isFeedbackSuccessOpen) {
        BackHandler { isFeedbackSuccessOpen = false }
        FeedbackSuccessScreen(
            onBackClick = { isFeedbackSuccessOpen = false },
            onBackToFeedbackClick = { isFeedbackSuccessOpen = false },
            onGoToHomeClick = {
                isFeedbackSuccessOpen = false
                isFeedbackOpen = false
                isHelpSupportOpen = false
                selectedTab = BottomNavItem.HOME
            }
        )
    } else if (isContactSuccessOpen) {
        BackHandler { isContactSuccessOpen = false }
        ContactUsSuccessScreen(
            onBackClick = { isContactSuccessOpen = false },
            onBackToContactUsClick = { isContactSuccessOpen = false },
            onGoToHomeClick = {
                isContactSuccessOpen = false
                isContactSupportOpen = false
                isHelpSupportOpen = false
                selectedTab = BottomNavItem.HOME
            }
        )
    } else if (isShareOpen) {
        BackHandler { isShareOpen = false }
        ShareFileScreen(
            fileName = selectedDetailFile?.fileName ?: "AML Assignment.pdf",
            onBackClick = { isShareOpen = false },
            onShareOptionClick = { _ -> isShareOpen = false }
        )
    } else if (isAiHistoryOpen) {
        BackHandler { isAiHistoryOpen = false }
        AiChatHistoryScreen(
            onBackClick = { isAiHistoryOpen = false }
        )
    } else if (isFeedbackOpen) {
        BackHandler { isFeedbackOpen = false }
        FeedbackScreen(
            onBackClick = { isFeedbackOpen = false },
            onSubmitFeedbackClick = { _, _, _, _ -> isFeedbackSuccessOpen = true }
        )
    } else if (isContactSupportOpen) {
        BackHandler { isContactSupportOpen = false }
        ContactSupportScreen(
            onBackClick = { isContactSupportOpen = false },
            onSendMessageClick = { _, _, _, _ -> isContactSuccessOpen = true }
        )
    } else if (isHelpSupportOpen) {
        BackHandler { isHelpSupportOpen = false }
        HelpSupportScreen(
            onBackClick = { isHelpSupportOpen = false },
            onContactSupportClick = { isContactSupportOpen = true },
            onSendFeedbackClick = { isFeedbackOpen = true }
        )
    } else if (isAboutOpen) {
        BackHandler { isAboutOpen = false }
        AboutScreen(
            onBackClick = { isAboutOpen = false }
        )
    } else if (isThemeOpen) {
        BackHandler { isThemeOpen = false }
        ThemeSettingsScreen(
            initialThemeMode = currentThemeMode,
            onBackClick = { isThemeOpen = false },
            onSaveTheme = { newTheme ->
                onThemeChanged(newTheme)
                isThemeOpen = false
            }
        )
    } else if (isNotificationsOpen) {
        BackHandler { isNotificationsOpen = false }
        NotificationsScreen(
            onBackClick = { isNotificationsOpen = false },
            onSaveSettingsClick = { isNotificationsOpen = false }
        )
    } else if (isFilePermissionsOpen) {
        BackHandler { isFilePermissionsOpen = false }
        FilePermissionsDetailScreen(
            onBackClick = { isFilePermissionsOpen = false }
        )
    } else if (isSetPriorityOpen && selectedDetailFile != null) {
        BackHandler { isSetPriorityOpen = false }
        SetPriorityScreen(
            fileName = selectedDetailFile!!.fileName,
            fileSize = selectedDetailFile!!.fileSize,
            fileFormat = selectedDetailFile!!.fileType.substringBefore(" "),
            initialPriority = selectedDetailFile!!.priority,
            onBackClick = { isSetPriorityOpen = false },
            onSavePriority = { newPriority ->
                selectedDetailFile = selectedDetailFile!!.copy(priority = newPriority)
                isSetPriorityOpen = false
                showSaveConfirmation = true
            }
        )
    } else if (isViewerOpen && selectedDetailFile != null) {
        BackHandler { isViewerOpen = false }
        DocumentViewerScreen(
            fileName = selectedDetailFile!!.fileName,
            showSaveConfirmation = showSaveConfirmation,
            onBackClick = {
                isViewerOpen = false
                showSaveConfirmation = false
            },
            onShareClick = { isShareOpen = true },
            onSetPriorityClick = { isSetPriorityOpen = true },
            onConfirmSave = {
                showSaveConfirmation = false
                isViewerOpen = false
                selectedDetailFile = null
                selectedTab = BottomNavItem.SAVED
            },
            onDismissSaveConfirmation = { showSaveConfirmation = false }
        )
    } else if (selectedDetailFile != null) {
        BackHandler { selectedDetailFile = null }
        FileDetailScreen(
            fileData = selectedDetailFile!!,
            onBackClick = { selectedDetailFile = null },
            onOpenFileClick = { isViewerOpen = true },
            onShareClick = { isShareOpen = true },
            onSetPriorityClick = {
                isViewerOpen = true
                isSetPriorityOpen = true
            },
            onMoreOptionsClick = {
                isViewerOpen = true
                isSetPriorityOpen = true
            }
        )
    } else {
        if (selectedTab != BottomNavItem.HOME) {
            BackHandler { selectedTab = BottomNavItem.HOME }
        }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                BottomNavigationBar(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedTab) {
                    BottomNavItem.HOME -> HomeScreen(
                        onSeeAllClick = { isRecentFilesOpen = true },
                        onMicClick = { selectedTab = BottomNavItem.AI },
                        onPriorityCardClick = { _ ->
                            selectedTab = BottomNavItem.FILES
                        },
                        onFileClick = { file ->
                            selectedDetailFile = FileDetailData(
                                fileName = file.title,
                                fileSize = file.size,
                                fileType = "${file.format} Document",
                                createdDate = "Oct 5, 2024",
                                modifiedDate = "Oct 5, 2024",
                                category = "Assignment",
                                location = "Internal Storage",
                                priority = file.priority
                            )
                        }
                    )
                    BottomNavItem.AI -> AiScreen(
                        onBackClick = { selectedTab = BottomNavItem.HOME },
                        onViewSearchHistoryClick = { isAiHistoryOpen = true },
                        onOpenFile = { file ->
                            selectedDetailFile = FileDetailData(
                                fileName = file.title,
                                fileSize = file.size,
                                fileType = "${file.format} Document",
                                createdDate = file.dateText,
                                modifiedDate = file.dateText,
                                category = "Assignment",
                                location = "Internal Storage",
                                priority = file.priority
                            )
                        }
                    )
                    BottomNavItem.FILES -> MyFilesScreen(
                        onBackClick = { selectedTab = BottomNavItem.HOME },
                        onQuickAccessClick = { _ ->
                            selectedTab = BottomNavItem.SAVED
                        }
                    )
                    BottomNavItem.SAVED -> SavedScreen(
                        onBackClick = { selectedTab = BottomNavItem.HOME },
                        onOpenFile = { file ->
                            selectedDetailFile = FileDetailData(
                                fileName = file.title,
                                fileSize = file.size,
                                fileType = "PDF Document",
                                createdDate = file.dateText,
                                modifiedDate = file.dateText,
                                category = "Saved Document",
                                location = "Internal Storage",
                                priority = file.priority
                            )
                        },
                        onShareFileClick = { file ->
                            selectedDetailFile = FileDetailData(
                                fileName = file.title,
                                fileSize = file.size,
                                fileType = "PDF Document",
                                priority = file.priority
                            )
                            isShareOpen = true
                        }
                    )
                    BottomNavItem.PROFILE -> ProfileScreen(
                        onBackClick = { selectedTab = BottomNavItem.HOME },
                        onFilePermissionsClick = { isFilePermissionsOpen = true },
                        onNotificationsClick = { isNotificationsOpen = true },
                        onThemeClick = { isThemeOpen = true },
                        onAboutClick = { isAboutOpen = true },
                        onHelpSupportClick = { isHelpSupportOpen = true },
                        onLogoutClick = onLogout
                    )
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    selectedTab: BottomNavItem,
    onTabSelected: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        HorizontalDivider(
            color = Color(0xFFF1F5F9),
            thickness = 1.dp
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem.entries.forEach { item ->
                val isSelected = item == selectedTab
                val contentColor = if (isSelected) Color(0xFF7C3AED) else Color(0xFF9CA3AF)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onTabSelected(item) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.title,
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = item.title,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
fun PlaceholderTabContent(
    title: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FE)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF111827)
        )
    }
}

@Preview
@Composable
fun MainScreenPreview() {
    MainScreen()
}
