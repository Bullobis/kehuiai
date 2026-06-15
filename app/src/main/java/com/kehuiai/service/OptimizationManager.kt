package com.kehuiai.service

import android.content.Context
import android.os.Build
import android.util.Log
import com.kehuiai.data.model.BaseModelType
import com.kehuiai.data.model.MemoryStrategy
import com.kehuiai.data.model.ControlNetType
import com.kehuiai.data.model.GenerationMode
import com.kehuiai.data.model.GenerationParams
import com.kehuiai.data.model.HiresUpscaler
import com.kehuiai.data.model.LoraParam
import com.kehuiai.data.model.ONNXProvider
import com.kehuiai.data.model.OptimizationLevel
import com.kehuiai.data.model.SchedulerType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * 方向四：性能优化管理器
 * 支持：ONNX Runtime 加速、Lora Mapping 优化、更快的调度器
 */
class OptimizationManager(private val context: Context) {
    
    companion object {
        private const val TAG = "OptimizationManager"
        
        // 缓存目录
        private const val OPT_CACHE_DIR = "cache/optimized"
        
        // ONNX 模型缓存
        private const val ONNX_CACHE_DIR = "cache/onnx"
    }
    
    private val cacheDir = File(context.filesDir, OPT_CACHE_DIR)
    private val onnxCacheDir = File(context.filesDir, ONNX_CACHE_DIR)
    
    private val _settings = MutableStateFlow(OptimizationSettings())
    val settings: StateFlow<OptimizationSettings> = _settings.asStateFlow()
    
    private val _hardwareCapabilities = MutableStateFlow(HardwareCapabilities())
    val hardwareCapabilities: StateFlow<HardwareCapabilities> = _hardwareCapabilities.asStateFlow()
    
    private val _currentProvider = MutableStateFlow<ONNXProvider?>(null)
    val currentProvider: StateFlow<ONNXProvider?> = _currentProvider.asStateFlow()
    
    private var isInitialized = false
    
    init {
        if (!cacheDir.exists()) cacheDir.mkdirs()
        if (!onnxCacheDir.exists()) onnxCacheDir.mkdirs()
    }
    
    /**
     * 初始化
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true
        
        try {
            Log.i(TAG, "初始化性能优化管理器...")
            
            // 检测硬件能力
            detectHardwareCapabilities()
            
            // 加载设置
            loadSettings()
            
            // 选择最佳 Provider
            selectBestProvider()
            
            isInitialized = true
            Log.i(TAG, "性能优化管理器初始化完成")
            Log.i(TAG, "最佳 Provider: ${_currentProvider.value}")
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "初始化失败: ${e.message}")
            false
        }
    }
    
    /**
     * 检测硬件能力
     */
    private fun detectHardwareCapabilities() {
        val capabilities = HardwareCapabilities()
        
        // 检测 CPU
        capabilities.cpuCores = Runtime.getRuntime().availableProcessors()
        capabilities.cpuFeatures = detectCPUFeatures()
        
        // 检测 GPU
        capabilities.gpuVendor = detectGPUVendor()
        capabilities.hasOpenCL = detectOpenCL()
        capabilities.hasVulkan = detectVulkan()
        
        // 检测 NPU
        capabilities.hasNPU = detectNPU()
        capabilities.npuVendor = detectNPUVendor()
        
        // 检测内存
        capabilities.totalMemory = getTotalMemory()
        capabilities.isLowMemoryDevice = capabilities.totalMemory < 6 * 1024 * 1024 * 1024L // < 6GB
        
        // 检测 Android 版本
        capabilities.androidVersion = Build.VERSION.SDK_INT
        
        _hardwareCapabilities.value = capabilities
        
        Log.i(TAG, "硬件检测结果:")
        Log.i(TAG, "  - CPU: ${capabilities.cpuCores} 核")
        Log.i(TAG, "  - GPU: ${capabilities.gpuVendor}")
        Log.i(TAG, "  - OpenCL: ${capabilities.hasOpenCL}")
        Log.i(TAG, "  - Vulkan: ${capabilities.hasVulkan}")
        Log.i(TAG, "  - NPU: ${capabilities.hasNPU} (${capabilities.npuVendor})")
        Log.i(TAG, "  - 内存: ${capabilities.totalMemory / (1024 * 1024 * 1024)} GB")
    }
    
