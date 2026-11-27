package com.simats.intellifile.data.manager

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import com.simats.intellifile.data.local.LocalFileDatabaseHelper
import com.simats.intellifile.data.model.ExtractedFolderRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

object ZipExtractor {

    data class ZipExtractProgress(
        val currentFileName: String,
        val extractedCount: Int,
        val totalCount: Int,
        val percentage: Int
    )

    data class ZipExtractResult(
        val success: Boolean,
        val extractedFolder: File? = null,
        val totalFiles: Int = 0,
        val totalSubfolders: Int = 0,
        val totalBytes: Long = 0L,
        val errorMessage: String? = null
    )

    data class ZipQuickInfo(
        val isValid: Boolean,
        val totalEntries: Int,
        val isEncrypted: Boolean,
        val estimatedSize: Long,
        val errorMessage: String? = null
    )

    /**
     * Resolves an InputStream for a given path or content Uri.
     */
    private fun openZipInputStream(context: Context, zipUriOrPath: String): InputStream {
        return if (zipUriOrPath.startsWith("content://")) {
            val uri = Uri.parse(zipUriOrPath)
            context.contentResolver.openInputStream(uri)
                ?: throw IOException("Unable to open input stream for content URI: $zipUriOrPath")
        } else {
            val file = File(zipUriOrPath)
            if (!file.exists() || !file.canRead()) {
                // Try fallback to content URI parsing if looks like a URI
                if (zipUriOrPath.contains(":")) {
                    context.contentResolver.openInputStream(Uri.parse(zipUriOrPath))
                        ?: throw FileNotFoundException("ZIP file not found at path: $zipUriOrPath")
                } else {
                    throw FileNotFoundException("ZIP file not found or inaccessible: $zipUriOrPath")
                }
            } else {
                FileInputStream(file)
            }
        }
    }

    /**
     * Inspects a ZIP archive to quickly count entries, check encryption, and estimate uncompressed size.
     */
    suspend fun inspectZip(context: Context, zipUriOrPath: String): ZipQuickInfo = withContext(Dispatchers.IO) {
        var count = 0
        var totalSize = 0L
        var isEncrypted = false

        try {
            openZipInputStream(context, zipUriOrPath).use { rawIn ->
                BufferedInputStream(rawIn).use { bufIn ->
                    ZipInputStream(bufIn).use { zipIn ->
                        var entry: ZipEntry?
                        while (true) {
                            try {
                                entry = zipIn.nextEntry ?: break
                            } catch (ze: ZipException) {
                                val msg = ze.message?.lowercase() ?: ""
                                if (msg.contains("encrypt") || msg.contains("password")) {
                                    return@withContext ZipQuickInfo(
                                        isValid = false,
                                        totalEntries = count,
                                        isEncrypted = true,
                                        estimatedSize = totalSize,
                                        errorMessage = "This ZIP archive is password-protected or encrypted, which is not supported."
                                    )
                                }
                                throw ze
                            }

                            count++
                            if (entry.size > 0) {
                                totalSize += entry.size
                            } else if (entry.compressedSize > 0) {
                                totalSize += entry.compressedSize * 2 // conservative estimate
                            }
                            zipIn.closeEntry()
                        }
                    }
                }
            }
            ZipQuickInfo(
                isValid = count > 0,
                totalEntries = count,
                isEncrypted = isEncrypted,
                estimatedSize = totalSize,
                errorMessage = if (count == 0) "This ZIP archive appears to be empty." else null
            )
        } catch (e: Exception) {
            val msg = e.message?.lowercase() ?: ""
            val isPass = msg.contains("encrypt") || msg.contains("password")
            ZipQuickInfo(
                isValid = false,
                totalEntries = count,
                isEncrypted = isPass,
                estimatedSize = totalSize,
                errorMessage = when {
                    isPass -> "This ZIP archive is password-protected or encrypted, which is not supported."
                    e is EOFException || e is ZipException -> "Invalid or corrupted ZIP archive format."
                    else -> e.message ?: "Failed to read ZIP archive."
                }
            )
        }
    }

