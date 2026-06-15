package com.kehuiai.service

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
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * 可绘AI v3.5.0 - 收藏系统
 */
class FavoritesManager(private val context: Context) {

    companion object {
        private const val TAG = "FavoritesManager"
        private const val DB_NAME = "favorites.db"
        private const val DB_VERSION = 1
    }
    
    data class FavoriteItem(
        val id: Long = 0,
        val prompt: String,
        val negativePrompt: String = "",
        val imagePath: String = "",
        val thumbnail: Bitmap? = null,
        val tags: List<String> = emptyList(),
        val category: String = "默认",
        val rating: Int = 0,
        val notes: String = "",
        val createdAt: Long = System.currentTimeMillis()
    )
    
    data class Category(
        val name: String,
        val count: Int,
        val icon: String = "📁"
    )
    
    enum class SortOrder { DATE_DESC, DATE_ASC, RATING_DESC, PROMPT_ASC }
    
    private val dbHelper = FavoritesDbHelper(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _favorites = MutableStateFlow<List<FavoriteItem>>(emptyList())
    val favorites: StateFlow<List<FavoriteItem>> = _favorites.asStateFlow()
    
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()
    
    init { refreshAll() }
    
    fun addFavorite(item: FavoriteItem): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("prompt", item.prompt)
            put("negative_prompt", item.negativePrompt)
            put("image_path", item.imagePath)
            item.thumbnail?.let { put("thumbnail", bitmapToBytes(it)) }
            put("tags", item.tags.joinToString(","))
            put("category", item.category)
            put("rating", item.rating)
            put("notes", item.notes)
            put("created_at", item.createdAt)
        }
        val id = db.insert("favorites", null, values)
        refreshAll()
        Log.i(TAG, "添加收藏: ID=$id")
        return id
    }
    
    fun updateFavorite(item: FavoriteItem): Boolean {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("prompt", item.prompt)
            put("negative_prompt", item.negativePrompt)
            put("image_path", item.imagePath)
            item.thumbnail?.let { put("thumbnail", bitmapToBytes(it)) }
            put("tags", item.tags.joinToString(","))
            put("category", item.category)
            put("rating", item.rating)
            put("notes", item.notes)
        }
        val rows = db.update("favorites", values, "_id = ?", arrayOf(item.id.toString()))
        refreshAll()
        return rows > 0
    }
    
    fun deleteFavorite(id: Long): Boolean {
        val db = dbHelper.writableDatabase
        val rows = db.delete("favorites", "_id = ?", arrayOf(id.toString()))
        refreshAll()
        return rows > 0
    }
    
    fun getFavorites(
        category: String? = null,
        tag: String? = null,
        searchQuery: String? = null,
        sort: SortOrder = SortOrder.DATE_DESC,
        limit: Int = 100
    ): List<FavoriteItem> {
        val db = dbHelper.readableDatabase
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()
        
        category?.let { conditions.add("category = ?"); args.add(it) }
        tag?.let { conditions.add("tags LIKE ?"); args.add("%$it%") }
        searchQuery?.let { conditions.add("prompt LIKE ?"); args.add("%$it%") }
        
        val selection = if (conditions.isEmpty()) null else conditions.joinToString(" AND ")
        val selectionArgs = if (args.isEmpty()) null else args.toTypedArray()
        val orderBy = when (sort) {
            SortOrder.DATE_DESC -> "created_at DESC"
            SortOrder.DATE_ASC -> "created_at ASC"
            SortOrder.RATING_DESC -> "rating DESC"
            SortOrder.PROMPT_ASC -> "prompt ASC"
        }
        
        val cursor = db.query("favorites", null, selection, selectionArgs, null, null, orderBy, "$limit")
        return cursorToList(cursor)
    }
    
    fun getById(id: Long): FavoriteItem? {
        val db = dbHelper.readableDatabase
        val cursor = db.query("favorites", null, "_id = ?", arrayOf(id.toString()), null, null, null)
        return cursorToList(cursor).firstOrNull()
    }
    
    fun updateRating(id: Long, rating: Int): Boolean {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply { put("rating", rating.coerceIn(0, 5)) }
        val rows = db.update("favorites", values, "_id = ?", arrayOf(id.toString()))
        refreshAll()
        return rows > 0
    }
    
    fun getTopRated(limit: Int = 10): List<FavoriteItem> = getFavorites(sort = SortOrder.RATING_DESC, limit = limit)
    
    fun refreshAll() {
        scope.launch {
            _favorites.value = getFavorites()
            _categories.value = getCategories()
        }
    }
    
    fun release() {
        scope.cancel()
        dbHelper.close()
    }
    
    private fun getCategories(): List<Category> {
        val db = dbHelper.readableDatabase
        val cats = mutableMapOf<String, Int>()
        val cursor = db.rawQuery("SELECT category, COUNT(*) FROM favorites GROUP BY category", null)
        cursor.use {
            while (it.moveToNext()) {
                cats[it.getString(0) ?: "默认"] = it.getInt(1)
            }
        }
        return cats.map { Category(it.key, it.value) }
    }
    
    private fun cursorToList(cursor: Cursor): List<FavoriteItem> {
        val items = mutableListOf<FavoriteItem>()
        cursor.use {
            while (it.moveToNext()) {
                val tagsStr = it.getString(it.getColumnIndexOrThrow("tags")) ?: ""
                items.add(FavoriteItem(
                    id = it.getLong(it.getColumnIndexOrThrow("_id")),
                    prompt = it.getString(it.getColumnIndexOrThrow("prompt")) ?: "",
                    negativePrompt = it.getString(it.getColumnIndexOrThrow("negative_prompt")) ?: "",
                    imagePath = it.getString(it.getColumnIndexOrThrow("image_path")) ?: "",
                    thumbnail = bytesToBitmap(it.getBlob(it.getColumnIndexOrThrow("thumbnail"))),
                    tags = if (tagsStr.isNotEmpty()) tagsStr.split(",") else emptyList(),
                    category = it.getString(it.getColumnIndexOrThrow("category")) ?: "默认",
                    rating = it.getInt(it.getColumnIndexOrThrow("rating")),
                    notes = it.getString(it.getColumnIndexOrThrow("notes")) ?: "",
                    createdAt = it.getLong(it.getColumnIndexOrThrow("created_at"))
                ))
            }
        }
        return items
    }
    
    private fun bitmapToBytes(bitmap: Bitmap?): ByteArray? {
        bitmap ?: return null
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
    
    private fun bytesToBitmap(bytes: ByteArray?): Bitmap? {
        bytes ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
    
    private inner class FavoritesDbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE favorites (
                    _id INTEGER PRIMARY KEY AUTOINCREMENT,
                    prompt TEXT NOT NULL,
                    negative_prompt TEXT,
                    image_path TEXT,
                    thumbnail BLOB,
                    tags TEXT,
                    category TEXT DEFAULT '默认',
                    rating INTEGER DEFAULT 0,
                    notes TEXT,
                    created_at INTEGER
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX idx_category ON favorites(category)")
            db.execSQL("CREATE INDEX idx_rating ON favorites(rating)")
        }
        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {}
    }
}
