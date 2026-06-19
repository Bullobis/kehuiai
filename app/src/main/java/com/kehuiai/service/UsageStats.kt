@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST", "DEPRECATION", "USELESS_ELVIS")
package com.kehuiai.service

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

/**
 * 可绘AI v3.5.0 - 使用统计
 */
class UsageStats(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "usage_stats"
    }
    
    data class GenerationStats(
        val todayCount: Int = 0,
        val weekCount: Int = 0,
        val totalCount: Int = 0,
        val favoriteCount: Int = 0
    )
    
    data class ModelUsage(
        val modelId: String,
        val modelName: String,
        val useCount: Int,
        val percentage: Float
    )
    
    data class DailyStats(
        val date: String,
        val count: Int,
        val avgTime: Float
    )
    
    data class PerformanceReport(
        val avgGenerationTime: Float,
        val avgMemoryUsage: Float,
        val successRate: Float,
        val mostUsedModel: String,
        val peakHour: Int
    )
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val _generationStats = MutableStateFlow(GenerationStats())
    val generationStats: StateFlow<GenerationStats> = _generationStats.asStateFlow()
    
    private val _modelUsage = MutableStateFlow<List<ModelUsage>>(emptyList())
    val modelUsage: StateFlow<List<ModelUsage>> = _modelUsage.asStateFlow()
    
    init {
        refreshStats()
    }
    
    fun recordGeneration(modelId: String, success: Boolean, timeMs: Long) {
        val today = getTodayKey()
        
        // 更新总数
        val total = prefs.getInt("total_generations", 0) + 1
        prefs.edit().putInt("total_generations", total).apply()
        
        // 更新今日
        val todayCount = prefs.getInt("today_$today", 0) + 1
        prefs.edit().putInt("today_$today", todayCount).apply()
        
        // 更新本周
        val weekKey = getWeekKey()
        val weekCount = prefs.getInt("week_$weekKey", 0) + 1
        prefs.edit().putInt("week_$weekKey", weekCount).apply()
        
        // 更新模型使用
        val modelCount = prefs.getInt("model_$modelId", 0) + 1
        prefs.edit().putInt("model_$modelId", modelCount).apply()
        
        // 更新平均时间
        val totalTime = prefs.getLong("total_time", 0) + timeMs
        prefs.edit().putLong("total_time", totalTime).apply()
        
        // 更新成功率
        val successCount = prefs.getInt("success_count", 0) + if (success) 1 else 0
        prefs.edit().putInt("success_count", successCount).apply()
        
        refreshStats()
    }
    
    fun recordFavorite() {
        val count = prefs.getInt("favorites", 0) + 1
        prefs.edit().putInt("favorites", count).apply()
        refreshStats()
    }
    
    fun getPerformanceReport(): PerformanceReport {
        val total = prefs.getInt("total_generations", 1)
        val totalTime = prefs.getLong("total_time", 0)
        val successCount = prefs.getInt("success_count", 0)
        
        return PerformanceReport(
            avgGenerationTime = totalTime / total.toFloat(),
            avgMemoryUsage = 256f, // 模拟值
            successRate = successCount.toFloat() / total,
            mostUsedModel = getMostUsedModel(),
            peakHour = getPeakHour()
        )
    }
    
    fun getDailyStats(days: Int = 7): List<DailyStats> {
        val stats = mutableListOf<DailyStats>()
        val calendar = Calendar.getInstance()
        
        repeat(days) {
            val key = "daily_${calendar.get(Calendar.YEAR)}_${calendar.get(Calendar.DAY_OF_YEAR)}"
            val count = prefs.getInt(key, 0)
            stats.add(DailyStats(
                date = "${calendar.get(Calendar.MONTH) + 1}/${calendar.get(Calendar.DAY_OF_MONTH)}",
                count = count,
                avgTime = if (count > 0) 5000f else 0f
            ))
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        
        return stats.reversed()
    }
    
    private fun refreshStats() {
        val today = getTodayKey()
        val weekKey = getWeekKey()
        
        _generationStats.value = GenerationStats(
            todayCount = prefs.getInt("today_$today", 0),
            weekCount = prefs.getInt("week_$weekKey", 0),
            totalCount = prefs.getInt("total_generations", 0),
            favoriteCount = prefs.getInt("favorites", 0)
        )
        
        _modelUsage.value = getModelUsageList()
    }
    
    private fun getModelUsageList(): List<ModelUsage> {
        val models = listOf("sd15", "sdxl", "flux", "zimage")
        val total = models.sumOf { prefs.getInt("model_$it", 0).toLong() }.coerceAtLeast(1)
        
        val names = mapOf("sd15" to "SD 1.5", "sdxl" to "SDXL", "flux" to "Flux", "zimage" to "Z-Image")
        
        return models.map { id ->
            val count = prefs.getInt("model_$id", 0)
            ModelUsage(id, names[id] ?: id, count, count.toFloat() / total)
        }.sortedByDescending { it.useCount }
    }
    
    private fun getMostUsedModel(): String {
        return _modelUsage.value.firstOrNull()?.modelId ?: "sd15"
    }
    
    private fun getPeakHour(): Int {
        // 返回模拟峰值时段
        return 20 // 晚上8点
    }
    
    private fun getTodayKey(): String {
        val c = Calendar.getInstance()
        return "${c.get(Calendar.YEAR)}_${c.get(Calendar.DAY_OF_YEAR)}"
    }
    
    private fun getWeekKey(): String {
        val c = Calendar.getInstance()
        return "${c.get(Calendar.YEAR)}_W${c.get(Calendar.WEEK_OF_YEAR)}"
    }
    
    fun clearAllStats() {
        prefs.edit().clear().apply()
        refreshStats()
    }
    
    fun release() = scope.cancel()
}
