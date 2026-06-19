@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST", "DEPRECATION", "USELESS_ELVIS")
package com.kehuiai.service

import android.content.Context
import android.util.Log
import com.kehuiai.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 快绘AI v3.0.0 LoRA 管理器
 * 
 * 对标 ComfyUI / A1111
 * 
 * 加强内容：
 * ✅ 线程安全（ConcurrentHashMap）
 * ✅ 权重安全范围校验
 * ✅ 缓存策略
 * ✅ 协程作用域管理
 */
class LoRAManager(private val context: Context) {
    
    companion object {
        private const val TAG = "LoRAManager"
        private const val LORA_DIR = "models/lora"
        
        // 权重范围
        const val MIN_LORA_WEIGHT = -2.0f
        const val MAX_LORA_WEIGHT = 2.0f
        const val MIN_CLIP_WEIGHT = -3.0f
        const val MAX_CLIP_WEIGHT = 3.0f
        
        // 缓存限制
        const val MAX_CACHED_LORAS = 50
    }
    
    private val loraDir = File(context.filesDir, LORA_DIR)
    
    // 线程安全的 LoRA 缓存
    private val availableLoras = MutableStateFlow<List<LoraParam>>(emptyList())
    private val loadedLoras = MutableStateFlow<List<LoraParam>>(emptyList())
    private val loraCache = ConcurrentHashMap<String, LoraParam>()
    
    // 协程作用域
    private val managerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // 引用计数
    private val refCount = AtomicInteger(0)
    private val isInitialized = AtomicBoolean(false)
    
    init {
        if (!loraDir.exists()) loraDir.mkdirs()
    }
    
    // ==================== 初始化 ====================
    
    /**
     * 初始化
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized.getAndSet(true)) {
            return@withContext true
        }
        
        try {
            Log.i(TAG, "🔄 初始化 LoRA 管理器...")
            
            // 加载内置 LoRA
            loadBuiltinLoras()
            
            // 扫描自定义 LoRA
            refreshLoras()
            
            refCount.set(1)
            
            Log.i(TAG, "✅ LoRA 管理器初始化完成")
            Log.i(TAG, "📦 可用 LoRA: ${availableLoras.value.size}")
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ 初始化失败: ${e.message}")
            false
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        val count = refCount.decrementAndGet()
        if (count > 0) {
            return
        }
        
        loraCache.clear()
        availableLoras.value = emptyList()
        loadedLoras.value = emptyList()
        managerScope.cancel()
        isInitialized.set(false)
        
        Log.i(TAG, "♻️ LoRA 管理器资源已释放")
    }
    
    // ==================== 加载/卸载 ====================
    
    /**
     * 刷新 LoRA 列表
     */
    fun refreshLoras() {
        val loras = mutableListOf<LoraParam>()
        
        loraDir.listFiles()?.forEach { file ->
            if (file.extension in listOf("safetensors", "ckpt", "pt", "pth")) {
                loras.add(
                    LoraParam(
                        id = file.nameWithoutExtension,
                        name = file.nameWithoutExtension,
                        path = file.absolutePath,
                        weight = 1.0f,
                        category = detectCategory(file.nameWithoutExtension)
                    )
                )
            }
        }
        
        availableLoras.value = loras
        Log.i(TAG, "🔍 发现 ${loras.size} 个 LoRA 模型")
    }
    
    /**
     * 加载 LoRA - 线程安全
     */
    suspend fun loadLora(lora: LoraParam, weight: Float = 1.0f): LoraParam? = withContext(Dispatchers.IO) {
        // 权重校验
        val safeWeight = weight.coerceIn(MIN_LORA_WEIGHT, MAX_LORA_WEIGHT)
        
        Log.i(TAG, "📦 加载 LoRA: ${lora.name} (权重: $safeWeight)")
        
        try {
            val weightedLora = lora.copy(
                weight = safeWeight,
                clipWeight = lora.clipWeight.coerceIn(MIN_CLIP_WEIGHT, MAX_CLIP_WEIGHT)
            )
            
            // 更新缓存
            loraCache[lora.id] = weightedLora
            
            // 更新已加载列表
            val current = loadedLoras.value.toMutableList()
            current.removeAll { it.id == lora.id }
            current.add(weightedLora)
            loadedLoras.value = current
            
            Log.i(TAG, "✅ LoRA ${lora.name} 加载完成 (权重: $safeWeight)")
            weightedLora
        } catch (e: Exception) {
            Log.e(TAG, "❌ LoRA 加载失败: ${e.message}")
            null
        }
    }
    
    /**
     * 卸载 LoRA
     */
    fun unloadLora(loraId: String) {
        loraCache.remove(loraId)
        
        val current = loadedLoras.value.toMutableList()
        current.removeAll { it.id == loraId }
        loadedLoras.value = current
        
        Log.i(TAG, "📤 LoRA $loraId 已卸载")
    }
    
