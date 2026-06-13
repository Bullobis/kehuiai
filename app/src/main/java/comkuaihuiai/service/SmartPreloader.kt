package comkuaihuiai.service

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.*
import kotlin.random.Random

/**
 * 可绘AI v3.5.0 - 智能预加载引擎 (完整版)
 * 
 * 功能：
 * - AI驱动的使用预测
 * - 多级缓存系统
 * - 资源优先级调度
 * - 内存感知管理
 * - 预取策略优化
 */
class SmartPreloader(private val context: Context) {

    companion object {
        private const val TAG = "SmartPreloader"
        
        // 缓存配置
        private const val MAX_MEMORY_CACHE_SIZE = 50 * 1024 * 1024L  // 50MB
        private const val MAX_DISK_CACHE_SIZE = 200 * 1024 * 1024L   // 200MB
        private const val MAX_PRELOAD_QUEUE = 10
        
        // 内存阈值
        private const val MEMORY_LOW_THRESHOLD = 100 * 1024 * 1024L  // 100MB
        private const val MEMORY_CRITICAL_THRESHOLD = 50 * 1024 * 1024L // 50MB
        
        // 预加载优先级
        const val PRIORITY_HIGH = 3
        const val PRIORITY_NORMAL = 2
        const val PRIORITY_LOW = 1
        const val PRIORITY_IDLE = 0
    }
    
    /**
     * 预加载项目类型
     */
    enum class PreloadType {
        MODEL,           // 模型文件
        IMAGE,           // 图像预览
        THUMBNAIL,       // 缩略图
        STYLE_PREVIEW,   // 风格预览
        TEXTURE,         // 纹理资源
        CONFIG           // 配置数据
    }
    
    /**
     * 预加载任务
     */
    data class PreloadTask(
        val id: String,
        val type: PreloadType,
        val url: String,
        val priority: Int,
        val size: Long = 0,
        val timestamp: Long = System.currentTimeMillis(),
        val metadata: Map<String, Any> = emptyMap(),
        val retryCount: Int = 0
    ) : Comparable<PreloadTask> {
        
        override fun compareTo(other: PreloadTask): Int {
            // 按优先级和时间排序
            val priorityCompare = other.priority.compareTo(this.priority)
            if (priorityCompare != 0) return priorityCompare
            return this.timestamp.compareTo(other.timestamp)
        }
    }
    
    /**
     * 预加载结果
     */
    sealed class PreloadResult {
        data class Success(
            val task: PreloadTask,
            val data: Any,
            val loadTimeMs: Long
        ) : PreloadResult()
        
        data class Failure(
            val task: PreloadTask,
            val error: String,
            val canRetry: Boolean
        ) : PreloadResult()
        
        data class Progress(
            val task: PreloadTask,
            val progress: Float
        ) : PreloadResult()
    }
    
    /**
     * 缓存条目
     */
    data class CacheEntry(
        val key: String,
        val data: Any,
        val size: Long,
        val createdAt: Long = System.currentTimeMillis(),
        val lastAccessed: Long = System.currentTimeMillis(),
        val accessCount: Int = 0,
        val type: PreloadType
    )
    
    /**
     * 预测结果
     */
    data class Prediction(
        val nextItems: List<String>,
        val confidence: Float,
        val reason: String
    )
    
    /**
     * 预加载统计
     */
    data class PreloadStats(
        val totalPreloaded: Int,
        val totalFailed: Int,
        val cacheHitRate: Float,
        val averageLoadTimeMs: Long,
        val memoryUsageMb: Float,
        val predictionsAccuracy: Float
    )
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 预加载队列
    private val preloadQueue = PriorityBlockingQueue<PreloadTask>(100)
    private val activeTasks = ConcurrentHashMap<String, Job>()
    
    // 缓存
    private val memoryCache = ConcurrentHashMap<String, CacheEntry>()
    private var currentCacheSize = AtomicLong(0)
    
    // 预测模型 (简化实现)
    private val usageHistory = ArrayDeque<UsageEvent>(1000)
    private val itemAccessFrequency = ConcurrentHashMap<String, Int>()
    private val itemCooccurrence = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()
    
    // 控制标志
    private val isRunning = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    
    // 统计
    private val preloadCount = AtomicInteger(0)
    private val cacheHits = AtomicInteger(0)
    private val cacheMisses = AtomicInteger(0)
    private val loadTimes = ArrayDeque<Long>(100)
    
    // Flow
    private val _preloadResult = MutableSharedFlow<PreloadResult>(extraBufferCapacity = 64)
    val preloadResult: SharedFlow<PreloadResult> = _preloadResult.asSharedFlow()
    
