/**
 * SmartSeeder.kt
 * 智能种子推荐引擎
 * 
 * 功能：
 * - 基于历史成功记录推荐种子
 * - 相似提示词种子迁移
 * - 风格一致性种子保持
 * - 变体探索 (确定性 + 随机性平衡)
 * - 种子哈希分析
 */
package com.kehui.ai.service

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*
import kotlin.random.Random
import comkuaihuiai.data.model.GenerationParams

/**
 * 种子推荐结果
 */
data class SeedRecommendation(
    val seed: Long,
    val confidence: Float,             // 置信度 0-1
    val reason: RecommendationReason,
    val similarPastSeeds: List<Long>,   // 相似的历史种子
    val expectedVariance: Float,        // 预期变化程度
    val styleConsistency: Float,        // 风格一致性
    val exploration: Boolean             // 是否为探索性推荐
)

/**
 * 推荐原因
 */
enum class RecommendationReason {
    HISTORICAL_SUCCESS,    // 基于历史成功
    STYLE_MATCH,           // 风格匹配
    PARAMETER_SIMILARITY,  // 参数相似
    VARIATION_EXPLORATION, // 变化探索
    DIVERSITY_BOOST,       // 多样性增强
    STABILITY,             // 稳定性优先
    EXPERIMENTAL           // 实验性
}

/**
 * 历史生成记录
 */
data class GenerationRecord(
    val seed: Long,
    val prompt: String,
    val normalizedPrompt: String,
    val parameters: GenerationParams,
    val qualityScore: Float,           // 用户评分或质量分析分数
    val tags: List<String>,
    val timestamp: Long,
    val imageHash: String? = null,
    val isFavorite: Boolean = false
)

/**
 * 提示词嵌入 (简化的词袋模型)
 */
data class PromptEmbedding(
    val promptId: String,
    val vector: FloatArray,
    val hash: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PromptEmbedding) return false
        return promptId == other.promptId
    }
    
    override fun hashCode(): Int = promptId.hashCode()
}

/**
 * 种子聚类
 */
data class SeedCluster(
    val id: Int,
    val centerSeed: Long,
    val members: List<Long>,
    val commonStyles: Set<String>,
    val averageQuality: Float,
    val size: Int
)

/**
 * 智能种子推荐引擎
 */
class SmartSeeder(private val context: Context) {
    
    companion object {
        private const val TAG = "SmartSeeder"
        private const val PREFS_NAME = "smart_seeder_prefs"
        private const val KEY_SEED_COUNT = "seed_count"
        private const val KEY_LAST_SEED = "last_seed"
        
        // 推荐参数
        private const val TOP_K_SIMILAR = 10
        private const val MIN_HISTORY_FOR_RECOMMEND = 5
        private const val MAX_CLUSTER_SIZE = 100
        
        // 相似度阈值
        private const val SIMILARITY_THRESHOLD = 0.65f
        private const val HIGH_CONFIDENCE = 0.80f
        
        // 探索参数
        private const val EXPLORATION_RATE = 0.15f
        private const val VARIATION_STRENGTH = 0.1f
        private const val EMBEDDING_DIM = 32
    }
    
    // ===== 状态 =====
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _history = MutableStateFlow<List<GenerationRecord>>(loadHistory())
    val history: StateFlow<List<GenerationRecord>> = _history.asStateFlow()
    
    private val _embeddings = MutableStateFlow<Map<String, PromptEmbedding>>(emptyMap())
    val embeddings: StateFlow<Map<String, PromptEmbedding>> = _embeddings.asStateFlow()
    
    private val _clusters = MutableStateFlow<List<SeedCluster>>(emptyList())
    val clusters: StateFlow<List<SeedCluster>> = _clusters.asStateFlow()
    
    // 种子推荐缓存
    private val _recommendations = MutableStateFlow<Map<String, List<SeedRecommendation>>>(emptyMap())
    val recommendations: StateFlow<Map<String, List<SeedRecommendation>>> = _recommendations.asStateFlow()
    
    // 统计
    private var totalSeedsGenerated = prefs.getInt(KEY_SEED_COUNT, 0)
    private var lastUsedSeed = prefs.getLong(KEY_LAST_SEED, -1L)
    
    // ===== 主要接口 =====
    
