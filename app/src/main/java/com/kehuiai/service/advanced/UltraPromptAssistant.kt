package com.kehuiai.service.advanced

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.kehuiai.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.*

/**
 * 快绘AI v3.6.0 超级提示词助手
 * 智能提示词生成、优化、翻译
 */
class UltraPromptAssistant(private val context: Context) {

    companion object {
        private const val TAG = "UltraPromptAssistant"
    }

    // ========== 提示词类型 ==========
    
    enum class PromptType {
        TEXT_TO_IMAGE,    // 文生图
        IMAGE_TO_IMAGE,   // 图生图
        INPAINT,          // 局部重绘
        OUTPAINT,         // 画布扩展
        SKETCH,           // 草图
        STYLE_TRANSFER,   // 风格迁移
        PORTRAIT,         // 人像
        LANDSCAPE,        // 风景
        ANIMAL,           // 动物
        PRODUCT,          // 产品
        ARCHITECTURE,     // 建筑
        ABSTRACT          // 抽象
    }
    
    // ========== 主题词库 ==========
    
    object SubjectLibrary {
        
        // 人像主题
        val portrait = listOf(
            "beautiful woman", "handsome man", "young girl", "elderly man",
            "asian woman", "caucasian man", "african woman", "latin woman",
            "portrait of a person", "close-up portrait", "full body portrait",
            "half body portrait", "profile portrait", "group photo"
        )
        
        // 姿势
        val poses = listOf(
            "standing", "sitting", "walking", "running", "dancing",
            "leaning", "lying down", "reclining", "looking at camera",
            "looking away", "turning around", "arms crossed", "hands in pockets"
        )
        
        // 表情
        val expressions = listOf(
            "smile", "serious", "neutral", "happy", "sad",
            "angry", "surprised", "confident", "mysterious", "peaceful"
        )
        
        // 服装
        val clothing = listOf(
            "casual clothes", "formal suit", "dress", "jeans and t-shirt",
            "traditional clothing", "uniform", "swimwear", "formal gown",
            "business attire", "sportswear", "winter coat", "summer outfit"
        )
        
        // 发型
        val hairstyles = listOf(
            "short hair", "long hair", "curly hair", "straight hair",
            "wavy hair", "ponytail", "bun", "braid",
            "bald", "shaved head", "messy hair", "neat hair"
        )
        
        // 风景主题
        val landscape = listOf(
            "mountain", "ocean", "forest", "desert", "beach",
            "sunset", "sunrise", "night sky", "starry night",
            "waterfall", "river", "lake", "meadow",
            "cityscape", "countryside", "village", "temple"
        )
        
        // 动物主题
        val animals = listOf(
            "dog", "cat", "bird", "horse", "lion", "tiger",
            "eagle", "wolf", "fox", "rabbit", "butterfly",
            "fish", "dragon", "butterfly", "owl"
        )
        
        // 产品主题
        val products = listOf(
            "sneaker", "watch", "phone", "headphones", "bottle",
            "perfume", "cosmetics", "jewelry", "handbag", "glasses"
        )
        
        // 建筑主题
        val architecture = listOf(
            "modern building", "traditional house", "skyscraper", "castle",
            "temple", "church", "bridge", "tower", "palace",
            "garden", "interior design", "coffee shop", "library"
        )
        
        // 艺术风格
        val artStyles = listOf(
            "oil painting", "watercolor", "digital art", "sketch",
            "charcoal drawing", "pastel", "acrylic", "ink painting",
            "graffiti", "pixel art", "vector art", "3d render"
        )
        
        // 摄影风格
        val photoStyles = listOf(
            "portrait photography", "landscape photography", "street photography",
            "macro photography", "wildlife photography", "fashion photography",
            "product photography", "food photography", "architecture photography"
        )
    }
    
    // ========== 提示词生成器 ==========
    
