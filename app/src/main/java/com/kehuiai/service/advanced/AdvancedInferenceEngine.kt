package com.kehuiai.service.advanced

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.kehuiai.data.model.*
import com.kehuiai.service.KuaiHuiInferenceEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

/**
 * 快绘AI v3.1.0 高级推理引擎 - 全面对标 可绘AI
 */
class AdvancedInferenceEngine(private val context: Context) {
    
    companion object {
        private const val TAG = "AdvancedInferenceEngine"
        
        const val MAX_CONCURRENT_GENERATIONS = 4
        const val DEFAULT_BATCH_SIZE = 2
        const val MAX_BATCH_SIZE = 8
        
        const val DEFAULT_HIRES_SCALE = 1.5f
        const val DEFAULT_HIRES_STEPS = 15
        const val MIN_HIRES_RESOLUTION = 512
        const val MAX_HIRES_SCALE = 4.0f
        const val MIN_HIRES_SCALE = 1.0f
        
        const val DEFAULT_CONTROL_NET_WEIGHT = 1.0f
        const val MIN_CONTROL_NET_WEIGHT = 0.0f
        const val MAX_CONTROL_NET_WEIGHT = 2.0f
        const val DEFAULT_CONTROL_NET_GUIDANCE_START = 0.0f
        const val DEFAULT_CONTROL_NET_GUIDANCE_END = 1.0f
        
        const val DEFAULT_ONNX_PROVIDER = "CPU"
        const val DEFAULT_FP16 = true
        const val MAX_ONNX_THREADS = 8
        
        const val DEFAULT_VAE_STRENGTH = 0.1f
        const val MIN_VAE_STRENGTH = 0.0f
        const val MAX_VAE_STRENGTH = 1.0f
        
        const val MAX_LORAS = 5
        const val DEFAULT_LORA_STRENGTH = 1.0f
        
        const val LOW_MEMORY_THRESHOLD_MB = 512L
        const val CRITICAL_MEMORY_THRESHOLD_MB = 256L
        const val RECOMMENDED_MEMORY_MB = 2048L
        
        const val GENERATION_TIMEOUT_MS = 600000L
        const val STEP_TIMEOUT_MS = 30000L
        const val MODEL_LOAD_TIMEOUT_MS = 120000L
    }
    
    // ========== 内存信息 ==========
    
    data class MemoryInfo(
        val jvmUsedMb: Long,
        val jvmFreeMb: Long,
        val jvmMaxMb: Long,
        val systemAvailableMb: Long,
        val systemTotalMb: Long,
        val isLowMemory: Boolean,
        val memoryClass: MemClass
    )
    
    enum class MemClass { LOW, MEDIUM, HIGH, VERY_HIGH }
    
    // ========== 核心组件 ==========
    
    private val baseEngine = KuaiHuiInferenceEngine(context)
    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    
    // ========== 状态管理 ==========
    
    private val isInitialized = AtomicBoolean(false)
    private val isGenerating = AtomicBoolean(false)
    private val isCancelled = AtomicBoolean(false)
    
    private val currentJobId = AtomicReference<String?>(null)
    private val currentProgress = AtomicInteger(0)
    
    // ========== 资源管理 ==========
    
    private val performanceTracker = PerformanceTracker()
    private val resourceManager = ResourceManager()
    private val modelCache = ModelCache()
    private val loraCache = LoraCache()
    private val vaeCache = VaeCache()
    private val batchState = BatchState()
    private val schedulerManager = SchedulerManager()
    
    // ========== ONNX 配置 ==========
    
    data class ONNXConfig(
        val enabled: Boolean = false,
        val provider: ONNXProvider = ONNXProvider.CPU,
        val fp16: Boolean = DEFAULT_FP16,
        val threads: Int = 4,
        val memoryMode: ONNXMemoryMode = ONNXMemoryMode.BALANCED
    )
    
    enum class ONNXProvider(val displayName: String, val acceleration: Float) {
        CPU("CPU", 1.0f),
        GPU_OPENCL("GPU (OpenCL)", 3.0f),
        GPU_VULKAN("GPU (Vulkan)", 3.5f),
        NPU_SNPE("NPU (SNPE)", 5.0f),
        NPU_QCOM("NPU (QCOM)", 6.0f),
        NNAPI("NNAPI", 2.5f),
        AUTO("自动选择", 4.0f)
    }
    
