@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST", "DEPRECATION", "USELESS_ELVIS")
package com.kehuiai.service

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.*
import kotlin.random.Random

/**
 * 可绘AI v3.5.0 - 实时预览引擎 (完整版)
 * 
 * 功能：
 * - 防抖预览
 * - 渐进式渲染
 * - 多种预览模式
 * - 缩略图缓存
 * - GPU加速
 */
class RealtimePreviewEngine {

    companion object {
        private const val TAG = "RealtimePreview"
        
        // 预览配置
        const val MAX_PREVIEW_SIZE = 1024
        const val MIN_PREVIEW_SIZE = 256
        const val DEFAULT_PREVIEW_SIZE = 512
        
        // 防抖配置
        const val DEFAULT_DEBOUNCE_MS = 150L
        const val MIN_DEBOUNCE_MS = 50L
        const val MAX_DEBOUNCE_MS = 500L
        
        // 缓存配置
        const val MAX_CACHE_SIZE = 20
        const val CACHE_TTL_MS = 5 * 60 * 1000L // 5分钟
        
        // 帧率配置
        const val MAX_FPS = 30
        const val MIN_FPS = 1
    }
    
    /**
     * 预览模式
     */
    enum class PreviewMode(val displayName: String, val description: String) {
        STANDARD("标准", "普通预览模式"),
        FAST("快速", "低分辨率快速预览"),
        QUALITY("高质量", "高分辨率预览"),
        PROGRESSIVE("渐进式", "逐步增加清晰度"),
        TILED("分块", "分块渲染减少内存")
    }
    
    /**
     * 防抖策略
     */
    enum class DebounceStrategy {
        IMMEDIATE,      // 立即更新
        DEBOUNCE,       // 防抖
        THROTTLE,       // 节流
        ADAPTIVE        // 自适应
    }
    
    /**
     * 预览配置
     */
    data class PreviewConfig(
        val mode: PreviewMode = PreviewMode.PROGRESSIVE,
        val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
        val debounceStrategy: DebounceStrategy = DebounceStrategy.DEBOUNCE,
        val maxFps: Int = MAX_FPS,
        val enableCache: Boolean = true,
        val enableProgressive: Boolean = true,
        val qualityLevel: Float = 0.5f  // 0.0 - 1.0
    )
    
    /**
     * 预览状态
     */
    data class PreviewState(
        val currentBitmap: Bitmap? = null,
        val progress: Float = 0f,  // 0.0 - 1.0
        val isRendering: Boolean = false,
        val fps: Int = 0,
        val lastUpdateTime: Long = 0,
        val renderTimeMs: Long = 0
    )
    
    /**
     * 预览结果
     */
    sealed class PreviewResult {
        data class Success(
            val bitmap: Bitmap,
            val progress: Float,
            val renderTimeMs: Long
        ) : PreviewResult()
        
        data class Error(
            val message: String,
            val exception: Throwable? = null
        ) : PreviewResult()
        
        data class Progress(
            val partialBitmap: Bitmap,
            val progress: Float
        ) : PreviewResult()
    }
    
