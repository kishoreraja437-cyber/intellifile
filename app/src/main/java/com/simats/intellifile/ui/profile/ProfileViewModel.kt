package com.simats.intellifile.ui.profile

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simats.intellifile.data.model.UserDto
import com.simats.intellifile.data.model.UserSettingsDto
import com.simats.intellifile.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

data class ProfileUiState(
    val isLoading: Boolean = false,
    val user: UserDto? = null,
    val settings: UserSettingsDto = UserSettingsDto(),
    val errorMessage: String? = null,
    val statusMessage: String? = null
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    val authRepo = AuthRepository(application)
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        // 1. Immediately load local cached profile and settings (0 network requests)
        val initialUser = authRepo.getLocalProfile()
        val initialSettings = authRepo.sessionManager.getNotificationSettings()
        _uiState.value = _uiState.value.copy(
            user = initialUser,
            settings = initialSettings,
            isLoading = false
        )

        // 2. Only fetch from API if profile is NOT cached locally yet (first profile opening)
        if (!authRepo.hasCachedProfile()) {
            loadProfile(forceRefresh = false)
        }
        if (!authRepo.sessionManager.hasCachedSettings()) {
            loadSettings(forceRefresh = false)
        }
    }

    fun getEmail(): String = _uiState.value.user?.email?.ifBlank { authRepo.sessionManager.getEmail() } ?: authRepo.sessionManager.getEmail()
    fun getFullName(): String = _uiState.value.user?.full_name?.ifBlank { authRepo.sessionManager.getFullName() } ?: authRepo.sessionManager.getFullName()
    fun getProfileImage(): String? = _uiState.value.user?.profile_image ?: authRepo.sessionManager.getProfileImage()

    /**
     * Loads profile data following the caching strategy:
     * - If forceRefresh is false and local cache exists: loads from cache immediately (0 network calls).
     * - If forceRefresh is true or no local cache exists: fetches from backend API and saves locally.
     * - If network call fails: falls back to previously cached data so UI is never empty.
     */
    fun loadProfile(forceRefresh: Boolean = false) {
        // Fast-path: if profile is already cached locally and not forcing refresh, do not make an API request
        if (!forceRefresh && authRepo.hasCachedProfile()) {
            val localUser = _uiState.value.user ?: authRepo.getLocalProfile()
            if (localUser != null) {
                _uiState.value = _uiState.value.copy(
                    user = localUser,
                    isLoading = false
                )
                return
            }
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = authRepo.getProfile(forceRefresh = forceRefresh)
            result.onSuccess { user ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    user = user,
                    errorMessage = null
                )
            }.onFailure { ex ->
                val fallbackUser = _uiState.value.user ?: authRepo.getLocalProfile()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    user = fallbackUser,
                    errorMessage = if (fallbackUser == null) (ex.localizedMessage ?: "Failed to fetch profile") else null
                )
            }
        }
    }

    /**
     * Explicit user-triggered refresh.
     */
    fun refreshProfile() {
        loadProfile(forceRefresh = true)
        loadSettings(forceRefresh = true)
    }

    fun loadSettings(forceRefresh: Boolean = false) {
        if (!forceRefresh && authRepo.sessionManager.hasCachedSettings()) {
            _uiState.value = _uiState.value.copy(settings = authRepo.sessionManager.getNotificationSettings())
            return
        }
        viewModelScope.launch {
            val result = authRepo.getSettings(forceRefresh = forceRefresh)
            result.onSuccess { settings ->
                _uiState.value = _uiState.value.copy(settings = settings)
            }
        }
    }

    fun editProfile(name: String, email: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepo.editProfile(name, email)
            result.onSuccess { updatedUser ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    user = updatedUser,
                    statusMessage = "Profile updated successfully."
                )
                onDone()
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = ex.localizedMessage
                )
            }
        }
    }

    fun uploadProfilePhoto(uri: Uri, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val context = getApplication<Application>()
                val persistentFile = File(context.filesDir, "user_profile_avatar.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(persistentFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val localPhotoUriStr = "file://${persistentFile.absolutePath}"
                authRepo.sessionManager.updateProfile(
                    name = authRepo.sessionManager.getFullName(),
                    email = authRepo.sessionManager.getEmail(),
                    profileImage = localPhotoUriStr
                )
                _uiState.value = _uiState.value.copy(
                    user = (_uiState.value.user ?: UserDto(
                        id = authRepo.sessionManager.getUserId(),
                        full_name = authRepo.sessionManager.getFullName(),
                        email = authRepo.sessionManager.getEmail()
                    )).copy(profile_image = localPhotoUriStr)
                )

                val result = authRepo.uploadProfileImage(persistentFile)
                result.onSuccess { photoUrl ->
                    val finalUrl = if (photoUrl.isNotBlank()) photoUrl else localPhotoUriStr
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        user = _uiState.value.user?.copy(profile_image = finalUrl),
                        statusMessage = "Photo uploaded successfully."
                    )
                    onDone()
                }.onFailure { ex ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        statusMessage = "Photo saved."
                    )
                    onDone()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage
                )
            }
        }
    }

    fun removeProfilePhoto() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = authRepo.removeProfilePhoto()
            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    user = _uiState.value.user?.copy(profile_image = null),
                    statusMessage = "Photo removed."
                )
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = ex.localizedMessage
                )
            }
        }
    }

    fun updateSettings(settings: UserSettingsDto) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(settings = settings)
            authRepo.updateSettings(settings)
        }
    }

    fun logout(onSuccess: () -> Unit) {
        viewModelScope.launch {
            authRepo.logout()
            _uiState.value = ProfileUiState()
            onSuccess()
        }
    }
}
