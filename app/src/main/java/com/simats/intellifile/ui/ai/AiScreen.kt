package com.simats.intellifile.ui.ai

import com.simats.intellifile.ui.common.AnimatedMicrophoneButton

import androidx.compose.animation.core.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.simats.intellifile.ui.common.DraggableScrollbar
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Error
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import android.widget.Toast
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simats.intellifile.R
import com.simats.intellifile.data.manager.LocalFileManager
import com.simats.intellifile.data.repository.AiRepository
import com.simats.intellifile.ui.home.PriorityLevel
import com.simats.intellifile.ui.home.PriorityTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class AiFileResult(
    val title: String,
    val format: String,
    val size: String,
    val dateText: String,
    val priority: PriorityLevel? = null,
    val iconResId: Int = R.drawable.pdf,
    val id: Int? = null,
    val page: Int? = null,
    val snippet: String? = null,
    val source: String = "IntelliSpace", // "Device Files" or "IntelliSpace"
    val uriString: String? = null,
    val filePath: String? = null,
    val fileUrl: String? = null,
    val category: String? = null,
    val pendingPriorityAction: PriorityLevel? = null
)

private fun getFormatIcon(format: String?): Int {
    return when (format?.uppercase()) {
        "DOCX", "DOC" -> R.drawable.doc
        "PPT", "PPTX" -> R.drawable.ppt
        else -> R.drawable.pdf
    }
}

sealed class ChatMessage {
    data class User(val text: String) : ChatMessage()
    data class Ai(
        val text: String,
        val files: List<AiFileResult> = emptyList(),
        val isLoading: Boolean = false
    ) : ChatMessage()
}

