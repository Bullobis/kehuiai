package comkuaihuiai.service

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * 可绘AI v3.6.0 - 智能提示词引擎
 * 
 * 功能：
 * - 自动补全
 * - 语法检查
 * - 中英翻译
 * - Tag优化
 * - 风格建议
 */
class PromptOptimizer {

    companion object {
        private const val TAG = "PromptOptimizer"
        
        // Tag 权重标记
        private val WEIGHT_PATTERN = Pattern.compile("\\((.+?):([\\d.]+)\\)")
        private val BRACKET_WEIGHT_PATTERN = Pattern.compile("\\[(.+?):([\\d.]+)\\]")
        
        // 最大长度
        private const val MAX_PROMPT_LENGTH = 500
        private const val MAX_TAGS = 50
    }
    
    /**
     * 提示词类型
     */
    enum class PromptType {
        TEXT_TO_IMAGE,    // 文生图
        IMAGE_TO_IMAGE,   // 图生图
        INPAINTING,       // 局部重绘
        CONTROLLED        // 控制生成
    }
    
    /**
     * 优化配置
     */
    data class OptimizationConfig(
        val enableAutoComplete: Boolean = true,
        val enableTranslation: Boolean = true,
        val enableSyntaxCheck: Boolean = true,
        val enableTagOptimization: Boolean = true,
        val enableStyleSuggestions: Boolean = true,
        val targetLanguage: String = "en",  // en, zh, ja
        val maxLength: Int = MAX_PROMPT_LENGTH
    )
    
    /**
     * 优化结果
     */
    data class OptimizationResult(
        val original: String,
        val optimized: String,
        val changes: List<PromptChange>,
        val suggestions: List<String>,
        val tags: List<PromptTag>,
        val grammarIssues: List<GrammarIssue>,
        val confidence: Float
    )
    
    /**
     * 提示词变化
     */
    data class PromptChange(
        val type: ChangeType,
        val original: String,
        val replacement: String,
        val reason: String
    )
    
    enum class ChangeType {
        ADDED, REMOVED, REPLACED, REORDERED, WEIGHT_ADJUSTED
    }
    
    /**
     * 提示词Tag
     */
    data class PromptTag(
        val text: String,
        val weight: Float = 1.0f,
        val category: TagCategory,
        val isNegative: Boolean = false
    )
    
    enum class TagCategory {
        SUBJECT,          // 主体
        STYLE,            // 风格
        MEDIUM,           // 媒介
        QUALITY,          // 质量
        CAMERA,           // 相机/视角
        LIGHTING,         // 光照
        ENVIRONMENT,      // 环境
        EMOTION,          // 情绪
        OTHER             // 其他
    }
    
    /**
     * 语法问题
     */
    data class GrammarIssue(
        val start: Int,
        val end: Int,
        val text: String,
        val issue: String,
        val suggestion: String?
    )
    
    /**
     * 翻译结果
     */
    data class Translation(
        val original: String,
        val translated: String,
        val sourceLang: String,
        val targetLang: String,
        val confidence: Float
    )
    
    /**
     * 风格预设
     */
    data class StylePreset(
        val name: String,
        val tags: List<String>,
        val description: String,
        val category: String
    )
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // 缓存
    private val translationCache = ConcurrentHashMap<String, Translation>()
    private val suggestionCache = ConcurrentHashMap<String, List<String>>()
    
    // 配置
    private var config = OptimizationConfig()
    
    // 预设风格
    private val stylePresets = listOf(
        StylePreset("动漫", listOf("anime", "illustration", " vibrant colors", " detailed"), "日式动漫风格", "anime"),
        StylePreset("写实", listOf("photorealistic", "realistic", "8k", "highly detailed"), "照片级真实风格", "realistic"),
        StylePreset("油画", listOf("oil painting", "artwork", "brushstroke", "museum quality"), "油画质感", "painting"),
        StylePreset("水彩", listOf("watercolor", "soft colors", "ethereal", "delicate"), "水彩画风格", "painting"),
        StylePreset("赛博朋克", listOf("cyberpunk", "neon lights", "futuristic", "dark atmosphere"), "赛博朋克风格", "sci-fi"),
        StylePreset("奇幻", listOf("fantasy", "magical", "epic", "cinematic lighting"), "奇幻风格", "fantasy"),
        StylePreset("像素", listOf("pixel art", "retro", "8-bit", "game sprite"), "像素艺术", "retro"),
        StylePreset("素描", listOf("pencil sketch", "monochrome", "detailed lineart", "shading"), "素描风格", "sketch"),
        StylePreset("电影感", listOf("cinematic", "film grain", "anamorphic", "dramatic lighting"), "电影风格", "cinematic"),
        StylePreset("复古", listOf("vintage", "retro", "faded colors", "1970s"), "复古胶片风格", "vintage")
    )
    
