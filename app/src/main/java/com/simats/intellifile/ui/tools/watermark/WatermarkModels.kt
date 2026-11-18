package com.simats.intellifile.ui.tools.watermark

import android.graphics.RectF
import java.io.File

/**
 * Types of watermarks detectable in PDF documents.
 */
enum class WatermarkType(val label: String) {
    TEXT_KEYWORD("Text Keyword"),
    ROTATED_TEXT("Rotated / Angled Text"),
    TRANSPARENT_OBJECT("Transparent / Opacity Overlay"),
    IMAGE_XOBJECT("Image XObject"),
    FORM_XOBJECT("Form XObject"),
    ARTIFACT_MARKED_CONTENT("Marked Content Artifact"),
    SCANNED_STAMP("Scanned Stamp / Inpainting"),
    CUSTOM_TEXT("Custom Text Target"),
    CUSTOM_REGION("Custom Region Scrub")
}

/**
 * Represents a single detected watermark element inside the PDF.
 */
data class DetectedWatermark(
    val id: String,
    val type: WatermarkType,
    val textOrName: String,
    val detectionReason: String,
    val affectedPages: List<Int>, // 0-indexed page numbers
    val confidence: Float, // 0.0 to 1.0
    val isSelected: Boolean = true,
    val isScanned: Boolean = false,
    val boundingBoxRel: RectF? = null // Coordinates relative to page size (0.0 to 1.0)
) {
    val affectedPagesSummary: String
        get() {
            if (affectedPages.isEmpty()) return "Unknown"
            if (affectedPages.size == 1) return "Page ${affectedPages[0] + 1}"
            return "Pages ${affectedPages.map { it + 1 }.joinToString(", ")}"
        }
}

/**
 * Security and digital signature status of the target PDF.
 */
data class PdfSecurityStatus(
    val isEncrypted: Boolean = false,
    val isDigitallySigned: Boolean = false,
    val signatureDetails: String = "",
    val canModify: Boolean = true,
    val restrictionMessage: String? = null
)

/**
 * Complete analysis result of a scanned or native PDF.
 */
data class WatermarkAnalysisResult(
    val totalPages: Int,
    val isScannedPdf: Boolean,
    val securityStatus: PdfSecurityStatus,
    val detectedWatermarks: List<DetectedWatermark>,
    val pageDimensions: Map<Int, Pair<Float, Float>> = emptyMap() // Page index to (width, height)
)

/**
 * User options passed into the watermark removal engine.
 */
data class WatermarkRemovalOptions(
    val selectedWatermarkIds: Set<String>,
    val customTextToRemove: String? = null,
    val customRegionRel: RectF? = null,
    val customRegionPage: Int = -1, // -1 means all pages
    val applyScannedInpainting: Boolean = true,
    val proceedWithUnsignedCopy: Boolean = false
)

/**
 * Output result from the watermark removal execution.
 */
data class WatermarkRemovalResult(
    val isSuccess: Boolean,
    val cleanedFile: File? = null,
    val cleanedFileName: String = "",
    val removedObjectsCount: Int = 0,
    val message: String = "",
    val isScannedProcessed: Boolean = false,
    val warningMessage: String? = null,
    val errorMessage: String? = null
)

enum class WatermarkPreviewMode {
    BEFORE, AFTER
}
