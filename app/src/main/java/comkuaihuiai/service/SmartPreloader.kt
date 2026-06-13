package comkuaihuiai.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 可绘AI v3.5.0 - 智能预加载器
 */
class SmartPreloader(private val context: Context) {

    companion object {
        private const val TAG = "SmartPreloader"
    }
    
    enum class PreloadType { MODEL, LORA, VAE, UPSCALER }
    
    data class PreloadItem(
        val id: String,
        val type: PreloadType,
        val name: String,
        var priority: Int = 0,
        var weight: Float = 1f,
        var useCount: Int = 0,
        var lastUsed: Long = 0
    )
    
    data class PrewarmState(
        val isActive: Boolean = false,
        val progress: Float = 0f,
        val loadedCount: Int = 0
    )
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val prefs: SharedPreferences = context.getSharedPreferences("smart_preloader", Context.MODE_PRIVATE)
    
    private val preloadItems = ConcurrentHashMap<String, PreloadItem>()
    private val loadedItems = ConcurrentHashMap<String, Boolean>()
    
    private val _state = MutableStateFlow(PrewarmState())
    val state: StateFlow<PrewarmState> = _state.asStateFlow()
    
    init { initializeDefaults() }
    
    private fun initializeDefaults() {
        listOf(
            PreloadItem("sd15", PreloadType.MODEL, "SD 1.5", priority = 10),
            PreloadItem("sdxl", PreloadType.MODEL, "SDXL 1.0", priority = 8),
            PreloadItem("flux", PreloadType.MODEL, "Flux 1", priority = 6),
            PreloadItem("zimage", PreloadType.MODEL, "Z-Image", priority = 9)
        ).forEach { preloadItems[it.id] = it }
    }
    
    fun recordUsage(itemId: String) {
        preloadItems[itemId]?.let { item ->
            item.useCount += 1
            item.lastUsed = System.currentTimeMillis()
            recalculateWeights()
        }
    }
    
    suspend fun predictNext(): List<PreloadItem> = withContext(Dispatchers.Default) {
        preloadItems.values.sortedByDescending { it.weight }.take(5)
    }
    
    fun prewarm(priority: List<String>? = null) {
        scope.launch {
            _state.value = PrewarmState(isActive = true)
            val items = priority?.mapNotNull { preloadItems[it] } ?: preloadItems.values.toList()
            items.forEachIndexed { index, item ->
                _state.value = PrewarmState(isActive = true, progress = (index + 1).toFloat() / items.size, loadedCount = index)
                preloadItem(item)
                delay(200)
            }
            _state.value = PrewarmState()
        }
    }
    
    suspend fun preloadItem(item: PreloadItem): Boolean = withContext(Dispatchers.Default) {
        if (!loadedItems.containsKey(item.id)) {
            delay(500)
            loadedItems[item.id] = true
        }
        true
    }
    
    fun getLoadedItems(): List<String> = loadedItems.keys.toList()
    fun isLoaded(itemId: String): Boolean = loadedItems.contains(itemId)
    fun release() = scope.cancel()
    
    private fun recalculateWeights() {
        val maxUse = preloadItems.values.maxOfOrNull { it.useCount }?.coerceAtLeast(1) ?: 1
        preloadItems.values.forEach { item ->
            item.weight = (item.useCount.toFloat() / maxUse) * 0.6f + 0.4f
        }
    }
}
