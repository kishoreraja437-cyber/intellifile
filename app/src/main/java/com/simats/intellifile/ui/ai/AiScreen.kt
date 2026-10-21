package com.simats.intellifile.ui.ai

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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

data class AiFileResult(
    val title: String,
    val format: String,
    val size: String,
    val dateText: String,
    val priority: PriorityLevel,
    val iconResId: Int = R.drawable.pdf
)

sealed class ChatMessage {
    data class User(val text: String) : ChatMessage()
    data class Ai(val text: String, val files: List<AiFileResult> = emptyList()) : ChatMessage()
}

@Composable
fun AiScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onOpenFile: (AiFileResult) -> Unit = {},
    onViewSearchHistoryClick: () -> Unit = {}
) {
    var promptInput by remember { mutableStateOf("") }

    val initialMessages = remember {
        mutableStateListOf(
            ChatMessage.User("Find my AML assignment file"),
            ChatMessage.Ai(
                text = "I found 3 files related to AML assignment.",
                files = listOf(
                    AiFileResult(
                        title = "AML Assignment.pdf",
                        format = "PDF",
                        size = "2.4 MB",
                        dateText = "Oct 5, 2024",
                        priority = PriorityLevel.IMPORTANT
                    ),
                    AiFileResult(
                        title = "AML Notes.pdf",
                        format = "PDF",
                        size = "5.1 MB",
                        dateText = "Oct 2, 2024",
                        priority = PriorityLevel.NORMAL
                    ),
                    AiFileResult(
                        title = "AML Question Bank.pdf",
                        format = "PDF",
                        size = "3.2 MB",
                        dateText = "Sep 28, 2024",
                        priority = PriorityLevel.LOW
                    )
                )
            )
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
            // Header with Back Button and Robot Avatar
            AiHeader(
                onBackClick = onBackClick,
                onViewSearchHistoryClick = onViewSearchHistoryClick
            )

            // Chat Messages List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)
            ) {
                items(initialMessages) { message ->
                    when (message) {
                        is ChatMessage.User -> {
                            UserMessageBubble(text = message.text)
                        }
                        is ChatMessage.Ai -> {
                            AiMessageBubble(
                                text = message.text,
                                files = message.files,
                                onOpenFile = onOpenFile
                            )
                        }
                    }
                }
            }

            // Bottom Prompt Bar
            AiBottomInputBar(
                query = promptInput,
                onQueryChange = { promptInput = it },
                onSendClick = {
                    if (promptInput.isNotBlank()) {
                        initialMessages.add(ChatMessage.User(promptInput))
                        val userQuery = promptInput
                        promptInput = ""
                        initialMessages.add(
                            ChatMessage.Ai(
                                text = "Searching for files matching \"$userQuery\"..."
                            )
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun AiHeader(
    onBackClick: () -> Unit,
    onViewSearchHistoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF111827)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Large Robot Avatar
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(Color(0xFFDCE2FF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = "IntelliFile AI Robot",
                tint = Color(0xFF6C5CE7),
                modifier = Modifier.size(54.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "IntelliFile AI",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF111827)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Find your files with natural language",
            fontSize = 14.sp,
            color = Color(0xFF6B7280)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // View AI Search History Button
        ViewSearchHistoryButton(
            onClick = onViewSearchHistoryClick,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}

@Composable
fun ViewSearchHistoryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFEEF0FD))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = "AI Sparkle",
                tint = Color(0xFF6C5CE7),
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "View AI Search History",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6C5CE7)
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "View History",
            tint = Color(0xFF6C5CE7),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun UserMessageBubble(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = 20.dp,
                        bottomEnd = 4.dp
                    )
                )
                .background(Color(0xFF6C5CE7))
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Text(
                text = text,
                fontSize = 14.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun AiMessageBubble(
    text: String,
    files: List<AiFileResult>,
    onOpenFile: (AiFileResult) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = 4.dp,
                        bottomEnd = 20.dp
                    )
                )
                .background(Color(0xFFE0E7FF))
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Text(
                text = text,
                fontSize = 14.sp,
                color = Color(0xFF1E1B4B),
                fontWeight = FontWeight.Medium
            )
        }

        if (files.isNotEmpty()) {
            files.forEach { file ->
                AiFileCard(file = file, onOpenClick = { onOpenFile(file) })
            }
        }
    }
}

@Composable
fun AiFileCard(
    file: AiFileResult,
    onOpenClick: () -> Unit,
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
                text = "${file.format} · ${file.size} · ${file.dateText}",
                fontSize = 11.sp,
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            modifier = Modifier.height(36.dp)
        ) {
            Text(
                text = "Open",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

@Composable
fun AiBottomInputBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .height(56.dp)
            .clip(CircleShape)
            .background(Color(0xFFEEF0FD))
            .padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (query.isEmpty()) {
                Text(
                    text = "Ask anything about your files...",
                    fontSize = 14.sp,
                    color = Color(0xFF6B7280)
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFF6C5CE7))
                .clickable { onSendClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AiScreenPreview() {
    AiScreen()
}