    /**
     * 记录一次生成
     */
    fun recordGeneration(record: GenerationRecord) {
        val currentHistory = _history.value.toMutableList()
        
        // 更新或添加
        val existingIdx = currentHistory.indexOfFirst { 
            it.seed == record.seed && it.prompt == record.prompt 
        }
        
        if (existingIdx >= 0) {
            currentHistory[existingIdx] = record
        } else {
            currentHistory.add(record)
        }
        
        // 保持历史在合理范围
        val trimmedHistory = if (currentHistory.size > 1000) {
            currentHistory.sortedByDescending { it.qualityScore }.take(500)
        } else {
            currentHistory
        }
        
        _history.value = trimmedHistory
        saveHistory(trimmedHistory)
        
        // 更新嵌入
        updateEmbedding(record)
        
        // 更新聚类
        if (trimmedHistory.size >= MIN_HISTORY_FOR_RECOMMEND) {
            recomputeClusters()
        }
        
        totalSeedsGenerated++
        lastUsedSeed = record.seed
        saveStats()
        
        Log.d(TAG, "记录生成: seed=${record.seed}, score=${record.qualityScore}, history_size=${trimmedHistory.size}")
    }
    
    /**
     * 推荐种子
     */
    fun recommend(
        prompt: String,
        parameters: GenerationParams,
        mode: RecommendationMode = RecommendationMode.BALANCED,
        count: Int = 5
    ): List<SeedRecommendation> {
        val normalizedPrompt = normalizePrompt(prompt)
        val historyRecords = _history.value
        
        if (historyRecords.size < MIN_HISTORY_FOR_RECOMMEND) {
            // 历史不足，返回随机种子
            return generateFallbackRecommendations(count, mode)
        }
        
        val recommendations = mutableListOf<SeedRecommendation>()
        
        when (mode) {
            RecommendationMode.BALANCED -> {
                // 混合策略: 40% 历史最佳 + 30% 相似推荐 + 30% 探索
                val historical = recommendFromHistory(historyRecords, count * 2 / 5)
                val similar = recommendFromSimilar(prompt, normalizedPrompt, parameters, count * 3 / 10)
                val exploration = generateExplorationSeeds(count * 3 / 10, normalizedPrompt)
                recommendations.addAll(historical)
                recommendations.addAll(similar)
                recommendations.addAll(exploration)
            }
            RecommendationMode.QUALITY_FOCUS -> {
                // 质量优先: 70% 历史最佳 + 20% 相似 + 10% 探索
                val historical = recommendFromHistory(historyRecords, count * 7 / 10)
                val similar = recommendFromSimilar(prompt, normalizedPrompt, parameters, count * 2 / 10)
                val exploration = generateExplorationSeeds(count * 1 / 10, normalizedPrompt)
                recommendations.addAll(historical)
                recommendations.addAll(similar)
                recommendations.addAll(exploration)
            }
            RecommendationMode.DIVERSITY -> {
                // 多样性优先: 20% 历史 + 30% 相似 + 50% 探索
                val historical = recommendFromHistory(historyRecords, count * 2 / 10)
                val similar = recommendFromSimilar(prompt, normalizedPrompt, parameters, count * 3 / 10)
                val exploration = generateExplorationSeeds(count * 5 / 10, normalizedPrompt)
                recommendations.addAll(historical)
                recommendations.addAll(similar)
                recommendations.addAll(exploration)
            }
            RecommendationMode.STYLE_LOCK -> {
                // 风格锁定: 60% 同风格 + 30% 相似 + 10% 变化
                val styleLock = recommendWithStyleLock(normalizedPrompt, parameters, count * 6 / 10)
                val similar = recommendFromSimilar(prompt, normalizedPrompt, parameters, count * 3 / 10)
                val variation = generateVariations(lastUsedSeed, count * 1 / 10)
                recommendations.addAll(styleLock)
                recommendations.addAll(similar)
                recommendations.addAll(variation)
            }
            RecommendationMode.EXPLORATION -> {
                // 完全探索
                recommendations.addAll(generateExplorationSeeds(count, normalizedPrompt))
            }
        }
        
        // 去除重复并排序
        val uniqueRecs = recommendations
            .distinctBy { it.seed }
            .sortedByDescending { it.confidence }
            .take(count)
        
        // 缓存推荐
        val promptHash = normalizedPrompt.hashCode().toString()
        val cached = _recommendations.value.toMutableMap()
        cached[promptHash] = uniqueRecs
        _recommendations.value = cached
        
        return uniqueRecs
    }
    
