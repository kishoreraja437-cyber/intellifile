package com.simats.intellifile.ui.viewer.ppt

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simats.intellifile.data.repository.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PPTViewerUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val document: PPTDocument? = null,
    val currentSlideIndex: Int = 0,
    val currentSlideBitmap: Bitmap? = null,
    val thumbnails: Map<Int, Bitmap> = emptyMap(),
    val zoomScale: Float = 1.0f,
    val panOffset: Offset = Offset.Zero,
    val isFullscreen: Boolean = false,
    val showThumbnailsDrawer: Boolean = false,
    val showNotes: Boolean = false,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val matchingSlideIndices: List<Int> = emptyList(),
    val currentSearchMatchIndex: Int = 0,
    val showJumpDialog: Boolean = false
) {
    val totalSlides: Int
        get() = document?.totalSlides ?: 1

    val currentSlideNumber: Int
        get() = currentSlideIndex + 1

    val currentSlideInfo: PPTSlideInfo?
        get() = document?.slides?.getOrNull(currentSlideIndex)

    val canGoNext: Boolean
        get() = currentSlideIndex < totalSlides - 1

    val canGoPrevious: Boolean
        get() = currentSlideIndex > 0
}

class PPTViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PPTViewerUiState())
    val uiState: StateFlow<PPTViewerUiState> = _uiState.asStateFlow()

    private var currentUriString: String = ""
    private var currentFilePath: String = ""
    private var currentFileName: String = ""
    private var currentDocumentId: Int? = null

    private var slideRenderJob: Job? = null
    private var thumbnailJob: Job? = null

    /**
     * Loads the presentation using LibreOffice rendering when available,
     * seamlessly falling back to the local high-fidelity OpenXML engine if offline.
     */
    fun loadPresentation(
        context: Context,
        uriString: String,
        filePath: String,
        fileName: String,
        documentId: Int? = null
    ) {
        currentUriString = uriString
        currentFilePath = filePath
        currentFileName = fileName
        currentDocumentId = documentId

        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                currentSlideBitmap = null,
                thumbnails = emptyMap()
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            var presentationDoc: PPTDocument? = null

            // Strategy 1: Automatic high-accuracy LibreOffice rendering via backend
            try {
                val docRepo = DocumentRepository(context)
                val conversionResult = docRepo.convertPresentationToPdf(
                    documentId = documentId,
                    filePath = filePath,
                    uriString = uriString,
                    fileName = fileName
                )

                if (conversionResult.isSuccess) {
                    val pdfFile = conversionResult.getOrNull()
                    if (pdfFile != null && pdfFile.exists() && pdfFile.length() > 0) {
                        val pdfDocResult = PPTDocumentHandler.loadPresentationFromPdf(pdfFile, fileName)
                        if (pdfDocResult.isSuccess) {
                            presentationDoc = pdfDocResult.getOrNull()
                            Log.d("PPTViewerViewModel", "Rendered presentation via LibreOffice PDF: ${presentationDoc?.totalSlides} slides")
                        }
                    }
                } else {
                    Log.w("PPTViewerViewModel", "LibreOffice conversion notice: ${conversionResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.w("PPTViewerViewModel", "LibreOffice conversion unavailable, falling back to local engine: ${e.message}")
            }

            // Strategy 2: Local High-Fidelity OpenXML Engine fallback
            val finalResult = if (presentationDoc != null) {
                Result.success(presentationDoc)
            } else {
                PPTDocumentHandler.loadPresentation(
                    context = context,
                    uriString = uriString,
                    path = filePath,
                    fileName = fileName
                )
            }

            finalResult.onSuccess { doc ->
                // Render first slide bitmap
                val firstBmp = PPTDocumentHandler.renderSlideBitmap(0, scale = 1.5f)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = null,
                        document = doc,
                        currentSlideIndex = 0,
                        currentSlideBitmap = firstBmp,
                        zoomScale = 1.0f,
                        panOffset = Offset.Zero,
                        isSearchActive = false,
                        searchQuery = "",
                        matchingSlideIndices = emptyList(),
                        currentSearchMatchIndex = 0
                    )
                }

                // Start pre-rendering thumbnails and adjacent slides
                startThumbnailGeneration(doc.totalSlides)
                preloadAdjacentSlides(0, doc.totalSlides)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Failed to open presentation visually."
                    )
                }
            }
        }
    }

    /**
     * Renders a specific slide index and updates the visual state.
     */
    fun goToSlide(slideIndex: Int) {
        val total = _uiState.value.totalSlides
        val target = slideIndex.coerceIn(0, (total - 1).coerceAtLeast(0))
        if (target == _uiState.value.currentSlideIndex && _uiState.value.currentSlideBitmap != null) {
            _uiState.update { it.copy(showJumpDialog = false) }
            return
        }

        _uiState.update {
            it.copy(
                currentSlideIndex = target,
                zoomScale = 1.0f,
                panOffset = Offset.Zero,
                showJumpDialog = false
            )
        }

        slideRenderJob?.cancel()
        slideRenderJob = viewModelScope.launch(Dispatchers.IO) {
            val bmp = PPTDocumentHandler.renderSlideBitmap(target, scale = 1.5f)
            _uiState.update {
                if (it.currentSlideIndex == target) {
                    it.copy(currentSlideBitmap = bmp)
                } else it
            }
            preloadAdjacentSlides(target, total)
        }
    }

    fun nextSlide() {
        val state = _uiState.value
        if (state.canGoNext) {
            goToSlide(state.currentSlideIndex + 1)
        }
    }

    fun previousSlide() {
        val state = _uiState.value
        if (state.canGoPrevious) {
            goToSlide(state.currentSlideIndex - 1)
        }
    }

    private fun preloadAdjacentSlides(current: Int, total: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            if (current + 1 < total) {
                PPTDocumentHandler.renderSlideBitmap(current + 1, scale = 1.5f)
            }
            if (current - 1 >= 0) {
                PPTDocumentHandler.renderSlideBitmap(current - 1, scale = 1.5f)
            }
        }
    }

    private fun startThumbnailGeneration(totalSlides: Int) {
        thumbnailJob?.cancel()
        thumbnailJob = viewModelScope.launch(Dispatchers.IO) {
            for (i in 0 until totalSlides) {
                val thumb = PPTDocumentHandler.renderThumbnailBitmap(i, scale = 0.25f)
                if (thumb != null) {
                    _uiState.update {
                        it.copy(thumbnails = it.thumbnails + (i to thumb))
                    }
                }
            }
        }
    }

    fun zoomIn() {
        _uiState.update {
            val next = (it.zoomScale + 0.25f).coerceAtMost(5.0f)
            it.copy(zoomScale = next)
        }
    }

    fun zoomOut() {
        _uiState.update {
            val next = (it.zoomScale - 0.25f).coerceAtLeast(1.0f)
            val pan = if (next == 1.0f) Offset.Zero else it.panOffset
            it.copy(zoomScale = next, panOffset = pan)
        }
    }

    fun resetZoom() {
        _uiState.update {
            it.copy(
                zoomScale = 1.0f,
                panOffset = Offset.Zero
            )
        }
    }

    fun updateZoomAndPan(zoomDelta: Float, panDelta: Offset) {
        _uiState.update {
            val newScale = (it.zoomScale * zoomDelta).coerceIn(1.0f, 5.0f)
            val maxPanX = (1000f * (newScale - 1f)).coerceAtLeast(0f)
            val maxPanY = (800f * (newScale - 1f)).coerceAtLeast(0f)
            val rawPan = if (newScale == 1.0f) Offset.Zero else it.panOffset + panDelta
            val clampedPan = Offset(
                x = rawPan.x.coerceIn(-maxPanX, maxPanX),
                y = rawPan.y.coerceIn(-maxPanY, maxPanY)
            )
            it.copy(zoomScale = newScale, panOffset = clampedPan)
        }
    }

    fun toggleFullscreen() {
        _uiState.update { it.copy(isFullscreen = !it.isFullscreen) }
    }

    fun setFullscreen(fullscreen: Boolean) {
        _uiState.update { it.copy(isFullscreen = fullscreen) }
    }

    fun toggleThumbnailsDrawer() {
        _uiState.update { it.copy(showThumbnailsDrawer = !it.showThumbnailsDrawer) }
    }

    fun setThumbnailsDrawer(show: Boolean) {
        _uiState.update { it.copy(showThumbnailsDrawer = show) }
    }

    fun toggleNotes() {
        _uiState.update { it.copy(showNotes = !it.showNotes) }
    }

    fun setSearchActive(active: Boolean) {
        _uiState.update {
            it.copy(
                isSearchActive = active,
                searchQuery = if (!active) "" else it.searchQuery,
                matchingSlideIndices = if (!active) emptyList() else it.matchingSlideIndices
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        val doc = _uiState.value.document ?: return
        if (query.isBlank()) {
            _uiState.update {
                it.copy(
                    searchQuery = query,
                    matchingSlideIndices = emptyList(),
                    currentSearchMatchIndex = 0
                )
            }
            return
        }

        val matches = mutableListOf<Int>()
        doc.slides.forEachIndexed { index, slide ->
            if (slide.title.contains(query, ignoreCase = true) ||
                slide.extractedText.contains(query, ignoreCase = true) ||
                slide.notes.contains(query, ignoreCase = true)
            ) {
                matches.add(index)
            }
        }

        val firstMatch = matches.firstOrNull() ?: _uiState.value.currentSlideIndex

        _uiState.update {
            it.copy(
                searchQuery = query,
                matchingSlideIndices = matches,
                currentSearchMatchIndex = 0
            )
        }

        if (matches.isNotEmpty() && firstMatch != _uiState.value.currentSlideIndex) {
            goToSlide(firstMatch)
        }
    }

    fun nextSearchResult() {
        val matches = _uiState.value.matchingSlideIndices
        if (matches.isEmpty()) return

        val nextMatchIndex = (_uiState.value.currentSearchMatchIndex + 1) % matches.size
        val targetSlide = matches[nextMatchIndex]

        _uiState.update {
            it.copy(currentSearchMatchIndex = nextMatchIndex)
        }
        goToSlide(targetSlide)
    }

    fun previousSearchResult() {
        val matches = _uiState.value.matchingSlideIndices
        if (matches.isEmpty()) return

        val currentIndex = _uiState.value.currentSearchMatchIndex
        val prevMatchIndex = if (currentIndex - 1 < 0) matches.size - 1 else currentIndex - 1
        val targetSlide = matches[prevMatchIndex]

        _uiState.update {
            it.copy(currentSearchMatchIndex = prevMatchIndex)
        }
        goToSlide(targetSlide)
    }

    fun clearSearch() {
        _uiState.update {
            it.copy(
                isSearchActive = false,
                searchQuery = "",
                matchingSlideIndices = emptyList(),
                currentSearchMatchIndex = 0
            )
        }
    }

    fun setShowJumpDialog(show: Boolean) {
        _uiState.update { it.copy(showJumpDialog = show) }
    }

    fun retry(context: Context) {
        if (currentUriString.isNotBlank() || currentFilePath.isNotBlank() || currentFileName.isNotBlank()) {
            loadPresentation(context, currentUriString, currentFilePath, currentFileName, currentDocumentId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        slideRenderJob?.cancel()
        thumbnailJob?.cancel()
        PPTDocumentHandler.close()
    }
}

