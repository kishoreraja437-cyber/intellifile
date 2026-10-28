package com.simats.intellifile.data.model

import com.google.gson.annotations.SerializedName

// --- User & Auth ---

data class UserDto(
    @SerializedName("id") val id: Int,
    @SerializedName("full_name") val full_name: String,
    @SerializedName("email") val email: String,
    @SerializedName("profile_image") val profile_image: String? = null,
    @SerializedName("profile_image_path") val profile_image_path: String? = null,
    @SerializedName("created_at") val created_at: String? = null
)

data class AuthResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("token") val token: String? = null,
    @SerializedName("user") val user: UserDto? = null
)

data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class SignUpRequest(
    @SerializedName("full_name") val full_name: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class ForgotPasswordRequest(
    @SerializedName("email") val email: String,
    @SerializedName("new_password") val new_password: String
)

data class ChangePasswordRequest(
    @SerializedName("current_password") val current_password: String,
    @SerializedName("new_password") val new_password: String
)

data class EditProfileRequest(
    @SerializedName("full_name") val full_name: String,
    @SerializedName("email") val email: String
)

data class UserSettingsDto(
    @SerializedName("theme_mode") val theme_mode: String = "SYSTEM",
    @SerializedName("important_alerts") val important_alerts: Boolean = true,
    @SerializedName("deadline_reminders") val deadline_reminders: Boolean = true,
    @SerializedName("new_doc_alerts") val new_doc_alerts: Boolean = true,
    @SerializedName("quiet_hours") val quiet_hours: Boolean = false
)

data class SettingsResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("settings") val settings: UserSettingsDto? = null
)

data class UpdateSettingsRequest(
    @SerializedName("theme_mode") val theme_mode: String,
    @SerializedName("important_alerts") val important_alerts: Int,
    @SerializedName("deadline_reminders") val deadline_reminders: Int,
    @SerializedName("new_doc_alerts") val new_doc_alerts: Int,
    @SerializedName("quiet_hours") val quiet_hours: Int
)

// --- Documents ---

data class DocumentDto(
    @SerializedName("id") val id: Int,
    @SerializedName("user_id") val user_id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("format") val format: String = "PDF",
    @SerializedName("file_size") val file_size: String = "1.0 MB",
    @SerializedName("file_path") val file_path: String? = null,
    @SerializedName("file_url") val file_url: String? = null,
    @SerializedName("category") val category: String = "General",
    @SerializedName("location") val location: String = "Internal Storage",
    @SerializedName("priority") val priority: String = "NORMAL",
    @SerializedName("is_bookmarked") val is_bookmarked: Boolean = false,
    @SerializedName("is_saved") val is_saved: Boolean = false,
    @SerializedName("total_pages") val total_pages: Int = 1,
    @SerializedName("content_text") val content_text: String? = null,
    @SerializedName("content_text_length") val content_text_length: Int = 0,
    @SerializedName("has_text") val has_text: Boolean = false,
    @SerializedName("created_at") val created_at: String? = null,
    @SerializedName("last_accessed") val last_accessed: String? = null
)

data class DocumentListResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("count") val count: Int = 0,
    @SerializedName("documents") val documents: List<DocumentDto> = emptyList(),
    @SerializedName("message") val message: String? = null
)

data class DocumentDetailResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("document") val document: DocumentDto? = null,
    @SerializedName("message") val message: String? = null
)

data class DocumentUploadResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("document") val document: DocumentDto? = null
)

data class PrioritySummaryItem(
    @SerializedName("title") val title: String? = null,
    @SerializedName("count") val count: Int = 0,
    @SerializedName("percentage") val percentage: Float = 0f,
    @SerializedName("count_text") val count_text: String? = null
)

data class PrioritySummary(
    @SerializedName("important") val important: PrioritySummaryItem? = null,
    @SerializedName("normal") val normal: PrioritySummaryItem? = null,
    @SerializedName("low") val low: PrioritySummaryItem? = null
)

