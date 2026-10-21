package com.simats.intellifile.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.intellifile.R

enum class FileFormatFilter(val label: String) {
    ALL("All"),
    PDF("PDF"),
    DOC("DOC"),
    PPT("PPT"),
    OTHER("Other")
}

@Composable
fun RecentFilesScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onFileClick: (RecentFile) -> Unit = {},
    onShareFileClick: (RecentFile) -> Unit = {},
    onMoreOptionsClick: (RecentFile) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFormatFilter by remember { mutableStateOf(FileFormatFilter.ALL) }

    val recentFiles = remember {
        listOf(
            RecentFile(
                title = "AML Assignment.pdf",
                format = "PDF",
                size = "2.4 MB",
                dateText = "Today",
                priority = PriorityLevel.IMPORTANT,
                iconResId = R.drawable.pdf
            ),
            RecentFile(
                title = "DBMS Notes.pdf",
                format = "PDF",
                size = "1.8 MB",
                dateText = "1 day ago",
                priority = PriorityLevel.NORMAL,
                iconResId = R.drawable.pdf
            ),
            RecentFile(
                title = "Project Report.pdf",
                format = "PDF",
                size = "3.1 MB",
                dateText = "2 days ago",
                priority = PriorityLevel.LOW,
                iconResId = R.drawable.pdf
            ),
            RecentFile(
                title = "Resume.pdf",
                format = "PDF",
                size = "0.9 MB",
                dateText = "3 days ago",
                priority = PriorityLevel.NORMAL,
                iconResId = R.drawable.pdf
            ),
            RecentFile(
                title = "Lab Record.pdf",
                format = "DOC",
                size = "2.2 MB",
                dateText = "4 days ago",
                priority = PriorityLevel.NORMAL,
                iconResId = R.drawable.doc
            ),
            RecentFile(
                title = "Research Paper.pdf",
                format = "PDF",
                size = "4.5 MB",
                dateText = "5 days ago",
                priority = PriorityLevel.IMPORTANT,
                iconResId = R.drawable.pdf
            )
        )
    }

    val filteredFiles = recentFiles.filter { file ->
        val matchesSearch = file.title.contains(searchQuery, ignoreCase = true)
        val matchesFormat = when (selectedFormatFilter) {
            FileFormatFilter.ALL -> true
            FileFormatFilter.PDF -> file.format.equals("PDF", ignoreCase = true)
            FileFormatFilter.DOC -> file.format.equals("DOC", ignoreCase = true) || file.format.equals("DOCX", ignoreCase = true)
            FileFormatFilter.PPT -> file.format.equals("PPT", ignoreCase = true) || file.format.equals("PPTX", ignoreCase = true)
            FileFormatFilter.OTHER -> !listOf("PDF", "DOC", "DOCX", "PPT", "PPTX").contains(file.format.uppercase())
        }
        matchesSearch && matchesFormat
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FE))
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
                            tint = Color(0xFF111827)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = "Recent Files",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )
                }

                IconButton(onClick = { /* Search toggle */ }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color(0xFF111827)
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
                // Search Field
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEEF0FD))
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
                                    text = "Search recent files...",
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

                // File Format Filter Chips Row
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(FileFormatFilter.entries) { filter ->
                            val isSelected = filter == selectedFormatFilter
                            FormatFilterChip(
                                filter = filter,
                                isSelected = isSelected,
                                onClick = { selectedFormatFilter = filter }
                            )
                        }
                    }
                }

                // Recent Files List
                items(filteredFiles) { file ->
                    RecentFileItemCard(
                        file = file,
                        onClick = { onFileClick(file) },
                        onShareClick = { onShareFileClick(file) }
                    )
                }
            }
        }
    }
}

@Composable
fun FormatFilterChip(
    filter: FileFormatFilter,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) Color(0xFF6C5CE7) else Color(0xFFEEF0FD)
    val textColor = if (isSelected) Color.White else Color(0xFF6C5CE7)

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = filter.label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            color = textColor
        )
    }
}

@Composable
fun RecentFileItemCard(
    file: RecentFile,
    onClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .clickable { onClick() }
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
                color = Color(0xFF111827)
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = "${file.size} · ${file.dateText}",
                fontSize = 12.sp,
                color = Color(0xFF6B7280)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        PriorityTag(priority = file.priority)

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

            FileOptionsDropdownMenu(
                expanded = isMenuOpen,
                onDismissRequest = { isMenuOpen = false },
                onShareClick = onShareClick,
                onDeleteClick = { /* Handle delete file */ }
            )
        }
    }
}

@Composable
fun FileOptionsDropdownMenu(
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
fun RecentFilesScreenPreview() {
    RecentFilesScreen()
}