    data class GeneratedPrompt(
        val positive: String,
        val negative: String,
        val style: String?,
        val tags: List<String>,
        val quality: String,
        val confidence: Float
    )
    
    /**
     * 根据主题生成提示词
     */
    fun generatePrompt(
        type: PromptType,
        subject: String,
        style: String? = null,
        quality: String = "high",
        includeLighting: Boolean = true,
        includeCamera: Boolean = false,
        customTags: List<String> = emptyList()
    ): GeneratedPrompt {
        
        val tags = mutableListOf<String>()
        
        // 添加质量标签
        tags.addAll(getQualityTags(quality))
        
        // 添加主题
        tags.add(subject)
        
        // 添加风格
        if (style != null) {
            tags.addAll(getStyleTags(style))
        }
        
        // 根据类型添加特定标签
        when (type) {
            PromptType.PORTRAIT -> {
                tags.addAll(getPortraitTags())
                if (includeLighting) tags.add("soft lighting")
                if (includeCamera) tags.add("85mm lens")
            }
            PromptType.LANDSCAPE -> {
                tags.addAll(getLandscapeTags())
                if (includeLighting) tags.add("golden hour")
                if (includeCamera) tags.add("wide angle lens")
            }
            PromptType.ANIMAL -> {
                tags.addAll(getAnimalTags())
                if (includeLighting) tags.add("natural lighting")
            }
            PromptType.PRODUCT -> {
                tags.addAll(getProductTags())
                if (includeLighting) tags.add("studio lighting")
                if (includeCamera) tags.add("macro lens")
            }
            PromptType.ARCHITECTURE -> {
                tags.addAll(getArchitectureTags())
                if (includeLighting) tags.add("dramatic lighting")
            }
            else -> {}
        }
        
        // 添加自定义标签
        tags.addAll(customTags)
        
        // 构建提示词
        val positive = tags.distinct().joinToString(", ")
        val negative = getNegativePrompt(type)
        
        return GeneratedPrompt(
            positive = positive,
            negative = negative,
            style = style,
            tags = tags,
            quality = quality,
            confidence = 0.85f
        )
    }
    
    /**
     * 从关键词生成完整提示词
     */
    fun generateFromKeywords(
        keywords: List<String>,
        intent: String = "general"
    ): String {
        val tags = mutableListOf<String>()
        
        // 质量标签
        tags.addAll(getQualityTags("high"))
        
        // 添加关键词
        tags.addAll(keywords)
        
        // 根据意图添加修饰
        when (intent) {
            "portrait" -> {
                tags.addAll(listOf("portrait", "detailed face", "professional photography"))
                if ("lighting" in keywords) tags.add("cinematic lighting")
            }
            "landscape" -> {
                tags.addAll(listOf("landscape", "scenic", "breathtaking"))
                if ("time" in keywords) tags.add("golden hour")
            }
            "art" -> {
                tags.addAll(listOf("artwork", "creative", "artistic"))
                if ("medium" in keywords) tags.add("digital art")
            }
            "product" -> {
                tags.addAll(listOf("product shot", "commercial", "professional"))
                tags.add("clean background")
            }
        }
        
        return tags.distinct().joinToString(", ")
    }
    
    // ========== 提示词优化 ==========
    
    /**
     * 优化提示词
     */
    fun optimizePrompt(prompt: String): String {
        val tags = parsePrompt(prompt)
        
        // 去重
        val uniqueTags = tags.distinct()
        
        // 排序（质量标签优先）
        val sortedTags = sortTags(uniqueTags)
        
        return sortedTags.joinToString(", ")
    }
    
    /**
     * 增强提示词
     */
    fun enhancePrompt(
        prompt: String,
        addQuality: Boolean = true,
        addLighting: Boolean = true,
        addStyle: Boolean = true
    ): String {
        val tags = parsePrompt(prompt).toMutableList()
        
        if (addQuality) {
            tags.addAll(0, getQualityTags("high"))
        }
        
        if (addLighting) {
            tags.add("beautiful lighting")
        }
        
        if (addStyle) {
            tags.add("professional")
        }
        
        return tags.distinct().joinToString(", ")
    }
    
