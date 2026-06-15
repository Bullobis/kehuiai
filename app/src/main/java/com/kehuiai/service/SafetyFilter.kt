package com.kehuiai.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.MessageDigest

/**
 * 可绘AI v3.6 - 多层内容安全审核系统
 *
 * 三重审核机制：
 * 1. Prompt 预审（生成前）
 * 2. 过程中审（生成中）
 * 3. 成品终审（生成后）
 */
class SafetyFilter(private val context: Context) {

    companion object {
        private const val TAG = "SafetyFilter"

        // 敏感词库路径
        private const val BLOCKLIST_FILE = "filters/blocklist.txt"
        private const val NSFW_KEYWORDS_FILE = "filters/nsfw_keywords.txt"

        // 审核级别
        const val LEVEL_NONE = 0
        const val LEVEL_LIGHT = 1
        const val LEVEL_MODERATE = 2
        const val LEVEL_STRICT = 3

        // 风险评分阈值
        private const val RISK_THRESHOLD_LIGHT = 0.7f
        private const val RISK_THRESHOLD_MODERATE = 0.5f
        private const val RISK_THRESHOLD_STRICT = 0.3f

        @Volatile
        private var INSTANCE: SafetyFilter? = null

        fun getInstance(context: Context): SafetyFilter {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SafetyFilter(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // ===== 嵌套数据类 =====

    data class ReviewResult(
        val passed: Boolean,
        val riskLevel: RiskLevel,
        val reason: String? = null,
        val suggestions: List<String> = emptyList(),
        val warnings: List<String> = emptyList(),
        val durationMs: Long = 0
    )

    data class MidCheckResult(
        val shouldContinue: Boolean,
        val riskLevel: RiskLevel,
        val reason: String? = null
    )

    data class FinalReviewResult(
        val passed: Boolean,
        val issues: List<String>,
        val imageHash: String? = null,
        val durationMs: Long = 0
    )

    data class SafetyStats(
        val totalReviewed: Int = 0,
        val passed: Int = 0,
        val blocked: Int = 0,
        val flagged: Int = 0
    )

    enum class RiskLevel {
        NONE, LOW, MEDIUM, HIGH
    }

    // ===== 实例成员 =====

    // 审核级别
    private var _reviewLevel = MutableStateFlow(LEVEL_MODERATE)
    val reviewLevel: StateFlow<Int> = _reviewLevel.asStateFlow()

    // 是否启用审核
    private var _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    // 统计数据
    private val _stats = MutableStateFlow(SafetyStats())
    val stats: StateFlow<SafetyStats> = _stats.asStateFlow()

    // 敏感词库（内存缓存）
    private val sensitiveKeywords = mutableSetOf<String>()
    private val nsfwKeywords = mutableSetOf<String>()
    private val safeKeywords = mutableSetOf<String>()

    // 预编译正则（性能优化）
    private val sensitivePatterns = mutableListOf<Regex>()

    // 上次加载时间
    private var lastLoadTime = 0L

    init {
        loadBlocklists()
    }

    /**
     * 设置审核级别
     */
    fun setReviewLevel(level: Int) {
        _reviewLevel.value = level.coerceIn(LEVEL_NONE, LEVEL_STRICT)
    }

    /**
     * 设置是否启用审核
     */
    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
    }

    /**
     * Prompt 预审（生成前）
     */
    fun reviewPrompt(prompt: String, isNsfwAllowed: Boolean = false): ReviewResult {
        val startTime = System.nanoTime()

        if (!_isEnabled.value || _reviewLevel.value == LEVEL_NONE) {
            return ReviewResult(passed = true, riskLevel = RiskLevel.NONE, durationMs = elapsed(startTime))
        }

        // 敏感词检测
        val sensitiveMatches = findSensitiveMatches(prompt)
        if (sensitiveMatches.isNotEmpty()) {
            incrementBlocked()
            return ReviewResult(
                passed = false,
                riskLevel = RiskLevel.HIGH,
                reason = "包含敏感内容: ${sensitiveMatches.joinToString(", ")}",
                warnings = listOf("建议修改提示词"),
                durationMs = elapsed(startTime)
            )
        }

        // NSFW 检测
        if (!isNsfwAllowed) {
            val nsfwMatches = findNsfwMatches(prompt)
            val riskScore = calculateRiskScore(prompt)

            val threshold = when (_reviewLevel.value) {
                LEVEL_LIGHT -> RISK_THRESHOLD_LIGHT
                LEVEL_MODERATE -> RISK_THRESHOLD_MODERATE
                LEVEL_STRICT -> RISK_THRESHOLD_STRICT
                else -> RISK_THRESHOLD_MODERATE
            }

            if (nsfwMatches.isNotEmpty() || riskScore > threshold) {
                incrementFlagged()
                val riskLevel = when {
                    riskScore > 0.8f -> RiskLevel.HIGH
                    riskScore > 0.5f -> RiskLevel.MEDIUM
                    else -> RiskLevel.LOW
                }
                return ReviewResult(
                    passed = riskLevel != RiskLevel.HIGH,
                    riskLevel = riskLevel,
                    reason = if (nsfwMatches.isNotEmpty()) "可能包含不适宜内容: ${nsfwMatches.take(3).joinToString()}" else null,
                    suggestions = listOf("尝试更中性的描述", "降低相关词汇权重"),
                    warnings = nsfwMatches,
                    durationMs = elapsed(startTime)
                )
            }
        }

        // 格式安全检查
        val formatIssues = checkFormatSafety(prompt)
        if (formatIssues.isNotEmpty()) {
            return ReviewResult(
                passed = true,
                riskLevel = RiskLevel.LOW,
                warnings = formatIssues,
                durationMs = elapsed(startTime)
            )
        }

        incrementPassed()
        return ReviewResult(passed = true, riskLevel = RiskLevel.NONE, durationMs = elapsed(startTime))
    }

    /**
     * 过程中审（生成中）
     */
    fun midGenerationCheck(step: Int, totalSteps: Int, currentImage: Bitmap?): MidCheckResult {
        if (!_isEnabled.value || _reviewLevel.value == LEVEL_NONE) {
            return MidCheckResult(shouldContinue = true, riskLevel = RiskLevel.NONE)
        }

        // 仅在关键节点检查
        if (step != totalSteps / 2 && step != totalSteps - 1) {
            return MidCheckResult(shouldContinue = true, riskLevel = RiskLevel.NONE)
        }

        currentImage ?: return MidCheckResult(shouldContinue = true, riskLevel = RiskLevel.NONE)

        // 肤色分析
        val skinTone = analyzeSkinTone(currentImage)
        if (skinTone > 0.9f && _reviewLevel.value >= LEVEL_STRICT) {
            return MidCheckResult(
                shouldContinue = false,
                riskLevel = RiskLevel.MEDIUM,
                reason = "肤色检测异常，建议调整生成参数"
            )
        }

        // 图像质量检查
        val quality = analyzeImageQuality(currentImage)
        if (quality < 0.3f) {
            return MidCheckResult(
                shouldContinue = false,
                riskLevel = RiskLevel.LOW,
                reason = "图像质量过低，可能生成不良内容"
            )
        }

        return MidCheckResult(shouldContinue = true, riskLevel = RiskLevel.NONE)
    }

    /**
     * 成品终审（生成后）
     */
    fun finalReview(bitmap: Bitmap, prompt: String): FinalReviewResult {
        val startTime = System.nanoTime()

        if (!_isEnabled.value || _reviewLevel.value == LEVEL_NONE) {
            return FinalReviewResult(passed = true, issues = emptyList(), durationMs = elapsed(startTime))
        }

        val issues = mutableListOf<String>()

        // 肤色检查
        val skinTone = analyzeSkinTone(bitmap)
        if (skinTone > 0.95f) {
            issues.add("检测到高肤色区域")
        }

        // 图像哈希（可选，用于追溯）
        val imageHash = hashBitmap(bitmap)

        // 组合风险评估
        val riskScore = calculateRiskScore(prompt)
        val threshold = when (_reviewLevel.value) {
            LEVEL_STRICT -> RISK_THRESHOLD_STRICT
            LEVEL_MODERATE -> RISK_THRESHOLD_MODERATE
            else -> RISK_THRESHOLD_LIGHT
        }

        if (riskScore > threshold) {
            issues.add("Prompt 风险评分较高 (${String.format("%.2f", riskScore)})")
        }

        val passed = issues.isEmpty() || issues.none { it.contains("高肤色") || it.contains("敏感") }

        return FinalReviewResult(
            passed = passed,
            issues = issues,
            imageHash = imageHash,
            durationMs = elapsed(startTime)
        )
    }

    /**
     * 查找敏感词匹配
     */
    private fun findSensitiveMatches(text: String): List<String> {
        val lowerText = text.lowercase()
        return sensitiveKeywords.filter { keyword ->
            lowerText.contains(keyword.lowercase())
        }
    }

    /**
     * 查找 NSFW 关键词匹配
     */
    private fun findNsfwMatches(text: String): List<String> {
        val lowerText = text.lowercase()
        return nsfwKeywords.filter { keyword ->
            lowerText.contains(keyword.lowercase())
        }
    }

    /**
     * 计算风险评分
     */
    private fun calculateRiskScore(text: String): Float {
        if (sensitivePatterns.isEmpty()) return 0f

        var score = 0f
        val lowerText = text.lowercase()

        for (pattern in sensitivePatterns) {
            if (pattern.containsMatchIn(lowerText)) {
                score += 0.3f
            }
        }

        // Safe keywords reduce score
        for (keyword in safeKeywords) {
            if (lowerText.contains(keyword.lowercase())) {
                score -= 0.1f
            }
        }

        return score.coerceIn(0f, 1f)
    }

    /**
     * 检查格式安全性
     */
    private fun checkFormatSafety(prompt: String): List<String> {
        val issues = mutableListOf<String>()

        // 检测恶意注入
        if (prompt.contains("--") && prompt.contains("nsfw", ignoreCase = true)) {
            issues.add("检测到可能的参数注入")
        }

        // 检测超长 prompt（可能是攻击）
        if (prompt.length > 2000) {
            issues.add("Prompt 过长，可能包含恶意内容")
        }

        return issues
    }

    /**
     * 肤色分析（简化版）
     */
    private fun analyzeSkinTone(bitmap: Bitmap): Float {
        return try {
            val width = bitmap.width.coerceAtMost(100)
            val height = bitmap.height.coerceAtMost(100)
            val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
            var skinPixels = 0
            var totalPixels = width * height

            for (x in 0 until width) {
                for (y in 0 until height) {
                    val pixel = scaled.getPixel(x, y)
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF

                    // 简化的肤色判断
                    if (r > 95 && g > 40 && b > 20 &&
                        r > g && r > b &&
                        kotlin.math.abs(r - g) > 15 &&
                        r - g > 15
                    ) {
                        skinPixels++
                    }
                }
            }
            scaled.recycle()
            skinPixels.toFloat() / totalPixels.toFloat()
        } catch (e: Exception) {
            0f
        }
    }

    /**
     * 图像质量分析（简化版）
     */
    private fun analyzeImageQuality(bitmap: Bitmap): Float {
        return try {
            val width = bitmap.width.coerceAtMost(64)
            val height = bitmap.height.coerceAtMost(64)
            val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)

            // 简化的质量评估：检查像素方差
            var sum = 0L
            var sumSq = 0L
            var totalPixels = width * height

            for (x in 0 until width) {
                for (y in 0 until height) {
                    val pixel = scaled.getPixel(x, y)
                    val gray = ((pixel shr 16) and 0xFF + (pixel shr 8) and 0xFF + pixel and 0xFF) / 3
                    sum += gray
                    sumSq += gray * gray
                }
            }

            scaled.recycle()

            val mean = sum.toDouble() / totalPixels
            val variance = (sumSq.toDouble() / totalPixels) - (mean * mean)
            (variance / 65025.0).toFloat().coerceIn(0f, 1f)
        } catch (e: Exception) {
            0.5f
        }
    }

    /**
     * 计算图像哈希
     */
    private fun hashBitmap(bitmap: Bitmap): String {
        return try {
            val scaled = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
            val pixels = IntArray(64)
            for (i in 0..7) {
                for (j in 0..7) {
                    val pixel = scaled.getPixel(i, j)
                    pixels[i * 8 + j] = ((pixel shr 16) and 0xFF + (pixel shr 8) and 0xFF + pixel and 0xFF) / 3
                }
            }
            scaled.recycle()

            val hash = StringBuilder()
            for (i in pixels) {
                hash.append(if (i > 128) '1' else '0')
            }

            // 转换为十六进制
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(hash.toString().toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 加载词库
     */
    private fun loadBlocklists() {
        // 内置基础词库
        loadBuiltInBlocklist()

        // 尝试加载外部词库
        try {
            val blocklistFile = File(context.filesDir, BLOCKLIST_FILE)
            val nsfwFile = File(context.filesDir, NSFW_KEYWORDS_FILE)

            if (blocklistFile.exists()) {
                blocklistFile.readLines().forEach { line ->
                    if (line.isNotBlank() && !line.startsWith("#")) {
                        sensitiveKeywords.add(line.trim())
                    }
                }
            }

            if (nsfwFile.exists()) {
                nsfwFile.readLines().forEach { line ->
                    if (line.isNotBlank() && !line.startsWith("#")) {
                        nsfwKeywords.add(line.trim().lowercase())
                    }
                }
            }

            lastLoadTime = System.currentTimeMillis()

            // 预编译正则
            rebuildPatterns()
        } catch (e: Exception) {
            Log.e(TAG, "加载词库失败: ${e.message}")
        }
    }

    /**
     * 加载内置基础词库
     */
    private fun loadBuiltInBlocklist() {
        // 敏感词（简化示例）
        sensitiveKeywords.addAll(listOf(
            "violence", "blood", "weapon", "gore", "explicit"
        ))

        // NSFW 词
        nsfwKeywords.addAll(listOf(
            "nsfw", "explicit", "adult", "xxx"
        ))

        // 安全词（降低风险）
        safeKeywords.addAll(listOf(
            "art", "painting", "drawing", "illustration", "cartoon",
            "anime", "digital art", "concept art", "portrait", "landscape"
        ))
    }

    /**
     * 重建正则模式
     */
    private fun rebuildPatterns() {
        sensitivePatterns.clear()
        sensitiveKeywords.forEach { keyword ->
            try {
                sensitivePatterns.add(Regex(keyword, RegexOption.IGNORE_CASE))
            } catch (e: Exception) {
                // 忽略无效正则
            }
        }
    }

    /**
     * 更新屏蔽词
     */
    fun updateBlocklist(url: String) {
        // 可扩展：从网络更新词库
        Log.i(TAG, "更新屏蔽词库: $url")
    }

    private fun incrementPassed() {
        _stats.value = _stats.value.copy(
            totalReviewed = _stats.value.totalReviewed + 1,
            passed = _stats.value.passed + 1
        )
    }

    private fun incrementBlocked() {
        _stats.value = _stats.value.copy(
            totalReviewed = _stats.value.totalReviewed + 1,
            blocked = _stats.value.blocked + 1
        )
    }

    private fun incrementFlagged() {
        _stats.value = _stats.value.copy(
            totalReviewed = _stats.value.totalReviewed + 1,
            flagged = _stats.value.flagged + 1
        )
    }

    /**
     * 清理资源
     */
    fun release() {
        sensitivePatterns.clear()
        sensitiveKeywords.clear()
        nsfwKeywords.clear()
        safeKeywords.clear()
        INSTANCE = null
    }

    private fun elapsed(startNs: Long): Long = (System.nanoTime() - startNs) / 1_000_000
}
