package comkuaihuiai.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * 可绘AI v3.5.0 - 提示词广场
 */
class PromptGallery(private val context: Context) {

    companion object {
        private const val TAG = "PromptGallery"
        private const val PREFS_NAME = "prompt_gallery"
    }
    
    data class PromptItem(
        val id: String,
        val title: String,
        val prompt: String,
        val negativePrompt: String = "",
        val category: String = "通用",
        val tags: List<String> = emptyList(),
        val author: String = "社区",
        val likes: Int = 0,
        val uses: Int = 0,
        val rating: Float = 0f,
        val imageUrl: String? = null,
        val createdAt: Long = System.currentTimeMillis()
    )
    
    data class Category(
        val name: String,
        val icon: String,
        val promptCount: Int
    )
    
    enum class SortBy { POPULAR, RECENT, RATING }
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _prompts = MutableStateFlow<List<PromptItem>>(emptyList())
    val prompts: StateFlow<List<PromptItem>> = _prompts.asStateFlow()
    
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()
    
    private val _searchResults = MutableSharedFlow<List<PromptItem>>()
    val searchResults: SharedFlow<List<PromptItem>> = _searchResults.asSharedFlow()
    
    init {
        loadBuiltInPrompts()
        loadUserPrompts()
    }
    
    private fun loadBuiltInPrompts() {
        val builtIn = listOf(
            // 人物类
            PromptItem("p1", "梦幻少女", "masterpiece, best quality, 1girl, dreamy, ethereal, soft lighting, flower field, flowing dress, golden hour", 
                "low quality, worst quality, blurry", "人像", listOf("梦幻", "少女", "唯美"), "官方", 328, 1250, 4.8f),
            PromptItem("p2", "赛博朋克", "masterpiece, best quality, 1girl, cyberpunk, neon lights, futuristic city, holographic, techwear, glowing eyes",
                "low quality, worst quality", "人像", listOf("赛博朋克", "科技感"), "官方", 512, 2340, 4.9f),
            PromptItem("p3", "古风美女", "masterpiece, best quality, 1chinese girl, hanfu, traditional chinese clothing, elegant, long black hair, palace background",
                "low quality, worst quality, western", "人像", listOf("古风", "中国风", "汉服"), "官方", 456, 1890, 4.7f),
            
            // 风景类
            PromptItem("l1", "日落海滩", "masterpiece, best quality, beautiful sunset beach, golden hour, palm trees, reflections on water, warm colors, cinematic",
                "low quality, worst quality, blurry", "风景", listOf("海滩", "日落", "温暖"), "官方", 234, 890, 4.6f),
            PromptItem("l2", "未来城市", "masterpiece, best quality, futuristic city, floating buildings, flying cars, neon signs, rain, cyberpunk aesthetic",
                "low quality, worst quality", "风景", listOf("科幻", "城市", "赛博朋克"), "官方", 345, 1560, 4.8f),
            PromptItem("l3", "樱花树下", "masterpiece, best quality, sakura trees, falling cherry blossoms, peaceful garden, soft pink colors, anime style",
                "low quality, worst quality", "风景", listOf("樱花", "日本", "唯美"), "官方", 289, 1100, 4.7f),
            
            // 艺术类
            PromptItem("a1", "油画肖像", "masterpiece, best quality, portrait, oil painting style, classical art, renaissance, detailed brushwork, warm tones",
                "low quality, worst quality", "艺术", listOf("油画", "古典", "肖像"), "官方", 198, 780, 4.5f),
            PromptItem("a2", "水墨山水", "masterpiece, best quality, chinese ink painting style, traditional landscape, mountains, mist, minimalistic, zen",
                "low quality, worst quality, western", "艺术", listOf("水墨", "中国风", "山水"), "官方", 267, 920, 4.6f),
            
            // 动漫类
            PromptItem("an1", "热血少年", "masterpiece, best quality, anime style, 1boy, dynamic pose, action scene, bright colors, shonen manga",
                "low quality, worst quality, realistic", "动漫", listOf("热血", "少年", "动作"), "官方", 312, 1450, 4.7f),
            PromptItem("an2", "魔法少女", "masterpiece, best quality, anime style, 1girl, magical girl, sparkles, transformation, cute outfit, fantasy",
                "low quality, worst quality", "动漫", listOf("魔法少女", "可爱", "魔法"), "官方", 389, 1680, 4.8f),
            
            // 风格类
            PromptItem("s1", "像素艺术", "pixel art style, 8-bit, retro game, vibrant colors, detailed, nostalgic gaming aesthetic",
                "low quality, worst quality, realistic", "风格", listOf("像素", "复古", "游戏"), "官方", 156, 650, 4.4f),
            PromptItem("s2", "浮世绘风格", "ukiyo-e style, traditional japanese woodblock print, waves, boat, muted colors, vintage aesthetic",
                "low quality, worst quality, modern", "风格", listOf("浮世绘", "日本", "传统"), "官方", 178, 720, 4.5f)
        )
        
        _prompts.value = builtIn
        updateCategories()
    }
    