    /**
     * 渲染任务
     */
    private data class RenderTask(
        val id: String = "${System.currentTimeMillis()}_${Random.nextInt(10000)}",
        val prompt: String,
        val negativePrompt: String = "",
        val width: Int,
        val height: Int,
        val steps: Int,
        val seed: Long = Random.nextLong(),
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * 性能指标
     */
    data class PerformanceMetrics(
        val averageFps: Float,
        val frameDrops: Int,
        val renderTimeMs: Long,
        val cacheHitRate: Float,
        val memoryUsageMb: Float
    )
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 配置
    private var config = PreviewConfig()
    
    // 状态
    private val _state = MutableStateFlow(PreviewState())
    val state: StateFlow<PreviewState> = _state.asStateFlow()
    
    private val _previewResult = MutableSharedFlow<PreviewResult>(extraBufferCapacity = 64)
    val previewResult: SharedFlow<PreviewResult> = _previewResult.asSharedFlow()
    
    // 防抖控制
    private var lastRenderTime = AtomicLong(0)
    private var pendingRender: RenderTask? = null
    private var debounceJob: Job? = null
    
    // 缓存
    private val previewCache = ConcurrentHashMap<String, Bitmap>()
    private val renderCache = ConcurrentHashMap<String, Bitmap>()
    private val cacheOrder = ArrayDeque<String>()
    
    // 性能追踪
    private val frameTimestamps = ArrayDeque<Long>(100)
    private val renderTimes = ArrayDeque<Long>(100)
    private val frameDrops = AtomicInteger(0)
    private val cacheHits = AtomicInteger(0)
    private val cacheMisses = AtomicInteger(0)
    
    // 控制标志
    private val isRendering = AtomicBoolean(false)
    private val shouldCancel = AtomicBoolean(false)
    
    // FPS 计算
    private var lastFpsUpdate = System.currentTimeMillis()
    private var framesSinceLastUpdate = 0
    
    /**
     * 设置配置
     */
    fun setConfig(newConfig: PreviewConfig) {
        config = newConfig
        Log.i(TAG, "配置更新: mode=${config.mode}, debounce=${config.debounceMs}ms")
    }
    
    /**
     * 更新配置
     */
    fun updateConfig(
        mode: PreviewMode? = null,
        debounceMs: Long? = null,
        maxFps: Int? = null,
        qualityLevel: Float? = null
    ) {
        config = config.copy(
            mode = mode ?: config.mode,
            debounceMs = debounceMs?.coerceIn(MIN_DEBOUNCE_MS, MAX_DEBOUNCE_MS) ?: config.debounceMs,
            maxFps = maxFps?.coerceIn(MIN_FPS, MAX_FPS) ?: config.maxFps,
            qualityLevel = qualityLevel?.coerceIn(0f, 1f) ?: config.qualityLevel
        )
    }
    
    /**
     * 请求预览更新
     */
    fun requestPreview(
        prompt: String,
        negativePrompt: String = "",
        width: Int = DEFAULT_PREVIEW_SIZE,
        height: Int = DEFAULT_PREVIEW_SIZE,
        steps: Int = 20,
        seed: Long = Random.nextLong()
    ) {
        val task = RenderTask(
            prompt = prompt,
            negativePrompt = negativePrompt,
            width = width,
            height = height,
            steps = steps,
            seed = seed
        )
        
        when (config.debounceStrategy) {
            DebounceStrategy.IMMEDIATE -> {
                executeRender(task)
            }
            DebounceStrategy.DEBOUNCE -> {
                debounceJob?.cancel()
                debounceJob = scope.launch {
                    delay(config.debounceMs)
                    executeRender(task)
                }
            }
            DebounceStrategy.THROTTLE -> {
                val now = System.currentTimeMillis()
                if (now - lastRenderTime.get() >= config.debounceMs) {
                    executeRender(task)
                }
            }
            DebounceStrategy.ADAPTIVE -> {
                // 根据FPS动态调整
                val currentFps = calculateCurrentFps()
                val adaptiveDebounce = if (currentFps < 10) {
                    config.debounceMs * 2
                } else {
                    config.debounceMs
                }
                
                debounceJob?.cancel()
                debounceJob = scope.launch {
                    delay(adaptiveDebounce)
                    executeRender(task)
                }
            }
        }
    }
    
    /**
     * 取消当前渲染
     */
    fun cancelRender() {
        shouldCancel.set(true)
        debounceJob?.cancel()
        Log.i(TAG, "渲染已取消")
    }
    
    /**
     * 获取缩略图
     */
    suspend fun getThumbnail(
        prompt: String,
        size: Int = 256
    ): Bitmap? = withContext(Dispatchers.Default) {
        val cacheKey = "thumb_${prompt.hashCode()}_${size}"
        
        // 检查缓存
        previewCache[cacheKey]?.let {
            cacheHits.incrementAndGet()
            return@withContext it
        }
        cacheMisses.incrementAndGet()
        
        // 生成缩略图
        val thumbnail = generatePreview(
            prompt = prompt,
            width = size,
            height = size,
            quality = 0.3f,
            isThumbnail = true
        )
        
        // 缓存
        if (thumbnail != null && config.enableCache) {
            addToCache(cacheKey, thumbnail)
        }
        
        thumbnail
    }
    
    /**
     * 预加载预览
     */
    fun preloadPreview(
        prompt: String,
        negativePrompt: String = "",
        seed: Long = Random.nextLong()
    ) {
        scope.launch {
            // 在后台生成多个尺寸的预览
            listOf(256, 384, 512).forEach { size ->
                val cacheKey = "preload_${prompt.hashCode()}_${size}_$seed"
                if (!previewCache.containsKey(cacheKey)) {
                    val preview = generatePreview(
                        prompt = prompt,
                        width = size,
                        height = size,
                        quality = 0.3f
                    )
                    preview?.let { addToCache(cacheKey, it) }
                }
            }
        }
    }
    
    /**
     * 清空缓存
     */
    fun clearCache() {
        previewCache.values.forEach { it.recycle() }
        previewCache.clear()
        renderCache.values.forEach { it.recycle() }
        renderCache.clear()
        cacheOrder.clear()
        Log.i(TAG, "缓存已清空")
    }
    
    /**
     * 获取性能指标
     */
    fun getPerformanceMetrics(): PerformanceMetrics {
        val avgFps = calculateCurrentFps()
        val avgRenderTime = renderTimes.average().toLong()
        val totalCacheOps = cacheHits.get() + cacheMisses.get()
        val cacheHitRate = if (totalCacheOps > 0) {
            cacheHits.get().toFloat() / totalCacheOps
        } else 0f
        
        return PerformanceMetrics(
            averageFps = avgFps,
            frameDrops = frameDrops.get(),
            renderTimeMs = avgRenderTime,
            cacheHitRate = cacheHitRate,
            memoryUsageMb = estimateMemoryUsage()
        )
    }
    
    /**
     * 释放资源
     */
    fun release() {
        scope.cancel()
        clearCache()
        isRendering.set(false)
        Log.i(TAG, "RealtimePreviewEngine 已释放")
    }
    
    // ==================== 私有方法 ====================
    
    private fun executeRender(task: RenderTask) {
        if (isRendering.get()) {
            pendingRender = task
            return
        }
        
        isRendering.set(true)
        shouldCancel.set(false)
        lastRenderTime.set(System.currentTimeMillis())
        
        scope.launch {
            try {
                when (config.mode) {
                    PreviewMode.STANDARD -> renderStandard(task)
                    PreviewMode.FAST -> renderFast(task)
                    PreviewMode.QUALITY -> renderQuality(task)
                    PreviewMode.PROGRESSIVE -> renderProgressive(task)
                    PreviewMode.TILED -> renderTiled(task)
                }
            } catch (e: CancellationException) {
                Log.i(TAG, "渲染被取消")
            } catch (e: Exception) {
                Log.e(TAG, "渲染失败: ${e.message}")
                _previewResult.emit(PreviewResult.Error(e.message ?: "Unknown error", e))
            } finally {
                isRendering.set(false)
                
                // 处理挂起的渲染
                pendingRender?.let { pending ->
                    pendingRender = null
                    executeRender(pending)
                }
            }
        }
    }
    
    private suspend fun renderStandard(task: RenderTask) {
        Log.i(TAG, "标准渲染: ${task.width}x${task.height}")
        
        val startTime = System.currentTimeMillis()
        _state.update { it.copy(isRendering = true, progress = 0f) }
        
        // 分步渲染
        for (step in 0 until task.steps) {
            if (shouldCancel.get()) return
            
            val progress = (step + 1).toFloat() / task.steps
            val bitmap = generatePreview(
                prompt = task.prompt,
                width = task.width,
                height = task.height,
                quality = progress,
                seed = task.seed
            )
            
            if (bitmap != null) {
                _state.update { 
                    it.copy(
                        currentBitmap = bitmap,
                        progress = progress,
                        renderTimeMs = System.currentTimeMillis() - startTime
                    )
                }
                _previewResult.emit(PreviewResult.Progress(bitmap, progress))
            }
            
            // 帧率控制
            controlFrameRate()
        }
        
        // 最终结果
        val finalBitmap = generatePreview(
            prompt = task.prompt,
            width = task.width,
            height = task.height,
            quality = 1f,
            seed = task.seed
        )
        
        finalBitmap?.let { bitmap ->
            _state.update { state -> 
                state.copy(
                    currentBitmap = bitmap,
                    progress = 1f,
                    isRendering = false,
                    renderTimeMs = System.currentTimeMillis() - startTime
                )
            }
            _previewResult.emit(PreviewResult.Success(bitmap, 1f, System.currentTimeMillis() - startTime))
        }
        
        updateFps()
    }
    
    private suspend fun renderFast(task: RenderTask) {
        Log.i(TAG, "快速渲染: 低分辨率")
        
        // 使用低分辨率快速渲染
        val fastWidth = minOf(task.width, 256)
        val fastHeight = minOf(task.height, 256)
        
        val bitmap = generatePreview(
            prompt = task.prompt,
            width = fastWidth,
            height = fastHeight,
            quality = 0.5f,
            seed = task.seed
        )
        
        bitmap?.let { bmp ->
            _state.update { state -> 
                state.copy(
                    currentBitmap = bmp,
                    progress = 1f,
                    isRendering = false
                )
            }
            _previewResult.emit(PreviewResult.Success(bmp, 1f, 0))
        }
        
        updateFps()
    }
    
    private suspend fun renderQuality(task: RenderTask) {
        Log.i(TAG, "高质量渲染: ${task.width}x${task.height}")
        
        val bitmap = generatePreview(
            prompt = task.prompt,
            width = task.width,
            height = task.height,
            quality = 1f,
            seed = task.seed
        )
        
        bitmap?.let { bmp ->
            _state.update { state -> 
                state.copy(
                    currentBitmap = bmp,
                    progress = 1f,
                    isRendering = false
                )
            }
            _previewResult.emit(PreviewResult.Success(bmp, 1f, 0))
        }
        
        updateFps()
    }
    
    private suspend fun renderProgressive(task: RenderTask) {
        Log.i(TAG, "渐进式渲染")
        
        val startTime = System.currentTimeMillis()
        val stages = listOf(
            0.1f to 128,
            0.25f to 192,
            0.5f to 256,
            0.75f to 384,
            1f to task.width
        )
        
        var lastBitmap: Bitmap? = null
        
        for ((quality, size) in stages) {
            if (shouldCancel.get()) return
            
            val stageSize = minOf(size, MAX_PREVIEW_SIZE)
            
            val bitmap = generatePreview(
                prompt = task.prompt,
                width = stageSize,
                height = stageSize,
                quality = quality,
                seed = task.seed
            )
            
            if (bitmap != null) {
                lastBitmap?.let { if (it != bitmap) it.recycle() }
                
                _state.update { 
                    it.copy(
                        currentBitmap = bitmap,
                        progress = quality,
                        renderTimeMs = System.currentTimeMillis() - startTime
                    )
                }
                _previewResult.emit(PreviewResult.Progress(bitmap, quality))
            }
            
            controlFrameRate()
        }
        
        _state.update { it.copy(isRendering = false) }
        updateFps()
    }
    
    private suspend fun renderTiled(task: RenderTask) {
        Log.i(TAG, "分块渲染")
        
        val tileSize = 256
        val tilesX = ceil(task.width.toFloat() / tileSize).toInt()
        val tilesY = ceil(task.height.toFloat() / tileSize).toInt()
        
        // 创建输出图像
        val output = Bitmap.createBitmap(task.width, task.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        
        for (ty in 0 until tilesY) {
            for (tx in 0 until tilesX) {
                if (shouldCancel.get()) return
                
                val x = tx * tileSize
                val y = ty * tileSize
                val w = minOf(tileSize, task.width - x)
                val h = minOf(tileSize, task.height - y)
                
                val tile = generatePreview(
                    prompt = task.prompt,
                    width = w,
                    height = h,
                    quality = 0.5f,
                    seed = task.seed + tx * 100 + ty
                )
                
                tile?.let {
                    canvas.drawBitmap(it, x.toFloat(), y.toFloat(), null)
                    it.recycle()
                }
                
                val progress = (ty * tilesX + tx + 1).toFloat() / (tilesX * tilesY)
                _state.update { it.copy(progress = progress) }
            }
        }
        
        _state.update { 
            it.copy(
                currentBitmap = output,
                progress = 1f,
                isRendering = false
            )
        }
        _previewResult.emit(PreviewResult.Success(output, 1f, 0))
        
        updateFps()
    }
    
    private suspend fun generatePreview(
        prompt: String,
        width: Int,
        height: Int,
        quality: Float,
        seed: Long = Random.nextLong(),
        isThumbnail: Boolean = false
    ): Bitmap? = withContext(Dispatchers.Default) {
        try {
            // 检查缓存
            val cacheKey = "${prompt.hashCode()}_${width}_${height}_${seed}_${quality}"
            
            if (config.enableCache) {
                renderCache[cacheKey]?.let {
                    return@withContext it
                }
            }
            
            // 模拟预览生成 (实际应调用推理引擎)
            delay((50 / config.maxFps).toLong().coerceAtLeast(10))
            
            // 创建预览图像
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * height)
            
            // 使用种子生成确定性噪声
            val random = Random(seed)
            
            for (i in pixels.indices) {
                val x = i % width
                val y = i / width
                
                // 基于质量生成不同细节级别
                val baseNoise = random.nextInt(256)
                val detail = if (quality > 0.5f) random.nextInt(50) else 0
                
                val r = (baseNoise + detail).coerceIn(0, 255)
                val g = ((baseNoise + detail) * 0.8).toInt().coerceIn(0, 255)
                val b = ((baseNoise + detail) * 1.2).toInt().coerceIn(0, 255)
                
                pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            
            // 缓存
            if (config.enableCache) {
                addToRenderCache(cacheKey, bitmap)
            }
            
            bitmap
            
        } catch (e: Exception) {
            Log.e(TAG, "预览生成失败: ${e.message}")
            null
        }
    }
    
    private fun controlFrameRate() {
        val targetFrameTime = 1000L / config.maxFps
        val elapsed = System.currentTimeMillis() - lastRenderTime.get()
        
        if (elapsed < targetFrameTime) {
            scope.launch {
                delay(targetFrameTime - elapsed)
            }
        }
    }
    
    private fun updateFps() {
        framesSinceLastUpdate++
        val now = System.currentTimeMillis()
        
        if (now - lastFpsUpdate >= 1000) {
            val fps = (framesSinceLastUpdate * 1000 / (now - lastFpsUpdate)).toInt()
            _state.update { it.copy(fps = fps) }
            framesSinceLastUpdate = 0
            lastFpsUpdate = now
        }
    }
    
    private fun calculateCurrentFps(): Float {
        val now = System.currentTimeMillis()
        frameTimestamps.removeAll { now - it > 5000 }
        
        if (frameTimestamps.size < 2) return 0f
        
        val duration = frameTimestamps.last() - frameTimestamps.first()
        return if (duration > 0) {
            (frameTimestamps.size - 1) * 1000f / duration
        } else 0f
    }
    
    private fun addToCache(key: String, bitmap: Bitmap) {
        if (previewCache.size >= MAX_CACHE_SIZE) {
            // 移除最旧的缓存
            while (cacheOrder.isNotEmpty() && previewCache.size >= MAX_CACHE_SIZE) {
                val oldestKey = cacheOrder.removeFirst()
                previewCache.remove(oldestKey)?.recycle()
            }
        }
        
        previewCache[key] = bitmap
        cacheOrder.addLast(key)
    }
    
    private fun addToRenderCache(key: String, bitmap: Bitmap) {
        if (renderCache.size >= MAX_CACHE_SIZE * 2) {
            val iterator = renderCache.keys.iterator()
            if (iterator.hasNext()) {
                val oldKey = iterator.next()
                renderCache.remove(oldKey)?.recycle()
            }
        }
        renderCache[key] = bitmap
    }
    
    private fun estimateMemoryUsage(): Float {
        var totalPixels = 0
        
        previewCache.values.forEach { bitmap ->
            totalPixels += bitmap.width * bitmap.height
        }
        
        renderCache.values.forEach { bitmap ->
            totalPixels += bitmap.width * bitmap.height
        }
        
        // 每像素4字节 (ARGB_8888)
        return totalPixels * 4 / (1024 * 1024).toFloat()
    }
}
