package com.simats.intellifile.data.api

import com.simats.intellifile.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface IntelliFileApiService {

    // --- Authentication & Profile ---

    @POST("auth/login.php")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<AuthResponse>

    @POST("auth/signup.php")
    suspend fun signup(
        @Body request: SignUpRequest
    ): Response<AuthResponse>

    @GET("auth/profile.php")
    suspend fun getProfile(
        @Query("user_id") userId: Int? = null,
        @Query("email") email: String? = null
    ): Response<AuthResponse>

    @POST("auth/edit_profile.php")
    suspend fun editProfile(
        @Body request: EditProfileRequest
    ): Response<AuthResponse>

    @Multipart
    @POST("auth/upload_profile.php")
    suspend fun uploadProfileImage(
        @Part profileImage: MultipartBody.Part
    ): Response<GenericResponse>

    @POST("auth/remove_profile_photo.php")
    suspend fun removeProfilePhoto(): Response<GenericResponse>

    @POST("auth/change_password.php")
    suspend fun changePassword(
        @Body request: ChangePasswordRequest
    ): Response<GenericResponse>

    @POST("auth/forgotpassword.php")
    suspend fun forgotPassword(
        @Body request: ForgotPasswordRequest
    ): Response<GenericResponse>

    @GET("auth/get_settings.php")
    suspend fun getSettings(
        @Query("user_id") userId: Int? = null,
        @Query("email") email: String? = null
    ): Response<SettingsResponse>

    @POST("auth/update_settings.php")
    suspend fun updateSettings(
        @Body request: UpdateSettingsRequest
    ): Response<GenericResponse>

    @POST("auth/logout.php")
    suspend fun logout(): Response<GenericResponse>

    // --- Documents & AI Vault ---

    @Multipart
    @POST("documents/upload.php")
    suspend fun uploadDocument(
        @Part document: MultipartBody.Part,
        @Part("category") category: RequestBody? = null,
        @Part("priority") priority: RequestBody? = null,
        @Part("title") title: RequestBody? = null,
        @Part("user_id") userId: RequestBody? = null
    ): Response<DocumentUploadResponse>

    @GET("documents/list.php")
    suspend fun getDocuments(
        @Query("category") category: String? = null,
        @Query("format") format: String? = null,
        @Query("priority") priority: String? = null,
        @Query("search") search: String? = null
    ): Response<DocumentListResponse>

    @GET("documents/detail.php")
    suspend fun getDocumentDetail(
        @Query("id") documentId: Int
    ): Response<DocumentDetailResponse>

    @GET("documents/recent.php")
    suspend fun getRecentDocuments(
        @Query("limit") limit: Int = 10
    ): Response<DocumentListResponse>

    @GET("documents/saved.php")
    suspend fun getSavedDocuments(
        @Query("priority") priority: String = "ALL"
    ): Response<DocumentListResponse>

    @POST("documents/set_priority.php")
    suspend fun setPriority(
        @Body request: SetPriorityRequest
    ): Response<GenericResponse>

    @POST("documents/bookmark.php")
    suspend fun bookmarkDocument(
        @Body request: BookmarkRequest
    ): Response<GenericResponse>

    @POST("documents/delete.php")
    suspend fun deleteDocument(
        @Body request: DeleteDocumentRequest
    ): Response<GenericResponse>

    @GET("documents/statistics.php")
    suspend fun getStatistics(): Response<StatisticsResponse>

    @POST("documents/share.php")
    suspend fun shareDocument(
        @Body request: ShareDocumentRequest
    ): Response<GenericResponse>

    // --- AI, Search & Chat ---

    @POST("ai/chat.php")
    suspend fun sendChatMessage(
        @Body request: AiChatRequest
    ): Response<AiChatResponse>

    @GET("ai/history.php")
    suspend fun getChatHistory(
        @Query("category") category: String = "ALL"
    ): Response<AiHistoryResponse>

    @POST("ai/clear_history.php")
    suspend fun clearChatHistory(): Response<GenericResponse>

    @GET("ai/search.php")
    suspend fun searchDocuments(
        @Query("query") query: String
    ): Response<DocumentListResponse>

    @POST("ai/summarize.php")
    suspend fun summarizeDocument(
        @Body request: AiSummarizeRequest
    ): Response<AiSummarizeResponse>

    @POST("ai/predict.php")
    suspend fun predictCategoryAndPriority(
        @Body request: AiPredictRequest
    ): Response<AiPredictResponse>

    // --- Support & Feedback ---

    @POST("support/contact.php")
    suspend fun submitContact(
        @Body request: ContactRequest
    ): Response<GenericResponse>

    @POST("support/feedback.php")
    suspend fun submitFeedback(
        @Body request: FeedbackRequest
    ): Response<GenericResponse>
}