@Composable
fun AiScreen(
    modifier: Modifier = Modifier,
    chatViewModel: ChatViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    initialConversationId: String? = null,
    onBackClick: () -> Unit = {},
    onOpenFile: (AiFileResult) -> Unit = {},
    onViewSearchHistoryClick: () -> Unit = {},
    onNewChatClick: () -> Unit = {},
    onMicClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val aiRepository = remember { AiRepository(context) }
    val uiState by chatViewModel.uiState.collectAsState()

    val defaultGreeting = remember {
        ChatMessage.Ai(
            text = "I am Intelli AI and I help to find the files",
            files = emptyList()
        )
    }

    var promptInput by remember { mutableStateOf("") }
    val displayMessages = if (uiState.messages.isNotEmpty()) uiState.messages else listOf(defaultGreeting)

    val clipboardManager = LocalClipboardManager.current
    var editingMessageIndex by remember { mutableStateOf<Int?>(null) }
    var editingMessageText by remember { mutableStateOf("") }
    var isEditingUserMessage by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Keyboard (IME) Visibility & WindowInsets Detection
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val isKeyboardOpen = imeBottom > 0

    // Automatically scroll to latest message when messages change or keyboard appears
    LaunchedEffect(displayMessages.size, isKeyboardOpen) {
        if (displayMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayMessages.size - 1)
        }
    }

    // Load conversation when an initialConversationId is provided and not already active
    LaunchedEffect(initialConversationId) {
        if (!initialConversationId.isNullOrBlank() && initialConversationId != uiState.currentConversationId) {
            chatViewModel.loadConversation(initialConversationId)
        }
    }

    val handleStartNewChat: () -> Unit = {
        chatViewModel.startNewChat()
        promptInput = ""
        onNewChatClick()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF2FBF9),
                        Color(0xFFEAF6F3),
                        Color(0xFFF5FCFA)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .then(if (!isKeyboardOpen) Modifier.navigationBarsPadding() else Modifier)
        ) {
            val hasActiveConversation = uiState.currentConversationId != null || uiState.messages.any { it is ChatMessage.User }

            // Header with Back Button and Robot Avatar
            AiHeader(
                onBackClick = onBackClick,
                onViewSearchHistoryClick = onViewSearchHistoryClick,
                hasActiveConversation = hasActiveConversation,
                conversationTitle = uiState.conversationTitle,
                onNewChatClick = handleStartNewChat,
                onMicClick = onMicClick
            )

            if (!hasActiveConversation) {
                // Empty state matching reference image Left Screen
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        AiFluidOrbAvatar(
                            size = 110.dp,
                            iconSize = 54.dp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "IntelliFile AI",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F3E46),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "I am Intelli AI and I help to find the files",
                            fontSize = 13.5.sp,
                            color = Color(0xFF5A7A80),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(26.dp))

                        ViewSearchHistoryButton(
                            onClick = onViewSearchHistoryClick,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Greeting Pill with Aqua Border (No Copy / Edit buttons below)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color.White)
                                .border(1.5.dp, Color(0xFF80CBC4), RoundedCornerShape(22.dp))
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "I am Intelli AI and I help to find the files",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF0F3E46)
                            )
                        }
                    }
                }
            } else {
                // Active chat messages list matching reference image Right Screen
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (uiState.isLoadingConversation) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF009688),
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(displayMessages) { index, message ->
                                when (message) {
                                    is ChatMessage.User -> {
                                        UserMessageBubble(
                                            text = message.text,
                                            onCopy = {
                                                clipboardManager.setText(AnnotatedString(message.text))
                                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                            },
                                            onEdit = {
                                                editingMessageIndex = index
                                                editingMessageText = message.text
                                                isEditingUserMessage = true
                                            }
                                        )
                                    }
                                    is ChatMessage.Ai -> {
                                        AiMessageBubble(
                                            text = message.text,
                                            files = message.files,
                                            isLoading = message.isLoading,
                                            onOpenFile = onOpenFile,
                                            onSelectPriorityClick = { file, prio ->
                                                chatViewModel.assignPriorityToFile(file, prio)
                                            },
                                            onCopy = {
                                                clipboardManager.setText(AnnotatedString(message.text))
                                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    DraggableScrollbar(
                        state = listState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(top = 8.dp, bottom = 8.dp)
                    )
                }
            }

        // Edit Message Popup
        if (editingMessageIndex != null) {
            val targetIdx = editingMessageIndex!!
            AnimatedEditMessageDialog(
                isOpen = true,
                isUserMessage = isEditingUserMessage,
                initialText = editingMessageText,
                onDismiss = { editingMessageIndex = null },
                onSave = { newText ->
                    if (newText.isNotBlank()) {
                        chatViewModel.updateMessageAt(targetIdx, newText.trim())
                        Toast.makeText(
                            context,
                            "Message updated",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    editingMessageIndex = null
                },
                onSaveAndAskAi = if (isEditingUserMessage) {
                    { newText ->
                        if (newText.isNotBlank()) {
                            editingMessageIndex = null
                            chatViewModel.saveAndAsk(targetIdx, newText.trim())
                        }
                    }
                } else null,
                onCopyToInputBar = { textToCopy ->
                    promptInput = textToCopy
                    editingMessageIndex = null
                    Toast.makeText(context, "Loaded into input bar", Toast.LENGTH_SHORT).show()
                }
            )
        }

            // Bottom Prompt Bar
            AiBottomInputBar(
                query = promptInput,
                isThinking = uiState.isThinking,
                onQueryChange = { promptInput = it },
                onMicClick = onMicClick,
                onSendClick = {
                    if (promptInput.isNotBlank() && !uiState.isThinking) {
                        val userQuery = promptInput.trim()
                        promptInput = ""
                        chatViewModel.sendMessage(userQuery)
                    }
                }
            )
        }
    }
}

@Composable
fun AiFluidOrbAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    iconSize: Dp = 48.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "aiOrbPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
        ) {
            val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
            val radius = size.toPx() * 0.44f

            // 1. Soft radial background glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2DD4BF).copy(alpha = 0.35f),
                        Color(0xFF00BFA5).copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.toPx() * 0.5f
                )
            )

            // 2. Central Solid Teal Orb Disc
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF2DD4BF),
                        Color(0xFF00BFA5),
                        Color(0xFF00897B)
                    ),
                    start = Offset(center.x - radius, center.y - radius),
                    end = Offset(center.x + radius, center.y + radius)
                ),
                radius = radius,
                center = center
            )

            // 3. Subtle inner lighting sheen
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.28f),
                        Color.Transparent
                    ),
                    center = Offset(center.x - radius * 0.35f, center.y - radius * 0.35f),
                    radius = radius * 0.7f
                ),
                radius = radius * 0.7f,
                center = Offset(center.x - radius * 0.35f, center.y - radius * 0.35f)
            )

            // 4. Crisp 4-pointed white sparkle stars inside
            // Large Star at center-left
            drawSparkleStar(
                center = Offset(center.x - radius * 0.15f, center.y),
                outerRadius = radius * 0.48f,
                color = Color.White
            )
            // Smaller Star at upper-right
            drawSparkleStar(
                center = Offset(center.x + radius * 0.42f, center.y - radius * 0.36f),
                outerRadius = radius * 0.24f,
                color = Color.White
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSparkleStar(
    center: Offset,
    outerRadius: Float,
    color: Color
) {
    val path = Path().apply {
        moveTo(center.x, center.y - outerRadius)
        quadraticBezierTo(center.x, center.y, center.x + outerRadius, center.y)
        quadraticBezierTo(center.x, center.y, center.x, center.y + outerRadius)
        quadraticBezierTo(center.x, center.y, center.x - outerRadius, center.y)
        quadraticBezierTo(center.x, center.y, center.x, center.y - outerRadius)
        close()
    }
    drawPath(path = path, color = color)
}

@Composable
fun AiHeader(
    onBackClick: () -> Unit,
    onViewSearchHistoryClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasActiveConversation: Boolean = false,
    conversationTitle: String = "Inteli AI",
    onNewChatClick: () -> Unit = {},
    onMicClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left side: Back Button & (if active) Avatar + Title + Subtitle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF0F3E46)
                )
            }

            if (hasActiveConversation) {
                Spacer(modifier = Modifier.width(4.dp))
                AiFluidOrbAvatar(size = 38.dp, iconSize = 18.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = conversationTitle.ifBlank { "Inteli AI" },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F3E46),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "I help to find the files",
                        fontSize = 11.sp,
                        color = Color(0xFF5A7A80)
                    )
                }
            }
        }

        // Right side:
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedMicrophoneButton(
                onClick = onMicClick,
                buttonSize = 36.dp
            )

            if (hasActiveConversation) {
                IconButton(onClick = onViewSearchHistoryClick) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Search History",
                        tint = Color(0xFF0F3E46)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFE0F4F2))
                        .clickable { onNewChatClick() }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "+ New Chat",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF009688)
                    )
                }
            }
        }
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
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFE0F4F2))
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "AI Sparkle",
                tint = Color(0xFF009688),
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "View AI Search History",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F3E46)
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "View History",
            tint = Color(0xFF009688),
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun UserMessageBubble(
    text: String,
    onCopy: () -> Unit = {},
    onEdit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0xFF009688))
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                text = text,
                fontSize = 14.sp,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Action Options: Copy & Edit
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp, end = 6.dp)
        ) {
            MessageActionButton(
                icon = Icons.Outlined.ContentCopy,
                label = "Copy",
                onClick = onCopy,
                tintColor = Color(0xFF009688)
            )
            Spacer(modifier = Modifier.width(12.dp))
            MessageActionButton(
                icon = Icons.Outlined.Edit,
                label = "Edit",
                onClick = onEdit,
                tintColor = Color(0xFF009688)
            )
        }
    }
}

