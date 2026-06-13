package comkuaihuiai.service.native

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * KehuiAI 完整推理引擎
 * 整合 KehuiAI 2.6.0 的所有功能
 * 
 * 支持：
 * - 文生图 (txt2img)
 * - 图生图 (img2img)
 * - 局部重绘 (inpainting)
 * - 超分辨率 (upscale)
 * - LoRA 加载
 * - ControlNet
 * - 提示词缓存
 * - 多种调度器
 */
class KehuiAIInferenceEngine(private val context: Context) {

    companion object {
        private const val TAG = "KehuiAIEngine"
        
        // 默认模型目录
        const val DEFAULT_MODEL_DIR = "models/mnn"
        
        @Volatile
        private var instance: KehuiAIInferenceEngine? = null
        
        fun getInstance(context: Context): KehuiAIInferenceEngine {
            return instance ?: synchronized(this) {
                instance ?: KehuiAIInferenceEngine(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // 底层 native 引擎
    private val nativeEngine = NativeKehuiAIEngine.getInstance(context)
    
    // 模型管理器
    private val modelManager = KehuiAIModelManager.getInstance(context)
    
    // 状态
    private var isInitialized = false
    private var currentEngineType = NativeKehuiAIEngine.ENGINE_MNN
    
    // LoRA 加载状态
    private val loadedLoras = mutableMapOf<String, Float>()
    
    // ControlNet 加载状态
    private val loadedControlNets = mutableMapOf<Int, String>()
    
    // 当前 VAE
    private var currentVAE: String? = null
    
    // 当前调度器
    private var currentSampler = 0
    
    // ============== 初始化 ==============
    
    /**
     * 初始化引擎
     */
    suspend fun initialize(engineType: Int = NativeKehuiAIEngine.ENGINE_MNN): Boolean {
        if (isInitialized) return true
        
        try {
            Log.i(TAG, "Initializing KehuiAI engine...")
            
            // 初始化模型
            val modelInitResult = modelManager.initialize()
            if (!modelInitResult) {
                Log.e(TAG, "Failed to initialize models")
                return false
            }
            
            // 获取模型路径
            val modelPaths = modelManager.getModelPaths()
            val modelDir = File(modelPaths.clipPath).parentFile?.absolutePath ?: ""
            
            // 初始化 native 引擎
            val nativeResult = nativeEngine.initialize(modelDir, engineType)
            if (!nativeResult) {
                Log.e(TAG, "Failed to initialize native engine")
                return false
            }
            
            currentEngineType = engineType
            isInitialized = true
            
            Log.i(TAG, "KehuiAI engine initialized successfully")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Init error: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 销毁引擎
     */
    fun destroy() {
        try {
            nativeEngine.destroy()
            isInitialized = false
            loadedLoras.clear()
            loadedControlNets.clear()
            Log.i(TAG, "Engine destroyed")
        } catch (e: Exception) {
            Log.e(TAG, "Destroy error: ${e.message}")
        }
    }
    
    // ============== 生成功能 ==============
    
    /**
     * 文生图
     */
    fun txt2img(
        prompt: String,
        negativePrompt: String = "",
        width: Int = 512,
        height: Int = 512,
        steps: Int = 20,
        cfgScale: Float = 7.5f,
        seed: Int = -1,
        sampler: Int = currentSampler,
        enablePromptCache: Boolean = true
    ): Flow<KehuiAIProgress> = flow {
        emit(KehuiAIProgress.Status("Starting txt2img..."))
        
        if (!isInitialized) {
            val initResult = initialize(currentEngineType)
            if (!initResult) {
                emit(KehuiAIProgress.Error("Failed to initialize"))
                return@flow
            }
        }
        
        emit(KehuiAIProgress.Progress(0, "Generating..."))
        
        try {
            nativeEngine.generate(
                prompt = prompt,
                negativePrompt = negativePrompt,
                width = width,
                height = height,
                steps = steps,
                cfgScale = cfgScale,
                seed = seed,
                samplerType = sampler,
                strength = 0.8f,
                enablePromptCache = enablePromptCache
            ).collect { progress ->
                when (progress) {
                    is LDGenerationProgress.Status -> {
                        emit(KehuiAIProgress.Status(progress.message))
                    }
                    is LDGenerationProgress.Progress -> {
                        emit(KehuiAIProgress.Progress(progress.percent, progress.message))
                    }
                    is LDGenerationProgress.Completed -> {
                        emit(KehuiAIProgress.Completed(progress.image))
                    }
                    is LDGenerationProgress.Error -> {
                        emit(KehuiAIProgress.Error(progress.message))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "txt2img error: ${e.message}", e)
            emit(KehuiAIProgress.Error(e.message ?: "Unknown error"))
        }
    }
    
    /**
     * 图生图
     */
    fun img2img(
        inputImage: Bitmap,
        prompt: String,
        negativePrompt: String = "",
        width: Int = 512,
        height: Int = 512,
        steps: Int = 20,
        cfgScale: Float = 7.5f,
        seed: Int = -1,
        sampler: Int = currentSampler,
        strength: Float = 0.8f
    ): Flow<KehuiAIProgress> = flow {
        emit(KehuiAIProgress.Status("Starting img2img..."))
        
        if (!isInitialized) {
            emit(KehuiAIProgress.Error("Engine not initialized"))
            return@flow
        }
        
        emit(KehuiAIProgress.Progress(0, "Processing..."))
        
        try {
            nativeEngine.generateImg2Img(
                inputImage = inputImage,
                prompt = prompt,
                negativePrompt = negativePrompt,
                width = width,
                height = height,
                steps = steps,
                cfgScale = cfgScale,
                seed = seed,
                samplerType = sampler,
                strength = strength
            ).collect { progress ->
                when (progress) {
                    is LDGenerationProgress.Status -> {
                        emit(KehuiAIProgress.Status(progress.message))
                    }
                    is LDGenerationProgress.Progress -> {
                        emit(KehuiAIProgress.Progress(progress.percent, progress.message))
                    }
                    is LDGenerationProgress.Completed -> {
                        emit(KehuiAIProgress.Completed(progress.image))
                    }
                    is LDGenerationProgress.Error -> {
                        emit(KehuiAIProgress.Error(progress.message))
                    }
                }
            }
        } catch (e: Exception) {
            emit(KehuiAIProgress.Error(e.message ?: "Error"))
        }
    }
    
    /**
     * 局部重绘
     */
    fun inpaint(
        inputImage: Bitmap,
        maskImage: Bitmap,
        prompt: String,
        negativePrompt: String = "",
        width: Int = 512,
        height: Int = 512,
        steps: Int = 20,
        cfgScale: Float = 7.5f,
        seed: Int = -1,
        sampler: Int = currentSampler,
        strength: Float = 0.8f
    ): Flow<KehuiAIProgress> = flow {
        emit(KehuiAIProgress.Status("Starting inpainting..."))
        
        if (!isInitialized) {
            emit(KehuiAIProgress.Error("Engine not initialized"))
            return@flow
        }
        
        try {
            nativeEngine.generateInpaint(
                inputImage = inputImage,
                maskImage = maskImage,
                prompt = prompt,
                negativePrompt = negativePrompt,
                width = width,
                height = height,
                steps = steps,
                cfgScale = cfgScale,
                seed = seed,
                samplerType = sampler,
                strength = strength
            ).collect { progress ->
                when (progress) {
                    is LDGenerationProgress.Status -> emit(KehuiAIProgress.Status(progress.message))
                    is LDGenerationProgress.Progress -> emit(KehuiAIProgress.Progress(progress.percent, progress.message))
                    is LDGenerationProgress.Completed -> emit(KehuiAIProgress.Completed(progress.image))
                    is LDGenerationProgress.Error -> emit(KehuiAIProgress.Error(progress.message))
                }
            }
        } catch (e: Exception) {
            emit(KehuiAIProgress.Error(e.message ?: "Error"))
        }
    }
    
    /**
     * 超分辨率
     */
    fun upscale(
        inputImage: Bitmap,
        scale: Int = 2,
        method: Int = 0
    ): Flow<KehuiAIProgress> = flow {
        emit(KehuiAIProgress.Status("Starting upscale..."))
        
        if (!isInitialized) {
            emit(KehuiAIProgress.Error("Engine not initialized"))
            return@flow
        }
        
        try {
            nativeEngine.upscale(inputImage, scale, method).collect { progress ->
                when (progress) {
                    is LDGenerationProgress.Status -> emit(KehuiAIProgress.Status(progress.message))
                    is LDGenerationProgress.Progress -> emit(KehuiAIProgress.Progress(progress.percent, progress.message))
                    is LDGenerationProgress.Completed -> emit(KehuiAIProgress.Completed(progress.image))
                    is LDGenerationProgress.Error -> emit(KehuiAIProgress.Error(progress.message))
                }
            }
        } catch (e: Exception) {
            emit(KehuiAIProgress.Error(e.message ?: "Error"))
        }
    }
    
    // ============== LoRA 管理 ==============
    
    /**
     * 加载 LoRA
     */
    fun loadLoRA(path: String, weight: Float = 1.0f): Boolean {
        val result = nativeEngine.loadLoRA(path, weight)
        if (result) {
            val name = File(path).nameWithoutExtension
            loadedLoras[name] = weight
            Log.i(TAG, "LoRA loaded: $name (weight: $weight)")
        }
        return result
    }
    
    /**
     * 卸载 LoRA
     */
    fun unloadLoRA(name: String) {
        nativeEngine.unloadLoRA(name)
        loadedLoras.remove(name)
        Log.i(TAG, "LoRA unloaded: $name")
    }
    
    /**
     * 卸载所有 LoRA
     */
    fun unloadAllLoRA() {
        nativeEngine.unloadAllLoRA()
        loadedLoras.clear()
        Log.i(TAG, "All LoRAs unloaded")
    }
    
    /**
     * 获取已加载的 LoRA 列表
     */
    fun getLoadedLoras(): Map<String, Float> = loadedLoras.toMap()
    
    // ============== ControlNet ==============
    
    /**
     * 加载 ControlNet
     */
    fun loadControlNet(path: String, controlType: Int): Boolean {
        val result = nativeEngine.loadControlNet(path, controlType)
        if (result) {
            val name = File(path).nameWithoutExtension
            loadedControlNets[controlType] = name
            Log.i(TAG, "ControlNet loaded: $name (type: $controlType)")
        }
        return result
    }
    
    /**
     * 卸载 ControlNet
     */
    fun unloadControlNet(controlType: Int) {
        nativeEngine.unloadControlNet(controlType)
        loadedControlNets.remove(controlType)
    }
    
    /**
     * 获取已加载的 ControlNet
     */
    fun getLoadedControlNets(): Map<Int, String> = loadedControlNets.toMap()
    
    // ============== VAE ==============
    
    /**
     * 加载 VAE
     */
    fun loadVAE(path: String): Boolean {
        val result = nativeEngine.loadVAE(path)
        if (result) {
            currentVAE = File(path).nameWithoutExtension
        }
        return result
    }
    
    /**
     * 设置 VAE
     */
    fun setVAE(name: String) {
        nativeEngine.setVAE(name)
        currentVAE = name
    }
    
    /**
     * 获取当前 VAE
     */
    fun getCurrentVAE(): String? = currentVAE
    
    // ============== 调度器 ==============
    
    /**
     * 设置调度器
     */
    fun setScheduler(samplerIndex: Int) {
        nativeEngine.setScheduler(samplerIndex)
        currentSampler = samplerIndex
    }
    
    /**
     * 获取调度器列表
     */
    fun getSchedulerNames(): List<String> = nativeEngine.getSchedulerNames()
    
    /**
     * 获取当前调度器
     */
    fun getCurrentSampler(): Int = currentSampler
    
    // ============== Embeddings ==============
    
    /**
     * 加载 Embeddings
     */
    fun loadEmbeddings(path: String): Boolean {
        return nativeEngine.loadEmbeddings(path)
    }
    
    /**
     * 卸载 Embeddings
     */
    fun unloadEmbeddings() {
        nativeEngine.unloadEmbeddings()
    }
    
    // ============== 缓存 ==============
    
    /**
     * 清除提示词缓存
     */
    fun clearPromptCache() {
        nativeEngine.clearPromptCache()
    }
    
    /**
     * 清除模型缓存
     */
    fun clearModelCache() {
        nativeEngine.clearModelCache()
    }
    
    // ============== Safety Checker ==============
    
    /**
     * 设置 Safety Checker
     */
    fun setSafetyChecker(path: String) {
        nativeEngine.setSafetyChecker(path)
    }
    
    /**
     * 检查图像安全性
     */
    fun checkSafety(bitmap: Bitmap): Boolean {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return nativeEngine.checkSafety(stream.toByteArray())
    }
    
    // ============== 信息 ==============
    
    /**
     * 获取设备信息
     */
    fun getDeviceInfo(): String = nativeEngine.getDeviceInfo()
    
    /**
     * 获取模型信息
     */
    fun getModelInfo(): String = nativeEngine.getModelInfo()
    
    /**
     * 获取引擎状态
     */
    fun isInitialized(): Boolean = isInitialized
    
    /**
     * 获取引擎类型
     */
    fun getEngineType(): Int = currentEngineType
    
    /**
     * 获取完整状态报告
     */
    fun getStatusReport(): String {
        return buildString {
            appendLine("=== KehuiAI Engine Status ===")
            appendLine("Initialized: $isInitialized")
            appendLine("Engine Type: $currentEngineType")
            appendLine("Current Sampler: ${getSchedulerNames().getOrElse(currentSampler) { "unknown" }}")
            appendLine("Current VAE: ${currentVAE ?: "default"}")
            appendLine()
            appendLine("--- LoRAs (${loadedLoras.size}) ---")
            loadedLoras.forEach { (name, weight) ->
                appendLine("  $name: $weight")
            }
            appendLine()
            appendLine("--- ControlNets (${loadedControlNets.size}) ---")
            loadedControlNets.forEach { (type, name) ->
                appendLine("  Type $type: $name")
            }
            appendLine()
            appendLine(getDeviceInfo())
        }
    }
}

// 进度回调 sealed class
sealed class KehuiAIProgress {
    data class Status(val message: String) : KehuiAIProgress()
    data class Progress(val percent: Int, val message: String) : KehuiAIProgress()
    data class Completed(val image: Bitmap) : KehuiAIProgress()
    data class Error(val message: String) : KehuiAIProgress()
}
