package com.kehuiai.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 可绘AI v3.6.6 使用统计服务
 * 追踪用户使用行为，帮助改进应用
 */
class UsageStatsService private constructor(context: Context) {
    
    companion object {
        private const val TAG = "UsageStats"
        private const val PREFS_NAME = "usage_stats"
        
        @Volatile
        private var INSTANCE: UsageStatsService? = null
        
        fun getInstance(context: Context): UsageStatsService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UsageStatsService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // 统计数据
    data class Stats(
        val totalGenerations: Int = 0,
        val totalImages: Int = 0,
        val totalVideos: Int = 0,
        val totalGenerationTime: Long = 0,
        val lastGenerationTime: Long = 0,
        val favoriteModel: String = "",
        val favoriteScheduler: String = "",
        val averageGenerationTime: Float = 0f,
        val mostUsedSize: String = "",
        val averageSteps: Int = 0,
        val startDate: Long = 0,
        val lastUsedDate: Long = 0,
        val consecutiveDays: Int = 0,
        val weeklyStats: Map<String, Int> = emptyMap()
    )
    
    private val _stats = MutableStateFlow(loadStats())
    val stats: StateFlow<Stats> = _stats.asStateFlow()
    
    // 今日统计
    private val _todayStats = MutableStateFlow(Stats())
    val todayStats: StateFlow<Stats> = _todayStats.asStateFlow()
    
    init {
        updateTodayStats()
    }
    
    /**
     * 记录一次图像生成
     */
    fun recordGeneration(
        generationTimeMs: Long,
        model: String,
        scheduler: String,
        width: Int,
        height: Int,
        steps: Int
    ) {
        val editor = prefs.edit()
        
        // 总生成次数
        val totalGens = prefs.getInt("total_generations", 0) + 1
        editor.putInt("total_generations", totalGens)
        
        // 总图片数
        val totalImages = prefs.getInt("total_images", 0) + 1
        editor.putInt("total_images", totalImages)
        
        // 总生成时间
        val totalTime = prefs.getLong("total_generation_time", 0) + generationTimeMs
        editor.putLong("total_generation_time", totalTime)
        
        // 最后一次生成时间
        editor.putLong("last_generation_time", System.currentTimeMillis())
        
        // 更新常用模型
        updateFavorite("favorite_model", model)
        
        // 更新常用调度器
        updateFavorite("favorite_scheduler", scheduler)
        
        // 更新常用尺寸
        val size = "${width}x${height}"
        updateFavorite("favorite_size", size)
        
        // 更新平均步数
        val currentAvgSteps = prefs.getInt("total_steps", 0)
        val newTotalSteps = currentAvgSteps + steps
        editor.putInt("total_steps", newTotalSteps)
        editor.putInt("average_steps", newTotalSteps / totalGens)
        
        // 更新连续使用天数
        updateConsecutiveDays()
        
        // 今日统计
        recordTodayGeneration()
        
        // 保存
        editor.apply()
        
        // 更新 StateFlow
        _stats.value = loadStats()
        
        Log.i(TAG, "记录生成: 总计 $totalGens 次, 本次耗时 ${generationTimeMs}ms")
    }
    
    /**
     * 记录一次视频生成
     */
    fun recordVideoGeneration(generationTimeMs: Long, model: String) {
        val editor = prefs.edit()
        
        val totalVideos = prefs.getInt("total_videos", 0) + 1
        editor.putInt("total_videos", totalVideos)
        
        val totalTime = prefs.getLong("total_generation_time", 0) + generationTimeMs
        editor.putLong("total_generation_time", totalTime)
        
        editor.putLong("last_generation_time", System.currentTimeMillis())
        
        updateConsecutiveDays()
        recordTodayGeneration()
        
        editor.apply()
        _stats.value = loadStats()
    }
    
    /**
     * 更新最喜欢的项
     */
    private fun updateFavorite(key: String, value: String) {
        val currentFav = prefs.getString("${key}_counts", "") ?: ""
        val countsMap: MutableMap<String, Int> = mutableMapOf()
        currentFav.split(";").filter { it.isNotEmpty() }.forEach { entry ->
            val parts = entry.split(":")
            if (parts.isNotEmpty()) {
                val k = parts[0]
                val v = parts.getOrNull(1)?.toIntOrNull() ?: 0
                countsMap[k] = v
            }
        }
        
        countsMap[value] = (countsMap[value] ?: 0) + 1
        
        val topItem = countsMap.entries.maxByOrNull { it.value }?.key ?: value
        prefs.edit().putString(key, topItem).apply()
        prefs.edit().putString("${key}_counts", countsMap.entries.joinToString(";") { e -> "${e.key}:${e.value}" }).apply()
    }
    
    /**
     * 更新连续使用天数
     */
    private fun updateConsecutiveDays() {
        val today = getDateString(System.currentTimeMillis())
        val lastUsed = prefs.getLong("last_used_date", 0)
        val lastUsedDate = getDateString(lastUsed)
        
        if (lastUsedDate == today) {
            // 今天已经使用过
            return
        }
        
        val yesterday = getDateString(System.currentTimeMillis() - 86400000)
        
        if (lastUsedDate == yesterday) {
            // 昨天也使用过，连续天数 +1
            val consecutive = prefs.getInt("consecutive_days", 0) + 1
            prefs.edit().putInt("consecutive_days", consecutive).apply()
        } else {
            // 重新开始计数
            prefs.edit().putInt("consecutive_days", 1).apply()
        }
        
        prefs.edit().putLong("last_used_date", System.currentTimeMillis()).apply()
        
        // 如果是首次使用，记录开始日期
        if (prefs.getLong("start_date", 0) == 0L) {
            prefs.edit().putLong("start_date", System.currentTimeMillis()).apply()
        }
    }
    
    /**
     * 记录今日统计
     */
    private fun recordTodayGeneration() {
        val today = getDateString(System.currentTimeMillis())
        val todayGens = prefs.getInt("today_generations_$today", 0) + 1
        prefs.edit().putInt("today_generations_$today", todayGens).apply()
        
        updateTodayStats()
    }
    
    /**
     * 更新今日统计
     */
    private fun updateTodayStats() {
        val today = getDateString(System.currentTimeMillis())
        val todayGens = prefs.getInt("today_generations_$today", 0)
        val todayTime = prefs.getLong("today_generation_time_$today", 0)
        
        _todayStats.value = Stats(
            totalGenerations = todayGens,
            totalGenerationTime = todayTime
        )
    }
    
    /**
     * 加载统计数据
     */
    private fun loadStats(): Stats {
        val totalGens = prefs.getInt("total_generations", 0)
        val totalTime = prefs.getLong("total_generation_time", 0)
        
        return Stats(
            totalGenerations = totalGens,
            totalImages = prefs.getInt("total_images", 0),
            totalVideos = prefs.getInt("total_videos", 0),
            totalGenerationTime = totalTime,
            lastGenerationTime = prefs.getLong("last_generation_time", 0),
            favoriteModel = prefs.getString("favorite_model", "未统计") ?: "未统计",
            favoriteScheduler = prefs.getString("favorite_scheduler", "未统计") ?: "未统计",
            averageGenerationTime = if (totalGens > 0) totalTime.toFloat() / totalGens else 0f,
            mostUsedSize = prefs.getString("favorite_size", "未统计") ?: "未统计",
            averageSteps = prefs.getInt("average_steps", 0),
            startDate = prefs.getLong("start_date", 0),
            lastUsedDate = prefs.getLong("last_used_date", 0),
            consecutiveDays = prefs.getInt("consecutive_days", 0)
        )
    }
    
    /**
     * 获取日期字符串
     */
    private fun getDateString(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
    }
    
    /**
     * 获取本周统计
     */
    fun getWeeklyStats(): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        
        for (i in 6 downTo 0) {
            val dayTimestamp = System.currentTimeMillis() - (i * 86400000)
            val day = dateFormat.format(Date(dayTimestamp))
            val dayOfWeek = SimpleDateFormat("EEE", Locale.getDefault()).format(Date(dayTimestamp))
            val gens = prefs.getInt("today_generations_$day", 0)
            result[dayOfWeek] = gens
        }
        
        return result
    }
    