    /**
     * 简化提示词
     */
    fun simplifyPrompt(prompt: String, maxTags: Int = 20): String {
        val tags = parsePrompt(prompt)
        
        // 保留最重要的标签
        val important = tags.take(maxTags)
        
        return important.joinToString(", ")
    }
    
    /**
     * 翻译提示词
     */
    fun translatePrompt(prompt: String, toLanguage: String): String {
        // 简化翻译（实际应该调用翻译API）
        val translations = mapOf(
            "beautiful" to "美丽",
            "woman" to "女人",
            "portrait" to "肖像",
            "landscape" to "风景"
        )
        
        return translations.entries.fold(prompt) { acc, (en, cn) ->
            acc.replace(en, cn)
        }
    }
    
    // ========== 标签管理 ==========
    
    private fun getQualityTags(quality: String): List<String> {
        return when (quality.lowercase()) {
            "ultra", "highest" -> listOf("masterpiece", "best quality", "ultra detailed", "8k")
            "high" -> listOf("masterpiece", "best quality", "highly detailed", "detailed")
            "medium" -> listOf("high quality", "detailed")
            "low", "draft" -> listOf("low quality")
            else -> listOf("best quality", "detailed")
        }
    }
    
    private fun getStyleTags(style: String): List<String> {
        return when (style.lowercase()) {
            "realistic", "写实" -> listOf("photorealistic", "realistic", "photo", "real life")
            "anime", "动漫" -> listOf("anime", "anime style", "illustration")
            "illustration", "插画" -> listOf("digital illustration", "artwork", "concept art")
            "oil painting", "油画" -> listOf("oil painting", "canvas texture", "classical")
            "watercolor", "水彩" -> listOf("watercolor", "watercolour painting")
            "sketch", "素描" -> listOf("pencil sketch", "drawing", "detailed sketch")
            "3d", "3d render" -> listOf("3d render", "3d", "cgi")
            "pixel", "像素" -> listOf("pixel art", "pixelated", "retro")
            "fantasy", "奇幻" -> listOf("fantasy", "magical", "ethereal")
            "sci-fi", "科幻" -> listOf("sci-fi", "futuristic", "cyberpunk")
            else -> listOf(style)
        }
    }
    
    private fun getPortraitTags(): List<String> {
        return listOf(
            "portrait", "detailed face", "detailed eyes", "detailed skin",
            "beautiful face", "symmetrical face", "professional portrait"
        )
    }
    
    private fun getLandscapeTags(): List<String> {
        return listOf(
            "landscape", "scenic", "breathtaking view", "nature",
            "high detail", "ambient occlusion"
        )
    }
    
    private fun getAnimalTags(): List<String> {
        return listOf(
            "animal", "wildlife", "nature", "detailed fur",
            "sharp focus", "life-like"
        )
    }
    
    private fun getProductTags(): List<String> {
        return listOf(
            "product photography", "commercial", "clean background",
            "sharp focus", "professional lighting", "studio"
        )
    }
    
    private fun getArchitectureTags(): List<String> {
        return listOf(
            "architecture", "building", "architectural detail",
            "interior design", "modern", "clean lines"
        )
    }
    
    private fun getNegativePrompt(type: PromptType): String {
        val base = listOf(
            "blurry", "low quality", "watermark", "text", "logo",
            "bad anatomy", "bad hands", "extra digits", "fewer digits",
            "cropped", "worst quality"
        )
        
        val extras = when (type) {
            PromptType.PORTRAIT -> listOf(
                "deformed face", "bad face", "extra fingers", "fewer fingers",
                "asymmetric eyes", "bad eyes", "poorly drawn hands"
            )
            PromptType.ANIMAL -> listOf(
                "deformed animal", "bad anatomy", "extra limbs"
            )
            else -> emptyList()
        }
        
        return (base + extras).joinToString(", ")
    }
    
