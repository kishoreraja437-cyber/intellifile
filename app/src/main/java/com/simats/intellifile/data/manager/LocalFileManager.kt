package com.simats.intellifile.data.manager

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.simats.intellifile.R
import com.simats.intellifile.data.local.LocalFileDatabaseHelper
import com.simats.intellifile.data.model.LocalFileItem
import com.simats.intellifile.ui.home.PriorityLevel
import com.simats.intellifile.data.api.NetworkConfig
import com.simats.intellifile.data.model.DocumentDto
import com.simats.intellifile.data.repository.DocumentRepository
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

object LocalFileManager {

    /**
     * Strictly document formats. No images, audio, video, or music.
     */
    private val SUPPORTED_DOCUMENT_EXTENSIONS = setOf(
        "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "csv", "json",
        "tsv", "xml", "html", "htm", "md", "rtf", "log", "zip"
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

    @Volatile
    private var cachedFiles: List<LocalFileItem>? = null
    @Volatile
    private var lastScanTimeMs: Long = 0L
    private const val CACHE_DURATION_MS = 60_000L // 60 seconds

    fun invalidateCache() {
        cachedFiles = null
        lastScanTimeMs = 0L
    }

    /**
     * Scans real document files on the user's phone via Android MediaStore and storage.
     * Combines retrieved files with offline SQLite metadata (priority, bookmarks, vault status).
     * Does NOT automatically assign fake or default priorities to documents:
     * priority is null unless explicitly assigned by the user.
     * Strictly on-device: does NOT upload any file.
     */
    fun getDeviceFiles(context: Context, forceRefresh: Boolean = false): List<LocalFileItem> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedFiles != null && (now - lastScanTimeMs < CACHE_DURATION_MS)) {
            return cachedFiles!!
        }

        val dbHelper = LocalFileDatabaseHelper.getInstance(context)
        val cachedVaultDocs = try { DocumentRepository.getCachedVaultDocs(context) } catch (_: Exception) { emptyList() }
        if (cachedVaultDocs.isNotEmpty()) {
            dbHelper.restoreServerDocuments(cachedVaultDocs)
        }
        val metadataMap = dbHelper.getAllMetadata()
        val fileList = mutableListOf<LocalFileItem>()
        val seenPathsOrUris = mutableSetOf<String>()
        val seenTitles = mutableSetOf<String>()

        try {
            val contentUri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.MediaColumns.DATE_ADDED
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
                val dateAddedCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)