    /**
     * 基于提示词相似度推荐
     */
    private fun recommendFromSimilar(
        prompt: String,
        normalizedPrompt: String,
        parameters: GenerationParams,
        count: Int
    ): List<SeedRecommendation> {
        val currentEmbedding = computeEmbedding(normalizedPrompt)
        val allEmbeddings = _embeddings.value
        
        // 计算余弦相似度
        val similarities = mutableListOf<Pair<GenerationRecord, Float>>()
        
        for (record in _history.value) {
            val embedding = allEmbeddings[record.prompt]
                ?: computeEmbedding(record.normalizedPrompt)
            
            val sim = cosineSimilarity(currentEmbedding.vector, embedding.vector)
            if (sim >= SIMILARITY_THRESHOLD) {
                similarities.add(record to sim)
            }
        }
        
        // 按相似度排序，取 top-K
        val topSimilar = similarities
            .sortedByDescending { it.second }
            .take(TOP_K_SIMILAR)
        
        if (topSimilar.isEmpty()) return emptyList()
        
        // 从相似记录中选择种子
        val seedScores = mutableMapOf<Long, Float>()
        val seedCounts = mutableMapOf<Long, Int>()
        
        for ((record, sim) in topSimilar) {
            val baseScore = record.qualityScore
            val adjustedScore = baseScore * sim * (if (record.isFavorite) 1.2f else 1f)
            seedScores[record.seed] = (seedScores[record.seed] ?: 0f) + adjustedScore
            seedCounts[record.seed] = (seedCounts[record.seed] ?: 0) + 1
        }
        
        // 平均分
        val avgScores = seedScores.mapValues { (seed, total) ->
            total / (seedCounts[seed] ?: 1)
        }
        
        return avgScores.entries
            .sortedByDescending { it.value }
            .take(count)
            .map { (seed, score) ->
                val record = _history.value.find { it.seed == seed }!!
                val similarPast = topSimilar
                    .filter { it.first.seed == seed }
                    .map { it.first.seed }
                    .distinct()
                    .take(5)
                
                SeedRecommendation(
                    seed = seed,
                    confidence = score,
                    reason = RecommendationReason.PARAMETER_SIMILARITY,
                    similarPastSeeds = similarPast,
                    expectedVariance = computeExpectedVariance(seed, normalizedPrompt),
                    styleConsistency = computeStyleConsistency(seed, normalizedPrompt),
                    exploration = false
                )
            }
    }
    
    /**
     * 从历史中选择最佳
     */
    private fun recommendFromHistory(
        historyRecords: List<GenerationRecord>,
        count: Int
    ): List<SeedRecommendation> {
        if (historyRecords.isEmpty()) return emptyList()
        
        val topRecords = historyRecords
            .sortedByDescending { it.qualityScore }
            .take(count * 2)
        
        val recommendations = mutableListOf<SeedRecommendation>()
        
        for (record in topRecords.take(count)) {
            recommendations.add(SeedRecommendation(
                seed = record.seed,
                confidence = record.qualityScore,
                reason = RecommendationReason.HISTORICAL_SUCCESS,
                similarPastSeeds = emptyList(),
                expectedVariance = 0.1f,
                styleConsistency = 0.9f,
                exploration = false
            ))
        }
        
        return recommendations
    }
    
    /**
     * 风格锁定推荐
     */
    private fun recommendWithStyleLock(
        prompt: String,
        parameters: GenerationParams,
        count: Int
    ): List<SeedRecommendation> {
        // 提取风格关键词
        val styles = extractStyles(prompt)
        
        if (styles.isEmpty()) {
            return recommendFromHistory(_history.value, count)
        }
        
        // 找相同风格的历史记录
        val sameStyleRecords = _history.value.filter { record ->
            styles.any { style -> record.tags.any { tag -> 
                tag.contains(style, ignoreCase = true) || 
                style.contains(tag, ignoreCase = true)
            }}
        }
        
        if (sameStyleRecords.size < count) {
            return recommendFromHistory(sameStyleRecords, count)
        }
        
        return recommendFromHistory(sameStyleRecords, count).map { rec ->
            rec.copy(
                reason = RecommendationReason.STYLE_MATCH,
                styleConsistency = 0.95f
            )
        }
    }
    
