@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST", "DEPRECATION", "USELESS_ELVIS")
package com.kehuiai.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 可绘AI v3.6 - 智能负提示词顾问
 *
 * 核心功能：
 * 1. 分析正提示词，智能推荐负提示词
 * 2. 基于场景/风格/内容的上下文感知推荐
 * 3. 学习用户偏好，个性化推荐
 * 4. 负面提示词组合优化
 * 5. 强度等级控制（轻度/中度/强力）
 */
class NegativePromptAdvisor(private val context: Context) {

    companion object {
        private const val TAG = "NegativePromptAdvisor"
        private const val PREFS_NAME = "negative_prompt_prefs"
        private const val KEY_LEARNED_TERMS = "learned_terms"
        private const val KEY_PREFERRED_INTENSITY = "preferred_intensity"

        // 强度等级
        const val INTENSITY_LIGHT = 1
        const val INTENSITY_MODERATE = 2
        const val INTENSITY_STRONG = 3
        const val INTENSITY_EXTREME = 4

        @Volatile
        private var INSTANCE: NegativePromptAdvisor? = null

        fun getInstance(context: Context): NegativePromptAdvisor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NegativePromptAdvisor(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // ===== 负提示词词库 =====
    
    // 通用高质量负提示词
    data class NegativeTerm(
        val keyword: String,
        val category: Category,
        val intensity: Int,  // 1-4
        val description: String,
        val conflicts: List<String> = emptyList()  // 与哪些正提示词冲突
    )

    enum class Category {
        QUALITY,      // 质量相关
        STYLE,        // 风格相关
        CONTENT,      // 内容相关
        TECHNICAL,    // 技术相关
        SUBJECT,      // 主体相关
        COMPOSITION,  // 构图相关
        EMOTION,      // 情感相关
        CULTURAL      // 文化相关
    }

    // ===== 推荐结果 =====
    data class Recommendation(
        val terms: List<String>,
        val explanations: List<String>,
        val confidence: Float,       // 置信度 0-1
        val intensity: Int,
        val categoryBreakdown: Map<Category, List<String>>
    )

    // ===== 状态 =====
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _preferredIntensity = MutableStateFlow(prefs.getInt(KEY_PREFERRED_INTENSITY, INTENSITY_MODERATE))
    val preferredIntensity: StateFlow<Int> = _preferredIntensity.asStateFlow()

    private val _learnedTerms = MutableStateFlow(loadLearnedTerms())
    val learnedTerms: StateFlow<Set<String>> = _learnedTerms.asStateFlow()

    // ===== 负提示词数据库 =====

    private val qualityTerms = listOf(
        NegativeTerm("lowres", Category.QUALITY, 2, "低分辨率、拉伸、模糊"),
        NegativeTerm("bad anatomy", Category.QUALITY, 3, "解剖结构错误、身体变形"),
        NegativeTerm("bad hands", Category.QUALITY, 3, "手部畸形、手指错误"),
        NegativeTerm("missing fingers", Category.QUALITY, 2, "手指缺失"),
        NegativeTerm("extra digits", Category.QUALITY, 2, "多余手指"),
        NegativeTerm("floating fingers", Category.QUALITY, 2, "手指漂浮、不自然"),
        NegativeTerm("mutated hands", Category.QUALITY, 2, "变异手部"),
        NegativeTerm("bad proportions", Category.QUALITY, 3, "比例失调"),
        NegativeTerm("ugly", Category.QUALITY, 2, "丑陋"),
        NegativeTerm("distorted", Category.QUALITY, 2, "扭曲变形"),
        NegativeTerm("deformed", Category.QUALITY, 2, "畸形"),
        NegativeTerm("disfigured", Category.QUALITY, 3, "毁容"),
        NegativeTerm("poorly drawn face", Category.QUALITY, 3, "面部绘制粗糙"),
        NegativeTerm("mutation", Category.QUALITY, 2, "变异"),
        NegativeTerm("mutated", Category.QUALITY, 2, "突变"),
        NegativeTerm("blurry", Category.QUALITY, 2, "模糊"),
        NegativeTerm("out of focus", Category.QUALITY, 2, "脱焦"),
        NegativeTerm("low quality", Category.QUALITY, 2, "低质量"),
        NegativeTerm("worst quality", Category.QUALITY, 2, "最差质量"),
        NegativeTerm("low quality", Category.QUALITY, 2, "低质量"),
        NegativeTerm("jpeg artifacts", Category.QUALITY, 1, "JPEG伪影"),
        NegativeTerm("compression artifacts", Category.QUALITY, 1, "压缩伪影"),
        NegativeTerm("noise", Category.QUALITY, 2, "噪点"),
        NegativeTerm("dithering", Category.QUALITY, 1, "抖动"),
        NegativeTerm("artifacts", Category.QUALITY, 2, "伪影")
    )

    private val styleTerms = listOf(
        NegativeTerm("anime style", Category.STYLE, 1, "Anime风格（当不想要时）"),
        NegativeTerm("cartoon style", Category.STYLE, 1, "卡通风格（当不想要时）"),
        NegativeTerm("3d render", Category.STYLE, 1, "3D渲染（当不想要时）"),
        NegativeTerm("semi-realistic", Category.STYLE, 1, "半真实感"),
        NegativeTerm("illustration style", Category.STYLE, 1, "插画风格"),
        NegativeTerm("digital art style", Category.STYLE, 1, "数字艺术风格"),
        NegativeTerm("photoshop", Category.STYLE, 1, "Photoshop痕迹"),
        NegativeTerm("instagram filter", Category.STYLE, 1, "Instagram滤镜"),
        NegativeTerm("snapchat filter", Category.STYLE, 1, "Snapchat滤镜"),
        NegativeTerm("western animation", Category.STYLE, 1, "西方动画风格"),
        NegativeTerm("anime screencap", Category.STYLE, 1, "动画截图感"),
        NegativeTerm("manga", Category.STYLE, 1, "漫画感"),
        NegativeTerm("hentai", Category.STYLE, 4, "色情动漫"),
        NegativeTerm("nsfw", Category.STYLE, 4, "成人内容")
    )

    private val subjectTerms = listOf(
        NegativeTerm("person", Category.SUBJECT, 1, "人物（当不想要时）"),
        NegativeTerm("human", Category.SUBJECT, 1, "人类（当不想要时）"),
        NegativeTerm("face", Category.SUBJECT, 1, "面部（当不想要时）"),
        NegativeTerm("text", Category.SUBJECT, 2, "文字错误"),
        NegativeTerm("watermark", Category.SUBJECT, 3, "水印"),
        NegativeTerm("signature", Category.SUBJECT, 2, "签名"),
        NegativeTerm("logo", Category.SUBJECT, 2, "标志"),
        NegativeTerm("brand", Category.SUBJECT, 1, "品牌"),
        NegativeTerm("copyright text", Category.SUBJECT, 2, "版权文字"),
        NegativeTerm("username", Category.SUBJECT, 2, "用户名"),
        NegativeTerm("UI", Category.SUBJECT, 2, "用户界面元素"),
        NegativeTerm("chart", Category.SUBJECT, 1, "图表"),
        NegativeTerm("graph", Category.SUBJECT, 1, "图表")
    )

    private val contentTerms = listOf(
        NegativeTerm("nude", Category.CONTENT, 4, "裸露"),
        NegativeTerm("naked", Category.CONTENT, 4, "裸体"),
        NegativeTerm("nsfw", Category.CONTENT, 4, "成人内容"),
        NegativeTerm("explicit", Category.CONTENT, 4, "露骨内容"),
        NegativeTerm("blood", Category.CONTENT, 2, "血液"),
        NegativeTerm("gore", Category.CONTENT, 3, "血腥"),
        NegativeTerm("violence", Category.CONTENT, 3, "暴力"),
        NegativeTerm("weapon", Category.CONTENT, 2, "武器"),
        NegativeTerm("guns", Category.CONTENT, 2, "枪支"),
        NegativeTerm("knives", Category.CONTENT, 1, "刀具"),
        NegativeTerm("drug", Category.CONTENT, 3, "毒品"),
        NegativeTerm("alcohol", Category.CONTENT, 1, "酒精"),
        NegativeTerm("smoking", Category.CONTENT, 1, "吸烟"),
        NegativeTerm("cigarette", Category.CONTENT, 1, "香烟")
    )

    private val technicalTerms = listOf(
        NegativeTerm("jpeg", Category.TECHNICAL, 1, "JPEG压缩痕迹"),
        NegativeTerm("png", Category.TECHNICAL, 1, "PNG格式痕迹"),
        NegativeTerm("webp", Category.TECHNICAL, 1, "WebP格式痕迹"),
        NegativeTerm("pixelated", Category.TECHNICAL, 2, "像素化"),
        NegativeTerm("interpolated", Category.TECHNICAL, 2, "插值放大"),
        NegativeTerm("upscaled", Category.TECHNICAL, 1, "放大痕迹"),
        NegativeTerm("AI-generated", Category.TECHNICAL, 1, "AI生成痕迹"),
        NegativeTerm("AI upscaled", Category.TECHNICAL, 1, "AI放大痕迹"),
        NegativeTerm("watermark", Category.TECHNICAL, 3, "水印"),
        NegativeTerm("frame", Category.TECHNICAL, 1, "边框"),
        NegativeTerm("border", Category.TECHNICAL, 1, "边界")
    )

    private val compositionTerms = listOf(
        NegativeTerm("cropped", Category.COMPOSITION, 2, "裁剪不当"),
        NegativeTerm("out of frame", Category.COMPOSITION, 2, "出框"),
        NegativeTerm("cut off", Category.COMPOSITION, 2, "截断"),
        NegativeTerm("partial view", Category.COMPOSITION, 1, "部分视图"),
        NegativeTerm("duplicate", Category.COMPOSITION, 2, "重复元素"),
        NegativeTerm("multiple heads", Category.COMPOSITION, 3, "多头"),
        NegativeTerm("multiple faces", Category.COMPOSITION, 3, "多脸"),
        NegativeTerm("extra limbs", Category.COMPOSITION, 3, "多余肢体"),
        NegativeTerm("too many fingers", Category.COMPOSITION, 2, "手指过多"),
        NegativeTerm("asymmetric", Category.COMPOSITION, 1, "不对称"),
        NegativeTerm("uneven", Category.COMPOSITION, 1, "不均匀"),
        NegativeTerm("amateur", Category.COMPOSITION, 1, "业余感")
    )

    private val emotionTerms = listOf(
        NegativeTerm("sad", Category.EMOTION, 1, "悲伤表情（当不想要时）"),
        NegativeTerm("angry", Category.EMOTION, 1, "愤怒表情（当不想要时）"),
        NegativeTerm("disgusting", Category.EMOTION, 2, "厌恶表情"),
        NegativeTerm("frowning", Category.EMOTION, 1, "皱眉"),
        NegativeTerm("grimacing", Category.EMOTION, 1, "苦相"),
        NegativeTerm("teeth", Category.EMOTION, 1, "牙齿问题"),
        NegativeTerm("clenched teeth", Category.EMOTION, 1, "咬紧牙关"),
        NegativeTerm("open mouth", Category.EMOTION, 1, "张嘴问题"),
        NegativeTerm("dull", Category.EMOTION, 1, "呆滞眼神"),
        NegativeTerm("dead eyes", Category.EMOTION, 2, "死鱼眼")
    )

    private val allTerms: List<NegativeTerm> by lazy {
        qualityTerms + styleTerms + subjectTerms + contentTerms +
        technicalTerms + compositionTerms + emotionTerms
    }

    // ===== 主要接口 =====

    /**
     * 分析正提示词，生成负提示词推荐
     * @param positivePrompt 正提示词
     * @param intensity 强度等级（1-4）
     * @param isNsfwAllowed 是否允许NSFW内容
     */
    fun recommend(
        positivePrompt: String,
        intensity: Int = _preferredIntensity.value,
        isNsfwAllowed: Boolean = false
    ): Recommendation {
        val lowerPrompt = positivePrompt.lowercase()
        val selectedTerms = mutableListOf<String>()
        val explanations = mutableListOf<String>()
        val categoryBreakdown = mutableMapOf<Category, MutableList<String>>()

        // 1. 场景分析
        val sceneAnalysis = analyzeScene(lowerPrompt)

        // 2. 添加通用质量词（根据强度）
        val qualityTerms = selectQualityTerms(intensity)
        qualityTerms.forEach { (term, reason) ->
            if (!selectedTerms.contains(term)) {
                selectedTerms.add(term)
                explanations.add(reason)
                val qL: MutableList<String>? = categoryBreakdown[Category.QUALITY]; if (qL != null) qL.add(term) else { categoryBreakdown[Category.QUALITY] = mutableListOf(term) }
            }
        }

        // 3. 基于场景添加特定负提示词
        for (analysis in sceneAnalysis) {
            val termList: List<NegativeTerm> = analysis.terms
            for (term in termList) {
                val negTerm: String = term.keyword
                val alreadySelected: Boolean = selectedTerms.contains(negTerm)
                val withinIntensity: Boolean = term.intensity <= intensity
                if (!alreadySelected && withinIntensity) {
                    selectedTerms.add(negTerm)
                    val reasonText: String = "${analysis.reason}: ${term.description}"
                    explanations.add(reasonText)
                    val existingList: MutableList<String>? = categoryBreakdown[term.category]
                    if (existingList != null) {
                        existingList.add(negTerm)
                    } else {
                        val newList: MutableList<String> = mutableListOf(negTerm)
                        categoryBreakdown[term.category] = newList
                    }
                }
            }
        }

        // 4. 如果是人物场景，添加人物相关的负提示词
        if (containsKeywords(lowerPrompt, listOf("person", "man", "woman", "girl", "boy", "portrait", "face", "character", "people"))) {
            addPortraitTerms(selectedTerms, explanations, categoryBreakdown, intensity)
        }

        // 5. 如果是风景场景，添加风景特定的负提示词
        if (containsKeywords(lowerPrompt, listOf("landscape", "scenery", "nature", "mountain", "sky", "ocean", "forest", "city"))) {
            addLandscapeTerms(selectedTerms, explanations, categoryBreakdown, intensity)
        }

        // 6. 如果是艺术风格，添加风格负提示词
        if (containsKeywords(lowerPrompt, listOf("anime", "manga", "cartoon", "painting", "illustration", "artwork"))) {
            addArtStyleTerms(selectedTerms, explanations, categoryBreakdown, intensity)
        }

        // 7. 如果是真实感场景，添加真实感负提示词
        if (containsKeywords(lowerPrompt, listOf("realistic", "photo", "photograph", "real life", "actual"))) {
            addRealisticTerms(selectedTerms, explanations, categoryBreakdown, intensity)
        }

        // 8. 如果是动物场景
        if (containsKeywords(lowerPrompt, listOf("animal", "dog", "cat", "bird", "fish", "horse", "wildlife"))) {
            addAnimalTerms(selectedTerms, explanations, categoryBreakdown, intensity)
        }

        // 9. 添加用户学习到的偏好词
        _learnedTerms.value.forEach { term ->
            if (!selectedTerms.contains(term)) {
                selectedTerms.add(term)
                explanations.add("基于您的偏好（学习）")
                val qL: MutableList<String>? = categoryBreakdown[Category.QUALITY]; if (qL != null) qL.add(term) else { categoryBreakdown[Category.QUALITY] = mutableListOf(term) }
            }
        }

        // 10. NSFW 过滤
        if (!isNsfwAllowed) {
            val nsfwTerms = contentTerms.filter { it.intensity >= 3 }
            nsfwTerms.forEach { term ->
                if (!selectedTerms.contains(term.keyword)) {
                    selectedTerms.add(term.keyword)
                    explanations.add("安全过滤: ${term.description}")
                    val cL: MutableList<String>? = categoryBreakdown[term.category]; if (cL != null) cL.add(term.keyword) else { categoryBreakdown[term.category] = mutableListOf(term.keyword) }
                }
            }
        }

        // 计算置信度
        val confidence = calculateConfidence(positivePrompt, selectedTerms.size, sceneAnalysis.size)

        return Recommendation(
            terms = selectedTerms.take(30),  // 限制数量
            explanations = explanations,
            confidence = confidence,
            intensity = intensity,
            categoryBreakdown = categoryBreakdown
        )
    }

    /**
     * 获取快速推荐（简化版）
     */
    fun quickRecommend(isPortrait: Boolean = false, isArtStyle: Boolean = false): String {
        val terms = mutableListOf<String>()

        // 通用质量词
        terms.addAll(listOf("lowres", "bad anatomy", "bad hands", "text", "watermark", "signature"))

        if (isPortrait) {
            terms.addAll(listOf("missing fingers", "extra digits", "floating fingers", "worst quality", "low quality", "poorly drawn face"))
            terms.addAll(listOf("multiple faces", "duplicate", "mutation", "mutated hands"))
        }

        if (isArtStyle) {
            terms.addAll(listOf("realistic", "photorealistic", "3d render", "photo"))
        }

        return terms.distinct().joinToString(", ")
    }

    /**
     * 获取推荐负提示词的数量
     */
    fun getRecommendedCount(intensity: Int): Int {
        return when (intensity) {
            INTENSITY_LIGHT -> 5
            INTENSITY_MODERATE -> 10
            INTENSITY_STRONG -> 15
            INTENSITY_EXTREME -> 25
            else -> 10
        }
    }

    /**
     * 学习用户的负提示词偏好
     */
    fun learnFromFeedback(addedTerms: List<String>, removedTerms: List<String>) {
        val current = _learnedTerms.value.toMutableSet()
        addedTerms.forEach { term ->
            if (term.isNotBlank()) current.add(term.lowercase().trim())
        }
        removedTerms.forEach { term ->
            current.remove(term.lowercase().trim())
        }
        _learnedTerms.value = current
        saveLearnedTerms(current)
    }

    /**
     * 获取所有可用负提示词（用于选择界面）
     */
    fun getAllNegativeTerms(): List<NegativeTerm> = allTerms

    /**
     * 获取特定类别的负提示词
     */
    fun getTermsByCategory(category: Category): List<NegativeTerm> {
        return allTerms.filter { it.category == category }
    }

    /**
     * 获取完整推荐（包含详细信息）
     */
    fun getDetailedRecommendation(prompt: String): Map<String, Any> {
        val rec = recommend(prompt)
        return mapOf(
            "negative_prompt" to rec.terms.joinToString(", "),
            "term_count" to rec.terms.size,
            "confidence" to rec.confidence,
            "intensity" to rec.intensity,
            "categories" to rec.categoryBreakdown.mapValues { it.value.size },
            "explanations" to rec.explanations.take(10)
        )
    }

    // ===== 私有方法 =====

    private data class SceneAnalysis(
        val keywords: List<String>,
        val terms: List<NegativeTerm>,
        val reason: String
    )

    private fun analyzeScene(prompt: String): List<SceneAnalysis> {
        val analyses = mutableListOf<SceneAnalysis>()

        // 人物场景
        if (containsKeywords(prompt, listOf("person", "man", "woman", "girl", "boy", "portrait", "selfie", "character"))) {
            analyses.add(SceneAnalysis(
                keywords = listOf("person", "man", "woman", "portrait"),
                terms = qualityTerms.filter { it.keyword in listOf("bad anatomy", "bad hands", "missing fingers", "extra digits", "poorly drawn face", "mutated hands") },
                reason = "人物场景 → 手部和面部质量"
            ))
        }

        // 动物场景
        if (containsKeywords(prompt, listOf("animal", "dog", "cat", "bird", "horse", "wildlife", "pet"))) {
            analyses.add(SceneAnalysis(
                keywords = listOf("animal"),
                terms = listOf(
                    NegativeTerm("deformed", Category.SUBJECT, 2, "动物变形", listOf("animal")),
                    NegativeTerm("distorted", Category.SUBJECT, 2, "扭曲", listOf("animal")),
                    NegativeTerm("wrong animal", Category.SUBJECT, 3, "错误动物", listOf("animal"))
                ),
                reason = "动物场景 → 形态问题"
            ))
        }

        // 风景场景
        if (containsKeywords(prompt, listOf("landscape", "scenery", "mountain", "ocean", "sky", "forest", "cityscape"))) {
            analyses.add(SceneAnalysis(
                keywords = listOf("landscape"),
                terms = compositionTerms.filter { it.keyword in listOf("cropped", "out of frame", "cut off", "asymmetric") },
                reason = "风景场景 → 构图问题"
            ))
        }

        // 文字场景
        if (containsKeywords(prompt, listOf("text", "word", "sign", "logo", "title", "poster", "book"))) {
            analyses.add(SceneAnalysis(
                keywords = listOf("text"),
                terms = listOf(
                    NegativeTerm("wrong text", Category.SUBJECT, 4, "错误文字", listOf("text")),
                    NegativeTerm("gibberish", Category.SUBJECT, 3, "乱码文字", listOf("text")),
                    NegativeTerm("garbled text", Category.SUBJECT, 3, "乱码", listOf("text"))
                ),
                reason = "文字场景 → 文字正确性"
            ))
        }

        // 动作场景
        if (containsKeywords(prompt, listOf("action", "running", "fighting", "dancing", "sport", "motion", "movement"))) {
            analyses.add(SceneAnalysis(
                keywords = listOf("action"),
                terms = qualityTerms.filter { it.keyword in listOf("bad anatomy", "bad proportions", "distorted", "extra limbs") },
                reason = "动作场景 → 动态姿势质量"
            ))
        }

        return analyses
    }

    private fun selectQualityTerms(intensity: Int): List<Pair<String, String>> {
        return when (intensity) {
            INTENSITY_LIGHT -> listOf(
                "low quality" to "去除低质量",
                "blurry" to "去除模糊",
                "worst quality" to "去除最差质量"
            )
            INTENSITY_MODERATE -> listOf(
                "low quality" to "去除低质量",
                "worst quality" to "去除最差质量",
                "bad anatomy" to "去除解剖错误",
                "bad hands" to "去除手部问题",
                "blurry" to "去除模糊",
                "distorted" to "去除扭曲",
                "deformed" to "去除畸形",
                "jpeg artifacts" to "去除JPEG伪影",
                "noise" to "去除噪点",
                "out of focus" to "去除脱焦"
            )
            INTENSITY_STRONG -> listOf(
                "low quality" to "去除低质量",
                "worst quality" to "去除最差质量",
                "bad anatomy" to "去除解剖错误",
                "bad hands" to "去除手部问题",
                "missing fingers" to "去除缺失手指",
                "extra digits" to "去除多余手指",
                "mutated hands" to "去除变异手部",
                "blurry" to "去除模糊",
                "distorted" to "去除扭曲",
                "deformed" to "去除畸形",
                "disfigured" to "去除毁容",
                "jpeg artifacts" to "去除JPEG伪影",
                "compression artifacts" to "去除压缩伪影",
                "noise" to "去除噪点",
                "artifacts" to "去除伪影"
            )
            INTENSITY_EXTREME -> listOf(
                "low quality" to "去除低质量",
                "worst quality" to "去除最差质量",
                "lowres" to "去除低分辨率",
                "bad anatomy" to "去除解剖错误",
                "bad hands" to "去除手部问题",
                "missing fingers" to "去除缺失手指",
                "extra digits" to "去除多余手指",
                "floating fingers" to "去除漂浮手指",
                "mutated hands" to "去除变异手部",
                "bad proportions" to "去除比例失调",
                "blurry" to "去除模糊",
                "distorted" to "去除扭曲",
                "deformed" to "去除畸形",
                "disfigured" to "去除毁容",
                "poorly drawn face" to "去除粗糙面部",
                "mutation" to "去除变异",
                "mutated" to "去除突变",
                "jpeg artifacts" to "去除JPEG伪影",
                "compression artifacts" to "去除压缩伪影",
                "artifacts" to "去除伪影",
                "noise" to "去除噪点",
                "out of focus" to "去除脱焦",
                "pixelated" to "去除像素化",
                "interpolated" to "去除插值",
                "duplicate" to "去除重复"
            )
            else -> listOf("low quality" to "去除低质量")
        }
    }

    private fun addPortraitTerms(
        selected: MutableList<String>,
        explanations: MutableList<String>,
        breakdown: MutableMap<Category, MutableList<String>>,
        intensity: Int
    ) {
        val terms = if (intensity >= INTENSITY_MODERATE) {
            listOf("missing fingers", "extra digits", "poorly drawn face", "multiple faces", "duplicate")
        } else {
            listOf("poorly drawn face")
        }
        terms.forEach { term ->
            if (!selected.contains(term)) {
                selected.add(term)
                explanations.add("人像专项优化: $term")
                val q: MutableList<String> = breakdown[Category.QUALITY] ?: mutableListOf<String>().also { breakdown[Category.QUALITY] = it }; q.add(term)
            }
        }
    }

    private fun addLandscapeTerms(
        selected: MutableList<String>,
        explanations: MutableList<String>,
        breakdown: MutableMap<Category, MutableList<String>>,
        intensity: Int
    ) {
        val terms = listOf("cropped", "out of frame", "asymmetric", "uneven")
        terms.forEach { term ->
            if (!selected.contains(term)) {
                selected.add(term)
                explanations.add("风景专项优化: $term")
                val c: MutableList<String> = breakdown[Category.COMPOSITION] ?: mutableListOf<String>().also { breakdown[Category.COMPOSITION] = it }; c.add(term)
            }
        }
    }

    private fun addArtStyleTerms(
        selected: MutableList<String>,
        explanations: MutableList<String>,
        breakdown: MutableMap<Category, MutableList<String>>,
        intensity: Int
    ) {
        // 艺术风格时通常不想要照片感
        if (!selected.contains("realistic")) {
            selected.add("realistic")
            explanations.add("避免照片感")
            val s1: MutableList<String> = breakdown[Category.STYLE] ?: mutableListOf<String>().also { breakdown[Category.STYLE] = it }; s1.add("realistic")
        }
        if (!selected.contains("photorealistic")) {
            selected.add("photorealistic")
            explanations.add("避免真实感")
            val s2: MutableList<String> = breakdown[Category.STYLE] ?: mutableListOf<String>().also { breakdown[Category.STYLE] = it }; s2.add("photorealistic")
        }
    }

    private fun addRealisticTerms(
        selected: MutableList<String>,
        explanations: MutableList<String>,
        breakdown: MutableMap<Category, MutableList<String>>,
        intensity: Int
    ) {
        // 真实感场景时通常不想要艺术风格
        if (!selected.contains("anime style")) {
            selected.add("anime style")
            explanations.add("避免动画风格")
            val s3: MutableList<String> = breakdown[Category.STYLE] ?: mutableListOf<String>().also { breakdown[Category.STYLE] = it }; s3.add("anime style")
        }
        if (!selected.contains("cartoon style")) {
            selected.add("cartoon style")
            explanations.add("避免卡通风格")
            val s4: MutableList<String> = breakdown[Category.STYLE] ?: mutableListOf<String>().also { breakdown[Category.STYLE] = it }; s4.add("cartoon style")
        }
        if (!selected.contains("illustration")) {
            selected.add("illustration")
            explanations.add("避免插画感")
            val s5: MutableList<String> = breakdown[Category.STYLE] ?: mutableListOf<String>().also { breakdown[Category.STYLE] = it }; s5.add("illustration")
        }
    }

    private fun addAnimalTerms(
        selected: MutableList<String>,
        explanations: MutableList<String>,
        breakdown: MutableMap<Category, MutableList<String>>,
        intensity: Int
    ) {
        val terms = listOf("deformed", "distorted", "wrong animal")
        terms.forEach { term ->
            if (!selected.contains(term)) {
                selected.add(term)
                explanations.add("动物专项优化: $term")
                val sub: MutableList<String> = breakdown[Category.SUBJECT] ?: mutableListOf<String>().also { breakdown[Category.SUBJECT] = it }; sub.add(term)
            }
        }
    }

    private fun calculateConfidence(prompt: String, termCount: Int, sceneMatchCount: Int): Float {
        var confidence = 0.5f

        // 提示词越长，置信度越高
        confidence += (prompt.length / 500f).coerceAtMost(0.2f)

        // 匹配场景越多，置信度越高
        confidence += (sceneMatchCount * 0.05f).coerceAtMost(0.2f)

        // 负提示词数量合理性
        confidence += when {
            termCount in 5..20 -> 0.1f
            termCount > 20 -> 0.05f
            else -> 0f
        }

        return confidence.coerceIn(0f, 1f)
    }

    private fun containsKeywords(text: String, keywords: List<String>): Boolean {
        return keywords.any { text.contains(it, ignoreCase = true) }
    }

    private fun loadLearnedTerms(): Set<String> {
        val saved = prefs.getString(KEY_LEARNED_TERMS, "") ?: ""
        return if (saved.isBlank()) emptySet()
        else saved.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    private fun saveLearnedTerms(terms: Set<String>) {
        prefs.edit().putString(KEY_LEARNED_TERMS, terms.joinToString(",")).apply()
    }

    fun setPreferredIntensity(intensity: Int) {
        _preferredIntensity.value = intensity.coerceIn(1, 4)
        prefs.edit().putInt(KEY_PREFERRED_INTENSITY, _preferredIntensity.value).apply()
    }

    fun release() {
        INSTANCE = null
    }
}
