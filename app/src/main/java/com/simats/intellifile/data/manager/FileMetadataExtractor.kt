package com.simats.intellifile.data.manager

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.simats.intellifile.data.local.LocalFileDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipInputStream

data class ExtractedFileDetails(
    val format: String,
    val formattedSize: String,
    val countLabel: String,
    val countValue: String,
    val dateAddedText: String,
    val lastOpenedText: String
)

object FileMetadataExtractor {

    /**
     * Resolves the actual detected file format from the filename or MIME type.
     */
    fun resolveFormat(fileName: String, fileType: String? = null): String {
        val ext = fileName.substringAfterLast('.', "").uppercase().trim()
        if (ext.isNotBlank()) {
            return when (ext) {
                "PDF" -> "PDF"
                "PPTX" -> "PPTX"
                "PPT" -> "PPT"
                "DOCX" -> "DOCX"
                "DOC" -> "DOC"
                "XLSX" -> "XLSX"
                "XLS" -> "XLS"
                "CSV" -> "CSV"
                "TSV" -> "TSV"
                "TXT" -> "TXT"
                "RTF" -> "RTF"
                "ODT" -> "ODT"
                "ODP" -> "ODP"
                "ODS" -> "ODS"
                "JSON" -> "JSON"
                "XML" -> "XML"
                "HTML", "HTM" -> "HTML"
                "MD" -> "MD"
                "LOG" -> "LOG"
                "ZIP" -> "ZIP"
                else -> ext
            }
        }
        val cleanType = fileType?.substringBefore(" ")?.uppercase()?.trim()
        if (!cleanType.isNullOrBlank() && cleanType != "DOCUMENT" && cleanType != "UNKNOWN") {
            return cleanType
        }
        val mime = LocalFileManager.resolveMimeType(fileName)
        return LocalFileManager.resolveFormat(fileName, mime)
    }

    /**
     * Retrieves the format-specific count label.
     */
    fun getCountLabel(format: String): String {
        return when (format.uppercase()) {
            "PDF" -> "Total Pages"
            "PPT", "PPTX", "PPS", "PPSX", "POT", "POTX" -> "Total Slides"
            "DOC", "DOCX", "ODT", "RTF" -> "Total Pages"
            "XLS", "XLSX", "ODS" -> "Total Sheets"
            "CSV", "TSV" -> "Total Rows"
            "TXT", "LOG", "MD" -> "Total Lines"
            else -> "Total Pages"
        }
    }

