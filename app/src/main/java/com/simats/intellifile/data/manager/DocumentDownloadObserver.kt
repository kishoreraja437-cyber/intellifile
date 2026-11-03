package com.simats.intellifile.data.manager

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.simats.intellifile.data.session.SessionManager
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Monitors device downloads and incoming documents (from Browser, WhatsApp, Telegram, etc.).
 * When a user downloads or receives a new document file, dispatches an instant heads-up notification.
 */
class DocumentDownloadObserver private constructor(private val context: Context) :
    ContentObserver(Handler(Looper.getMainLooper())) {

    private val sessionManager = SessionManager(context)
    private val prefs = context.getSharedPreferences("intellifile_doc_monitor", Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeFileObservers = mutableListOf<FileObserver>()
    private val isScanning = AtomicBoolean(false)
    private var isRegistered = false

    // In-memory cache synced with persistent SharedPreferences
    private val notifiedPaths = mutableSetOf<String>()

    companion object {
        @Volatile
        private var instance: DocumentDownloadObserver? = null

        private const val KEY_LAST_SCAN_TIME_MS = "last_scan_time_ms"
        private const val KEY_NOTIFIED_PATHS = "notified_paths"

        val SUPPORTED_EXTENSIONS = setOf(
            "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "csv", "json"
        )

        fun getInstance(context: Context): DocumentDownloadObserver {
            return instance ?: synchronized(this) {
                instance ?: DocumentDownloadObserver(context.applicationContext).also {
                    instance = it
                }
            }
        }

        fun startWatching(context: Context) {
            val observer = getInstance(context)
            observer.register()
        }
    }

    init {
        // Load persistent notified paths
        val saved = prefs.getStringSet(KEY_NOTIFIED_PATHS, emptySet()) ?: emptySet()
        notifiedPaths.addAll(saved)

        // If never scanned before, initialize lastScanTime to 1 hour ago so recent downloads are notified
        if (!prefs.contains(KEY_LAST_SCAN_TIME_MS)) {
            val initialScanTime = System.currentTimeMillis() - 3600_000L // 1 hour ago
            prefs.edit().putLong(KEY_LAST_SCAN_TIME_MS, initialScanTime).apply()
        }
    }

    @Synchronized
    fun register() {
        if (isRegistered) return
        isRegistered = true

        startDirectoryObservers()

        try {
            val contentResolver = context.contentResolver
            contentResolver.registerContentObserver(
                MediaStore.Files.getContentUri("external"),
                true,
                this
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.registerContentObserver(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    true,
                    this
                )
            }
        } catch (_: Exception) {}

        // Initial check
        mainHandler.postDelayed({ checkForNewDocuments() }, 1000)
    }

    @Synchronized
    fun unregister() {
        if (!isRegistered) return
        isRegistered = false

        try {
            context.contentResolver.unregisterContentObserver(this)
        } catch (_: Exception) {}

        for (obs in activeFileObservers) {
            try {
                obs.stopWatching()
            } catch (_: Exception) {}
        }
        activeFileObservers.clear()
    }

    private fun getCandidateDirectories(): List<File> {
        val dirs = mutableListOf<File>()

        try {
            // Standard Downloads
            val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (dl != null && dl.exists()) dirs.add(dl)

            val rootDl = File(Environment.getExternalStorageDirectory(), "Download")
            if (rootDl.exists() && !dirs.contains(rootDl)) dirs.add(rootDl)

            // WhatsApp Documents (Android 11+ scoped path)
            val waAndroidDoc = File(
                Environment.getExternalStorageDirectory(),
                "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents"
            )
            if (waAndroidDoc.exists() && !dirs.contains(waAndroidDoc)) dirs.add(waAndroidDoc)

            // Legacy WhatsApp Documents path
            val waLegacyDoc = File(
                Environment.getExternalStorageDirectory(),
                "WhatsApp/Media/WhatsApp Documents"
            )
            if (waLegacyDoc.exists() && !dirs.contains(waLegacyDoc)) dirs.add(waLegacyDoc)

            // WhatsApp Business Documents
            val waBizDoc = File(
                Environment.getExternalStorageDirectory(),
                "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Documents"
            )
            if (waBizDoc.exists() && !dirs.contains(waBizDoc)) dirs.add(waBizDoc)

            // Telegram Documents
            val tgDoc = File(Environment.getExternalStorageDirectory(), "Telegram/Telegram Documents")
            if (tgDoc.exists() && !dirs.contains(tgDoc)) dirs.add(tgDoc)

            val tgAndroidDoc = File(
                Environment.getExternalStorageDirectory(),
                "Android/media/org.telegram.messenger/Telegram/Telegram Documents"
            )
            if (tgAndroidDoc.exists() && !dirs.contains(tgAndroidDoc)) dirs.add(tgAndroidDoc)

            // Standard Documents folder
            val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (docs != null && docs.exists() && !dirs.contains(docs)) dirs.add(docs)

            val rootDocs = File(Environment.getExternalStorageDirectory(), "Documents")
            if (rootDocs.exists() && !dirs.contains(rootDocs)) dirs.add(rootDocs)
        } catch (_: Exception) {}

        return dirs
    }

    private fun startDirectoryObservers() {
        val candidateDirs = getCandidateDirectories()
        for (dir in candidateDirs) {
            try {
                val observer = createFolderObserver(dir.absolutePath)
                observer.startWatching()
                activeFileObservers.add(observer)
            } catch (_: Exception) {}
        }
    }

    @Suppress("DEPRECATION")
    private fun createFolderObserver(dirPath: String): FileObserver {
        val mask = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO or FileObserver.CREATE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(File(dirPath), mask) {
                override fun onEvent(event: Int, path: String?) {
                    if (!path.isNullOrBlank()) {
                        mainHandler.postDelayed({ checkForNewDocuments() }, 600)
                    }
                }
            }
        } else {
            object : FileObserver(dirPath, mask) {
                override fun onEvent(event: Int, path: String?) {
                    if (!path.isNullOrBlank()) {
                        mainHandler.postDelayed({ checkForNewDocuments() }, 600)
                    }
                }
            }
        }
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        mainHandler.postDelayed({ checkForNewDocuments() }, 600)
    }

    /**
     * Checks for newly added or downloaded documents across filesystem and MediaStore.
     */
    fun checkForNewDocuments() {
        // Must be enabled by user in notification settings
        if (!sessionManager.isNewDocAlertsEnabled()) return
        if (sessionManager.isQuietHoursEnabled() && NotificationHelper.isInQuietHours()) return
        if (!NotificationHelper.hasNotificationPermission(context)) return

        if (!isScanning.compareAndSet(false, true)) {
            return
        }

        try {
            val lastScanTime = prefs.getLong(
                KEY_LAST_SCAN_TIME_MS,
                System.currentTimeMillis() - 3600_000L
            )
            val currentTime = System.currentTimeMillis()

            // Layer 1: Direct File System Inspection (zero latency, detects files before MediaStore indexes)
            scanDirectFileSystem(lastScanTime)

            // Layer 2: MediaStore Inspection (covers scoped storage and provider downloads)
            scanMediaStore(lastScanTime)

            // Update last scan timestamp
            prefs.edit().putLong(KEY_LAST_SCAN_TIME_MS, currentTime).apply()
        } catch (_: Exception) {
        } finally {
            isScanning.set(false)
        }
    }

    private fun scanDirectFileSystem(lastScanTime: Long) {
        val dirs = getCandidateDirectories()
        val timeThreshold = lastScanTime - 60_000L // 1 minute leeway for clock skew

        for (dir in dirs) {
            try {
                if (!dir.exists() || !dir.canRead()) continue
                val files = dir.listFiles() ?: continue

                for (file in files) {
                    if (!file.isFile) continue
                    val name = file.name
                    if (name.startsWith(".")) continue
                    if (name.endsWith(".crdownload", ignoreCase = true) ||
                        name.endsWith(".tmp", ignoreCase = true) ||
                        name.endsWith(".part", ignoreCase = true)) {
                        continue
                    }

                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (!SUPPORTED_EXTENSIONS.contains(ext)) continue
                    if (file.length() <= 0L) continue

                    val path = file.absolutePath
                    if (isAlreadyNotified(path, name)) continue

                    val modTime = file.lastModified()
                    // Modified after last scan OR modified within the last 2 hours
                    if (modTime >= timeThreshold || (System.currentTimeMillis() - modTime) < 7200_000L) {
                        markNotified(path, name)
                        val source = resolveSource(path)
                        NotificationHelper.sendNewDocumentAlert(context, name, source, path)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun scanMediaStore(lastScanTime: Long) {
        val urisToQuery = mutableListOf(MediaStore.Files.getContentUri("external"))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            urisToQuery.add(MediaStore.Downloads.EXTERNAL_CONTENT_URI)
        }

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DATA
        )

        // Query files modified within recent threshold
        val timeSec = (lastScanTime - 60_000L) / 1000L
        val selection = "${MediaStore.Files.FileColumns.DATE_MODIFIED} >= ? AND ${MediaStore.Files.FileColumns.SIZE} > 0"
        val selectionArgs = arrayOf(timeSec.toString())
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC LIMIT 50"

        for (contentUri in urisToQuery) {
            try {
                val cursor = context.contentResolver.query(
                    contentUri,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )

                cursor?.use { c ->
                    val nameCol = c.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val sizeCol = c.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                    val pathCol = c.getColumnIndex(MediaStore.Files.FileColumns.DATA)

                    while (c.moveToNext()) {
                        val filePath = if (pathCol != -1) c.getString(pathCol) ?: "" else ""
                        val fileName = if (nameCol != -1) c.getString(nameCol) ?: "" else ""
                        val sizeBytes = if (sizeCol != -1) c.getLong(sizeCol) else 0L

                        val effectiveName = if (fileName.isNotBlank()) fileName else File(filePath).name
                        if (effectiveName.startsWith(".")) continue
                        if (effectiveName.endsWith(".crdownload", ignoreCase = true) ||
                            effectiveName.endsWith(".tmp", ignoreCase = true) ||
                            effectiveName.endsWith(".part", ignoreCase = true)) {
                            continue
                        }

                        val ext = effectiveName.substringAfterLast('.', "").lowercase()
                        if (!SUPPORTED_EXTENSIONS.contains(ext) || sizeBytes <= 0L) continue

                        if (isAlreadyNotified(filePath, effectiveName)) continue

                        markNotified(filePath, effectiveName)
                        val source = resolveSource(filePath)
                        NotificationHelper.sendNewDocumentAlert(context, effectiveName, source, filePath)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun isAlreadyNotified(path: String, fileName: String): Boolean {
        synchronized(notifiedPaths) {
            if (path.isNotBlank() && notifiedPaths.contains(path)) return true
            if (fileName.isNotBlank() && notifiedPaths.contains(fileName)) return true
            return false
        }
    }

    private fun markNotified(path: String, fileName: String) {
        synchronized(notifiedPaths) {
            if (path.isNotBlank()) notifiedPaths.add(path)
            if (fileName.isNotBlank()) notifiedPaths.add(fileName)
            // Persist to SharedPreferences
            prefs.edit().putStringSet(KEY_NOTIFIED_PATHS, HashSet(notifiedPaths)).apply()
        }
    }

    private fun resolveSource(filePath: String): String {
        val lower = filePath.lowercase()
        return when {
            lower.contains("whatsapp") -> "Received via WhatsApp"
            lower.contains("download") -> "Downloaded from Browser"
            lower.contains("telegram") -> "Received via Telegram"
            else -> "New Document on Device"
        }
    }
}
