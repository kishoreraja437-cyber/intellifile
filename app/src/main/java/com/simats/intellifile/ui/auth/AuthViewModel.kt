package com.simats.intellifile.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simats.intellifile.data.model.UserDto
import com.simats.intellifile.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val currentUser: UserDto? = null
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val authRepo = AuthRepository(application)

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun login(
        email: String,
        pass: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        if (email.isBlank() || pass.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter both email and password.")
            onError("Please enter both email and password.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepo.login(email, pass)
            result.onSuccess { user ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentUser = user,
                    errorMessage = null
                )
                onSuccess()
            }.onFailure { ex ->
                val msg = ex.localizedMessage ?: "Login failed. Check your connection."
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = msg)
                onError(msg)
            }
        }
    }

    fun signup(
        fullName: String,
        email: String,
        pass: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        if (fullName.isBlank() || email.isBlank() || pass.isBlank()) {
            val msg = "Full name, email, and password are required."
            _uiState.value = _uiState.value.copy(errorMessage = msg)
            onError(msg)
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            val msg = "Please enter a valid email address."
            _uiState.value = _uiState.value.copy(errorMessage = msg)
            onError(msg)
            return
        }

        if (pass.length < 8) {
            val msg = "Password must be at least 8 characters."
            _uiState.value = _uiState.value.copy(errorMessage = msg)
            onError(msg)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepo.signup(fullName, email, pass)
            result.onSuccess { user ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentUser = user,
                    errorMessage = null
                )
                onSuccess()
            }.onFailure { ex ->
                val msg = ex.localizedMessage ?: "Signup failed. Please try again."
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = msg)
                onError(msg)
            }
        }
    }

    fun forgotPassword(
        email: String,
        newPass: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        if (email.isBlank() || newPass.isBlank()) {
            val msg = "Please enter your email and a new password."
            _uiState.value = _uiState.value.copy(errorMessage = msg)
            onError(msg)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepo.forgotPassword(email, newPass)
            result.onSuccess { msg ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    successMessage = msg,
                    errorMessage = null
                )
                onSuccess()
            }.onFailure { ex ->
                val msg = ex.localizedMessage ?: "Password reset failed."
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = msg)
                onError(msg)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