@Composable
fun AiMessageBubble(
    text: String,
    files: List<AiFileResult>,
    isLoading: Boolean = false,
    onOpenFile: (AiFileResult) -> Unit,
    onSelectPriorityClick: ((AiFileResult, PriorityLevel) -> Unit)? = null,
    onCopy: () -> Unit = {},
    onEdit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "aiThinkingPulse")
    val thinkingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thinkingAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Light mint AI response card matching reference image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFE6F7F5))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Analyzing",
                        tint = Color(0xFF00BFA5),
                        modifier = Modifier.size(26.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (text.isNotBlank() && !text.startsWith("Searching")) text else "Analyzing document...",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F3E46)
                        )
                        AiBouncingDotsIndicator(
                            dotColor = Color(0xFF00BFA5),
                            dotSize = 8.dp,
                            spacing = 8.dp
                        )
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF00BFA5),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = text,
                        fontSize = 14.sp,
                        color = Color(0xFF0F3E46),
                        fontWeight = FontWeight.Medium,
                        lineHeight = 20.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (files.isNotEmpty()) {
            files.forEach { file ->
                val priorityAction = file.pendingPriorityAction
                AiFileCard(
                    file = file,
                    onOpenClick = { onOpenFile(file) },
                    onSelectPriorityClick = if (priorityAction != null && onSelectPriorityClick != null) {
                        { onSelectPriorityClick(file, priorityAction) }
                    } else null
                )
            }
        }

        // Action Options: Copy only (Do not show the user Edit option below AI-generated responses)
        if (!isLoading) {
            Row(
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp)
            ) {
                MessageActionButton(
                    icon = Icons.Outlined.ContentCopy,
                    label = "Copy",
                    onClick = onCopy,
                    tintColor = Color(0xFF009688)
                )
            }
        }
    }
}

