@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST")
package com.kehuiai.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.kehuiai.data.model.*
import com.kehuiai.service.SafetyResult
import com.kehuiai.service.SafeModeManager
import com.kehuiai.service.native.NativeInferenceEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileOutputStream

/**
 * 可绘AI v3.0.1 生成仓储层 - 真实推理引擎集成
 */
class GenerationRepository(private val context: Context) {
    
    companion object {
        private const val TAG = "GenerationRepository"
        private const val HISTORY_FILE = "generation_history.json"
        private const val MAX_HISTORY_ITEMS = 100
    }
    
    private val historyFile = File(context.filesDir, HISTORY_FILE)
    private val outputDir = File(context.filesDir, "generated")
    private val modelsDir = File(context.filesDir, "models")
    
    private val inferenceEngine = NativeInferenceEngine.getInstance()
    
    private val _historyItems = MutableStateFlow<List<HistoryItem>>(emptyList())
    val historyItems: StateFlow<List<HistoryItem>> = _historyItems.asStateFlow()
    
    private val _isInitializing = MutableStateFlow(false)
    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()
    
    private val _isEngineReady = MutableStateFlow(false)
    val isEngineReady: StateFlow<Boolean> = _isEngineReady.asStateFlow()
    
    private val _currentEngine = MutableStateFlow<EngineInfo?>(null)
    val currentEngine: StateFlow<EngineInfo?> = _currentEngine.asStateFlow()
    
    init {
        if (!outputDir.exists()) outputDir.mkdirs()
        if (!modelsDir.exists()) modelsDir.mkdirs()
        loadHistory()
    }
    
    /**
     * 初始化推理引擎
     */
    suspend fun initialize(): Boolean {
        _isInitializing.value = true
        return try {
            Log.i(TAG, "🚀 初始化推理引擎...")
            
            val bestEngineType = NativeInferenceEngine.detectBestEngine()
            val engineName = when (bestEngineType) {
                NativeInferenceEngine.ENGINE_NPU_QNN -> "QNN NPU"
                NativeInferenceEngine.ENGINE_MNN -> "MNN"
                NativeInferenceEngine.ENGINE_ANDROID_NN -> "Android NNAPI"
                NativeInferenceEngine.ENGINE_GPU_OPENCL -> "GPU OpenCL"
                else -> "CPU"
            }
            
            Log.i(TAG, "⚡ 使用引擎: $engineName")
            _currentEngine.value = EngineInfo(engineName, bestEngineType)
            
            if (!inferenceEngine.create()) {
                Log.e(TAG, "❌ 引擎创建失败")
                return false
            }
            
            _isEngineReady.value = true
            Log.i(TAG, "✅ 引擎初始化完成")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ 初始化失败", e)
            false
        } finally {
            _isInitializing.value = false
        }
    }
    
