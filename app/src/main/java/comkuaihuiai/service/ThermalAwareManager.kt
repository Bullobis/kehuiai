package comkuaihuiai.service

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.RandomAccessFile

/**
 * 可绘AI v3.6 - 温度感知性能管理器
 *
 * 核心功能：
 * 1. 实时监控设备温度（CPU/GPU/电池/皮肤温度）
 * 2. 温度分级：冷态 → 正常 → 温热 → 过热 → 危险
 * 3. 根据温度动态调整：GPU频率、NPU启用、批处理大小、精度
 * 4. 温度趋势预测（防止突然过热降频导致卡顿）
 * 5. 智能预冷策略（提前降频，避免过热）
 */
class ThermalAwareManager(private val context: Context) {

    companion object {
        private const val TAG = "ThermalAware"

        // 温度阈值（摄氏度）
        private const val TEMP_COLD = 30      // 冷态：可全力输出
        private const val TEMP_NORMAL = 40    // 正常：标准性能
        private const val TEMP_WARM = 45      // 温热：降低批处理
        private const val TEMP_HOT = 50       // 过热：降频 + 关闭NPU
        private const val TEMP_DANGER = 55    // 危险：最小化输出

        // 温度读取间隔
        private const val READ_INTERVAL_MS = 2000L

        // 采样窗口（用于趋势计算）
        private const val TREND_WINDOW_SIZE = 5

        @Volatile
        private var INSTANCE: ThermalAwareManager? = null

        fun getInstance(context: Context): ThermalAwareManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThermalAwareManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // ===== 温度等级 =====
    enum class ThermalLevel {
        UNKNOWN,    // 未知
        COLD,       // 冷态（< 30°C）
        NORMAL,     // 正常（30-40°C）
        WARM,       // 温热（40-45°C）
        HOT,        // 过热（45-50°C）
        DANGER,     // 危险（> 50°C）
        CRITICAL    // 临界（> 55°C）
    }

    // ===== 性能档位 =====
    enum class PerformanceTier {
        MAX,        // 最大性能（冷态）
        HIGH,       // 高性能（正常）
        BALANCED,   // 均衡（温热）
        ECO,        // 节能（过热）
        MINIMAL,    // 最小化（危险）
        EMERGENCY   // 紧急（临界）
    }

    // ===== 温度数据 =====
    data class ThermalData(
        val cpuTemp: Float = 0f,
        val gpuTemp: Float = 0f,
        val batteryTemp: Float = 0f,
        val skinTemp: Float = 0f,
        val overallTemp: Float = 0f,
        val level: ThermalLevel = ThermalLevel.UNKNOWN,
        val trend: Float = 0f,  // 温度变化趋势（正=上升）
        val timestamp: Long = System.currentTimeMillis()
    )

    // ===== 性能配置 =====
    data class PerformanceConfig(
        val tier: PerformanceTier = PerformanceTier.HIGH,
        val gpuFrequency: Int = 100,        // 百分比 0-100
        val npuEnabled: Boolean = true,
        val maxBatchSize: Int = 4,
        val precision: PrecisionMode = PrecisionMode.FP16,
        val thermalThrottle: Float = 0f,     // 节流比例 0-1
        val memoryLimit: MemoryLevel = MemoryLevel.MEDIUM
    )

    enum class PrecisionMode {
        FP32,   // 全精度
        FP16,   // 半精度
        INT8,   // 整型量化
        INT4    // 4位量化（最低精度）
    }

    enum class MemoryLevel {
        LOW,        // 严重限制
        MEDIUM,     // 中等限制
        HIGH,       // 高限制
        FULL        // 无限制
    }

    // ===== 状态流 =====
    private val _thermalData = MutableStateFlow(ThermalData())
    val thermalData: StateFlow<ThermalData> = _thermalData.asStateFlow()

    private val _performanceConfig = MutableStateFlow(PerformanceConfig())
    val performanceConfig: StateFlow<PerformanceConfig> = _performanceConfig.asStateFlow()

    private val _thermalHistory = MutableStateFlow<List<ThermalData>>(emptyList())
    val thermalHistory: StateFlow<List<ThermalData>> = _thermalHistory.asStateFlow()

    // 温度采样历史（用于趋势计算）
    private val temperatureHistory = ArrayDeque<Float>(TREND_WINDOW_SIZE)

    // 当前性能档位
    private var currentTier: PerformanceTier = PerformanceTier.HIGH
    private var lastConfigUpdate: Long = 0

    // ===== 主要接口 =====

    /**
     * 获取当前推荐的性能配置
     * 每次调用会根据最新温度数据更新
     */
    fun getRecommendedConfig(): PerformanceConfig {
        val data = _thermalData.value
        return when (data.level) {
            ThermalLevel.COLD -> PerformanceConfig(
                tier = PerformanceTier.MAX,
                gpuFrequency = 100,
                npuEnabled = true,
                maxBatchSize = 8,
                precision = PrecisionMode.FP16,
                thermalThrottle = 0f,
                memoryLimit = MemoryLevel.FULL
            )
            ThermalLevel.NORMAL -> PerformanceConfig(
                tier = PerformanceTier.HIGH,
                gpuFrequency = 85,
                npuEnabled = true,
                maxBatchSize = 4,
                precision = PrecisionMode.FP16,
                thermalThrottle = 0f,
                memoryLimit = MemoryLevel.HIGH
            )
            ThermalLevel.WARM -> PerformanceConfig(
                tier = PerformanceTier.BALANCED,
                gpuFrequency = 65,
                npuEnabled = true,
                maxBatchSize = 2,
                precision = PrecisionMode.FP16,
                thermalThrottle = 0.1f,
                memoryLimit = MemoryLevel.MEDIUM
            )
            ThermalLevel.HOT -> PerformanceConfig(
                tier = PerformanceTier.ECO,
                gpuFrequency = 40,
                npuEnabled = false,
                maxBatchSize = 1,
                precision = PrecisionMode.INT8,
                thermalThrottle = 0.3f,
                memoryLimit = MemoryLevel.LOW
            )
            ThermalLevel.DANGER -> PerformanceConfig(
                tier = PerformanceTier.MINIMAL,
                gpuFrequency = 20,
                npuEnabled = false,
                maxBatchSize = 1,
                precision = PrecisionMode.INT8,
                thermalThrottle = 0.6f,
                memoryLimit = MemoryLevel.LOW
            )
            ThermalLevel.CRITICAL -> PerformanceConfig(
                tier = PerformanceTier.EMERGENCY,
                gpuFrequency = 10,
                npuEnabled = false,
                maxBatchSize = 1,
                precision = PrecisionMode.INT4,
                thermalThrottle = 0.9f,
                memoryLimit = MemoryLevel.LOW
            )
            ThermalLevel.UNKNOWN -> _performanceConfig.value
        }
    }

    /**
     * 获取推荐分辨率（根据当前温度档位）
     */
    fun getRecommendedResolution(hasHighResolutionModel: Boolean = false): Pair<Int, Int> {
        val tier = _performanceConfig.value.tier
        return when (tier) {
            PerformanceTier.MAX -> if (hasHighResolutionModel) 1024 to 1024 else 896 to 896
            PerformanceTier.HIGH -> if (hasHighResolutionModel) 896 to 896 else 768 to 768
            PerformanceTier.BALANCED -> if (hasHighResolutionModel) 768 to 768 else 512 to 768
            PerformanceTier.ECO -> if (hasHighResolutionModel) 512 to 768 else 512 to 512
            PerformanceTier.MINIMAL, PerformanceTier.EMERGENCY -> 512 to 512
        }
    }

    /**
     * 获取推荐步数（温度越高，步数越少）
     */
    fun getRecommendedSteps(baseSteps: Int): Int {
        val throttle = _performanceConfig.value.thermalThrottle
        return when {
            throttle <= 0f -> baseSteps
            throttle < 0.3f -> (baseSteps * 0.8f).toInt()
            throttle < 0.6f -> (baseSteps * 0.6f).toInt()
            else -> (baseSteps * 0.4f).toInt()
        }.coerceAtLeast(10)
    }

    /**
     * 是否应该启用高清修复（高分娃）
     */
    fun shouldEnableHiresFix(): Boolean {
        val tier = _performanceConfig.value.tier
        return tier == PerformanceTier.MAX || tier == PerformanceTier.HIGH
    }

    /**
     * 获取推荐CFG值
     */
    fun getRecommendedCFG(baseCFG: Float): Float {
        val throttle = _performanceConfig.value.thermalThrottle
        return when {
            throttle <= 0f -> baseCFG
            throttle < 0.3f -> baseCFG
            throttle < 0.6f -> (baseCFG * 0.85f).coerceAtLeast(5f)
            else -> (baseCFG * 0.7f).coerceAtLeast(5f)
        }
    }

    /**
     * 获取温度友好度评分（0-100）
     */
    fun getThermalFriendlinessScore(): Int {
        val temp = _thermalData.value.overallTemp
        return when {
            temp <= 0 -> 50  // 未知
            temp < TEMP_NORMAL -> 100
            temp < TEMP_WARM -> 80
            temp < TEMP_HOT -> 50
            temp < TEMP_DANGER -> 25
            else -> 0
        }
    }

    /**
     * 获取设备健康状态描述
     */
    fun getHealthStatus(): String {
        val data = _thermalData.value
        val score = getThermalFriendlinessScore()
        return when {
            data.level == ThermalLevel.UNKNOWN -> "设备温度：检测中..."
            score >= 90 -> "🟢 设备状态优秀 · ${data.overallTemp.toInt()}°C · ${currentTier.name}模式"
            score >= 60 -> "🟡 设备状态良好 · ${data.overallTemp.toInt()}°C · ${currentTier.name}模式"
            score >= 30 -> "🟠 设备有点热 · ${data.overallTemp.toInt()}°C · ${currentTier.name}模式（已降频）"
            score >= 10 -> "🔴 设备过热 · ${data.overallTemp.toInt()}°C · ${currentTier.name}模式（强烈建议暂停）"
            else -> "⚠️ 设备危险 · ${data.overallTemp.toInt()}°C · 紧急模式（已强制降频）"
        }
    }

    // ===== 内部方法 =====

    /**
     * 读取当前温度数据
     * 支持多种温度源：sysfs、battery、thermal framework
     */
    fun readTemperature(): ThermalData {
        val cpuTemp = readCpuTemp()
        val gpuTemp = readGpuTemp()
        val batteryTemp = readBatteryTemp()
        val skinTemp = readSkinTemp()

        // 综合温度计算（加权平均）
        val overallTemp = calculateOverallTemp(cpuTemp, gpuTemp, batteryTemp, skinTemp)

        // 计算趋势
        val trend = calculateTrend(overallTemp)

        // 确定温度等级
        val level = determineLevel(overallTemp)

        // 更新历史
        updateHistory(overallTemp)

        val data = ThermalData(
            cpuTemp = cpuTemp,
            gpuTemp = gpuTemp,
            batteryTemp = batteryTemp,
            skinTemp = skinTemp,
            overallTemp = overallTemp,
            level = level,
            trend = trend,
            timestamp = System.currentTimeMillis()
        )

        _thermalData.value = data

        // 更新性能配置
        val config = getRecommendedConfig()
        if (config.tier != currentTier) {
            currentTier = config.tier
            _performanceConfig.value = config
            Log.w(TAG, "温度档位切换: ${config.tier.name} (${overallTemp.toInt()}°C)")
        }

        return data
    }

    /**
     * 读取 CPU 温度
     * 尝试多种 sysfs 路径
     */
    private fun readCpuTemp(): Float {
        val paths = listOf(
            // Qualcomm Snapdragon
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/devices/virtual/thermal/thermal_zone0/temp",
            // MediaTek
            "/sys/class/thermal/thermal_zone1/temp",
            // 通用
            "/sys/class/hwmon/hwmon0/temp1_input",
            "/sys/class/hwmon/hwmon1/temp1_input",
            // CPU 核心温度
            "/sys/devices/system/cpu/cpu0/cpufreq/cpu_temp",
            // Battery temperature
            "/sys/class/power_supply/battery/temp",
            // 设备树
            "/sys/devices/virtual/thermal/thermal_zone18/temp"
        )

        for (path in paths) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val content = file.readText().trim()
                    val temp = content.toFloatOrNull() ?: continue
                    // 有些返回毫摄氏度
                    return if (temp > 1000) temp / 1000 else temp
                }
            } catch (e: Exception) {
                continue
            }
        }

        // 备用：通过 battery temp 估算
        return readBatteryTemp()
    }

    /**
     * 读取 GPU 温度
     */
    private fun readGpuTemp(): Float {
        val paths = listOf(
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/class/thermal/thermal_zone5/temp",
            "/sys/devices/virtual/thermal/thermal_zone2/temp",
            "/sys/class/kgsl/kgsl-3d0/temp",
            "/sys/class/hwmon/hwmon2/temp1_input"
        )

        for (path in paths) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val content = file.readText().trim()
                    val temp = content.toFloatOrNull() ?: continue
                    return if (temp > 1000) temp / 1000 else temp
                }
            } catch (e: Exception) {
                continue
            }
        }

        // 如果无法读取 GPU 温度，返回 CPU 温度作为参考
        return readCpuTemp() + 3f
    }

    /**
     * 读取电池温度
     */
    private fun readBatteryTemp(): Float {
        val paths = listOf(
            "/sys/class/power_supply/battery/batt_temp",
            "/sys/class/power_supply/battery/temp",
            "/sys/class/power_supply/battery/battery_temp",
            "/sys/class/power_supply/Battery/temp"
        )

        for (path in paths) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val content = file.readText().trim()
                    val temp = content.toFloatOrNull() ?: continue
                    return if (temp > 100) temp / 10 else temp
                }
            } catch (e: Exception) {
                continue
            }
        }

        return 0f
    }

    /**
     * 读取皮肤温度（设备外壳）
     */
    private fun readSkinTemp(): Float {
        val paths = listOf(
            "/sys/class/thermal/thermal_zone8/temp",
            "/sys/class/thermal/thermal_zone10/temp",
            "/sys/class/thermal/thermal_zone12/temp"
        )

        for (path in paths) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val content = file.readText().trim()
                    val temp = content.toFloatOrNull() ?: continue
                    return if (temp > 1000) temp / 1000 else temp
                }
            } catch (e: Exception) {
                continue
            }
        }

        return 0f
    }

    /**
     * 计算综合温度（加权平均）
     */
    private fun calculateOverallTemp(
        cpu: Float,
        gpu: Float,
        battery: Float,
        skin: Float
    ): Float {
        var sum = 0f
        var count = 0f

        if (cpu > 0) { sum += cpu * 0.4f; count += 0.4f }
        if (gpu > 0) { sum += gpu * 0.35f; count += 0.35f }
        if (battery > 0) { sum += battery * 0.15f; count += 0.15f }
        if (skin > 0) { sum += skin * 0.1f; count += 0.1f }

        return if (count > 0) sum / count else cpu
    }

    /**
     * 计算温度变化趋势
     */
    private fun calculateTrend(currentTemp: Float): Float {
        temperatureHistory.addLast(currentTemp)
        if (temperatureHistory.size > TREND_WINDOW_SIZE) {
            temperatureHistory.removeFirst()
        }

        if (temperatureHistory.size < 2) return 0f

        // 简单线性回归斜率
        val n = temperatureHistory.size
        var sumX = 0f
        var sumY = 0f
        var sumXY = 0f
        var sumXX = 0f

        temperatureHistory.forEachIndexed { index, temp ->
            val x = index.toFloat()
            sumX += x
            sumY += temp
            sumXY += x * temp
            sumXX += x * x
        }

        val denominator = n * sumXX - sumX * sumX
        if (denominator == 0f) return 0f

        return (n * sumXY - sumX * sumY) / denominator
    }

    /**
     * 确定温度等级
     */
    private fun determineLevel(temp: Float): ThermalLevel {
        return when {
            temp <= 0 -> ThermalLevel.UNKNOWN
            temp < TEMP_COLD -> ThermalLevel.COLD
            temp < TEMP_NORMAL -> ThermalLevel.NORMAL
            temp < TEMP_WARM -> ThermalLevel.WARM
            temp < TEMP_HOT -> ThermalLevel.HOT
            temp < TEMP_DANGER -> ThermalLevel.DANGER
            else -> ThermalLevel.CRITICAL
        }
    }

    /**
     * 更新温度历史
     */
    private fun updateHistory(temp: Float) {
        val current = _thermalHistory.value.toMutableList()
        current.add(ThermalData(overallTemp = temp, level = determineLevel(temp)))
        // 保留最近 60 条记录
        if (current.size > 60) {
            current.removeAt(0)
        }
        _thermalHistory.value = current
    }

    /**
     * 获取温度历史摘要
     */
    fun getTemperatureSummary(): String {
        val history = _thermalHistory.value
        if (history.isEmpty()) return "暂无温度数据"

        val temps = history.map { it.overallTemp }.filter { it > 0 }
        if (temps.isEmpty()) return "温度传感器不可用"

        val avg = temps.average()
        val max = temps.maxOrNull() ?: 0f
        val min = temps.minOrNull() ?: 0f
        val trend = history.lastOrNull()?.trend ?: 0f

        val trendStr = when {
            trend > 0.5f -> "↑ 上升中"
            trend < -0.5f -> "↓ 下降中"
            else -> "→ 稳定"
        }

        return "平均 ${avg.toInt()}°C | 最高 ${max.toInt()}°C | 最低 ${min.toInt()}°C | $trendStr"
    }

    /**
     * 预测未来温度（基于趋势）
     */
    fun predictTemperature(secondsAhead: Int = 30): Float {
        val current = _thermalData.value.overallTemp
        val trend = _thermalData.value.trend
        return (current + trend * (secondsAhead / 2f)).coerceIn(0f, 80f)
    }

    /**
     * 是否应该触发预冷
     */
    fun shouldTriggerPreCool(): Boolean {
        val trend = _thermalData.value.trend
        val temp = _thermalData.value.overallTemp
        return trend > 1.0f && temp > TEMP_WARM - 3
    }

    /**
     * 获取设备信息
     */
    fun getDeviceThermalInfo(): Map<String, Any> {
        return mapOf(
            "device" to Build.DEVICE,
            "model" to Build.MODEL,
            "hardware" to Build.HARDWARE,
            "supported_thermal_zones" to countThermalZones(),
            "has_gpu_thermal" to hasGpuThermal(),
            "has_battery_thermal" to hasBatteryThermal()
        )
    }

    private fun countThermalZones(): Int {
        val thermalDir = File("/sys/class/thermal")
        return if (thermalDir.exists()) {
            thermalDir.listFiles()?.count { it.name.startsWith("thermal_zone") } ?: 0
        } else 0
    }

    private fun hasGpuThermal(): Boolean {
        return File("/sys/class/kgsl/kgsl-3d0/temp").exists() ||
               File("/sys/devices/virtual/thermal/thermal_zone2/temp").exists()
    }

    private fun hasBatteryThermal(): Boolean {
        return File("/sys/class/power_supply/battery/temp").exists()
    }

    /**
     * 释放资源
     */
    fun release() {
        temperatureHistory.clear()
        _thermalHistory.value = emptyList()
        INSTANCE = null
    }
}