    // 常用质量Tag
    private val qualityTags = listOf(
        "masterpiece", "best quality", "highly detailed", "8k", "ultra sharp",
        "absurdres", "incredible absurdres", "huge filesize"
    )
    
    // 常用否定Tag
    private val negativeTags = listOf(
        "low quality", "worst quality", "blurry", "bad anatomy", "bad hands",
        "missing fingers", "extra digits", "floating limbs", "deformed"
    )
    
    /**
     * 设置配置
     */
    fun setConfig(newConfig: OptimizationConfig) {
        config = newConfig
        Log.i(TAG, "配置已更新: $config")
    }
    
    /**
     * 优化提示词
     */
    suspend fun optimize(
        prompt: String,
        type: PromptType = PromptType.TEXT_TO_IMAGE
    ): OptimizationResult = withContext(Dispatchers.Default) {
        Log.i(TAG, "优化提示词: ${prompt.take(50)}...")
        
        val changes = mutableListOf<PromptChange>()
        val suggestions = mutableListOf<String>()
        var optimized = prompt
        
        // 1. 语法检查
        val grammarIssues = if (config.enableSyntaxCheck) {
            checkGrammar(prompt)
        } else emptyList()
        
        // 2. 长度检查
        if (prompt.length > config.maxLength) {
            suggestions.add("提示词过长 (${prompt.length}/${config.maxLength})，建议精简")
        }
        
        // 3. Tag 优化
        if (config.enableTagOptimization) {
            val tagOptimization = optimizeTags(prompt)
            optimized = tagOptimization.first
            changes.addAll(tagOptimization.second)
        }
        
        // 4. 自动补全
        if (config.enableAutoComplete) {
            val autoComplete = autoComplete(optimized, type)
            if (autoComplete.isNotEmpty()) {
                changes.add(PromptChange(
                    ChangeType.ADDED,
                    "",
                    autoComplete,
                    "自动补全建议"
                ))
                optimized += ", $autoComplete"
            }
        }
        
        // 5. 风格建议
        if (config.enableStyleSuggestions) {
            suggestions.addAll(getStyleSuggestions(optimized))
        }
        
        // 解析 Tags
        val tags = parseTags(optimized)
        
        OptimizationResult(
            original = prompt,
            optimized = optimized.take(config.maxLength),
            changes = changes,
            suggestions = suggestions,
            tags = tags,
            grammarIssues = grammarIssues,
            confidence = calculateConfidence(prompt, changes, grammarIssues)
        )
    }
    
    /**
     * 翻译提示词
     */
    suspend fun translate(
        text: String,
        sourceLang: String = "auto",
        targetLang: String = "en"
    ): Translation = withContext(Dispatchers.Default) {
        val cacheKey = "${text.hashCode()}_${sourceLang}_$targetLang"
        
        translationCache[cacheKey]?.let { return@withContext it }
        
        Log.i(TAG, "翻译: ${text.take(30)}... ($sourceLang -> $targetLang)")
        
        // 简单翻译模拟
        val translated = simpleTranslate(text, targetLang)
        
        val result = Translation(
            original = text,
            translated = translated,
            sourceLang = sourceLang,
            targetLang = targetLang,
            confidence = 0.85f
        )
        
        translationCache[cacheKey] = result
        result
    }
    
    /**
     * 自动补全
     */
    suspend fun autoComplete(
        prompt: String,
        type: PromptType = PromptType.TEXT_TO_IMAGE
    ): String = withContext(Dispatchers.Default) {
        val words = prompt.lowercase().split(Regex("[\\s,.!?]+")).filter { it.isNotEmpty() }
        
        val suggestions = mutableListOf<String>()
        
        // 检查是否包含风格
        val hasStyle = stylePresets.any { preset ->
            words.any { word -> preset.tags.any { tag -> tag.contains(word) } }
        }
        
        if (!hasStyle) {
            suggestions.add("masterpiece")
            suggestions.add("best quality")
        }
        
        // 检查是否有相机/视角
        val hasCamera = words.any { word -> 
            word.contains("view") || word.contains("angle") || 
            word.contains("shot") || word.contains("perspective")
        }
        
        if (!hasCamera) {
            suggestions.add("detailed view")
        }
        
        // 检查是否有光照
        val hasLighting = words.any { word ->
            word.contains("light") || word.contains("shadow") || 
            word.contains("sun") || word.contains("glow")
        }
        
        if (!hasLighting) {
            suggestions.add("cinematic lighting")
        }
        
        suggestions.joinToString(", ")
    }
    