    /**
     * 生成探索种子
     */
    private fun generateExplorationSeeds(
        count: Int,
        normalizedPrompt: String
    ): List<SeedRecommendation> {
        val recommendations = mutableListOf<SeedRecommendation>()
        
        // 1. 从聚类中心采样
        val clusterCenters = _clusters.value
            .filter { it.size >= 3 }
            .sortedByDescending { it.averageQuality }
            .take(count / 2 + 1)
        
        for (cluster in clusterCenters) {
            recommendations.add(SeedRecommendation(
                seed = cluster.centerSeed + Random.nextLong(-1000, 1000),
                confidence = cluster.averageQuality * 0.7f,
                reason = RecommendationReason.VARIATION_EXPLORATION,
                similarPastSeeds = cluster.members.take(3),
                expectedVariance = 0.3f,
                styleConsistency = 0.6f,
                exploration = true
            ))
        }
        
        // 2. 完全随机探索
        val remaining = count - recommendations.size
        for (i in 0 until remaining) {
            val randomSeed = Random.nextLong(0, Long.MAX_VALUE)
            recommendations.add(SeedRecommendation(
                seed = randomSeed,
                confidence = 0.3f,
                reason = RecommendationReason.EXPERIMENTAL,
                similarPastSeeds = emptyList(),
                expectedVariance = 0.5f,
                styleConsistency = 0.3f,
                exploration = true
            ))
        }
        
        return recommendations
    }
    
    /**
     * 生成变体种子
     */
    private fun generateVariations(
        baseSeed: Long,
        count: Int
    ): List<SeedRecommendation> {
        val recommendations = mutableListOf<SeedRecommendation>()
        
        for (i in 1..count) {
            val variation = (baseSeed + i * 12345L * i) % Long.MAX_VALUE
            val adjustedVariance = VARIATION_STRENGTH * i / count
            
            recommendations.add(SeedRecommendation(
                seed = variation,
                confidence = 0.6f - adjustedVariance * 0.2f,
                reason = RecommendationReason.VARIATION_EXPLORATION,
                similarPastSeeds = listOf(baseSeed),
                expectedVariance = adjustedVariance,
                styleConsistency = 1f - adjustedVariance,
                exploration = false
            ))
        }
        
        return recommendations
    }
    
    /**
     * 备用推荐 (历史不足时)
     */
    private fun generateFallbackRecommendations(
        count: Int,
        mode: RecommendationMode
    ): List<SeedRecommendation> {
        return (0 until count).map { i ->
            val seed = Random.nextLong(0, Long.MAX_VALUE)
            SeedRecommendation(
                seed = seed,
                confidence = 0.3f,
                reason = RecommendationReason.EXPERIMENTAL,
                similarPastSeeds = emptyList(),
                expectedVariance = 0.4f,
                styleConsistency = 0.3f,
                exploration = true
            )
        }
    }
    
    /**
     * 确定性随机种子生成
     */
    fun generateDeterministicSeed(
        prompt: String,
        baseSeed: Long = System.currentTimeMillis()
    ): Long {
        // 使用提示词生成确定性伪随机种子
        val promptHash = prompt.hashCode().toLong()
        val combined = baseSeed xor promptHash
        return (combined * 6364136223846793005L + 1442695040888963407L) % Long.MAX_VALUE
    }
    
    /**
     * 批量生成种子
     */
    fun generateBatchSeeds(
        count: Int,
        baseSeed: Long = System.currentTimeMillis(),
        variation: Float = 0.1f
    ): List<Long> {
        return (0 until count).map { i ->
            val variationAmount = (i.toFloat() / count) * variation
            val offset = (Random.nextLong() * variationAmount).toLong()
            (baseSeed + offset + i * 31337L) % Long.MAX_VALUE
        }
    }
    
    // ===== 嵌入计算 =====
    
