package com.simats.intellifile.data.chat

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simats.intellifile.data.repository.AiRepository
import com.simats.intellifile.ui.ai.AiFileResult
import com.simats.intellifile.ui.ai.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class ChatRepository(private val context: Context) {

    private val db = ChatDatabase.getInstance(context)
    private val chatDao = db.chatDao()
    private val messageDao = db.messageDao()
    private val aiRepository = AiRepository(context)
    private val gson = Gson()

    /**
     * Restores the last active conversation or the most recently updated conversation.
     * Returns null if no previous conversation exists in local storage.
     */
    suspend fun getActiveOrLatestConversation(): ChatEntity? = withContext(Dispatchers.IO) {
        val active = chatDao.activeChat
        if (active != null) return@withContext active
        val all = chatDao.allChats
        all.firstOrNull()
    }

    /**
     * Retrieves all chats ordered by last updated timestamp.
     */
    suspend fun getAllChats(): List<ChatEntity> = withContext(Dispatchers.IO) {
        chatDao.allChats
    }

    /**
     * Retrieves a single chat by its ID.
     */
    suspend fun getChatById(conversationId: String): ChatEntity? = withContext(Dispatchers.IO) {
        chatDao.getChatById(conversationId)
    }

    /**
     * Marks a conversation as active and deactivates all other conversations.
     */
    suspend fun setActiveConversation(conversationId: String) = withContext(Dispatchers.IO) {
        chatDao.deactivateAllChats()
        chatDao.setActiveChat(conversationId)
    }

    /**
     * Creates a new conversation record in Room without overwriting previous chats.
     */
    suspend fun createNewConversation(
        conversationId: String = "conv_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
        title: String = "New Chat"
    ): ChatEntity = withContext(Dispatchers.IO) {
        chatDao.deactivateAllChats()
        val now = System.currentTimeMillis()
        val newChat = ChatEntity(
            conversationId,
            title,
            now,
            now,
            true
        )
        chatDao.insertChat(newChat)
        newChat
    }

    /**
     * Loads all messages for a conversation from Room in ascending order of timestamp.
     */
    suspend fun getMessagesForConversation(conversationId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        val entities = messageDao.getMessagesForConversation(conversationId)
        entities.map { entity ->
            if (entity.senderType.equals("USER", ignoreCase = true)) {
                ChatMessage.User(entity.messageText)
            } else {
                val files = parseFilesJson(entity.filesJson)
                ChatMessage.Ai(
                    text = entity.messageText,
                    files = files,
                    isLoading = false
                )
            }
        }
    }

    /**
     * Saves a user message to Room. Automatically updates conversation title and timestamp.
     */
    suspend fun saveUserMessage(
        conversationId: String,
        text: String
    ): MessageEntity = withContext(Dispatchers.IO) {
        // Ensure conversation exists
        var chat = chatDao.getChatById(conversationId)
        val now = System.currentTimeMillis()
        if (chat == null) {
            val derivedTitle = text.take(35).trim()
            chat = ChatEntity(conversationId, derivedTitle.ifBlank { "New Chat" }, now, now, true)
            chatDao.deactivateAllChats()
            chatDao.insertChat(chat)
        } else {
            // Update title if it is still default
            if (chat.title == "New Chat" && text.isNotBlank()) {
                val derivedTitle = text.take(35).trim()
                chatDao.updateChatTitle(conversationId, derivedTitle, now)
            } else {
                chatDao.updateChatTimestamp(conversationId, now)
            }
        }

        val msgId = "msg_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val messageEntity = MessageEntity(
            msgId,
            conversationId,
            text,
            "USER",
            now,
            null
        )
        messageDao.insertMessage(messageEntity)
        messageEntity
    }

    /**
     * Saves an AI response message to Room, including attached file results.
     */
    suspend fun saveAiMessage(
        conversationId: String,
        text: String,
        files: List<AiFileResult> = emptyList()
    ): MessageEntity = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        chatDao.updateChatTimestamp(conversationId, now)

        val filesJson = if (files.isNotEmpty()) {
            try {
                gson.toJson(files)
            } catch (_: Exception) {
                null
            }
        } else null

        val msgId = "msg_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val messageEntity = MessageEntity(
            msgId,
            conversationId,
            text,
            "AI",
            now,
            filesJson
        )
        messageDao.insertMessage(messageEntity)
        messageEntity
    }

    /**
     * Updates an existing message's text (e.g. after edit).
     */
    suspend fun updateMessage(messageId: String, newText: String) = withContext(Dispatchers.IO) {
        messageDao.updateMessageText(messageId, newText)
    }

    /**
     * Deletes a conversation and its messages.
     */
    suspend fun deleteConversation(conversationId: String) = withContext(Dispatchers.IO) {
        messageDao.deleteMessagesForConversation(conversationId)
        chatDao.deleteChatById(conversationId)
        // Also notify backend if numeric ID
        conversationId.toIntOrNull()?.let { numId ->
            try {
                aiRepository.clearChatHistory(numId)
            } catch (_: Exception) {}
        }
    }

    /**
     * Deletes all conversations and messages from local database.
     */
    suspend fun clearAllConversations() = withContext(Dispatchers.IO) {
        messageDao.deleteAllMessages()
        chatDao.deleteAllChats()
        try {
            aiRepository.clearChatHistory(null)
        } catch (_: Exception) {}
    }

    /**
     * Syncs remote conversations into local Room DB so remote history is also available offline.
     */
    suspend fun syncRemoteHistory() = withContext(Dispatchers.IO) {
        try {
            val result = aiRepository.getChatHistory()
            result.onSuccess { remoteList ->
                remoteList.forEach { remote ->
                    val idStr = remote.id.toString()
                    val existing = chatDao.getChatById(idStr)
                    if (existing == null) {
                        val now = System.currentTimeMillis()
                        val newEntity = ChatEntity(
                            idStr,
                            remote.title.ifBlank { "Conversation $idStr" },
                            now,
                            now,
                            false
                        )
                        chatDao.insertChat(newEntity)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun parseFilesJson(json: String?): List<AiFileResult> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<AiFileResult>>() {}.type
            gson.fromJson<List<AiFileResult>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
