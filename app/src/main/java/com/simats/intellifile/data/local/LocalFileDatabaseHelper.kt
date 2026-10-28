package com.simats.intellifile.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
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
    val lastAccessed: Long = System.currentTimeMillis()
)

class LocalFileDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    companion object {
        private const val DATABASE_NAME = "intellifile_local.db"
        private const val DATABASE_VERSION = 2

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
                $COL_LAST_ACCESSED INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()
        db.execSQL(createQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_METADATA")
        onCreate(db)
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
                    lastAccessed = c.getLong(colAccessed)
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
                return LocalFileMetadata(
                    uriOrPath = c.getString(c.getColumnIndexOrThrow(COL_URI_OR_PATH)),
                    fileName = c.getString(c.getColumnIndexOrThrow(COL_FILE_NAME)),
                    priority = priority,
                    category = c.getString(c.getColumnIndexOrThrow(COL_CATEGORY)),
                    isBookmarked = c.getInt(c.getColumnIndexOrThrow(COL_IS_BOOKMARKED)) == 1,
                    isVaultSynced = c.getInt(c.getColumnIndexOrThrow(COL_IS_VAULT_SYNCED)) == 1,
                    vaultDocId = if (c.isNull(colVaultDocId)) null else c.getInt(colVaultDocId),
                    notes = c.getString(c.getColumnIndexOrThrow(COL_NOTES)) ?: "",
                    lastAccessed = c.getLong(c.getColumnIndexOrThrow(COL_LAST_ACCESSED))
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
        return newBookmarked
    }

    /**
     * Marks a file as synced to the AI Vault with an optional server document ID.
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
    }
}