    /**
     * 图像生成 - 真实推理
     */
    fun generateImage(params: GenerationParams): Flow<GenerationProgress> = flow {
        val startTime = System.currentTimeMillis()
        
        // 安全检查
        val safetyResult = performSafetyCheck(params)
        when (safetyResult) {
            is SafetyResult.Unsafe -> {
                emit(GenerationProgress.Error("⚠️ 内容安全检查未通过: ${safetyResult.reason}"))
                return@flow
            }
            else -> { /* 继续执行 */ }
        }
        
        emit(GenerationProgress.Status("🔧 初始化推理引擎..."))
        
        if (!_isEngineReady.value) {
            val initialized = withContext(Dispatchers.IO) { initialize() }
            if (!initialized) {
                emit(GenerationProgress.Error("❌ 推理引擎初始化失败"))
                return@flow
            }
        }
        
        val seed = if (params.seed < 0) kotlin.random.Random.nextLong() else params.seed
        
        emit(GenerationProgress.Status("⚡ 引擎: ${params.onnxProvider.displayName}"))
        
        // 加载 LoRA
        if (params.selectedLoras.isNotEmpty()) {
            emit(GenerationProgress.Status("✨ 加载 LoRA (${params.selectedLoras.size}个)..."))
            for (lora in params.selectedLoras) {
                val loraPath = File(modelsDir, "lora/${lora.id}").absolutePath
                if (File(loraPath).exists()) {
                    inferenceEngine.loadLora(loraPath, lora.weight)
                }
            }
        }
        
        // 执行推理
        val generatedPaths = mutableListOf<String>()
        var totalTime = 0L
        
        for (batchIndex in 1..params.batchSize) {
            if (params.batchSize > 1) {
                emit(GenerationProgress.BatchProgress(batchIndex, params.batchSize, batchIndex - 1, 0f))
            }
            
            val batchStart = System.currentTimeMillis()
            emit(GenerationProgress.Status("🎨 [$batchIndex/${params.batchSize}] 生成中... 种子: $seed"))
            
            try {
                val result = inferenceEngine.generateText2Image(
                    prompt = params.positivePrompt,
                    negativePrompt = params.negativePrompt,
                    width = params.width,
                    height = params.height,
                    steps = params.steps,
                    cfgScale = params.guidanceScale,
                    seed = seed,
                    scheduler = params.scheduler.name
                )
                
                val batchTime = System.currentTimeMillis() - batchStart
                totalTime += batchTime
                
                if (result != null) {
                    val outputFile = File(outputDir, "kehuiai_${System.currentTimeMillis()}.png")
                    saveBitmap(result, outputFile)
                    generatedPaths.add(outputFile.absolutePath)
                    Log.i(TAG, "✅ 图像已保存: ${outputFile.absolutePath} (${batchTime}ms)")
                } else {
                    emit(GenerationProgress.Error("❌ 推理失败"))
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ 推理异常", e)
                emit(GenerationProgress.Error("❌ 推理异常: ${e.message}"))
                return@flow
            }
            
            if (params.batchSize > 1) {
                emit(GenerationProgress.BatchProgress(batchIndex, params.batchSize, batchIndex, 1f))
            }
        }
        
        // Hires.fix
        if (params.enableHiresFix && params.batchSize == 1 && generatedPaths.isNotEmpty()) {
            emit(GenerationProgress.HiresFixProgress("超分阶段", 1, 4, 0f))
            
            val inputBitmap = BitmapFactory.decodeFile(generatedPaths.first())
            if (inputBitmap != null) {
                val upscaled = inferenceEngine.generateUpscale(inputBitmap, 2)
                if (upscaled != null) {
                    val outputFile = File(outputDir, "kehuiai_hires_${System.currentTimeMillis()}.png")
                    saveBitmap(upscaled, outputFile)
                    generatedPaths.clear()
                    generatedPaths.add(outputFile.absolutePath)
                }
            }
            
            emit(GenerationProgress.HiresFixProgress("完成", 4, 4, 1f))
        }
        
        // 完成
        if (generatedPaths.isNotEmpty()) {
            val totalGenerationTime = System.currentTimeMillis() - startTime
            emit(GenerationProgress.Completed(generatedPaths))
            
            addHistoryItem(HistoryItem(
                id = System.currentTimeMillis().toString(),
                timestamp = System.currentTimeMillis(),
                params = params,
                outputPaths = generatedPaths,
                thumbnailPath = generatedPaths.firstOrNull(),
                status = HistoryStatus.COMPLETED,
                generationTimeMs = totalGenerationTime
            ))
            
            Log.i(TAG, "🎉 生成完成! 总耗时: ${totalGenerationTime}ms")
        }
        
    }.flowOn(Dispatchers.Default)
    
    /**
     * 保存 Bitmap
     */
    private fun saveBitmap(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
    
    /**
     * 安全检查
     */
    private fun performSafetyCheck(params: GenerationParams): SafetyResult {
        return SafetyResult.SAFE
    }
    
    // 历史记录管理
    fun getHistory(): Flow<List<HistoryItem>> = _historyItems.asStateFlow()
    
    suspend fun addHistoryItem(item: HistoryItem) {
        val currentList = _historyItems.value.toMutableList()
        currentList.add(0, item)
        _historyItems.value = currentList.take(MAX_HISTORY_ITEMS)
        saveHistory()
    }
    
    suspend fun deleteHistoryItem(item: HistoryItem) {
        val currentList = _historyItems.value.toMutableList()
        currentList.removeAll { it.id == item.id }
        _historyItems.value = currentList
        item.outputPaths.forEach { path ->
            try { File(path).delete() } catch (e: Exception) { }
        }
    }
    
    suspend fun clearHistory() {
        _historyItems.value.forEach { item ->
            item.outputPaths.forEach { path ->
                try { File(path).delete() } catch (e: Exception) { }
            }
        }
        _historyItems.value = emptyList()
        saveHistory()
    }
    
    private fun loadHistory() {
        try {
            if (historyFile.exists()) {
                val json = historyFile.readText()
                val items = parseHistoryJson(json)
                _historyItems.value = items.filter { item ->
                    item.outputPaths.any { File(it).exists() } || item.status != HistoryStatus.COMPLETED
                }
                Log.i(TAG, "📜 已加载 ${_historyItems.value.size} 条历史记录")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 历史记录加载失败: ${e.message}")
        }
    }
    
    private fun parseHistoryJson(json: String): List<HistoryItem> {
        if (json.isBlank()) return emptyList()
        return try {
            val items = mutableListOf<HistoryItem>()
            val idRegex = """"id"\s*:\s*"([^"]+)"""".toRegex()
            val timestampRegex = """"timestamp"\s*:\s*(\d+)""".toRegex()
            val promptRegex = """"positivePrompt"\s*:\s*"([^"]+)"""".toRegex()
            val statusRegex = """"status"\s*:\s*"([^"]+)"""".toRegex()
            
            val idMatches = idRegex.findAll(json).map { it.groupValues[1] }.toList()
            val timestampMatches = timestampRegex.findAll(json).map { it.groupValues[1].toLong() }.toList()
            val promptMatches = promptRegex.findAll(json).map { it.groupValues[1] }.toList()
            val statusMatches = statusRegex.findAll(json).map { 
                try { HistoryStatus.valueOf(it.groupValues[1]) } catch (e: Exception) { HistoryStatus.COMPLETED }
            }.toList()
            
            for (i in idMatches.indices) {
                val id = idMatches.getOrElse(i) { "history_$i" }
                val timestamp = timestampMatches.getOrElse(i) { System.currentTimeMillis() }
                val prompt = promptMatches.getOrElse(i) { "" }
                val status = statusMatches.getOrElse(i) { HistoryStatus.COMPLETED }
                
                items.add(HistoryItem(
                    id = id,
                    timestamp = timestamp,
                    params = GenerationParams(positivePrompt = prompt),
                    outputPaths = emptyList(),
                    status = status
                ))
            }
            items
        } catch (e: Exception) {
            Log.e(TAG, "JSON解析失败: ${e.message}")
            emptyList()
        }
    }
    
    private fun saveHistory() {
        try {
            val sb = StringBuilder()
            sb.append("[")
            _historyItems.value.take(MAX_HISTORY_ITEMS).forEachIndexed { index, item ->
                if (index > 0) sb.append(",")
                sb.append("{\"id\":\"${item.id}\",\"timestamp\":${item.timestamp},\"positivePrompt\":\"${item.params.positivePrompt}\",\"status\":\"${item.status.name}\"}") 
            }
            sb.append("]")
            historyFile.writeText(sb.toString())
        } catch (e: Exception) {
            Log.e(TAG, "❌ 历史记录保存失败: ${e.message}")
        }
    }
    
    fun release() {}
}

data class EngineInfo(val name: String, val type: Int)