    /**
     * Extracts a ZIP archive safely into the target folder.
     */
    suspend fun extractZip(
        context: Context,
        zipUriOrPath: String,
        zipDisplayName: String,
        targetParentDir: File,
        folderName: String,
        createSubfolder: Boolean = true,
        onProgress: (ZipExtractProgress) -> Unit = {}
    ): ZipExtractResult = withContext(Dispatchers.IO) {
        // 1. Resolve Destination Directory
        val cleanFolderName = sanitizeFileName(folderName.ifBlank { zipDisplayName.removeSuffix(".zip").ifBlank { "Extracted_Files" } })
        val destDir = if (createSubfolder) {
            File(targetParentDir, cleanFolderName)
        } else {
            targetParentDir
        }

        if (!destDir.exists() && !destDir.mkdirs()) {
            return@withContext ZipExtractResult(
                success = false,
                errorMessage = "Failed to create destination folder: ${destDir.absolutePath}. Check storage permissions."
            )
        }

        val canonicalDest = try {
            destDir.canonicalFile
        } catch (e: Exception) {
            return@withContext ZipExtractResult(
                success = false,
                errorMessage = "Invalid destination path: ${e.message}"
            )
        }

        // 2. Pre-scan for Total Count & Disk Space Verification
        val inspectInfo = inspectZip(context, zipUriOrPath)
        if (!inspectInfo.isValid && inspectInfo.errorMessage != null && inspectInfo.totalEntries == 0) {
            return@withContext ZipExtractResult(
                success = false,
                errorMessage = inspectInfo.errorMessage
            )
        }

        val totalExpected = if (inspectInfo.totalEntries > 0) inspectInfo.totalEntries else 1
        val freeSpace = destDir.usableSpace
        if (freeSpace > 0 && inspectInfo.estimatedSize > 0 && freeSpace < (inspectInfo.estimatedSize + 5 * 1024 * 1024L)) {
            return@withContext ZipExtractResult(
                success = false,
                errorMessage = "Insufficient storage space. Need approximately ${LocalFileManager.formatFileSize(inspectInfo.estimatedSize)} free."
            )
        }

        var filesExtracted = 0
        var foldersExtracted = 0
        var totalBytesWritten = 0L
        val newlyCreatedFiles = mutableListOf<String>()
        val buffer = ByteArray(16384) // 16KB buffer for fast throughput

        try {
            openZipInputStream(context, zipUriOrPath).use { rawIn ->
                BufferedInputStream(rawIn, 32768).use { bufIn ->
                    ZipInputStream(bufIn).use { zipIn ->
                        var entry: ZipEntry?
                        var entryIndex = 0

                        while (true) {
                            try {
                                entry = zipIn.nextEntry ?: break
                            } catch (ze: ZipException) {
                                val msg = ze.message?.lowercase() ?: ""
                                if (msg.contains("encrypt") || msg.contains("password")) {
                                    return@withContext ZipExtractResult(
                                        success = false,
                                        errorMessage = "This ZIP archive is password-protected or encrypted, which is not supported."
                                    )
                                }
                                throw ze
                            }

                            entryIndex++
                            val rawName = entry.name
                            if (rawName.isBlank()) {
                                zipIn.closeEntry()
                                continue
                            }

                            // 3. Security: ZIP Slip Prevention
                            val targetFile = File(destDir, rawName).canonicalFile
                            if (!isSafeDestination(canonicalDest, targetFile)) {
                                throw SecurityException("Path traversal attempt detected in entry: $rawName")
                            }

                            val pct = ((entryIndex.toFloat() / totalExpected.toFloat()) * 100f).toInt().coerceIn(0, 99)
                            onProgress(
                                ZipExtractProgress(
                                    currentFileName = targetFile.name,
                                    extractedCount = entryIndex,
                                    totalCount = totalExpected,
                                    percentage = pct
                                )
                            )

                            if (entry.isDirectory) {
                                if (!targetFile.exists()) {
                                    targetFile.mkdirs()
                                    foldersExtracted++
                                }
                            } else {
                                // Ensure parent directory exists for nested files
                                val parent = targetFile.parentFile
                                if (parent != null && !parent.exists()) {
                                    parent.mkdirs()
                                    foldersExtracted++
                                }

                                // 4. Duplicate Filename Handling: Prevent silent loss
                                val safeFile = getSafeOutputFile(targetFile)

                                FileOutputStream(safeFile).use { outStream ->
                                    BufferedOutputStream(outStream, 16384).use { bufOut ->
                                        var len: Int
                                        while (zipIn.read(buffer).also { len = it } > 0) {
                                            bufOut.write(buffer, 0, len)
                                            totalBytesWritten += len
                                        }
                                        bufOut.flush()
                                    }
                                }

                                filesExtracted++
                                newlyCreatedFiles.add(safeFile.absolutePath)
                            }
                            zipIn.closeEntry()
                        }
                    }
                }
            }

            // 5. Finalize progress to 100%
            onProgress(
                ZipExtractProgress(
                    currentFileName = "Done",
                    extractedCount = filesExtracted,
                    totalCount = filesExtracted,
                    percentage = 100
                )
            )

            // 6. Register newly extracted files in Android MediaStore so they show in apps immediately
            if (newlyCreatedFiles.isNotEmpty()) {
                try {
                    MediaScannerConnection.scanFile(
                        context,
                        newlyCreatedFiles.toTypedArray(),
                        null,
                        null
                    )
                } catch (_: Exception) {}
            }

            // 7. Calculate real subfolders and files in the destination folder
            val (finalFilesCount, finalSubfoldersCount, finalSize) = scanFolderStats(destDir)

            // 8. Record in Local Database
            val record = ExtractedFolderRecord(
                folderPath = destDir.absolutePath,
                folderName = destDir.name,
                sourceZipPath = zipUriOrPath,
                sourceZipName = zipDisplayName,
                totalFiles = finalFilesCount,
                totalSubfolders = finalSubfoldersCount,
                totalSizeBytes = finalSize,
                extractedDateMs = System.currentTimeMillis()
            )
            LocalFileDatabaseHelper.getInstance(context).recordExtractedFolder(record)

            ZipExtractResult(
                success = true,
                extractedFolder = destDir,
                totalFiles = finalFilesCount,
                totalSubfolders = finalSubfoldersCount,
                totalBytes = finalSize
            )
        } catch (se: SecurityException) {
            ZipExtractResult(
                success = false,
                errorMessage = "Security Warning: ${se.message}"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            val msg = e.message?.lowercase() ?: ""
            ZipExtractResult(
                success = false,
                errorMessage = when {
                    msg.contains("encrypt") || msg.contains("password") -> "This ZIP archive is password-protected or encrypted, which is not supported."
                    msg.contains("space") || msg.contains("enospc") -> "Insufficient storage space to complete extraction."
                    e is EOFException || e is ZipException -> "Invalid or corrupted ZIP archive."
                    else -> "Extraction failed: ${e.message}"
                }
            )
        }
    }

    /**
     * Prevents ZIP Slip path traversal attacks by verifying that entry is inside the target directory.
     */
    private fun isSafeDestination(destDir: File, entryFile: File): Boolean {
        val destPath = destDir.canonicalPath
        val entryPath = entryFile.canonicalPath
        return entryPath == destPath || entryPath.startsWith(destPath + File.separator)
    }

    /**
     * Generates a safe file target without silently destroying existing files.
     */
    private fun getSafeOutputFile(requestedFile: File): File {
        if (!requestedFile.exists()) return requestedFile
        val parent = requestedFile.parentFile ?: return requestedFile
        val base = requestedFile.nameWithoutExtension
        val ext = requestedFile.extension
        val extDot = if (ext.isNotBlank()) ".$ext" else ""
        var count = 1
        var candidate: File
        do {
            candidate = File(parent, "$base ($count)$extDot")
            count++
        } while (candidate.exists())
        return candidate
    }

    /**
     * Recursively computes files, subfolders, and size.
     */
    fun scanFolderStats(dir: File): Triple<Int, Int, Long> {
        var files = 0
        var subfolders = 0
        var size = 0L

        fun walk(folder: File) {
            val children = folder.listFiles() ?: return
            for (child in children) {
                if (child.isDirectory) {
                    subfolders++
                    walk(child)
                } else if (child.isFile) {
                    files++
                    size += child.length()
                }
            }
        }

        walk(dir)
        return Triple(files, subfolders, size)
    }

    /**
     * Sanitizes folder name for file systems.
     */
    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }

    /**
     * Resolves the default destination folder for a ZIP file.
     */
    fun getDefaultDestinationFolder(context: Context, zipFilePathOrUri: String): File {
        // Try same location as the ZIP file if it has a real accessible path
        if (!zipFilePathOrUri.startsWith("content://")) {
            val zipFile = File(zipFilePathOrUri)
            val parent = zipFile.parentFile
            if (parent != null && parent.exists() && parent.canWrite()) {
                return parent
            }
        }

        // Fallback to Public Downloads/IntelliFile_Extracted or Public Documents
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (publicDownloads != null && publicDownloads.exists() && publicDownloads.canWrite()) {
            return publicDownloads
        }

        val publicDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (publicDocs != null && publicDocs.exists() && publicDocs.canWrite()) {
            return publicDocs
        }

        // App-specific external storage as safe guarantee
        return context.getExternalFilesDir(null) ?: context.filesDir
    }
}
