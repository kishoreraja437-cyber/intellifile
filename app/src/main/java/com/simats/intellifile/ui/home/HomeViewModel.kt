package com.simats.intellifile.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simats.intellifile.data.manager.LocalFileManager
import com.simats.intellifile.data.model.DocumentDto
import com.simats.intellifile.data.model.LocalFileItem
import com.simats.intellifile.data.repository.DocumentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val localFiles: List<LocalFileItem> = emptyList(),
    val serverRecentDocuments: List<DocumentDto> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = ""
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val docRepo = DocumentRepository(application)
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadFiles()
    }

    fun loadFiles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val local = LocalFileManager.getDeviceFiles(getApplication())

            _uiState.value = _uiState.value.copy(localFiles = local, isLoading = false)

            // Asynchronously fetch server recent docs
            val serverResult = docRepo.getRecentDocuments(10)
            serverResult.onSuccess { serverDocs ->
                _uiState.value = _uiState.value.copy(serverRecentDocuments = serverDocs)
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }
}
