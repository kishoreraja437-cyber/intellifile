package com.simats.intellifile.ui.main



import androidx.activity.compose.BackHandler

import androidx.compose.animation.AnimatedContent

import androidx.compose.animation.core.Animatable

import androidx.compose.animation.core.Spring

import androidx.compose.animation.core.animateDpAsState

import androidx.compose.animation.core.animateFloatAsState

import androidx.compose.animation.core.spring

import androidx.compose.animation.core.tween

import androidx.compose.animation.fadeIn

import androidx.compose.animation.fadeOut

import androidx.compose.animation.scaleIn

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

import androidx.compose.foundation.Canvas

import androidx.compose.foundation.background

import androidx.compose.foundation.border

import androidx.compose.foundation.clickable

import androidx.compose.foundation.interaction.MutableInteractionSource

import androidx.compose.foundation.layout.*

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.AutoAwesome

import androidx.compose.material.icons.filled.Bookmark

import androidx.compose.material.icons.filled.Favorite

import androidx.compose.material.icons.filled.Folder

import androidx.compose.material.icons.filled.Home

import androidx.compose.material.icons.filled.Person

import androidx.compose.material.icons.outlined.AutoAwesome

import androidx.compose.material.icons.outlined.BookmarkBorder

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

import androidx.compose.ui.draw.shadow

import androidx.compose.ui.geometry.Offset

import androidx.compose.ui.geometry.Size

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
import com.simats.intellifile.ui.voice.VoiceAssistantModal


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
import com.simats.intellifile.ui.viewer.ppt.PPTViewerScreen



enum class BottomNavItem(

    val title: String,

    val selectedIcon: ImageVector,

    val unselectedIcon: ImageVector

) {

    HOME("Home", Icons.Default.Home, Icons.Outlined.Home),

    FILES("Files", Icons.Default.Folder, Icons.Outlined.Folder),

    AI("AI", Icons.Default.AutoAwesome, Icons.Outlined.AutoAwesome),

    SAVED("Saved", Icons.Default.Bookmark, Icons.Outlined.BookmarkBorder),

    PROFILE("Profile", Icons.Default.Person, Icons.Outlined.Person)

}



@Composable

