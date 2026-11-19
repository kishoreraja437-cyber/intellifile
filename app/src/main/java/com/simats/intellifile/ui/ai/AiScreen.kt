package com.simats.intellifile.ui.ai

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
    val source: String = "AI Vault", // "Device Files" or "AI Vault"
    val uriString: String? = null,
    val filePath: String? = null,
    val fileUrl: String? = null,
    val category: String? = null
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
            text = "I am IntelliFile AI and I help to find the files",
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
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .then(if (!isKeyboardOpen) Modifier.navigationBarsPadding() else Modifier)
        ) {
            val hasActiveConversation = uiState.currentConversationId != null || displayMessages.any { it is ChatMessage.User }

            // Header with Back Button and Robot Avatar
            AiHeader(
                onBackClick = onBackClick,
                onViewSearchHistoryClick = onViewSearchHistoryClick,
                hasActiveConversation = hasActiveConversation,
                conversationTitle = uiState.conversationTitle,
                onNewChatClick = handleStartNewChat,
                onMicClick = onMicClick
            )

            // Chat Messages List
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
                                    color = MaterialTheme.colorScheme.primary,
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
                                        onCopy = {
                                            clipboardManager.setText(AnnotatedString(message.text))
                                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        onEdit = {
                                            editingMessageIndex = index
                                            editingMessageText = message.text
                                            isEditingUserMessage = false
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

        // Animated Edit Message Dialog with Living AI Aura & Spring Physics
        if (editingMessageIndex != null) {
            val targetIdx = editingMessageIndex!!
            AnimatedEditMessageDialog(
                isOpen = true,
                isUserMessage = isEditingUserMessage,
                initialText = editingMessageText,
                onDismiss = { editingMessageIndex = null },
                onSave = { newText ->
                    if (newText.isNotBlank()) {
                        chatViewModel.updateMessageAt(targetIdx, newText)
                        Toast.makeText(
                            context,
                            if (isEditingUserMessage) "Message updated" else "AI response updated",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    editingMessageIndex = null
                },
                onSaveAndAskAi = if (isEditingUserMessage) {
                    { newText ->
                        if (newText.isNotBlank()) {
                            chatViewModel.updateMessageAt(targetIdx, newText)
                            editingMessageIndex = null
                            chatViewModel.sendMessage(newText)
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
    size: Dp = 90.dp,
    iconSize: Dp = 48.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "aiOrbPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing)
        ),
        label = "rotationAngle"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale, rotationZ = rotationAngle)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF26E07F).copy(alpha = 0.85f),
                        Color(0xFF00C2A8).copy(alpha = 0.65f),
                        Color(0xFF55A9AF).copy(alpha = 0.35f),
                        Color.Transparent
                    )
                )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize(0.78f)
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

        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = "AI Sparkle Logo",
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun AiHeader(
    onBackClick: () -> Unit,
    onViewSearchHistoryClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasActiveConversation: Boolean = false,
    conversationTitle: String = "IntelliFile AI",
    onNewChatClick: () -> Unit = {},
    onMicClick: () -> Unit = {}
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (hasActiveConversation) {
                    Spacer(modifier = Modifier.width(4.dp))
                    AiFluidOrbAvatar(size = 36.dp, iconSize = 20.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = conversationTitle.ifBlank { "IntelliFile AI" },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "I help to find the files",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMicClick) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Assistant",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                if (hasActiveConversation) {
                    IconButton(onClick = onViewSearchHistoryClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Chat,
                            contentDescription = "Chat History",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onNewChatClick() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Chat",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "New Chat",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        if (!hasActiveConversation) {
            Spacer(modifier = Modifier.height(4.dp))

            AiFluidOrbAvatar(
                size = 90.dp,
                iconSize = 48.dp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "IntelliFile AI",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "I am IntelliFile AI and I help to find the files",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            ViewSearchHistoryButton(
                onClick = onViewSearchHistoryClick,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
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
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
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
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "View AI Search History",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "View History",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
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
    val enterAnim = remember { Animatable(0.92f) }
    val alphaAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enterAnim.animateTo(1.0f, tween(250, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        alphaAnim.animateTo(1.0f, tween(200, easing = FastOutSlowInEasing))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = enterAnim.value, scaleY = enterAnim.value, alpha = alphaAnim.value),
        horizontalAlignment = Alignment.End
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
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Text(
                text = text,
                fontSize = 14.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }

        // Action Options: Copy & Edit
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp, end = 4.dp)
        ) {
            MessageActionButton(
                icon = Icons.Outlined.ContentCopy,
                label = "Copy",
                onClick = onCopy
            )
            Spacer(modifier = Modifier.width(6.dp))
            MessageActionButton(
                icon = Icons.Outlined.Edit,
                label = "Edit",
                onClick = onEdit
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
    onCopy: () -> Unit = {},
    onEdit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val enterAnim = remember { Animatable(0.92f) }
    val alphaAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enterAnim.animateTo(1.0f, tween(280, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        alphaAnim.animateTo(1.0f, tween(220, easing = FastOutSlowInEasing))
    }

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
            .fillMaxWidth()
            .graphicsLayer(scaleX = enterAnim.value, scaleY = enterAnim.value, alpha = alphaAnim.value),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Dusky Aqua AI Response Bubble
        Box(
            modifier = Modifier
                .shadow(
                    elevation = 3.dp,
                    shape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = 4.dp,
                        bottomEnd = 20.dp
                    ),
                    ambientColor = Color(0xFF207583).copy(alpha = 0.15f),
                    spotColor = Color(0xFF207583).copy(alpha = 0.22f),
                    clip = false
                )
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = 4.dp,
                        bottomEnd = 20.dp
                    )
                )
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFE6F5F5), // Soft Dusky Aqua Light
                            Color(0xFFD6EFEF)  // Dusky Aqua Surface
                        )
                    )
                )
                .border(
                    width = 1.2.dp,
                    color = Color(0xFFA4D8DD).copy(alpha = 0.85f),
                    shape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = 4.dp,
                        bottomEnd = 20.dp
                    )
                )
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF207583)
                    )
                    Text(
                        text = text,
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                        color = Color(0xFF207583).copy(alpha = thinkingAlpha)
                    )
                }
            } else {
                Text(
                    text = text,
                    fontSize = 14.sp,
                    color = Color(0xFF0F3E46), // Deep Dusky Aqua for sharp, beautiful legibility
                    fontWeight = FontWeight.Medium,
                    lineHeight = 20.sp
                )
            }
        }

        if (files.isNotEmpty()) {
            files.forEach { file ->
                AiFileCard(file = file, onOpenClick = { onOpenFile(file) })
            }
        }

        // Action Options: Copy & Edit (only when response is ready)
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
                    tintColor = Color(0xFF207583) // Dusky Aqua
                )
                Spacer(modifier = Modifier.width(6.dp))
                MessageActionButton(
                    icon = Icons.Outlined.Edit,
                    label = "Edit",
                    onClick = onEdit,
                    tintColor = Color(0xFF207583) // Dusky Aqua
                )
            }
        }
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
    val activeColor = tintColor ?: MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = activeColor.copy(alpha = 0.75f),
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = activeColor
        )
    }
}