    // ========== 提示词解析 ==========
    
    private fun parsePrompt(prompt: String): List<String> {
        return prompt.split(",", "，")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
    }
    
    private fun sortTags(tags: List<String>): List<String> {
        val qualityPriority = listOf("masterpiece", "best quality", "high quality")
        val subjectPriority = listOf("portrait", "landscape", "animal", "building")
        
        return tags.sortedWith(compareBy({ tag ->
            when {
                qualityPriority.any { tag.contains(it) } -> 0
                subjectPriority.any { tag.contains(it) } -> 1
                else -> 2
            }
        }))
    }
    
    // ========== 提示词建议 ==========
    
    data class Suggestion(
        val text: String,
        val type: String,
        val category: String,
        val icon: String
    )
    
    /**
     * 获取提示词建议
     */
    fun getSuggestions(context: String): List<Suggestion> {
        val suggestions = mutableListOf<Suggestion>()
        
        // 根据上下文提供建议
        when {
            context.contains("portrait", ignoreCase = true) ||
            context.contains("人", ignoreCase = true) -> {
                suggestions.addAll(getPortraitSuggestions())
            }
            context.contains("landscape", ignoreCase = true) ||
            context.contains("风景", ignoreCase = true) -> {
                suggestions.addAll(getLandscapeSuggestions())
            }
            context.contains("animal", ignoreCase = true) ||
            context.contains("动物", ignoreCase = true) -> {
                suggestions.addAll(getAnimalSuggestions())
            }
            else -> {
                suggestions.addAll(getGeneralSuggestions())
            }
        }
        
        return suggestions
    }
    
    private fun getPortraitSuggestions(): List<Suggestion> {
        return listOf(
            Suggestion("detailed face", "quality", "质量", "✨"),
            Suggestion("beautiful eyes", "feature", "特征", "👁️"),
            Suggestion("soft lighting", "lighting", "光照", "💡"),
            Suggestion("85mm lens", "camera", "镜头", "📷"),
            Suggestion("studio background", "background", "背景", "🎭"),
            Suggestion("professional makeup", "style", "风格", "💄")
        )
    }
    
    private fun getLandscapeSuggestions(): List<Suggestion> {
        return listOf(
            Suggestion("golden hour", "lighting", "光照", "🌅"),
            Suggestion("wide angle lens", "camera", "镜头", "📷"),
            Suggestion("highly detailed", "quality", "质量", "✨"),
            Suggestion("dramatic sky", "feature", "特征", "☁️"),
            Suggestion("vibrant colors", "style", "风格", "🎨"),
            Suggestion("mist", "atmosphere", "氛围", "🌫️")
        )
    }
    
    private fun getAnimalSuggestions(): List<Suggestion> {
        return listOf(
            Suggestion("detailed fur", "quality", "质量", "✨"),
            Suggestion("sharp focus", "quality", "质量", "🔍"),
            Suggestion("natural habitat", "setting", "环境", "🏞️"),
            Suggestion("wildlife photography", "style", "风格", "📸"),
            Suggestion("golden hour lighting", "lighting", "光照", "🌅"),
            Suggestion("life-like", "quality", "质量", "🎯")
        )
    }
    
    private fun getGeneralSuggestions(): List<Suggestion> {
        return listOf(
            Suggestion("masterpiece", "quality", "质量", "✨"),
            Suggestion("best quality", "quality", "质量", "⭐"),
            Suggestion("highly detailed", "quality", "质量", "🔍"),
            Suggestion("beautiful lighting", "lighting", "光照", "💡"),
            Suggestion("professional", "style", "风格", "🎯"),
            Suggestion("8k", "quality", "质量", "📺")
        )
    }
    
    // ========== 预设提示词 ==========
    
    data class PromptPreset(
        val id: String,
        val name: String,
        val nameCN: String,
        val prompt: String,
        val negative: String,
        val category: String,
        val emoji: String
    )
    