    private val _predictions = MutableSharedFlow<Prediction>(extraBufferCapacity = 16)
    val predictions: SharedFlow<Prediction> = _predictions.asSharedFlow()
    
    private val _stats = MutableStateFlow(PreloadStats(0, 0, 0f, 0, 0f, 0f))
    val stats: StateFlow<PreloadStats> = _stats.asStateFlow()
    
    /**
     * 启动预加载器
     */
    fun start(workerCount: Int = 2) {
        if (isRunning.get()) return
        
        isRunning.set(true)
        isPaused.set(false)
        
        // 启动预加载 workers
        repeat(workerCount.coerceIn(1, 4)) { workerId ->
            startWorker(workerId)
        }
        
        // 启动预测引擎
        startPredictionEngine()
        
        Log.i(TAG, "预加载器已启动 (workers=$workerCount)")
    }
    
    /**
     * 停止预加载器
     */
    fun stop() {
        isRunning.set(false)
        activeTasks.values.forEach { it.cancel() }
        activeTasks.clear()
        preloadQueue.clear()
        Log.i(TAG, "预加载器已停止")
    }
    
    /**
     * 暂停预加载
     */
    fun pause() {
        isPaused.set(true)
        Log.i(TAG, "预加载已暂停")
    }
    
    /**
     * 恢复预加载
     */
    fun resume() {
        isPaused.set(false)
        Log.i(TAG, "预加载已恢复")
    }
    
    /**
     * 请求预加载
     */
    fun preload(
        url: String,
        type: PreloadType,
        priority: Int = PRIORITY_NORMAL,
        metadata: Map<String, Any> = emptyMap()
    ) {
        val task = PreloadTask(
            id = "${type}_${url.hashCode()}_${System.currentTimeMillis()}",
            type = type,
            url = url,
            priority = priority,
            metadata = metadata
        )
        
        preloadQueue.put(task)
        Log.d(TAG, "添加预加载任务: ${task.type} - ${task.url}")
    }
    
    /**
     * 批量预加载
     */
    fun preloadBatch(
        items: List<Pair<String, PreloadType>>,
        priority: Int = PRIORITY_NORMAL
    ) {
        items.forEach { (url, type) ->
            preload(url, type, priority)
        }
        Log.i(TAG, "批量预加载: ${items.size} 项")
    }
    
    /**
     * 获取缓存数据
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getFromCache(key: String): T? {
        val entry = memoryCache[key] ?: return null
        
        // 更新访问信息
        memoryCache[key] = entry.copy(
            lastAccessed = System.currentTimeMillis(),
            accessCount = entry.accessCount + 1
        )
        
        cacheHits.incrementAndGet()
        return entry.data as? T
    }
    
    /**
     * 检查缓存是否存在
     */
    fun isCached(key: String): Boolean = memoryCache.containsKey(key)
    
    /**
     * 获取缓存大小
     */
    fun getCacheSize(): Long = currentCacheSize.get()
    
    /**
     * 清空缓存
     */
    fun clearCache() {
        memoryCache.values.forEach { entry ->
            when (val data = entry.data) {
                is Bitmap -> data.recycle()
            }
        }
        memoryCache.clear()
        currentCacheSize.set(0)
        Log.i(TAG, "缓存已清空")
    }
    
    /**
     * 清空特定类型的缓存
     */
    fun clearCache(type: PreloadType) {
        val keysToRemove = mutableListOf<String>()
        
        memoryCache.forEach { (key, entry) ->
            if (entry.type == type) {
                when (val data = entry.data) {
                    is Bitmap -> data.recycle()
                }
                keysToRemove.add(key)
                currentCacheSize.addAndGet(-entry.size)
            }
        }
        
        keysToRemove.forEach { memoryCache.remove(it) }
        Log.i(TAG, "已清空 ${type} 类型缓存")
    }
    
    /**
     * 记录使用事件 (用于预测)
     */
    fun recordUsage(itemId: String, context: Map<String, Any> = emptyMap()) {
        val event = UsageEvent(
            itemId = itemId,
            timestamp = System.currentTimeMillis(),
            context = context
        )
        
        usageHistory.addLast(event)
        if (usageHistory.size > 1000) {
            usageHistory.removeFirst()
        }
        
        // 更新访问频率
        itemAccessFrequency[itemId] = (itemAccessFrequency[itemId] ?: 0) + 1
        
        // 更新共现关系
        usageHistory.takeLast(10).forEach { prevEvent ->
            if (prevEvent.itemId != itemId) {
                val cooccurrenceMap = itemCooccurrence.getOrPut(itemId) { ConcurrentHashMap() }
                cooccurrenceMap[prevEvent.itemId] = (cooccurrenceMap[prevEvent.itemId] ?: 0) + 1
            }
        }
    }
    