@Composable
fun AiFileCard(
    file: AiFileResult,
    onOpenClick: () -> Unit,
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
                else -> "AI Vault / Cloud Storage"
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
        IconButton(
            onClick = onMicClick,
            enabled = !isThinking,
            modifier = Modifier.size(38.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Voice Assistant",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

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
    isUserMessage: Boolean,
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onSaveAndAskAi: ((String) -> Unit)? = null,
    onCopyToInputBar: ((String) -> Unit)? = null
) {
    if (!isOpen) return

    var currentText by remember(initialText) { mutableStateOf(initialText) }

    // Spring Entrance Animation
    val scaleAnim = remember { Animatable(0.72f) }
    val alphaAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(36f) }

    LaunchedEffect(Unit) {
        launch {
            scaleAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            alphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
            )
        }
        launch {
            offsetYAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    // Ambient AI Living Glow & Shimmer Transitions
    val infiniteTransition = rememberInfiniteTransition(label = "aiDialogPulse")

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val borderOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "borderOffset"
    )

    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )

    val sparkleRotation by infiniteTransition.animateFloat(
        initialValue = -14f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkleRotation"
    )

    val sparkleScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkleScale"
    )

    // Dusky Aqua Dynamic Living Holographic Border
    val livingBorderBrush = Brush.linearGradient(
        colors = if (!isUserMessage) {
            listOf(
                Color(0xFF134E57), // Deep Dusky Aqua
                Color(0xFF207583), // Primary Dusky Aqua
                Color(0xFF3293A0), // Medium Dusky Aqua
                Color(0xFF5AB3BE), // Light Dusky Aqua
                Color(0xFF7DD3DC), // Aqua Highlight
                Color(0xFF207583), // Primary Dusky Aqua
                Color(0xFF134E57)  // Loop
            )
        } else {
            listOf(
                Color(0xFF6366F1),
                Color(0xFF8B5CF6),
                Color(0xFFEC4899),
                Color(0xFF06B6D4),
                Color(0xFF3B82F6),
                Color(0xFF6366F1)
            )
        },
        start = Offset(borderOffset, 0f),
        end = Offset(borderOffset + 450f, 650f)
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        // Scrim with smooth fade
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.58f * alphaAnim.value))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .padding(horizontal = 20.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            // Main Animated Dialog Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scaleAnim.value
                        scaleY = scaleAnim.value
                        alpha = alphaAnim.value
                        translationY = offsetYAnim.value
                    }
                    .shadow(
                        elevation = 22.dp,
                        shape = RoundedCornerShape(26.dp),
                        ambientColor = if (!isUserMessage) Color(0xFF207583).copy(alpha = glowAlpha) else Color(0xFF8B5CF6).copy(alpha = glowAlpha),
                        spotColor = if (!isUserMessage) Color(0xFF134E57).copy(alpha = glowAlpha) else Color(0xFF6366F1).copy(alpha = glowAlpha),
                        clip = false
                    )
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFFFFFF),
                                Color(0xFFFBFBFE),
                                Color(0xFFF8FAFC)
                            )
                        )
                    )
                    .border(
                        width = 2.dp,
                        brush = livingBorderBrush,
                        shape = RoundedCornerShape(26.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Consume click
                    )
            ) {
                // Specular Light Sheen Overlay
                Canvas(modifier = Modifier.matchParentSize()) {
                    val sweepX = size.width * shimmerProgress
                    val sweepBrush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.40f),
                            Color.Transparent
                        ),
                        start = Offset(sweepX - 80.dp.toPx(), 0f),
                        end = Offset(sweepX + 80.dp.toPx(), size.height * 0.7f)
                    )
                    drawRect(brush = sweepBrush)

                    // Subtle radial specular gleam in top-left
                    val cornerGleam = Brush.radialGradient(
                        colors = listOf(
                            (if (!isUserMessage) Color(0xFFA4D8DD) else Color(0xFFC7D2FE)).copy(alpha = 0.40f),
                            Color.Transparent
                        ),
                        center = Offset(30.dp.toPx(), 30.dp.toPx()),
                        radius = 130.dp.toPx()
                    )
                    drawRect(brush = cornerGleam)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header: Animated AI Sparkle Orb, Title, and Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // Pulsating AI 3D Orb Icon with Dusky Aqua
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .shadow(
                                        elevation = 8.dp,
                                        shape = CircleShape,
                                        ambientColor = if (!isUserMessage) Color(0xFF207583).copy(alpha = 0.45f) else Color(0xFF7C3AED).copy(alpha = 0.45f),
                                        spotColor = if (!isUserMessage) Color(0xFF134E57).copy(alpha = 0.55f) else Color(0xFF4F46E5).copy(alpha = 0.55f)
                                    )
                                    .clip(CircleShape)
                                    .background(
                                        brush = Brush.radialGradient(
                                            colors = if (!isUserMessage) {
                                                listOf(
                                                    Color(0xFF459CA9), // Dusky Aqua light
                                                    Color(0xFF207583), // Dusky Aqua primary
                                                    Color(0xFF134E57)  // Dusky Aqua deep
                                                )
                                            } else {
                                                listOf(
                                                    Color(0xFF8B5CF6),
                                                    Color(0xFF6366F1),
                                                    Color(0xFF4F46E5)
                                                )
                                            }
                                        )
                                    )
                                    .border(1.5.dp, Color.White.copy(alpha = 0.75f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Sparkle",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .graphicsLayer {
                                            rotationZ = sparkleRotation
                                            scaleX = sparkleScale
                                            scaleY = sparkleScale
                                        }
                                )
                            }

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (isUserMessage) "Edit Prompt" else "Edit AI Response",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (!isUserMessage) Color(0xFFE0F4F4) else Color(0xFFEEF2FF))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isUserMessage) "User Query" else "AI Smart Edit",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (!isUserMessage) Color(0xFF134E57) else Color(0xFF4F46E5)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isUserMessage) "Refine your prompt to update search results" else "Personalize or fix this AI response in chat",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Close Button with smooth hover effect
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
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Text Editor Box with Dusky Aqua Styling
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (!isUserMessage) Color(0xFFF0F9F9) else Color(0xFFF8FAFC))
                            .border(
                                width = 1.2.dp,
                                color = if (!isUserMessage) Color(0xFFA4D8DD) else Color(0xFFCBD5E1),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(14.dp)
                    ) {
                        OutlinedTextField(
                            value = currentText,
                            onValueChange = { currentText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 110.dp, max = 220.dp),
                            shape = RoundedCornerShape(12.dp),
                            placeholder = {
                                Text(
                                    text = if (isUserMessage) "Type your prompt here..." else "Type or refine AI response text...",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 14.sp
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (!isUserMessage) Color(0xFF207583) else Color(0xFF6366F1),
                                unfocusedBorderColor = if (!isUserMessage) Color(0xFFBFE3E6) else Color(0xFFE2E8F0),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                cursorColor = if (!isUserMessage) Color(0xFF207583) else Color(0xFF6366F1)
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Character & Word Counter Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val wordCount = remember(currentText) {
                                currentText.trim().split(Regex("\\s+")).count { it.isNotBlank() }
                            }
                            Text(
                                text = "${currentText.length} characters · $wordCount words",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF64748B)
                            )

                            if (currentText.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { currentText = "" }
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Clear",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }

                    // Auxiliary Action: Copy to Input Bar
                    if (onCopyToInputBar != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (!isUserMessage) Color(0xFFE0F4F4) else Color(0xFFEEF2FF))
                                .clickable { onCopyToInputBar(currentText) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                tint = if (!isUserMessage) Color(0xFF207583) else Color(0xFF4F46E5),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Copy text to input bar",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (!isUserMessage) Color(0xFF134E57) else Color(0xFF4F46E5)
                            )
                        }
                    }

                    // Action Buttons Row (Cancel, Save & Ask AI, Save)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cancel Button
                        DialogBouncyButton(
                            text = "Cancel",
                            onClick = onDismiss,
                            modifier = Modifier.weight(0.85f),
                            isPrimary = false
                        )

                        // Save & Ask AI (User prompt)
                        if (isUserMessage && onSaveAndAskAi != null) {
                            DialogBouncyButton(
                                text = "Save & Ask",
                                icon = Icons.Default.AutoAwesome,
                                onClick = { onSaveAndAskAi(currentText) },
                                modifier = Modifier.weight(1.15f),
                                isPrimary = true,
                                gradientColors = listOf(Color(0xFF2563EB), Color(0xFF4F46E5))
                            )
                        }

                        // Save Button
                        DialogBouncyButton(
                            text = "Save",
                            icon = Icons.Default.Check,
                            onClick = { onSave(currentText) },
                            modifier = Modifier.weight(1f),
                            isPrimary = true,
                            gradientColors = if (!isUserMessage) {
                                listOf(Color(0xFF207583), Color(0xFF134E57)) // Dusky Aqua Primary
                            } else {
                                listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DialogBouncyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    isPrimary: Boolean = false,
    gradientColors: List<Color> = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "btnScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isPrimary) {
                    Modifier
                        .shadow(
                            elevation = 6.dp,
                            shape = RoundedCornerShape(12.dp),
                            ambientColor = gradientColors.first().copy(alpha = 0.4f),
                            spotColor = gradientColors.last().copy(alpha = 0.5f)
                        )
                        .background(brush = Brush.horizontalGradient(gradientColors))
                } else {
                    Modifier
                        .background(Color(0xFFF1F5F9))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 12.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isPrimary) Color.White else Color(0xFF475569),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPrimary) Color.White else Color(0xFF475569),
                maxLines = 1
            )
        }
    }
}