data class StatisticsDto(
    @SerializedName("total_files") val total_files: Int = 0,
    @SerializedName("saved_files") val saved_files: Int = 0,
    @SerializedName("priority_summary") val priority_summary: PrioritySummary? = null,
    @SerializedName("formats") val formats: Map<String, Int> = emptyMap()
)

data class StatisticsResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("statistics") val statistics: StatisticsDto? = null,
    @SerializedName("message") val message: String? = null
)

data class SetPriorityRequest(
    @SerializedName("document_id") val document_id: Int,
    @SerializedName("priority") val priority: String
)

data class BookmarkRequest(
    @SerializedName("document_id") val document_id: Int,
    @SerializedName("is_bookmarked") val is_bookmarked: Int
)

data class DeleteDocumentRequest(
    @SerializedName("document_id") val document_id: Int
)

data class ShareDocumentRequest(
    @SerializedName("document_id") val document_id: Int,
    @SerializedName("recipient_email") val recipient_email: String,
    @SerializedName("permission") val permission: String = "View Only"
)

// --- AI & Chat ---

data class AiChatRequest(
    @SerializedName("message") val message: String,
    @SerializedName("conversation_id") val conversation_id: Int? = null,
    @SerializedName("category") val category: String? = null
)

data class RelatedFileDto(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("title") val title: String,
    @SerializedName("format") val format: String? = null,
    @SerializedName("size") val size: String? = null,
    @SerializedName("priority") val priority: String? = null
)

data class AiChatMessageDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("sender") val sender: String,
    @SerializedName("text") val text: String,
    @SerializedName("time") val time: String? = null,
    @SerializedName("related_files") val related_files: List<RelatedFileDto>? = null
)

data class AiChatResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("conversation_id") val conversation_id: Int = 0,
    @SerializedName("category") val category: String? = null,
    @SerializedName("message") val message: AiChatMessageDto? = null,
    @SerializedName("reply") val reply: String? = null,
    @SerializedName("related_files") val related_files: List<RelatedFileDto>? = null
)

data class AiConversationDto(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("snippet") val snippet: String = "",
    @SerializedName("category") val category: String = "GENERAL",
    @SerializedName("dateText") val dateText: String? = null,
    @SerializedName("timeText") val timeText: String? = null,
    @SerializedName("created_at") val created_at: String? = null
)

data class AiHistoryResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("count") val count: Int = 0,
    @SerializedName("history") val history: List<AiConversationDto> = emptyList(),
    @SerializedName("message") val message: String? = null
)

data class AiSummarizeRequest(
    @SerializedName("document_id") val document_id: Int? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("content_text") val content_text: String? = null
)

data class AiSummarizeResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("title") val title: String? = null,
    @SerializedName("summary") val summary: String? = null,
    @SerializedName("bullet_points") val bullet_points: List<String> = emptyList(),
    @SerializedName("sentence_count") val sentence_count: Int = 0,
    @SerializedName("message") val message: String? = null
)

data class AiPredictRequest(
    @SerializedName("title") val title: String,
    @SerializedName("content_text") val content_text: String
)

data class AiPredictResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("category") val category: String = "General",
    @SerializedName("priority") val priority: String = "NORMAL",
    @SerializedName("confidence") val confidence: Float = 0f,
    @SerializedName("message") val message: String? = null
)

// --- Support & Feedback ---

data class FeedbackRequest(
    @SerializedName("rating") val rating: Int,
    @SerializedName("category") val category: String,
    @SerializedName("feedback_text") val feedback_text: String,
    @SerializedName("email") val email: String? = null
)

data class ContactRequest(
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String,
    @SerializedName("subject") val subject: String,
    @SerializedName("message") val message: String
)

data class GenericResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("share_url") val share_url: String? = null,
    @SerializedName("profile_image_url") val profile_image_url: String? = null
)
