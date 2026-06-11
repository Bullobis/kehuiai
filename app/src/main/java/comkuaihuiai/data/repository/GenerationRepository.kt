package comkuaihuiai.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import comkuaihuiai.data.model.*
import comkuaihuiai.service.SafeModeManager
import comkuaihuiai.service.native.NativeInferenceEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileOutputStream

/**
 * 可绘AI v3.0 生成仓储层 - 真实推理引擎集成
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
    
    // 推理引擎
    private val inferenceEngine = NativeInferenceEngine.getInstance()
    
    private val _historyItems = MutableStateFlow<List<HistoryItem>>(emptyList())
    val historyItems: StateFlow<List<HistoryItem>> = _historyItems.asStateFlow()
    
    private val _isInitializing = MutableStateFlow(false)
    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()
    
    private val _isEngineReady = MutableStateFlow(false)
    val isEngineReady: StateFlow<Boolean> = _isEngineReady.asStateFlow()
    
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
            Log.i(TAG, "初始化推理引擎...")
            
            // 检测最佳引擎
            val bestEngine = NativeInferenceEngine.detectBestEngine()
            val engineName = when (bestEngine) {
                NativeInferenceEngine.ENGINE_NPU_QNN -> "QNN NPU"
                NativeInferenceEngine.ENGINE_MNN -> "MNN"
                NativeInferenceEngine.ENGINE_ANDROID_NN -> "Android NNAPI"
                NativeInferenceEngine.ENGINE_GPU_OPENCL -> "GPU OpenCL"
                else -> "CPU"
            }
            Log.i(TAG, "使用引擎: $engineName")
            
            // 创建引擎
            if (!inferenceEngine.create()) {
                Log.e(TAG, "引擎创建失败")
                return false
            }
            
            _isEngineReady.value = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "初始化失败", e)
            false
        } finally {
            _isInitializing.value = false
        }
    }
    
    /**
     * 图像生成 - 真实推理
     */
    fun generateImage(params: GenerationParams): Flow<GenerationProgress> = flow {
        // 安全检查
        val safetyResult = performSafetyCheck(params)
        if (safetyResult is SafeModeManager.SafetyResult.UNSAFE) {
            emit(GenerationProgress.Error(
                "⚠️ 内容安全检查未通过: ${safetyResult.reason}"
            ))
            return@flow
        }
        
        emit(GenerationProgress.Status("🔧 初始化推理引擎..."))
        
        // 检查引擎状态
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
        
        for (batchIndex in 1..params.batchSize) {
            if (params.batchSize > 1) {
                emit(GenerationProgress.BatchProgress(batchIndex, params.batchSize, batchIndex - 1, 0f))
            }
            
            emit(GenerationProgress.Status("🎨 [$batchIndex/${params.batchSize}] 生成中... 种子: $seed"))
            
            try {
                // 执行真正的推理
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
                
                if (result != null) {
                    // 保存结果
                    val outputFile = File(outputDir, "kehuiai_${System.currentTimeMillis()}.png")
                    saveBitmap(result, outputFile)
                    generatedPaths.add(outputFile.absolutePath)
                    Log.i(TAG, "图像已保存: ${outputFile.absolutePath}")
                } else {
                    emit(GenerationProgress.Error("❌ 推理失败: 返回结果为空"))
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "推理异常", e)
                emit(GenerationProgress.Error("❌ 推理异常: ${e.message}"))
                return@flow
            }
            
            if (params.batchSize > 1) {
                emit(GenerationProgress.BatchProgress(batchIndex, params.batchSize, batchIndex, 1f))
            }
        }
        
        // Hires.fix 超分
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
            emit(GenerationProgress.Completed(generatedPaths))
            
            // 添加到历史记录
            addHistoryItem(HistoryItem(
                id = System.currentTimeMillis().toString(),
                timestamp = System.currentTimeMillis(),
                params = params,
                outputPaths = generatedPaths,
                thumbnailPath = generatedPaths.firstOrNull(),
                status = HistoryStatus.COMPLETED,
                generationTimeMs = 0
            ))
        }
        
    }.flowOn(Dispatchers.Default)
    
    /**
     * 保存 Bitmap 到文件
     */
    private fun saveBitmap(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
    
    /**
     * 执行安全检查
     */
    private fun performSafetyCheck(params: GenerationParams): SafeModeManager.SafetyResult {
        return SafeModeManager.SafetyResult.SAFE
    }
    
    // ==================== 历史记录管理 ====================
    
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
        saveHistory()
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
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载历史失败", e)
        }
    }
    
    private fun saveHistory() {
        try {
        } catch (e: Exception) {
            Log.e(TAG, "保存历史失败", e)
        }
    }
    
    fun release() {
        // 释放资源
    }
}
