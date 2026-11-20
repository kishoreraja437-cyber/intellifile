package com.simats.intellifile.data.repository

import android.content.Context
import android.net.Uri
import com.simats.intellifile.data.api.IntelliFileApiService
import com.simats.intellifile.data.api.NetworkConfig
import com.simats.intellifile.data.api.RetrofitClient
import com.simats.intellifile.data.manager.LocalFileManager
import com.simats.intellifile.data.model.*
import com.simats.intellifile.data.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream


class DocumentRepository(private val context: Context) {
    private val api: IntelliFileApiService
        get() = RetrofitClient.getApiService(context)

    /**
     * Uploads a local file to the server's IntelliSpace.
     * Upload occurs ONLY after the user explicitly triggers "Add to IntelliSpace".
     */
    suspend fun uploadToVault(
        filePath: String,
        uriString: String,
        fileName: String,
        category: String? = null,
        priority: String? = null
    ): Result<DocumentDto> {
        val safeFileName = fileName.substringAfterLast('/').substringAfterLast('\\').trim().ifBlank { "document" }
        val cleanName = safeFileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val tempFile = File(context.cacheDir, "vault_upload_${System.currentTimeMillis()}_$cleanName")

        return try {
            var streamOpened = false

            // Strategy 1: ContentResolver via uriString (safest across all Android versions)
            if (uriString.isNotBlank()) {
                try {
                    val uri = Uri.parse(uriString)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                            streamOpened = true
                        }
                    }
                } catch (_: Exception) {}
            }