@Composable
fun AiBouncingDotsIndicator(
    modifier: Modifier = Modifier,
    dotColor: Color = Color(0xFF00BFA5),
    dotSize: Dp = 8.dp,
    spacing: Dp = 8.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bouncingDots")

    val dot0Scale by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot0Scale"
    )

    val dot1Scale by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450, delayMillis = 150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1Scale"
    )

    val dot2Scale by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450, delayMillis = 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2Scale"
    )

    val dot0Alpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot0Alpha"
    )

    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450, delayMillis = 150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1Alpha"
    )

    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450, delayMillis = 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2Alpha"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .graphicsLayer {
                    scaleX = dot0Scale
                    scaleY = dot0Scale
                    alpha = dot0Alpha
                }
                .background(dotColor, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(dotSize)
                .graphicsLayer {
                    scaleX = dot1Scale
                    scaleY = dot1Scale
                    alpha = dot1Alpha
                }
                .background(dotColor, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(dotSize)
                .graphicsLayer {
                    scaleX = dot2Scale
                    scaleY = dot2Scale
                    alpha = dot2Alpha
                }
                .background(dotColor, CircleShape)
        )
    }
}

@Composable
fun MessageActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tintColor: Color? = null
) {
    val activeColor = tintColor ?: Color(0xFF009688)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = activeColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = activeColor
        )
    }
}

