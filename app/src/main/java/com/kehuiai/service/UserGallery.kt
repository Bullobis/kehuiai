package com.kehuiai.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * 可绘AI v3.5.0 - 用户画廊
 */
class UserGallery(private val context: Context) {

    companion object {
        private const val TAG = "UserGallery"
    }
    
    data class GalleryItem(
        val id: String,
        val imagePath: String,
        val thumbnail: Bitmap? = null,
        val prompt: String,
        val likes: Int = 0,
        val views: Int = 0,
        val comments: Int = 0,
        val isPublic: Boolean = false,
        val createdAt: Long = System.currentTimeMillis()
    )
    
    data class UserProfile(
        val name: String = "匿名用户",
        val avatar: Bitmap? = null,
        val totalWorks: Int = 0,
        val totalLikes: Int = 0,
        val followers: Int = 0,
        val following: Int = 0
    )
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _items = MutableStateFlow<List<GalleryItem>>(emptyList())
    val items: StateFlow<List<GalleryItem>> = _items.asStateFlow()
    
    private val _profile = MutableStateFlow(UserProfile())
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()
    
    private val _featured = MutableStateFlow<List<GalleryItem>>(emptyList())
    val featured: StateFlow<List<GalleryItem>> = _featured.asStateFlow()
    
    init {
        loadSampleData()
    }
    
    private fun loadSampleData() {
        val samples = listOf(
            GalleryItem("g1", "/img1.png", null, "梦幻少女", 128, 1024, 12),
            GalleryItem("g2", "/img2.png", null, "赛博朋克城市", 256, 2048, 24),
            GalleryItem("g3", "/img3.png", null, "古风美女", 189, 1536, 18)
        )
        _items.value = samples
        _featured.value = samples.sortedByDescending { it.likes }
    }
    
    suspend fun publishItem(item: GalleryItem): Boolean = withContext(Dispatchers.Default) {
        delay(500) // 模拟上传
        _items.value = _items.value + item.copy(isPublic = true)
        Log.i(TAG, "发布作品: ${item.id}")
        true
    }
    
    suspend fun likeItem(itemId: String): Boolean = withContext(Dispatchers.Default) {
        _items.value = _items.value.map { 
            if (it.id == itemId) it.copy(likes = it.likes + 1) else it 
        }
        true
    }
    
    suspend fun deleteItem(itemId: String): Boolean = withContext(Dispatchers.Default) {
        _items.value = _items.value.filter { it.id != itemId }
        true
    }
    
    fun getPublicGallery(): List<GalleryItem> = _items.value.filter { it.isPublic }
    
    fun getMyGallery(): List<GalleryItem> = _items.value
    
    suspend fun shareItem(itemId: String): String = withContext(Dispatchers.Default) {
        "https://kehuiai.com/share/$itemId"
    }
    
    fun updateProfile(profile: UserProfile) {
        _profile.value = profile
    }
    
    fun release() = scope.cancel()
}
