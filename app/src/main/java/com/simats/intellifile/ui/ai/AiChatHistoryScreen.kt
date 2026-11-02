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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.intellifile.data.repository.AiRepository
import kotlinx.coroutines.launch

enum class HistoryCategory(val label: String, val icon: ImageVector?) {
    ALL("All", null),
    ANALYSIS("Document Analysis", Icons.Outlined.Description),
    SEARCH("File Search", Icons.Default.Search),
    GENERAL("General", Icons.AutoMirrored.Outlined.Chat)
}

data class ChatHistoryItem(
    val id: Int = 0,
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
    onDeleteIndividualClick: (ChatHistoryItem) -> Unit = {},
    onHistoryItemClick: (ChatHistoryItem) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val aiRepository = remember { AiRepository(context) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(HistoryCategory.ALL) }
    var isLoading by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<ChatHistoryItem?>(null) }

    val historyItems = remember { mutableStateListOf<ChatHistoryItem>() }

    fun fetchHistory() {
        coroutineScope.launch {
            isLoading = true
            val catParam = when (selectedCategory) {
                HistoryCategory.ALL -> "ALL"
                HistoryCategory.ANALYSIS -> "ANALYSIS"
                HistoryCategory.SEARCH -> "SEARCH"
                HistoryCategory.GENERAL -> "GENERAL"
            }
            val result = aiRepository.getChatHistory(catParam)
            isLoading = false
            result.onSuccess { dtoList ->
                historyItems.clear()
                dtoList.forEach { dto ->
                    val cat = when (dto.category.uppercase()) {
                        "ANALYSIS" -> HistoryCategory.ANALYSIS
                        "SEARCH" -> HistoryCategory.SEARCH
                        else -> HistoryCategory.GENERAL
                    }
                    val icon = when (cat) {
                        HistoryCategory.ANALYSIS -> Icons.Outlined.Description
                        HistoryCategory.SEARCH -> Icons.Default.Search
                        else -> Icons.Outlined.ChatBubbleOutline
                    }
                    historyItems.add(
                        ChatHistoryItem(
                            id = dto.id,
                            title = dto.title,
                            snippet = dto.snippet,
                            dateText = dto.dateText ?: "Recent",
                            timeText = dto.timeText ?: "",
                            category = cat,
                            icon = icon
                        )
                    )
                }
            }.onFailure {
                // If API fails, keep current state
            }
        }
    }

    LaunchedEffect(selectedCategory) {
        fetchHistory()
    }

    val filteredItems = historyItems.filter { item ->
        val matchesSearch = searchQuery.isBlank() ||
                item.title.contains(searchQuery, ignoreCase = true) ||
                item.snippet.contains(searchQuery, ignoreCase = true)
        val matchesCategory = selectedCategory == HistoryCategory.ALL || item.category == selectedCategory
        matchesSearch && matchesCategory
    }

    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = {
                Text(
                    text = "Delete Chat",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete '${itemToDelete?.title}'? All messages in this chat will be removed.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = itemToDelete
                        itemToDelete = null
                        if (target != null) {
                            coroutineScope.launch {
                                val res = aiRepository.clearChatHistory(conversationId = target.id)
                                res.onSuccess {
                                    historyItems.removeAll { it.id == target.id }
                                    onDeleteIndividualClick(target)
                                }
                            }
                        }
                    }
                ) {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text(
                        text = "Cancel",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(
                    text = "Clear AI History",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to clear your AI chat history? All previous conversations and messages will be permanently deleted.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        coroutineScope.launch {
                            val res = aiRepository.clearChatHistory()
                            res.onSuccess {
                                historyItems.clear()
                                onClearHistoryClick()
                            }
                        }
                    }
                ) {
                    Text(
                        text = "Clear All",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text(
                        text = "Cancel",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
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
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = "AI Chat History",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (historyItems.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirmDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Clear History",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    AiFluidOrbAvatar(
                        size = 40.dp,
                        iconSize = 20.dp
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
                // Sub-heading
                item {
                    Text(
                        text = "Access your previous AI conversations\nand document searches.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
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
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary,
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
                                    color = Color(0xFF537779)
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

                // Loading State
                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                } else if (filteredItems.isEmpty()) {
                    // Empty State
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Chat,
                                contentDescription = "Empty History",
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No AI chat history found",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "Try a different search keyword" else "Your conversations will appear here",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    }
                } else {
                    // History Items List
                    items(filteredItems, key = { it.id }) { item ->
                        HistoryCardItem(
                            item = item,
                            onClick = { onHistoryItemClick(item) },
                            onDeleteClick = { itemToDelete = item }
                        )
                    }
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
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.primary

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
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = MaterialTheme.colorScheme.primary,
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
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "${item.dateText}\n${item.timeText}",
                    fontSize = 10.sp,
                    color = Color(0xFF9CA3AF),
                    lineHeight = 13.sp,
                    textAlign = TextAlign.End
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

        Spacer(modifier = Modifier.width(4.dp))

        // Individual Delete Button on the side of the chat item
        IconButton(
            onClick = onDeleteClick,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "Delete Chat",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp)
            )
        }

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
