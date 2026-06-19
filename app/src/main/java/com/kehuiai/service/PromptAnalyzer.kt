@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST", "DEPRECATION", "USELESS_ELVIS")
package com.kehuiai.service

import android.content.Context
import android.util.Log

/**
 * 提示词分析器
 * 分析、优化、增强提示词
 */
class PromptAnalyzer(private val context: Context) {

    companion object {
        private const val TAG = "PromptAnalyzer"
        
        // 质量修饰词
        private val QUALITY_MODIFIERS = listOf(
            "masterpiece", "best quality", "high quality", "ultra detailed",
            "4k", "8k", "highly detailed", "photorealistic", "realistic",
            "professional", "amazing", "excellent", "beautiful", "perfect"
        )
        
        // 风格标签
        private val STYLE_TAGS = listOf(
            "anime", "manga", "illustration", "digital art", "painting",
            "photo", "photograph", "cinematic", "cinematic lighting",
            "3d render", "concept art", "vector art", "pixel art"
        )
        
        // 构图标签
        private val COMPOSITION_TAGS = listOf(
            "portrait", "landscape", "close-up", "full body", "bust",
            "wide shot", "dynamic angle", "overhead view", "side view",
            "front view", "back view", "three-quarter view"
        )
        
        // 光照标签
        private val LIGHTING_TAGS = listOf(
            "soft lighting", "hard lighting", "natural lighting", "studio lighting",
            "rim lighting", "backlighting", "volumetric lighting", "god rays",
            "cinematic lighting", "dramatic lighting", "golden hour", "blue hour"
        )
        
        // 负面提示词建议
        private val DEFAULT_NEGATIVE = listOf(
            "lowres", "bad anatomy", "bad hands", "text", "error",
            "missing fingers", "extra digit", "fewer digits", "cropped",
            "worst quality", "low quality", "normal quality", "jpeg artifacts",
            "signature", "watermark", "username", "blurry", "artist name"
        )
    }

    /**
     * 分析提示词
     */
    fun analyze(prompt: String): PromptAnalysis {
        val tokens = tokenize(prompt)
        val wordCount = tokens.size
        val qualityScore = calculateQualityScore(tokens)
        val detectedStyles = detectStyles(tokens)
        val detectedComposition = detectComposition(tokens)
        val detectedLighting = detectLighting(tokens)
        val hasWeight = containsWeight(prompt)
        val hasLora = containsLora(prompt)
        val hasEmbedding = containsEmbedding(prompt)
        
        return PromptAnalysis(
            originalPrompt = prompt,
            tokens = tokens,
            wordCount = wordCount,
            qualityScore = qualityScore,
            detectedStyles = detectedStyles,
            detectedComposition = detectedComposition,
            detectedLighting = detectedLighting,
            hasWeight = hasWeight,
            hasLora = hasLora,
            hasEmbedding = hasEmbedding,
            suggestions = generateSuggestions(tokens, qualityScore, prompt)
        )
    }

    /**
     * 优化提示词
     */
    fun optimize(prompt: String, target: OptimizationTarget = OptimizationTarget.BALANCED): String {
        val analysis = analyze(prompt)
        val tokens = analysis.tokens.toMutableList()
        
        when (target) {
            OptimizationTarget.QUALITY -> {
                // 添加质量修饰词
                if (!QUALITY_MODIFIERS.any { tokens.any { t -> t.contains(it) } }) {
                    tokens.add(0, "masterpiece")
                    tokens.add(1, "best quality")
                }
                // 添加高分辨率
                if (!tokens.any { it.contains("k") && it.all { c -> c.isDigit() || c == 'k' } }) {
                    tokens.add("highly detailed")
                    tokens.add("4k")
                }
            }
            
            OptimizationTarget.DETAIL -> {
                // 增强细节描述
                if (analysis.detectedStyles.isEmpty()) {
                    tokens.add("detailed")
                    tokens.add("intricate")
                }
            }
            
            OptimizationTarget.SPEED -> {
                // 简化提示词以加快生成
                val importantTokens = tokens.take(10)
                return importantTokens.joinToString(", ")
            }
            
            OptimizationTarget.BALANCED -> {
                // 平衡优化
                if (analysis.qualityScore < 0.5f) {
                    tokens.add(0, "masterpiece")
                }
                if (!tokens.any { it.contains("detail") }) {
                    tokens.add("detailed")
                }
            }
            
            OptimizationTarget.ARTISTIC -> {
                // 艺术风格增强
                tokens.add(0, "artstation trending")
                tokens.add("concept art")
                tokens.add("digital painting")
            }
        }
        
        return tokens.joinToString(", ")
    }

