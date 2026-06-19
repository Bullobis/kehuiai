@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST", "DEPRECATION", "USELESS_ELVIS")
package com.kehuiai.service

import android.content.Context
import android.util.Log
import com.kehuiai.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

/**
 * 方向三：Embedding 管理器 - 更多模型生态
 * Embedding 用于文字嵌入和概念注入
 */
class EmbeddingsManager(private val context: Context) {
    
    companion object {
        private const val TAG = "EmbeddingsManager"
        private const val EMBEDDINGS_DIR = "models/embeddings"
        
        // 内置触发词
        val BUILTIN_TRIGGERS = listOf(
            "masterpiece" to "提升整体质量",
            "best quality" to "最佳质量",
            "high quality" to "高质量",
            "absurdres" to "高分辨率",
            "incredibly absurdres" to "超高分辨率",
            "incredibly detailed" to "极其详细",
            "intricate details" to "复杂细节",
            "8k" to "8K分辨率",
            "4k" to "4K分辨率",
            "detailed face" to "详细面部",
            "beautiful detailed eyes" to "美丽细节的眼睛",
            "solo" to "单人",
            "1girl" to "一个女孩",
            "1boy" to "一个男孩",
            "anime" to "动漫风格",
            "realistic" to "写实风格",
            "photorealistic" to "照片级写实",
            "cinematic lighting" to "电影光照",
            "studio lighting" to "工作室光照",
            "dramatic lighting" to "戏剧光照"
        )
    }
    
    private val embeddingsDir = File(context.filesDir, EMBEDDINGS_DIR)
    
    private val _availableEmbeddings = MutableStateFlow<List<EmbeddingInfo>>(emptyList())
    val availableEmbeddings: StateFlow<List<EmbeddingInfo>> = _availableEmbeddings.asStateFlow()
    
    private val _selectedEmbeddings = MutableStateFlow<List<EmbeddingParam>>(emptyList())
    val selectedEmbeddings: StateFlow<List<EmbeddingParam>> = _selectedEmbeddings.asStateFlow()
    
    init {
        if (!embeddingsDir.exists()) embeddingsDir.mkdirs()
        loadBuiltinEmbeddings()
        scanCustomEmbeddings()
    }
    
    /**
     * 加载内置触发词
     */
    private fun loadBuiltinEmbeddings() {
        val builtin = BUILTIN_TRIGGERS.mapIndexed { index, (trigger, desc) ->
            EmbeddingInfo(
                id = "builtin_$index",
                name = trigger,
                path = "builtin",
                triggerWords = listOf(trigger),
                description = desc,
                isBuiltin = true,
                isTextual = true
            )
        }
        
        _availableEmbeddings.value = builtin
        Log.i(TAG, "加载 ${builtin.size} 个内置触发词")
    }
    
    /**
     * 扫描自定义 Embedding
     */
    private fun scanCustomEmbeddings() {
        val custom = mutableListOf<EmbeddingInfo>()
        
        embeddingsDir.listFiles()?.forEach { file ->
            when (file.extension.lowercase()) {
                "pt", "pth", "bin", "safetensors" -> {
                    custom.add(
                        EmbeddingInfo(
                            id = file.nameWithoutExtension,
                            name = file.nameWithoutExtension,
                            path = file.absolutePath,
                            triggerWords = detectTriggerWords(file),
                            description = "自定义嵌入",
                            isBuiltin = false,
                            isTextual = false,
                            size = file.length()
                        )
                    )
                }
            }
        }
        
        if (custom.isNotEmpty()) {
            _availableEmbeddings.value = _availableEmbeddings.value + custom
            Log.i(TAG, "发现 ${custom.size} 个自定义 Embedding")
        }
    }
    
    /**
     * 检测触发词
     */
    private fun detectTriggerWords(file: File): List<String> {
        // 简单检测：从文件名中提取
        val name = file.nameWithoutExtension
        val triggers = mutableListOf<String>()
        
        // 常见触发词检测
        BUILTIN_TRIGGERS.forEach { (trigger, _) ->
            if (name.lowercase().contains(trigger.lowercase())) {
                triggers.add(trigger)
            }
        }
        
        // 如果没有检测到，返回文件名作为提示
        if (triggers.isEmpty()) {
            triggers.add(name)
        }
        
        return triggers
    }
    
