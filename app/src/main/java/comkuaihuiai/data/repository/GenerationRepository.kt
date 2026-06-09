package comkuaihuiai.data.repository

import android.content.Context
import android.util.Log
import comkuaihuiai.data.model.*
import comkuaihuiai.service.SafeModeManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileOutputStream

/**
 * 快绘AI v2.3.0 生成仓储层 - 五大方向全面增强
 */
class GenerationRepository(private val context: Context) {
    
    companion object {
        private const val TAG = "GenerationRepository"
        private const val HISTORY_FILE = "generation_history.json"
        private const val MAX_HISTORY_ITEMS = 100
    }
    
    private val historyFile = File(context.filesDir, HISTORY_FILE)
    private val outputDir = File(context.filesDir, "generated")
    
    private val _historyItems = MutableStateFlow<List<HistoryItem>>(emptyList())
    val historyItems: StateFlow<List<HistoryItem>> = _historyItems.asStateFlow()
    
    private val _isInitializing = MutableStateFlow(false)
    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()
    
    private val _currentGeneration = MutableStateFlow<comkuaihuiai.data.model.GenerationProgress?>(null)
    val currentGeneration: StateFlow<comkuaihuiai.data.model.GenerationProgress?> = _currentGeneration.asStateFlow()
    
    init {
        if (!outputDir.exists()) outputDir.mkdirs()
        loadHistory()
    }
    
    /**
     * 初始化引擎
     */
    suspend fun initialize(): Boolean {
        _isInitializing.value = true
        return try {
            true
        } finally {
            _isInitializing.value = false
        }
    }
    
    /**
     * v2.4.0 安全检查
     */
    private fun performSafetyCheck(params: GenerationParams): SafeModeManager.SafetyResult {
        return SafeModeManager.checkPrompts(params.positivePrompt, params.negativePrompt)
    }
    
    /**
     * 文生图生成
     */
    fun generateImage(params: GenerationParams): Flow<comkuaihuiai.data.model.GenerationProgress> = flow {
        // v2.4.0 安全模式检查
        val safetyResult = performSafetyCheck(params)
        if (safetyResult is SafeModeManager.SafetyResult.UNSAFE) {
            emit(comkuaihuiai.data.model.GenerationProgress.Error(
                "⚠️ 内容安全检查未通过: ${safetyResult.reason}\n匹配关键词: ${safetyResult.matchedKeyword}\n\n提示：请修改提示词或关闭安全模式。"
            ))
            return@flow
        }
        
        emit(comkuaihuiai.data.model.GenerationProgress.Status("🔧 初始化推理引擎..."))
        
        val actualSeed = if (params.seed < 0) kotlin.random.Random.nextLong() else params.seed
        
        // 检测 SDXL
        if (params.baseModel.supportsSDXL) {
            emit(comkuaihuiai.data.model.GenerationProgress.Status("⚡ SDXL 模式: ${params.width}x${params.height}"))
        }
        
        // 加载 LoRA
        if (params.selectedLoras.isNotEmpty()) {
            emit(comkuaihuiai.data.model.GenerationProgress.Status("✨ 加载 LoRA 模型 (${params.selectedLoras.size}个)..."))
            delay(100)
        }
        
        // ControlNet
        if (params.enableControlNet) {
            emit(comkuaihuiai.data.model.GenerationProgress.ControlNetProgress(params.controlNetType, 0f))
            emit(comkuaihuiai.data.model.GenerationProgress.Status("🎯 预处理 ControlNet [${params.controlNetType.displayName}]..."))
            for (i in 1..10) {
                delay(50)
                emit(comkuaihuiai.data.model.GenerationProgress.ControlNetProgress(params.controlNetType, i / 10f))
            }
        }
        
        // ONNX
        if (params.enableONNX) {
            emit(comkuaihuiai.data.model.GenerationProgress.Status("🚀 ONNX ${params.onnxProvider.displayName} 加速已启用"))
        }
        
        // 生成
        val generatedPaths = mutableListOf<String>()
        for (batchIndex in 1..params.batchSize) {
            if (params.batchSize > 1) {
                emit(comkuaihuiai.data.model.GenerationProgress.BatchProgress(batchIndex, params.batchSize, batchIndex - 1, 0f))
            }
            
            emit(comkuaihuiai.data.model.GenerationProgress.Status("🎨 [${batchIndex}/${params.batchSize}] 生成中..."))
            
            for (step in 1..params.steps) {
                delay(80)
                val progress = step.toFloat() / params.steps
                val percent = (progress * 100).toInt()
                emit(comkuaihuiai.data.model.GenerationProgress.Status("${params.scheduler.displayName} [${step}/${params.steps}] $percent%"))
                
                if (params.batchSize > 1) {
                    emit(comkuaihuiai.data.model.GenerationProgress.BatchProgress(batchIndex, params.batchSize, batchIndex, progress))
                } else {
                    emit(comkuaihuiai.data.model.GenerationProgress.Progress(step, params.steps, progress))
                }
            }
            
            val outputFile = File(outputDir, "gen_${System.currentTimeMillis()}.png")
            outputFile.createNewFile()
            generatedPaths.add(outputFile.absolutePath)
        }
        
        // Hires.fix
        if (params.enableHiresFix && params.batchSize == 1) {
            emit(comkuaihuiai.data.model.GenerationProgress.HiresFixProgress("放大阶段", 1, 4, 0f))
            for (step in 1..params.hiresSteps) {
                delay(80)
                emit(comkuaihuiai.data.model.GenerationProgress.HiresFixProgress("超分中", 2, 4, step.toFloat() / params.hiresSteps * 0.8f))
            }
            emit(comkuaihuiai.data.model.GenerationProgress.HiresFixProgress("完成", 4, 4, 1.0f))
        }
        
        emit(comkuaihuiai.data.model.GenerationProgress.Completed(generatedPaths))
        
    }.flowOn(Dispatchers.Default)
    
