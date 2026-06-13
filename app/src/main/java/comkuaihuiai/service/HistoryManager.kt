package comkuaihuiai.service

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * 可绘AI v3.5.0 - 历史记录管理器
 */
class HistoryManager(private val context: Context) {

    companion object {
        private const val TAG = "HistoryManager"
        private const val DB_NAME = "kehuiai_history.db"
        private const val DB_VERSION = 1
    }
    
    data class HistoryItem(
        val id: Long = 0,
        val prompt: String,
        val negativePrompt: String = "",
        val imagePath: String = "",
        val thumbnail: Bitmap? = null,
        val baseModel: String = "",
        val seed: Long = -1,
        val steps: Int = 20,
        val guidance: Float = 7f,
        val width: Int = 512,
        val height: Int = 512,
        val favorite: Boolean = false,
        val createdAt: Long = System.currentTimeMillis()
    )
    
    data class FilterCriteria(
        val searchQuery: String? = null,
        val baseModel: String? = null,
        val favoriteOnly: Boolean = false
    )
    
    enum class SortBy { DATE_DESC, DATE_ASC, PROMPT_ASC }
    
    private val dbHelper = HistoryDbHelper(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _items = MutableStateFlow<List<HistoryItem>>(emptyList())
    val items: StateFlow<List<HistoryItem>> = _items.asStateFlow()
    
    init { refreshHistory() }
    
    fun addHistory(item: HistoryItem): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("prompt", item.prompt)
            put("negative_prompt", item.negativePrompt)
            put("image_path", item.imagePath)
            val thumb = item.thumbnail
            thumb?.let { bmp -> put("thumbnail", bitmapToBytes(bmp)) }
            put("base_model", item.baseModel)
            put("seed", item.seed)
            put("steps", item.steps)
            put("guidance", item.guidance)
            put("width", item.width)
            put("height", item.height)
            put("favorite", if (item.favorite) 1 else 0)
            put("created_at", item.createdAt)
        }
        val id = db.insert("history", null, values)
        refreshHistory()
        Log.i(TAG, "添加历史: ID=$id")
        return id
    }
    
    fun getHistory(filter: FilterCriteria = FilterCriteria(), sort: SortBy = SortBy.DATE_DESC, limit: Int = 100): List<HistoryItem> {
        val db = dbHelper.readableDatabase
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()
        
        filter.searchQuery?.let { 
            conditions.add("prompt LIKE ?")
            args.add("%$it%")
        }
        filter.baseModel?.let { 
            conditions.add("base_model = ?")
            args.add(it)
        }
        if (filter.favoriteOnly) conditions.add("favorite = 1")
        
        val selection = if (conditions.isEmpty()) null else conditions.joinToString(" AND ")
        val selectionArgs = if (args.isEmpty()) null else args.toTypedArray()
        val orderBy = when (sort) {
            SortBy.DATE_DESC -> "created_at DESC"
            SortBy.DATE_ASC -> "created_at ASC"
            SortBy.PROMPT_ASC -> "prompt ASC"
        }
        
        val cursor = db.query("history", null, selection, selectionArgs, null, null, orderBy, "$limit")
        return cursorToList(cursor)
    }
    
    fun toggleFavorite(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        val item = getHistoryById(id) ?: return false
        val values = ContentValues().apply { put("favorite", if (item.favorite) 0 else 1) }
        val rows = db.update("history", values, "_id = ?", arrayOf(id.toString()))
        refreshHistory()
        return rows > 0
    }
    
    fun deleteHistory(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        val rows = db.delete("history", "_id = ?", arrayOf(id.toString()))
        refreshHistory()
        return rows > 0
    }
    
    fun getHistoryById(id: Long): HistoryItem? {
        val db = dbHelper.readableDatabase
        val cursor = db.query("history", null, "_id = ?", arrayOf(id.toString()), null, null, null)
        return cursorToList(cursor).firstOrNull()
    }
    
    fun getFavorites(): List<HistoryItem> = getHistory(FilterCriteria(favoriteOnly = true))
    
    fun searchPrompts(query: String): List<HistoryItem> = getHistory(FilterCriteria(searchQuery = query))
    
    fun getStatistics(): HistoryStats {
        val db = dbHelper.readableDatabase
        fun count(where: String? = null): Int {
            val sql = "SELECT COUNT(*) FROM history" + (where?.let { " WHERE $it" } ?: "")
            val cursor = db.rawQuery(sql, null)
            cursor.use { return if (it.moveToFirst()) it.getInt(0) else 0 }
        }
        return HistoryStats(
            totalGenerations = count(),
            favorites = count("favorite = 1"),
            todayGenerations = count("date(created_at/1000, 'unixepoch') = date('now')")
        )
    }
    
    fun refreshHistory() {
        scope.launch {
            _items.value = getHistory()
        }
    }
    
    fun release() {
        scope.cancel()
        dbHelper.close()
    }
    
    private fun cursorToList(cursor: Cursor): List<HistoryItem> {
        val items = mutableListOf<HistoryItem>()
        cursor.use {
            while (it.moveToNext()) {
                items.add(HistoryItem(
                    id = it.getLong(it.getColumnIndexOrThrow("_id")),
                    prompt = it.getString(it.getColumnIndexOrThrow("prompt")) ?: "",
                    negativePrompt = it.getString(it.getColumnIndexOrThrow("negative_prompt")) ?: "",
                    imagePath = it.getString(it.getColumnIndexOrThrow("image_path")) ?: "",
                    thumbnail = bytesToBitmap(it.getBlob(it.getColumnIndexOrThrow("thumbnail"))),
                    baseModel = it.getString(it.getColumnIndexOrThrow("base_model")) ?: "",
                    seed = it.getLong(it.getColumnIndexOrThrow("seed")),
                    steps = it.getInt(it.getColumnIndexOrThrow("steps")),
                    guidance = it.getFloat(it.getColumnIndexOrThrow("guidance")),
                    width = it.getInt(it.getColumnIndexOrThrow("width")),
                    height = it.getInt(it.getColumnIndexOrThrow("height")),
                    favorite = it.getInt(it.getColumnIndexOrThrow("favorite")) == 1,
                    createdAt = it.getLong(it.getColumnIndexOrThrow("created_at"))
                ))
            }
        }
        return items
    }
    
    private fun bitmapToBytes(bitmap: Bitmap?): ByteArray? {
        bitmap ?: return null
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
    
    private fun bytesToBitmap(bytes: ByteArray?): Bitmap? {
        bytes ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
    
    data class HistoryStats(val totalGenerations: Int, val favorites: Int, val todayGenerations: Int)
    
    private inner class HistoryDbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE history (
                    _id INTEGER PRIMARY KEY AUTOINCREMENT,
                    prompt TEXT NOT NULL,
                    negative_prompt TEXT,
                    image_path TEXT,
                    thumbnail BLOB,
                    base_model TEXT,
                    seed INTEGER,
                    steps INTEGER,
                    guidance REAL,
                    width INTEGER,
                    height INTEGER,
                    favorite INTEGER DEFAULT 0,
                    created_at INTEGER
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX idx_created ON history(created_at)")
            db.execSQL("CREATE INDEX idx_favorite ON history(favorite)")
        }
        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {}
    }
}