    /**
     * 搜索 Embedding
     */
    fun searchEmbeddings(query: String): List<EmbeddingInfo> {
        if (query.isBlank()) return _availableEmbeddings.value
        
        val lowerQuery = query.lowercase()
        return _availableEmbeddings.value.filter { embedding ->
            embedding.name.lowercase().contains(lowerQuery) ||
            embedding.description.lowercase().contains(lowerQuery) ||
            embedding.triggerWords.any { it.lowercase().contains(lowerQuery) }
        }
    }
    
    /**
     * 选择 Embedding
     */
    fun selectEmbedding(embedding: EmbeddingInfo) {
        val param = EmbeddingParam(
            id = embedding.id,
            name = embedding.name,
            path = embedding.path,
            tokens = 1,
            isEnabled = true,
            activationText = embedding.triggerWords.firstOrNull() ?: ""
        )
        
        val current = _selectedEmbeddings.value.toMutableList()
        if (current.none { it.id == embedding.id }) {
            current.add(param)
            _selectedEmbeddings.value = current
        }
    }
    
    /**
     * 取消选择 Embedding
     */
    fun deselectEmbedding(embeddingId: String) {
        _selectedEmbeddings.value = _selectedEmbeddings.value.filter { it.id != embeddingId }
    }
    
    /**
     * 切换启用状态
     */
    fun toggleEmbeddingEnabled(embeddingId: String) {
        val current = _selectedEmbeddings.value.toMutableList()
        val index = current.indexOfFirst { it.id == embeddingId }
        if (index >= 0) {
            current[index] = current[index].copy(isEnabled = !current[index].isEnabled)
            _selectedEmbeddings.value = current
        }
    }
    
    /**
     * 获取选中的 Embedding
     */
    fun getSelectedEmbeddings(): List<EmbeddingParam> {
        return _selectedEmbeddings.value.filter { it.isEnabled }
    }
    
    /**
     * 清除所有选择
     */
    fun clearSelection() {
        _selectedEmbeddings.value = emptyList()
    }
    
    /**
     * 生成提示词
     */
    fun buildPrompt(basePrompt: String): String {
        val selected = getSelectedEmbeddings()
        if (selected.isEmpty()) return basePrompt
        
        val triggers = selected.map { it.activationText }.filter { it.isNotBlank() }
        return if (triggers.isEmpty()) {
            basePrompt
        } else {
            "$basePrompt, ${triggers.joinToString(", ")}"
        }
    }
    
    /**
     * 删除 Embedding
     */
    suspend fun deleteEmbedding(embeddingId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val embedding = _availableEmbeddings.value.find { it.id == embeddingId }
            if (embedding != null && !embedding.isBuiltin) {
                File(embedding.path).delete()
                _availableEmbeddings.value = _availableEmbeddings.value.filter { it.id != embeddingId }
                deselectEmbedding(embeddingId)
                Log.i(TAG, "删除 Embedding: ${embedding.name}")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "删除 Embedding 失败: ${e.message}")
            false
        }
    }
    
    /**
     * 获取 Embedding 数量
     */
    fun getCount(): Int = _availableEmbeddings.value.size
    
    /**
     * 获取选中数量
     */
    fun getSelectedCount(): Int = _selectedEmbeddings.value.size
    
    /**
     * 刷新列表
     */
    fun refresh() {
        val builtin = _availableEmbeddings.value.filter { it.isBuiltin }
        scanCustomEmbeddings()
    }
    
    /**
     * 释放资源
     */
    fun release() {
        clearSelection()
    }
}

/**
 * Embedding 信息
 */
data class EmbeddingInfo(
    val id: String,
    val name: String,
    val path: String,
    val triggerWords: List<String>,
    val description: String,
    val isBuiltin: Boolean = false,
    val isTextual: Boolean = false,
    val size: Long = 0,
    val downloadProgress: Float = 0f
)
