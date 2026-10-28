package com.simats.intellifile.data.manager

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.simats.intellifile.R
import com.simats.intellifile.data.local.LocalFileDatabaseHelper
import com.simats.intellifile.data.model.LocalFileItem
import com.simats.intellifile.ui.home.PriorityLevel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LocalFileManager {

    /**
     * Strictly document formats. No images, audio, video, or music.
     */
    private val SUPPORTED_DOCUMENT_EXTENSIONS = setOf(
        "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "csv", "json"
    )

    /**
     * Checks if the app currently has read access for storage on device.
     */
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Scans real document files on the user's phone via Android MediaStore and storage.
     * Combines retrieved files with offline SQLite metadata (priority, bookmarks, vault status).
     * Does NOT automatically assign fake or default priorities to documents:
     * priority is null unless explicitly assigned by the user.
     * Strictly on-device: does NOT upload any file.
     */
    fun getDeviceFiles(context: Context): List<LocalFileItem> {
        val dbHelper = LocalFileDatabaseHelper.getInstance(context)
        val metadataMap = dbHelper.getAllMetadata()
        val fileList = mutableListOf<LocalFileItem>()
        val seenPathsOrUris = mutableSetOf<String>()

        try {
            val contentUri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.DATA
            )

            // Filter strictly for supported document types
            val selectionArgsList = mutableListOf<String>()
            val extClauses = SUPPORTED_DOCUMENT_EXTENSIONS.map { ext ->
                selectionArgsList.add("%.$ext")
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
            }.joinToString(" OR ")

            val selection = "($extClauses)"
            val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

            val cursor = context.contentResolver.query(
                contentUri,
                projection,
                selection,
                selectionArgsList.toTypedArray(),
                sortOrder
            )

            cursor?.use { c ->
                val idCol = c.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val nameCol = c.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = c.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val dateCol = c.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val mimeCol = c.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                val dataCol = c.getColumnIndex(MediaStore.Files.FileColumns.DATA)

                while (c.moveToNext()) {
                    val id = if (idCol != -1) c.getLong(idCol) else 0L
                    val name = if (nameCol != -1) c.getString(nameCol) else null
                    val size = if (sizeCol != -1) c.getLong(sizeCol) else 0L
                    val dateSec = if (dateCol != -1) c.getLong(dateCol) else 0L
                    val mime = if (mimeCol != -1) c.getString(mimeCol) else null
                    val dataPath = if (dataCol != -1) c.getString(dataCol) else null

                    if (name.isNullOrBlank()) continue

                    val itemUri = ContentUris.withAppendedId(contentUri, id)
                    val key = dataPath ?: itemUri.toString()
                    seenPathsOrUris.add(key)

                    val format = resolveFormat(name, mime)
                    val iconRes = resolveIconResId(format)
                    val meta = metadataMap[key] ?: metadataMap[name]

                    // Priority is strictly user-assigned: null if not set
                    val priority = meta?.priority
                    val isBookmarked = meta?.isBookmarked ?: false
                    val isVaultSynced = meta?.isVaultSynced ?: false
                    val vaultDocId = meta?.vaultDocId

                    fileList.add(
                        LocalFileItem(
                            id = id,
                            title = name,
                            path = dataPath ?: "Internal Storage",
                            uri = itemUri,
                            sizeBytes = size,
                            sizeFormatted = formatFileSize(size),
                            dateModifiedMs = dateSec * 1000L,
                            dateText = formatDate(dateSec * 1000L),
                            format = format,
                            mimeType = mime ?: resolveMimeType(name),
                            priority = priority,
                            isBookmarked = isBookmarked,
                            isVaultSynced = isVaultSynced,
                            vaultDocId = vaultDocId,
                            iconResId = iconRes
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Secondary fallback: Scan standard Downloads & Documents public folders if MediaStore query was empty
        if (fileList.isEmpty()) {
            val fallbackDirs = listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            )

            for (dir in fallbackDirs) {
                if (dir != null && dir.exists() && dir.canRead()) {
                    scanDirectory(dir, fileList, seenPathsOrUris, metadataMap)
                }
            }
        }

        return fileList.sortedByDescending { it.dateModifiedMs }
    }

    private fun scanDirectory(
        directory: File,
        results: MutableList<LocalFileItem>,
        seen: MutableSet<String>,
        metadataMap: Map<String, com.simats.intellifile.data.local.LocalFileMetadata>
    ) {
        val files = directory.listFiles() ?: return
        for (f in files) {
            if (f.isFile && !seen.contains(f.absolutePath)) {
                val ext = f.extension.lowercase()
                if (SUPPORTED_DOCUMENT_EXTENSIONS.contains(ext)) {
                    seen.add(f.absolutePath)
                    val format = resolveFormat(f.name, null)
                    val meta = metadataMap[f.absolutePath] ?: metadataMap[f.name]

                    results.add(
                        LocalFileItem(
                            id = f.hashCode().toLong(),
                            title = f.name,
                            path = f.absolutePath,
                            uri = Uri.fromFile(f),
                            sizeBytes = f.length(),
                            sizeFormatted = formatFileSize(f.length()),
                            dateModifiedMs = f.lastModified(),
                            dateText = formatDate(f.lastModified()),
                            format = format,
                            mimeType = resolveMimeType(f.name),
                            priority = meta?.priority,
                            isBookmarked = meta?.isBookmarked ?: false,
                            isVaultSynced = meta?.isVaultSynced ?: false,
                            vaultDocId = meta?.vaultDocId,
                            iconResId = resolveIconResId(format)
                        )
                    )
                }
            }
        }
    }

    /**
     * Resolves the user-facing format label (PDF, DOC, DOCX, PPT, PPTX, XLS, XLSX, TXT, CSV, JSON).
     */
    fun resolveFormat(fileName: String, mimeType: String?): String {
        val ext = fileName.substringAfterLast('.', "").uppercase()
        if (ext.isNotEmpty()) {
            return when (ext) {
                "PDF" -> "PDF"
                "DOC", "DOCX" -> ext
                "PPT", "PPTX" -> ext
                "XLS", "XLSX" -> ext
                "CSV" -> "CSV"
                "TXT" -> "TXT"
                "JSON" -> "JSON"
                else -> ext
            }
        }
        if (mimeType != null) {
            return when {
                mimeType.contains("pdf", true) -> "PDF"
                mimeType.contains("word", true) -> "DOCX"
                mimeType.contains("powerpoint", true) || mimeType.contains("presentation", true) -> "PPTX"
                mimeType.contains("excel", true) || mimeType.contains("spreadsheet", true) -> "XLSX"
                mimeType.contains("text/plain", true) -> "TXT"
                mimeType.contains("csv", true) -> "CSV"
                mimeType.contains("json", true) -> "JSON"
                else -> "DOC"
            }
        }
        return "DOC"
    }

    /**
     * Returns matching drawable icon resource ID based on format.
     */
    fun resolveIconResId(format: String): Int {
        return when (format.uppercase()) {
            "PDF" -> R.drawable.pdf
            "DOC", "DOCX" -> R.drawable.doc
            "PPT", "PPTX" -> R.drawable.ppt
            else -> R.drawable.normal
        }
    }

    /**
     * Resolves standard MIME type for document extensions.
     */
    fun resolveMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "txt" -> "text/plain"
            "csv" -> "text/csv"
            "json" -> "application/json"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
    }

    /**
     * Formats bytes into human-readable text (e.g. "2.4 MB", "512 KB").
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 KB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    /**
     * Formats millisecond epoch into relative or date string.
     */
    fun formatDate(timeMs: Long): String {
        if (timeMs <= 0L) return "Recent"
        val now = System.currentTimeMillis()
        val diff = now - timeMs

        val oneDay = 24 * 60 * 60 * 1000L
        return when {
            diff < oneDay -> "Today"
            diff < 2 * oneDay -> "Yesterday"
            diff < 7 * oneDay -> "${diff / oneDay} days ago"
            else -> SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(timeMs))
        }
    }

    /**
     * Opens a real file using an Android Intent chooser with the system viewer.
     */
    fun openFileWithSystem(context: Context, fileItem: LocalFileItem): Boolean {
        try {
            val contentUri: Uri = if (fileItem.uri != Uri.EMPTY && fileItem.uri.scheme == "content") {
                fileItem.uri
            } else if (fileItem.path.isNotBlank()) {
                val file = File(fileItem.path)
                if (file.exists()) {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                } else {
                    fileItem.uri
                }
            } else {
                fileItem.uri
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, fileItem.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(intent, "Open with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                context,
                "No app available to open ${fileItem.title}",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }
    }

    /**
     * Shares a real file via Android Intent.ACTION_SEND.
     */
    fun shareFileWithSystem(context: Context, fileItem: LocalFileItem) {
        try {
            val contentUri: Uri = if (fileItem.uri != Uri.EMPTY && fileItem.uri.scheme == "content") {
                fileItem.uri
            } else if (fileItem.path.isNotBlank()) {
                val file = File(fileItem.path)
                if (file.exists()) {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                } else {
                    fileItem.uri
                }
            } else {
                fileItem.uri
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = fileItem.mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Share ${fileItem.title}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Could not share file", Toast.LENGTH_SHORT).show()
        }
    }
}