                while (c.moveToNext()) {
                    val id = if (idCol != -1) c.getLong(idCol) else 0L
                    val name = if (nameCol != -1) c.getString(nameCol) else null
                    val size = if (sizeCol != -1) c.getLong(sizeCol) else 0L
                    val dateSec = if (dateCol != -1) c.getLong(dateCol) else 0L
                    val mime = if (mimeCol != -1) c.getString(mimeCol) else null
                    val dataPath = if (dataCol != -1) c.getString(dataCol) else null
                    val dateAddedSec = if (dateAddedCol != -1) c.getLong(dateAddedCol) else 0L

                    if (name.isNullOrBlank()) continue

                    val itemUri = ContentUris.withAppendedId(contentUri, id)
                    val key = dataPath ?: itemUri.toString()
                    seenPathsOrUris.add(key)
                    seenTitles.add(name.trim().lowercase())

                    val format = resolveFormat(name, mime)
                    val iconRes = resolveIconResId(format)
                    val meta = metadataMap[key] 
                        ?: (if (dataPath != null) metadataMap[dataPath] else null) 
                        ?: metadataMap[itemUri.toString()] 
                        ?: metadataMap[name]
                        ?: metadataMap[name.lowercase()]

                    // Priority is strictly user-assigned: null if not set
                    var priority = meta?.priority
                    var isBookmarked = meta?.isBookmarked ?: false
                    var isVaultSynced = meta?.isVaultSynced ?: false
                    var vaultDocId = meta?.vaultDocId

                    // Connect with server-side document if not yet synced locally
                    val matchedVault = cachedVaultDocs.firstOrNull { it.title.trim().equals(name.trim(), ignoreCase = true) }
                    if (matchedVault != null) {
                        isVaultSynced = true
                        if (vaultDocId == null || vaultDocId <= 0) {
                            vaultDocId = matchedVault.id
                        }
                        if (priority == null && !matchedVault.priority.isNullOrBlank()) {
                            try {
                                priority = PriorityLevel.valueOf(matchedVault.priority.uppercase())
                            } catch (_: Exception) {}
                        }
                        if (matchedVault.is_bookmarked) {
                            isBookmarked = true
                        }
                    }

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
                            iconResId = iconRes,
                            dateAddedMs = dateAddedSec * 1000L
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
                    scanDirectory(dir, fileList, seenPathsOrUris, seenTitles, metadataMap, cachedVaultDocs)
                }
            }
        }

        // Include any server IntelliSpace documents that were not found in local disk scan
        for (vDoc in cachedVaultDocs) {
            val vTitle = vDoc.title.trim()
            if (vTitle.isNotBlank() && !seenTitles.contains(vTitle.lowercase())) {
                seenTitles.add(vTitle.lowercase())
                val vFormat = vDoc.format?.takeIf { it.isNotBlank() } ?: resolveFormat(vTitle, null)
                val vPriority = vDoc.priority?.let {
                    try { PriorityLevel.valueOf(it.uppercase()) } catch (_: Exception) { null }
                } ?: metadataMap[vTitle]?.priority ?: metadataMap[vTitle.lowercase()]?.priority

                val vPath = if (!vDoc.location.isNullOrBlank() && vDoc.location != "Internal Storage") vDoc.location else (if (!vDoc.file_path.isNullOrBlank()) vDoc.file_path else "Internal Storage")
                val vUri = if (!vDoc.file_url.isNullOrBlank()) Uri.parse(vDoc.file_url) else Uri.EMPTY

                fileList.add(
                    LocalFileItem(
                        id = vDoc.id.toLong(),
                        title = vTitle,
                        path = vPath,
                        uri = vUri,
                        sizeBytes = 0L,
                        sizeFormatted = vDoc.file_size?.takeIf { it.isNotBlank() } ?: "1.0 MB",
                        dateModifiedMs = System.currentTimeMillis(),
                        dateText = vDoc.last_accessed?.takeIf { it.isNotBlank() } ?: vDoc.created_at?.takeIf { it.isNotBlank() } ?: "Recent",
                        format = vFormat,
                        mimeType = resolveMimeType(vTitle),
                        priority = vPriority,
                        isBookmarked = vDoc.is_bookmarked,
                        isVaultSynced = true,
                        vaultDocId = vDoc.id,
                        iconResId = resolveIconResId(vFormat),
                        dateAddedMs = 0L
                    )
                )
            }
        }

        val sorted = fileList.sortedByDescending { it.dateModifiedMs }
        cachedFiles = sorted
        lastScanTimeMs = System.currentTimeMillis()
        return sorted
    }

    private fun scanDirectory(
        directory: File,
        results: MutableList<LocalFileItem>,
        seen: MutableSet<String>,
        seenTitles: MutableSet<String>,
        metadataMap: Map<String, com.simats.intellifile.data.local.LocalFileMetadata>,
        cachedVaultDocs: List<DocumentDto>
    ) {
        val files = directory.listFiles() ?: return
        for (f in files) {
            if (f.isFile && !seen.contains(f.absolutePath)) {
                val ext = f.extension.lowercase()
                if (SUPPORTED_DOCUMENT_EXTENSIONS.contains(ext)) {
                    seen.add(f.absolutePath)
                    seenTitles.add(f.name.trim().lowercase())
                    val format = resolveFormat(f.name, null)
                    val meta = metadataMap[f.absolutePath] 
                        ?: metadataMap[f.name]
                        ?: metadataMap[f.name.lowercase()]

                    var priority = meta?.priority
                    var isBookmarked = meta?.isBookmarked ?: false
                    var isVaultSynced = meta?.isVaultSynced ?: false
                    var vaultDocId = meta?.vaultDocId

                    val matchedVault = cachedVaultDocs.firstOrNull { it.title.trim().equals(f.name.trim(), ignoreCase = true) }
                    if (matchedVault != null) {
                        isVaultSynced = true
                        if (vaultDocId == null || vaultDocId <= 0) {
                            vaultDocId = matchedVault.id
                        }
                        if (priority == null && !matchedVault.priority.isNullOrBlank()) {
                            try {
                                priority = PriorityLevel.valueOf(matchedVault.priority.uppercase())
                            } catch (_: Exception) {}
                        }
                        if (matchedVault.is_bookmarked) {
                            isBookmarked = true
                        }
                    }

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
                            priority = priority,
                            isBookmarked = isBookmarked,
                            isVaultSynced = isVaultSynced,
                            vaultDocId = vaultDocId,
                            iconResId = resolveIconResId(format)
                        )
                    )
                }
            }
        }
    }

    /**
     * Resolves the user-facing format label (PDF, DOC, DOCX, PPT, PPTX, XLS, XLSX, TXT, CSV, JSON, TSV, XML, HTML, MD, RTF, LOG, ZIP).
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
                "TSV" -> "TSV"
                "TXT" -> "TXT"
                "JSON" -> "JSON"
                "XML" -> "XML"
                "HTML", "HTM" -> "HTML"
                "MD" -> "MD"
                "RTF" -> "RTF"
                "LOG" -> "LOG"
                "ZIP" -> "ZIP"
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
                mimeType.contains("tab-separated", true) -> "TSV"
                mimeType.contains("json", true) -> "JSON"
                mimeType.contains("xml", true) -> "XML"
                mimeType.contains("html", true) -> "HTML"
                mimeType.contains("markdown", true) -> "MD"
                mimeType.contains("rtf", true) -> "RTF"
                mimeType.contains("zip", true) -> "ZIP"
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
            "tsv" -> "text/tab-separated-values"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "html", "htm" -> "text/html"
            "md" -> "text/markdown"
            "rtf" -> "application/rtf"
            "log" -> "text/plain"
            "zip" -> "application/zip"
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

    /**
     * Copies a file's location or URI to the system clipboard.
     */
    fun copyPathToClipboard(context: Context, pathOrUri: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("File Path", pathOrUri)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "File path copied to clipboard", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Could not copy path", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Permanently deletes a file from the device (filesystem, MediaStore, and SQLite cache).
     * Handles both direct file paths, MediaStore content URIs, and scoped storage locations.
     */
    fun deleteLocalFile(context: Context, path: String?, uriString: String?): Boolean {
        var deleted = false

        // 1. Delete from direct filesystem path
        if (!path.isNullOrBlank()) {
            try {
                val file = File(path)
                if (file.exists()) {
                    deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Delete via ContentResolver / MediaStore URI
        if (!uriString.isNullOrBlank()) {
            try {
                val uri = Uri.parse(uriString)
                when (uri.scheme) {
                    "content" -> {
                        val count = context.contentResolver.delete(uri, null, null)
                        if (count > 0) deleted = true
                    }
                    "file" -> {
                        val f = File(uri.path ?: "")
                        if (f.exists()) {
                            val ok = if (f.isDirectory) f.deleteRecursively() else f.delete()
                            if (ok) deleted = true
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Fallback purge from MediaStore Files provider using DATA column
        if (!path.isNullOrBlank()) {
            try {
                val filesUri = MediaStore.Files.getContentUri("external")
                val rows = context.contentResolver.delete(
                    filesUri,
                    "${MediaStore.Files.FileColumns.DATA} = ?",
                    arrayOf(path)
                )
                if (rows > 0) deleted = true
            } catch (e: Exception) {
                // Scoped storage restriction or already removed
            }
        }

        // 4. Purge SQLite metadata records
        try {
            val dbHelper = LocalFileDatabaseHelper.getInstance(context)
            if (!path.isNullOrBlank()) {
                val fileName = File(path).name
                dbHelper.deleteFileMetadata(path, fileName)
            }
            if (!uriString.isNullOrBlank()) {
                dbHelper.deleteFileMetadata(uriString)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 5. Invalidate scan cache so all screens refresh immediately
        invalidateCache()

        return deleted
    }

    /**
     * Shares a file directly to a specific target app (WhatsApp, Gmail, Telegram, Bluetooth)
     * or falls back to system chooser if not installed.
     */
    fun shareFileToTarget(context: Context, fileItem: LocalFileItem, targetName: String) {
        val packageName = when (targetName.lowercase()) {
            "whatsapp" -> {
                val isNormalInstalled = try {
                    context.packageManager.getPackageInfo("com.whatsapp", 0)
                    true
                } catch (_: Exception) { false }
                if (isNormalInstalled) "com.whatsapp" else "com.whatsapp.w4b"
            }
            "gmail" -> "com.google.android.gm"
            "telegram" -> "org.telegram.messenger"
            "bluetooth" -> "com.android.bluetooth"
            else -> null
        }

        if (packageName != null) {
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
                    } else fileItem.uri
                } else fileItem.uri

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = fileItem.mimeType
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    setPackage(packageName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(shareIntent)
                return
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "$targetName is not installed. Opening share options...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Fallback to standard system chooser
        shareFileWithSystem(context, fileItem)
    }

    /**
     * Resolves a ParcelFileDescriptor for reading a file (e.g. for PdfRenderer).
     */
    fun getFileDescriptor(context: Context, uriString: String, path: String): ParcelFileDescriptor? {
        if (path.isNotBlank()) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    if (pfd != null) return pfd
                }
            } catch (_: Exception) {}
        }

        if (uriString.isNotBlank()) {
            try {
                val uri = Uri.parse(uriString)
                if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
                    val file = File(uri.path!!)
                    if (file.exists() && file.canRead()) {
                        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                        if (pfd != null) return pfd
                    }
                }
                if (uri.scheme == "content") {
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) return pfd
                }
            } catch (_: Exception) {}
        }

        if (path.isNotBlank()) {
            try {
                val file = File(path)
                if (file.exists()) {
                    val fpUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val pfd = context.contentResolver.openFileDescriptor(fpUri, "r")
                    if (pfd != null) return pfd
                }
            } catch (_: Exception) {}
        }

        return null
    }

    /**
     * Opens an InputStream for a document by URI or file path.
     * Supports local files, Android ContentProvider URIs, and remote/server document URLs.
     */
    fun openInputStream(context: Context, uriString: String, path: String): InputStream? {
        if (path.isNotBlank()) {
            if (path.startsWith("http://") || path.startsWith("https://")) {
                try {
                    return java.net.URL(path).openStream()
                } catch (_: Exception) {}
            } else if (path.startsWith("uploads/") || path.contains("uploads/")) {
                try {
                    val resolved = NetworkConfig.resolveMediaUrl(path)
                    if (resolved != null && (resolved.startsWith("http://") || resolved.startsWith("https://"))) {
                        return java.net.URL(resolved).openStream()
                    }
                } catch (_: Exception) {}
            } else {
                try {
                    val file = File(path)
                    if (file.exists() && file.canRead()) {
                        return file.inputStream()
                    }
                } catch (_: Exception) {}
            }
        }

        if (uriString.isNotBlank()) {
            try {
                val uri = Uri.parse(uriString)
                if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
                    val file = File(uri.path!!)
                    if (file.exists() && file.canRead()) {
                        return file.inputStream()
                    }
                } else if (uri.scheme == "http" || uri.scheme == "https") {
                    return java.net.URL(uriString).openStream()
                } else if (uri.scheme == "content") {
                    val stream = context.contentResolver.openInputStream(uri)
                    if (stream != null) return stream
                }
            } catch (_: Exception) {}

            // Try resolving if uriString is a relative server path
            if (uriString.startsWith("uploads/") || uriString.contains("uploads/")) {
                try {
                    val resolved = NetworkConfig.resolveMediaUrl(uriString)
                    if (resolved != null && (resolved.startsWith("http://") || resolved.startsWith("https://"))) {
                        return java.net.URL(resolved).openStream()
                    }
                } catch (_: Exception) {}
            }
        }

        return null
    }

    /**
     * Prepares a seekable, sanitized PDF File stored in local storage or cache.
     * Detects and strips any leading BOM or whitespace before '%PDF-' so native PdfRenderer
     * does not crash on non-zero offsets, and downloads remote/content streams to a local seekable file.
     */
    fun prepareSeekablePdfFile(context: Context, uriString: String, path: String): File? {
        // Step 1: Check local path directly
        if (path.isNotBlank() && !path.startsWith("http://") && !path.startsWith("https://") && !path.startsWith("uploads/")) {
            val localFile = File(path)
            if (localFile.exists() && localFile.canRead() && localFile.length() > 4) {
                try {
                    val header = ByteArray(1024)
                    val readCount = localFile.inputStream().use { it.read(header) }
                    if (readCount >= 4) {
                        if (header[0] == '%'.code.toByte() && header[1] == 'P'.code.toByte() &&
                            header[2] == 'D'.code.toByte() && header[3] == 'F'.code.toByte()) {
                            return localFile
                        }
                        // Check if %PDF- exists in first 1024 bytes (e.g. BOM / leading whitespace)
                        val headerStr = String(header, 0, readCount, Charsets.ISO_8859_1)
                        val pdfIdx = headerStr.indexOf("%PDF-")
                        if (pdfIdx > 0) {
                            val sanitizedFile = File(context.cacheDir, "sanitized_pdf_${System.currentTimeMillis()}.pdf")
                            localFile.inputStream().use { input ->
                                input.skip(pdfIdx.toLong())
                                sanitizedFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            if (sanitizedFile.exists() && sanitizedFile.length() > 0) {
                                return sanitizedFile
                            }
                        }
                    }
                    return localFile
                } catch (_: Exception) {}
            }
        }

        // Step 2: Read stream (handles content URIs, http URLs, relative paths) and write to seekable file
        return try {
            val stream = openInputStream(context, uriString, path) ?: return null
            val tempFile = File(context.cacheDir, "seekable_pdf_${System.currentTimeMillis()}.pdf")
            stream.use { rawInput ->
                val buffered = java.io.BufferedInputStream(rawInput)
                buffered.mark(2048)
                val probe = ByteArray(1024)
                val readProbe = buffered.read(probe)
                buffered.reset()

                if (readProbe > 0) {
                    val probeStr = String(probe, 0, readProbe, Charsets.ISO_8859_1)
                    val pdfIdx = probeStr.indexOf("%PDF-")
                    if (pdfIdx > 0) {
                        buffered.skip(pdfIdx.toLong())
                    }
                }

                tempFile.outputStream().use { output ->
                    buffered.copyTo(output)
                }
            }
            if (tempFile.exists() && tempFile.length() > 0) tempFile else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Reads text content from supported text-based documents (TXT, CSV, JSON, MD, DOCX, PPTX).
     */
    fun readTextContent(
        context: Context,
        uriString: String,
        path: String,
        format: String,
        maxChars: Int = 50000
    ): String {
        val stream = openInputStream(context, uriString, path) ?: return ""
        return try {
            when (format.uppercase()) {
                "TXT", "CSV", "TSV", "JSON", "XML", "HTML", "HTM", "MD", "LOG", "RTF" -> {
                    stream.bufferedReader().use { reader ->
                        val buffer = CharArray(maxChars)
                        val read = reader.read(buffer, 0, maxChars)
                        if (read > 0) String(buffer, 0, read) else ""
                    }
                }
                "DOCX" -> {
                    val bytes = stream.readBytes()
                    val docx = DocumentContentParser.parseDocx(bytes, "document.docx")
                    if (docx.fullText.isNotBlank()) docx.fullText.take(maxChars) else "No readable text found in Word document."
                }
                "DOC" -> {
                    val bytes = stream.readBytes()
                    val doc = DocumentContentParser.parseDoc(bytes, "document.doc")
                    if (doc.fullText.isNotBlank()) doc.fullText.take(maxChars) else "No readable text found in Word document."
                }
                "PPTX" -> extractPptxText(stream, maxChars)
                "PPT" -> {
                    val bytes = stream.readBytes()
                    val presentation = DocumentContentParser.parsePpt(bytes, "presentation.ppt")
                    if (presentation != null && presentation.slides.isNotEmpty()) {
                        presentation.slides.joinToString("\n\n") { slide ->
                            val body = if (slide.points.isNotEmpty()) slide.points.joinToString("\n") else slide.rawContent
                            "--- Slide ${slide.slideNumber} ---\n${slide.title}\n$body"
                        }.take(maxChars)
                    } else {
                        "No readable text found in PowerPoint presentation."
                    }
                }
                else -> {
                    try {
                        stream.bufferedReader().use { it.readText().take(maxChars) }
                    } catch (_: Exception) {
                        ""
                    }
                }
            }
        } catch (e: Exception) {
            "Error reading file content: ${e.localizedMessage}"
        } finally {
            try { stream.close() } catch (_: Exception) {}
        }
    }

    /**
     * Parses word/document.xml inside a DOCX file to extract text.
     */
    fun extractDocxText(inputStream: InputStream, maxChars: Int = 50000): String {
        return try {
            val zip = ZipInputStream(inputStream)
            var entry = zip.nextEntry
            val sb = StringBuilder()
            val pRegex = Regex("<w:p[ >](.*?)</w:p>")
            val tRegex = Regex("<w:t[^>]*>(.*?)</w:t>")

            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xml = zip.bufferedReader().readText()
                    val paragraphs = pRegex.findAll(xml)
                    for (p in paragraphs) {
                        val pText = tRegex.findAll(p.value).map { it.groupValues[1] }.joinToString("")
                        if (pText.isNotBlank()) {
                            sb.append(unescapeXml(pText)).append("\n\n")
                            if (sb.length >= maxChars) break
                        }
                    }
                    break
                }
                entry = zip.nextEntry
            }
            if (sb.isNotEmpty()) sb.toString().take(maxChars) else "No readable text found in Word document."
        } catch (e: Exception) {
            "Unable to parse DOCX text: ${e.localizedMessage}"
        }
    }

    /**
     * Parses ppt/slides/slide*.xml inside a PPTX file to extract slide texts.
     */
    fun extractPptxText(inputStream: InputStream, maxChars: Int = 50000): String {
        return try {
            val zip = ZipInputStream(inputStream)
            var entry = zip.nextEntry
            val slides = mutableListOf<String>()
            var slideIndex = 1
            val tRegex = Regex("<a:t[^>]*>(.*?)</a:t>")

            while (entry != null) {
                if (entry.name.startsWith("ppt/slides/slide") && entry.name.endsWith(".xml")) {
                    val xml = zip.bufferedReader().readText()
                    val textParts = tRegex.findAll(xml)
                        .map { unescapeXml(it.groupValues[1]) }
                        .filter { it.isNotBlank() }
                        .toList()
                    if (textParts.isNotEmpty()) {
                        slides.add("--- Slide $slideIndex ---\n" + textParts.joinToString("\n"))
                        slideIndex++
                    }
                }
                entry = zip.nextEntry
            }
            if (slides.isNotEmpty()) slides.joinToString("\n\n").take(maxChars) else "No readable text found in PowerPoint presentation."
        } catch (e: Exception) {
            "Unable to parse PPTX text: ${e.localizedMessage}"
        }
    }

    private fun unescapeXml(text: String): String {
        return text.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }

    /**
     * Resolves local file access (URI string and local file path) for an IntelliSpace document.
     * 1. Checks matching local file from device.
     * 2. Checks local file at doc.file_path.
     * 3. Checks app cacheDir for existing downloaded/cached copy.
     * 4. Downloads from remote server into cacheDir if remote URL is available.
     * 5. If offline or download fails, writes doc.content_text into cacheDir so file can still be opened and viewed.
     */
    suspend fun resolveVaultDocumentAccess(
        context: Context,
        doc: DocumentDto,
        deviceFiles: List<LocalFileItem>
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        // 1. Try local match from device files
        val localMatch = deviceFiles.find {
            it.title.equals(doc.title, ignoreCase = true) ||
            it.title.substringBeforeLast('.').equals(doc.title.substringBeforeLast('.'), ignoreCase = true) ||
            (!doc.file_path.isNullOrBlank() && it.path == doc.file_path)
        }
        if (localMatch != null && (localMatch.uri != Uri.EMPTY || File(localMatch.path).exists())) {
            return@withContext Pair(localMatch.uri.toString(), localMatch.path)
        }

        // 2. Check if a local file already exists at doc.file_path directly
        if (!doc.file_path.isNullOrBlank()) {
            val directFile = File(doc.file_path)
            if (directFile.exists() && directFile.canRead()) {
                return@withContext Pair("", directFile.absolutePath)
            }
        }

        // 3. Check if cached in app cacheDir
        val safeFileName = "vault_${doc.id}_" + doc.title.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val cachedFile = File(context.cacheDir, safeFileName)
        if (cachedFile.exists() && cachedFile.length() > 0) {
            return@withContext Pair("", cachedFile.absolutePath)
        }

        // 4. Download from remote URL if available
        val remoteUrl = NetworkConfig.resolveMediaUrl(doc.file_url ?: doc.file_path)
        if (!remoteUrl.isNullOrBlank()) {
            try {
                val url = java.net.URL(remoteUrl)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 15000
                conn.requestMethod = "GET"
                conn.instanceFollowRedirects = true
                if (conn.responseCode in 200..299) {
                    conn.inputStream.use { input ->
                        FileOutputStream(cachedFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (cachedFile.exists() && cachedFile.length() > 0) {
                        return@withContext Pair("", cachedFile.absolutePath)
                    }
                }
            } catch (_: Exception) {}
        }

        // 5. Fallback: if document has extracted text content, create a viewable text file
        if (!doc.content_text.isNullOrBlank()) {
            try {
                val textFileName = "vault_${doc.id}_${doc.title.substringBeforeLast('.')}.txt"
                val textFile = File(context.cacheDir, textFileName)
                textFile.writeText(doc.content_text)
                return@withContext Pair("", textFile.absolutePath)
            } catch (_: Exception) {}
        }

        Pair("", doc.file_path ?: "")
    }
}
