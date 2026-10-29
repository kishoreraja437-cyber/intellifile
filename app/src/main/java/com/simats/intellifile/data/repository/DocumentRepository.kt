package com.simats.intellifile.data.repository

import android.content.Context
import android.net.Uri
import com.simats.intellifile.data.api.IntelliFileApiService
import com.simats.intellifile.data.api.RetrofitClient
import com.simats.intellifile.data.manager.LocalFileManager
import com.simats.intellifile.data.model.*
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
     * Uploads a local file to the server's AI Vault.
     * Upload occurs ONLY after the user explicitly triggers "Add to AI Vault".
     */
    suspend fun uploadToVault(
        filePath: String,
        uriString: String,
        fileName: String,
        category: String? = null,
        priority: String? = null
    ): Result<DocumentDto> {
        return try {
            // Resolve file for upload
            val uploadFile: File = if (filePath.isNotBlank() && File(filePath).exists()) {
                File(filePath)
            } else if (uriString.isNotBlank()) {
                // Copy stream to cache for upload
                val uri = Uri.parse(uriString)
                val tempFile = File(context.cacheDir, fileName.ifBlank { "upload_doc" })
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile
            } else {
                return Result.failure(Exception("Cannot locate file content for upload."))
            }

            val mimeType = LocalFileManager.resolveMimeType(fileName)
            val requestFile = uploadFile.asRequestBody(mimeType.toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("document", fileName, requestFile)

            val categoryPart = category?.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaTypeOrNull())
            val priorityPart = priority?.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = api.uploadDocument(filePart, categoryPart, priorityPart)
            if (response.isSuccessful && response.body()?.success == true) {
                val doc = response.body()?.document ?: return Result.failure(Exception("Upload completed but no document returned"))
                Result.success(doc)
            } else {
                val msg = response.body()?.message ?: response.errorBody()?.string() ?: "Upload failed"
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(e)
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
}