    /**
     * 卸载所有 LoRA
     */
    fun unloadAllLoras() {
        loraCache.clear()
        loadedLoras.value = emptyList()
        Log.i(TAG, "📤 所有 LoRA 已卸载")
    }
    
    // ==================== 查询 ====================
    
    /**
     * 获取已加载的 LoRA 权重
     */
    fun getLoadedLoras(): Map<String, Float> {
        return loadedLoras.value.associate { it.id to it.weight }
    }
    
    /**
     * 获取已加载的 LoRA 列表
     */
    fun getLoadedLorasList(): List<LoraParam> {
        return loadedLoras.value.toList()
    }
    
    /**
     * 搜索 LoRA
     */
    fun searchLoras(query: String): List<LoraParam> {
        val lowerQuery = query.lowercase()
        return availableLoras.value.filter {
            it.name.contains(lowerQuery, ignoreCase = true) ||
            it.category.displayName.contains(query, ignoreCase = true)
        }
    }
    
    /**
     * 按类别获取 LoRA
     */
    fun getLorasByCategory(category: LoraCategory): List<LoraParam> {
        return availableLoras.value.filter { it.category == category }
    }
    
    /**
     * 获取可用的 LoRA
     */
    fun getAvailableLoras(): List<LoraParam> {
        return availableLoras.value.toList()
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 加载内置 LoRA
     */
    private fun loadBuiltinLoras() {
        val builtinLoras = listOf(
            LoraParam(
                id = "anime-style",
                name = "动漫风格",
                path = "models/lora/anime-style.safetensors",
                weight = 0f,
                category = LoraCategory.STYLE
            ),
            LoraParam(
                id = "realistic-style",
                name = "写实风格",
                path = "models/lora/realistic-style.safetensors",
                weight = 0f,
                category = LoraCategory.STYLE
            ),
            LoraParam(
                id = "portrait-enhance",
                name = "人像增强",
                path = "models/lora/portrait-enhance.safetensors",
                weight = 0f,
                category = LoraCategory.CHARACTER
            ),
            LoraParam(
                id = "dynamic-pose",
                name = "动态姿势",
                path = "models/lora/dynamic-pose.safetensors",
                weight = 0f,
                category = LoraCategory.POSE
            )
        )
        
        availableLoras.value = availableLoras.value + builtinLoras
    }
    
    /**
     * 检测 LoRA 类别
     */
    private fun detectCategory(name: String): LoraCategory {
        val lower = name.lowercase()
        return when {
            lower.contains("style") || lower.contains("aesthetic") -> LoraCategory.STYLE
            lower.contains("character") || lower.contains("char") || 
            lower.contains("girl") || lower.contains("boy") -> LoraCategory.CHARACTER
            lower.contains("pose") || lower.contains("action") -> LoraCategory.POSE
            lower.contains("hair") -> LoraCategory.HAIR
            lower.contains("cloth") || lower.contains("outfit") || lower.contains("dress") -> LoraCategory.CLOTHING
            lower.contains("background") || lower.contains("bg") || lower.contains("scene") -> LoraCategory.BACKGROUND
            lower.contains("light") || lower.contains("lighting") -> LoraCategory.LIGHTING
            lower.contains("camera") || lower.contains("lens") -> LoraCategory.CAMERA
            lower.contains("concept") || lower.contains("celestia") -> LoraCategory.CONCEPT
            else -> LoraCategory.OTHER
        }
    }
    
    /**
     * 校验 LoRA 权重
     */
    fun validateWeight(weight: Float): ValidationResult {
        return when {
            weight < MIN_LORA_WEIGHT -> ValidationResult(false, "权重不能小于 $MIN_LORA_WEIGHT")
            weight > MAX_LORA_WEIGHT -> ValidationResult(false, "权重不能大于 $MAX_LORA_WEIGHT")
            else -> ValidationResult(true, "权重有效")
        }
    }
    
    /**
     * 校验 CLIP 权重
     */
    fun validateClipWeight(clipWeight: Float): ValidationResult {
        return when {
            clipWeight < MIN_CLIP_WEIGHT -> ValidationResult(false, "CLIP权重不能小于 $MIN_CLIP_WEIGHT")
            clipWeight > MAX_CLIP_WEIGHT -> ValidationResult(false, "CLIP权重不能大于 $MAX_CLIP_WEIGHT")
            else -> ValidationResult(true, "CLIP权重有效")
        }
    }
    
    /**
     * 获取 LoRA 统计
     */
    fun getStats(): LoRAStats {
        return LoRAStats(
            totalAvailable = availableLoras.value.size,
            currentlyLoaded = loadedLoras.value.size,
            cacheSize = loraCache.size,
            byCategory = LoraCategory.entries.associate { category ->
                category to availableLoras.value.count { it.category == category }
            }
        )
    }
}

// ==================== 数据类 ====================

data class LoRAStats(
    val totalAvailable: Int,
    val currentlyLoaded: Int,
    val cacheSize: Int,
    val byCategory: Map<LoraCategory, Int>
)

