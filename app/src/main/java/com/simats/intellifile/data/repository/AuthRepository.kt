package com.simats.intellifile.data.repository

import android.content.Context
import com.simats.intellifile.data.api.IntelliFileApiService
import com.simats.intellifile.data.api.RetrofitClient
import com.simats.intellifile.data.model.*
import com.simats.intellifile.data.session.SessionManager
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class AuthRepository(private val context: Context) {
    private val api: IntelliFileApiService
        get() = RetrofitClient.getApiService(context)
    val sessionManager = SessionManager(context)

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

    suspend fun getProfile(): Result<UserDto> {
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
                val resolvedImg = com.simats.intellifile.data.api.NetworkConfig.resolveMediaUrl(user.profile_image)
                user = user.copy(
                    full_name = resolvedName,
                    email = resolvedEmail,
                    profile_image = resolvedImg
                )
                sessionManager.updateProfile(user.full_name, user.email, user.profile_image)
                Result.success(user)
            } else {
                val errorMsg = response.body()?.message ?: "Failed to fetch profile"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun editProfile(name: String, email: String): Result<UserDto> {
        return try {
            val response = api.editProfile(EditProfileRequest(name.trim(), email.trim(), user_id = sessionManager.getUserId()))
            if (response.isSuccessful && response.body()?.success == true) {
                val user = response.body()?.user ?: response.body()?.profile ?: UserDto(
                    id = sessionManager.getUserId(),
                    full_name = name,
                    email = email
                )
                sessionManager.updateProfile(user.full_name, user.email, user.profile_image)
                Result.success(user)
            } else {
                val errorMsg = response.body()?.message ?: "Failed to update profile"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadProfileImage(imageFile: File): Result<String> {
        return try {
            val requestFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("profile_image", imageFile.name, requestFile)
            val response = api.uploadProfileImage(part)
            if (response.isSuccessful && response.body()?.success == true) {
                val url = response.body()?.profile_image_url ?: response.body()?.profile_image ?: ""
                sessionManager.updateProfile(
                    name = sessionManager.getFullName(),
                    email = sessionManager.getEmail(),
                    profileImage = url
                )
                Result.success(url)
            } else {
                val errorMsg = response.body()?.message ?: "Failed to upload image"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeProfilePhoto(): Result<Boolean> {
        return try {
            val response = api.removeProfilePhoto()
            if (response.isSuccessful && response.body()?.success == true) {
                sessionManager.updateProfile(
                    name = sessionManager.getFullName(),
                    email = sessionManager.getEmail(),
                    profileImage = null
                )
                Result.success(true)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Failed to remove photo"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSettings(): Result<UserSettingsDto> {
        return try {
            val response = api.getSettings()
            if (response.isSuccessful && response.body()?.success == true) {
                val settings = response.body()?.settings ?: UserSettingsDto()
                Result.success(settings)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Failed to load settings"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateSettings(settings: UserSettingsDto): Result<Boolean> {
        return try {
            val request = UpdateSettingsRequest(
                theme_mode = settings.theme_mode,
                important_alerts = if (settings.important_alerts) 1 else 0,
                deadline_reminders = if (settings.deadline_reminders) 1 else 0,
                new_doc_alerts = if (settings.new_doc_alerts) 1 else 0,
                quiet_hours = if (settings.quiet_hours) 1 else 0
            )
            val response = api.updateSettings(request)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(true)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Failed to update settings"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout(): Result<Boolean> {
        return try {
            api.logout()
            sessionManager.clearSession()
            RetrofitClient.resetClient()
            Result.success(true)
        } catch (e: Exception) {
            sessionManager.clearSession()
            RetrofitClient.resetClient()
            Result.success(true)
        }
    }
}