            // Strategy 2: Direct file copy if accessible
            if ((!streamOpened || tempFile.length() <= 0L) && filePath.isNotBlank() && filePath != "Internal Storage") {
                try {
                    val directFile = File(filePath)
                    if (directFile.exists() && directFile.canRead()) {
                        directFile.inputStream().use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                                streamOpened = true
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // Strategy 3: MediaStore lookup matching fileName or path
            if (!streamOpened || tempFile.length() <= 0L) {
                try {
                    val deviceFiles = LocalFileManager.getDeviceFiles(context)
                    val matchedItem = deviceFiles.firstOrNull {
                        it.title.equals(fileName, ignoreCase = true) ||
                        it.title.equals(safeFileName, ignoreCase = true) ||
                        (filePath.isNotBlank() && it.path == filePath) ||
                        (uriString.isNotBlank() && it.uri.toString() == uriString)
                    }
                    if (matchedItem != null && matchedItem.uri != Uri.EMPTY) {
                        context.contentResolver.openInputStream(matchedItem.uri)?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                                streamOpened = true
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            if (!streamOpened || !tempFile.exists() || tempFile.length() <= 0L) {
                return Result.failure(Exception("Cannot read file content. Please check that storage access is granted."))
            }

            val mimeType = LocalFileManager.resolveMimeType(safeFileName)
            val requestFile = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("document", safeFileName, requestFile)

            val sessionManager = SessionManager(context)
            var userId = sessionManager.getUserId()
            if (userId <= 0) {
                userId = sessionManager.getCachedProfile()?.id ?: 1
            }
            val userIdPart = userId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            val titlePart = safeFileName.toRequestBody("text/plain".toMediaTypeOrNull())
            val categoryPart = (category?.takeIf { it.isNotBlank() } ?: "Document").toRequestBody("text/plain".toMediaTypeOrNull())
            val priorityPart = priority?.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = api.uploadDocument(
                document = filePart,
                category = categoryPart,
                priority = priorityPart,
                title = titlePart,
                userId = userIdPart
            )
            if (response.isSuccessful && response.body()?.success == true) {
                val doc = response.body()?.document ?: return Result.failure(Exception("Upload completed but no document returned"))
                Result.success(doc)
            } else {
                val msg = response.body()?.message ?: response.errorBody()?.string() ?: "Upload failed"
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun getDocuments(
        category: String? = null,
        format: String? = null,
        priority: String? = null,
        search: String? = null
    ): Result<List<DocumentDto>> {
        return try {
            val response = api.getDocuments(category, format, priority, search)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()?.documents ?: emptyList())
            } else {
                Result.failure(Exception(response.body()?.message ?: "Failed to load documents"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRecentDocuments(limit: Int = 10): Result<List<DocumentDto>> {
        return try {
            val response = api.getRecentDocuments(limit)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()?.documents ?: emptyList())
            } else {
                Result.failure(Exception(response.body()?.message ?: "Failed to load recent documents"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSavedDocuments(priority: String = "ALL"): Result<List<DocumentDto>> {
        return try {
            val response = api.getSavedDocuments(priority)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()?.documents ?: emptyList())
            } else {
                Result.failure(Exception(response.body()?.message ?: "Failed to load saved documents"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDocumentDetail(id: Int): Result<DocumentDto> {
        return try {
            val response = api.getDocumentDetail(id)
            if (response.isSuccessful && response.body()?.success == true) {
                val doc = response.body()?.document ?: return Result.failure(Exception("Document not found"))
                Result.success(doc)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Failed to fetch document details"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setPriority(documentId: Int, priority: String): Result<Boolean> {
        return try {
            val response = api.setPriority(SetPriorityRequest(documentId, priority))
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(true)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Failed to update priority"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun bookmarkDocument(documentId: Int, isBookmarked: Boolean): Result<Boolean> {
        return try {
            val response = api.bookmarkDocument(BookmarkRequest(documentId, if (isBookmarked) 1 else 0))
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(true)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Failed to update bookmark"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteDocument(documentId: Int): Result<Boolean> {
        return try {
            val response = api.deleteDocument(DeleteDocumentRequest(documentId))
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(true)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Failed to delete document"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStatistics(): Result<StatisticsDto> {
        return try {
            val response = api.getStatistics()
            if (response.isSuccessful && response.body()?.success == true) {
                val stats = response.body()?.statistics ?: StatisticsDto()
                Result.success(stats)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Failed to fetch statistics"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun shareDocument(documentId: Int, recipientEmail: String, permission: String = "View Only"): Result<String> {
        return try {
            val response = api.shareDocument(ShareDocumentRequest(documentId, recipientEmail, permission))
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()?.share_url ?: "Shared successfully")
            } else {
                Result.failure(Exception(response.body()?.message ?: "Failed to share document"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Converts a PowerPoint (PPT/PPTX) presentation into PDF via LibreOffice backend.
     * Caches the converted PDF locally in cacheDir/converted_ppt/ so subsequent opens are instant.
     */
    suspend fun convertPresentationToPdf(
        documentId: Int? = null,
        filePath: String? = null,
        uriString: String? = null,
        fileName: String
    ): Result<File> = withContext(Dispatchers.IO) {
        val safeFileName = fileName.substringAfterLast('/').substringAfterLast('\\').trim().ifBlank { "presentation.pptx" }
        val ext = safeFileName.substringAfterLast('.', "").lowercase()
        if (ext !in listOf("ppt", "pptx", "odp", "pps", "ppsx")) {
            return@withContext Result.failure(Exception("Unsupported presentation extension: $ext"))
        }

        val cacheSubDir = File(context.cacheDir, "converted_ppt").apply { mkdirs() }
        var tempSourceFile: File? = null

        try {
            // Case 1: If documentId is available, request conversion by documentId
            if (documentId != null && documentId > 0) {
                val resp = api.convertPptDocument(documentId)
                if (resp.isSuccessful && resp.body()?.success == true) {
                    val body = resp.body()!!
                    val pdfUrl = NetworkConfig.resolveMediaUrl(body.pdfUrl) ?: (NetworkConfig.getBaseUrl() + (body.pdfPath ?: ""))
                    val targetPdfFile = File(cacheSubDir, "doc_${documentId}_${body.totalPages ?: 0}.pdf")
                    if (targetPdfFile.exists() && targetPdfFile.length() > 1024) {
                        return@withContext Result.success(targetPdfFile)
                    }
                    val downloaded = downloadPdfToCache(pdfUrl, targetPdfFile)
                    if (downloaded && targetPdfFile.exists() && targetPdfFile.length() > 1024) {
                        return@withContext Result.success(targetPdfFile)
                    }
                }
            }

            // Case 2: If filePath points to a server-side document (starts with uploads/ or contains /intellifile_backend/uploads/)
            if (!filePath.isNullOrBlank() && (filePath.contains("uploads/") || filePath.contains("intellifile_backend/"))) {
                val serverRelPath = filePath.substringAfter("intellifile_backend/", filePath).trimStart('/')
                val resp = api.convertPptByPath(serverRelPath)
                if (resp.isSuccessful && resp.body()?.success == true) {
                    val body = resp.body()!!
                    val pdfUrl = NetworkConfig.resolveMediaUrl(body.pdfUrl) ?: (NetworkConfig.getBaseUrl() + (body.pdfPath ?: ""))
                    val cacheKey = Integer.toHexString(serverRelPath.hashCode())
                    val targetPdfFile = File(cacheSubDir, "path_${cacheKey}.pdf")
                    if (targetPdfFile.exists() && targetPdfFile.length() > 1024) {
                        return@withContext Result.success(targetPdfFile)
                    }
                    val downloaded = downloadPdfToCache(pdfUrl, targetPdfFile)
                    if (downloaded && targetPdfFile.exists() && targetPdfFile.length() > 1024) {
                        return@withContext Result.success(targetPdfFile)
                    }
                }
            }

            // Case 3: Local file on device - resolve source file and calculate MD5
            tempSourceFile = resolveLocalSourceFile(context, uriString, filePath, safeFileName)
            if (tempSourceFile == null || !tempSourceFile.exists() || tempSourceFile.length() <= 0L) {
                return@withContext Result.failure(Exception("Cannot read presentation file to convert."))
            }

            val fileHash = computeFileMd5(tempSourceFile) ?: (safeFileName.hashCode().toString() + "_" + tempSourceFile.length())
            val targetPdfFile = File(cacheSubDir, "${fileHash}.pdf")

            // If locally cached from a previous session, return immediately!
            if (targetPdfFile.exists() && targetPdfFile.length() > 1024) {
                return@withContext Result.success(targetPdfFile)
            }

            // Upload presentation to convert_ppt.php endpoint
            val mimeType = if (ext == "ppt") "application/vnd.ms-powerpoint" else "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            val requestFile = tempSourceFile.asRequestBody(mimeType.toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", safeFileName, requestFile)

            val resp = api.convertPptFile(filePart)
            if (resp.isSuccessful && resp.body()?.success == true) {
                val body = resp.body()!!
                val pdfUrl = NetworkConfig.resolveMediaUrl(body.pdfUrl) ?: (NetworkConfig.getBaseUrl() + (body.pdfPath ?: ""))
                val downloaded = downloadPdfToCache(pdfUrl, targetPdfFile)
                if (downloaded && targetPdfFile.exists() && targetPdfFile.length() > 1024) {
                    return@withContext Result.success(targetPdfFile)
                } else {
                    return@withContext Result.failure(Exception("Failed to download converted PDF from server."))
                }
            } else {
                val errMsg = resp.body()?.message ?: resp.errorBody()?.string() ?: "Server PPT conversion failed"
                return@withContext Result.failure(Exception(errMsg))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        } finally {
            try {
                // If temporary resolved file was created in cache, clean it up
                if (tempSourceFile != null && tempSourceFile.name.startsWith("ppt_convert_src_")) {
                    tempSourceFile.delete()
                }
            } catch (_: Exception) {}
        }
    }

    private fun downloadPdfToCache(url: String, targetFile: File): Boolean {
        return try {
            val client = RetrofitClient.getOkHttpClient(context)
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("Accept", "application/pdf, */*")
                .header("ngrok-skip-browser-warning", "true")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val body = response.body ?: return false
                val tempOut = File(targetFile.parentFile, targetFile.name + ".tmp")
                body.byteStream().use { input ->
                    FileOutputStream(tempOut).use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempOut.exists() && tempOut.length() > 0) {
                    if (targetFile.exists()) targetFile.delete()
                    tempOut.renameTo(targetFile)
                    true
                } else {
                    tempOut.delete()
                    false
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DocumentRepository", "Error downloading converted PDF: ${e.message}", e)
            false
        }
    }

    private fun computeFileMd5(file: File): String? {
        return try {
            val md = java.security.MessageDigest.getInstance("MD5")
            file.inputStream().use { stream ->
                val buffer = ByteArray(8192)
                var read: Int
                while (stream.read(buffer).also { read = it } > 0) {
                    md.update(buffer, 0, read)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveLocalSourceFile(
        context: Context,
        uriString: String?,
        filePath: String?,
        fileName: String
    ): File? {
        // Direct accessible file
        if (!filePath.isNullOrBlank() && filePath != "Internal Storage") {
            val f = File(filePath)
            if (f.exists() && f.canRead() && f.length() > 0L) {
                return f
            }
        }

        val tempFile = File(context.cacheDir, "ppt_convert_src_${System.currentTimeMillis()}_$fileName")
        var copied = false

        if (!uriString.isNullOrBlank()) {
            try {
                val uri = Uri.parse(uriString)
                context.contentResolver.openInputStream(uri)?.use { inStream ->
                    FileOutputStream(tempFile).use { outStream ->
                        inStream.copyTo(outStream)
                        copied = true
                    }
                }
            } catch (_: Exception) {}
        }

        if (!copied) {
            try {
                val deviceFiles = LocalFileManager.getDeviceFiles(context)
                val matched = deviceFiles.firstOrNull {
                    it.title.equals(fileName, ignoreCase = true) ||
                    (filePath != null && it.path == filePath) ||
                    (uriString != null && it.uri.toString() == uriString)
                }
                if (matched != null && matched.uri != Uri.EMPTY) {
                    context.contentResolver.openInputStream(matched.uri)?.use { inStream ->
                        FileOutputStream(tempFile).use { outStream ->
                            inStream.copyTo(outStream)
                            copied = true
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return if (copied && tempFile.exists() && tempFile.length() > 0) tempFile else null
    }
}
