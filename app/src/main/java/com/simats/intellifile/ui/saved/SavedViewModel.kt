package com.simats.intellifile.ui.saved

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simats.intellifile.data.local.LocalFileDatabaseHelper
import com.simats.intellifile.data.manager.LocalFileManager
import com.simats.intellifile.data.model.DocumentDto
import com.simats.intellifile.data.model.LocalFileItem
import com.simats.intellifile.data.repository.DocumentRepository
import com.simats.intellifile.ui.home.PriorityLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SavedUiState(
    val localFiles: List<LocalFileItem> = emptyList(),
    val serverSavedDocuments: List<DocumentDto> = emptyList(),
    val activeFilter: PriorityFilter = PriorityFilter.ALL,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class SavedViewModel(application: Application) : AndroidViewModel(application) {
    private val docRepo = DocumentRepository(application)
    private val dbHelper = LocalFileDatabaseHelper.getInstance(application)
    private val _uiState = MutableStateFlow(SavedUiState())
    val uiState: StateFlow<SavedUiState> = _uiState.asStateFlow()

    init {
        loadSavedFiles()
    }

    fun setFilter(filter: PriorityFilter) {
        _uiState.value = _uiState.value.copy(activeFilter = filter)
        loadSavedFiles(filter)
    }

    fun loadSavedFiles(filter: PriorityFilter = _uiState.value.activeFilter) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            // Local files filtered by priority / bookmarks
            val allLocal = LocalFileManager.getDeviceFiles(getApplication())
            val filteredLocal = allLocal.filter { item ->
                when (filter) {
                    PriorityFilter.ALL -> item.priority != null || item.isBookmarked
                    PriorityFilter.IMPORTANT -> item.priority == PriorityLevel.IMPORTANT
                    PriorityFilter.NORMAL -> item.priority == PriorityLevel.NORMAL
                    PriorityFilter.LOW -> item.priority == PriorityLevel.LOW
                    PriorityFilter.BOOKMARKED -> item.isBookmarked
                }
            }

            _uiState.value = _uiState.value.copy(localFiles = filteredLocal, isLoading = false)

            // Server saved files
            val priorityStr = when (filter) {
                PriorityFilter.ALL, PriorityFilter.BOOKMARKED -> "ALL"
                PriorityFilter.IMPORTANT -> "IMPORTANT"
                PriorityFilter.NORMAL -> "NORMAL"
                PriorityFilter.LOW -> "LOW"
            }
            val serverResult = docRepo.getSavedDocuments(priorityStr)
            serverResult.onSuccess { serverDocs ->
                _uiState.value = _uiState.value.copy(serverSavedDocuments = serverDocs)
            }
        }
    }

    fun toggleBookmark(file: LocalFileItem) {
        val key = file.path.ifBlank { file.uri.toString() }
        dbHelper.toggleBookmark(key, file.title)
        loadSavedFiles()
    }
}