    fun loadUserPrompts() {
        val json = prefs.getString("user_prompts", null) ?: return
        try {
            val array = JSONArray(json)
            val userPrompts = mutableListOf<PromptItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                userPrompts.add(PromptItem(
                    obj.getString("id"),
                    obj.getString("title"),
                    obj.getString("prompt"),
                    obj.optString("negativePrompt", ""),
                    obj.optString("category", "通用"),
                    obj.optJSONArray("tags")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
                    obj.optString("author", "我"),
                    obj.optInt("likes", 0),
                    obj.optInt("uses", 0),
                    obj.optDouble("rating", 0.0).toFloat(),
                    obj.optString("imageUrl", null),
                    obj.optLong("createdAt", System.currentTimeMillis())
                ))
            }
            _prompts.value = _prompts.value + userPrompts
            updateCategories()
        } catch (e: Exception) {
            Log.e(TAG, "加载用户提示词失败: ${e.message}")
        }
    }
    
    fun saveUserPrompt(item: PromptItem) {
        val userPrompts = _prompts.value.filter { it.author == "我" }.toMutableList()
        userPrompts.add(item.copy(id = "u_${System.currentTimeMillis()}", createdAt = System.currentTimeMillis()))
        
        val json = JSONArray()
        userPrompts.forEach { p ->
            json.put(JSONObject().apply {
                put("id", p.id)
                put("title", p.title)
                put("prompt", p.prompt)
                put("negativePrompt", p.negativePrompt)
                put("category", p.category)
                put("tags", JSONArray(p.tags))
                put("author", p.author)
                put("likes", p.likes)
                put("uses", p.uses)
                put("rating", p.rating)
                p.imageUrl?.let { put("imageUrl", it) }
                put("createdAt", p.createdAt)
            })
        }
        prefs.edit().putString("user_prompts", json.toString()).apply()
        _prompts.value = _prompts.value.filter { it.author != "我" } + userPrompts
        updateCategories()
    }
    
    suspend fun search(query: String, category: String? = null, sort: SortBy = SortBy.POPULAR): List<PromptItem> = 
        withContext(Dispatchers.Default) {
            var results = _prompts.value
            
            if (query.isNotEmpty()) {
                results = results.filter { 
                    it.prompt.contains(query, ignoreCase = true) || 
                    it.title.contains(query, ignoreCase = true) ||
                    it.tags.any { tag -> tag.contains(query, ignoreCase = true) }
                }
            }
            
            if (category != null) {
                results = results.filter { it.category == category }
            }
            
            results = when (sort) {
                SortBy.POPULAR -> results.sortedByDescending { it.uses }
                SortBy.RECENT -> results.sortedByDescending { it.createdAt }
                SortBy.RATING -> results.sortedByDescending { it.rating }
            }
            
            _searchResults.emit(results)
            results
        }
    
    fun getByCategory(category: String): List<PromptItem> = _prompts.value.filter { it.category == category }
    
    fun getRecommendations(count: Int = 5): List<PromptItem> {
        return _prompts.value.sortedByDescending { it.rating * 0.3f + it.likes * 0.2f + it.uses * 0.5f }.take(count)
    }
    
    fun likePrompt(id: String) {
        _prompts.value = _prompts.value.map { 
            if (it.id == id) it.copy(likes = it.likes + 1) else it 
        }
    }
    
    fun recordUse(id: String) {
        _prompts.value = _prompts.value.map { 
            if (it.id == id) it.copy(uses = it.uses + 1) else it 
        }
    }
    
    fun deleteUserPrompt(id: String) {
        _prompts.value = _prompts.value.filter { it.id != id || it.author != "我" }
        saveAllUserPrompts()
        updateCategories()
    }
    
    private fun saveAllUserPrompts() {
        val userPrompts = _prompts.value.filter { it.author == "我" }
        val json = JSONArray()
        userPrompts.forEach { p ->
            json.put(JSONObject().apply {
                put("id", p.id)
                put("title", p.title)
                put("prompt", p.prompt)
                put("negativePrompt", p.negativePrompt)
                put("category", p.category)
                put("tags", JSONArray(p.tags))
                put("author", p.author)
                put("likes", p.likes)
                put("uses", p.uses)
                put("rating", p.rating)
            })
        }
        prefs.edit().putString("user_prompts", json.toString()).apply()
    }
    
    private fun updateCategories() {
        val catMap = mutableMapOf<String, Int>()
        _prompts.value.forEach { 
            catMap[it.category] = catMap.getOrDefault(it.category, 0) + 1 
        }
        
        val icons = mapOf(
            "人像" to "👤", "风景" to "🏞️", "艺术" to "🎨", 
            "动漫" to "✨", "风格" to "🎭", "通用" to "📝"
        )
        
        _categories.value = catMap.map { Category(it.key, icons[it.key] ?: "📁", it.value) }
    }
    
    fun release() = scope.cancel()
}
