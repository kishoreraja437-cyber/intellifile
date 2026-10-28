package com.simats.intellifile.data.repository

import android.content.Context
import com.simats.intellifile.data.api.RetrofitClient
import com.simats.intellifile.data.model.*

class AiRepository(private val context: Context) {
    private val api = RetrofitClient.getApiService(context)

    suspend fun sendChatMessage(
        message: String,
        conversationId: Int? = null,
        category: String? = null
    ): Result<AiChatResponse> {
        return try {
            val response = api.sendChatMessage(AiChatRequest(message.trim(), conversationId, category))
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = response.body()?.reply ?: response.errorBody()?.string() ?: "AI failed to respond"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChatHistory(category: String = "ALL"): Result<List<AiConversationDto>> {
        return try {
            val response = api.getChatHistory(category)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()?.history ?: emptyList())
            } else {
                Result.failure(Exception(response.body()?.message ?: "Failed to fetch AI chat history"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearChatHistory(): Result<Boolean> {
        return try {
            val response = api.clearChatHistory()
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(true)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Failed to clear history"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchDocuments(query: String): Result<List<DocumentDto>> {
        return try {
            val response = api.searchDocuments(query.trim())
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()?.documents ?: emptyList())
            } else {
                Result.failure(Exception(response.body()?.message ?: "Search failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun summarizeDocument(
        documentId: Int? = null,
        title: String? = null,
        contentText: String? = null
    ): Result<AiSummarizeResponse> {
        return try {
            val response = api.summarizeDocument(AiSummarizeRequest(documentId, title, contentText))
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!)
            } else {
                val msg = response.body()?.message ?: response.errorBody()?.string() ?: "Failed to summarize"
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun predictCategoryAndPriority(
        title: String,
        contentText: String
    ): Result<AiPredictResponse> {
        return try {
            val response = api.predictCategoryAndPriority(AiPredictRequest(title, contentText))
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Prediction failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
