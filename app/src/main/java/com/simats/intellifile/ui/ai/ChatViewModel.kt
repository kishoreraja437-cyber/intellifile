package com.simats.intellifile.ui.ai

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simats.intellifile.R
import com.simats.intellifile.data.chat.ChatEntity
import com.simats.intellifile.data.chat.ChatRepository
import com.simats.intellifile.data.manager.LocalFileManager
import com.simats.intellifile.data.local.LocalFileDatabaseHelper
import com.simats.intellifile.data.model.LocalFileItem
import com.simats.intellifile.data.repository.AiRepository
import com.simats.intellifile.data.repository.DocumentRepository
import com.simats.intellifile.ui.home.PriorityLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ChatUiState(
    val currentConversationId: String? = null,
    val conversationTitle: String = "IntelliFile AI",
    val messages: List<ChatMessage> = emptyList(),
    val isThinking: Boolean = false,
    val isLoadingConversation: Boolean = false,
    val promptInput: String = "",
    val editingMessageIndex: Int? = null,
    val editingMessageText: String = "",
    val isEditingUserMessage: Boolean = false,
    val historyItems: List<ChatHistoryItem> = emptyList(),
    val isLoadingHistory: Boolean = false,
    val errorMessage: String? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val chatRepository = ChatRepository(application)
    private val aiRepository = AiRepository(application)

    private val defaultGreeting = ChatMessage.Ai(
        text = "I am IntelliFile AI and I help to find the files",
        files = emptyList()
    )

    private val _uiState = MutableStateFlow(
        ChatUiState(
            messages = listOf(defaultGreeting)
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _priorityUpdateVersion = MutableStateFlow(0)
    val priorityUpdateVersion: StateFlow<Int> = _priorityUpdateVersion.asStateFlow()

    fun notifyPriorityUpdated() {
        _priorityUpdateVersion.update { it + 1 }
    }

    private var pendingDisambiguation: Pair<List<LocalFileItem>, PriorityLevel>? = null

    fun assignPriorityToFile(file: AiFileResult, targetPriority: PriorityLevel) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val dbHelper = LocalFileDatabaseHelper.getInstance(context)

            withContext(Dispatchers.IO) {
                if (!file.filePath.isNullOrBlank() && file.filePath != "Internal Storage") {
                    dbHelper.setPriority(file.filePath, file.title, targetPriority)
                }
                if (!file.uriString.isNullOrBlank()) {
                    dbHelper.setPriority(file.uriString, file.title, targetPriority)
                }
                dbHelper.setPriority(file.title, file.title, targetPriority)

                val docId = file.id
                if (docId != null && docId > 0) {
                    try {
                        DocumentRepository(context).setPriority(docId, targetPriority.name)
                    } catch (_: Exception) {}
                }
            }

            notifyPriorityUpdated()
            pendingDisambiguation = null

            val updatedCard = file.copy(
                priority = targetPriority,
                pendingPriorityAction = null
            )

            val confirmationMsg = "I have updated the priority of '**${file.title}**' to **${targetPriority.title}** as requested.\n\nYou can view and filter this file under the ${targetPriority.title} priority section on your Home Screen, My Files, and Saved screen."

            val convId = _uiState.value.currentConversationId ?: "conv_${System.currentTimeMillis()}"
            val title = _uiState.value.conversationTitle

            chatRepository.ensureChatExists(convId, title)
            chatRepository.saveAiMessage(convId, confirmationMsg, listOf(updatedCard))

            _uiState.update { state ->
                state.copy(
                    currentConversationId = convId,
                    messages = state.messages + ChatMessage.Ai(
                        text = confirmationMsg,
                        files = listOf(updatedCard)
                    ),
                    isThinking = false
                )
            }
        }
    }

    fun assignPriorityToLocalFile(item: LocalFileItem, targetPriority: PriorityLevel) {
        val card = AiFileResult(
            id = if (item.id > 0) item.id.toInt() else null,
            title = item.title,
            format = item.format,
            size = item.sizeFormatted,
            dateText = item.dateText,
            priority = targetPriority,
            iconResId = getFormatIcon(item.format),
            source = "Device Files",
            uriString = item.uri.toString(),
            filePath = item.path,
            snippet = if (item.path.isNotBlank() && item.path != "Internal Storage") item.path else "Device Files",
            pendingPriorityAction = null
        )
        assignPriorityToFile(card, targetPriority)
    }

    init {
        startFreshChatOnLaunch()
    }

    /**
     * Requirement 1: New Chat When Reopening the App (ChatGPT-style)
     * When user completely exits and opens the app again, automatically start a fresh new chat.
     * Do NOT load previous messages into the active chat screen.
     * All previous conversations remain safely stored in Chat History.
     */
    fun startFreshChatOnLaunch() {
        _uiState.update {
            it.copy(
                currentConversationId = null,
                conversationTitle = "IntelliFile AI",
                messages = listOf(defaultGreeting),
                isThinking = false,
                isLoadingConversation = false,
                promptInput = ""
            )
        }
    }

    private fun generateConversationTitle(prompt: String): String {
        val clean = prompt.trim()
        val pattern = Regex("^(?:can\\s+you\\s+)?(?:please\\s+)?(?:search\\s+for|search|find|look\\s+for|show\\s+me|open|where\\s+is|what\\s+is\\s+in|check)\\s+", RegexOption.IGNORE_CASE)
        val stripped = pattern.replace(clean, "").trim()
        val rawTitle = if (stripped.isNotBlank()) stripped else clean
        val trimmed = rawTitle.take(45).trim().trim('"', '\'')
        if (trimmed.isNotBlank()) {
            return trimmed.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
        return "New Chat"
    }

    /**
     * Requirement 1: Chat History Restoration
     * Loads the exact selected conversation from Room, restoring all user messages,
     * AI responses, order, timestamps, title, and conversation context.
     * Does NOT create a new conversation.
     */
    fun loadConversation(conversationId: String) {
        if (conversationId.isBlank()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingConversation = true,
                    currentConversationId = conversationId,
                    messages = emptyList()
                )
            }
            chatRepository.setActiveConversation(conversationId)

            val chat = chatRepository.getChatById(conversationId)
            val storedMessages = chatRepository.getMessagesForConversation(conversationId)

            // Step 1: If Room DB already has messages, display them immediately
            if (storedMessages.isNotEmpty()) {
                val sanitizedMessages = storedMessages.map { msg ->
                    if (msg is ChatMessage.Ai) {
                        val fixedFiles = msg.files.map { f ->
                            f.copy(iconResId = getFormatIcon(f.format))
                        }
                        msg.copy(files = fixedFiles)
                    } else {
                        msg
                    }
                }
                _uiState.update {
                    it.copy(
                        currentConversationId = conversationId,
                        conversationTitle = chat?.title?.ifBlank { null } ?: "IntelliFile AI",
                        messages = sanitizedMessages,
                        isLoadingConversation = false,
                        promptInput = ""
                    )
                }
                return@launch
            }

            // Step 2: If empty locally, fetch from backend via aiRepository
            val numId = conversationId.toIntOrNull()
            if (numId != null) {
                val remoteRes = aiRepository.getConversationDetails(numId)
                remoteRes.onSuccess { details ->
                    val remoteMsgs = details.messages
                    val remoteConv = details.conversation
                    val title = remoteConv?.title?.ifBlank { null } ?: chat?.title?.ifBlank { null } ?: "Chat #$numId"

                    // Ensure conversation exists in Room
                    chatRepository.ensureChatExists(conversationId, title)

                    // Map remote messages into ChatMessage models
                    val parsed = remoteMsgs.map { rm ->
                        if (rm.sender.equals("USER", ignoreCase = true)) {
                            ChatMessage.User(rm.text)
                        } else {
                            val mappedFiles = (rm.files ?: rm.related_files ?: emptyList()).map { rf ->
                                val prio = when (rf.priority?.uppercase()) {
                                    "IMPORTANT" -> PriorityLevel.IMPORTANT
                                    "NORMAL" -> PriorityLevel.NORMAL
                                    "LOW" -> PriorityLevel.LOW
                                    else -> null
                                }
                                AiFileResult(
                                    id = rf.id,
                                    title = rf.title,
                                    format = rf.format ?: "PDF",
                                    size = rf.size ?: "1.0 MB",
                                    dateText = rf.dateText ?: "Recent",
                                    priority = prio,
                                    iconResId = getFormatIcon(rf.format),
                                    page = rf.page,
                                    snippet = rf.snippet,
                                    source = rf.source ?: "IntelliSpace",
                                    uriString = rf.uri_string,
                                    filePath = rf.file_path,
                                    fileUrl = rf.file_url,
                                    category = rf.category
                                )
                            }
                            ChatMessage.Ai(
                                text = rm.text,
                                files = mappedFiles,
                                isLoading = false
                            )
                        }
                    }

                    // Immediately present all restored messages on the AI Chat screen
                    _uiState.update {
                        it.copy(
                            currentConversationId = conversationId,
                            conversationTitle = title,
                            messages = if (parsed.isNotEmpty()) parsed else listOf(ChatMessage.Ai(text = "This conversation has no messages yet.")),
                            isLoadingConversation = false,
                            promptInput = ""
                        )
                    }

                    // Persist batch into Room DB asynchronously with preserved chronological ordering
                    chatRepository.saveMessagesBatch(conversationId, remoteMsgs)
                    return@launch
                }.onFailure { err ->
                    android.util.Log.e("ChatViewModel", "Error restoring remote conversation: ${err.message}")
                }
            }

            // Fallback if both Room and remote have no messages
            _uiState.update {
                it.copy(
                    currentConversationId = conversationId,
                    conversationTitle = chat?.title?.ifBlank { null } ?: "IntelliFile AI",
                    messages = listOf(ChatMessage.Ai(text = "This conversation has no messages yet.")),
                    isLoadingConversation = false,
                    promptInput = ""
                )
            }
        }
    }

    fun updateMessageAt(index: Int, newText: String) {
        val current = _uiState.value.messages.toMutableList()
        if (index in current.indices) {
            val old = current[index]
            val updated = when (old) {
                is ChatMessage.User -> ChatMessage.User(newText)
                is ChatMessage.Ai -> old.copy(text = newText)
            }
            current[index] = updated
            _uiState.update { it.copy(messages = current) }

            val convId = _uiState.value.currentConversationId ?: return
            viewModelScope.launch(Dispatchers.IO) {
                val entities = chatRepository.getMessageEntitiesForConversation(convId)
                if (index in entities.indices) {
                    chatRepository.updateMessage(entities[index].messageId, newText)
                }
            }
        }
    }

    /**
     * Requirement 2: Dedicated New Chat Button
     * Creates a new conversation ONLY when explicitly triggered.
     * Generates a unique conversation ID, clears current messages, and saves independently
     * without deleting or overwriting previous conversations.
     */
    fun startNewChat() {
        _uiState.update {
            it.copy(
                currentConversationId = null,
                conversationTitle = "IntelliFile AI",
                messages = listOf(defaultGreeting),
                isThinking = false,
                isLoadingConversation = false,
                promptInput = ""
            )
        }
    }

    fun onPromptInputChange(input: String) {
        _uiState.update { it.copy(promptInput = input) }
    }

    /**
     * Sends a user message, records it persistently in Room, triggers search/AI,
     * and saves the AI response persistently in Room.
     */
    fun sendMessage(userPrompt: String) {
        val trimmed = userPrompt.trim()
        if (trimmed.isBlank() || _uiState.value.isThinking) return

        val activeDisambiguation = pendingDisambiguation
        if (activeDisambiguation != null && !trimmed.startsWith("mark", ignoreCase = true) && !trimmed.startsWith("set", ignoreCase = true) && !trimmed.startsWith("change", ignoreCase = true)) {
            val (candidates, targetPriority) = activeDisambiguation
            val lower = trimmed.lowercase().trim()
            val matchedIndex = when {
                lower in listOf("1", "first", "1st", "the first one", "the first", "option 1", "#1") -> 0
                lower in listOf("2", "second", "2nd", "the second one", "the second", "option 2", "#2") -> 1
                lower in listOf("3", "third", "3rd", "the third one", "the third", "option 3", "#3") -> 2
                lower in listOf("4", "fourth", "4th", "the fourth one", "the fourth", "option 4", "#4") -> 3
                lower in listOf("5", "fifth", "5th", "the fifth one", "the fifth", "option 5", "#5") -> 4
                else -> {
                    val num = lower.toIntOrNull()
                    if (num != null && num in 1..candidates.size) {
                        num - 1
                    } else {
                        candidates.indexOfFirst { candidate ->
                            val pathLower = candidate.path.lowercase()
                            pathLower.contains(lower) || lower.split(Regex("\\s+")).any { it.length >= 3 && pathLower.contains(it) }
                        }
                    }
                }
            }

            if (matchedIndex in candidates.indices) {
                pendingDisambiguation = null
                val chosenItem = candidates[matchedIndex]
                val userMsg = ChatMessage.User(trimmed)
                _uiState.update { state ->
                    state.copy(
                        promptInput = "",
                        messages = state.messages.filterNot { it == defaultGreeting } + userMsg,
                        isThinking = false
                    )
                }
                val existingConvId = _uiState.value.currentConversationId ?: "conv_${System.currentTimeMillis()}"
                viewModelScope.launch {
                    chatRepository.ensureChatExists(existingConvId, _uiState.value.conversationTitle)
                    chatRepository.saveUserMessage(existingConvId, trimmed)
                    assignPriorityToLocalFile(chosenItem, targetPriority)
                }
                return
            }
        }

        val existingConvId = _uiState.value.currentConversationId
        val isNewConversation = existingConvId == null
        val generatedTitle = if (isNewConversation) generateConversationTitle(trimmed) else _uiState.value.conversationTitle

        val userMsg = ChatMessage.User(trimmed)
        val loadingMsg = ChatMessage.Ai(
            text = "Searching files and analyzing context...",
            isLoading = true
        )

        _uiState.update { state ->
            state.copy(
                promptInput = "",
                conversationTitle = generatedTitle,
                messages = state.messages.filterNot { it == defaultGreeting } + userMsg + loadingMsg,
                isThinking = true
            )
        }

        viewModelScope.launch {

            val intent = AiChatIntentDetector.detect(trimmed)
            val context = getApplication<Application>().applicationContext

            when (intent) {
                is AiUserIntent.FileSearch,
                is AiUserIntent.FileTypeFilter,
                is AiUserIntent.FileOpen -> {
                    val searchKeyword = when (intent) {
                        is AiUserIntent.FileSearch -> intent.query
                        is AiUserIntent.FileOpen -> intent.targetName
                        is AiUserIntent.FileTypeFilter -> intent.format
                        else -> trimmed
                    }
                    val formatFilter = when (intent) {
                        is AiUserIntent.FileSearch -> intent.format
                        is AiUserIntent.FileTypeFilter -> intent.format
                        else -> null
                    }

                    // A. Local Device Files Search
                    val localDeferred = async(Dispatchers.IO) {
                        try {
                            val allLocal = LocalFileManager.getDeviceFiles(context)
                            val searchTokens = searchKeyword.lowercase()
                                .split(Regex("[\\s_\\-,.]+"))
                                .filter { it.length >= 2 && !setOf("the", "my", "all", "file", "files", "document", "documents", "notes", "for", "with", "about").contains(it) }

                            allLocal.filter { f ->
                                val matchesFormat = when {
                                    formatFilter == null -> true
                                    formatFilter.equals("PPT", ignoreCase = true) || formatFilter.equals("PPTX", ignoreCase = true) ->
                                        f.format.equals("PPT", ignoreCase = true) || f.format.equals("PPTX", ignoreCase = true)
                                    formatFilter.equals("DOC", ignoreCase = true) || formatFilter.equals("DOCX", ignoreCase = true) ->
                                        f.format.equals("DOC", ignoreCase = true) || f.format.equals("DOCX", ignoreCase = true)
                                    formatFilter.equals("XLS", ignoreCase = true) || formatFilter.equals("XLSX", ignoreCase = true) ->
                                        f.format.equals("XLS", ignoreCase = true) || f.format.equals("XLSX", ignoreCase = true)
                                    else -> f.format.equals(formatFilter, ignoreCase = true)
                                }
                                if (!matchesFormat) return@filter false

                                if (intent is AiUserIntent.FileTypeFilter) return@filter true

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

                                if (f.sizeBytes in 1..500_000L && setOf("TXT", "CSV", "JSON", "MD", "DOCX", "PPTX", "PPT").contains(f.format.uppercase())) {
                                    try {
                                        val textContent = LocalFileManager.readTextContent(context, f.uri.toString(), f.path, f.format, 10000)
                                        if (textContent.contains(searchKeyword, ignoreCase = true)) return@filter true
                                        if (searchTokens.isNotEmpty() && searchTokens.all { textContent.contains(it, ignoreCase = true) }) return@filter true
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
                        } catch (_: Exception) {
                            emptyList<AiFileResult>()
                        }
                    }

                    // B. IntelliSpace Search via Backend
                    val vaultDeferred = async(Dispatchers.IO) {
                        aiRepository.sendChatMessage(
                            message = trimmed,
                            conversationId = existingConvId?.toIntOrNull()
                        )
                    }

                    val localMatches = localDeferred.await()
                    val vaultResult = vaultDeferred.await()

                    val vaultMatches = mutableListOf<AiFileResult>()
                    var backendExplanation: String? = null

                    vaultResult.onSuccess { response ->
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
                            AiFileResult(
                                id = rf.id,
                                title = rf.title,
                                format = rf.format ?: "PDF",
                                size = rf.size ?: "1.0 MB",
                                dateText = rf.dateText ?: "Recent",
                                priority = prio,
                                iconResId = getFormatIcon(rf.format),
                                page = rf.page,
                                snippet = rf.snippet,
                                source = rf.source ?: "IntelliSpace",
                                uriString = rf.uri_string,
                                filePath = rf.file_path,
                                fileUrl = rf.file_url,
                                category = rf.category
                            )
                        }
                        vaultMatches.addAll(mapped)
                    }

                    val allMatches = mutableListOf<AiFileResult>()
                    allMatches.addAll(localMatches)
                    for (vf in vaultMatches) {
                        if (allMatches.none { it.title.equals(vf.title, ignoreCase = true) }) {
                            allMatches.add(vf)
                        }
                    }

                    val replyText = if (allMatches.isEmpty()) {
                        "No matching files found for '$searchKeyword'. Try another filename or keyword."
                    } else {
                        when (intent) {
                            is AiUserIntent.FileOpen -> {
                                if (allMatches.size == 1) {
                                    "I found '**${allMatches.first().title}**'. Tap below to view."
                                } else {
                                    "I found **${allMatches.size} files** matching '**$searchKeyword**'. Tap a file card below to open it:"
                                }
                            }
                            is AiUserIntent.FileTypeFilter -> {
                                val fmt = formatFilter ?: "matching"
                                "Here are your **${allMatches.size} $fmt file${if (allMatches.size > 1) "s" else ""}** found across Device Files and IntelliSpace:"
                            }
                            else -> {
                                if (vaultMatches.isNotEmpty() && !backendExplanation.isNullOrBlank() && !backendExplanation!!.contains("could not find information", ignoreCase = true) && !backendExplanation!!.contains("No matching files", ignoreCase = true)) {
                                    "I found **${allMatches.size} matching file${if (allMatches.size > 1) "s" else ""}** for '**$searchKeyword**':\n\n$backendExplanation"
                                } else {
                                    "I found **${allMatches.size} matching file${if (allMatches.size > 1) "s" else ""}** for '**$searchKeyword**' across Device Files and IntelliSpace:"
                                }
                            }
                        }
                    }

                    var resolvedConvId = existingConvId
                    var finalTitle = generatedTitle

                    vaultResult.onSuccess { response ->
                        if (response.conversation_id > 0) {
                            resolvedConvId = response.conversation_id.toString()
                        }
                        if (!response.conversation_title.isNullOrBlank()) {
                            finalTitle = response.conversation_title
                        }
                    }

                    val finalConvId = resolvedConvId ?: "conv_${System.currentTimeMillis()}"

                    // Ensure conversation exists in Room with title
                    chatRepository.ensureChatExists(finalConvId, finalTitle)
                    // Save user message to Room
                    chatRepository.saveUserMessage(finalConvId, trimmed)
                    // Save AI response to Room
                    chatRepository.saveAiMessage(finalConvId, replyText, allMatches)

                    _uiState.update { state ->
                        state.copy(
                            currentConversationId = finalConvId,
                            conversationTitle = finalTitle,
                            messages = state.messages.filterNot { it == loadingMsg } + ChatMessage.Ai(replyText, allMatches),
                            isThinking = false
                        )
                    }
                }
                is AiUserIntent.SetFilePriority -> {
                    val requestedFileName = intent.fileName.trim()
                    val targetPriority = intent.priority

                    val allDeviceFiles = withContext(Dispatchers.IO) {
                        try {
                            LocalFileManager.getDeviceFiles(context)
                        } catch (_: Exception) {
                            emptyList<LocalFileItem>()
                        }
                    }

                    // 1. Search for matching file in device storage (exact title match first)
                    val exactMatches = allDeviceFiles.filter { f ->
                        f.title.equals(requestedFileName, ignoreCase = true) ||
                        f.title.substringBeforeLast('.').equals(requestedFileName, ignoreCase = true) ||
                        f.title.substringBeforeLast('.').equals(requestedFileName.substringBeforeLast('.'), ignoreCase = true)
                    }

                    val matchingFiles = if (exactMatches.isNotEmpty()) {
                        exactMatches
                    } else {
                        allDeviceFiles.filter { f ->
                            f.title.contains(requestedFileName, ignoreCase = true) ||
                            f.title.substringBeforeLast('.').contains(requestedFileName, ignoreCase = true)
                        }
                    }

                    if (matchingFiles.isEmpty()) {
                        pendingDisambiguation = null
                        val errorText = "I couldn't find any file named '**$requestedFileName**' in your device storage.\n\nPlease check the file name and verify that storage access is granted, or make sure the file is saved in your device storage."

                        val finalConvId = existingConvId ?: "conv_${System.currentTimeMillis()}"
                        val finalTitle = generatedTitle
                        chatRepository.ensureChatExists(finalConvId, finalTitle)
                        chatRepository.saveUserMessage(finalConvId, trimmed)
                        chatRepository.saveAiMessage(finalConvId, errorText, emptyList())

                        _uiState.update { state ->
                            state.copy(
                                currentConversationId = finalConvId,
                                conversationTitle = finalTitle,
                                messages = state.messages.filterNot { it == loadingMsg } + ChatMessage.Ai(errorText, emptyList()),
                                isThinking = false
                            )
                        }
                    } else if (matchingFiles.size == 1) {
                        pendingDisambiguation = null
                        val targetFile = matchingFiles.first()

                        withContext(Dispatchers.IO) {
                            val dbHelper = LocalFileDatabaseHelper.getInstance(context)
                            if (targetFile.path.isNotBlank() && targetFile.path != "Internal Storage") {
                                dbHelper.setPriority(targetFile.path, targetFile.title, targetPriority)
                            }
                            dbHelper.setPriority(targetFile.uri.toString(), targetFile.title, targetPriority)
                            dbHelper.setPriority(targetFile.title, targetFile.title, targetPriority)

                            if (targetFile.vaultDocId != null && targetFile.vaultDocId > 0) {
                                try {
                                    DocumentRepository(context).setPriority(targetFile.vaultDocId, targetPriority.name)
                                } catch (_: Exception) {}
                            }
                        }

                        notifyPriorityUpdated()

                        val updatedCard = AiFileResult(
                            id = if (targetFile.id > 0) targetFile.id.toInt() else null,
                            title = targetFile.title,
                            format = targetFile.format,
                            size = targetFile.sizeFormatted,
                            dateText = targetFile.dateText,
                            priority = targetPriority,
                            iconResId = getFormatIcon(targetFile.format),
                            source = "Device Files",
                            uriString = targetFile.uri.toString(),
                            filePath = targetFile.path,
                            snippet = if (targetFile.path.isNotBlank() && targetFile.path != "Internal Storage") targetFile.path else "Device Files",
                            pendingPriorityAction = null
                        )

                        val confirmationText = "I have marked '**${targetFile.title}**' as **${targetPriority.title}** priority.\n\nThe priority has been updated in your database and is immediately reflected on your Home Screen, My Files, and Saved screen."

                        val finalConvId = existingConvId ?: "conv_${System.currentTimeMillis()}"
                        val finalTitle = generatedTitle
                        chatRepository.ensureChatExists(finalConvId, finalTitle)
                        chatRepository.saveUserMessage(finalConvId, trimmed)
                        chatRepository.saveAiMessage(finalConvId, confirmationText, listOf(updatedCard))

                        _uiState.update { state ->
                            state.copy(
                                currentConversationId = finalConvId,
                                conversationTitle = finalTitle,
                                messages = state.messages.filterNot { it == loadingMsg } + ChatMessage.Ai(confirmationText, listOf(updatedCard)),
                                isThinking = false
                            )
                        }
                    } else {
                        pendingDisambiguation = Pair(matchingFiles, targetPriority)

                        val candidateCards = matchingFiles.mapIndexed { idx, item ->
                            val locationText = if (item.path.isNotBlank() && item.path != "Internal Storage") item.path else item.uri.toString()
                            AiFileResult(
                                id = if (item.id > 0) item.id.toInt() else null,
                                title = item.title,
                                format = item.format,
                                size = item.sizeFormatted,
                                dateText = item.dateText,
                                priority = item.priority,
                                iconResId = getFormatIcon(item.format),
                                source = "Device Files",
                                uriString = item.uri.toString(),
                                filePath = item.path,
                                snippet = "Option ${idx + 1}: $locationText",
                                pendingPriorityAction = targetPriority
                            )
                        }

                        val disambiguationPrompt = "I found **${matchingFiles.size} files** named '**$requestedFileName**'. Please select which file you want to mark as **${targetPriority.title}** by tapping **Select** below, or reply with the option number:"

                        val finalConvId = existingConvId ?: "conv_${System.currentTimeMillis()}"
                        val finalTitle = generatedTitle
                        chatRepository.ensureChatExists(finalConvId, finalTitle)
                        chatRepository.saveUserMessage(finalConvId, trimmed)
                        chatRepository.saveAiMessage(finalConvId, disambiguationPrompt, candidateCards)

                        _uiState.update { state ->
                            state.copy(
                                currentConversationId = finalConvId,
                                conversationTitle = finalTitle,
                                messages = state.messages.filterNot { it == loadingMsg } + ChatMessage.Ai(disambiguationPrompt, candidateCards),
                                isThinking = false
                            )
                        }
                    }
                }
                else -> {
                    // General query
                    val result = aiRepository.sendChatMessage(
                        message = trimmed,
                        conversationId = existingConvId?.toIntOrNull()
                    )

                    var resolvedConvId = existingConvId
                    var finalTitle = generatedTitle

                    result.onSuccess { response ->
                        if (response.conversation_id > 0) {
                            resolvedConvId = response.conversation_id.toString()
                        }
                        if (!response.conversation_title.isNullOrBlank()) {
                            finalTitle = response.conversation_title
                        }
                    }

                    val replyText = result.getOrNull()?.let { response ->
                        response.message?.text ?: response.reply ?: "I analyzed your query."
                    } ?: "I'm working in local mode right now. You can ask me to search, filter, or open your files anytime!"

                    val rawFiles = result.getOrNull()?.let { response ->
                        response.message?.files
                            ?: response.message?.related_files
                            ?: response.related_files
                            ?: emptyList()
                    } ?: emptyList()

                    val mappedFiles = rawFiles.map { rf ->
                        val prio = when (rf.priority?.uppercase()) {
                            "IMPORTANT" -> PriorityLevel.IMPORTANT
                            "LOW" -> PriorityLevel.LOW
                            else -> PriorityLevel.NORMAL
                        }
                        AiFileResult(
                            id = rf.id,
                            title = rf.title,
                            format = rf.format ?: "PDF",
                            size = rf.size ?: "1.0 MB",
                            dateText = rf.dateText ?: "Recent",
                            priority = prio,
                            iconResId = getFormatIcon(rf.format),
                            page = rf.page,
                            snippet = rf.snippet,
                            source = rf.source ?: "IntelliSpace",
                            uriString = rf.uri_string,
                            filePath = rf.file_path,
                            fileUrl = rf.file_url,
                            category = rf.category
                        )
                    }

                    val finalConvId = resolvedConvId ?: "conv_${System.currentTimeMillis()}"

                    // Ensure conversation exists in Room with title
                    chatRepository.ensureChatExists(finalConvId, finalTitle)
                    // Save user message to Room
                    chatRepository.saveUserMessage(finalConvId, trimmed)
                    // Save AI response to Room
                    chatRepository.saveAiMessage(finalConvId, replyText, mappedFiles)

                    _uiState.update { state ->
                        state.copy(
                            currentConversationId = finalConvId,
                            conversationTitle = finalTitle,
                            messages = state.messages.filterNot { it == loadingMsg } + ChatMessage.Ai(replyText, mappedFiles),
                            isThinking = false
                        )
                    }
                }
            }
        }
    }

    /**
     * Requirement 1 & 7: Loads chat history from Room DB (and syncs with remote).
     */
    fun loadChatHistory(category: String = "ALL", searchQuery: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingHistory = true) }
            chatRepository.syncRemoteHistory()

            val allChats = chatRepository.getAllChats()
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

            val items = allChats.map { chat ->
                val lastMsg = chatRepository.getMessagesForConversation(chat.conversationId).lastOrNull()
                val snippet = when (lastMsg) {
                    is ChatMessage.User -> lastMsg.text
                    is ChatMessage.Ai -> lastMsg.text
                    else -> "No messages yet"
                }

                val dateText = if (chat.updatedAt > 0) dateFormat.format(Date(chat.updatedAt)) else "Recent"
                val timeText = if (chat.updatedAt > 0) timeFormat.format(Date(chat.updatedAt)) else ""

                val cat = when {
                    snippet.contains("search", ignoreCase = true) || snippet.contains("find", ignoreCase = true) -> HistoryCategory.SEARCH
                    snippet.contains("analy", ignoreCase = true) || snippet.contains("summar", ignoreCase = true) -> HistoryCategory.ANALYSIS
                    else -> HistoryCategory.GENERAL
                }

                ChatHistoryItem(
                    id = chat.conversationId,
                    title = chat.title.ifBlank { "Conversation" },
                    snippet = snippet.take(80),
                    dateText = dateText,
                    timeText = timeText,
                    category = cat,
                    icon = when (cat) {
                        HistoryCategory.ANALYSIS -> Icons.Outlined.Description
                        HistoryCategory.SEARCH -> Icons.Default.Search
                        else -> Icons.Outlined.ChatBubbleOutline
                    }
                )
            }.filter { item ->
                val matchesCategory = category == "ALL" || item.category.name.equals(category, ignoreCase = true)
                val matchesSearch = searchQuery.isBlank() ||
                        item.title.contains(searchQuery, ignoreCase = true) ||
                        item.snippet.contains(searchQuery, ignoreCase = true)
                matchesCategory && matchesSearch
            }

            _uiState.update {
                it.copy(
                    historyItems = items,
                    isLoadingHistory = false
                )
            }
        }
    }

    fun deleteChat(conversationId: String) {
        viewModelScope.launch {
            chatRepository.deleteConversation(conversationId)
            if (_uiState.value.currentConversationId == conversationId) {
                startNewChat()
            }
            loadChatHistory()
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            chatRepository.clearAllConversations()
            startNewChat()
            loadChatHistory()
        }
    }

    fun editMessage(index: Int, newText: String) {
        if (index !in _uiState.value.messages.indices || newText.isBlank()) return
        val currentMessages = _uiState.value.messages.toMutableList()
        val oldMsg = currentMessages[index]
        currentMessages[index] = when (oldMsg) {
            is ChatMessage.User -> ChatMessage.User(newText)
            is ChatMessage.Ai -> oldMsg.copy(text = newText)
        }
        _uiState.update { it.copy(messages = currentMessages) }
    }

    private fun getFormatIcon(format: String?): Int {
        return when (format?.trim()?.removePrefix(".")?.uppercase()) {
            "DOCX", "DOC" -> R.drawable.doc
            "PPT", "PPTX" -> R.drawable.ppt
            else -> R.drawable.pdf
        }
    }
}
