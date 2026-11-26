package com.simats.intellifile.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.simats.intellifile.data.manager.LocalFileManager
import com.simats.intellifile.ui.home.PriorityLevel

/**
 * Metadata record for user customizations stored locally on device.
 * Priority is nullable by default: files only have a priority if the user
 * has explicitly assigned one.
 */
data class LocalFileMetadata(
    val uriOrPath: String,
    val fileName: String,
    val priority: PriorityLevel? = null,
    val category: String = "Document",
    val isBookmarked: Boolean = false,
    val isVaultSynced: Boolean = false,
    val vaultDocId: Int? = null,
    val notes: String = "",
    val lastAccessed: Long = System.currentTimeMillis(),
    val dateAdded: Long = 0L,
    val lastOpened: Long = 0L,
    val contentCountLabel: String? = null,
    val contentCountValue: String? = null
)

class LocalFileDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    companion object {
        private const val DATABASE_NAME = "intellifile_local.db"
        private const val DATABASE_VERSION = 3

        private const val TABLE_METADATA = "local_file_metadata"
        private const val COL_URI_OR_PATH = "uri_or_path"
        private const val COL_FILE_NAME = "file_name"
        private const val COL_PRIORITY = "priority"
        private const val COL_CATEGORY = "category"
        private const val COL_IS_BOOKMARKED = "is_bookmarked"
        private const val COL_IS_VAULT_SYNCED = "is_vault_synced"
        private const val COL_VAULT_DOC_ID = "vault_doc_id"
        private const val COL_NOTES = "notes"
        private const val COL_LAST_ACCESSED = "last_accessed"
        private const val COL_DATE_ADDED = "date_added"
        private const val COL_LAST_OPENED = "last_opened"
        private const val COL_CONTENT_COUNT_LABEL = "content_count_label"
        private const val COL_CONTENT_COUNT_VALUE = "content_count_value"

        @Volatile
        private var instance: LocalFileDatabaseHelper? = null

