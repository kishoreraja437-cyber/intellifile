package com.simats.intellifile.ui.tools.watermark

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class WatermarkRemoverUiState(
    val selectedPdfFile: File? = null,
    val selectedPdfUri: Uri? = null,
    val selectedPdfName: String = "",
    val isAnalyzing: Boolean = false,
    val analysisProgressMessage: String = "",
    val analysisResult: WatermarkAnalysisResult? = null,
    val selectedWatermarkIds: Set<String> = emptySet(),
    val customTextToRemove: String = "",
    val customRegionRel: RectF? = null,
    val isProcessing: Boolean = false,
    val processingProgress: Float = 0f,
    val processingStatus: String = "",
    val removalResult: WatermarkRemovalResult? = null,
    val currentPreviewPage: Int = 0,
    val previewMode: WatermarkPreviewMode = WatermarkPreviewMode.AFTER,
    val originalPageBitmap: Bitmap? = null,
    val cleanedPageBitmap: Bitmap? = null,
    val showSignatureWarning: Boolean = false,
    val showScannedNotice: Boolean = false,
    val errorMessage: String? = null
) {
    val totalPages: Int
        get() = analysisResult?.totalPages ?: 0

    val hasWatermarksDetected: Boolean
        get() = (analysisResult?.detectedWatermarks?.isNotEmpty() == true)

    val canStartRemoval: Boolean
        get() = !isAnalyzing && !isProcessing && selectedPdfFile != null &&
                (selectedWatermarkIds.isNotEmpty() || customTextToRemove.isNotBlank() || customRegionRel != null) &&
                (analysisResult?.securityStatus?.canModify == true)
}

class WatermarkRemoverViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "WatermarkRemoverVM"

    private val _uiState = MutableStateFlow(WatermarkRemoverUiState())
    val uiState: StateFlow<WatermarkRemoverUiState> = _uiState.asStateFlow()

    fun loadAndAnalyzePdf(
        context: Context,
        uri: Uri?,
        filePath: String = "",
        fileName: String = ""
    ) {
        val effectiveName = if (fileName.isNotBlank()) {
            fileName
        } else {
            uri?.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
        }

        _uiState.update {
            it.copy(
                selectedPdfUri = uri,
                selectedPdfName = effectiveName,
                isAnalyzing = true,
                analysisProgressMessage = "Opening PDF...",
                analysisResult = null,
                selectedWatermarkIds = emptySet(),
                customTextToRemove = "",
                customRegionRel = null,
                removalResult = null,
                originalPageBitmap = null,
                cleanedPageBitmap = null,
                errorMessage = null
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val file = PdfWatermarkEngine.resolvePdfFile(
                context = context,
                uriString = uri?.toString() ?: "",
                filePath = filePath,
                fileName = effectiveName
            )

            if (file == null || !file.exists()) {
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        errorMessage = "Could not access or open the selected PDF file."
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(selectedPdfFile = file) }

            // Render first page preview of original document
            val firstOriginal = PdfWatermarkEngine.renderPagePreview(file, 0)
            _uiState.update { it.copy(originalPageBitmap = firstOriginal) }

            // Run Analysis
            val result = PdfWatermarkEngine.analyzePdf(
                context = context,
                pdfFile = file,
                onProgress = { msg ->
                    _uiState.update { it.copy(analysisProgressMessage = msg) }
                }
            )

            result.onSuccess { analysis ->
                val autoSelectedIds = analysis.detectedWatermarks.map { it.id }.toSet()
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        analysisResult = analysis,
                        selectedWatermarkIds = autoSelectedIds,
                        showSignatureWarning = analysis.securityStatus.isDigitallySigned,
                        showScannedNotice = analysis.isScannedPdf,
                        errorMessage = if (!analysis.securityStatus.canModify) {
                            analysis.securityStatus.restrictionMessage
                        } else null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        errorMessage = err.message ?: "Failed analyzing PDF document."
                    )
                }
            }
        }
    }

    fun toggleWatermarkSelection(id: String) {
        _uiState.update { current ->
            val set = current.selectedWatermarkIds.toMutableSet()
            if (set.contains(id)) set.remove(id) else set.add(id)
            current.copy(selectedWatermarkIds = set)
        }
    }

    fun selectAllWatermarks(select: Boolean) {
        _uiState.update { current ->
            val set = if (select) {
                current.analysisResult?.detectedWatermarks?.map { it.id }?.toSet() ?: emptySet()
            } else emptySet()
            current.copy(selectedWatermarkIds = set)
        }
    }

    fun setCustomText(text: String) {
        _uiState.update { it.copy(customTextToRemove = text) }
    }

    fun setCustomRegion(region: RectF?) {
        _uiState.update { it.copy(customRegionRel = region) }
    }

    fun clearCustomRegion() {
        _uiState.update { it.copy(customRegionRel = null) }
    }

    fun dismissSignatureWarning() {
        _uiState.update { it.copy(showSignatureWarning = false) }
    }

    fun dismissScannedNotice() {
        _uiState.update { it.copy(showScannedNotice = false) }
    }

    fun confirmProceedWithUnsignedCopy(context: Context) {
        _uiState.update { it.copy(showSignatureWarning = false) }
        executeRemoval(context, proceedWithUnsigned = true)
    }

    fun startRemoval(context: Context) {
        val analysis = _uiState.value.analysisResult
        if (analysis?.securityStatus?.isDigitallySigned == true) {
            _uiState.update { it.copy(showSignatureWarning = true) }
            return
        }
        executeRemoval(context, proceedWithUnsigned = false)
    }

    private fun executeRemoval(context: Context, proceedWithUnsigned: Boolean) {
        val current = _uiState.value
        val file = current.selectedPdfFile ?: return
        val analysis = current.analysisResult ?: return

        _uiState.update {
            it.copy(
                isProcessing = true,
                processingProgress = 0.05f,
                processingStatus = "Starting watermark removal...",
                removalResult = null,
                errorMessage = null
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val options = WatermarkRemovalOptions(
                selectedWatermarkIds = current.selectedWatermarkIds,
                customTextToRemove = current.customTextToRemove.ifBlank { null },
                customRegionRel = current.customRegionRel,
                customRegionPage = current.currentPreviewPage,
                applyScannedInpainting = true,
                proceedWithUnsignedCopy = proceedWithUnsigned
            )

            val result = PdfWatermarkEngine.removeWatermarks(
                context = context,
                pdfFile = file,
                options = options,
                analysisResult = analysis,
                onProgress = { prog, status ->
                    _uiState.update {
                        it.copy(
                            processingProgress = prog,
                            processingStatus = status
                        )
                    }
                }
            )

            result.onSuccess { removal ->
                // Render first page preview of cleaned document
                val cleanedBmp = removal.cleanedFile?.let {
                    PdfWatermarkEngine.renderPagePreview(it, current.currentPreviewPage)
                }

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        removalResult = removal,
                        previewMode = WatermarkPreviewMode.AFTER,
                        cleanedPageBitmap = cleanedBmp,
                        errorMessage = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = err.message ?: "Failed removing watermarks."
                    )
                }
            }
        }
    }

    fun setPreviewPage(context: Context, pageIndex: Int) {
        val total = _uiState.value.totalPages
        if (total == 0) return
        val target = pageIndex.coerceIn(0, total - 1)
        if (target == _uiState.value.currentPreviewPage) return

        _uiState.update { it.copy(currentPreviewPage = target) }

        viewModelScope.launch(Dispatchers.IO) {
            val origFile = _uiState.value.selectedPdfFile
            val cleanedFile = _uiState.value.removalResult?.cleanedFile

            val origBmp = origFile?.let { PdfWatermarkEngine.renderPagePreview(it, target) }
            val cleanedBmp = cleanedFile?.let { PdfWatermarkEngine.renderPagePreview(it, target) }

            _uiState.update {
                it.copy(
                    originalPageBitmap = origBmp,
                    cleanedPageBitmap = cleanedBmp
                )
            }
        }
    }

    fun setPreviewMode(mode: WatermarkPreviewMode) {
        _uiState.update { it.copy(previewMode = mode) }
    }

    fun saveCleanedPdfToUri(context: Context, destinationUri: Uri): Boolean {
        val cleaned = _uiState.value.removalResult?.cleanedFile ?: return false
        return try {
            context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                cleaned.inputStream().use { inp ->
                    inp.copyTo(out)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving cleaned PDF to destination URI", e)
            false
        }
    }

    fun reset() {
        _uiState.value = WatermarkRemoverUiState()
    }
}
