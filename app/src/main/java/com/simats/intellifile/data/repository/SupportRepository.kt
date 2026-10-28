package com.simats.intellifile.data.repository

import android.content.Context
import com.simats.intellifile.data.api.RetrofitClient
import com.simats.intellifile.data.model.ContactRequest
import com.simats.intellifile.data.model.FeedbackRequest

class SupportRepository(private val context: Context) {
    private val api = RetrofitClient.getApiService(context)

    suspend fun submitContactMessage(
        name: String,
        email: String,
        subject: String,
        message: String
    ): Result<String> {
        return try {
            val response = api.submitContact(ContactRequest(name.trim(), email.trim(), subject.trim(), message.trim()))
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()?.message ?: "Message sent successfully")
            } else {
                Result.failure(Exception(response.body()?.message ?: "Failed to submit message"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitFeedback(
        rating: Int,
        category: String,
        feedbackText: String,
        email: String? = null
    ): Result<String> {
        return try {
            val response = api.submitFeedback(FeedbackRequest(rating, category, feedbackText.trim(), email?.trim()))
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()?.message ?: "Feedback submitted successfully")
            } else {
                Result.failure(Exception(response.body()?.message ?: "Failed to submit feedback"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
