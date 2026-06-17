package com.kehuiai.service.performance

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.kehuiai.data.model.ONNXProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import kotlin.math.roundToLong

/**
 * 快绘AI v2.4.0 性能优化管理器
 * 全面监控和优化性能，包括 CPU、内存、GPU/NPU 等
 */
class PerformanceOptimizer(private val context: Context) {
    
    companion object {
        private const val TAG = "PerformanceOptimizer"
        
        // 性能监控间隔
        const val MONITOR_INTERVAL_MS = 1000L
        
        // 内存警告阈值
        const val MEMORY_WARNING_THRESHOLD = 0.80f
        const val MEMORY_CRITICAL_THRESHOLD = 0.90f
        
        // CPU 使用警告阈值
        const val CPU_WARNING_THRESHOLD = 0.85f
    }
    
    // 性能监控状态
    private val _performanceState = MutableStateFlow(PerformanceState())
    val performanceState: StateFlow<PerformanceState> = _performanceState.asStateFlow()
    
    // 监控任务
    private var monitorJob: Job? = null
    
    // 系统信息
    private val systemInfo = detectSystemInfo()
    
    /**
     * v2.4.0 性能状态
     */
    data class PerformanceState(
        val cpuUsage: Float = 0f,
        val memoryUsage: Float = 0f,
        val memoryUsedMB: Long = 0,
        val memoryTotalMB: Long = 0,
        val temperature: Float = 0f,
        val batteryLevel: Int = 100,
        val recommendedProvider: ONNXProvider = ONNXProvider.CPU,
        val isLowPowerMode: Boolean = false,
        val thermalThrottling: Boolean = false,
        val recommendedQuality: QualityLevel = QualityLevel.BALANCED,
        val fps: Int = 0,
        val gpuUsage: Float = 0f
    )
    
    /**
     * 质量级别
     */
    enum class QualityLevel(
        val displayName: String,
        val stepsMultiplier: Float,
        val memoryMultiplier: Float
    ) {
        ULTRA_LOW("极低", 0.5f, 0.3f),
        LOW("低", 0.7f, 0.5f),
        BALANCED("均衡", 1.0f, 0.7f),
        HIGH("高质量", 1.3f, 0.9f),
        ULTRA("极致", 1.5f, 1.0f)
    }
    
    /**
     * 系统信息
     */
    data class SystemInfo(
        val deviceModel: String,
        val manufacturer: String,
        val socModel: String,
        val cpuCores: Int,
        val totalRAM: Long,
        val hasNPU: Boolean,
        val hasGPU: Boolean,
        val gpuModel: String,
        val isEmulator: Boolean,
        val androidVersion: Int
    )
    
    /**
     * v2.4.0 检测系统信息
     */
    private fun detectSystemInfo(): SystemInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        val cpuInfo = readCpuInfo()
        val cpuCores = Runtime.getRuntime().availableProcessors()
        
        val hasNPU = checkHardwareCapability("npu") || 
                     checkHardwareCapability("qnn") ||
                     checkHardwareCapability("hexagon")
        
        val hasGPU = checkHardwareCapability("gpu") ||
                     checkHardwareCapability("adreno") ||
                     checkHardwareCapability("mali") ||
                     checkHardwareCapability("powervr")
        
        val gpuModel = detectGpuModel()
        
        val isEmulator = Build.FINGERPRINT.contains("generic") ||
                        Build.FINGERPRINT.contains("emulator") ||
                        Build.MODEL.contains("Emulator")
        
