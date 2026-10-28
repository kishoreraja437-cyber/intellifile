package com.simats.intellifile.ui.files

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simats.intellifile.data.manager.LocalFileManager
import com.simats.intellifile.data.model.DocumentDto
import com.simats.intellifile.data.model.LocalFileItem
import com.simats.intellifile.data.model.StatisticsDto
import com.simats.intellifile.data.repository.DocumentRepository
import com.simats.intellifile.ui.home.PriorityLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MyFilesUiState(
    val selectedTab: StorageSourceTab = StorageSourceTab.DEVICE_FILES,
    val localFiles: List<LocalFileItem> = emptyList(),
    val vaultDocuments: List<DocumentDto> = emptyList(),
    val statistics: StatisticsDto? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class MyFilesViewModel(application: Application) : AndroidViewModel(application) {
    private val docRepo = DocumentRepository(application)
    private val _uiState = MutableStateFlow(MyFilesUiState())
    val uiState: StateFlow<MyFilesUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun setTab(tab: StorageSourceTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
        if (tab == StorageSourceTab.AI_VAULT && _uiState.value.vaultDocuments.isEmpty()) {
            loadVaultDocuments()
        }
    }

    fun loadData() {
        loadLocalFiles()
        loadVaultDocuments()
        loadStatistics()
    }

    fun loadLocalFiles() {
        viewModelScope.launch {
            val local = LocalFileManager.getDeviceFiles(getApplication())
            _uiState.value = _uiState.value.copy(localFiles = local)
        }
    }

    fun loadVaultDocuments() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = docRepo.getDocuments()
            result.onSuccess { docs ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    vaultDocuments = docs,
                    errorMessage = null
                )
            }.onFailure { ex ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = ex.localizedMessage
                )
            }
        }
    }

    fun loadStatistics() {
        viewModelScope.launch {
            val result = docRepo.getStatistics()
            result.onSuccess { stats ->
                _uiState.value = _uiState.value.copy(statistics = stats)
            }
        }
    }

    fun deleteVaultDocument(documentId: Int) {
        viewModelScope.launch {
            val result = docRepo.deleteDocument(documentId)
            result.onSuccess {
                loadVaultDocuments()
                loadStatistics()
            }
        }
    }
}