@Composable
fun AiFileCard(
    file: AiFileResult,
    onOpenClick: () -> Unit,
    onSelectPriorityClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val iconRes = when (file.format.trim().removePrefix(".").uppercase()) {
        "DOCX", "DOC" -> R.drawable.doc
        "PPT", "PPTX" -> R.drawable.ppt
        else -> R.drawable.pdf
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onOpenClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = "File Type Icon",
            modifier = Modifier
                .size(44.dp)
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
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Metadata row: Source badge, Format, Size
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Source badge
                val isDevice = file.source == "Device Files"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isDevice) Color(0xFF0288D1).copy(alpha = 0.12f)
                            else Color(0xFF6750A4).copy(alpha = 0.12f)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = file.source,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDevice) Color(0xFF0288D1) else Color(0xFF6750A4)
                    )
                }

                Text(
                    text = "${file.format.uppercase()} · ${file.size}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // File Location
            val displayLocation = when {
                !file.filePath.isNullOrBlank() -> file.filePath
                !file.uriString.isNullOrBlank() -> file.uriString
                file.source == "Device Files" -> "Internal Storage / Documents"
                else -> "IntelliSpace / Cloud Storage"
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Location",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = displayLocation,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Date
            Text(
                text = file.dateText,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            if (!file.snippet.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "\"${file.snippet}\"",
                    fontSize = 11.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    maxLines = 2
                )
            }

            if (file.priority != null) {
                Spacer(modifier = Modifier.height(6.dp))
                PriorityTag(priority = file.priority)
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (file.pendingPriorityAction != null && onSelectPriorityClick != null) {
            val prioColor = when (file.pendingPriorityAction) {
                PriorityLevel.IMPORTANT -> Color(0xFFEF4444)
                PriorityLevel.NORMAL -> Color(0xFFF59E0B)
                PriorityLevel.LOW -> Color(0xFF10B981)
            }
            Button(
                onClick = onSelectPriorityClick,
                colors = ButtonDefaults.buttonColors(containerColor = prioColor),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Select",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Select",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

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
    }
}

@Composable
fun AiBottomInputBar(
    query: String,
    isThinking: Boolean = false,
    onQueryChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onMicClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .height(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusRequester.requestFocus()
            }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (query.isEmpty()) {
                Text(
                    text = if (isThinking) "AI is thinking..." else "Ask anything about your files...",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                enabled = !isThinking,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    capitalization = KeyboardCapitalization.Sentences
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (query.isNotBlank() && !isThinking) {
                            onSendClick()
                        }
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Mic Button in bottom bar
        AnimatedMicrophoneButton(
            onClick = onMicClick,
            enabled = !isThinking,
            buttonSize = 34.dp
        )

        Spacer(modifier = Modifier.width(4.dp))

        val sendScale by animateFloatAsState(
            targetValue = if (query.isNotBlank() && !isThinking) 1.08f else 1.0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "sendScale"
        )

        Box(
            modifier = Modifier
                .size(40.dp)
                .graphicsLayer(scaleX = sendScale, scaleY = sendScale)
                .clip(CircleShape)
                .background(if (isThinking || query.isBlank()) Color.Gray.copy(alpha = 0.45f) else MaterialTheme.colorScheme.primary)
                .clickable(enabled = !isThinking && query.isNotBlank()) { onSendClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = Color.White,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AiScreenPreview() {
    AiScreen()
}


@Composable
fun AnimatedEditMessageDialog(
    isOpen: Boolean,
    isUserMessage: Boolean = true,
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onSaveAndAskAi: ((String) -> Unit)? = null,
    onCopyToInputBar: ((String) -> Unit)? = null
) {
    if (!isOpen) return

    var currentText by remember(initialText) { mutableStateOf(initialText) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.52f))
                .imePadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .padding(horizontal = 22.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 14.dp,
                        shape = RoundedCornerShape(26.dp),
                        ambientColor = Color.Black.copy(alpha = 0.08f),
                        spotColor = Color.Black.copy(alpha = 0.12f)
                    )
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(26.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Consume touch events
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header: Circular AI Sparkle Badge, Title, User Query Badge, Subtitle & Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // Circular Teal Orb Badge with White Sparkles
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                Color(0xFF00BFA5),
                                                Color(0xFF009688),
                                                Color(0xFF00897B)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Sparkle",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Edit Message",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFFCCFBF1))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "User Query",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0D9488)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Refine your message to update the search results.",
                                    fontSize = 12.5.sp,
                                    color = Color(0xFF64748B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Close Button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF1F5F9))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF475569),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Compact Message Editing Text Field
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = currentText,
                            onValueChange = { currentText = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF0F172A)
                            ),
                            cursorBrush = SolidColor(Color(0xFF009688)),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Dynamic Character & Word Count + Clear Option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val charCount = currentText.length
                        val wordCount = remember(currentText) {
                            currentText.trim().split(Regex("\\s+")).count { it.isNotBlank() }
                        }
                        val charLabel = "$charCount ${if (charCount == 1) "character" else "characters"}"
                        val wordLabel = "$wordCount ${if (wordCount == 1) "word" else "words"}"
                        Text(
                            text = "$charLabel • $wordLabel",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { currentText = "" }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = Color(0xFF475569),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Clear",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF475569)
                            )
                        }
                    }

                    // Copy Text to Input Bar Button
                    if (onCopyToInputBar != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFE0F4F2))
                                .clickable { onCopyToInputBar(currentText) }
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                tint = Color(0xFF00695C),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Copy text to input bar",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF004D40)
                            )
                        }
                    }

                    // Bottom Action Buttons Row (Cancel, Save & Ask, Save)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cancel Button
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Text(
                                text = "Cancel",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF334155)
                            )
                        }

                        // Save & Ask Button
                        if (onSaveAndAskAi != null) {
                            Button(
                                onClick = { onSaveAndAskAi(currentText) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF009688)),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(46.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Save & Ask",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // Save Button
                        Button(
                            onClick = { onSave(currentText) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC7F1EB)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF00695C),
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Save",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00695C)
                            )
                        }
                    }
                }
            }
        }
    }
}
