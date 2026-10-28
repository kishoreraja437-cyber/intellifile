package com.simats.intellifile.ui.saved

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.intellifile.R
import com.simats.intellifile.data.manager.LocalFileManager
import com.simats.intellifile.data.model.LocalFileItem
import com.simats.intellifile.ui.home.PriorityLevel
import com.simats.intellifile.ui.home.PriorityTag

data class SavedFileItem(
    val title: String,
    val size: String,
    val dateText: String,
    val priority: PriorityLevel? = null,
    val iconResId: Int = R.drawable.pdf,
    val uriString: String = "",
    val path: String = ""
)

enum class PriorityFilter(val label: String, val level: PriorityLevel?) {
    ALL("All", null),
    IMPORTANT("Important", PriorityLevel.IMPORTANT),
    NORMAL("Normal", PriorityLevel.NORMAL),
    LOW("Low", PriorityLevel.LOW)
}

@Composable
fun SavedScreen(
    modifier: Modifier = Modifier,
    initialFilter: PriorityFilter = PriorityFilter.ALL,
    onBackClick: () -> Unit = {},
    onOpenFile: (SavedFileItem) -> Unit = {},
    onShareFileClick: (SavedFileItem) -> Unit = {},
    onMoreOptionsClick: (SavedFileItem) -> Unit = {}
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember(initialFilter) { mutableStateOf(initialFilter) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var realFiles by remember { mutableStateOf<List<LocalFileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(refreshTrigger) {
        isLoading = true
        realFiles = LocalFileManager.getDeviceFiles(context)
        isLoading = false
    }

    // Only show documents explicitly bookmarked or explicitly assigned a priority
    val savedFiles = remember(realFiles) {
        realFiles.filter { it.isBookmarked || it.priority != null }.map { item ->
            SavedFileItem(
                title = item.title,
                size = item.sizeFormatted,
                dateText = item.dateText,
                priority = item.priority,
                iconResId = item.iconResId,
                uriString = item.uri.toString(),
                path = item.path
            )
        }
    }

    val filteredFiles = remember(savedFiles, searchQuery, selectedFilter) {
        savedFiles.filter { file ->
            val matchesSearch = file.title.contains(searchQuery, ignoreCase = true)
            val matchesPriority = selectedFilter.level == null || file.priority == selectedFilter.level
            matchesSearch && matchesPriority
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
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
                        text = "Saved Documents",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                IconButton(onClick = { refreshTrigger++ }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Search Input Field
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color(0xFF1E1B4B),
                            modifier = Modifier.size(22.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search saved documents...",
                                    fontSize = 15.sp,
                                    color = Color(0xFF6B7280)
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
                }

                // Filter Chips Row
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PriorityFilter.entries.forEach { filter ->
                            val isSelected = filter == selectedFilter
                            FilterChip(
                                filter = filter,
                                isSelected = isSelected,
                                onClick = { selectedFilter = filter },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Empty state if no saved files match
                if (!isLoading && filteredFiles.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Empty",
                                tint = Color(0xFF9CA3AF),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "No files match \"$searchQuery\"" else "No saved documents yet",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1F2937),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Bookmark a document or set its priority to view it here.",
                                fontSize = 13.sp,
                                color = Color(0xFF6B7280),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Saved File Cards List
                items(filteredFiles) { file ->
                    SavedFileCard(
                        file = file,
                        onOpenClick = { onOpenFile(file) },
                        onShareClick = { onShareFileClick(file) }
                    )
                }
            }
        }
    }
}

@Composable
fun FilterChip(
    filter: PriorityFilter,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when (filter) {
        PriorityFilter.ALL -> if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        PriorityFilter.IMPORTANT -> Color(0xFFFFEAEE)
        PriorityFilter.NORMAL -> Color(0xFFFFF6D8)
        PriorityFilter.LOW -> Color(0xFFE2F7ED)
    }

    val textColor = when (filter) {
        PriorityFilter.ALL -> if (isSelected) Color.White else Color(0xFF4B5563)
        PriorityFilter.IMPORTANT -> Color(0xFFEF4444)
        PriorityFilter.NORMAL -> Color(0xFFD97706)
        PriorityFilter.LOW -> Color(0xFF10B981)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = filter.label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
    }
}

@Composable
fun SavedFileCard(
    file: SavedFileItem,
    onOpenClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = file.iconResId),
            contentDescription = "File Type Icon",
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = file.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = "${file.size} · ${file.dateText}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1
            )

            if (file.priority != null) {
                Spacer(modifier = Modifier.height(6.dp))
                PriorityTag(priority = file.priority)
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = onOpenClick,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Text(
                text = "Open",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        Box {
            IconButton(
                onClick = { isMenuOpen = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More Options",
                    tint = Color(0xFF374151),
                    modifier = Modifier.size(18.dp)
                )
            }

            SavedFileOptionsDropdownMenu(
                expanded = isMenuOpen,
                onDismissRequest = { isMenuOpen = false },
                onShareClick = onShareClick,
                onDeleteClick = { /* Handle delete */ }
            )
        }
    }
}

@Composable
fun SavedFileOptionsDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onShareClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share",
                        tint = Color(0xFF6C5CE7),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "Share", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            },
            onClick = {
                onDismissRequest()
                onShareClick()
            }
        )
        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "Delete", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFFEF4444))
                }
            },
            onClick = {
                onDismissRequest()
                onDeleteClick()
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SavedScreenPreview() {
    SavedScreen()
}