    fun getPresets(): List<PromptPreset> {
        return listOf(
            // 人像类
            PromptPreset(
                id = "portrait_pro",
                name = "Professional Portrait",
                nameCN = "专业人像",
                prompt = "masterpiece, best quality, professional portrait, detailed face, beautiful eyes, soft lighting, 85mm lens, studio background, sharp focus, high resolution",
                negative = "blurry, low quality, deformed face, bad anatomy, extra fingers, asymmetric eyes, bad hands",
                category = "portrait",
                emoji = "👤"
            ),
            PromptPreset(
                id = "portrait_glamour",
                name = "Glamour Portrait",
                nameCN = "魅力人像",
                prompt = "masterpiece, best quality, glamour photography, beautiful woman, detailed face, flawless skin, professional makeup, dramatic lighting, fashion photography, bokeh",
                negative = "blurry, low quality, deformed, ugly, bad anatomy, bad hands, extra fingers",
                category = "portrait",
                emoji = "💃"
            ),
            PromptPreset(
                id = "portrait_cyberpunk",
                name = "Cyberpunk Portrait",
                nameCN = "赛博朋克人像",
                prompt = "masterpiece, best quality, cyberpunk, futuristic, neon lighting, glowing eyes, android, cyborg, intricate details, cinematic, dark atmosphere",
                negative = "blurry, low quality, realistic, natural lighting, mundane",
                category = "portrait",
                emoji = "🤖"
            ),
            
            // 风景类
            PromptPreset(
                id = "landscape_epic",
                name = "Epic Landscape",
                nameCN = "史诗风景",
                prompt = "masterpiece, best quality, epic landscape, mountain peak, dramatic sky, golden hour, volumetric lighting, mist, highly detailed, 8k, breathtaking view",
                negative = "blurry, low quality, indoor, people, buildings, deformed",
                category = "landscape",
                emoji = "🏔️"
            ),
            PromptPreset(
                id = "landscape_night",
                name = "Starry Night",
                nameCN = "星空夜景",
                prompt = "masterpiece, best quality, starry night, galaxy, milky way, night sky, dark atmosphere, glowing stars, astrophotography, long exposure, highly detailed",
                negative = "blurry, low quality, daytime, sun, bright",
                category = "landscape",
                emoji = "🌌"
            ),
            PromptPreset(
                id = "landscape_ocean",
                name = "Ocean View",
                nameCN = "海洋风景",
                prompt = "masterpiece, best quality, ocean, sea, waves, sunset, golden hour, beach, tropical, crystal clear water, reflection, highly detailed, peaceful",
                negative = "blurry, low quality, dirty water, pollution, crowded",
                category = "landscape",
                emoji = "🌊"
            ),
            
            // 动漫类
            PromptPreset(
                id = "anime_style",
                name = "Anime Style",
                nameCN = "动漫风格",
                prompt = "masterpiece, best quality, anime style, anime, illustration, vibrant colors, detailed eyes, beautiful hair, soft lighting, dynamic pose",
                negative = "realistic, photorealistic, 3d render, blurry, low quality, bad anatomy, bad hands",
                category = "anime",
                emoji = "🎨"
            ),
            PromptPreset(
                id = "anime_mecha",
                name = "Mecha Anime",
                nameCN = "机甲动漫",
                prompt = "masterpiece, best quality, mecha, robot, mechanical, detailed mechanical design, anime, sci-fi, dramatic lighting, intricate details, futuristic",
                negative = "realistic, organic, soft, blurry, low quality",
                category = "anime",
                emoji = "🤖"
            ),
            
            // 艺术类
            PromptPreset(
                id = "art_oil",
                name = "Oil Painting",
                nameCN = "油画",
                prompt = "masterpiece, best quality, oil painting, canvas texture, classical painting, renaissance style, detailed brushwork, dramatic lighting, museum quality",
                negative = "blurry, low quality, digital, modern, photograph",
                category = "art",
                emoji = "🖼️"
            ),
            PromptPreset(
                id = "art_watercolor",
                name = "Watercolor",
                nameCN = "水彩画",
                prompt = "masterpiece, best quality, watercolor painting, watercolor, soft colors, delicate, artistic, flowing, gentle, paper texture",
                negative = "blurry, low quality, digital, harsh, dark",
                category = "art",
                emoji = "🎨"
            ),
            
            // 产品类
            PromptPreset(
                id = "product_shot",
                name = "Product Shot",
                nameCN = "产品展示",
                prompt = "masterpiece, best quality, product photography, commercial, clean white background, sharp focus, professional studio lighting, reflection, high-end, advertisement",
                negative = "blurry, low quality, cluttered background, amateur, dark, shadows",
                category = "product",
                emoji = "📦"
            ),
            
            // 建筑类
            PromptPreset(
                id = "arch_modern",
                name = "Modern Architecture",
                nameCN = "现代建筑",
                prompt = "masterpiece, best quality, modern architecture, contemporary building, minimalist design, clean lines, glass facade, steel and concrete, dramatic lighting, professional photography",
                negative = "blurry, low quality, old building, messy, cluttered",
                category = "architecture",
                emoji = "🏛️"
            ),
            
            // 幻想类
            PromptPreset(
                id = "fantasy_dragon",
                name = "Fantasy Dragon",
                nameCN = "幻想龙",
                prompt = "masterpiece, best quality, dragon, fantasy, epic, mythical creature, detailed scales, wings, fire breathing, dramatic lighting, dark atmosphere, highly detailed, 8k",
                negative = "blurry, low quality, realistic, modern, mundane, boring",
                category = "fantasy",
                emoji = "🐉"
            ),
            PromptPreset(
                id = "fantasy_magic",
                name = "Magic Scene",
                nameCN = "魔法场景",
                prompt = "masterpiece, best quality, fantasy, magical, spell, magic, ethereal, glowing, mystical, particles, dramatic lighting, dark atmosphere, enchanted forest, fairy",
                negative = "blurry, low quality, realistic, mundane, boring, no magic",
                category = "fantasy",
                emoji = "✨"
            )
        )
    }
    