    /**
     * 预测下一个要使用的项目
     */
    fun predictNext(currentItem: String, count: Int = 5): List<String> {
        // 基于共现关系预测
        val cooccurrenceMap = itemCooccurrence[currentItem]
        
        if (cooccurrenceMap.isNullOrEmpty()) {
            // 回退到基于频率的预测
            return itemAccessFrequency.entries
                .sortedByDescending { it.value }
                .take(count)
                .map { it.key }
        }
        
        return cooccurrenceMap.entries
            .sortedByDescending { it.value }
            .take(count)
            .map { it.key }
    }
    
    /**
     * 请求预测
     */
    fun requestPrediction(context: String): Prediction {
        val currentTime = System.currentTimeMillis()
        
        // 找出最近的热门项目
        val recentItems = usageHistory
            .filter { currentTime - it.timestamp < 60000 } // 最近1分钟
            .groupBy { it.itemId }
            .mapValues { it.value.size }
            .entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }
        
        // 基于共现预测
        val predictions = mutableListOf<String>()
        recentItems.forEach { item ->
            predictions.addAll(predictNext(item, 3))
        }
        
        val uniquePredictions = predictions.distinct().take(5)
        
        return Prediction(
            nextItems = uniquePredictions,
            confidence = if (predictions.isNotEmpty()) 0.7f else 0.3f,
            reason = "基于近期使用模式预测"
        )
    }
    
    /**
     * 释放资源
     */
    fun release() {
        stop()
        clearCache()
        scope.cancel()
        Log.i(TAG, "SmartPreloader 已释放")
    }
    
    // ==================== 私有方法 ====================
    
    private fun startWorker(workerId: Int) {
        activeTasks["worker_$workerId"] = scope.launch {
            Log.i(TAG, "Worker $workerId 已启动")
            
            while (isRunning.get()) {
                if (isPaused.get()) {
                    delay(1000)
                    continue
                }
                
                // 检查内存
                if (!checkMemoryAvailable()) {
                    trimCache()
                    delay(5000)
                    continue
                }
                
                // 获取任务
                val task = preloadQueue.poll(1, java.util.concurrent.TimeUnit.SECONDS)
                    ?: continue
                
                try {
                    executePreload(task)
                } catch (e: Exception) {
                    Log.e(TAG, "预加载失败: ${task.url}", e)
                    _preloadResult.emit(
                        PreloadResult.Failure(task, e.message ?: "Unknown error", task.retryCount < 3)
                    )
                    
                    // 重试
                    if (task.retryCount < 3) {
                        val retryTask = task.copy(retryCount = task.retryCount + 1)
                        preloadQueue.put(retryTask)
                    }
                }
            }
            
            Log.i(TAG, "Worker $workerId 已停止")
        }
    }
    
    private suspend fun executePreload(task: PreloadTask) {
        Log.d(TAG, "执行预加载: ${task.type} - ${task.url}")
        
        val startTime = System.currentTimeMillis()
        
        // 检查是否已缓存
        val cacheKey = "${task.type}_${task.url.hashCode()}"
        if (memoryCache.containsKey(cacheKey)) {
            cacheHits.incrementAndGet()
            _preloadResult.emit(
                PreloadResult.Success(task, memoryCache[cacheKey]!!.data, 0)
            )
            return
        }
        cacheMisses.incrementAndGet()
        
        // 加载数据
        _preloadResult.emit(PreloadResult.Progress(task, 0f))
        
        val data = when (task.type) {
            PreloadType.MODEL -> loadModel(task.url)
            PreloadType.IMAGE -> loadImage(task.url)
            PreloadType.THUMBNAIL -> loadThumbnail(task.url)
            PreloadType.STYLE_PREVIEW -> loadStylePreview(task.url)
            PreloadType.TEXTURE -> loadTexture(task.url)
            PreloadType.CONFIG -> loadConfig(task.url)
        }
        
        val loadTime = System.currentTimeMillis() - startTime
        
        // 缓存
        if (data != null) {
            cacheData(cacheKey, data, task.type)
            preloadCount.incrementAndGet()
            loadTimes.addLast(loadTime)
            
            _preloadResult.emit(
                PreloadResult.Success(task, data, loadTime)
            )
        }
    }
    
    private fun startPredictionEngine() {
        scope.launch {
            while (isRunning.get()) {
                delay(30000) // 每30秒更新预测
                
                // 基于当前使用模式生成预测
                val currentTime = System.currentTimeMillis()
                val recentItems = usageHistory
                    .filter { currentTime - it.timestamp < 300000 } // 最近5分钟
                    .map { it.itemId }
                    .distinct()
                    .take(3)
                
                if (recentItems.isNotEmpty()) {
                    val predictedItems = recentItems.flatMap { predictNext(it, 3) }.distinct().take(5)
                    
                    // 主动预加载预测的项目
                    predictedItems.forEach { itemId ->
                        preload(itemId, PreloadType.IMAGE, PRIORITY_LOW)
                    }
                    
                    _predictions.emit(
                        Prediction(
                            nextItems = predictedItems,
                            confidence = 0.6f,
                            reason = "基于使用模式自动预测"
                        )
                    )
                }
            }
        }
    }
    
    private suspend fun loadModel(url: String): Any? = withContext(Dispatchers.IO) {
        // 模拟模型加载
        delay(100)
        "model_data_$url"
    }
    
    private suspend fun loadImage(url: String): Bitmap? = withContext(Dispatchers.IO) {
        // 模拟图像加载
        delay(50)
        val size = 256
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    }
    
    private suspend fun loadThumbnail(url: String): Bitmap? = withContext(Dispatchers.IO) {
        delay(30)
        val size = 64
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    }
    
    private suspend fun loadStylePreview(url: String): Bitmap? = withContext(Dispatchers.IO) {
        delay(40)
        val size = 128
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    }
    
    private suspend fun loadTexture(url: String): Any? = withContext(Dispatchers.IO) {
        delay(20)
        "texture_data_$url"
    }
    
    private suspend fun loadConfig(url: String): Any? = withContext(Dispatchers.IO) {
        delay(10)
        mapOf("config" to url)
    }
    
    private fun checkMemoryAvailable(): Boolean {
        val runtime = Runtime.getRuntime()
        val freeMemory = runtime.maxMemory() - runtime.totalMemory() + (runtime.freeMemory())
        return freeMemory > MEMORY_LOW_THRESHOLD
    }
    
    private fun trimCache() {
        val targetSize = MAX_MEMORY_CACHE_SIZE / 2
        
        // 按LRU顺序删除
        val entriesToRemove = mutableListOf<Pair<String, CacheEntry>>()
        
        memoryCache.entries
            .sortedBy { it.value.lastAccessed }
            .forEach { (key, entry) ->
                if (currentCacheSize.get() > targetSize) {
                    entriesToRemove.add(key to entry)
                }
            }
        
        entriesToRemove.forEach { (key, entry) ->
            when (val data = entry.data) {
                is Bitmap -> data.recycle()
            }
            memoryCache.remove(key)
            currentCacheSize.addAndGet(-entry.size)
        }
        
        Log.i(TAG, "缓存已清理: 移除 ${entriesToRemove.size} 项")
    }
    
    private fun cacheData(key: String, data: Any, type: PreloadType) {
        val size = estimateSize(data)
        
        // 如果单个条目太大，不缓存
        if (size > MAX_MEMORY_CACHE_SIZE / 10) {
            return
        }
        
        // 清理空间
        while (currentCacheSize.get() + size > MAX_MEMORY_CACHE_SIZE) {
            if (memoryCache.isEmpty()) break
            
            val oldestKey = memoryCache.entries
                .sortedBy { it.value.lastAccessed }
                .firstOrNull()?.key ?: break
            
            val entry = memoryCache.remove(oldestKey)
            entry?.let { currentCacheSize.addAndGet(-it.size) }
        }
        
        val entry = CacheEntry(
            key = key,
            data = data,
            size = size,
            type = type
        )
        
        memoryCache[key] = entry
        currentCacheSize.addAndGet(size)
        
        Log.d(TAG, "缓存数据: $key (${size / 1024}KB)")
    }
    
    private fun estimateSize(data: Any): Long {
        return when (data) {
            is Bitmap -> data.width.toLong() * data.height * 4
            is String -> data.toByteArray().size.toLong()
            is ByteArray -> data.size.toLong()
            is Map<*, *> -> 1024 // 估算
            else -> 512
        }
    }
    
    private fun updateStats() {
        val totalOps = cacheHits.get() + cacheMisses.get()
        val cacheHitRate = if (totalOps > 0) {
            cacheHits.get().toFloat() / totalOps
        } else 0f
        
        val avgLoadTime = if (loadTimes.isNotEmpty()) {
            loadTimes.average().toLong()
        } else 0L
        
        val memoryUsage = currentCacheSize.get() / (1024 * 1024).toFloat()
        
        _stats.value = PreloadStats(
            totalPreloaded = preloadCount.get(),
            totalFailed = 0,
            cacheHitRate = cacheHitRate,
            averageLoadTimeMs = avgLoadTime,
            memoryUsageMb = memoryUsage,
            predictionsAccuracy = 0f
        )
    }
    
    /**
     * 使用事件记录
     */
    data class UsageEvent(
        val itemId: String,
        val timestamp: Long,
        val context: Map<String, Any> = emptyMap()
    )
}