    /**
     * 增强提示词
     */
    fun enhance(prompt: String, enhanceType: EnhanceType = EnhanceType.DETAIL): String {
        val tokens = tokenize(prompt).toMutableList()
        
        when (enhanceType) {
            EnhanceType.DETAIL -> {
                // 添加细节词
                val detailWords = listOf(
                    "intricate details", "elaborate", "ornate", "complex",
                    "sophisticated", "meticulously detailed"
                )
                tokens.addAll(detailWords.shuffled().take(2))
            }
            
            EnhanceType.VISUAL -> {
                // 添加视觉效果词
                val visualWords = listOf(
                    "visual effects", "stunning visuals", "breathtaking",
                    "jaw-dropping", "impressive"
                )
                tokens.addAll(visualWords.shuffled().take(2))
            }
            
            EnhanceType.MOOD -> {
                // 添加情绪/氛围词
                val moodWords = listOf(
                    "moody", "atmospheric", "emotional", "expressive",
                    "captivating", "engaging"
                )
                tokens.addAll(moodWords.shuffled().take(2))
            }
            
            EnhanceType.COMPLETE -> {
                // 完整增强：质量 + 细节 + 视觉
                tokens.add(0, "masterpiece")
                tokens.add("best quality")
                tokens.add("highly detailed")
                tokens.add("4k")
                tokens.add("digital art")
                tokens.add("concept art")
            }
        }
        
        return tokens.joinToString(", ")
    }

    /**
     * 生成推荐负面提示词
     */
    fun generateNegativePrompt(prompt: String): String {
        val analysis = analyze(prompt)
        val negatives = DEFAULT_NEGATIVE.toMutableList()
        
        // 根据检测到的风格添加特定负面词
        if (analysis.detectedStyles.contains("anime")) {
            negatives.addAll(listOf("anime style", "cartoon", "3d render"))
        }
        if (analysis.detectedStyles.contains("photo")) {
            negatives.addAll(listOf("illustration", "drawing", "painting"))
        }
        
        return negatives.joinToString(", ")
    }

    /**
     * 提取提示词中的权重词
     */
    fun extractWeightedTerms(prompt: String): List<WeightedTerm> {
        val results = mutableListOf<WeightedTerm>()
        val regex = Regex("""\(([^:)]+)(?::(\d+\.?\d*))?\)""")
        
        regex.findAll(prompt).forEach { match ->
            val term = match.groupValues[1]
            val weight = match.groupValues[2].toFloatOrNull() ?: 1.0f
            results.add(WeightedTerm(term.trim(), weight, match.range))
        }
        
        // 也检测 [] 语法
        val bracketRegex = Regex("""\[([^\]]+)\]""")
        bracketRegex.findAll(prompt).forEach { match ->
            results.add(WeightedTerm(match.groupValues[1].trim(), 0.9f, match.range))
        }
        
        return results
    }

    /**
     * 清理提示词
     */
    fun cleanPrompt(prompt: String): String {
        var cleaned = prompt
        
        // 移除多余空格
        cleaned = cleaned.replace(Regex("""\s+"""), " ")
        
        // 移除重复的词
        val tokens = tokenize(cleaned)
        cleaned = tokens.distinct().joinToString(", ")
        
        // 移除空的括号
        cleaned = cleaned.replace(Regex("""\(\s*\)"""), "")
        cleaned = cleaned.replace(Regex("""\[\s*\]"""), "")
        
        return cleaned.trim()
    }

    /**
     * 翻译提示词（模拟）
     */
    fun translatePrompt(prompt: String, targetLang: String): String {
        // 这里应该调用翻译 API
        // 简化返回原文本
        return prompt
    }

    // ==================== 内部方法 ====================