    /**
     * 获取风格建议
     */
    fun getStyleSuggestions(prompt: String): List<String> {
        val cacheKey = prompt.hashCode().toString()
        suggestionCache[cacheKey]?.let { return it }
        
        val words = prompt.lowercase()
        val suggestions = mutableListOf<String>()
        
        // 匹配风格
        stylePresets.forEach { preset ->
            val matchCount = preset.tags.count { tag -> words.contains(tag) }
            if (matchCount >= 2) {
                suggestions.add("建议风格: ${preset.name}")
            }
        }
        
        // 检查缺失的质量Tag
        val hasQuality = qualityTags.any { words.contains(it) }
        if (!hasQuality) {
            suggestions.add("建议添加质量Tag: masterpiece, best quality")
        }
        
        // 检查是否有否定提示词
        val hasNegative = words.contains("negative") || words.contains("not")
        if (!hasNegative) {
            suggestions.add("建议添加否定提示词避免常见问题")
        }
        
        suggestionCache[cacheKey] = suggestions
        return suggestions
    }
    
    /**
     * 获取风格预设
     */
    fun getStylePresets(): List<StylePreset> = stylePresets
    
    /**
     * 获取常用Tag
     */
    fun getCommonTags(): List<String> = qualityTags
    
    /**
     * 获取否定Tag
     */
    fun getNegativeTags(): List<String> = negativeTags
    
    /**
     * 生成否定提示词
     */
    fun generateNegativePrompt(customNegatives: List<String> = emptyList()): String {
        val base = negativeTags.toMutableList()
        base.addAll(customNegatives)
        return base.distinct().joinToString(", ")
    }
    
    /**
     * 语法检查
     */
    fun checkGrammar(prompt: String): List<GrammarIssue> {
        val issues = mutableListOf<GrammarIssue>()
        
        // 检查括号匹配
        val openParen = prompt.count { it == '(' }
        val closeParen = prompt.count { it == ')' }
        if (openParen != closeParen) {
            issues.add(GrammarIssue(
                start = 0,
                end = prompt.length,
                text = prompt,
                issue = "括号不匹配",
                suggestion = "添加或移除 ${kotlin.math.abs(openParen - closeParen)} 个括号"
            ))
        }
        
        // 检查权重格式
        val weightMatcher = WEIGHT_PATTERN.matcher(prompt)
        while (weightMatcher.find()) {
            val weight = weightMatcher.group(2)?.toFloatOrNull() ?: 1f
            if (weight < 0.1f || weight > 2f) {
                issues.add(GrammarIssue(
                    start = weightMatcher.start(),
                    end = weightMatcher.end(),
                    text = weightMatcher.group(),
                    issue = "权重超出范围 (0.1-2.0)",
                    suggestion = "调整权重值"
                ))
            }
        }
        
        return issues
    }
    
    /**
     * 解析 Tags
     */
    fun parseTags(prompt: String): List<PromptTag> {
        val tags = mutableListOf<PromptTag>()
        val parts = prompt.split(Regex("[,]+")).map { it.trim() }.filter { it.isNotEmpty() }
        
        parts.forEach { part ->
            val isNegative = part.startsWith("(") && part.endsWith(")") && 
                            part.contains(":") && part.contains("negative")
            
            // 提取权重
            val weightMatcher = WEIGHT_PATTERN.matcher(part)
            var weight = 1.0f
            var text = part
            
            if (weightMatcher.find()) {
                text = weightMatcher.group(1) ?: part
                weight = weightMatcher.group(2)?.toFloatOrNull() ?: 1.0f
            }
            
            // 分类
            val category = categorizeTag(text)
            
            tags.add(PromptTag(
                text = text,
                weight = weight,
                category = category,
                isNegative = isNegative || text.startsWith("(") && part.contains("worst")
            ))
        }
        
        return tags
    }
    
    /**
     * 清理缓存
     */
    fun clearCache() {
        translationCache.clear()
        suggestionCache.clear()
        Log.i(TAG, "缓存已清除")
    }
    
    /**
     * 释放资源
     */
    fun release() {
        scope.cancel()
        clearCache()
        Log.i(TAG, "PromptOptimizer 已释放")
    }
    
    // ==================== 私有方法 ====================
    
