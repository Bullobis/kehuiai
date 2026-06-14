package comkuaihuiai.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import comkuaihuiai.data.model.GenerationParams

/**
 * 可绘AI v3.6 - 历史风格学习引擎
 * 
 * 通过分析用户的生成历史，自动学习并推荐用户偏好风格。
 * 特性：
 * - 风格向量学习（正向反馈）
 * - 参数偏好分析
 * - 使用习惯追踪
 * - 智能风格推荐
 */
class StyleLearningManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "StyleLearningManager"
        
        private const val PREFS_NAME = "style_learning_prefs"
        private const val KEY_STYLE_VECTOR = "style_vector"
        private const val KEY_STYLE_HISTORY = "style_history"
        private const val KEY_PREFERENCES = "user_preferences"
        private const val KEY_GENERATION_COUNT = "generation_count"
        private const val KEY_LAST_STYLE = "last_style"
        private const val KEY_STYLE_VERSION = "style_version"
        
        // 风格维度数量
        private const val STYLE_DIMENSIONS = 12
        
        // 最大历史记录
        private const val MAX_HISTORY = 100
        
        // 学习率（0-1，越高越快适应新风格）
        private const val LEARNING_RATE = 0.15f
        
        // 冷启动阈值（新用户需要多少样本才能推荐）
        private const val COLD_START_THRESHOLD = 5
        
        @Volatile
        private var INSTANCE: StyleLearningManager? = null
        
        fun getInstance(context: Context): StyleLearningManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StyleLearningManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // 用户风格向量（12维度）
    private val _styleVector = MutableStateFlow(FloatArray(STYLE_DIMENSIONS) { 0.5f })
    val styleVector: StateFlow<FloatArray> = _styleVector.asStateFlow()
    
    // 统计信息
    private val _stats = MutableStateFlow(StyleLearningStats())
    val stats: StateFlow<StyleLearningStats> = _stats.asStateFlow()
    
    // 推荐的风格参数
    private val _recommendedParams = MutableStateFlow<RecommendedParams?>(null)
    val recommendedParams: StateFlow<RecommendedParams?> = _recommendedParams.asStateFlow()
    
    // 风格历史
    private val _styleHistory = MutableStateFlow<List<StyleRecord>>(emptyList())
    val styleHistory: StateFlow<List<StyleRecord>> = _styleHistory.asStateFlow()
    
    private lateinit var prefs: SharedPreferences
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    
    // 风格维度说明
    private val dimensionNames = arrayOf(
        "Realistic",      // 0: 真实感
        "Artistic",       // 1: 艺术性
        "Colorful",       // 2: 色彩丰富度
        "Dark",           // 3: 暗色系
        "Bright",         // 4: 明亮
        "Detailed",       // 5: 细节
        "Minimal",        // 6: 极简
        "Vibrant",        // 7: 鲜艳
        "Soft",           // 8: 柔和
        "Sharp",          // 9: 锐利
        "Portrait",       // 10: 人像
        "Landscape"       // 11: 风景
    )
    
    init {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadFromPrefs()
        updateRecommendations()
    }
    
    /**
     * 记录一次生成历史（带反馈）
     * @param params 生成参数
     * @param feedback 用户反馈: 1=喜欢, 0=一般, -1=不喜欢
     */
    fun recordGeneration(params: GenerationParams, feedback: Int = 1) {
        // 构建风格特征
        val features = extractFeatures(params)
        
        // 更新风格向量
        val current = _styleVector.value.copyOf()
        for (i in features.indices) {
            // 带反馈的学习更新
            val delta = (features[i] - current[i]) * LEARNING_RATE * feedback
            current[i] = (current[i] + delta).coerceIn(0f, 1f)
        }
        _styleVector.value = current
        
        // 记录历史
        val record = StyleRecord(
            timestamp = System.currentTimeMillis(),
            features = features,
            feedback = feedback,
            modelType = params.baseModel.name ?: "UNKNOWN",
            style = "default",
            seed = params.seed
        )
        
        val history = _styleHistory.value.toMutableList()
        history.add(0, record)
        if (history.size > MAX_HISTORY) {
            history.removeAt(history.lastIndex)
        }
        _styleHistory.value = history
        
        // 更新统计
        val count = prefs.getInt(KEY_GENERATION_COUNT, 0) + 1
        prefs.edit().putInt(KEY_GENERATION_COUNT, count).apply()
        
        _stats.value = _stats.value.copy(
            totalGenerations = count,
            averageFeedback = calculateAverageFeedback(),
            dominantStyle = findDominantStyle(),
            lastUpdated = System.currentTimeMillis()
        )
        
        // 保存
        saveToPrefs()
        updateRecommendations()
        
        Log.i(TAG, "📝 记录生成历史 #${count}, 反馈=$feedback")
    }
    
    /**
     * 从参数提取风格特征
     */
    private fun extractFeatures(params: GenerationParams): FloatArray {
        val features = FloatArray(STYLE_DIMENSIONS) { 0.5f }
        
        // 采样步数反映细节程度
        val steps = params.steps.coerceIn(1, 50)
        features[5] = (steps / 50f).coerceIn(0f, 1f) // Detailed
        
        // CFG 反映风格强度
        val cfg = params.guidanceScale.coerceIn(1f, 20f)
        features[1] = ((cfg - 1) / 19f).coerceIn(0f, 1f) // Artistic
        
        // 分辨率
        val resolution = (params.width * params.height).toFloat()
        val maxRes = 1024 * 1024
        features[6] = if (resolution < maxRes * 0.3f) 0.7f else if (resolution > maxRes) 0.3f else 0.5f // Minimal
        
        // 风格预设映射
        // 风格预设映射（基于提示词分析）
        val style = "" // 风格由提示词分析驱动
        when {
            style.contains("realistic") || style.contains("photo") -> features[0] = 0.9f
            style.contains("anime") || style.contains("cartoon") -> features[10] = 0.7f
            style.contains("artistic") || style.contains("painting") -> {
                features[1] = 0.9f; features[7] = 0.7f
            }
            style.contains("landscape") || style.contains("nature") -> features[11] = 0.9f
            style.contains("portrait") || style.contains("face") -> features[10] = 0.9f
            style.contains("dark") || style.contains("noir") -> features[3] = 0.9f
            style.contains("bright") || style.contains("vivid") -> {
                features[4] = 0.9f; features[7] = 0.8f
            }
            style.contains("soft") || style.contains("glow") -> features[8] = 0.9f
            style.contains("sharp") || style.contains("detail") -> features[9] = 0.9f
        }
        
        // 提示词分析
        val prompt = params.positivePrompt.lowercase()
        val keywords = mapOf(
            0 to listOf("photo", "photorealistic", "realistic", "真实", "摄影"),
            1 to listOf("art", "artistic", "painting", "artwork", "艺术"),
            2 to listOf("colorful", "vibrant", "rainbow", "color", "色彩"),
            3 to listOf("dark", "night", "shadow", "gloomy", "暗色"),
            4 to listOf("bright", "sunny", "light", "glow", "明亮"),
            5 to listOf("detailed", "intricate", "complex", "细节"),
            6 to listOf("minimal", "simple", "clean", "极简"),
            7 to listOf("vivid", "saturated", "bold", "鲜艳"),
            8 to listOf("soft", "gentle", "smooth", "柔和"),
            9 to listOf("sharp", "crisp", "clear", "锐利"),
            10 to listOf("portrait", "face", "person", "人像", "人"),
            11 to listOf("landscape", "nature", "mountain", "scenery", "风景", "自然")
        )
        
        keywords.forEach { (dim, words) ->
            words.forEach { word ->
                if (prompt.contains(word)) {
                    features[dim] = (features[dim] + 0.3f).coerceAtMost(1f)
                }
            }
        }
        
        // 正则词
        val negPrompt = params.negativePrompt?.lowercase() ?: ""
        if (negPrompt.contains("cartoon") || negPrompt.contains("anime")) {
            features[0] = (features[0] + 0.2f).coerceAtMost(1f) // 更真实
        }
        if (negPrompt.contains("blurry") || negPrompt.contains("low quality")) {
            features[5] = (features[5] + 0.2f).coerceAtMost(1f) // 更细节
        }
        
        return features
    }
    
    /**
     * 获取推荐参数
     */
    fun getRecommendedParams(): RecommendedParams {
        return _recommendedParams.value ?: RecommendedParams()
    }
    
    /**
     * 更新推荐
     */
    private fun updateRecommendations() {
        val vector = _styleVector.value
        
        // 冷启动：使用默认推荐
        if (_stats.value.totalGenerations < COLD_START_THRESHOLD) {
            _recommendedParams.value = getDefaultRecommendations()
            return
        }
        
        // 基于风格向量生成推荐
        val topDims = dimensionNames.indices
            .sortedByDescending { vector[it] }
            .take(3)
        
        val recommendedStyle = when {
            vector[0] > 0.7f -> "Realistic"
            vector[1] > 0.7f -> "Artistic"
            vector[10] > 0.7f -> "Portrait"
            vector[11] > 0.7f -> "Landscape"
            vector[7] > 0.7f -> "Cinematic"
            else -> "Balanced"
        }
        
        val recommendedSteps = when {
            vector[5] > 0.7f -> 35 // 细节
            vector[6] > 0.7f -> 20 // 简约
            else -> 28
        }
        
        val recommendedCfg = when {
            vector[1] > 0.7f -> 9.0f // 艺术
            vector[0] > 0.7f -> 7.0f // 真实
            else -> 7.5f
        }
        
        val recommendedResolution = when {
            vector[11] > 0.7f -> Pair(1024, 768) // 风景横版
            vector[10] > 0.7f -> Pair(768, 1024) // 人像竖版
            else -> Pair(1024, 1024)
        }
        
        val recommendedStylePresets = topDims.map { dimensionNames[it] }
        
        _recommendedParams.value = RecommendedParams(
            suggestedStyle = recommendedStyle,
            suggestedSteps = recommendedSteps,
            suggestedCfgScale = recommendedCfg,
            suggestedResolution = recommendedResolution,
            suggestedStylePresets = recommendedStylePresets,
            confidence = vector[topDims.firstOrNull() ?: 0],
            reason = buildRecommendationReason(vector, topDims)
        )
    }
    
    private fun getDefaultRecommendations(): RecommendedParams {
        return RecommendedParams(
            suggestedStyle = "Balanced",
            suggestedSteps = 28,
            suggestedCfgScale = 7.5f,
            suggestedResolution = Pair(1024, 1024),
            suggestedStylePresets = listOf("Realistic", "Artistic", "Cinematic"),
            confidence = 0f,
            reason = "积累更多生成历史后提供个性化推荐"
        )
    }
    
    private fun buildRecommendationReason(vector: FloatArray, topDims: List<Int>): String {
        if (topDims.isEmpty()) return "基于您的生成偏好推荐"
        val topDimNames = topDims.map { dimensionNames[it] }
        return "您偏好 ${topDimNames.joinToString(" / ")} 风格"
    }
    
    /**
     * 计算平均反馈
     */
    private fun calculateAverageFeedback(): Float {
        val history = _styleHistory.value
        if (history.isEmpty()) return 0f
        return history.map { it.feedback }.average().toFloat()
    }
    
    /**
     * 找出主导风格
     */
    private fun findDominantStyle(): String {
        val styleCounts = mutableMapOf<String, Int>()
        _styleHistory.value.forEach { record ->
            styleCounts[record.style] = (styleCounts[record.style] ?: 0) + 1
        }
        return styleCounts.maxByOrNull { it.value }?.key ?: "default"
    }
    
    /**
     * 获取相似历史
     */
    fun getSimilarStyles(params: GenerationParams, limit: Int = 5): List<StyleRecord> {
        val currentFeatures = extractFeatures(params)
        return _styleHistory.value
            .filter { it.feedback >= 0 } // 只考虑正面反馈
            .sortedByDescending { record ->
                cosineSimilarity(currentFeatures, record.features)
            }
            .take(limit)
    }
    
    /**
     * 余弦相似度
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        return if (normA > 0 && normB > 0) dot / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)) else 0f
    }
    
    /**
     * 重置学习数据
     */
    fun reset() {
        _styleVector.value = FloatArray(STYLE_DIMENSIONS) { 0.5f }
        _styleHistory.value = emptyList()
        _stats.value = StyleLearningStats()
        _recommendedParams.value = getDefaultRecommendations()
        prefs.edit().clear().apply()
        Log.i(TAG, "🔄 风格学习数据已重置")
    }
    
    /**
     * 导出风格配置
     */
    fun exportStyleProfile(): String {
        val json = JSONObject().apply {
            put("version", 1)
            put("timestamp", System.currentTimeMillis())
            put("vector", JSONArray(_styleVector.value.map { it.toDouble() }))
            put("stats", JSONObject().apply {
                put("totalGenerations", _stats.value.totalGenerations)
                put("averageFeedback", _stats.value.averageFeedback.toDouble())
                put("dominantStyle", _stats.value.dominantStyle)
            })
            put("history", JSONArray(_styleHistory.value.map { record ->
                JSONObject().apply {
                    put("timestamp", record.timestamp)
                    put("style", record.style)
                    put("modelType", record.modelType)
                    put("feedback", record.feedback)
                }
            }))
        }
        return json.toString(2)
    }
    
    /**
     * 导入风格配置
     */
    fun importStyleProfile(json: String): Boolean {
        return try {
            val obj = JSONObject(json)
            val vectorArray = obj.getJSONArray("vector")
            val vector = FloatArray(STYLE_DIMENSIONS) { i ->
                if (i < vectorArray.length()) vectorArray.getDouble(i).toFloat() else 0.5f
            }
            _styleVector.value = vector
            
            val historyArray = obj.getJSONArray("history")
            val history = mutableListOf<StyleRecord>()
            for (i in 0 until historyArray.length()) {
                val item = historyArray.getJSONObject(i)
                history.add(StyleRecord(
                    timestamp = item.getLong("timestamp"),
                    features = FloatArray(STYLE_DIMENSIONS),
                    feedback = item.optInt("feedback", 1),
                    modelType = item.optString("modelType", "UNKNOWN"),
                    style = item.optString("style", "default"),
                    seed = item.optLong("seed", -1)
                ))
            }
            _styleHistory.value = history
            
            prefs.edit().putString(KEY_STYLE_VERSION, "1").apply()
            updateRecommendations()
            Log.i(TAG, "✅ 风格配置导入成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ 风格配置导入失败", e)
            false
        }
    }
    
    /**
     * 持久化
     */
    private fun saveToPrefs() {
        val vectorJson = JSONArray(_styleVector.value.map { it.toDouble() }).toString()
        prefs.edit()
            .putString(KEY_STYLE_VECTOR, vectorJson)
            .putString(KEY_LAST_STYLE, _stats.value.dominantStyle)
            .putLong("last_updated", System.currentTimeMillis())
            .apply()
    }
    
    /**
     * 加载
     */
    private fun loadFromPrefs() {
        try {
            val vectorJson = prefs.getString(KEY_STYLE_VECTOR, null)
            if (vectorJson != null) {
                val array = JSONArray(vectorJson)
                val vector = FloatArray(STYLE_DIMENSIONS) { i ->
                    if (i < array.length()) array.getDouble(i).toFloat() else 0.5f
                }
                _styleVector.value = vector
            }
            
            val count = prefs.getInt(KEY_GENERATION_COUNT, 0)
            _stats.value = _stats.value.copy(
                totalGenerations = count,
                dominantStyle = prefs.getString(KEY_LAST_STYLE, "default") ?: "default",
                lastUpdated = prefs.getLong("last_updated", 0L)
            )
            
            Log.i(TAG, "📂 风格数据已加载: ${count} 条记录")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 加载失败", e)
        }
    }
    
    // ===== 数据类 =====
    
    data class StyleRecord(
        val timestamp: Long,
        val features: FloatArray,
        val feedback: Int,
        val modelType: String,
        val style: String,
        val seed: Long
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as StyleRecord
            return timestamp == other.timestamp
        }
        override fun hashCode() = timestamp.hashCode()
    }
    
    data class StyleLearningStats(
        val totalGenerations: Int = 0,
        val averageFeedback: Float = 0f,
        val dominantStyle: String = "default",
        val lastUpdated: Long = 0L
    )
    
    data class RecommendedParams(
        val suggestedStyle: String = "Balanced",
        val suggestedSteps: Int = 28,
        val suggestedCfgScale: Float = 7.5f,
        val suggestedResolution: Pair<Int, Int> = Pair(1024, 1024),
        val suggestedStylePresets: List<String> = emptyList(),
        val confidence: Float = 0f,
        val reason: String = ""
    )
}
