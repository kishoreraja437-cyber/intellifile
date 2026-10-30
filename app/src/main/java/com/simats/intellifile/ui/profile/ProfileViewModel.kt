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
    private val authRepo = AuthRepository(application)
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
        loadSettings()
    }

    fun loadProfile() {
        val cachedImage = authRepo.sessionManager.getProfileImage()
        val cachedUser = UserDto(
            id = authRepo.sessionManager.getUserId(),
            full_name = authRepo.sessionManager.getFullName().ifBlank { "User" },
            email = authRepo.sessionManager.getEmail().ifBlank { "user@example.com" },
            profile_image = cachedImage
        )
        _uiState.value = _uiState.value.copy(user = cachedUser)

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = authRepo.getProfile()
            result.onSuccess { user ->
                val finalUser = if (user.profile_image.isNullOrBlank() && !cachedImage.isNullOrBlank()) {
                    user.copy(profile_image = cachedImage)
                } else {
                    user
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    user = finalUser,
                    errorMessage = null
                )
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = null // Keep cached user visible cleanly without blocking UI
                )
            }
        }
    }

    fun loadSettings() {
        viewModelScope.launch {
            val result = authRepo.getSettings()
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
                val tempFile = File(context.cacheDir, "avatar_upload_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val result = authRepo.uploadProfileImage(tempFile)
                result.onSuccess { photoUrl ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        user = _uiState.value.user?.copy(profile_image = photoUrl),
                        statusMessage = "Photo uploaded successfully."
                    )
                    onDone()
                }.onFailure { ex ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = ex.localizedMessage ?: "Failed to upload photo"
                    )
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
            onSuccess()
        }
    }
}
