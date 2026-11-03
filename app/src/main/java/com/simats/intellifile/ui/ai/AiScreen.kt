package com.simats.intellifile.ui.ai

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Add
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
    initialConversationId: Int? = null,
    onBackClick: () -> Unit = {},
    onOpenFile: (AiFileResult) -> Unit = {},
    onViewSearchHistoryClick: () -> Unit = {},
    onNewChatClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val aiRepository = remember { AiRepository(context) }

    val defaultGreeting = remember {
        ChatMessage.Ai(
            text = "Hello! I am your IntelliFile AI Assistant powered by RAG and Small Language Models.\n\nAsk me anything about your uploaded documents, search for files, request summaries, or ask general questions.",
            files = emptyList()
        )
    }

    var promptInput by remember { mutableStateOf("") }
    var currentConversationId by remember { mutableStateOf(initialConversationId) }
    var isThinking by remember { mutableStateOf(false) }
    var isLoadingConversation by remember { mutableStateOf(false) }

    val chatMessages = remember {
        mutableStateListOf<ChatMessage>(defaultGreeting)
    }

    // Load conversation when an initialConversationId is provided or changes
    LaunchedEffect(initialConversationId) {
        currentConversationId = initialConversationId
        if (initialConversationId != null && initialConversationId > 0) {
            isLoadingConversation = true
            val result = aiRepository.getConversationMessages(initialConversationId)
            isLoadingConversation = false
            result.onSuccess { msgs ->
                chatMessages.clear()
                if (msgs.isEmpty()) {
                    chatMessages.add(ChatMessage.Ai(text = "This conversation has no messages yet."))
                } else {
                    msgs.forEach { m ->
                        if (m.sender.equals("USER", ignoreCase = true)) {
                            chatMessages.add(ChatMessage.User(m.text))
                        } else {
                            val rawFiles = m.files ?: m.related_files ?: emptyList()
                            val mappedFiles = rawFiles.map { rf ->
                                val prio = when (rf.priority?.uppercase()) {
                                    "IMPORTANT" -> PriorityLevel.IMPORTANT
                                    "NORMAL" -> PriorityLevel.NORMAL
                                    "LOW" -> PriorityLevel.LOW
                                    else -> null
                                }
                                val icon = getFormatIcon(rf.format)
                                AiFileResult(
                                    id = rf.id,
                                    title = rf.title,
                                    format = rf.format ?: "PDF",
                                    size = rf.size ?: "1.0 MB",
                                    dateText = rf.dateText ?: "Recent",
                                    priority = prio,
                                    iconResId = icon,
                                    page = rf.page,
                                    snippet = rf.snippet,
                                    source = rf.source ?: "AI Vault",
                                    uriString = rf.uri_string,
                                    filePath = rf.file_path,
                                    fileUrl = rf.file_url,
                                    category = rf.category
                                )
                            }
                            chatMessages.add(ChatMessage.Ai(text = m.text, files = mappedFiles))
                        }
                    }
                }
            }.onFailure { err ->
                chatMessages.clear()
                chatMessages.add(
                    ChatMessage.Ai(
                        text = "Failed to load conversation: ${err.localizedMessage}"
                    )
                )
            }
        } else if (initialConversationId == null && currentConversationId == null && chatMessages.size <= 1) {
            chatMessages.clear()
            chatMessages.add(defaultGreeting)
        }
    }

    val handleStartNewChat: () -> Unit = {
        currentConversationId = null
        promptInput = ""
        isThinking = false
        isLoadingConversation = false
        chatMessages.clear()
        chatMessages.add(defaultGreeting)
        onNewChatClick()
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
                onViewSearchHistoryClick = onViewSearchHistoryClick,
                hasActiveConversation = currentConversationId != null,
                onNewChatClick = handleStartNewChat
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
                if (isLoadingConversation) {
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
                    items(chatMessages) { message ->
                        when (message) {
                            is ChatMessage.User -> {
                                UserMessageBubble(text = message.text)
                            }
                            is ChatMessage.Ai -> {
                                AiMessageBubble(
                                    text = message.text,
                                    files = message.files,
                                    isLoading = message.isLoading,
                                    onOpenFile = onOpenFile
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Prompt Bar
            AiBottomInputBar(
                query = promptInput,
                isThinking = isThinking,
                onQueryChange = { promptInput = it },
                onSendClick = {
                    if (promptInput.isNotBlank() && !isThinking) {
                        val userQuery = promptInput.trim()
                        promptInput = ""
                        chatMessages.add(ChatMessage.User(userQuery))
                        isThinking = true

                        val loadingMsg = ChatMessage.Ai(
                            text = "Searching files and analyzing context...",
                            isLoading = true
                        )
                        chatMessages.add(loadingMsg)

                        coroutineScope.launch {
                            val intent = AiChatIntentDetector.detect(userQuery)

                            when (intent) {
                                                                is AiUserIntent.FileSearch,
                                is AiUserIntent.FileTypeFilter,
                                is AiUserIntent.FileOpen -> {
                                    val searchKeyword = when (intent) {
                                        is AiUserIntent.FileSearch -> intent.query
                                        is AiUserIntent.FileOpen -> intent.targetName
                                        is AiUserIntent.FileTypeFilter -> intent.format
                                        else -> userQuery
                                    }
                                    val formatFilter = when (intent) {
                                        is AiUserIntent.FileSearch -> intent.format
                                        is AiUserIntent.FileTypeFilter -> intent.format
                                        else -> null
                                    }

                                    // 1. Search Local Device Files in parallel (MediaStore + content inspection)
                                    val localDeferred = async(Dispatchers.IO) {
                                        try {
                                            val allLocal = LocalFileManager.getDeviceFiles(context)
                                            val searchTokens = searchKeyword.lowercase()
                                                .split(Regex("[\\s_\\-,.]+"))
                                                .filter { it.length >= 2 && !setOf("the", "my", "all", "file", "files", "document", "documents", "notes", "for", "with", "about").contains(it) }

                                            allLocal.filter { f ->
                                                val matchesFormat = formatFilter == null || f.format.equals(formatFilter, ignoreCase = true)
                                                if (!matchesFormat) return@filter false

                                                if (intent is AiUserIntent.FileTypeFilter) {
                                                    return@filter true
                                                }

                                                val titleLower = f.title.lowercase()
                                                val pathLower = f.path.lowercase()
                                                val baseName = f.title.substringBeforeLast('.').lowercase()

                                                val directMatch = titleLower.contains(searchKeyword.lowercase()) ||
                                                        baseName.contains(searchKeyword.lowercase()) ||
                                                        pathLower.contains(searchKeyword.lowercase())

                                                if (directMatch) return@filter true

                                                if (searchTokens.isNotEmpty()) {
                                                    val tokenOverlap = searchTokens.count { token ->
                                                        titleLower.contains(token) || pathLower.contains(token)
                                                    }
                                                    if (tokenOverlap == searchTokens.size || (searchTokens.size >= 2 && tokenOverlap >= searchTokens.size - 1)) {
                                                        return@filter true
                                                    }
                                                }

                                                // Local content search for text-based files (< 500KB)
                                                if (f.sizeBytes in 1..500_000L && setOf("TXT", "CSV", "JSON", "MD", "DOCX", "PPTX").contains(f.format.uppercase())) {
                                                    try {
                                                        val textContent = LocalFileManager.readTextContent(context, f.uri.toString(), f.path, f.format, 10000)
                                                        if (textContent.contains(searchKeyword, ignoreCase = true)) {
                                                            return@filter true
                                                        }
                                                        if (searchTokens.isNotEmpty() && searchTokens.all { textContent.contains(it, ignoreCase = true) }) {
                                                            return@filter true
                                                        }
                                                    } catch (_: Exception) {}
                                                }

                                                false
                                            }.map { item ->
                                                AiFileResult(
                                                    id = item.id.toInt(),
                                                    title = item.title,
                                                    format = item.format,
                                                    size = item.sizeFormatted,
                                                    dateText = item.dateText,
                                                    priority = item.priority,
                                                    iconResId = getFormatIcon(item.format),
                                                    source = "Device Files",
                                                    uriString = item.uri.toString(),
                                                    filePath = item.path,
                                                    category = null
                                                )
                                            }
                                        } catch (e: Exception) {
                                            emptyList<AiFileResult>()
                                        }
                                    }

                                    // 2. Search AI Vault via Backend API in parallel
                                    val vaultDeferred = async(Dispatchers.IO) {
                                        aiRepository.sendChatMessage(
                                            message = userQuery,
                                            conversationId = currentConversationId
                                        )
                                    }

                                    val localMatches = localDeferred.await()
                                    val vaultResult = vaultDeferred.await()

                                    isThinking = false
                                    chatMessages.remove(loadingMsg)

                                    val vaultMatches = mutableListOf<AiFileResult>()
                                    var backendExplanation: String? = null

                                    vaultResult.onSuccess { response ->
                                        currentConversationId = response.conversation_id
                                        backendExplanation = response.message?.text ?: response.reply
                                        val rawFiles = response.message?.files
                                            ?: response.message?.related_files
                                            ?: response.related_files
                                            ?: emptyList()

                                        val mapped = rawFiles.map { rf ->
                                            val prio = when (rf.priority?.uppercase()) {
                                                "IMPORTANT" -> PriorityLevel.IMPORTANT
                                                "LOW" -> PriorityLevel.LOW
                                                "NORMAL" -> PriorityLevel.NORMAL
                                                else -> null
                                            }
                                            val icon = getFormatIcon(rf.format)
                                            AiFileResult(
                                                id = rf.id,
                                                title = rf.title,
                                                format = rf.format ?: "PDF",
                                                size = rf.size ?: "1.0 MB",
                                                dateText = rf.dateText ?: "Recent",
                                                priority = prio,
                                                iconResId = icon,
                                                page = rf.page,
                                                snippet = rf.snippet,
                                                source = rf.source ?: "AI Vault",
                                                uriString = rf.uri_string,
                                                filePath = rf.file_path,
                                                fileUrl = rf.file_url,
                                                category = rf.category
                                            )
                                        }
                                        vaultMatches.addAll(mapped)
                                    }.onFailure { err ->
                                        if (localMatches.isEmpty()) {
                                            chatMessages.add(
                                                ChatMessage.Ai(
                                                    text = "Could not reach the AI service: ${err.localizedMessage}\nPlease make sure the backend is running."
                                                )
                                            )
                                            return@launch
                                        }
                                    }

                                    // 3. Merge & Deduplicate (Device Files + AI Vault Files)
                                    val allMatches = mutableListOf<AiFileResult>()
                                    allMatches.addAll(localMatches)
                                    for (vf in vaultMatches) {
                                        if (allMatches.none { it.title.equals(vf.title, ignoreCase = true) }) {
                                            allMatches.add(vf)
                                        }
                                    }

                                    // 4. Formulate Response & Display in Chat UI
                                    if (allMatches.isEmpty()) {
                                        val emptyMsg = "No matching files found for '$searchKeyword'. Try another filename or keyword."
                                        chatMessages.add(ChatMessage.Ai(text = emptyMsg, files = emptyList()))
                                    } else {
                                        val reply = when (intent) {
                                            is AiUserIntent.FileOpen -> {
                                                if (allMatches.size == 1) {
                                                    val singleDoc = allMatches.first()
                                                    onOpenFile(singleDoc)
                                                    "Opening '**${singleDoc.title}**'. Tap below to view if not automatically opened."
                                                } else {
                                                    "I found **${allMatches.size} files** matching '**$searchKeyword**'. Tap a file card below to open it:"
                                                }
                                            }
                                            is AiUserIntent.FileTypeFilter -> {
                                                val fmt = formatFilter ?: "matching"
                                                "Here are your **${allMatches.size} ${fmt} file${if (allMatches.size > 1) "s" else ""}** found across Device Files and AI Vault:"
                                            }
                                            else -> {
                                                if (vaultMatches.isNotEmpty() && !backendExplanation.isNullOrBlank() && !backendExplanation!!.contains("could not find information", ignoreCase = true) && !backendExplanation!!.contains("No matching files", ignoreCase = true)) {
                                                    "I found **${allMatches.size} matching file${if (allMatches.size > 1) "s" else ""}** for '**$searchKeyword**' across Device Files and AI Vault:\n\n${backendExplanation}"
                                                } else {
                                                    "I found **${allMatches.size} matching file${if (allMatches.size > 1) "s" else ""}** for '**$searchKeyword**' across Device Files and AI Vault:"
                                                }
                                            }
                                        }

                                        chatMessages.add(
                                            ChatMessage.Ai(
                                                text = reply,
                                                files = allMatches
                                            )
                                        )
                                    }
                                }
                                else -> {
                                    // GENERAL_QUERY, DOCUMENT_QUERY, or APP_DATA_QUERY
                                    val result = aiRepository.sendChatMessage(
                                        message = userQuery,
                                        conversationId = currentConversationId
                                    )
                                    isThinking = false
                                    chatMessages.remove(loadingMsg)

                                    result.onSuccess { response ->
                                        currentConversationId = response.conversation_id
                                        val replyText = response.message?.text
                                            ?: response.reply
                                            ?: "I analyzed your documents."

                                        val rawFiles = response.message?.files
                                            ?: response.message?.related_files
                                            ?: response.related_files
                                            ?: emptyList()

                                        val mappedFiles = rawFiles.map { rf ->
                                            val prio = when (rf.priority?.uppercase()) {
                                                "IMPORTANT" -> PriorityLevel.IMPORTANT
                                                "LOW" -> PriorityLevel.LOW
                                                else -> PriorityLevel.NORMAL
                                            }
                                            val icon = getFormatIcon(rf.format)
                                            AiFileResult(
                                                id = rf.id,
                                                title = rf.title,
                                                format = rf.format ?: "PDF",
                                                size = rf.size ?: "1.0 MB",
                                                dateText = rf.dateText ?: "Recent",
                                                priority = prio,
                                                iconResId = icon,
                                                page = rf.page,
                                                snippet = rf.snippet,
                                                source = rf.source ?: "AI Vault",
                                                uriString = rf.uri_string,
                                                filePath = rf.file_path,
                                                fileUrl = rf.file_url,
                                                category = rf.category
                                            )
                                        }

                                        val localFallback = if (mappedFiles.isEmpty()) {
                                            try {
                                                val allLocal = LocalFileManager.getDeviceFiles(context)
                                                val queryTokens = userQuery.lowercase()
                                                    .split(Regex("[\\s_\\-,.]+"))
                                                    .filter { it.length >= 3 && !setOf("what", "when", "where", "which", "about", "show", "tell", "does", "have", "this", "that", "file", "document", "with", "from").contains(it) }

                                                allLocal.filter { f ->
                                                    val titleLower = f.title.lowercase()
                                                    queryTokens.isNotEmpty() && queryTokens.any { titleLower.contains(it) }
                                                }.take(5).map { item ->
                                                    AiFileResult(
                                                        id = item.id.toInt(),
                                                        title = item.title,
                                                        format = item.format,
                                                        size = item.sizeFormatted,
                                                        dateText = item.dateText,
                                                        priority = item.priority,
                                                        iconResId = getFormatIcon(item.format),
                                                        source = "Device Files",
                                                        uriString = item.uri.toString(),
                                                        filePath = item.path,
                                                        category = null
                                                    )
                                                }
                                            } catch (_: Exception) {
                                                emptyList<AiFileResult>()
                                            }
                                        } else {
                                            emptyList()
                                        }

                                        val finalFiles = if (mappedFiles.isNotEmpty()) mappedFiles else localFallback
                                        val finalReply = if (mappedFiles.isEmpty() && localFallback.isNotEmpty()) {
                                            "I found **${localFallback.size} matching file(s)** on your mobile device for '**$userQuery**':"
                                        } else {
                                            replyText
                                        }

                                        chatMessages.add(
                                            ChatMessage.Ai(
                                                text = finalReply,
                                                files = finalFiles
                                            )
                                        )
                                    }.onFailure { err ->
                                        chatMessages.add(
                                            ChatMessage.Ai(
                                                text = "Could not reach the AI service: ${err.localizedMessage}\nPlease make sure the Python AI microservice is running."
                                            )
                                        )
                                    }
                                }
                            }
                        }
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
    onNewChatClick: () -> Unit = {}
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
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            if (hasActiveConversation) {
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
            text = "Find your files with natural language",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(14.dp))

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
    }
}

@Composable
fun AiMessageBubble(
    text: String,
    files: List<AiFileResult>,
    isLoading: Boolean = false,
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
                .background(MaterialTheme.colorScheme.surfaceVariant)
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
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = text,
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                    )
                }
            } else {
                Text(
                    text = text,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Medium
                )
            }
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
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onOpenClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = file.iconResId),
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .height(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 18.dp, vertical = 6.dp),
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
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (isThinking) Color.Gray else MaterialTheme.colorScheme.primary)
                .clickable(enabled = !isThinking) { onSendClick() },
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