        fun getInstance(context: Context): LocalFileDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: LocalFileDatabaseHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createQuery = """
            CREATE TABLE $TABLE_METADATA (
                $COL_URI_OR_PATH TEXT PRIMARY KEY,
                $COL_FILE_NAME TEXT NOT NULL,
                $COL_PRIORITY TEXT DEFAULT NULL,
                $COL_CATEGORY TEXT NOT NULL DEFAULT 'Document',
                $COL_IS_BOOKMARKED INTEGER NOT NULL DEFAULT 0,
                $COL_IS_VAULT_SYNCED INTEGER NOT NULL DEFAULT 0,
                $COL_VAULT_DOC_ID INTEGER,
                $COL_NOTES TEXT DEFAULT '',
                $COL_LAST_ACCESSED INTEGER NOT NULL DEFAULT 0,
                $COL_DATE_ADDED INTEGER NOT NULL DEFAULT 0,
                $COL_LAST_OPENED INTEGER NOT NULL DEFAULT 0,
                $COL_CONTENT_COUNT_LABEL TEXT DEFAULT NULL,
                $COL_CONTENT_COUNT_VALUE TEXT DEFAULT NULL
            )
        """.trimIndent()
        db.execSQL(createQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE $TABLE_METADATA ADD COLUMN $COL_DATE_ADDED INTEGER NOT NULL DEFAULT 0")
            } catch (_: Exception) {}
            try {
                db.execSQL("ALTER TABLE $TABLE_METADATA ADD COLUMN $COL_LAST_OPENED INTEGER NOT NULL DEFAULT 0")
            } catch (_: Exception) {}
            try {
                db.execSQL("ALTER TABLE $TABLE_METADATA ADD COLUMN $COL_CONTENT_COUNT_LABEL TEXT DEFAULT NULL")
            } catch (_: Exception) {}
            try {
                db.execSQL("ALTER TABLE $TABLE_METADATA ADD COLUMN $COL_CONTENT_COUNT_VALUE TEXT DEFAULT NULL")
            } catch (_: Exception) {}
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        try {
            db.execSQL("ALTER TABLE $TABLE_METADATA ADD COLUMN $COL_DATE_ADDED INTEGER NOT NULL DEFAULT 0")
        } catch (_: Exception) {}
        try {
            db.execSQL("ALTER TABLE $TABLE_METADATA ADD COLUMN $COL_LAST_OPENED INTEGER NOT NULL DEFAULT 0")
        } catch (_: Exception) {}
        try {
            db.execSQL("ALTER TABLE $TABLE_METADATA ADD COLUMN $COL_CONTENT_COUNT_LABEL TEXT DEFAULT NULL")
        } catch (_: Exception) {}
        try {
            db.execSQL("ALTER TABLE $TABLE_METADATA ADD COLUMN $COL_CONTENT_COUNT_VALUE TEXT DEFAULT NULL")
        } catch (_: Exception) {}
    }

    /**
     * Retrieves all metadata stored locally indexed by URI or file path.
     */
    fun getAllMetadata(): Map<String, LocalFileMetadata> {
        val resultMap = mutableMapOf<String, LocalFileMetadata>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_METADATA,
            null,
            null,
            null,
            null,
            null,
            null
        )

        cursor.use { c ->
            val colKey = c.getColumnIndexOrThrow(COL_URI_OR_PATH)
            val colName = c.getColumnIndexOrThrow(COL_FILE_NAME)
            val colPriority = c.getColumnIndexOrThrow(COL_PRIORITY)
            val colCategory = c.getColumnIndexOrThrow(COL_CATEGORY)
            val colBookmarked = c.getColumnIndexOrThrow(COL_IS_BOOKMARKED)
            val colVault = c.getColumnIndexOrThrow(COL_IS_VAULT_SYNCED)
            val colVaultDocId = c.getColumnIndexOrThrow(COL_VAULT_DOC_ID)
            val colNotes = c.getColumnIndexOrThrow(COL_NOTES)
            val colAccessed = c.getColumnIndexOrThrow(COL_LAST_ACCESSED)
            val colDateAdded = c.getColumnIndex(COL_DATE_ADDED)
            val colLastOpened = c.getColumnIndex(COL_LAST_OPENED)
            val colCountLabel = c.getColumnIndex(COL_CONTENT_COUNT_LABEL)
            val colCountValue = c.getColumnIndex(COL_CONTENT_COUNT_VALUE)

            while (c.moveToNext()) {
                val key = c.getString(colKey)
                val priorityStr = if (!c.isNull(colPriority)) c.getString(colPriority) else null
                val priority = priorityStr?.let {
                    try {
                        PriorityLevel.valueOf(it.uppercase())
                    } catch (e: Exception) {
                        null
                    }
                }

                resultMap[key] = LocalFileMetadata(
                    uriOrPath = key,
                    fileName = c.getString(colName),
                    priority = priority,
                    category = c.getString(colCategory),
                    isBookmarked = c.getInt(colBookmarked) == 1,
                    isVaultSynced = c.getInt(colVault) == 1,
                    vaultDocId = if (c.isNull(colVaultDocId)) null else c.getInt(colVaultDocId),
                    notes = c.getString(colNotes) ?: "",
                    lastAccessed = c.getLong(colAccessed),
                    dateAdded = if (colDateAdded != -1 && !c.isNull(colDateAdded)) c.getLong(colDateAdded) else 0L,
                    lastOpened = if (colLastOpened != -1 && !c.isNull(colLastOpened)) c.getLong(colLastOpened) else 0L,
                    contentCountLabel = if (colCountLabel != -1 && !c.isNull(colCountLabel)) c.getString(colCountLabel) else null,
                    contentCountValue = if (colCountValue != -1 && !c.isNull(colCountValue)) c.getString(colCountValue) else null
                )
            }
        }

        return resultMap
    }

    /**
     * Gets metadata for a specific file by uri or path.
     */
    fun getMetadata(key: String): LocalFileMetadata? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_METADATA,
            null,
            "$COL_URI_OR_PATH = ?",
            arrayOf(key),
            null,
            null,
            null
        )

        cursor.use { c ->
            if (c.moveToFirst()) {
                val colPriority = c.getColumnIndexOrThrow(COL_PRIORITY)
                val priorityStr = if (!c.isNull(colPriority)) c.getString(colPriority) else null
                val priority = priorityStr?.let {
                    try {
                        PriorityLevel.valueOf(it.uppercase())
                    } catch (e: Exception) {
                        null
                    }
                }

                val colVaultDocId = c.getColumnIndexOrThrow(COL_VAULT_DOC_ID)
                val colDateAdded = c.getColumnIndex(COL_DATE_ADDED)
                val colLastOpened = c.getColumnIndex(COL_LAST_OPENED)
                val colCountLabel = c.getColumnIndex(COL_CONTENT_COUNT_LABEL)
                val colCountValue = c.getColumnIndex(COL_CONTENT_COUNT_VALUE)

                return LocalFileMetadata(
                    uriOrPath = c.getString(c.getColumnIndexOrThrow(COL_URI_OR_PATH)),
                    fileName = c.getString(c.getColumnIndexOrThrow(COL_FILE_NAME)),
                    priority = priority,
                    category = c.getString(c.getColumnIndexOrThrow(COL_CATEGORY)),
                    isBookmarked = c.getInt(c.getColumnIndexOrThrow(COL_IS_BOOKMARKED)) == 1,
                    isVaultSynced = c.getInt(c.getColumnIndexOrThrow(COL_IS_VAULT_SYNCED)) == 1,
                    vaultDocId = if (c.isNull(colVaultDocId)) null else c.getInt(colVaultDocId),
                    notes = c.getString(c.getColumnIndexOrThrow(COL_NOTES)) ?: "",
                    lastAccessed = c.getLong(c.getColumnIndexOrThrow(COL_LAST_ACCESSED)),
                    dateAdded = if (colDateAdded != -1 && !c.isNull(colDateAdded)) c.getLong(colDateAdded) else 0L,
                    lastOpened = if (colLastOpened != -1 && !c.isNull(colLastOpened)) c.getLong(colLastOpened) else 0L,
                    contentCountLabel = if (colCountLabel != -1 && !c.isNull(colCountLabel)) c.getString(colCountLabel) else null,
                    contentCountValue = if (colCountValue != -1 && !c.isNull(colCountValue)) c.getString(colCountValue) else null
                )
            }
        }
        return null
    }

    /**
     * Checks if a file is currently bookmarked.
     */
    fun isBookmarked(uriOrPath: String): Boolean {
        return getMetadata(uriOrPath)?.isBookmarked == true
    }

    /**
     * Explicitly sets bookmark status for a file.
     */
    fun setBookmarked(uriOrPath: String, fileName: String, isBookmarked: Boolean) {
        val existing = getMetadata(uriOrPath)
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_URI_OR_PATH, uriOrPath)
            put(COL_FILE_NAME, fileName)
            put(COL_IS_BOOKMARKED, if (isBookmarked) 1 else 0)
            if (existing != null) {
                if (existing.priority != null) {
                    put(COL_PRIORITY, existing.priority.name)
                }
                put(COL_CATEGORY, existing.category)
                put(COL_IS_VAULT_SYNCED, if (existing.isVaultSynced) 1 else 0)
                existing.vaultDocId?.let { put(COL_VAULT_DOC_ID, it) }
            }
            put(COL_LAST_ACCESSED, System.currentTimeMillis())
        }
        db.insertWithOnConflict(
            TABLE_METADATA,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
        LocalFileManager.invalidateCache()
    }

    /**
     * Updates or inserts a priority level explicitly set by the user.
     */
    fun setPriority(uriOrPath: String, fileName: String, priority: PriorityLevel?) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_URI_OR_PATH, uriOrPath)
            put(COL_FILE_NAME, fileName)
            if (priority != null) {
                put(COL_PRIORITY, priority.name)
            } else {
                putNull(COL_PRIORITY)
            }
            put(COL_LAST_ACCESSED, System.currentTimeMillis())
        }
        db.insertWithOnConflict(
            TABLE_METADATA,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
        LocalFileManager.invalidateCache()
    }

    /**
     * Toggles bookmark state for a file, returning the new bookmark status.
     */
    fun toggleBookmark(uriOrPath: String, fileName: String): Boolean {
        val existing = getMetadata(uriOrPath)
        val newBookmarked = !(existing?.isBookmarked ?: false)

        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_URI_OR_PATH, uriOrPath)
            put(COL_FILE_NAME, fileName)
            put(COL_IS_BOOKMARKED, if (newBookmarked) 1 else 0)
            if (existing != null) {
                if (existing.priority != null) {
                    put(COL_PRIORITY, existing.priority.name)
                }
                put(COL_CATEGORY, existing.category)
                put(COL_IS_VAULT_SYNCED, if (existing.isVaultSynced) 1 else 0)
                existing.vaultDocId?.let { put(COL_VAULT_DOC_ID, it) }
            }
            put(COL_LAST_ACCESSED, System.currentTimeMillis())
        }
        db.insertWithOnConflict(
            TABLE_METADATA,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
        LocalFileManager.invalidateCache()
        return newBookmarked
    }

    /**
     * Marks a file as synced to the IntelliSpace with an optional server document ID.
     */
    fun setVaultStatus(uriOrPath: String, fileName: String, isSynced: Boolean, vaultDocId: Int? = null) {
        val existing = getMetadata(uriOrPath)
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_URI_OR_PATH, uriOrPath)
            put(COL_FILE_NAME, fileName)
            put(COL_IS_VAULT_SYNCED, if (isSynced) 1 else 0)
            if (vaultDocId != null) {
                put(COL_VAULT_DOC_ID, vaultDocId)
            }
            if (existing != null) {
                if (existing.priority != null) {
                    put(COL_PRIORITY, existing.priority.name)
                }
                put(COL_CATEGORY, existing.category)
                put(COL_IS_BOOKMARKED, if (existing.isBookmarked) 1 else 0)
            }
            put(COL_LAST_ACCESSED, System.currentTimeMillis())
        }
        db.insertWithOnConflict(
            TABLE_METADATA,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
        LocalFileManager.invalidateCache()
    }

    /**
     * Removes IntelliSpace sync status for a deleted document.
     */
    fun removeVaultStatus(vaultDocId: Int, title: String = "") {
        try {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put(COL_IS_VAULT_SYNCED, 0)
                putNull(COL_VAULT_DOC_ID)
            }
            if (title.isNotBlank()) {
                db.update(
                    TABLE_METADATA,
                    cv,
                    "$COL_VAULT_DOC_ID = ? OR LOWER($COL_FILE_NAME) = ?",
                    arrayOf(vaultDocId.toString(), title.trim().lowercase())
                )
            } else {
                db.update(
                    TABLE_METADATA,
                    cv,
                    "$COL_VAULT_DOC_ID = ?",
                    arrayOf(vaultDocId.toString())
                )
            }
            LocalFileManager.invalidateCache()
        } catch (e: Exception) {
            android.util.Log.e("LocalFileDB", "Error removing vault status", e)
        }
    }

    /**
     * Synchronizes local metadata with the server's IntelliSpace document list.
     * Any row where is_vault_synced = 1 that is no longer present on the server has its synced flag reset to 0.
     */
    fun syncVaultStatus(validVaultDocIds: Set<Int>, validTitles: Set<String>) {
        try {
            val db = writableDatabase
            val cursor = db.query(
                TABLE_METADATA,
                arrayOf(COL_URI_OR_PATH, COL_FILE_NAME, COL_VAULT_DOC_ID),
                "$COL_IS_VAULT_SYNCED = 1",
                null, null, null, null
            )
            val toReset = mutableListOf<String>()
            cursor.use { c ->
                val colKey = c.getColumnIndexOrThrow(COL_URI_OR_PATH)
                val colName = c.getColumnIndexOrThrow(COL_FILE_NAME)
                val colDocId = c.getColumnIndexOrThrow(COL_VAULT_DOC_ID)
                while (c.moveToNext()) {
                    val key = c.getString(colKey)
                    val name = c.getString(colName)?.trim()?.lowercase() ?: ""
                    val docId = if (c.isNull(colDocId)) null else c.getInt(colDocId)

                    val isValid = (docId != null && validVaultDocIds.contains(docId)) ||
                            (name.isNotBlank() && validTitles.contains(name))
                    if (!isValid) {
                        toReset.add(key)
                    }
                }
            }
            if (toReset.isNotEmpty()) {
                db.beginTransaction()
                try {
                    val cv = ContentValues().apply {
                        put(COL_IS_VAULT_SYNCED, 0)
                        putNull(COL_VAULT_DOC_ID)
                    }
                    for (k in toReset) {
                        db.update(TABLE_METADATA, cv, "$COL_URI_OR_PATH = ?", arrayOf(k))
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                LocalFileManager.invalidateCache()
            }
        } catch (e: Exception) {
            android.util.Log.e("LocalFileDB", "Error syncing vault status", e)
        }
    }

    /**
     * Records a file opening event inside IntelliFile.
     */
    fun recordFileOpened(uriOrPath: String, fileName: String, timestamp: Long = System.currentTimeMillis()) {
        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_LAST_OPENED, timestamp)
                put(COL_LAST_ACCESSED, timestamp)
            }
            val updated = db.update(TABLE_METADATA, values, "$COL_URI_OR_PATH = ?", arrayOf(uriOrPath))
            if (updated <= 0) {
                val insertValues = ContentValues().apply {
                    put(COL_URI_OR_PATH, uriOrPath)
                    put(COL_FILE_NAME, fileName)
                    put(COL_LAST_OPENED, timestamp)
                    put(COL_LAST_ACCESSED, timestamp)
                }
                db.insertWithOnConflict(TABLE_METADATA, null, insertValues, SQLiteDatabase.CONFLICT_IGNORE)
            }
            if (fileName.isNotBlank() && fileName != uriOrPath) {
                db.update(TABLE_METADATA, values, "$COL_FILE_NAME = ? AND $COL_LAST_OPENED < ?", arrayOf(fileName, timestamp.toString()))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Retrieves the last opened timestamp from IntelliFile.
     * Returns null if never opened through IntelliFile.
     */
    fun getLastOpened(uriOrPath: String, fileName: String): Long? {
        try {
            val db = readableDatabase
            var cursor = db.query(
                TABLE_METADATA,
                arrayOf(COL_LAST_OPENED),
                "$COL_URI_OR_PATH = ?",
                arrayOf(uriOrPath),
                null, null, null
            )
            cursor.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(COL_LAST_OPENED)
                    if (idx != -1) {
                        val ts = c.getLong(idx)
                        if (ts > 0L) return ts
                    }
                }
            }
            if (fileName.isNotBlank() && fileName != uriOrPath) {
                cursor = db.query(
                    TABLE_METADATA,
                    arrayOf(COL_LAST_OPENED),
                    "$COL_FILE_NAME = ?",
                    arrayOf(fileName),
                    null, null, null
                )
                cursor.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(COL_LAST_OPENED)
                        if (idx != -1) {
                            val ts = c.getLong(idx)
                            if (ts > 0L) return ts
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Stores the MediaStore Date Added timestamp for persistent offline lookup.
     */
    fun setDateAdded(uriOrPath: String, fileName: String, dateAddedMs: Long) {
        if (dateAddedMs <= 0L) return
        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_DATE_ADDED, dateAddedMs)
            }
            val updated = db.update(TABLE_METADATA, values, "$COL_URI_OR_PATH = ?", arrayOf(uriOrPath))
            if (updated <= 0) {
                val insertValues = ContentValues().apply {
                    put(COL_URI_OR_PATH, uriOrPath)
                    put(COL_FILE_NAME, fileName)
                    put(COL_DATE_ADDED, dateAddedMs)
                }
                db.insertWithOnConflict(TABLE_METADATA, null, insertValues, SQLiteDatabase.CONFLICT_IGNORE)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Retrieves the cached Date Added timestamp.
     */
    fun getDateAdded(uriOrPath: String, fileName: String): Long? {
        try {
            val db = readableDatabase
            var cursor = db.query(
                TABLE_METADATA,
                arrayOf(COL_DATE_ADDED),
                "$COL_URI_OR_PATH = ?",
                arrayOf(uriOrPath),
                null, null, null
            )
            cursor.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(COL_DATE_ADDED)
                    if (idx != -1) {
                        val ts = c.getLong(idx)
                        if (ts > 0L) return ts
                    }
                }
            }
            if (fileName.isNotBlank() && fileName != uriOrPath) {
                cursor = db.query(
                    TABLE_METADATA,
                    arrayOf(COL_DATE_ADDED),
                    "$COL_FILE_NAME = ?",
                    arrayOf(fileName),
                    null, null, null
                )
                cursor.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(COL_DATE_ADDED)
                        if (idx != -1) {
                            val ts = c.getLong(idx)
                            if (ts > 0L) return ts
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Caches calculated content counts (pages, slides, sheets, rows, lines)
     * so subsequent opens load instantly without re-parsing files.
     */
    fun setCachedContentCount(uriOrPath: String, fileName: String, label: String, value: String) {
        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_CONTENT_COUNT_LABEL, label)
                put(COL_CONTENT_COUNT_VALUE, value)
            }
            val updated = db.update(TABLE_METADATA, values, "$COL_URI_OR_PATH = ?", arrayOf(uriOrPath))
            if (updated <= 0) {
                val insertValues = ContentValues().apply {
                    put(COL_URI_OR_PATH, uriOrPath)
                    put(COL_FILE_NAME, fileName)
                    put(COL_CONTENT_COUNT_LABEL, label)
                    put(COL_CONTENT_COUNT_VALUE, value)
                }
                db.insertWithOnConflict(TABLE_METADATA, null, insertValues, SQLiteDatabase.CONFLICT_IGNORE)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Retrieves cached content count.
     */
    fun getCachedContentCount(uriOrPath: String, fileName: String): Pair<String, String>? {
        try {
            val db = readableDatabase
            var cursor = db.query(
                TABLE_METADATA,
                arrayOf(COL_CONTENT_COUNT_LABEL, COL_CONTENT_COUNT_VALUE),
                "$COL_URI_OR_PATH = ?",
                arrayOf(uriOrPath),
                null, null, null
            )
            cursor.use { c ->
                if (c.moveToFirst()) {
                    val lIdx = c.getColumnIndex(COL_CONTENT_COUNT_LABEL)
                    val vIdx = c.getColumnIndex(COL_CONTENT_COUNT_VALUE)
                    if (lIdx != -1 && vIdx != -1 && !c.isNull(lIdx) && !c.isNull(vIdx)) {
                        val label = c.getString(lIdx)
                        val value = c.getString(vIdx)
                        if (!label.isNullOrBlank() && !value.isNullOrBlank()) {
                            return Pair(label, value)
                        }
                    }
                }
            }
            if (fileName.isNotBlank() && fileName != uriOrPath) {
                cursor = db.query(
                    TABLE_METADATA,
                    arrayOf(COL_CONTENT_COUNT_LABEL, COL_CONTENT_COUNT_VALUE),
                    "$COL_FILE_NAME = ?",
                    arrayOf(fileName),
                    null, null, null
                )
                cursor.use { c ->
                    if (c.moveToFirst()) {
                        val lIdx = c.getColumnIndex(COL_CONTENT_COUNT_LABEL)
                        val vIdx = c.getColumnIndex(COL_CONTENT_COUNT_VALUE)
                        if (lIdx != -1 && vIdx != -1 && !c.isNull(lIdx) && !c.isNull(vIdx)) {
                            val label = c.getString(lIdx)
                            val value = c.getString(vIdx)
                            if (!label.isNullOrBlank() && !value.isNullOrBlank()) {
                                return Pair(label, value)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