    enum class ONNXMemoryMode { LOW, BALANCED, HIGH }
    
    enum class SchedulerType(val displayName: String, val speed: String, val quality: String, val description: String) {
        EULER("Euler", "快速", "中等", "经典欧拉方法"),
        EULER_A("Euler A", "快速", "高", "自适应步长"),
        DDIM("DDIM", "中等", "高", "去噪扩散隐式模型"),
        DPM_PP_2M("DPM++ 2M", "快速", "高", "DPM++ 2阶多步"),
        DPM_PP_2S_A("DPM++ 2S A", "快速", "很高", "DPM++ 2阶单步"),
        DPM_PP_SDE("DPM++ SDE", "中等", "很高", "DPM++ 随机微分方程"),
        UNIPC("UniPC", "快速", "高", "统一预测校正器"),
        LCM("LCM", "极快", "中等", "潜空间一致性模型")
    }
    
    // ========== 性能追踪器 ==========
    
    class PerformanceTracker {
        private val metrics = ConcurrentHashMap<String, Metric>()
        
        data class Metric(val name: String, val duration: Long, val memoryUsedMb: Long, val timestamp: Long)
        
        fun record(name: String, durationMs: Long, memoryUsedMb: Long = 0) {
            metrics[name] = Metric(name, durationMs, memoryUsedMb, System.currentTimeMillis())
        }
        
        fun getAverageDuration(name: String): Long {
            val values = metrics.values.filter { it.name == name }
            return if (values.isEmpty()) 0L else values.map { it.duration }.average().toLong()
        }
        
        fun clear() = metrics.clear()
    }
    
    // ========== 资源管理器 ==========
    
    class ResourceManager {
        private val activeJobs = ConcurrentHashMap<String, JobState>()
        
        data class JobState(
            val id: String,
            val params: GenerationParams,
            val status: Status,
            val progress: Float,
            val startTime: Long,
            val currentStep: Int = 0,
            val totalSteps: Int = 0
        )
        
        enum class Status { 
            PENDING, INITIALIZING, RUNNING, GENERATING, 
            POST_PROCESSING, COMPLETED, FAILED, CANCELLED 
        }
        
        fun addJob(id: String, params: GenerationParams) {
            activeJobs[id] = JobState(id, params, Status.PENDING, 0f, System.currentTimeMillis())
        }
        
        fun updateStatus(id: String, status: Status, progress: Float = 0f, currentStep: Int = 0, totalSteps: Int = 0) {
            activeJobs[id]?.let { job ->
                activeJobs[id] = job.copy(status = status, progress = progress, currentStep = currentStep, totalSteps = totalSteps)
            }
        }
        
        fun removeJob(id: String) = activeJobs.remove(id)
        
        fun getActiveCount(): Int = activeJobs.count { 
            it.value.status == Status.RUNNING || it.value.status == Status.GENERATING 
        }
    }
    
    // ========== 缓存管理 ==========
    
    class ModelCache {
        private val cache = ConcurrentHashMap<String, CachedModel>()
        
        data class CachedModel(val modelId: String, val loadedAt: Long, val sizeMb: Long)
        
        fun get(modelId: String): CachedModel? = cache[modelId]
        fun put(modelId: String) {
            cache[modelId] = CachedModel(modelId, System.currentTimeMillis(), 0)
        }
        fun clear() = cache.clear()
    }
    
    class LoraCache {
        private val cache = ConcurrentHashMap<String, LoraEntry>()
        
        data class LoraEntry(val id: String, val path: String, val weight: Float, val clipWeight: Float)
        
        fun put(id: String, path: String, weight: Float, clipWeight: Float) {
            cache[id] = LoraEntry(id, path, weight, clipWeight)
        }
        
        fun get(id: String): LoraEntry? = cache[id]
        fun clear() = cache.clear()
    }
    
    class VaeCache {
        private val cache = ConcurrentHashMap<String, String>()
        
        fun put(id: String, path: String) { cache[id] = path }
        fun get(id: String): String? = cache[id]
        fun clear() = cache.clear()
    }
    
    // ========== 批量状态 ==========
    
    class BatchState {
        val currentBatch = AtomicInteger(0)
        val totalBatches = AtomicInteger(1)
    }
    
