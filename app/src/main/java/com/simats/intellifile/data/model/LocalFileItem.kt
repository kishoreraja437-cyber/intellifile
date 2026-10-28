package com.simats.intellifile.data.model

import android.net.Uri
import com.simats.intellifile.R
import com.simats.intellifile.ui.detail.FileDetailData
import com.simats.intellifile.ui.home.PriorityLevel
import com.simats.intellifile.ui.home.RecentFile
import com.simats.intellifile.ui.saved.SavedFileItem

/**
 * Unified model for real documents stored on the user's mobile device.
 * Exclusively handles documents (PDF, DOC, DOCX, PPT, PPTX, XLS, XLSX, TXT, CSV, JSON).
 * No images or music/media.
 * Priority is strictly user-assigned (null if unassigned).
 */
data class LocalFileItem(
    val id: Long = 0L,
    val title: String,
    val path: String = "",
    val uri: Uri = Uri.EMPTY,
    val sizeBytes: Long = 0L,
    val sizeFormatted: String = "0 KB",
    val dateModifiedMs: Long = 0L,
    val dateText: String = "Recent",
    val format: String = "PDF",
    val mimeType: String = "application/pdf",
    val priority: PriorityLevel? = null,
    val isBookmarked: Boolean = false,
    val isVaultSynced: Boolean = false,
    val vaultDocId: Int? = null,
    val iconResId: Int = R.drawable.pdf
) {
    fun toRecentFile(): RecentFile {
        return RecentFile(
            title = title,
            format = format,
            size = sizeFormatted,
            dateText = dateText,
            priority = priority,
            iconResId = iconResId,
            uriString = uri.toString(),
            path = path
        )
    }

    fun toSavedFileItem(): SavedFileItem {
        return SavedFileItem(
            title = title,
            size = sizeFormatted,
            dateText = dateText,
            priority = priority,
            iconResId = iconResId,
            uriString = uri.toString(),
            path = path
        )
    }

    fun toFileDetailData(): FileDetailData {
        return FileDetailData(
            fileName = title,
            fileSize = sizeFormatted,
            fileType = "$format Document",
            createdDate = dateText,
            modifiedDate = dateText,
            category = when (format) {
                "PDF" -> "Document"
                "DOC", "DOCX" -> "Word Document"
                "PPT", "PPTX" -> "Presentation"
                "XLS", "XLSX", "CSV" -> "Spreadsheet"
                "TXT", "JSON" -> "Text Document"
                else -> "Document"
            },
            location = if (path.isNotBlank()) path else "Internal Storage",
            priority = priority,
            iconResId = iconResId,
            uriString = uri.toString(),
            path = path,
            isVaultSynced = isVaultSynced
        )
    }
}