    /**
     * 计算提示词嵌入
     */
    private fun computeEmbedding(prompt: String): PromptEmbedding {
        val normalized = normalizePrompt(prompt)
        val words = normalized.lowercase()
            .replace(Regex("[^\\w\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }
        
        // 词袋模型 + 风格向量
        val featureVector = FloatArray(EMBEDDING_DIM)
        
        // 质量词
        val qualityWords = mapOf(
            "masterpiece" to 0.8f, "best" to 0.7f, "detailed" to 0.6f,
            "intricate" to 0.6f, "highres" to 0.5f, "8k" to 0.5f, "4k" to 0.4f,
            "photorealistic" to 0.9f, "realistic" to 0.7f, "beautiful" to 0.6f
        )
        
        // 风格词
        val styleWords = mapOf(
            "anime" to 1, "manga" to 1, "cartoon" to 2, "painting" to 3,
            "photo" to 4, "realistic" to 4, "portrait" to 5, "landscape" to 6,
            "fantasy" to 7, "sci-fi" to 7, "cyberpunk" to 7, "medieval" to 8,
            "modern" to 9, "ancient" to 8, "gothic" to 8, "baroque" to 3,
            "impressionist" to 3, "abstract" to 10
        )
        
        for ((idx, word) in words.withIndex()) {
            // 质量特征
            qualityWords[word]?.let { 
                featureVector[0] += it / words.size 
            }
            
            // 风格特征
            styleWords[word]?.let { styleIdx ->
                if (styleIdx < 20) {
                    featureVector[10 + styleIdx % 10] += 0.2f
                }
            }
            
            // 位置加权
            val positionWeight = 1f / (idx + 1)
            featureVector[20] += positionWeight * word.length.coerceAtMost(10) / 10f
        }
        
        // 归一化
        val norm = sqrt(featureVector.map { it * it }.sum())
        if (norm > 0f) {
            for (i in featureVector.indices) {
                featureVector[i] = featureVector[i] / norm
            }
        }
        
        val hash = computeSeedHash(normalized)
        
        return PromptEmbedding(
            promptId = normalized.take(50),
            vector = featureVector,
            hash = hash
        )
    }
    
    /**
     * 更新嵌入
     */
    private fun updateEmbedding(record: GenerationRecord) {
        val embedding = computeEmbedding(record.prompt)
        val current = _embeddings.value.toMutableMap()
        current[record.prompt] = embedding
        _embeddings.value = current
    }
    
    /**
     * 余弦相似度
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 0f) dotProduct / denom else 0f
    }
    
    // ===== 聚类 =====
    
    /**
     * 重新计算聚类
     */
    private fun recomputeClusters() {
        val historyRecords = _history.value
        if (historyRecords.size < 10) return
        
        val seedGroups = historyRecords
            .groupBy { it.tags.firstOrNull() ?: "default" }
            .filter { it.value.size >= 3 }
        
        val newClusters: List<SeedCluster> = seedGroups.entries.mapIndexed { idx, entry ->
            val records: List<GenerationRecord> = entry.value
            val seeds: List<Long> = records.map(GenerationRecord::seed)
            val centerIdx = seeds.size / 2
            
            SeedCluster(
                id = idx,
                centerSeed = seeds.sorted().getOrElse(centerIdx) { seeds.first() },
                members = seeds,
                commonStyles = records.flatMap { r: GenerationRecord -> r.tags }.toSet(),
                averageQuality = records.map { r: GenerationRecord -> r.qualityScore }.average().toFloat(),
                size = records.size
            )
        }.sortedByDescending { it.averageQuality }
        
        _clusters.value = newClusters
        Log.d(TAG, "聚类更新: ${newClusters.size} 个聚类")
    }
    
    // ===== 辅助方法 =====
    
    /**
     * 归一化提示词
     */
    private fun normalizePrompt(prompt: String): String {
        return prompt
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[^\\w\\s]"), "")
            .trim()
    }
    
    /**
     * 提取风格
     */
    private fun extractStyles(prompt: String): List<String> {
        val styleKeywords = listOf(
            "anime", "manga", "cartoon", "painting", "photo", "realistic",
            "portrait", "landscape", "fantasy", "sci-fi", "cyberpunk",
            "medieval", "modern", "gothic", "baroque", "abstract"
        )
        
        val lowerPrompt = prompt.lowercase()
        return styleKeywords.filter { lowerPrompt.contains(it) }
    }
    
    /**
     * 计算种子哈希
     */
    private fun computeSeedHash(prompt: String): Long {
        var hash = 0L
        for (char in prompt) {
            hash = 31L * hash + char.code
        }
        return hash
    }
    
    /**
     * 计算预期变化程度
     */
    private fun computeExpectedVariance(seed: Long, prompt: String): Float {
        val seedFactor = (seed % 1000) / 1000f
        val promptFactor = prompt.hashCode() % 100 / 100f
        return (seedFactor * 0.5f + promptFactor * 0.5f).coerceIn(0f, 1f)
    }
    
    /**
     * 计算风格一致性
     */
    private fun computeStyleConsistency(seed: Long, prompt: String): Float {
        val seedStyle = (seed % 10) / 10f
        val promptStyles = extractStyles(prompt)
        val promptStyleFactor = if (promptStyles.isNotEmpty()) 0.8f else 0.5f
        return (seedStyle * 0.4f + promptStyleFactor * 0.6f).coerceIn(0f, 1f)
    }
    
    /**
     * 持久化历史
     */
    private fun saveHistory(history: List<GenerationRecord>) {
        try {
            val file = File(context.filesDir, "seed_history.json")
            val lines = history.map { record ->
                "${record.seed}|${record.prompt.replace("|", "&#124;")}|${record.qualityScore}|${record.tags.joinToString(",")}|${record.timestamp}"
            }
            file.writeText(lines.joinToString("\n"))
        } catch (e: Exception) {
            Log.e(TAG, "保存历史失败: ${e.message}")
        }
    }
    
    private fun loadHistory(): List<GenerationRecord> {
        val file = File(context.filesDir, "seed_history.json")
        if (!file.exists()) return emptyList()
        
        return try {
            file.readLines().mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size >= 5) {
                    GenerationRecord(
                        seed = parts[0].toLongOrNull() ?: return@mapNotNull null,
                        prompt = parts[1].replace("&#124;", "|"),
                        normalizedPrompt = normalizePrompt(parts[1].replace("&#124;", "|")),
                        parameters = GenerationParams(positivePrompt = parts[1]),
                        qualityScore = parts[2].toFloatOrNull() ?: 0f,
                        tags = parts[3].split(",").filter { it.isNotEmpty() },
                        timestamp = parts[4].toLongOrNull() ?: 0L
                    )
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载历史失败: ${e.message}")
            emptyList()
        }
    }
    
    private fun saveStats() {
        prefs.edit()
            .putInt(KEY_SEED_COUNT, totalSeedsGenerated)
            .putLong(KEY_LAST_SEED, lastUsedSeed)
            .apply()
    }
    
    /**
     * 获取统计信息
     */
    fun getStats(): SeederStats {
        val historyRecords = _history.value
        val avgQuality = if (historyRecords.isNotEmpty()) {
            historyRecords.map { it.qualityScore }.average().toFloat()
        } else 0f
        
        val topQuality = historyRecords.maxOfOrNull { it.qualityScore } ?: 0f
        
        return SeederStats(
            totalGenerated = totalSeedsGenerated,
            historySize = historyRecords.size,
            clusterCount = _clusters.value.size,
            averageQuality = avgQuality,
            topQualityScore = topQuality,
            uniqueStyles = historyRecords.flatMap { it.tags }.toSet().size
        )
    }
    
    /**
     * 清除历史
     */
    fun clearHistory() {
        _history.value = emptyList()
        _embeddings.value = emptyMap()
        _clusters.value = emptyList()
        _recommendations.value = emptyMap()
        totalSeedsGenerated = 0
        lastUsedSeed = -1L
        
        File(context.filesDir, "seed_history.json").delete()
        saveStats()
    }
    
    data class SeederStats(
        val totalGenerated: Int,
        val historySize: Int,
        val clusterCount: Int,
        val averageQuality: Float,
        val topQualityScore: Float,
        val uniqueStyles: Int
    )
    
    enum class RecommendationMode {
        BALANCED,         // 平衡模式
        QUALITY_FOCUS,   // 质量优先
        DIVERSITY,       // 多样性优先
        STYLE_LOCK,      // 风格锁定
        EXPLORATION      // 完全探索
    }
    

}
