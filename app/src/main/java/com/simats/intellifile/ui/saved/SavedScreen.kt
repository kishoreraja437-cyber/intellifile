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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.simats.intellifile.ui.home.PriorityLevel
import com.simats.intellifile.ui.home.PriorityTag

data class SavedFileItem(
    val title: String,
    val size: String,
    val dateText: String,
    val priority: PriorityLevel,
    val iconResId: Int = R.drawable.pdf
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
    onBackClick: () -> Unit = {},
    onOpenFile: (SavedFileItem) -> Unit = {},
    onMoreOptionsClick: (SavedFileItem) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(PriorityFilter.ALL) }

    val savedFiles = remember {
        listOf(
            SavedFileItem(
                title = "AML Assignment.pdf",
                size = "2.4 MB",
                dateText = "Oct 5, 2024",
                priority = PriorityLevel.IMPORTANT,
                iconResId = R.drawable.pdf
            ),
            SavedFileItem(
                title = "Project Report.pdf",
                size = "3.1 MB",
                dateText = "Sep 28, 2024",
                priority = PriorityLevel.IMPORTANT,
                iconResId = R.drawable.pdf
            ),
            SavedFileItem(
                title = "DBMS Notes.pdf",
                size = "1.8 MB",
                dateText = "Oct 1, 2024",
                priority = PriorityLevel.NORMAL,
                iconResId = R.drawable.doc
            ),
            SavedFileItem(
                title = "Resume.pdf",
                size = "0.9 MB",
                dateText = "Sep 20, 2024",
                priority = PriorityLevel.LOW,
                iconResId = R.drawable.normal
            ),
            SavedFileItem(
                title = "Lab Record.pdf",
                size = "2.2 MB",
                dateText = "Sep 15, 2024",
                priority = PriorityLevel.NORMAL,
                iconResId = R.drawable.doc
            )
        )
    }

    val filteredFiles = savedFiles.filter { file ->
        val matchesSearch = file.title.contains(searchQuery, ignoreCase = true)
        val matchesPriority = selectedFilter.level == null || file.priority == selectedFilter.level
        matchesSearch && matchesPriority
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
                    text = "Saved Documents",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
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
                                    text = "Search saved files...",
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

                // Saved File Cards List
                items(filteredFiles) { file ->
                    SavedFileCard(
                        file = file,
                        onOpenClick = { onOpenFile(file) },
                        onMoreClick = { onMoreOptionsClick(file) }
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
        PriorityFilter.ALL -> if (isSelected) Color(0xFF6C5CE7) else Color(0xFFEEF0FD)
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
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
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

            Spacer(modifier = Modifier.height(6.dp))

            PriorityTag(priority = file.priority)
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = onOpenClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7)),
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

        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More Options",
                tint = Color(0xFF374151),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SavedScreenPreview() {
    SavedScreen()
}