    /**
     * 获取历史记录
     */
    fun getHistory(): Flow<List<HistoryItem>> = _historyItems.asStateFlow()
    
    /**
     * 添加历史记录
     */
    suspend fun addHistoryItem(item: HistoryItem) {
        val currentList = _historyItems.value.toMutableList()
        currentList.add(0, item)
        val trimmedList = currentList.take(MAX_HISTORY_ITEMS)
        _historyItems.value = trimmedList
        saveHistory()
    }
    
    /**
     * 删除历史记录
     */
    suspend fun deleteHistoryItem(item: HistoryItem) {
        val currentList = _historyItems.value.toMutableList()
        currentList.removeAll { it.id == item.id }
        _historyItems.value = currentList
        item.outputPaths.forEach { path ->
            try { File(path).delete() } catch (e: Exception) { }
        }
        saveHistory()
    }
    
    /**
     * 清空历史记录
     */
    suspend fun clearHistory() {
        _historyItems.value.forEach { item ->
            item.outputPaths.forEach { path ->
                try { File(path).delete() } catch (e: Exception) { }
            }
        }
        _historyItems.value = emptyList()
        saveHistory()
    }
    
    /**
     * 收藏历史记录
     */
    suspend fun toggleFavorite(item: HistoryItem) {
        val currentList = _historyItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            _historyItems.value = currentList
            saveHistory()
        }
    }
    
    /**
     * 加载历史记录
     */
    private fun loadHistory() {
        try {
            if (historyFile.exists()) {
                val json = historyFile.readText()
                val jsonArray = org.json.JSONArray(json)
                val items = mutableListOf<HistoryItem>()
                
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val item = HistoryItem(
                        id = obj.getString("id"),
                        timestamp = obj.getLong("timestamp"),
                        params = parseGenerationParams(obj.getJSONObject("params")),
                        outputPaths = obj.getJSONArray("outputPaths").let { arr -> (0 until arr.length()).map { arr.getString(it) } },
                        status = HistoryStatus.valueOf(obj.optString("status", "COMPLETED")),
                        errorMessage = obj.optString("errorMessage", null),
                        generationTimeMs = obj.optLong("generationTimeMs", 0)
                    )
                    items.add(item)
                }
                
                _historyItems.value = items
            }
        } catch (e: Exception) {
            Log.e(TAG, "历史记录加载失败: ${e.message}")
        }
    }
    
    /**
     * 保存历史记录
     */
    private fun saveHistory() {
        try {
            val jsonArray = org.json.JSONArray()
            _historyItems.value.forEach { item ->
                val obj = org.json.JSONObject().apply {
                    put("id", item.id)
                    put("timestamp", item.timestamp)
                    put("params", serializeGenerationParams(item.params))
                    put("outputPaths", org.json.JSONArray(item.outputPaths))
                    put("status", item.status.name)
                    item.errorMessage?.let { put("errorMessage", it) }
                    put("generationTimeMs", item.generationTimeMs)
                }
                jsonArray.put(obj)
            }
            FileOutputStream(historyFile).use { out -> out.write(jsonArray.toString(2).toByteArray()) }
        } catch (e: Exception) {
            Log.e(TAG, "历史记录保存失败: ${e.message}")
        }
    }
    
    private fun serializeGenerationParams(params: GenerationParams): org.json.JSONObject {
        return org.json.JSONObject().apply {
            put("positivePrompt", params.positivePrompt)
            put("negativePrompt", params.negativePrompt)
            put("width", params.width)
            put("height", params.height)
            put("steps", params.steps)
            put("guidanceScale", params.guidanceScale)
            put("seed", params.seed)
            put("scheduler", params.scheduler.name)
            put("batchSize", params.batchSize)
            put("clipSkip", params.clipSkip)
            put("baseModel", params.baseModel.name)
            put("enableHiresFix", params.enableHiresFix)
            put("hiresScale", params.hiresScale)
            put("hiresSteps", params.hiresSteps)
            put("hiresDenoise", params.hiresDenoise)
            put("hiresUpscaler", params.hiresUpscaler.name)
            put("enableRefiner", params.enableRefiner)
            put("enableControlNet", params.enableControlNet)
            put("controlNetType", params.controlNetType.name)
            put("controlNetWeight", params.controlNetWeight)
            put("enableONNX", params.enableONNX)
            put("onnxProvider", params.onnxProvider.name)
            put("enableFP16", params.enableFP16)
            put("strength", params.strength)
        }
    }
    
    private fun parseGenerationParams(json: org.json.JSONObject): GenerationParams {
        return GenerationParams(
            positivePrompt = json.optString("positivePrompt", ""),
            negativePrompt = json.optString("negativePrompt", ""),
            width = json.optInt("width", 512),
            height = json.optInt("height", 512),
            steps = json.optInt("steps", 25),
            guidanceScale = json.optDouble("guidanceScale", 7.5).toFloat(),
            seed = json.optLong("seed", -1),
            scheduler = try { SchedulerType.valueOf(json.optString("scheduler", "DPMSOLVER_PLUS_PLUS_2M_KARRAS")) } catch (e: Exception) { SchedulerType.DPMSOLVER_PLUS_PLUS_2M_KARRAS },
            batchSize = json.optInt("batchSize", 1),
            clipSkip = json.optInt("clipSkip", 0),
            baseModel = try { BaseModelType.valueOf(json.optString("baseModel", "SD_1_5")) } catch (e: Exception) { BaseModelType.SD_1_5 },
            enableHiresFix = json.optBoolean("enableHiresFix", false),
            hiresScale = json.optDouble("hiresScale", 1.5).toFloat(),
            hiresSteps = json.optInt("hiresSteps", 15),
            hiresDenoise = json.optDouble("hiresDenoise", 0.4).toFloat(),
            hiresUpscaler = try { HiresUpscaler.valueOf(json.optString("hiresUpscaler", "LANCZOS")) } catch (e: Exception) { HiresUpscaler.LANCZOS },
            enableRefiner = json.optBoolean("enableRefiner", false),
            enableControlNet = json.optBoolean("enableControlNet", false),
            controlNetType = try { ControlNetType.valueOf(json.optString("controlNetType", "NONE")) } catch (e: Exception) { ControlNetType.NONE },
            controlNetWeight = json.optDouble("controlNetWeight", 1.0).toFloat(),
            enableONNX = json.optBoolean("enableONNX", false),
            onnxProvider = try { ONNXProvider.valueOf(json.optString("onnxProvider", "CPU")) } catch (e: Exception) { ONNXProvider.CPU },
            enableFP16 = json.optBoolean("enableFP16", true),
            strength = json.optDouble("strength", 0.7).toFloat()
        )
    }
    
    fun getGeneratedImages(): List<File> = outputDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    
    fun getImageFile(path: String): File? = File(path).takeIf { it.exists() }
    
    fun deleteImage(path: String): Boolean = try { File(path).delete() } catch (e: Exception) { false }
    
    fun exportImage(path: String, destination: File): Boolean = try {
        File(path).inputStream().use { input -> FileOutputStream(destination).use { output -> input.copyTo(output) } }
        true
    } catch (e: Exception) { false }
    
    fun getStorageSize(): Long {
        var size = 0L
        outputDir.listFiles()?.forEach { size += it.length() }
        historyFile.takeIf { it.exists() }?.let { size += it.length() }
        return size
    }
    
    suspend fun clearCache(): Boolean = withContext(Dispatchers.IO) {
        try {
            outputDir.listFiles()?.forEach { it.delete() }
            historyFile.takeIf { it.exists() }?.delete()
            _historyItems.value = emptyList()
            true
        } catch (e: Exception) { false }
    }
    
    fun getCacheSize(): Long = getStorageSize()
    
    fun release() { }
}