        return SystemInfo(
            deviceModel = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            socModel = cpuInfo.socModel,
            cpuCores = cpuCores,
            totalRAM = memInfo.totalMem,
            hasNPU = hasNPU,
            hasGPU = hasGPU,
            gpuModel = gpuModel,
            isEmulator = isEmulator,
            androidVersion = Build.VERSION.SDK_INT
        )
    }
    
    /**
     * 读取 CPU 信息
     */
    private fun readCpuInfo(): CpuInfo {
        return try {
            val cpuInfo = File("/proc/cpuinfo").readText()
            val lines = cpuInfo.lines()
            
            var hardware = ""
            var modelName = ""
            
            for (line in lines) {
                when {
                    line.startsWith("Hardware:") -> hardware = line.substringAfter(":").trim()
                    line.startsWith("model name:") -> modelName = line.substringAfter(":").trim()
                }
            }
            
            CpuInfo(
                socModel = hardware.ifEmpty { modelName },
                architecture = System.getProperty("os.arch") ?: "unknown"
            )
        } catch (e: Exception) {
            CpuInfo(socModel = "Unknown", architecture = "unknown")
        }
    }
    
    data class CpuInfo(
        val socModel: String,
        val architecture: String
    )
    
    /**
     * 检查硬件能力
     */
    private fun checkHardwareCapability(cap: String): Boolean {
        return try {
            val cpuInfo = File("/proc/cpuinfo").readText().lowercase()
            val procInfo = File("/proc/").listFiles()
                ?.filter { it.name.startsWith("") }
                ?.joinToString { it.name }
                ?.lowercase() ?: ""
            
            cpuInfo.contains(cap) || procInfo.contains(cap)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 检测 GPU 型号
     */
    private fun detectGpuModel(): String {
        return when {
            systemInfo.hasNPU -> "Qualcomm NPU/QNN"
            checkHardwareCapability("adreno") -> {
                val matches = Regex("adreno\\s*(\\d+)").find(
                    File("/proc/cpuinfo").readText().lowercase()
                )
                "Adreno ${matches?.groupValues?.get(1) ?: "Unknown"}"
            }
            checkHardwareCapability("mali") -> {
                val matches = Regex("mali\\s*-?g\\??(\\d+)").find(
                    File("/proc/cpuinfo").readText().lowercase()
                )
                "Mali ${matches?.groupValues?.get(1) ?: "Unknown"}"
            }
            else -> "Unknown GPU"
        }
    }
    
    /**
     * v2.4.0 启动性能监控
     */
    fun startMonitoring(scope: CoroutineScope = CoroutineScope(Dispatchers.Default)) {
        if (monitorJob?.isActive == true) return
        
        monitorJob = scope.launch {
            while (isActive) {
                updatePerformanceState()
                delay(MONITOR_INTERVAL_MS)
            }
        }
        
        Log.i(TAG, "性能监控已启动")
    }
    
    /**
     * v2.4.0 停止性能监控
     */
    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        Log.i(TAG, "性能监控已停止")
    }
    
    /**
     * v2.4.0 更新性能状态
     */
    private suspend fun updatePerformanceState() = withContext(Dispatchers.IO) {
        try {
            val memoryInfo = getMemoryInfo()
            val cpuUsage = getCpuUsage()
            val temperature = getCpuTemperature()
            val batteryLevel = getBatteryLevel()
            
            val isLowPower = batteryLevel < 20
            val isThrottling = temperature > 45f
            
            val recommendedProvider = recommendProvider()
            val recommendedQuality = recommendQuality(cpuUsage, memoryInfo.usagePercent, temperature)
            
            _performanceState.value = PerformanceState(
                cpuUsage = cpuUsage,
                memoryUsage = memoryInfo.usagePercent,
                memoryUsedMB = memoryInfo.usedMB,
                memoryTotalMB = memoryInfo.totalMB,
                temperature = temperature,
                batteryLevel = batteryLevel,
                recommendedProvider = recommendedProvider,
                isLowPowerMode = isLowPower,
                thermalThrottling = isThrottling,
                recommendedQuality = recommendedQuality,
                gpuUsage = if (systemInfo.hasGPU) estimateGpuUsage() else 0f
            )
        } catch (e: Exception) {
            Log.w(TAG, "性能监控更新失败: ${e.message}")
        }
    }
    
    /**
     * v2.4.0 获取内存信息
     */
    private fun getMemoryInfo(): MemoryInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        val totalMB = memInfo.totalMem / (1024 * 1024)
        val availableMB = memInfo.availMem / (1024 * 1024)
        val usedMB = totalMB - availableMB
        
        return MemoryInfo(
            totalMB = totalMB,
            usedMB = usedMB,
            availableMB = availableMB,
            usagePercent = usedMB.toFloat() / totalMB,
            isLowMemory = memInfo.lowMemory
        )
    }
    
    data class MemoryInfo(
        val totalMB: Long,
        val usedMB: Long,
        val availableMB: Long,
        val usagePercent: Float,
        val isLowMemory: Boolean
    )
    
    /**
     * v2.4.0 获取 CPU 使用率
     */
    private fun getCpuUsage(): Float {
        return try {
            val reader = BufferedReader(FileReader("/proc/stat"))
            val line = reader.readLine()
            reader.close()
            
            val parts = line.split("\\s+".toRegex())
            if (parts.size >= 5) {
                val user = parts[1].toLongOrNull() ?: 0
                val nice = parts[2].toLongOrNull() ?: 0
                val system = parts[3].toLongOrNull() ?: 0
                val idle = parts[4].toLongOrNull() ?: 0
                val iowait = parts.getOrNull(5)?.toLongOrNull() ?: 0
                val irq = parts.getOrNull(6)?.toLongOrNull() ?: 0
                val softirq = parts.getOrNull(7)?.toLongOrNull() ?: 0
                
                val total = user + nice + system + idle + iowait + irq + softirq
                val active = user + nice + system + irq + softirq
                
                if (total > 0) {
                    (active.toFloat() / total * 100).coerceIn(0f, 100f)
                } else 0f
            } else 0f
        } catch (e: Exception) {
            0f
        }
    }
    
    /**
     * v2.4.0 获取 CPU 温度
     */
    private fun getCpuTemperature(): Float {
        val thermalPaths = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/devices/virtual/thermal/thermal_zone0/temp",
            "/sys/class/hwmon/hwmon0/temp1_input"
        )
        
        for (path in thermalPaths) {
            try {
                val temp = File(path).readText().trim().toLong()
                return if (temp > 1000) temp / 1000f else temp.toFloat()
            } catch (e: Exception) {
                continue
            }
        }
        
        return 0f
    }
    
    /**
     * v2.4.0 获取电池电量
     */
    private fun getBatteryLevel(): Int {
        return try {
            val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, filter)
            
            val level = batteryStatus?.getIntExtra(
                android.os.BatteryManager.EXTRA_LEVEL, -1
            ) ?: -1
            
            val scale = batteryStatus?.getIntExtra(
                android.os.BatteryManager.EXTRA_SCALE, -1
            ) ?: -1
            
            if (level >= 0 && scale > 0) {
                (level * 100 / scale)
            } else 100
        } catch (e: Exception) {
            100
        }
    }
    
    /**
     * v2.4.0 推荐推理 Provider
     */
    private fun recommendProvider(): ONNXProvider {
        val state = _performanceState.value
        
        return when {
            systemInfo.hasNPU && !state.thermalThrottling -> ONNXProvider.QNN
            systemInfo.hasGPU && !state.isLowPowerMode -> ONNXProvider.VULKAN
            systemInfo.gpuModel.contains("Adreno 6") || systemInfo.gpuModel.contains("Adreno 7") -> ONNXProvider.OPENCL
            else -> ONNXProvider.CPU
        }
    }
    
    /**
     * v2.4.0 推荐质量级别
     */
    private fun recommendQuality(
        cpuUsage: Float,
        memoryUsage: Float,
        temperature: Float
    ): QualityLevel {
        return when {
            memoryUsage > MEMORY_CRITICAL_THRESHOLD -> QualityLevel.ULTRA_LOW
            memoryUsage > MEMORY_WARNING_THRESHOLD -> QualityLevel.LOW
            cpuUsage > CPU_WARNING_THRESHOLD -> QualityLevel.LOW
            temperature > 45f -> QualityLevel.LOW
            _performanceState.value.isLowPowerMode -> QualityLevel.ULTRA_LOW
            else -> QualityLevel.BALANCED
        }
    }
    
    /**
     * v2.4.0 估算 GPU 使用率
     */
    private fun estimateGpuUsage(): Float {
        // 简化实现
        return if (systemInfo.hasGPU) {
            _performanceState.value.cpuUsage * 0.7f
        } else 0f
    }
    
    /**
     * v2.4.0 获取优化建议
     */
    fun getOptimizationSuggestions(): List<OptimizationSuggestion> {
        val suggestions = mutableListOf<OptimizationSuggestion>()
        val state = _performanceState.value
        
        if (state.memoryUsage > MEMORY_WARNING_THRESHOLD) {
            suggestions.add(
                OptimizationSuggestion(
                    type = SuggestionType.MEMORY,
                    priority = if (state.memoryUsage > MEMORY_CRITICAL_THRESHOLD) Priority.HIGH else Priority.MEDIUM,
                    message = "内存使用率较高 (${(state.memoryUsage * 100).toInt()}%)，建议降低分辨率或批次大小",
                    action = "建议切换到低显存模式"
                )
            )
        }
        
        if (state.thermalThrottling) {
            suggestions.add(
                OptimizationSuggestion(
                    type = SuggestionType.THERMAL,
                    priority = Priority.HIGH,
                    message = "设备温度过高 (${state.temperature}°C)，可能会导致性能下降",
                    action = "建议等待设备冷却或降低生成质量"
                )
            )
        }
        
        if (state.isLowPowerMode) {
            suggestions.add(
                OptimizationSuggestion(
                    type = SuggestionType.BATTERY,
                    priority = Priority.MEDIUM,
                    message = "低电量模式已开启，性能可能受限",
                    action = "建议充电后使用高质量模式"
                )
            )
        }
        
        if (state.cpuUsage > CPU_WARNING_THRESHOLD) {
            suggestions.add(
                OptimizationSuggestion(
                    type = SuggestionType.CPU,
                    priority = Priority.LOW,
                    message = "CPU 使用率较高 (${state.cpuUsage.toInt()}%)",
                    action = "建议关闭其他应用"
                )
            )
        }
        
        if (!systemInfo.hasNPU && !systemInfo.hasGPU) {
            suggestions.add(
                OptimizationSuggestion(
                    type = SuggestionType.HARDWARE,
                    priority = Priority.LOW,
                    message = "当前设备不支持硬件加速",
                    action = "建议使用 CPU 优化模式"
                )
            )
        }
        
        return suggestions
    }
    
    /**
     * v2.4.0 优化参数
     */
    fun optimizeParams(
        baseSteps: Int,
        baseWidth: Int,
        baseHeight: Int,
        batchSize: Int = 1
    ): OptimizedParams {
        val state = _performanceState.value
        val quality = state.recommendedQuality
        
        return OptimizedParams(
            steps = (baseSteps * quality.stepsMultiplier).toInt().coerceIn(5, 50),
            width = (baseWidth * quality.memoryMultiplier).toInt().coerceIn(256, 1024),
            height = (baseHeight * quality.memoryMultiplier).toInt().coerceIn(256, 1024),
            batchSize = if (state.memoryUsage > MEMORY_WARNING_THRESHOLD) 1 else batchSize,
            qualityLevel = quality,
            recommendedProvider = state.recommendedProvider
        )
    }
    
    data class OptimizedParams(
        val steps: Int,
        val width: Int,
        val height: Int,
        val batchSize: Int,
        val qualityLevel: QualityLevel,
        val recommendedProvider: ONNXProvider
    )
    
    /**
     * v2.4.0 优化建议
     */
    data class OptimizationSuggestion(
        val type: SuggestionType,
        val priority: Priority,
        val message: String,
        val action: String
    )
    
    enum class SuggestionType {
        MEMORY, THERMAL, BATTERY, CPU, HARDWARE
    }
    
    enum class Priority {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    /**
     * v2.4.0 获取系统信息摘要
     */
    fun getSystemInfoSummary(): String {
        return buildString {
            appendLine("📱 设备信息:")
            appendLine("  型号: ${systemInfo.deviceModel}")
            appendLine("  厂商: ${systemInfo.manufacturer}")
            appendLine("  SoC: ${systemInfo.socModel}")
            appendLine("  CPU: ${systemInfo.cpuCores} 核心")
            appendLine("  内存: ${systemInfo.totalRAM / (1024 * 1024 * 1024)} GB")
            appendLine()
            appendLine("🖥️ 硬件加速:")
            appendLine("  NPU: ${if (systemInfo.hasNPU) "✓ 支持" else "✗ 不支持"}")
            appendLine("  GPU: ${if (systemInfo.hasGPU) "✓ 支持 (${systemInfo.gpuModel})" else "✗ 不支持"}")
            appendLine()
            appendLine("📊 当前状态:")
            appendLine("  CPU: ${_performanceState.value.cpuUsage.toInt()}%")
            appendLine("  内存: ${_performanceState.value.memoryUsage.toInt()}%")
            appendLine("  温度: ${_performanceState.value.temperature}°C")
            appendLine("  电量: ${_performanceState.value.batteryLevel}%")
        }
    }
    
    /**
     * v2.4.0 释放资源
     */
    fun release() {
        stopMonitoring()
    }
}