    fun getPresetsByCategory(category: String): List<PromptPreset> {
        return getPresets().filter { it.category == category }
    }
    
    fun searchPresets(query: String): List<PromptPreset> {
        val q = query.lowercase()
        return getPresets().filter {
            it.name.lowercase().contains(q) ||
            it.nameCN.contains(query) ||
            it.prompt.lowercase().contains(q)
        }
    }
    
    // ========== 提示词评分 ==========
    
    data class PromptScore(
        val totalScore: Float,
        val qualityScore: Float,
        val styleScore: Float,
        val detailScore: Float,
        val balanceScore: Float,
        val suggestions: List<String>
    )
    
    /**
     * 评估提示词质量
     */
    fun evaluatePrompt(prompt: String): PromptScore {
        val tags = parsePrompt(prompt)
        
        // 质量评分
        val qualityTags = listOf("masterpiece", "best quality", "high quality", "detailed", "8k", "4k")
        val qualityScore = tags.count { tag ->
            qualityTags.any { tag.contains(it.lowercase()) }
        }.coerceAtMost(5) / 5f
        
        // 风格评分
        val styleTags = listOf("anime", "realistic", "painting", "photography", "digital", "oil")
        val styleScore = tags.count { tag ->
            styleTags.any { tag.contains(it.lowercase()) }
        }.coerceAtMost(3) / 3f
        
        // 细节评分
        val detailScore = (tags.size.coerceAtMost(30) - 5).coerceAtLeast(0) / 25f
        
        // 平衡评分
        val balanceScore = if (tags.size in 10..30) 1f else (tags.size / 30f).coerceAtMost(1f)
        
        // 总分
        val totalScore = (qualityScore * 0.3f + styleScore * 0.2f + detailScore * 0.3f + balanceScore * 0.2f)
        
        // 建议
        val suggestions = mutableListOf<String>()
        if (qualityScore < 0.5f) suggestions.add("添加更多质量标签，如 masterpiece, best quality")
        if (styleScore < 0.3f) suggestions.add("指定风格，如 anime, realistic, photorealistic")
        if (tags.size < 10) suggestions.add("增加更多描述细节")
        if (tags.size > 40) suggestions.add("提示词可能过长，考虑简化")
        
        return PromptScore(
            totalScore = totalScore,
            qualityScore = qualityScore,
            styleScore = styleScore,
            detailScore = detailScore,
            balanceScore = balanceScore,
            suggestions = suggestions
        )
    }
    