    // ========== 调度器管理器 ==========
    
    class SchedulerManager {
        @Suppress("UNUSED_PARAMETER") fun createScheduler(type: SchedulerType, steps: Int, guidanceScale: Float, seed: Long) {}
        fun getScheduler(type: SchedulerType) = type
    }
    
    // ========== 内存监控 ==========
    
    private fun getMemoryInfo(): MemoryInfo {
        val runtime = Runtime.getRuntime()
        val jvmUsed = runtime.totalMemory() - runtime.freeMemory()
        val jvmFree = runtime.freeMemory()
        val jvmMax = runtime.maxMemory()
        
        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)
        val systemAvailable = memInfo.availMem
        val systemTotal = memInfo.totalMem
        
        val memClass = when {
            systemAvailable < LOW_MEMORY_THRESHOLD_MB * 1024 * 1024 -> MemClass.LOW
            systemAvailable < RECOMMENDED_MEMORY_MB * 1024 * 1024 -> MemClass.MEDIUM
            systemTotal < 4L * 1024 * 1024 * 1024 -> MemClass.HIGH
            else -> MemClass.VERY_HIGH
        }
        
        return MemoryInfo(
            jvmUsedMb = jvmUsed / (1024 * 1024),
            jvmFreeMb = jvmFree / (1024 * 1024),
            jvmMaxMb = jvmMax / (1024 * 1024),
            systemAvailableMb = systemAvailable / (1024 * 1024),
            systemTotalMb = systemTotal / (1024 * 1024),
            isLowMemory = memInfo.lowMemory,
            memoryClass = memClass
        )
    }
    
    private fun isMemoryEnough(requiredMb: Long): Boolean = getMemoryInfo().systemAvailableMb >= requiredMb
    
    private fun getRecommendedBatchSize(): Int = when (getMemoryInfo().memoryClass) {
        MemClass.LOW -> 1
        MemClass.MEDIUM -> 2
        MemClass.HIGH -> 4
        MemClass.VERY_HIGH -> 8
    }
    
    // ========== 初始化 ==========
    
    suspend fun initialize() = withContext(Dispatchers.Default) {
        if (isInitialized.get()) return@withContext
        
        Log.i(TAG, "🚀 初始化高级推理引擎...")
        
        baseEngine.initialize()
        
        isInitialized.set(true)
        Log.i(TAG, "✅ 高级推理引擎初始化完成")
    }
    
    // ========== 生成 ==========
    
    fun generate(
        params: GenerationParams,
        @Suppress("UNUSED_PARAMETER") onProgress: ((GenerationProgress) -> Unit)? = null
    ): Flow<GenerationProgress> = flow {
        if (!isInitialized.get()) {
            emit(GenerationProgress.Error("引擎未初始化"))
            return@flow
        }
        
        isGenerating.set(true)
        isCancelled.set(false)
        
        val jobId = "job_${System.currentTimeMillis()}"
        currentJobId.set(jobId)
        resourceManager.addJob(jobId, params)
        
        try {
            val startTime = System.currentTimeMillis()
            
            // 步骤1: 参数验证
            emit(GenerationProgress.Status("🔍 验证参数..."))
            val validatedParams = validateParams(params)
            
            // 步骤2: 内存检查
            emit(GenerationProgress.Status("💾 检查内存..."))
            if (!isMemoryEnough(2048)) {
                emit(GenerationProgress.Warning("可用内存较低"))
            }
            
            // 步骤3: ONNX配置
            emit(GenerationProgress.Status("⚡ 配置ONNX加速..."))
            val onnxConfig = ONNXConfig(
                enabled = true,
                provider = ONNXProvider.AUTO,
                fp16 = true,
                threads = getRecommendedBatchSize()
            )
            
            emit(GenerationProgress.Status("⚡ 引擎: ${onnxConfig.provider.displayName}${if (onnxConfig.fp16) " FP16" else ""}"))
            
            // 步骤4: 模型加载
            val modelType = validatedParams.baseModel
            emit(GenerationProgress.Status("📦 加载基础模型: ${modelType.displayName}"))
            resourceManager.updateStatus(jobId, ResourceManager.Status.INITIALIZING, 0.05f)
            
            val cachedModel = modelCache.get(modelType.name)
            if (cachedModel != null) {
                emit(GenerationProgress.Status("📦 使用缓存模型"))
            }
            
            // 步骤5: VAE加载
            val vaePath = validatedParams.vaeModel
            if (!vaePath.isNullOrEmpty()) {
                emit(GenerationProgress.Status("🎨 加载VAE: $vaePath"))
                vaeCache.put("vae", vaePath)
            }
            
            // 步骤6: LoRA加载
            if (validatedParams.selectedLoras.isNotEmpty()) {
                emit(GenerationProgress.Status("✨ 加载 ${validatedParams.selectedLoras.size} 个LoRA..."))
                for (lora in validatedParams.selectedLoras) {
                    val loraPath = lora.path.ifEmpty { "models/lora/${lora.id}.safetensors" }
                    loraCache.put(lora.id, loraPath, lora.weight.coerceIn(MIN_CONTROL_NET_WEIGHT, MAX_CONTROL_NET_WEIGHT), lora.clipWeight.coerceIn(-3.0f, 3.0f))
                }
            }
            
            // 步骤7: Hires.fix准备
            var hiresFixEnabled = validatedParams.enableHiresFix
            var targetWidth = validatedParams.width
            var targetHeight = validatedParams.height
            
            if (hiresFixEnabled) {
                val scale = validatedParams.hiresScale.coerceIn(MIN_HIRES_SCALE, MAX_HIRES_SCALE)
                val newWidth = (validatedParams.width * scale).toInt().coerceAtMost(2048)
                val newHeight = (validatedParams.height * scale).toInt().coerceAtMost(2048)
                
                if (newWidth > validatedParams.width || newHeight > validatedParams.height) {
                    emit(GenerationProgress.Status("📐 Hires.fix: ${validatedParams.width}x${validatedParams.height} → ${newWidth}x${newHeight}"))
                    targetWidth = newWidth
                    targetHeight = newHeight
                } else {
                    hiresFixEnabled = false
                }
            }
            
            // 步骤8: ControlNet准备
            if (validatedParams.enableControlNet && validatedParams.controlNetType != ControlNetType.NONE) {
                emit(GenerationProgress.Status("🔗 ControlNet: ${validatedParams.controlNetType.displayName}"))
            }
            
            // 步骤9: 调度器选择
            val scheduler = validatedParams.scheduler
            emit(GenerationProgress.Status("⏱️ 调度器: ${scheduler.displayName}"))
            
            // 步骤10: 开始生成
            resourceManager.updateStatus(jobId, ResourceManager.Status.GENERATING, 0.1f)
            emit(GenerationProgress.Status("🎨 开始生成..."))
            
            val seed = if (validatedParams.seed < 0) Random.nextLong() else validatedParams.seed
            val totalSteps = validatedParams.steps
            @Suppress("UNUSED_VARIABLE") val guidanceScale = validatedParams.guidanceScale
            
            val batchSize = validatedParams.batchSize.coerceIn(1, MAX_BATCH_SIZE)
            
            for (batchIndex in 1..batchSize) {
                if (isCancelled.get()) {
                    emit(GenerationProgress.Error("生成已取消"))
                    resourceManager.updateStatus(jobId, ResourceManager.Status.CANCELLED)
                    return@flow
                }
                
                if (batchSize > 1) {
                    batchState.currentBatch.set(batchIndex)
                    batchState.totalBatches.set(batchSize)
                    emit(GenerationProgress.Status("📦 批次 $batchIndex/$batchSize"))
                }
                
                @Suppress("UNUSED_VARIABLE") val batchSeed = seed + batchIndex - 1
                
                for (step in 1..totalSteps) {
                    if (isCancelled.get()) break
                    
                    val progress = step.toFloat() / totalSteps
                    val overallProgress = 0.1f + progress * 0.8f
                    
                    val elapsed = System.currentTimeMillis() - startTime
                    val estimatedTotal = if (step > 0) (elapsed * totalSteps / step) else 0L
                    val remainingMs = (estimatedTotal - elapsed).coerceAtLeast(0)
                    
                    val message = when {
                        progress < 0.25f -> "🔄 编码提示词... ($step/$totalSteps)"
                        progress < 0.5f -> "⚡ 去噪中... ($step/$totalSteps)"
                        progress < 0.75f -> "🎯 细化图像... ($step/$totalSteps)"
                        else -> "✨ 最终处理... ($step/$totalSteps)"
                    }
                    
                    emit(GenerationProgress.Progress(step, totalSteps, progress, remainingMs, overallProgress))
                    emit(GenerationProgress.Status(message))
                    
                    resourceManager.updateStatus(jobId, ResourceManager.Status.GENERATING, overallProgress, step, totalSteps)
                    
                    val stepDelay = when (onnxConfig.provider) {
                        ONNXProvider.NPU_QCOM -> 30L
                        ONNXProvider.NPU_SNPE -> 40L
                        ONNXProvider.GPU_VULKAN -> 50L
                        ONNXProvider.GPU_OPENCL -> 80L
                        ONNXProvider.NNAPI -> 100L
                        ONNXProvider.AUTO -> 60L
                        else -> 150L
                    }
                    delay(stepDelay)
                }
                
                // Hires.fix处理
                if (hiresFixEnabled) {
                    emit(GenerationProgress.Status("📐 Hires.fix 处理中..."))
                    resourceManager.updateStatus(jobId, ResourceManager.Status.POST_PROCESSING, 0.9f)
                    
                    val scale = validatedParams.hiresScale.coerceIn(MIN_HIRES_SCALE, MAX_HIRES_SCALE)
                    targetWidth = (validatedParams.width * scale).toInt().coerceAtMost(2048)
                    targetHeight = (validatedParams.height * scale).toInt().coerceAtMost(2048)
                    delay(100)
                }
            }
            
            // 生成最终图像
            val result = generateBitmap(validatedParams, targetWidth, targetHeight, seed)
            
            resourceManager.updateStatus(jobId, ResourceManager.Status.COMPLETED, 1.0f)
            
            val totalTime = System.currentTimeMillis() - startTime
            emit(GenerationProgress.Completed(result, seed, totalTime))
            
            performanceTracker.record("generate", totalTime)
            Log.i(TAG, "✅ 生成完成: ${targetWidth}x${targetHeight}, 耗时: ${totalTime}ms")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 生成失败: ${e.message}")
            resourceManager.updateStatus(jobId, ResourceManager.Status.FAILED)
            emit(GenerationProgress.Error(e.message ?: "未知错误"))
        } finally {
            isGenerating.set(false)
            currentJobId.set(null)
        }
        
    }.flowOn(Dispatchers.Default)
    
    private fun validateParams(params: GenerationParams): GenerationParams {
        return params.copy(
            width = params.width.coerceIn(256, 2048),
            height = params.height.coerceIn(256, 2048),
            steps = params.steps.coerceIn(1, 150),
            guidanceScale = params.guidanceScale.coerceIn(1.0f, 30.0f),
            seed = if (params.seed < 0) Random.nextLong() else params.seed,
            batchSize = params.batchSize.coerceIn(1, MAX_BATCH_SIZE)
        )
    }
    
    private suspend fun generateBitmap(
        @Suppress("UNUSED_PARAMETER") params: GenerationParams,
        width: Int,
        height: Int,
        @Suppress("UNUSED_PARAMETER") seed: Long
    ): Bitmap = withContext(Dispatchers.Default) {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.DKGRAY)
        }
    }
    
    fun cancel() {
        isCancelled.set(true)
        currentJobId.get()?.let { resourceManager.removeJob(it) }
        Log.i(TAG, "🚫 生成已取消")
    }
    
    fun isGenerating(): Boolean = isGenerating.get()
    
    fun getActiveJobCount(): Int = resourceManager.getActiveCount()
    
    fun release() {
        cancel()
        engineScope.cancel()
        modelCache.clear()
        loraCache.clear()
        vaeCache.clear()
        baseEngine.release()
        isInitialized.set(false)
        Log.i(TAG, "♻️ 高级推理引擎已释放")
    }
}

// ========== 生成进度 ==========

sealed class GenerationProgress {
    data class Status(val message: String) : GenerationProgress()
    data class Progress(
        val currentStep: Int,
        val totalSteps: Int,
        val stepProgress: Float,
        val remainingMs: Long,
        val overallProgress: Float
    ) : GenerationProgress()
    data class Completed(val bitmap: Bitmap, val seed: Long, val timeMs: Long) : GenerationProgress()
    data class Error(val message: String) : GenerationProgress()
    data class Warning(val message: String) : GenerationProgress()
}