fun MainScreen(

    currentThemeMode: AppThemeMode = AppThemeMode.LIGHT,

    onThemeChanged: (AppThemeMode) -> Unit = {},

    onLogout: () -> Unit = {}

) {

    val context = LocalContext.current

    val sessionManager = remember { com.simats.intellifile.data.session.SessionManager(context) }
    val chatViewModel: com.simats.intellifile.ui.ai.ChatViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val priorityUpdateVersion by chatViewModel.priorityUpdateVersion.collectAsState()

    var selectedTab by remember { mutableStateOf(BottomNavItem.HOME) }

    var savedPriorityFilter by remember { mutableStateOf(PriorityFilter.ALL) }



    // Secondary Screen States

    var isRecentFilesOpen by remember { mutableStateOf(false) }

    var isAiHistoryOpen by remember { mutableStateOf(false) }

    var activeAiConversationId by remember { mutableStateOf<String?>(null) }
    var isVoiceAssistantOpen by remember { mutableStateOf(false) }

    var homeVoiceSearchQuery by remember { mutableStateOf("") }


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



    val tabBackStack = remember { mutableStateListOf(BottomNavItem.HOME) }

    fun navigateToTab(newTab: BottomNavItem) {
        if (selectedTab != newTab) {
            tabBackStack.remove(newTab)
            tabBackStack.add(newTab)
            selectedTab = newTab
        }
        isVoiceAssistantOpen = false
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
    }

    fun handleGoBack() {
        isVoiceAssistantOpen = false
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
            tabBackStack.size > 1 -> {
                tabBackStack.removeAt(tabBackStack.lastIndex)
                selectedTab = tabBackStack.last()
            }
            selectedTab != BottomNavItem.HOME -> {
                selectedTab = BottomNavItem.HOME
                tabBackStack.clear()
                tabBackStack.add(BottomNavItem.HOME)
            }
        }
    }

    val isOverlayScreenOpen = isVoiceAssistantOpen || isRecentFilesOpen || isAiHistoryOpen || selectedDetailFile != null ||
            isDocumentViewerOpen || isSetPriorityOpen || isFileSavedSuccessOpen || isShareOpen || isFilePermissionsOpen ||
            isNotificationsOpen || isThemeOpen || isAboutOpen ||
            isHelpSupportOpen || isContactSupportOpen || isContactSuccessOpen ||
            isFeedbackOpen || isFeedbackSuccessOpen

    // System Back Press Handler
    BackHandler(
        enabled = isOverlayScreenOpen || selectedTab != BottomNavItem.HOME || tabBackStack.size > 1
    ) {
        handleGoBack()
    }



    Scaffold(

        bottomBar = {

            if (!isOverlayScreenOpen) {

                BottomNavigationBar(
                    selectedTab = selectedTab,
                    onTabSelected = { newTab ->
                        navigateToTab(newTab)
                    }
                )

            }

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
                    val isInitialTab = initialState.startsWith("tab_")
                    val isTargetTab = targetState.startsWith("tab_")

                    // Butter-smooth Material 3 Emphasized Decelerate motion curve
                    val fluidEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

                    if (isInitialTab && isTargetTab) {
                        val tabOrder = listOf("tab_HOME", "tab_AI", "tab_FILES", "tab_SAVED", "tab_PROFILE")
                        val initialIdx = tabOrder.indexOf(initialState).takeIf { it >= 0 } ?: 0
                        val targetIdx = tabOrder.indexOf(targetState).takeIf { it >= 0 } ?: 0
                        val isForward = targetIdx > initialIdx

                        if (isForward) {
                            (slideInHorizontally(animationSpec = tween(380, easing = fluidEasing)) { fullWidth -> (fullWidth * 0.28f).toInt() } +
                                scaleIn(initialScale = 0.96f, animationSpec = tween(380, easing = fluidEasing)) +
                                fadeIn(animationSpec = tween(280)))
                                .togetherWith(
                                    slideOutHorizontally(animationSpec = tween(340, easing = fluidEasing)) { fullWidth -> -(fullWidth * 0.20f).toInt() } +
                                    scaleOut(targetScale = 0.96f, animationSpec = tween(340, easing = fluidEasing)) +
                                    fadeOut(animationSpec = tween(220))
                                )
                        } else {
                            (slideInHorizontally(animationSpec = tween(380, easing = fluidEasing)) { fullWidth -> -(fullWidth * 0.28f).toInt() } +
                                scaleIn(initialScale = 0.96f, animationSpec = tween(380, easing = fluidEasing)) +
                                fadeIn(animationSpec = tween(280)))
                                .togetherWith(
                                    slideOutHorizontally(animationSpec = tween(340, easing = fluidEasing)) { fullWidth -> (fullWidth * 0.20f).toInt() } +
                                    scaleOut(targetScale = 0.96f, animationSpec = tween(340, easing = fluidEasing)) +
                                    fadeOut(animationSpec = tween(220))
                                )
                        }
                    } else if (isTargetTab && !isInitialTab) {
                        // Returning from Overlay back to Tab
                        (scaleIn(initialScale = 0.94f, animationSpec = tween(360, easing = fluidEasing)) +
                            fadeIn(animationSpec = tween(280)))
                            .togetherWith(
                                slideOutVertically(animationSpec = tween(320, easing = fluidEasing)) { fullHeight -> (fullHeight * 0.15f).toInt() } +
                                scaleOut(targetScale = 0.92f, animationSpec = tween(320, easing = fluidEasing)) +
                                fadeOut(animationSpec = tween(220))
                            )
                    } else {
                        // Entering an Overlay (Document Viewer, File Detail, etc.)
                        (slideInVertically(animationSpec = tween(380, easing = fluidEasing)) { fullHeight -> (fullHeight * 0.12f).toInt() } +
                            scaleIn(initialScale = 0.92f, animationSpec = tween(380, easing = fluidEasing)) +
                            fadeIn(animationSpec = tween(300)))
                            .togetherWith(
                                scaleOut(targetScale = 0.95f, animationSpec = tween(300, easing = fluidEasing)) +
                                fadeOut(animationSpec = tween(220))
                            )
                    }
                },
                label = "mainScreenTransition"

            ) { navKey ->

                when (navKey) {

                    "recent_files" -> RecentFilesScreen(

                        onBackClick = { handleGoBack() },

                        onMicClick = { isVoiceAssistantOpen = true },

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



                    "document_viewer" -> {
                        val format = selectedDetailFile?.fileType?.substringBefore(" ")?.uppercase() ?: "PDF"
                        val name = selectedDetailFile?.fileName ?: "Document.pdf"
                        val isPresentation = format in listOf("PPT", "PPTX", "PPS", "PPSX", "POT", "POTX") ||
                                name.endsWith(".pptx", ignoreCase = true) ||
                                name.endsWith(".ppt", ignoreCase = true)

                        if (isPresentation) {
                            PPTViewerScreen(
                                fileName = name,
                                filePath = selectedDetailFile?.path ?: "",
                                fileUriString = selectedDetailFile?.uriString ?: "",
                                onBackClick = {
                                    isDocumentViewerOpen = false
                                    if (selectedTab == BottomNavItem.AI || selectedTab == BottomNavItem.FILES) {
                                        selectedDetailFile = null
                                    }
                                },
                                onShareClick = { isShareOpen = true }
                            )
                        } else {
                            DocumentViewerScreen(
                                fileName = selectedDetailFile?.fileName ?: "Document.pdf",
                                filePath = selectedDetailFile?.path ?: "",
                                fileUriString = selectedDetailFile?.uriString ?: "",
                                fileFormat = selectedDetailFile?.fileType?.substringBefore(" ") ?: "PDF",
                                totalPages = 1,
                                onBackClick = {
                                    isDocumentViewerOpen = false
                                    if (selectedTab == BottomNavItem.AI || selectedTab == BottomNavItem.FILES) {
                                        selectedDetailFile = null
                                    }
                                },
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
                        }
                    }



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
                        chatViewModel = chatViewModel,
                        onBackClick = { isAiHistoryOpen = false },

                        onNewChatClick = {
                            chatViewModel.startNewChat()
                            activeAiConversationId = null
                            isAiHistoryOpen = false
                            navigateToTab(BottomNavItem.AI)
                        },

                        onClearHistoryClick = {

                            activeAiConversationId = null

                        },

                        onDeleteIndividualClick = { deletedItem ->

                            if (activeAiConversationId == deletedItem.id) {

                                activeAiConversationId = null

                            }

                        },

                        onHistoryItemClick = { historyItem ->

                            chatViewModel.loadConversation(historyItem.id)

                            activeAiConversationId = historyItem.id

                            isAiHistoryOpen = false

                            navigateToTab(BottomNavItem.AI)

                        }

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

                        onSelectFilesClick = {
                            isFilePermissionsOpen = false
                            navigateToTab(BottomNavItem.FILES)
                        }

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



                        else -> AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = {
                                val forward = targetState.ordinal > initialState.ordinal
                                if (forward) {
                                    (slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { (it * 0.08f).toInt() } + fadeIn(tween(200)))
                                        .togetherWith(slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) { -(it * 0.08f).toInt() } + fadeOut(tween(160)))
                                } else {
                                    (slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { -(it * 0.08f).toInt() } + fadeIn(tween(200)))
                                        .togetherWith(slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) { (it * 0.08f).toInt() } + fadeOut(tween(160)))
                                }
                            },
                            label = "tabScreenSlideTransition"
                        ) { tab ->
                            when (tab) {

                            BottomNavItem.HOME -> HomeScreen(
                                priorityUpdateVersion = priorityUpdateVersion,
                                externalSearchQuery = homeVoiceSearchQuery,

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

                                onMicClick = { isVoiceAssistantOpen = true }

                            )

                            BottomNavItem.AI -> AiScreen(
                                chatViewModel = chatViewModel,
                                initialConversationId = activeAiConversationId,

                                onBackClick = { handleGoBack() },

                                onMicClick = { isVoiceAssistantOpen = true },

                                onOpenFile = { aiFile ->
                                    val localMatch = LocalFileManager.getDeviceFiles(context).firstOrNull {
                                        it.title.equals(aiFile.title, ignoreCase = true) ||
                                        it.title.equals(aiFile.title.substringBeforeLast('.'), ignoreCase = true) ||
                                        (!aiFile.uriString.isNullOrBlank() && it.uri.toString() == aiFile.uriString) ||
                                        (!aiFile.filePath.isNullOrBlank() && it.path == aiFile.filePath)
                                    }

                                    val finalUri = if (!aiFile.uriString.isNullOrBlank()) aiFile.uriString else (localMatch?.uri?.toString() ?: "")
                                    val finalPath = if (!aiFile.filePath.isNullOrBlank()) aiFile.filePath else (localMatch?.path ?: "")

                                    val resolvedIcon = when (aiFile.format.trim().removePrefix(".").uppercase()) {
                                        "DOCX", "DOC" -> R.drawable.doc
                                        "PPT", "PPTX" -> R.drawable.ppt
                                        else -> R.drawable.pdf
                                    }

                                    selectedDetailFile = FileDetailData(
                                        fileName = aiFile.title,
                                        fileSize = aiFile.size,
                                        fileType = "${aiFile.format} Document",
                                        createdDate = aiFile.dateText,
                                        modifiedDate = aiFile.dateText,
                                        priority = aiFile.priority,
                                        iconResId = resolvedIcon,
                                        uriString = finalUri,
                                        path = finalPath
                                    )
                                    isDocumentViewerOpen = true
                                },

                                onViewSearchHistoryClick = { isAiHistoryOpen = true },

                                onNewChatClick = {

                                    activeAiConversationId = null

                                }

                            )

                            BottomNavItem.FILES -> MyFilesScreen(
                                priorityUpdateVersion = priorityUpdateVersion,
                                onBackClick = { handleGoBack() },
                                onMicClick = { isVoiceAssistantOpen = true },
                                onQuickAccessClick = { priorityLevel ->
                                    savedPriorityFilter = when (priorityLevel) {
                                        PriorityLevel.IMPORTANT -> PriorityFilter.IMPORTANT
                                        PriorityLevel.NORMAL -> PriorityFilter.NORMAL
                                        PriorityLevel.LOW -> PriorityFilter.LOW
                                    }
                                    selectedTab = BottomNavItem.SAVED
                                },
                                onNavigateToAiChat = { _ ->
                                    selectedTab = BottomNavItem.AI
                                },
                                onOpenFile = { fileDetail ->
                                    selectedDetailFile = fileDetail
                                    isDocumentViewerOpen = true
                                }
                            )

                            BottomNavItem.SAVED -> SavedScreen(
                                priorityUpdateVersion = priorityUpdateVersion,
                                initialFilter = savedPriorityFilter,

                                onBackClick = { handleGoBack() },

                                onMicClick = { isVoiceAssistantOpen = true },

                                onOpenFile = { file ->

                                    val ext = file.title.substringAfterLast('.', "").uppercase()

                                    val detectedType = if (ext.isNotBlank()) "$ext Document" else "Document"

                                    selectedDetailFile = FileDetailData(

                                        fileName = file.title,

                                        fileSize = file.size,

                                        fileType = detectedType,

                                        createdDate = file.dateText,

                                        modifiedDate = file.dateText,

                                        category = "Saved Document",

                                        location = if (file.path.isNotBlank()) file.path else "Internal Storage",

                                        priority = file.priority,

                                        iconResId = file.iconResId,

                                        uriString = file.uriString,

                                        path = file.path

                                    )

                                    isDocumentViewerOpen = true

                                },

                                onShareFileClick = { file ->

                                    val ext = file.title.substringAfterLast('.', "").uppercase()

                                    val detectedType = if (ext.isNotBlank()) "$ext Document" else "Document"

                                    selectedDetailFile = FileDetailData(

                                        fileName = file.title,

                                        fileSize = file.size,

                                        fileType = detectedType,

                                        priority = file.priority,

                                        iconResId = file.iconResId,

                                        uriString = file.uriString,

                                        path = file.path

                                    )

                                    isShareOpen = true

                                }

                            )

                            BottomNavItem.PROFILE -> ProfileScreen(

                                userName = sessionManager.getFullName(),

                                userEmail = sessionManager.getEmail(),

                                onBackClick = { handleGoBack() },

                                onMicClick = { isVoiceAssistantOpen = true },

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

        if (isVoiceAssistantOpen) {
            VoiceAssistantModal(
                onDismiss = { isVoiceAssistantOpen = false },
                onNavigateToTab = { tab ->
                    navigateToTab(tab)
                },
                onNavigateToOverlay = { overlayKey ->
                    isVoiceAssistantOpen = false
                    when (overlayKey) {
                        "recent_files" -> isRecentFilesOpen = true
                        "notifications" -> isNotificationsOpen = true
                        "theme" -> isThemeOpen = true
                        "about" -> isAboutOpen = true
                        "help" -> isHelpSupportOpen = true
                        "ai_history" -> isAiHistoryOpen = true
                    }
                },
                onGoBack = {
                    handleGoBack()
                },
                onOpenInAiChat = { convId ->
                    activeAiConversationId = convId?.toString()
                    navigateToTab(BottomNavItem.AI)
                },
                onSearchHome = { query ->
                    homeVoiceSearchQuery = query
                    navigateToTab(BottomNavItem.HOME)
                },
                onOpenFile = { fileDetail ->
                    isVoiceAssistantOpen = false
                    selectedDetailFile = fileDetail
                }
            )
        }

}



@Composable
fun BottomNavigationBar(
    selectedTab: BottomNavItem,
    onTabSelected: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = remember { BottomNavItem.entries }
    val selectedIndex = items.indexOf(selectedTab).coerceAtLeast(0)

    // Ultra-smooth physics glide for the liquid wave across tabs
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = 0.82f, // Gentle, controlled settle with zero harsh snap
            stiffness = 550f      // Responsive, fluid gliding without dragging lag
        ),
        label = "bottomNavWaveIndex"
    )

    // Expanding ripple animation state triggered when tab selection changes
    val rippleProgress = remember { Animatable(0f) }
    LaunchedEffect(selectedTab) {
        rippleProgress.snapTo(0f)
        rippleProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
        )
    }

    // Reusable Path objects to avoid frame-by-frame heap allocations during animation
    val bgPath = remember { Path() }
    val wavePath = remember { Path() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Floating Rounded White Pill Container with Subtle Aqua Shadow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(32.dp),
                    spotColor = Color(0x3800C2A8),
                    ambientColor = Color(0x1800C2A8)
                )
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFD8F1F4), RoundedCornerShape(32.dp))
        ) {
            // 1. Canvas Layer: Zero-Allocation Fluid Liquid Wave & Expanding Ripple Ring
            Canvas(modifier = Modifier.fillMaxSize()) {
                val tabWidth = size.width / items.size
                val waveCenterX = tabWidth * (animatedIndex + 0.5f)
                val crestHeight = 22.dp.toPx()
                val waveSpan = tabWidth * 1.55f

                // (a) Ambient soft aqua glow wave (background layer)
                val bgSpan = tabWidth * 1.85f
                val bgHeight = 15.dp.toPx()
                val bgStartX = (waveCenterX - bgSpan).coerceAtLeast(0f)
                val bgEndX = (waveCenterX + bgSpan).coerceAtMost(size.width)

                bgPath.reset()
                bgPath.moveTo(0f, size.height)
                bgPath.lineTo(bgStartX, size.height)
                bgPath.cubicTo(
                    bgStartX + bgSpan * 0.45f, size.height,
                    waveCenterX - bgSpan * 0.28f, size.height - bgHeight,
                    waveCenterX, size.height - bgHeight
                )
                bgPath.cubicTo(
                    waveCenterX + bgSpan * 0.28f, size.height - bgHeight,
                    bgEndX - bgSpan * 0.45f, size.height,
                    bgEndX, size.height
                )
                bgPath.lineTo(size.width, size.height)
                bgPath.close()

                drawPath(
                    path = bgPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x3300C2A8),
                            Color(0x1400C2A8),
                            Color.Transparent
                        ),
                        startY = size.height - 20.dp.toPx(),
                        endY = size.height
                    )
                )

                // (b) Primary vibrant liquid wave contour (foreground wave)
                val waveStartX = (waveCenterX - waveSpan).coerceAtLeast(0f)
                val waveEndX = (waveCenterX + waveSpan).coerceAtMost(size.width)

                wavePath.reset()
                wavePath.moveTo(0f, size.height)
                wavePath.lineTo(waveStartX, size.height)
                wavePath.cubicTo(
                    waveStartX + waveSpan * 0.42f, size.height,
                    waveCenterX - waveSpan * 0.24f, size.height - crestHeight,
                    waveCenterX, size.height - crestHeight
                )
                wavePath.cubicTo(
                    waveCenterX + waveSpan * 0.24f, size.height - crestHeight,
                    waveEndX - waveSpan * 0.42f, size.height,
                    waveEndX, size.height
                )
                wavePath.lineTo(size.width, size.height)
                wavePath.close()

                drawPath(
                    path = wavePath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x9900C2A8),
                            Color(0xDD00C2A8),
                            Color(0xFF00C2A8)
                        ),
                        startY = size.height - crestHeight,
                        endY = size.height
                    )
                )

                // (c) Expanding subtle ripple animation below selected icon
                val rippleVal = rippleProgress.value
                if (rippleVal in 0.001f..0.999f) {
                    val rippleRadiusPx = 10.dp.toPx() + (26.dp.toPx() * rippleVal)
                    val rippleAlpha = (1f - rippleVal) * 0.5f
                    val strokeWidth = 2.dp.toPx() * (1f - rippleVal * 0.6f)

                    drawOval(
                        color = Color(0xFF00C2A8).copy(alpha = rippleAlpha),
                        topLeft = Offset(
                            x = waveCenterX - rippleRadiusPx,
                            y = (size.height - 18.dp.toPx()) - (rippleRadiusPx * 0.45f)
                        ),
                        size = Size(
                            width = rippleRadiusPx * 2f,
                            height = rippleRadiusPx * 0.9f
                        ),
                        style = Stroke(width = strokeWidth)
                    )
                }

                // (d) Subtle floating aqua droplets near active icon
                val dropletAlpha = (1f - kotlin.math.abs(animatedIndex - selectedIndex)).coerceIn(0f, 1f)
                if (dropletAlpha > 0.05f) {
                    drawCircle(
                        color = Color(0xFF00C2A8).copy(alpha = 0.55f * dropletAlpha),
                        radius = 3.dp.toPx(),
                        center = Offset(waveCenterX - 22.dp.toPx(), 22.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0xFF00C2A8).copy(alpha = 0.45f * dropletAlpha),
                        radius = 3.5.dp.toPx(),
                        center = Offset(waveCenterX + 20.dp.toPx(), 25.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0xFF00C2A8).copy(alpha = 0.35f * dropletAlpha),
                        radius = 2.dp.toPx(),
                        center = Offset(waveCenterX - 14.dp.toPx(), 36.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0xFF00C2A8).copy(alpha = 0.4f * dropletAlpha),
                        radius = 2.dp.toPx(),
                        center = Offset(waveCenterX + 16.dp.toPx(), 38.dp.toPx())
                    )
                }
            }

            // 2. Interactive Navigation Tabs Row
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                items.forEach { item ->
                    val isSelected = item == selectedTab

                    // Smooth spring bounce upward for active icon
                    val bounceOffset by animateDpAsState(
                        targetValue = if (isSelected) (-8).dp else 0.dp,
                        animationSpec = spring(
                            dampingRatio = 0.65f, // Responsive bouncy feel
                            stiffness = 750f       // Quick, natural settle
                        ),
                        label = "bounceOffset_${item.title}"
                    )

                    // Spring scale for icon
                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.16f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = 0.68f,
                            stiffness = 750f
                        ),
                        label = "iconScale_${item.title}"
                    )

                    // Soft circular highlight alpha & scale behind active icon
                    val highlightAlpha by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0f,
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        label = "highlightAlpha_${item.title}"
                    )
                    val highlightScale by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0.5f,
                        animationSpec = spring(
                            dampingRatio = 0.7f,
                            stiffness = 700f
                        ),
                        label = "highlightScale_${item.title}"
                    )

                    // Active icon: Dusky Aqua (0xFF00C2A8); Inactive: Dark Teal (0xFF134E5E)
                    val iconTint by animateColorAsState(
                        targetValue = if (isSelected) Color(0xFF00C2A8) else Color(0xFF134E5E),
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                        label = "iconTint_${item.title}"
                    )

                    // Active text: Bold Dusky Aqua (0xFF0D9488); Inactive: Dark Teal (0xFF134E5E)
                    val labelColor by animateColorAsState(
                        targetValue = if (isSelected) Color(0xFF0D9488) else Color(0xFF134E5E),
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                        label = "labelColor_${item.title}"
                    )
                    val labelScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.05f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = 0.7f,
                            stiffness = 700f
                        ),
                        label = "labelScale_${item.title}"
                    )

                    val interactionSource = remember(item) { MutableInteractionSource() }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                onTabSelected(item)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Icon Box with GPU-accelerated graphicsLayer translation (zero recomposition/relayout)
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .graphicsLayer {
                                        translationY = bounceOffset.toPx()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                // Soft aqua circular highlight behind active icon (deferred to draw layer)
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .graphicsLayer {
                                            scaleX = highlightScale
                                            scaleY = highlightScale
                                            alpha = highlightAlpha
                                        }
                                        .background(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    Color(0x4000C2A8),
                                                    Color(0x1800C2A8),
                                                    Color.Transparent
                                                )
                                            ),
                                            shape = CircleShape
                                        )
                                )

                                Icon(
                                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title,
                                    tint = iconTint,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                        }
                                )
                            }

                            Spacer(modifier = Modifier.height(1.dp))

                            Text(
                                text = item.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = labelColor,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = labelScale
                                    scaleY = labelScale
                                }
                            )
                        }
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

