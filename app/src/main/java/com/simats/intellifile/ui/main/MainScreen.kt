package com.simats.intellifile.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import com.simats.intellifile.R
import com.simats.intellifile.data.local.LocalFileDatabaseHelper
import com.simats.intellifile.data.manager.LocalFileManager
import com.simats.intellifile.data.model.LocalFileItem
import com.simats.intellifile.ui.about.AboutScreen
import com.simats.intellifile.ui.ai.AiChatHistoryScreen
import com.simats.intellifile.ui.ai.AiScreen
import com.simats.intellifile.ui.detail.FileDetailData
import com.simats.intellifile.ui.detail.FileDetailScreen
import com.simats.intellifile.ui.feedback.FeedbackScreen
import com.simats.intellifile.ui.feedback.FeedbackSuccessScreen
import com.simats.intellifile.ui.files.MyFilesScreen
import com.simats.intellifile.ui.home.HomeScreen
import com.simats.intellifile.ui.home.PriorityLevel
import com.simats.intellifile.ui.home.RecentFilesScreen
import com.simats.intellifile.ui.notifications.NotificationsScreen
import com.simats.intellifile.ui.permission.FilePermissionsDetailScreen
import com.simats.intellifile.ui.priority.SetPriorityScreen
import com.simats.intellifile.ui.profile.ProfileScreen
import com.simats.intellifile.ui.saved.FileSavedSuccessScreen
import com.simats.intellifile.ui.saved.PriorityFilter
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
    HOME("Home", Icons.Default.Home, Icons.Outlined.Home),
    AI("AI", Icons.Default.AutoAwesome, Icons.Outlined.AutoAwesome),
    FILES("Files", Icons.Default.Folder, Icons.Outlined.Folder),
    SAVED("Saved", Icons.Default.Favorite, Icons.Outlined.FavoriteBorder),
    PROFILE("Profile", Icons.Default.Person, Icons.Outlined.Person)
}

