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
     * Shares a file directly to a specific target app (WhatsApp, Gmail, Telegram, Bluetooth)
     * or falls back to system chooser if not installed.
     */
    fun shareFileToTarget(context: Context, fileItem: LocalFileItem, targetName: String) {
        val packageName = when (targetName.lowercase()) {
            "whatsapp" -> "com.whatsapp"
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
        if (uriString.isNotBlank()) {
            try {
                val uri = Uri.parse(uriString)
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) return pfd
            } catch (_: Exception) {}
        }

        if (path.isNotBlank()) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    if (pfd != null) return pfd
                }
            } catch (_: Exception) {}

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
     */
    fun openInputStream(context: Context, uriString: String, path: String): InputStream? {
        if (uriString.isNotBlank()) {
            try {
                val uri = Uri.parse(uriString)
                val stream = context.contentResolver.openInputStream(uri)
                if (stream != null) return stream
            } catch (_: Exception) {}
        }

        if (path.isNotBlank()) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    return file.inputStream()
                }
            } catch (_: Exception) {}
        }

        return null
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
                "TXT", "CSV", "JSON", "MD" -> {
                    stream.bufferedReader().use { reader ->
                        val buffer = CharArray(maxChars)
                        val read = reader.read(buffer, 0, maxChars)
                        if (read > 0) String(buffer, 0, read) else ""
                    }
                }
                "DOCX" -> extractDocxText(stream, maxChars)
                "PPTX" -> extractPptxText(stream, maxChars)
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
}