    /**
     * 重置统计数据
     */
    fun resetStats() {
        prefs.edit().clear().apply()
        _stats.value = Stats()
        _todayStats.value = Stats()
    }
    
    /**
     * 获取成就列表
     */
    fun getAchievements(): List<Achievement> {
        val totalGens = prefs.getInt("total_generations", 0)
        val consecutiveDays = prefs.getInt("consecutive_days", 0)
        val totalTime = prefs.getLong("total_generation_time", 0)
        
        return listOf(
            Achievement(
                "🌱", "初出茅庐", "完成第一次生成", totalGens >= 1, totalGens.toString()
            ),
            Achievement(
                "🎨", "小试牛刀", "完成10次生成", totalGens >= 10, totalGens.toString()
            ),
            Achievement(
                "🔥", "渐入佳境", "完成50次生成", totalGens >= 50, totalGens.toString()
            ),
            Achievement(
                "⭐", "熟能生巧", "完成100次生成", totalGens >= 100, totalGens.toString()
            ),
            Achievement(
                "🏆", "大师级", "完成500次生成", totalGens >= 500, totalGens.toString()
            ),
            Achievement(
                "📅", "坚持不懈", "连续使用7天", consecutiveDays >= 7, "$consecutiveDays 天"
            ),
            Achievement(
                "💪", "持之以恒", "连续使用30天", consecutiveDays >= 30, "$consecutiveDays 天"
            ),
            Achievement(
                "⏱️", "时间见证", "累计使用10小时", totalTime >= 36000000, formatDuration(totalTime)
            ),
            Achievement(
                "⏰", "漫长旅程", "累计使用100小时", totalTime >= 360000000, formatDuration(totalTime)
            )
        )
    }
    
    /**
     * 格式化时长
     */
    private fun formatDuration(ms: Long): String {
        val hours = ms / 3600000
        val minutes = (ms % 3600000) / 60000
        return when {
            hours > 0 -> "${hours}小时${minutes}分钟"
            minutes > 0 -> "${minutes}分钟"
            else -> "< 1分钟"
        }
    }
    
    /**
     * 成就数据类
     */
    data class Achievement(
        val emoji: String,
        val name: String,
        val description: String,
        val unlocked: Boolean,
        val progress: String
    )
}