@Composable
fun MainScreen(
    currentThemeMode: AppThemeMode = AppThemeMode.LIGHT,
    onThemeChanged: (AppThemeMode) -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(BottomNavItem.HOME) }
    var savedPriorityFilter by remember { mutableStateOf(PriorityFilter.ALL) }

    // Secondary Screen States
    var isRecentFilesOpen by remember { mutableStateOf(false) }
    var isAiHistoryOpen by remember { mutableStateOf(false) }
    var selectedDetailFile by remember { mutableStateOf<FileDetailData?>(null) }
    var isDocumentViewerOpen by remember { mutableStateOf(false) }
    var isSetPriorityOpen by remember { mutableStateOf(false) }
    var isFileSavedSuccessOpen by remember { mutableStateOf(false) }
    var isShareOpen by remember { mutableStateOf(false) }
    var isFilePermissionsOpen by remember { mutableStateOf(false) }
    var isNotificationsOpen by remember { mutableStateOf(false) }
    var isThemeOpen by remember { mutableStateOf(false) }
    var isAboutOpen by remember { mutableStateOf(false) }
    var isHelpSupportOpen by remember { mutableStateOf(false) }
    var isContactSupportOpen by remember { mutableStateOf(false) }
    var isContactSuccessOpen by remember { mutableStateOf(false) }
    var isFeedbackOpen by remember { mutableStateOf(false) }
    var isFeedbackSuccessOpen by remember { mutableStateOf(false) }

    // System Back Press Handler
    BackHandler(
        enabled = isRecentFilesOpen || isAiHistoryOpen || selectedDetailFile != null ||
                isDocumentViewerOpen || isSetPriorityOpen || isFileSavedSuccessOpen || isShareOpen || isFilePermissionsOpen ||
                isNotificationsOpen || isThemeOpen || isAboutOpen ||
                isHelpSupportOpen || isContactSupportOpen || isContactSuccessOpen ||
                isFeedbackOpen || isFeedbackSuccessOpen || selectedTab != BottomNavItem.HOME
    ) {
        when {
            isShareOpen -> isShareOpen = false
            isFileSavedSuccessOpen -> isFileSavedSuccessOpen = false
            isDocumentViewerOpen -> isDocumentViewerOpen = false
            isFeedbackSuccessOpen -> isFeedbackSuccessOpen = false
            isFeedbackOpen -> isFeedbackOpen = false
            isContactSuccessOpen -> isContactSuccessOpen = false
            isContactSupportOpen -> isContactSupportOpen = false
            isHelpSupportOpen -> isHelpSupportOpen = false
            isAboutOpen -> isAboutOpen = false
            isThemeOpen -> isThemeOpen = false
            isNotificationsOpen -> isNotificationsOpen = false
            isFilePermissionsOpen -> isFilePermissionsOpen = false
            isSetPriorityOpen -> isSetPriorityOpen = false
            selectedDetailFile != null -> selectedDetailFile = null
            isAiHistoryOpen -> isAiHistoryOpen = false
            isRecentFilesOpen -> isRecentFilesOpen = false
            selectedTab != BottomNavItem.HOME -> selectedTab = BottomNavItem.HOME
        }
    }

    Scaffold(
        bottomBar = {
            BottomNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { newTab ->
                    // Reset all secondary overlay screen states when switching bottom tabs
                    isRecentFilesOpen = false
                    isAiHistoryOpen = false
                    selectedDetailFile = null
                    isDocumentViewerOpen = false
                    isSetPriorityOpen = false
                    isFileSavedSuccessOpen = false
                    isShareOpen = false
                    isFilePermissionsOpen = false
                    isNotificationsOpen = false
                    isThemeOpen = false
                    isAboutOpen = false
                    isHelpSupportOpen = false
                    isContactSupportOpen = false
                    isContactSuccessOpen = false
                    isFeedbackOpen = false
                    isFeedbackSuccessOpen = false

                    if (newTab == BottomNavItem.SAVED) {
                        savedPriorityFilter = PriorityFilter.ALL
                    }
                    selectedTab = newTab
                }
            )
        }
    ) { innerPadding ->
        val currentNavigationKey = when {
            isShareOpen -> "share"
            isFileSavedSuccessOpen -> "file_saved_success"
            isDocumentViewerOpen -> "document_viewer"
            isFeedbackSuccessOpen -> "feedback_success"
            isFeedbackOpen -> "feedback"
            isContactSuccessOpen -> "contact_success"
            isContactSupportOpen -> "contact_support"
            isHelpSupportOpen -> "help_support"
            isAboutOpen -> "about"
            isThemeOpen -> "theme"
            isNotificationsOpen -> "notifications"
            isFilePermissionsOpen -> "file_permissions"
            isSetPriorityOpen -> "set_priority"
            selectedDetailFile != null -> "file_detail_${selectedDetailFile?.fileName}"
            isAiHistoryOpen -> "ai_history"
            isRecentFilesOpen -> "recent_files"
            else -> "tab_${selectedTab.name}"
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentNavigationKey,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.96f, animationSpec = tween(300)) togetherWith
                            fadeOut(animationSpec = tween(200))
                },
                label = "mainScreenTransition"
            ) { navKey ->
                when (navKey) {
                    "recent_files" -> RecentFilesScreen(
                        onBackClick = { isRecentFilesOpen = false },
                        onFileClick = { file ->
                            selectedDetailFile = FileDetailData(
                                fileName = file.title,
                                fileSize = file.size,
                                fileType = "${file.format} Document",
                                createdDate = file.dateText,
                                modifiedDate = file.dateText,
                                priority = file.priority,
                                iconResId = file.iconResId,
                                uriString = file.uriString,
                                path = file.path
                            )
                        },
                        onShareFileClick = { file ->
                            selectedDetailFile = FileDetailData(
                                fileName = file.title,
                                fileSize = file.size,
                                fileType = "${file.format} Document",
                                priority = file.priority,
                                iconResId = file.iconResId,
                                uriString = file.uriString,
                                path = file.path
                            )
                            isShareOpen = true
                        }
                    )

                    "document_viewer" -> DocumentViewerScreen(
                        fileName = selectedDetailFile?.fileName ?: "Document.pdf",
                        filePath = selectedDetailFile?.path ?: "",
                        fileUriString = selectedDetailFile?.uriString ?: "",
                        fileFormat = selectedDetailFile?.fileType?.substringBefore(" ") ?: "PDF",
                        totalPages = 1,
                        onBackClick = { isDocumentViewerOpen = false },
                        onShareClick = { isShareOpen = true },
                        onMoreClick = {
                            isDocumentViewerOpen = false
                            isSetPriorityOpen = true
                        },
                        onSetPriorityClick = {
                            isDocumentViewerOpen = false
                            isSetPriorityOpen = true
                        },
                        onConfirmSave = {
                            isDocumentViewerOpen = false
                            isSetPriorityOpen = true
                        }
                    )

                    "file_saved_success" -> FileSavedSuccessScreen(
                        fileName = selectedDetailFile?.fileName ?: "Document.pdf",
                        fileSize = selectedDetailFile?.fileSize ?: "0 KB",
                        priority = selectedDetailFile?.priority ?: PriorityLevel.NORMAL,
                        onBackClick = { isFileSavedSuccessOpen = false },
                        onViewSavedClick = {
                            isFileSavedSuccessOpen = false
                            selectedDetailFile = null
                            isDocumentViewerOpen = false
                            selectedTab = BottomNavItem.SAVED
                        },
                        onGoToHomeClick = {
                            isFileSavedSuccessOpen = false
                            selectedDetailFile = null
                            isDocumentViewerOpen = false
                            selectedTab = BottomNavItem.HOME
                        }
                    )

                    "ai_history" -> AiChatHistoryScreen(
                        onBackClick = { isAiHistoryOpen = false },
                        onClearHistoryClick = { /* Clear history */ },
                        onHistoryItemClick = { /* Load history */ }
                    )

                    "set_priority" -> SetPriorityScreen(
                        fileName = selectedDetailFile?.fileName ?: "Document.pdf",
                        fileSize = selectedDetailFile?.fileSize ?: "0 KB",
                        fileFormat = selectedDetailFile?.fileType?.substringBefore(" ") ?: "PDF",
                        iconResId = selectedDetailFile?.iconResId ?: R.drawable.pdf,
                        initialPriority = selectedDetailFile?.priority ?: PriorityLevel.NORMAL,
                        onBackClick = { isSetPriorityOpen = false },
                        onSavePriority = { newPriority ->
                            selectedDetailFile?.let { detail ->
                                val key = if (detail.path.isNotBlank()) detail.path else detail.uriString
                                LocalFileDatabaseHelper.getInstance(context).setPriority(
                                    uriOrPath = key,
                                    fileName = detail.fileName,
                                    priority = newPriority
                                )
                                selectedDetailFile = detail.copy(priority = newPriority)
                            }
                            isSetPriorityOpen = false
                            isFileSavedSuccessOpen = true
                        }
                    )

                    "share" -> ShareFileScreen(
                        fileName = selectedDetailFile?.fileName ?: "Document.pdf",
                        fileSize = selectedDetailFile?.fileSize ?: "0 KB",
                        fileFormat = selectedDetailFile?.fileType ?: "Document",
                        iconResId = selectedDetailFile?.iconResId ?: R.drawable.pdf,
                        filePath = selectedDetailFile?.path ?: "",
                        fileUriString = selectedDetailFile?.uriString ?: "",
                        onBackClick = { isShareOpen = false },
                        onShareOptionClick = { /* Share option clicked */ }
                    )

                    "file_permissions" -> FilePermissionsDetailScreen(
                        onBackClick = { isFilePermissionsOpen = false },
                        onManageAccessClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                try {
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            } else {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        },
                        onSelectFilesClick = { isRecentFilesOpen = true }
                    )

                    "notifications" -> NotificationsScreen(
                        onBackClick = { isNotificationsOpen = false },
                        onSaveSettingsClick = { isNotificationsOpen = false }
                    )

                    "theme" -> ThemeSettingsScreen(
                        initialThemeMode = currentThemeMode,
                        onBackClick = { isThemeOpen = false },
                        onSaveTheme = { newTheme ->
                            onThemeChanged(newTheme)
                            isThemeOpen = false
                        }
                    )

                    "about" -> AboutScreen(
                        onBackClick = { isAboutOpen = false }
                    )

                    "help_support" -> HelpSupportScreen(
                        onBackClick = { isHelpSupportOpen = false },
                        onContactSupportClick = { isContactSupportOpen = true },
                        onSendFeedbackClick = { isFeedbackOpen = true }
                    )

                    "contact_support" -> ContactSupportScreen(
                        onBackClick = { isContactSupportOpen = false },
                        onSendMessageClick = { _, _, _, _ ->
                            isContactSupportOpen = false
                            isContactSuccessOpen = true
                        }
                    )

                    "contact_success" -> ContactUsSuccessScreen(
                        onBackClick = { isContactSuccessOpen = false },
                        onBackToContactUsClick = { isContactSuccessOpen = false },
                        onGoToHomeClick = {
                            isContactSuccessOpen = false
                            selectedTab = BottomNavItem.HOME
                        }
                    )

                    "feedback" -> FeedbackScreen(
                        onBackClick = { isFeedbackOpen = false },
                        onSubmitFeedbackClick = { _, _, _, _ ->
                            isFeedbackOpen = false
                            isFeedbackSuccessOpen = true
                        }
                    )

                    "feedback_success" -> FeedbackSuccessScreen(
                        onBackClick = { isFeedbackSuccessOpen = false },
                        onBackToFeedbackClick = { isFeedbackSuccessOpen = false },
                        onGoToHomeClick = {
                            isFeedbackSuccessOpen = false
                            selectedTab = BottomNavItem.HOME
                        }
                    )

                    else -> when {
                        selectedDetailFile != null -> FileDetailScreen(
                            fileData = selectedDetailFile!!,
                            onBackClick = { selectedDetailFile = null },
                            onOpenFileClick = { isDocumentViewerOpen = true },
                            onShareClick = { isShareOpen = true },
                            onMoreOptionsClick = { isSetPriorityOpen = true },
                            onSetPriorityClick = { isSetPriorityOpen = true },
                            onPriorityChanged = { newPriority ->
                                selectedDetailFile = selectedDetailFile?.copy(priority = newPriority)
                            }
                        )

                        else -> when (selectedTab) {
                            BottomNavItem.HOME -> HomeScreen(
                                onSeeAllClick = { isRecentFilesOpen = true },
                                onFileClick = { file ->
                                    selectedDetailFile = FileDetailData(
                                        fileName = file.title,
                                        fileSize = file.size,
                                        fileType = "${file.format} Document",
                                        createdDate = file.dateText,
                                        modifiedDate = file.dateText,
                                        priority = file.priority,
                                        iconResId = file.iconResId,
                                        uriString = file.uriString,
                                        path = file.path
                                    )
                                },
                                onShareFileClick = { file ->
                                    selectedDetailFile = FileDetailData(
                                        fileName = file.title,
                                        fileSize = file.size,
                                        fileType = "${file.format} Document",
                                        createdDate = file.dateText,
                                        modifiedDate = file.dateText,
                                        priority = file.priority,
                                        iconResId = file.iconResId,
                                        uriString = file.uriString,
                                        path = file.path
                                    )
                                    isShareOpen = true
                                },
                                onPriorityCardClick = { priorityLevel ->
                                    savedPriorityFilter = when (priorityLevel) {
                                        PriorityLevel.IMPORTANT -> PriorityFilter.IMPORTANT
                                        PriorityLevel.NORMAL -> PriorityFilter.NORMAL
                                        PriorityLevel.LOW -> PriorityFilter.LOW
                                    }
                                    selectedTab = BottomNavItem.SAVED
                                },
                                onMicClick = { selectedTab = BottomNavItem.AI }
                            )
                            BottomNavItem.AI -> AiScreen(
                                onBackClick = { selectedTab = BottomNavItem.HOME },
                                onOpenFile = { aiFile ->
                                    selectedDetailFile = FileDetailData(
                                        fileName = aiFile.title,
                                        fileSize = aiFile.size,
                                        fileType = aiFile.format,
                                        createdDate = aiFile.dateText,
                                        modifiedDate = aiFile.dateText,
                                        priority = aiFile.priority,
                                        iconResId = aiFile.iconResId
                                    )
                                },
                                onViewSearchHistoryClick = { isAiHistoryOpen = true }
                            )
                            BottomNavItem.FILES -> MyFilesScreen(
                                onBackClick = { selectedTab = BottomNavItem.HOME },
                                onQuickAccessClick = { priorityLevel ->
                                    savedPriorityFilter = when (priorityLevel) {
                                        PriorityLevel.IMPORTANT -> PriorityFilter.IMPORTANT
                                        PriorityLevel.NORMAL -> PriorityFilter.NORMAL
                                        PriorityLevel.LOW -> PriorityFilter.LOW
                                    }
                                    selectedTab = BottomNavItem.SAVED
                                }
                            )
                            BottomNavItem.SAVED -> SavedScreen(
                                initialFilter = savedPriorityFilter,
                                onBackClick = { selectedTab = BottomNavItem.HOME },
                                onOpenFile = { file ->
                                    selectedDetailFile = FileDetailData(
                                        fileName = file.title,
                                        fileSize = file.size,
                                        fileType = "Document",
                                        createdDate = file.dateText,
                                        modifiedDate = file.dateText,
                                        category = "Saved Document",
                                        location = if (file.path.isNotBlank()) file.path else "Internal Storage",
                                        priority = file.priority,
                                        iconResId = file.iconResId,
                                        uriString = file.uriString,
                                        path = file.path
                                    )
                                },
                                onShareFileClick = { file ->
                                    selectedDetailFile = FileDetailData(
                                        fileName = file.title,
                                        fileSize = file.size,
                                        fileType = "Document",
                                        priority = file.priority,
                                        iconResId = file.iconResId,
                                        uriString = file.uriString,
                                        path = file.path
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
    }
}

@Composable
fun BottomNavigationBar(
    selectedTab: BottomNavItem,
    onTabSelected: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = BottomNavItem.entries
    val selectedIndex = items.indexOf(selectedTab)

    // Animated X index position for smooth fluid sliding transition across tabs
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "fluidNavAnimation"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
            .height(84.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Fluid Wavy Glass Background Canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(32.dp))
        ) {
            val width = size.width
            val height = size.height
            val itemWidth = width / items.size
            val activeX = itemWidth * animatedIndex + itemWidth / 2f

            // Organic Wavy Glass Path with Top Bulge at Active Tab Position
            val path = Path().apply {
                moveTo(0f, height)
                lineTo(0f, 16f)

                val bulgeWidth = 110.dp.toPx()
                val bulgeLeft = (activeX - bulgeWidth / 2f).coerceAtLeast(0f)
                val bulgeRight = (activeX + bulgeWidth / 2f).coerceAtMost(width)

                lineTo(bulgeLeft, 16f)

                // Smooth organic fluid curve going upwards
                cubicTo(
                    bulgeLeft + bulgeWidth * 0.25f, 16f,
                    activeX - bulgeWidth * 0.2f, -12f,
                    activeX, -12f
                )
                cubicTo(
                    activeX + bulgeWidth * 0.2f, -12f,
                    bulgeRight - bulgeWidth * 0.25f, 16f,
                    bulgeRight, 16f
                )

                lineTo(width, 16f)
                lineTo(width, height)
                close()
            }

            // Draw Light Off-White Glass Background
            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFEFF6FC)
                    )
                )
            )

            // Draw Glowing Cyan/Aqua Accent Outline
            drawPath(
                path = path,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFE2EEF8),
                        Color(0xFF55A9AF).copy(alpha = 0.5f),
                        Color(0xFF26E07F).copy(alpha = 0.6f),
                        Color(0xFF55A9AF).copy(alpha = 0.5f),
                        Color(0xFFE2EEF8)
                    )
                ),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Row of 5 Nav Items
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            items.forEachIndexed { _, item ->
                val isSelected = item == selectedTab

                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.15f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "iconScale"
                )

                val iconOffsetY by animateDpAsState(
                    targetValue = if (isSelected) (-16).dp else 0.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "iconOffsetY"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onTabSelected(item) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.offset(y = iconOffsetY)
                    ) {
                        // Glowing Cyan/Aqua Active Button Circle
                        Box(
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                // Outer Glow Aura
                                Canvas(
                                    modifier = Modifier.size(58.dp)
                                ) {
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                Color(0xFF55A9AF).copy(alpha = 0.5f),
                                                Color(0xFF26E07F).copy(alpha = 0.3f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                                }

                                // Solid Cyan/Aqua Circle Button
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    Color(0xFF00C2A8),
                                                    Color(0xFF55A9AF)
                                                )
                                            )
                                        )
                                )
                            }

                            Icon(
                                imageVector = if (item == BottomNavItem.AI) Icons.Default.AutoAwesome else if (isSelected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.title,
                                tint = if (isSelected) Color.White else Color(0xFF4A6572),
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer(scaleX = iconScale, scaleY = iconScale)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = item.title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFF55A9AF) else Color(0xFF4A6572)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MainScreen()
}