    /**
     * Formats a millisecond timestamp for "Date Added to Device" (date only).
     */
    fun formatDateAdded(timeMs: Long): String {
        if (timeMs <= 0L) return "Not available"
        return SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timeMs))
    }

    /**
     * Formats a millisecond timestamp for "Last Opened" (date and time).
     */
    fun formatLastOpened(timeMs: Long): String {
        if (timeMs <= 0L) return "Never opened"
        return SimpleDateFormat("MMM dd, yyyy, hh:mm a", Locale.getDefault()).format(Date(timeMs))
    }

    /**
     * Extracts actual file size in human-readable format.
     */
    fun extractSize(
        context: Context,
        path: String,
        uriString: String,
        fileName: String,
        fallbackSize: String
    ): String {
        if (path.isNotBlank() && path != "Internal Storage") {
            try {
                val f = File(path)
                if (f.exists() && f.length() > 0L) {
                    return LocalFileManager.formatFileSize(f.length())
                }
            } catch (_: Exception) {}
        }
        if (uriString.isNotBlank()) {
            try {
                val uri = Uri.parse(uriString)
                context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx != -1) {
                            val size = cursor.getLong(sizeIdx)
                            if (size > 0L) return LocalFileManager.formatFileSize(size)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        if (fileName.isNotBlank()) {
            try {
                val contentUri = MediaStore.Files.getContentUri("external")
                context.contentResolver.query(
                    contentUri,
                    arrayOf(MediaStore.Files.FileColumns.SIZE),
                    "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?",
                    arrayOf(fileName),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                        if (sizeIdx != -1) {
                            val size = cursor.getLong(sizeIdx)
                            if (size > 0L) return LocalFileManager.formatFileSize(size)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return if (fallbackSize.isNotBlank() && fallbackSize != "0 KB") fallbackSize else "Not available"
    }

    /**
     * Dynamically calculates content count (pages, slides, sheets, rows, or lines)
     * using format-specific extraction.
     */
    fun extractContentCount(
        context: Context,
        format: String,
        path: String,
        uriString: String,
        fileName: String
    ): Pair<String, String> {
        val label = getCountLabel(format)
        val upper = format.uppercase()

        val count: Int? = when (upper) {
            "PDF" -> getPdfPageCount(context, path, uriString)
            "PPTX", "PPSX", "POTX" -> getPptxSlideCount(context, path, uriString)
            "PPT" -> getPptSlideCount(context, path, uriString, fileName)
            "DOCX" -> getDocxPageCount(context, path, uriString)
            "DOC" -> null // Legacy DOC binary pagination is not reliably available without a printer engine
            "XLSX" -> getXlsxSheetCount(context, path, uriString)
            "XLS" -> getXlsSheetCount(context, path, uriString, fileName)
            "CSV", "TSV" -> getCsvRowCount(context, path, uriString)
            "TXT", "LOG", "MD" -> getTextLineCount(context, path, uriString)
            else -> null
        }

        val value = if (count != null && count > 0) "$count" else "Not available"
        return Pair(label, value)
    }

    private fun getInputStream(context: Context, path: String, uriString: String): InputStream? {
        if (path.isNotBlank() && path != "Internal Storage") {
            try {
                val f = File(path)
                if (f.exists() && f.canRead()) {
                    return FileInputStream(f)
                }
            } catch (_: Exception) {}
        }
        if (uriString.isNotBlank()) {
            try {
                val uri = Uri.parse(uriString)
                val stream = context.contentResolver.openInputStream(uri)
                if (stream != null) return stream
            } catch (_: Exception) {}
        }
        return null
    }

    private fun getFileDescriptor(context: Context, path: String, uriString: String): ParcelFileDescriptor? {
        if (path.isNotBlank() && path != "Internal Storage") {
            try {
                val f = File(path)
                if (f.exists() && f.canRead()) {
                    return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                }
            } catch (_: Exception) {}
        }
        if (uriString.isNotBlank()) {
            try {
                val uri = Uri.parse(uriString)
                return context.contentResolver.openFileDescriptor(uri, "r")
            } catch (_: Exception) {}
        }
        return null
    }

    private fun getPdfPageCount(context: Context, path: String, uriString: String): Int? {
        val pfd = getFileDescriptor(context, path, uriString) ?: return null
        return try {
            pfd.use {
                val renderer = PdfRenderer(it)
                val count = renderer.pageCount
                renderer.close()
                count
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getPptxSlideCount(context: Context, path: String, uriString: String): Int? {
        val inStream = getInputStream(context, path, uriString) ?: return null
        return try {
            inStream.use { raw ->
                BufferedInputStream(raw).use { buf ->
                    ZipInputStream(buf).use { zip ->
                        var count = 0
                        val regex = Regex("(?i)ppt/slides/slide[0-9]+\\.xml$")
                        var entry = zip.nextEntry
                        while (entry != null) {
                            val name = entry.name.replace('\\', '/')
                            if (regex.containsMatchIn(name)) {
                                count++
                            }
                            entry = zip.nextEntry
                        }
                        if (count > 0) count else null
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getPptSlideCount(context: Context, path: String, uriString: String, fileName: String): Int? {
        val inStream = getInputStream(context, path, uriString) ?: return null
        return try {
            val bytes = inStream.use { it.readBytes() }
            val presentation = DocumentContentParser.parsePpt(bytes, fileName)
            val count = presentation?.totalSlides ?: 0
            if (count > 0) count else null
        } catch (_: Exception) {
            null
        }
    }

    private fun getDocxPageCount(context: Context, path: String, uriString: String): Int? {
        val inStream = getInputStream(context, path, uriString) ?: return null
        return try {
            inStream.use { raw ->
                BufferedInputStream(raw).use { buf ->
                    ZipInputStream(buf).use { zip ->
                        var appXmlPages: Int? = null
                        var pageBreaks = 0
                        var hasDocumentXml = false

                        var entry = zip.nextEntry
                        while (entry != null) {
                            val name = entry.name.replace('\\', '/').lowercase()
                            if (name == "docprops/app.xml") {
                                val text = readEntryString(zip)
                                val match = Regex("<Pages>([0-9]+)</Pages>").find(text)
                                val p = match?.groupValues?.get(1)?.toIntOrNull()
                                if (p != null && p > 0) {
                                    appXmlPages = p
                                }
                            } else if (name == "word/document.xml") {
                                hasDocumentXml = true
                                val text = readEntryString(zip)
                                val count1 = Regex("<w:br[^>]*w:type=\"page\"").findAll(text).count()
                                val count2 = Regex("<w:lastRenderedPageBreak").findAll(text).count()
                                val breaks = maxOf(count1, count2)
                                if (breaks > 0) {
                                    pageBreaks = breaks + 1
                                }
                            }
                            entry = zip.nextEntry
                        }
                        appXmlPages ?: if (pageBreaks > 0) pageBreaks else if (hasDocumentXml) 1 else null
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getXlsxSheetCount(context: Context, path: String, uriString: String): Int? {
        val inStream = getInputStream(context, path, uriString) ?: return null
        return try {
            inStream.use { raw ->
                BufferedInputStream(raw).use { buf ->
                    ZipInputStream(buf).use { zip ->
                        var count = 0
                        val regex = Regex("(?i)xl/worksheets/sheet[0-9]+\\.xml$")
                        var entry = zip.nextEntry
                        while (entry != null) {
                            val name = entry.name.replace('\\', '/')
                            if (regex.containsMatchIn(name)) {
                                count++
                            }
                            entry = zip.nextEntry
                        }
                        if (count > 0) count else null
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getXlsSheetCount(context: Context, path: String, uriString: String, fileName: String): Int? {
        val inStream = getInputStream(context, path, uriString) ?: return null
        return try {
            inStream.close()
            val parsed = DocumentContentParser.parse(context, uriString, path, "XLS", fileName)
            if (parsed is ParsedDocumentContent.Spreadsheet && parsed.sheets.isNotEmpty()) {
                parsed.sheets.size
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getCsvRowCount(context: Context, path: String, uriString: String): Int? {
        val inStream = getInputStream(context, path, uriString) ?: return null
        return try {
            inStream.use { raw ->
                BufferedReader(InputStreamReader(raw, StandardCharsets.UTF_8)).use { reader ->
                    var count = 0
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (!line.isNullOrBlank()) {
                            count++
                        }
                    }
                    if (count > 0) count else null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getTextLineCount(context: Context, path: String, uriString: String): Int? {
        val inStream = getInputStream(context, path, uriString) ?: return null
        return try {
            inStream.use { raw ->
                BufferedReader(InputStreamReader(raw, StandardCharsets.UTF_8)).use { reader ->
                    var count = 0
                    while (reader.readLine() != null) {
                        count++
                    }
                    if (count > 0) count else null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readEntryString(zip: ZipInputStream): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var len: Int
        while (zip.read(buffer).also { len = it } > 0) {
            out.write(buffer, 0, len)
        }
        return out.toString(StandardCharsets.UTF_8.name())
    }

    /**
     * Retrieves the original Date Added from Android MediaStore.
     * Never uses modified date or creation date as a substitute.
     * Never replaces historical date with today's date.
     * If not found, returns "Not available".
     */
    fun extractDateAdded(
        context: Context,
        path: String,
        uriString: String,
        fileName: String
    ): String {
        val dbHelper = LocalFileDatabaseHelper.getInstance(context)
        val key = if (path.isNotBlank() && path != "Internal Storage") path else uriString

        // Check local database cache first
        val cached = dbHelper.getDateAdded(key, fileName)
        if (cached != null && cached > 0L) {
            return formatDateAdded(cached)
        }

        // 1. Query by URI if it is a content URI
        if (uriString.isNotBlank() && uriString.startsWith("content://")) {
            try {
                val uri = Uri.parse(uriString)
                context.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns.DATE_ADDED),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val colIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                        if (colIdx != -1) {
                            val sec = cursor.getLong(colIdx)
                            if (sec > 0L) {
                                val ms = sec * 1000L
                                dbHelper.setDateAdded(key, fileName, ms)
                                return formatDateAdded(ms)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Query MediaStore by DATA = path
        if (path.isNotBlank() && path.startsWith("/")) {
            try {
                val contentUri = MediaStore.Files.getContentUri("external")
                context.contentResolver.query(
                    contentUri,
                    arrayOf(MediaStore.MediaColumns.DATE_ADDED),
                    "${MediaStore.Files.FileColumns.DATA} = ?",
                    arrayOf(path),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val colIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                        if (colIdx != -1) {
                            val sec = cursor.getLong(colIdx)
                            if (sec > 0L) {
                                val ms = sec * 1000L
                                dbHelper.setDateAdded(key, fileName, ms)
                                return formatDateAdded(ms)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Query MediaStore by DISPLAY_NAME = fileName
        if (fileName.isNotBlank()) {
            try {
                val contentUri = MediaStore.Files.getContentUri("external")
                context.contentResolver.query(
                    contentUri,
                    arrayOf(MediaStore.MediaColumns.DATE_ADDED),
                    "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?",
                    arrayOf(fileName),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val colIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                        if (colIdx != -1) {
                            val sec = cursor.getLong(colIdx)
                            if (sec > 0L) {
                                val ms = sec * 1000L
                                dbHelper.setDateAdded(key, fileName, ms)
                                return formatDateAdded(ms)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return "Not available"
    }

    /**
     * Retrieves the last opened timestamp from the local persistent database.
     * If the file was never opened in IntelliFile, returns "Never opened".
     */
    fun extractLastOpened(
        context: Context,
        path: String,
        uriString: String,
        fileName: String
    ): String {
        val dbHelper = LocalFileDatabaseHelper.getInstance(context)
        val key = if (path.isNotBlank() && path != "Internal Storage") path else uriString
        val ts = dbHelper.getLastOpened(key, fileName)
        return if (ts != null && ts > 0L) {
            formatLastOpened(ts)
        } else {
            "Never opened"
        }
    }

    /**
     * Asynchronously extracts all 5 metadata fields without blocking the main UI thread.
     */
    suspend fun extractDetails(
        context: Context,
        fileName: String,
        filePath: String,
        fileUriString: String,
        fallbackFileSize: String = "0 KB",
        fallbackFormat: String = "PDF"
    ): ExtractedFileDetails = withContext(Dispatchers.IO) {
        val dbHelper = LocalFileDatabaseHelper.getInstance(context)
        val key = if (filePath.isNotBlank() && filePath != "Internal Storage") filePath else fileUriString

        val format = resolveFormat(fileName, fallbackFormat)
        val size = extractSize(context, filePath, fileUriString, fileName, fallbackFileSize)
        val expectedLabel = getCountLabel(format)

        val cachedCount = dbHelper.getCachedContentCount(key, fileName)
        val (countLabel, countValue) = if (cachedCount != null && cachedCount.first == expectedLabel) {
            cachedCount
        } else {
            val calculated = extractContentCount(context, format, filePath, fileUriString, fileName)
            dbHelper.setCachedContentCount(key, fileName, calculated.first, calculated.second)
            calculated
        }

        val dateAdded = extractDateAdded(context, filePath, fileUriString, fileName)
        val lastOpened = extractLastOpened(context, filePath, fileUriString, fileName)

        ExtractedFileDetails(
            format = format,
            formattedSize = size,
            countLabel = countLabel,
            countValue = countValue,
            dateAddedText = dateAdded,
            lastOpenedText = lastOpened
        )
    }
}