    /**
     * 检测 CPU 特性
     */
    private fun detectCPUFeatures(): List<String> {
        val features = mutableListOf<String>()
        
        try {
            val cpuInfo = File("/proc/cpuinfo").readText()
            
            when {
                cpuInfo.contains("ARMv8") || cpuInfo.contains("aarch64") -> {
                    features.add("ARMv8")
                    features.add("64-bit")
                }
                cpuInfo.contains("neon") || cpuInfo.contains("NEON") -> {
                    features.add("NEON")
                }
                cpuInfo.contains("asimd") -> features.add("ASIMD")
                cpuInfo.contains("fp") -> features.add("FP")
            }
            
            // 检测具体型号
            when {
                cpuInfo.contains("Cortex-A78") -> features.add("Cortex-A78")
                cpuInfo.contains("Cortex-A76") -> features.add("Cortex-A76")
                cpuInfo.contains("Cortex-A55") -> features.add("Cortex-A55")
                cpuInfo.contains("Kryo") -> features.add("Kryo")
                cpuInfo.contains("Snapdragon") -> features.add("Snapdragon")
                cpuInfo.contains("Dimensity") -> features.add("Dimensity")
                cpuInfo.contains("Exynos") -> features.add("Exynos")
                cpuInfo.contains("MediaTek") -> features.add("MediaTek")
            }
        } catch (e: Exception) {
            Log.w(TAG, "CPU 特性检测失败: ${e.message}")
        }
        
        return features
    }
    
    /**
     * 检测 GPU 厂商
     */
    private fun detectGPUVendor(): String {
        return when {
            Build.HARDWARE.contains(" adreno", ignoreCase = true) -> "Qualcomm Adreno"
            Build.HARDWARE.contains(" mali", ignoreCase = true) -> {
                when {
                    Build.HARDWARE.contains("mali-g", ignoreCase = true) -> "ARM Mali-G"
                    else -> "ARM Mali"
                }
            }
            Build.HARDWARE.contains("powervr", ignoreCase = true) -> "PowerVR"
            Build.HARDWARE.contains("img", ignoreCase = true) -> "IMG"
            else -> "Unknown"
        }
    }
    
