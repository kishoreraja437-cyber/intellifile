package com.simats.intellifile.data.repository

import android.content.Context
import com.simats.intellifile.data.api.IntelliFileApiService
import com.simats.intellifile.data.api.NetworkConfig
import com.simats.intellifile.data.api.RetrofitClient
import com.simats.intellifile.data.model.*
import com.simats.intellifile.data.session.SessionManager
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class AuthRepository(private val context: Context) {
    private val api: IntelliFileApiService
        get() = RetrofitClient.getApiService(context)
    val sessionManager = SessionManager(context)

    // In-memory cache & request deduplication for Profile
    @Volatile
    private var inMemoryUser: UserDto? = null
    private val profileMutex = Mutex()
    private var activeProfileJob: Deferred<Result<UserDto>>? = null

    // In-memory cache & request deduplication for Settings
    @Volatile
    private var inMemorySettings: UserSettingsDto? = null
    private val settingsMutex = Mutex()
    private var activeSettingsJob: Deferred<Result<UserSettingsDto>>? = null

    fun hasCachedProfile(): Boolean {
        return (inMemoryUser != null && sessionManager.hasCachedProfile()) || sessionManager.hasCachedProfile()
    }

    fun getLocalProfile(): UserDto? {
        return inMemoryUser ?: sessionManager.getCachedProfile()
    }

    suspend fun login(email: String, password: String): Result<UserDto> {
        return try {
            val trimmedEmail = email.trim()
            val response = api.login(LoginRequest(trimmedEmail, password))
            if (response.isSuccessful && response.body()?.success == true) {
                val body = response.body()!!
                val token = body.token ?: ""
                var user = body.user ?: body.profile ?: return Result.failure(Exception("No user profile returned"))
                val resolvedEmail = if (!user.email.isNullOrBlank()) user.email.trim() else trimmedEmail
                val resolvedImg = com.simats.intellifile.data.api.NetworkConfig.resolveMediaUrl(user.profile_image)
                user = user.copy(
                    email = resolvedEmail,
                    profile_image = resolvedImg
                )
                sessionManager.saveUserSession(token, user)
                inMemoryUser = null // Reset so first Profile screen visit calls Profile API as required
                RetrofitClient.resetClient()
                Result.success(user)
            } else {
                val errorMsg = extractErrorMessage(response.body()?.message, response.errorBody()?.string(), "Login failed")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signup(fullName: String, email: String, password: String): Result<UserDto> {
        return try {
            val trimmedEmail = email.trim()
            val response = api.signup(SignUpRequest(fullName.trim(), trimmedEmail, password))
            if (response.isSuccessful && response.body()?.success == true) {
                val body = response.body()!!
                val token = body.token ?: ""
                var user = body.user ?: body.profile ?: return Result.failure(Exception("No user profile returned"))
                val resolvedEmail = if (!user.email.isNullOrBlank()) user.email.trim() else trimmedEmail
                val resolvedImg = com.simats.intellifile.data.api.NetworkConfig.resolveMediaUrl(user.profile_image)
                user = user.copy(
                    email = resolvedEmail,
                    profile_image = resolvedImg
                )
                sessionManager.saveUserSession(token, user)
                inMemoryUser = null
                RetrofitClient.resetClient()
                Result.success(user)
            } else {
                val errorMsg = extractErrorMessage(response.body()?.message, response.errorBody()?.string(), "Sign up failed")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun forgotPassword(email: String, newPassword: String, confirmPassword: String = newPassword): Result<String> {
        return try {
            val response = api.forgotPassword(
                ForgotPasswordRequest(
                    email = email.trim(),
                    new_password = newPassword,
                    confirm_password = confirmPassword
                )
            )
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()?.message ?: "Password reset successful")
            } else {
                val errorMsg = extractErrorMessage(response.body()?.message, response.errorBody()?.string(), "Password reset failed")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractErrorMessage(bodyMessage: String?, errorBodyStr: String?, fallback: String): String {
        if (!bodyMessage.isNullOrBlank()) return bodyMessage
        if (!errorBodyStr.isNullOrBlank()) {
            try {
                val json = org.json.JSONObject(errorBodyStr)
                if (json.has("message") && !json.isNull("message")) {
                    val m = json.getString("message")
                    if (m.isNotBlank()) return m
                }
            } catch (_: Exception) {}
        }
        return fallback
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<String> {
        return try {
            val response = api.changePassword(ChangePasswordRequest(currentPassword, newPassword))
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()?.message ?: "Password changed successfully")
            } else {
                val errorMsg = response.body()?.message ?: response.errorBody()?.string() ?: "Password change failed"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Retrieves profile data with proper caching strategy and request deduplication.
     * - If forceRefresh is false and local cache is available: loads from cache immediately (0 API calls).
     * - If forceRefresh is true or no local cache exists: executes single deduplicated API request.
     * - On API failure: falls back to previously cached local data.
     */
    suspend fun getProfile(forceRefresh: Boolean = false): Result<UserDto> {
        // 1. Fast-path: return cached profile immediately if available and not forcing refresh
        if (!forceRefresh) {
            inMemoryUser?.let { return Result.success(it) }
            val localUser = sessionManager.getCachedProfile()
            if (localUser != null && sessionManager.hasCachedProfile()) {
                inMemoryUser = localUser
                return Result.success(localUser)
            }
        }

        // 2. Request deduplication: check if another request is already in-flight
        var ongoingJob: Deferred<Result<UserDto>>? = null
        var isInitiator = false

        profileMutex.withLock {
            if (!forceRefresh) {
                inMemoryUser?.let { return Result.success(it) }
                val localUser = sessionManager.getCachedProfile()
                if (localUser != null && sessionManager.hasCachedProfile()) {
                    inMemoryUser = localUser
                    return Result.success(localUser)
                }
            }

            if (activeProfileJob != null && activeProfileJob!!.isActive) {
                ongoingJob = activeProfileJob
                isInitiator = false
            } else {
                ongoingJob = null
                isInitiator = true
            }
        }

        if (!isInitiator && ongoingJob != null) {
            return ongoingJob.await()
        }

        // 3. Initiate single network request
        return coroutineScope {
            val deferred = async {
                executeGetProfileNetworkCall()
            }
            profileMutex.withLock {
                activeProfileJob = deferred
            }
            try {
                deferred.await()
            } finally {
                profileMutex.withLock {
                    if (activeProfileJob == deferred) {
                        activeProfileJob = null
                    }
                }
            }
        }
    }

    private suspend fun executeGetProfileNetworkCall(): Result<UserDto> {
        return try {
            val currentUserId = sessionManager.getUserId()
            val currentEmail = sessionManager.getEmail()
            val response = api.getProfile(
                userId = if (currentUserId > 0) currentUserId else null,
                email = currentEmail.ifBlank { null }
            )
            if (response.isSuccessful && response.body()?.success == true) {
                var user = response.body()?.user ?: response.body()?.profile ?: return Result.failure(Exception("User not found"))
                val resolvedEmail = if (!user.email.isNullOrBlank()) user.email.trim() else currentEmail
                val resolvedName = if (!user.full_name.isNullOrBlank()) user.full_name.trim() else sessionManager.getFullName()
                val resolvedImg = NetworkConfig.resolveMediaUrl(user.profile_image) ?: sessionManager.getProfileImage()
                user = user.copy(
                    full_name = resolvedName,
                    email = resolvedEmail,
                    profile_image = resolvedImg
                )
                sessionManager.saveCachedProfile(user)
                inMemoryUser = user
                Result.success(user)
            } else {
                val fallback = inMemoryUser ?: sessionManager.getCachedProfile()
                if (fallback != null) {
                    Result.success(fallback)
                } else {
                    val errorMsg = response.body()?.message ?: "Failed to fetch profile"
                    Result.failure(Exception(errorMsg))
                }
            }
        } catch (e: Exception) {
            val fallback = inMemoryUser ?: sessionManager.getCachedProfile()
            if (fallback != null) {
                Result.success(fallback)
            } else {
                Result.failure(e)
            }
        }
    }

    /**
     * Updates the user's profile.
     * Updates the local cache immediately (optimistic update) and synchronizes with the backend.
     */
    suspend fun editProfile(name: String, email: String): Result<UserDto> {
        val trimmedName = name.trim()
        val trimmedEmail = email.trim()

        // 1. Immediate local cache update
        val existing = inMemoryUser ?: sessionManager.getCachedProfile()
        val optimisticUser = (existing ?: UserDto(
            id = sessionManager.getUserId(),
            full_name = trimmedName,
            email = trimmedEmail
        )).copy(
            full_name = trimmedName,
            email = trimmedEmail
        )
        sessionManager.saveCachedProfile(optimisticUser)
        inMemoryUser = optimisticUser

        // 2. Synchronize with backend API
        return try {
            val response = api.editProfile(EditProfileRequest(trimmedName, trimmedEmail, user_id = sessionManager.getUserId()))
            if (response.isSuccessful && response.body()?.success == true) {
                val serverUser = response.body()?.user ?: response.body()?.profile ?: optimisticUser
                val finalUser = serverUser.copy(
                    full_name = if (!serverUser.full_name.isNullOrBlank()) serverUser.full_name.trim() else trimmedName,
                    email = if (!serverUser.email.isNullOrBlank()) serverUser.email.trim() else trimmedEmail,
                    profile_image = com.simats.intellifile.data.api.NetworkConfig.resolveMediaUrl(serverUser.profile_image) ?: optimisticUser.profile_image
                )
                sessionManager.saveCachedProfile(finalUser)
                inMemoryUser = finalUser
                Result.success(finalUser)
            } else {
                val errorMsg = response.body()?.message ?: "Failed to update profile"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            // Keep local changes active even if network was unavailable
            Result.success(optimisticUser)
        }
    }

    /**
     * Uploads a new profile image.
     * On success, updates the local cache immediately.
     */
    suspend fun uploadProfileImage(imageFile: File): Result<String> {
        return try {
            val requestFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("profile_image", imageFile.name, requestFile)
            val response = api.uploadProfileImage(part)
            if (response.isSuccessful && response.body()?.success == true) {
                val url = response.body()?.profile_image_url ?: response.body()?.profile_image ?: ""
                val resolvedUrl = com.simats.intellifile.data.api.NetworkConfig.resolveMediaUrl(url)
                val existing = inMemoryUser ?: sessionManager.getCachedProfile() ?: UserDto(
                    id = sessionManager.getUserId(),
                    full_name = sessionManager.getFullName(),
                    email = sessionManager.getEmail()
                )
                val updated = existing.copy(profile_image = resolvedUrl ?: url)
                sessionManager.saveCachedProfile(updated)
                inMemoryUser = updated
                Result.success(resolvedUrl ?: url)
            } else {
                val errorMsg = response.body()?.message ?: "Failed to upload image"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Removes the profile photo.
     * Updates the local cache immediately and synchronizes with the backend.
     */
    suspend fun removeProfilePhoto(): Result<Boolean> {
        // Immediate local cache update
        val existing = inMemoryUser ?: sessionManager.getCachedProfile()
        if (existing != null) {
            val updated = existing.copy(profile_image = null, profile_image_path = null)
            sessionManager.saveCachedProfile(updated)
            inMemoryUser = updated
        } else {
            sessionManager.updateProfile(sessionManager.getFullName(), sessionManager.getEmail(), null)
        }

        return try {
            val response = api.removeProfilePhoto()
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(true)
            } else {
                Result.success(true) // local photo already removed
            }
        } catch (e: Exception) {
            Result.success(true) // local photo already removed
        }
    }

    /**
     * Retrieves settings with caching and deduplication.
     */
    suspend fun getSettings(forceRefresh: Boolean = false): Result<UserSettingsDto> {
        if (!forceRefresh) {
            inMemorySettings?.let { return Result.success(it) }
            if (sessionManager.hasCachedSettings()) {
                val local = sessionManager.getNotificationSettings()
                inMemorySettings = local
                return Result.success(local)
            }
        }

        var ongoingJob: Deferred<Result<UserSettingsDto>>? = null
        var isInitiator = false

        settingsMutex.withLock {
            if (!forceRefresh) {
                inMemorySettings?.let { return Result.success(it) }
                if (sessionManager.hasCachedSettings()) {
                    val local = sessionManager.getNotificationSettings()
                    inMemorySettings = local
                    return Result.success(local)
                }
            }
            if (activeSettingsJob != null && activeSettingsJob!!.isActive) {
                ongoingJob = activeSettingsJob
                isInitiator = false
            } else {
                ongoingJob = null
                isInitiator = true
            }
        }

        if (!isInitiator && ongoingJob != null) {
            return ongoingJob.await()
        }

        return coroutineScope {
            val deferred = async {
                executeGetSettingsNetworkCall()
            }
            settingsMutex.withLock {
                activeSettingsJob = deferred
            }
            try {
                deferred.await()
            } finally {
                settingsMutex.withLock {
                    if (activeSettingsJob == deferred) {
                        activeSettingsJob = null
                    }
                }
            }
        }
    }

    private suspend fun executeGetSettingsNetworkCall(): Result<UserSettingsDto> {
        return try {
            val currentUserId = sessionManager.getUserId()
            val currentEmail = sessionManager.getEmail()
            val response = api.getSettings(
                userId = if (currentUserId > 0) currentUserId else null,
                email = currentEmail.ifBlank { null }
            )
            if (response.isSuccessful && response.body()?.success == true) {
                val settings = response.body()?.settings ?: UserSettingsDto()
                sessionManager.saveNotificationSettings(
                    importantAlerts = settings.important_alerts,
                    deadlineReminders = settings.deadline_reminders,
                    newDocAlerts = settings.new_doc_alerts,
                    quietHours = settings.quiet_hours
                )
                inMemorySettings = settings
                Result.success(settings)
            } else {
                val localSettings = sessionManager.getNotificationSettings()
                inMemorySettings = localSettings
                Result.success(localSettings)
            }
        } catch (e: Exception) {
            val localSettings = sessionManager.getNotificationSettings()
            inMemorySettings = localSettings
            Result.success(localSettings)
        }
    }

    suspend fun updateSettings(settings: UserSettingsDto): Result<Boolean> {
        return try {
            inMemorySettings = settings
            sessionManager.saveNotificationSettings(
                importantAlerts = settings.important_alerts,
                deadlineReminders = settings.deadline_reminders,
                newDocAlerts = settings.new_doc_alerts,
                quietHours = settings.quiet_hours
            )
            val currentUserId = sessionManager.getUserId()
            val currentEmail = sessionManager.getEmail()
            val request = UpdateSettingsRequest(
                theme_mode = settings.theme_mode,
                important_alerts = if (settings.important_alerts) 1 else 0,
                deadline_reminders = if (settings.deadline_reminders) 1 else 0,
                new_doc_alerts = if (settings.new_doc_alerts) 1 else 0,
                quiet_hours = if (settings.quiet_hours) 1 else 0,
                user_id = if (currentUserId > 0) currentUserId else null,
                email = currentEmail.ifBlank { null }
            )
            val response = api.updateSettings(request)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(true)
            } else {
                Result.success(true) // local changes saved
            }
        } catch (e: Exception) {
            Result.success(true) // local changes saved
        }
    }

    /**
     * Clears all cached profile, settings, and session data on logout.
     */
    
    suspend fun updateFcmToken(token: String, deviceName: String = "Android Device"): Result<Boolean> {
        return try {
            sessionManager.saveFcmToken(token)
            val currentUserId = sessionManager.getUserId()
            val currentEmail = sessionManager.getEmail()
            val request = UpdateFcmTokenRequest(
                fcm_token = token,
                device_name = deviceName,
                user_id = if (currentUserId > 0) currentUserId else null,
                email = currentEmail.ifBlank { null }
            )
            val response = api.updateFcmToken(request)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(true)
            } else {
                Result.success(true)
            }
        } catch (e: Exception) {
            Result.success(true)
        }
    }

    suspend fun logout(): Result<Boolean> {
        return try {
            api.logout()
            inMemoryUser = null
            inMemorySettings = null
            sessionManager.clearSession()
            RetrofitClient.resetClient()
            Result.success(true)
        } catch (e: Exception) {
            inMemoryUser = null
            inMemorySettings = null
            sessionManager.clearSession()
            RetrofitClient.resetClient()
            Result.success(true)
        }
    }
}
