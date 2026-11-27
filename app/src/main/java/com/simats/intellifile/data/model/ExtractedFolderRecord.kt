package com.simats.intellifile.data.model

data class ExtractedFolderRecord(
    val folderPath: String,
    val folderName: String,
    val sourceZipPath: String = "",
    val sourceZipName: String = "",
    val totalFiles: Int = 0,
    val totalSubfolders: Int = 0,
    val totalSizeBytes: Long = 0L,
    val extractedDateMs: Long = System.currentTimeMillis()
)
