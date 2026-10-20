package com.simats.intellifile.ui.main

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
import com.simats.intellifile.ui.ai.AiScreen
import com.simats.intellifile.ui.detail.FileDetailData
import com.simats.intellifile.ui.detail.FileDetailScreen
import com.simats.intellifile.ui.home.HomeScreen
import com.simats.intellifile.ui.priority.SetPriorityScreen
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
fun MainScreen() {
    var selectedTab by remember { mutableStateOf(BottomNavItem.HOME) }
    var selectedDetailFile by remember { mutableStateOf<FileDetailData?>(null) }
    var isSetPriorityOpen by remember { mutableStateOf(false) }
    var isViewerOpen by remember { mutableStateOf(false) }

    if (isViewerOpen && selectedDetailFile != null) {
        DocumentViewerScreen(
            fileName = selectedDetailFile!!.fileName,
            onBackClick = { isViewerOpen = false }
        )
    } else if (isSetPriorityOpen && selectedDetailFile != null) {
        SetPriorityScreen(
            fileName = selectedDetailFile!!.fileName,
            fileSize = selectedDetailFile!!.fileSize,
            fileFormat = selectedDetailFile!!.fileType.substringBefore(" "),
            initialPriority = selectedDetailFile!!.priority,
            onBackClick = { isSetPriorityOpen = false },
            onSavePriority = { newPriority ->
                selectedDetailFile = selectedDetailFile!!.copy(priority = newPriority)
                isSetPriorityOpen = false
            }
        )
    } else if (selectedDetailFile != null) {
        FileDetailScreen(
            fileData = selectedDetailFile!!,
            onBackClick = { selectedDetailFile = null },
            onOpenFileClick = { isViewerOpen = true },
            onSetPriorityClick = { isSetPriorityOpen = true },
            onMoreOptionsClick = { isSetPriorityOpen = true }
        )
    } else {
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
                        onSeeAllClick = { selectedTab = BottomNavItem.AI },
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
                    BottomNavItem.FILES -> PlaceholderTabContent(title = "All Files")
                    BottomNavItem.SAVED -> PlaceholderTabContent(title = "Saved Items")
                    BottomNavItem.PROFILE -> PlaceholderTabContent(title = "User Profile")
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
