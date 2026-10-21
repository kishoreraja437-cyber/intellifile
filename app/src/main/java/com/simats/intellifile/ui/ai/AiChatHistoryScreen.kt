package com.simats.intellifile.ui.ai

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Description
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class HistoryCategory(val label: String, val icon: ImageVector?) {
    ALL("All", null),
    ANALYSIS("Document Analysis", Icons.Outlined.Description),
    SEARCH("File Search", Icons.Default.Search),
    GENERAL("General", Icons.AutoMirrored.Outlined.Chat)
}

data class ChatHistoryItem(
    val title: String,
    val snippet: String,
    val dateText: String,
    val timeText: String,
    val category: HistoryCategory,
    val icon: ImageVector
)

@Composable
fun AiChatHistoryScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onClearHistoryClick: () -> Unit = {},
    onHistoryItemClick: (ChatHistoryItem) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(HistoryCategory.ALL) }

    val historyItems = remember {
        mutableStateListOf(
            ChatHistoryItem(
                title = "Summarize this PDF",
                snippet = "Here's a summary of the key points from the document 'Project_Report.pdf'...",
                dateText = "Oct 5, 2024",
                timeText = "10:24 AM",
                category = HistoryCategory.ANALYSIS,
                icon = Icons.Outlined.Description
            ),
            ChatHistoryItem(
                title = "Find important files",
                snippet = "I found 5 important files related to 'assignment' in your documents...",
                dateText = "Oct 4, 2024",
                timeText = "04:15 PM",
                category = HistoryCategory.SEARCH,
                icon = Icons.Default.Search
            ),
            ChatHistoryItem(
                title = "Explain this document",
                snippet = "This document appears to be a project proposal for the AI-based document...",
                dateText = "Oct 3, 2024",
                timeText = "11:32 AM",
                category = HistoryCategory.ANALYSIS,
                icon = Icons.Outlined.ChatBubbleOutline
            ),
            ChatHistoryItem(
                title = "Compare two files",
                snippet = "The main differences between the two files are in the content and file size...",
                dateText = "Oct 2, 2024",
                timeText = "08:45 PM",
                category = HistoryCategory.ANALYSIS,
                icon = Icons.Outlined.Description
            ),
            ChatHistoryItem(
                title = "Suggest file priorities",
                snippet = "Based on your recent activity, here are the top 5 files you should prioritize...",
                dateText = "Oct 1, 2024",
                timeText = "02:17 PM",
                category = HistoryCategory.GENERAL,
                icon = Icons.Outlined.AutoAwesome
            ),
            ChatHistoryItem(
                title = "General Query",
                snippet = "What is the best way to organize my files for better productivity?",
                dateText = "Sep 29, 2024",
                timeText = "09:52 AM",
                category = HistoryCategory.GENERAL,
                icon = Icons.Outlined.ChatBubbleOutline
            ),
            ChatHistoryItem(
                title = "Extract key points",
                snippet = "Here are the key points from your document 'Notes.pdf'...",
                dateText = "Sep 28, 2024",
                timeText = "05:30 PM",
                category = HistoryCategory.ANALYSIS,
                icon = Icons.Outlined.Description
            )
        )
    }

    val filteredItems = historyItems.filter { item ->
        val matchesSearch = item.title.contains(searchQuery, ignoreCase = true) || item.snippet.contains(searchQuery, ignoreCase = true)
        val matchesCategory = selectedCategory == HistoryCategory.ALL || item.category == selectedCategory
        matchesSearch && matchesCategory
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
                    .padding(horizontal = 12.dp, vertical = 10.dp),
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
                        text = "AI Chat History",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        historyItems.clear()
                        onClearHistoryClick()
                    }) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Clear History",
                            tint = Color(0xFF111827)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFDCE2FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "AI Robot",
                            tint = Color(0xFF6C5CE7),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Sub-heading
                item {
                    Text(
                        text = "Access your previous AI conversations\nand document searches.",
                        fontSize = 13.sp,
                        color = Color(0xFF6B7280),
                        lineHeight = 18.sp
                    )
                }

                // Search Bar
                item {
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
                            tint = Color(0xFF1E1B4B),
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search chat history...",
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
                }

                // Filter Category Chips
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(HistoryCategory.entries) { category ->
                            val isSelected = category == selectedCategory
                            HistoryFilterChip(
                                category = category,
                                isSelected = isSelected,
                                onClick = { selectedCategory = category }
                            )
                        }
                    }
                }

                // History Items List
                items(filteredItems) { item ->
                    HistoryCardItem(
                        item = item,
                        onClick = { onHistoryItemClick(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryFilterChip(
    category: HistoryCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) Color(0xFF6C5CE7) else Color(0xFFEEF0FD)
    val contentColor = if (isSelected) Color.White else Color(0xFF374151)

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (category.icon != null) {
            Icon(
                imageVector = category.icon,
                contentDescription = category.label,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        Text(
            text = category.label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor
        )
    }
}

@Composable
fun HistoryCardItem(
    item: ChatHistoryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFFEEF0FD)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = Color(0xFF6C5CE7),
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827),
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "${item.dateText}\n${item.timeText}",
                    fontSize = 10.sp,
                    color = Color(0xFF9CA3AF),
                    lineHeight = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.snippet,
                fontSize = 12.sp,
                color = Color(0xFF6B7280),
                maxLines = 2
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Open",
            tint = Color(0xFF9CA3AF),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AiChatHistoryScreenPreview() {
    AiChatHistoryScreen()
}