    private fun tokenize(prompt: String): List<String> {
        return prompt
            .split(Regex(""",\s*"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun calculateQualityScore(tokens: List<String>): Float {
        if (tokens.isEmpty()) return 0f
        
        var score = 0f
        
        // 质量词
        tokens.forEach { token ->
            if (QUALITY_MODIFIERS.any { token.contains(it, ignoreCase = true) }) {
                score += 0.2f
            }
        }
        
        // 风格词
        if (STYLE_TAGS.any { tokens.any { t -> t.contains(it, ignoreCase = true) } }) {
            score += 0.2f
        }
        
        // 构图词
        if (COMPOSITION_TAGS.any { tokens.any { t -> t.contains(it, ignoreCase = true) } }) {
            score += 0.1f
        }
        
        // 光照词
        if (LIGHTING_TAGS.any { tokens.any { t -> t.contains(it, ignoreCase = true) } }) {
            score += 0.1f
        }
        
        // 长度加分
        if (tokens.size >= 5) score += 0.2f
        if (tokens.size >= 10) score += 0.1f
        
        return score.coerceIn(0f, 1f)
    }

    private fun detectStyles(tokens: List<String>): List<String> {
        return STYLE_TAGS.filter { tag ->
            tokens.any { it.contains(tag, ignoreCase = true) }
        }
    }

    private fun detectComposition(tokens: List<String>): List<String> {
        return COMPOSITION_TAGS.filter { tag ->
            tokens.any { it.contains(tag, ignoreCase = true) }
        }
    }

    private fun detectLighting(tokens: List<String>): List<String> {
        return LIGHTING_TAGS.filter { tag ->
            tokens.any { it.contains(tag, ignoreCase = true) }
        }
    }

    private fun containsWeight(prompt: String): Boolean {
        return prompt.contains(":") && prompt.contains("(")
    }

    private fun containsLora(prompt: String): Boolean {
        return prompt.contains("<") && prompt.contains(">")
    }

    private fun containsEmbedding(prompt: String): Boolean {
        return tokenize(prompt).any { it.startsWith("embedding:") }
    }

    private fun generateSuggestions(tokens: List<String>, qualityScore: Float, prompt: String): List<String> {
        val suggestions = mutableListOf<String>()
        
        if (qualityScore < 0.5f) {
            suggestions.add("添加质量修饰词如: masterpiece, best quality")
        }
        
        if (!STYLE_TAGS.any { tokens.any { t -> t.contains(it, ignoreCase = true) } }) {
            suggestions.add("指定艺术风格: anime, photo, painting 等")
        }
        
        if (!LIGHTING_TAGS.any { tokens.any { t -> t.contains(it, ignoreCase = true) } }) {
            suggestions.add("添加光照描述: soft lighting, cinematic lighting 等")
        }
        
        if (tokens.size < 5) {
            suggestions.add("增加更多细节描述以获得更好效果")
        }
        
        if (!containsWeight(prompt) && !containsLora(prompt)) {
            suggestions.add("使用 () 调整词权重，如: (word:1.2)")
        }
        
        return suggestions
    }
}

/**
 * 提示词分析结果
 */
data class PromptAnalysis(
    val originalPrompt: String,
    val tokens: List<String>,
    val wordCount: Int,
    val qualityScore: Float,
    val detectedStyles: List<String>,
    val detectedComposition: List<String>,
    val detectedLighting: List<String>,
    val hasWeight: Boolean,
    val hasLora: Boolean,
    val hasEmbedding: Boolean,
    val suggestions: List<String>
)

/**
 * 加权词项
 */
data class WeightedTerm(
    val term: String,
    val weight: Float,
    val range: IntRange
)

/**
 * 优化目标
 */
enum class OptimizationTarget {
    QUALITY,   // 质量优先
    DETAIL,    // 细节优先
    SPEED,     // 速度优先
    BALANCED,  // 平衡
    ARTISTIC   // 艺术风格
}

/**
 * 增强类型
 */
enum class EnhanceType {
    DETAIL,   // 细节增强
    VISUAL,   // 视觉增强
    MOOD,     // 氛围增强
    COMPLETE  // 完整增强
}