    /**
     * 检测 OpenCL 支持
     */
    private fun detectOpenCL(): Boolean {
        return try {
            // 简单检测：检查系统库
            val result = Runtime.getRuntime().exec("find /system/lib -name '*OpenCL*' 2>/dev/null | head -5")
            result.inputStream.bufferedReader().readText().isNotBlank()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 检测 Vulkan 支持
     */
    private fun detectVulkan(): Boolean {
        return try {
            val result = Runtime.getRuntime().exec("vulkaninfo 2>/dev/null | head -20")
            result.inputStream.bufferedReader().readText().contains("Vulkan")
        } catch (e: Exception) {
            // Android 5.0+ 默认支持 Vulkan
            Build.VERSION.SDK_INT >= 21
        }
    }
    
    /**
     * 检测 NPU 支持
     */
    private fun detectNPU(): Boolean {
        return try {
            // 检查 NPU 驱动
            val npuDirs = listOf(
                "/sys/class/npu",
                "/sys/devices/system/npu",
                "/proc/npu"
            )
            
            npuDirs.any { File(it).exists() } ||
            Build.HARDWARE.contains("qnp", ignoreCase = true) || // Qualcomm NPU
            Build.HARDWARE.contains("mtk_npu", ignoreCase = true) || // MediaTek NPU
            Build.HARDWARE.contains("npu", ignoreCase = true) // 通用 NPU
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 检测 NPU 厂商
     */
    private fun detectNPUVendor(): String {
        return when {
            Build.HARDWARE.contains("qnp", ignoreCase = true) -> "Qualcomm AI Engine (QNN)"
            Build.HARDWARE.contains("mtk_npu", ignoreCase = true) -> "MediaTek NeuroPilot"
            Build.HARDWARE.contains("da Vinci", ignoreCase = true) -> "Huawei Da Vinci"
            Build.HARDWARE.contains("npu", ignoreCase = true) -> "Integrated NPU"
            else -> "Unknown"
        }
    }
    
    /**
     * 获取总内存
     */
    private fun getTotalMemory(): Long {
        return try {
            val memInfo = android.app.ActivityManager.MemoryInfo()
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            activityManager.getMemoryInfo(memInfo)
            memInfo.totalMem
        } catch (e: Exception) {
            4L * 1024 * 1024 * 1024 // 默认 4GB
        }
    }
    
    /**
     * 选择最佳 Provider
     */
    private fun selectBestProvider() {
        val caps = _hardwareCapabilities.value
        
        val bestProvider = when {
            // Qualcomm NPU
            caps.npuVendor.contains("Qualcomm") && caps.hasNPU -> ONNXProvider.QNN
            
            // MediaTek NPU
            caps.npuVendor.contains("MediaTek") && caps.hasNPU -> ONNXProvider.QNN
            
            // Apple Silicon
            Build.MODEL.contains("iPhone") || Build.MODEL.contains("iPad") -> ONNXProvider.COREML
            
            // NVIDIA GPU (如果可用)
            caps.gpuVendor.contains("NVIDIA") -> ONNXProvider.CUDA
            
            // DirectML (Windows)
            caps.gpuVendor.contains("AMD") || caps.gpuVendor.contains("NVIDIA") -> ONNXProvider.DIRECTML
            
            // Vulkan GPU
            caps.hasVulkan && caps.gpuVendor != "Unknown" -> ONNXProvider.VULKAN
            
            // OpenCL GPU
            caps.hasOpenCL && caps.gpuVendor != "Unknown" -> ONNXProvider.OPENCL
            
            // Android NNAPI
            Build.VERSION.SDK_INT >= 27 -> ONNXProvider.NNAPI
            
            // 通用加速
            Build.VERSION.SDK_INT >= 24 -> ONNXProvider.XNNPACK
            
            else -> ONNXProvider.CPU
        }
        
        _currentProvider.value = bestProvider
        Log.i(TAG, "选择最佳 Provider: $bestProvider")
    }
    
    /**
     * 设置 ONNX Provider
     */
    fun setONNXProvider(provider: ONNXProvider) {
        _currentProvider.value = provider
        _settings.value = _settings.value.copy(onnxProvider = provider)
        Log.i(TAG, "ONNX Provider 设置为: ${provider.displayName}")
    }
    
    /**
     * 启用/禁用 ONNX
     */
    fun setONNXEnabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(onnxEnabled = enabled)
        Log.i(TAG, "ONNX 加速: $enabled")
    }
    
    /**
     * 启用/禁用 FP16
     */
    fun setFP16Enabled(enabled: Boolean) {
        _settings.value = _settings.value.copy(fp16Enabled = enabled)
        Log.i(TAG, "FP16 半精度: $enabled")
    }
    
    /**
     * 设置优化级别
     */
    fun setOptimizationLevel(level: OptimizationLevel) {
        _settings.value = _settings.value.copy(optimizationLevel = level)
        Log.i(TAG, "优化级别: ${level.displayName}")
    }
    
    /**
     * 设置 CPU 线程数
     */
    fun setCPUThreads(threads: Int) {
        val cappedThreads = threads.coerceIn(1, _hardwareCapabilities.value.cpuCores)
        _settings.value = _settings.value.copy(cpuThreads = cappedThreads)
        Log.i(TAG, "CPU 线程: $cappedThreads")
    }
    
    /**
     * 设置内存策略
     */
    fun setMemoryStrategy(strategy: MemoryStrategy) {
        _settings.value = _settings.value.copy(memoryStrategy = strategy)
        Log.i(TAG, "内存策略: ${strategy.displayName}")
    }
    
    /**
     * 获取优化后的参数
     */
    fun optimizeParams(params: GenerationParams): GenerationParams {
        val settings = _settings.value
        val caps = _hardwareCapabilities.value
        
        // 根据设置调整参数
        return params.copy(
            enableONNX = settings.onnxEnabled,
            onnxProvider = settings.onnxProvider,
            enableFP16 = settings.fp16Enabled && settings.onnxProvider != ONNXProvider.CPU,
            cpuThreads = settings.cpuThreads
        )
    }
    
    /**
     * 计算预估速度
     */
    fun estimateSpeedMultiplier(params: GenerationParams): Float {
        var multiplier = 1.0f
        
        // ONNX 加速
        if (params.enableONNX) {
            multiplier *= when (params.onnxProvider) {
                ONNXProvider.QNN -> 3.0f // NPU 最快
                ONNXProvider.CUDA -> 2.5f
                ONNXProvider.COREML -> 2.5f
                ONNXProvider.DIRECTML -> 2.0f
                ONNXProvider.VULKAN -> 1.8f
                ONNXProvider.OPENCL -> 1.5f
                ONNXProvider.NNAPI -> 1.5f
                ONNXProvider.XNNPACK -> 1.3f
                else -> 1.0f
            }
        }
        
        // FP16 加速
        if (params.enableFP16 && params.onnxProvider != ONNXProvider.CPU) {
            multiplier *= 1.5f
        }
        
        // 调度器速度
        multiplier *= params.scheduler.speed
        
        // 优化级别
        multiplier *= settings.value.optimizationLevel.speedMultiplier
        
        return multiplier
    }
    
    /**
     * 预估生成时间
     */
    fun estimateGenerationTime(steps: Int, width: Int, height: Int, params: GenerationParams): Long {
        // 基准时间（毫秒）
        val pixelCount = width * height
        val baseTime = (pixelCount / 1024.0 * steps * 10).toLong()
        
        // 速度倍数
        val speedMultiplier = estimateSpeedMultiplier(params)
        
        return (baseTime / speedMultiplier).toLong()
    }
    
    /**
     * 获取可用 Provider 列表
     */
    fun getAvailableProviders(): List<ONNXProvider> {
        val caps = _hardwareCapabilities.value
        val providers = mutableListOf(ONNXProvider.CPU)
        
        if (caps.hasNPU && (caps.npuVendor.contains("Qualcomm") || caps.npuVendor.contains("MediaTek"))) {
            providers.add(ONNXProvider.QNN)
        }
        
        if (caps.hasVulkan) {
            providers.add(ONNXProvider.VULKAN)
        }
        
        if (caps.hasOpenCL) {
            providers.add(ONNXProvider.OPENCL)
        }
        
        if (Build.VERSION.SDK_INT >= 27) {
            providers.add(ONNXProvider.NNAPI)
        }
        
        if (Build.VERSION.SDK_INT >= 24) {
            providers.add(ONNXProvider.XNNPACK)
        }
        
        return providers.sortedByDescending { it.priority }
    }
    
    /**
     * 导出设置
     */
    fun exportSettings(): OptimizationSettings = _settings.value
    
    /**
     * 导入设置
     */
    fun importSettings(settings: OptimizationSettings) {
        _settings.value = settings
        saveSettings()
    }
    
    /**
     * 保存设置
     */
    private fun saveSettings() {
        try {
            val settings = _settings.value
            val file = File(cacheDir, "optimization_settings.json")
            file.writeText("""
                {
                    "onnxEnabled": ${settings.onnxEnabled},
                    "onnxProvider": "${settings.onnxProvider.name}",
                    "fp16Enabled": ${settings.fp16Enabled},
                    "optimizationLevel": "${settings.optimizationLevel.name}",
                    "cpuThreads": ${settings.cpuThreads},
                    "memoryStrategy": "${settings.memoryStrategy.name}"
                }
            """.trimIndent())
            Log.d(TAG, "设置已保存")
        } catch (e: Exception) {
            Log.e(TAG, "设置保存失败: ${e.message}")
        }
    }
    
    /**
     * 加载设置
     */
    private fun loadSettings() {
        try {
            val file = File(cacheDir, "optimization_settings.json")
            if (file.exists()) {
                val json = file.readText()
                // 简化解析
                val enabled = json.contains("\"onnxEnabled\": true")
                val fp16 = json.contains("\"fp16Enabled\": true")
                
                _settings.value = _settings.value.copy(
                    onnxEnabled = enabled,
                    fp16Enabled = fp16
                )
                Log.d(TAG, "设置已加载")
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置加载失败: ${e.message}")
        }
    }
    
    /**
     * 清理缓存
     */
    suspend fun clearCache(): Boolean = withContext(Dispatchers.IO) {
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
            onnxCacheDir.listFiles()?.forEach { it.delete() }
            Log.i(TAG, "优化缓存已清理")
            true
        } catch (e: Exception) {
            Log.e(TAG, "缓存清理失败: ${e.message}")
            false
        }
    }
    
    /**
     * 获取缓存大小
     */
    fun getCacheSize(): Long {
        var size = 0L
        cacheDir.listFiles()?.forEach { size += it.length() }
        onnxCacheDir.listFiles()?.forEach { size += it.length() }
        return size
    }
    
    /**
     * 释放资源
     */
    fun release() {
        _settings.value = OptimizationSettings()
        _currentProvider.value = null
        isInitialized = false
    }
}

/**
 * 优化设置
 */
data class OptimizationSettings(
    val onnxEnabled: Boolean = false,
    val onnxProvider: ONNXProvider = ONNXProvider.CPU,
    val fp16Enabled: Boolean = true,
    val optimizationLevel: OptimizationLevel = OptimizationLevel.BASIC,
    val cpuThreads: Int = 4,
    val memoryStrategy: MemoryStrategy = MemoryStrategy.BALANCED
)

/**
 * 硬件能力
 */
data class HardwareCapabilities(
    var cpuCores: Int = 4,
    var cpuFeatures: List<String> = emptyList(),
    var gpuVendor: String = "Unknown",
    var hasOpenCL: Boolean = false,
    var hasVulkan: Boolean = false,
    var hasNPU: Boolean = false,
    var npuVendor: String = "Unknown",
    var totalMemory: Long = 0L,
    var isLowMemoryDevice: Boolean = true,
    var androidVersion: Int = 0
) {
    val memoryGB: Float get() = totalMemory / (1024f * 1024f * 1024f)
    
    val hasGPUAcceleration: Boolean get() = hasOpenCL || hasVulkan
    
    val summary: String get() = buildString {
        append("CPU: $cpuCores 核")
        if (cpuFeatures.isNotEmpty()) {
            append(" (${cpuFeatures.take(2).joinToString(", ")})")
        }
        append("\n")
        append("GPU: $gpuVendor")
        if (hasNPU) append(" + NPU ($npuVendor)")
        append("\n")
        append("内存: ${String.format("%.1f", memoryGB)} GB")
    }
}