    // ========== 提示词模板 ==========
    
    data class PromptTemplate(
        val id: String,
        val name: String,
        val slots: List<TemplateSlot>,
        val preset: String
    )
    
    data class TemplateSlot(
        val id: String,
        val label: String,
        val hint: String,
        val required: Boolean,
        val options: List<String>? = null
    )
    
    /**
     * 获取提示词模板
     */
    fun getTemplates(): List<PromptTemplate> {
        return listOf(
            PromptTemplate(
                id = "portrait_template",
                name = "人像模板",
                slots = listOf(
                    TemplateSlot("subject", "主体", "如：beautiful woman, handsome man", true),
                    TemplateSlot("pose", "姿势", "如：standing, sitting", false, SubjectLibrary.poses),
                    TemplateSlot("expression", "表情", "如：smile, serious", false, SubjectLibrary.expressions),
                    TemplateSlot("clothing", "服装", "如：dress, suit", false, SubjectLibrary.clothing),
                    TemplateSlot("hairstyle", "发型", "如：long hair, short hair", false, SubjectLibrary.hairstyles),
                    TemplateSlot("lighting", "光照", "如：soft lighting, dramatic", false),
                    TemplateSlot("style", "风格", "如：anime, realistic", false)
                ),
                preset = "masterpiece, best quality, {subject}, {pose}, {expression}, {clothing}, {hairstyle}, {lighting}, {style}"
            ),
            PromptTemplate(
                id = "landscape_template",
                name = "风景模板",
                slots = listOf(
                    TemplateSlot("scene", "场景", "如：mountain, ocean, forest", true, SubjectLibrary.landscape),
                    TemplateSlot("time", "时间", "如：sunset, golden hour", false),
                    TemplateSlot("weather", "天气", "如：clear sky, cloudy", false),
                    TemplateSlot("lighting", "光照", "如：dramatic lighting, soft", false),
                    TemplateSlot("style", "风格", "如：photorealistic, anime", false)
                ),
                preset = "masterpiece, best quality, {scene}, {time}, {weather}, {lighting}, {style}, highly detailed"
            ),
            PromptTemplate(
                id = "product_template",
                name = "产品模板",
                slots = listOf(
                    TemplateSlot("product", "产品", "如：sneaker, watch", true, SubjectLibrary.products),
                    TemplateSlot("material", "材质", "如：leather, metal", false),
                    TemplateSlot("lighting", "光照", "如：studio lighting", false),
                    TemplateSlot("background", "背景", "如：white background", false),
                    TemplateSlot("style", "风格", "如：commercial, luxury", false)
                ),
                preset = "masterpiece, best quality, {product} photography, {material}, {lighting}, {background}, {style}, sharp focus"
            )
        )
    }
    
    /**
     * 使用模板生成提示词
     */
    fun applyTemplate(templateId: String, values: Map<String, String>): String {
        val template = getTemplates().find { it.id == templateId } ?: return ""
        
        var prompt = template.preset
        
        for ((slotId, value) in values) {
            prompt = prompt.replace("{$slotId}", value)
        }
        
        return prompt
    }
}