    private fun optimizeTags(prompt: String): Pair<String, List<PromptChange>> {
        val changes = mutableListOf<PromptChange>()
        var optimized = prompt
        
        // 移除重复Tag
        val tags = prompt.split(Regex("[,]+")).map { it.trim().lowercase() }.toSet()
        if (tags.size != prompt.split(Regex("[,]+")).size) {
            changes.add(PromptChange(
                ChangeType.REMOVED,
                "重复Tags",
                tags.joinToString(", "),
                "移除重复项"
            ))
            optimized = tags.joinToString(", ")
        }
        
        // 调整顺序 (质量Tag放最后)
        val parts = optimized.split(Regex("[,]+")).map { it.trim() }.toMutableList()
        val qualityTagsInPrompt = parts.filter { part -> 
            qualityTags.any { quality -> part.lowercase().contains(quality) }
        }
        
        if (qualityTagsInPrompt.size > 1) {
            // 将质量Tag移到最后
            parts.removeAll(qualityTagsInPrompt.toSet())
            parts.addAll(qualityTagsInPrompt)
            
            if (parts.joinToString(", ") != optimized) {
                changes.add(PromptChange(
                    ChangeType.REORDERED,
                    optimized,
                    parts.joinToString(", "),
                    "质量Tag重新排序"
                ))
                optimized = parts.joinToString(", ")
            }
        }
        
        return optimized to changes
    }
    
    private fun categorizeTag(tag: String): TagCategory {
        val lowerTag = tag.lowercase()
        
        return when {
            // 风格
            stylePresets.any { it.tags.any { t -> lowerTag.contains(t) } } -> TagCategory.STYLE
            
            // 质量
            qualityTags.any { lowerTag.contains(it) } -> TagCategory.QUALITY
            
            // 媒介
            lowerTag.contains("painting") || lowerTag.contains("drawing") ||
            lowerTag.contains("sketch") || lowerTag.contains("photo") -> TagCategory.MEDIUM
            
            // 相机/视角
            lowerTag.contains("view") || lowerTag.contains("angle") ||
            lowerTag.contains("shot") || lowerTag.contains("perspective") ||
            lowerTag.contains("close-up") || lowerTag.contains("portrait") -> TagCategory.CAMERA
            
            // 光照
            lowerTag.contains("light") || lowerTag.contains("shadow") ||
            lowerTag.contains("sun") || lowerTag.contains("glow") ||
            lowerTag.contains("lighting") -> TagCategory.LIGHTING
            
            // 环境
            lowerTag.contains("background") || lowerTag.contains("scene") ||
            lowerTag.contains("environment") || lowerTag.contains("sky") -> TagCategory.ENVIRONMENT
            
            // 情绪
            lowerTag.contains("happy") || lowerTag.contains("sad") ||
            lowerTag.contains("emotion") || lowerTag.contains("mood") -> TagCategory.EMOTION
            
            // 主体 (默认)
            else -> TagCategory.SUBJECT
        }
    }
    
    private fun simpleTranslate(text: String, targetLang: String): String {
        // 简化的翻译模拟
        val translations = mapOf(
            "漂亮" to "beautiful",
            "女孩" to "girl",
            "男孩" to "boy",
            "风景" to "landscape",
            "天空" to "sky",
            "花朵" to "flower",
            "森林" to "forest",
            "海洋" to "ocean",
            "日出" to "sunrise",
            "日落" to "sunset",
            "美丽" to "beautiful",
            "可爱" to "cute",
            "帅" to "handsome",
            "动漫" to "anime",
            "写实" to "realistic",
            "幻想" to "fantasy"
        )
        
        var result = text
        translations.forEach { (zh, en) ->
            if (targetLang == "en") {
                result = result.replace(zh, en)
            }
        }
        
        // 如果没有翻译，返回原文
        if (result == text && targetLang == "en") {
            // 添加英文提示
            result = "$text, detailed, high quality"
        }
        
        return result
    }
    
    private fun calculateConfidence(
        prompt: String,
        changes: List<PromptChange>,
        issues: List<GrammarIssue>
    ): Float {
        var confidence = 1.0f
        
        // 减少因修改导致的置信度
        confidence -= changes.size * 0.05f
        
        // 减少因语法问题导致的置信度
        confidence -= issues.size * 0.1f
        
        // 增加因包含质量Tag
        if (qualityTags.any { prompt.lowercase().contains(it) }) {
            confidence += 0.1f
        }
        
        return confidence.coerceIn(0.3f, 0.98f)
    }
}
