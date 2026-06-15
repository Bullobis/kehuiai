package com.kehuiai.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 可绘AI v3.6 - 智能分辨率与宽高比顾问
 *
 * 核心功能：
 * 1. 根据场景推荐最佳宽高比
 * 2. 根据 VRAM/设备能力推荐分辨率
 * 3. 根据模型类型推荐分辨率
 * 4. 考虑纵横比语义（人像/风景/特写等）
 * 5. 热门分辨率历史学习
 */
class AspectRatioAdvisor(private val context: Context) {

    companion object {
        private const val TAG = "AspectRatioAdvisor"
        private const val PREFS_NAME = "aspect_ratio_prefs"
        private const val KEY_PREFERRED_RATIO = "preferred_ratio"
        private const val KEY_PREFERRED_RES = "preferred_res"

        @Volatile
        private var INSTANCE: AspectRatioAdvisor? = null

        fun getInstance(context: Context): AspectRatioAdvisor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AspectRatioAdvisor(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // ===== 宽高比配置 =====
    data class AspectRatio(
        val id: String,
        val name: String,
        val displayName: String,    // 例如 "1:1", "16:9"
        val widthRatio: Int,
        val heightRatio: Int,
        val description: String,   // 适合场景描述
        val typicalUse: List<String>,  // 典型用途
        val score: Float = 1.0f   // 推荐得分
    )

    // ===== 推荐结果 =====
    data class Recommendation(
        val aspectRatio: AspectRatio,
        val resolution: Pair<Int, Int>,
        val reason: String,
        val warnings: List<String> = emptyList(),
        val vramEstimateMb: Long,
        val confidence: Float
    )

    // ===== 预设宽高比 =====
    val aspectRatios = listOf(
        AspectRatio("1_1", "正方形", "1:1", 1, 1,
            "适合头像、图标、社交媒体头像、艺术品展示",
            listOf("头像", "图标", "NFT", "印章")),
        AspectRatio("4_3", "标准", "4:3", 4, 3,
            "适合一般照片、文档插图、桌面壁纸",
            listOf("照片", "插图", "壁纸")),
        AspectRatio("3_2", "照片", "3:2", 3, 2,
            "经典摄影比例，适合风景和人像",
            listOf("风景", "人像", "摄影")),
        AspectRatio("16_9", "宽屏", "16:9", 16, 9,
            "适合电影感场景、视频封面、桌面壁纸",
            listOf("视频封面", "电影感", "壁纸")),
        AspectRatio("21_9", "超宽", "21:9", 21, 9,
            "适合全景场景、电影画面、宽幅风景",
            listOf("全景", "电影", "超宽风景")),
        AspectRatio("9_16", "竖屏", "9:16", 9, 16,
            "适合手机壁纸、故事板、社交媒体故事",
            listOf("手机壁纸", "Stories", "小红书")),
        AspectRatio("2_3", "人像", "2:3", 2, 3,
            "适合全身人像、海报、杂志封面",
            listOf("人像", "海报", "杂志")),
        AspectRatio("3_4", "竖向", "3:4", 3, 4,
            "适合人像摄影、手机内容创作",
            listOf("人像", "创作")),
        AspectRatio("9_21", "超竖", "9:21", 9, 21,
            "适合超长竖图、手机故事、全屏内容",
            listOf("长图", "手机全屏")),
        AspectRatio("portrait_wide", "人像宽", "5:7", 5, 7,
            "适合人像特写、艺术人像",
            listOf("艺术人像", "写真"))
    )

    // ===== 最大分辨率配置（按VRAM） =====
    enum class VramTier(val maxPixels: Long, val maxDimension: Int, val description: String) {
        LOW(512 * 512, 512, "低配设备（< 4GB VRAM）"),
        MEDIUM(768 * 768, 768, "中配设备（4-6GB VRAM）"),
        HIGH(1024 * 1024, 1024, "高配设备（6-8GB VRAM）"),
        ULTRA(1024 * 1536, 1536, "旗舰设备（8GB+ VRAM）"),
        EXTREME(2048 * 2048, 2048, "顶级设备（12GB+ VRAM）")
    }

    // ===== 状态 =====
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _preferredRatio = MutableStateFlow(prefs.getString(KEY_PREFERRED_RATIO, "16_9") ?: "16_9")
    val preferredRatio: StateFlow<String> = _preferredRatio.asStateFlow()

    private val _preferredResolution = MutableStateFlow(prefs.getInt(KEY_PREFERRED_RES, 1024))
    val preferredResolution: StateFlow<Int> = _preferredResolution.asStateFlow()

    // ===== 主要接口 =====

    /**
     * 根据提示词分析场景，推荐宽高比
     */
    fun recommendForPrompt(
        prompt: String,
        vramTier: VramTier = VramTier.HIGH,
        isHiresFix: Boolean = false
    ): Recommendation {
        val lowerPrompt = prompt.lowercase()

        // 1. 场景分析
        val sceneScore = analyzeSceneScores(lowerPrompt)

        // 2. 获取候选宽高比
        val candidates = aspectRatios.map { ratio ->
            val score = calculateRatioScore(ratio, sceneScore, lowerPrompt)
            ratio to score
        }.sortedByDescending { it.second }

        // 3. 选择最佳
        val best = candidates.first()

        // 4. 计算推荐分辨率
        val maxDim = calculateMaxDimension(vramTier, isHiresFix)
        val resolution = calculateResolution(best.first, maxDim, vramTier)

        // 5. 生成原因
        val reason = buildReason(best.first, sceneScore, lowerPrompt)

        // 6. 检查警告
        val warnings = checkWarnings(best.first, resolution, vramTier, isHiresFix)

        return Recommendation(
            aspectRatio = best.first,
            resolution = resolution,
            reason = reason,
            warnings = warnings,
            vramEstimateMb = estimateVramUsage(resolution.first, resolution.second, isHiresFix),
            confidence = best.second
        )
    }

    /**
     * 根据类型推荐（简单入口）
     */
    fun recommendForType(
        type: ContentType,
        vramTier: VramTier = VramTier.HIGH
    ): Recommendation {
        val ratio = when (type) {
            ContentType.PORTRAIT -> aspectRatios.find { it.id == "2_3" } ?: aspectRatios.first()
            ContentType.LANDSCAPE -> aspectRatios.find { it.id == "16_9" } ?: aspectRatios.first()
            ContentType.SQUARE -> aspectRatios.find { it.id == "1_1" } ?: aspectRatios.first()
            ContentType.STORY -> aspectRatios.find { it.id == "9_16" } ?: aspectRatios.first()
            ContentType.POSTER -> aspectRatios.find { it.id == "2_3" } ?: aspectRatios.first()
            ContentType.ICON -> aspectRatios.find { it.id == "1_1" } ?: aspectRatios.first()
            ContentType.WALLPAPER -> aspectRatios.find { it.id == "16_9" } ?: aspectRatios.first()
            ContentType.PROFILE -> aspectRatios.find { it.id == "1_1" } ?: aspectRatios.first()
            ContentType.BANNER -> aspectRatios.find { it.id == "21_9" } ?: aspectRatios.first()
            ContentType.MOBILE_CONTENT -> aspectRatios.find { it.id == "9_16" } ?: aspectRatios.first()
        }

        val maxDim = calculateMaxDimension(vramTier, false)
        val resolution = calculateResolution(ratio, maxDim, vramTier)

        return Recommendation(
            aspectRatio = ratio,
            resolution = resolution,
            reason = "${ratio.name}（${ratio.displayName}）最适合${type.name.lowercase()}",
            vramEstimateMb = estimateVramUsage(resolution.first, resolution.second, false),
            confidence = 0.95f
        )
    }

    /**
     * 获取最常用的宽高比（基于历史）
     */
    fun getMostUsedRatio(): AspectRatio {
        val saved = prefs.getString("most_used_ratio", "16_9") ?: "16_9"
        return aspectRatios.find { it.id == saved } ?: aspectRatios.first()
    }

    /**
     * 学习用户偏好（记录使用）
     */
    fun recordUsage(ratioId: String, resolution: Int) {
        val editor = prefs.edit()
        editor.putString(KEY_PREFERRED_RATIO, ratioId)
        editor.putInt(KEY_PREFERRED_RES, resolution)
        _preferredRatio.value = ratioId
        _preferredResolution.value = resolution
        editor.apply()

        // 更新使用计数
        val count = prefs.getInt("ratio_count_$ratioId", 0) + 1
        editor.putInt("ratio_count_$ratioId", count)
        editor.apply()
    }

    /**
     * 获取所有宽高比及其推荐度
     */
    fun getAllRatiosWithScores(prompt: String): List<Pair<AspectRatio, Float>> {
        val lowerPrompt = prompt.lowercase()
        val sceneScore = analyzeSceneScores(lowerPrompt)
        return aspectRatios.map { ratio ->
            ratio to calculateRatioScore(ratio, sceneScore, lowerPrompt)
        }.sortedByDescending { it.second }
    }

    /**
     * 获取推荐分辨率列表
     */
    fun getRecommendedResolutions(vramTier: VramTier): List<Pair<Int, Int>> {
        val maxDim = calculateMaxDimension(vramTier, false)
        return listOf(
            maxDim / 2 * 2 to maxDim / 2 * 2,
            (maxDim * 3 / 4 / 2 * 2) to (maxDim * 4 / 3 / 2 * 2),
            maxDim / 2 * 2 to (maxDim * 3 / 2 / 2 * 2),
            (maxDim * 3 / 4 / 2 * 2) to (maxDim * 4 / 3 / 2 * 2),
            maxDim / 2 * 2 to maxDim / 2 * 2,
            maxDim to maxDim,
            maxDim to (maxDim * 2 / 3 / 2 * 2),
            (maxDim * 2 / 3 / 2 * 2) to maxDim
        ).filter { it.first >= 256 && it.second >= 256 }
    }

    // ===== 枚举类型 =====
    enum class ContentType {
        PORTRAIT,      // 人像
        LANDSCAPE,     // 风景
        SQUARE,        // 方形
        STORY,         // 故事/手机
        POSTER,        // 海报
        ICON,          // 图标
        WALLPAPER,     // 壁纸
        PROFILE,       // 头像
        BANNER,        // 横幅
        MOBILE_CONTENT // 手机内容
    }

    // ===== 私有方法 =====

    private data class SceneScores(
        val isPortrait: Boolean,
        val isLandscape: Boolean,
        val isSquare: Boolean,
        val isMobile: Boolean,
        val isFilm: Boolean,
        val isWide: Boolean,
        val isPoster: Boolean,
        val isArt: Boolean,
        val isPhoto: Boolean,
        val isIcon: Boolean
    )

    private fun analyzeSceneScores(prompt: String): SceneScores {
        return SceneScores(
            isPortrait = containsAny(prompt, listOf("portrait", "人像", "人物", "girl", "boy", "man", "woman", "selfie", "头像", "写真", "模特", "face", "full body", "全身")),
            isLandscape = containsAny(prompt, listOf("landscape", "风景", "nature", "mountain", "ocean", "sky", "forest", "sea", "scenery", "scenic", "outdoor")),
            isSquare = containsAny(prompt, listOf("square", "icon", "avatar", "头像", "stamp", "印章", "badge", "logo", "profile")),
            isMobile = containsAny(prompt, listOf("phone wallpaper", "mobile", "手机壁纸", "story", "stories", "小红书", "instagram story", "tiktok", "wechat")),
            isFilm = containsAny(prompt, listOf("cinematic", "film", "movie", "电影感", "cinema", "anamorphic", "cinematic lighting", "movie scene")),
            isWide = containsAny(prompt, listOf("panorama", "panoramic", "全景", "wide shot", "ultrawide", "超宽", "full scene", "epic")),
            isPoster = containsAny(prompt, listOf("poster", "海报", "cover", "杂志", "magazine", "flyer", "广告")),
            isArt = containsAny(prompt, listOf("art", "artwork", "painting", "illustration", "drawing", "artistic", "digital art", "艺术", "插画")),
            isPhoto = containsAny(prompt, listOf("photo", "photograph", "photorealistic", "realistic", "照片", "摄影", "actual photo")),
            isIcon = containsAny(prompt, listOf("icon", "emoji", "sticker", "logo", "badge", "button", "app icon", "图标", "表情"))
        )
    }

    private fun calculateRatioScore(
        ratio: AspectRatio,
        scene: SceneScores,
        prompt: String
    ): Float {
        var score = 0.5f

        // 横向风景场景
        if (scene.isLandscape && ratio.widthRatio > ratio.heightRatio) {
            score += 0.3f
            if (ratio.id == "16_9" || ratio.id == "3_2") score += 0.2f
        }

        // 竖向人像场景
        if (scene.isPortrait && ratio.heightRatio > ratio.widthRatio) {
            score += 0.3f
            if (ratio.id == "2_3" || ratio.id == "3_4") score += 0.2f
        }

        // 正方形场景
        if (scene.isSquare && ratio.id == "1_1") score += 0.5f

        // 手机/故事场景
        if (scene.isMobile && ratio.id == "9_16") score += 0.5f
        if (scene.isMobile && ratio.id == "9_21") score += 0.3f

        // 电影感
        if (scene.isFilm) {
            if (ratio.id == "21_9") score += 0.4f
            if (ratio.id == "16_9") score += 0.3f
        }

        // 全景
        if (scene.isWide && ratio.id == "21_9") score += 0.5f

        // 海报
        if (scene.isPoster && (ratio.id == "2_3" || ratio.id == "3_4")) score += 0.3f

        // 图标
        if ((scene.isIcon || scene.isSquare) && ratio.id == "1_1") score += 0.4f

        // 从提示词直接推断
        val lowerPrompt = prompt.lowercase()
        aspectRatios.forEach { r ->
            if (lowerPrompt.contains(r.displayName.lowercase()) ||
                lowerPrompt.contains(r.name.lowercase())) {
                if (r.id == ratio.id) score += 0.4f
            }
        }

        return score.coerceIn(0f, 1f)
    }

    private fun calculateMaxDimension(vramTier: VramTier, isHiresFix: Boolean): Int {
        val base = vramTier.maxDimension
        return if (isHiresFix) (base * 0.75f).toInt() else base
    }

    private fun calculateResolution(
        ratio: AspectRatio,
        maxDimension: Int,
        vramTier: VramTier
    ): Pair<Int, Int> {
        // 确保是 64 的倍数（SD 模型要求）
        fun align(value: Int): Int = (value / 64) * 64

        val gcd = gcd(ratio.widthRatio, ratio.heightRatio)
        val w = ratio.widthRatio / gcd
        val h = ratio.heightRatio / gcd

        // 根据最大尺寸和比例计算
        var width: Int
        var height: Int

        if (w >= h) {
            width = align(maxDimension.coerceAtMost(vramTier.maxDimension))
            height = align((width * h / w).toInt())
        } else {
            height = align(maxDimension.coerceAtMost(vramTier.maxDimension))
            width = align((height * w / h).toInt())
        }

        // 确保不超过 VRAM 限制
        val maxPixels = vramTier.maxPixels.toLong()
        if (width.toLong() * height > maxPixels) {
            val scale = kotlin.math.sqrt(maxPixels.toDouble() / (width * height))
            width = align((width * scale).toInt())
            height = align((height * scale).toInt())
        }

        return width.coerceAtLeast(256) to height.coerceAtLeast(256)
    }

    private fun buildReason(ratio: AspectRatio, scene: SceneScores, prompt: String): String {
        val reasons = mutableListOf<String>()

        if (scene.isPortrait && ratio.heightRatio > ratio.widthRatio) {
            reasons.add("检测到人像场景，竖向比例更合适")
        }
        if (scene.isLandscape && ratio.widthRatio > ratio.heightRatio) {
            reasons.add("检测到风景场景，横向比例更合适")
        }
        if (scene.isMobile) {
            reasons.add("检测到手机内容，竖屏比例最优")
        }
        if (scene.isFilm) {
            reasons.add("电影感场景推荐 ${ratio.displayName} 电影比例")
        }
        if (scene.isWide) {
            reasons.add("全景场景推荐超宽比例")
        }
        if (scene.isSquare) {
            reasons.add("方形内容最推荐 ${ratio.displayName}")
        }

        if (reasons.isEmpty()) {
            reasons.add("${ratio.displayName} - ${ratio.description}")
        }

        reasons.add("适合：${ratio.typicalUse.take(3).joinToString("、")}")

        return reasons.joinToString("；")
    }

    private fun checkWarnings(
        ratio: AspectRatio,
        resolution: Pair<Int, Int>,
        vramTier: VramTier,
        isHiresFix: Boolean
    ): List<String> {
        val warnings = mutableListOf<String>()

        val pixels = resolution.first.toLong() * resolution.second
        if (pixels > vramTier.maxPixels) {
            warnings.add("⚠️ 分辨率较高，可能需要更长的生成时间")
        }

        if (isHiresFix && pixels > vramTier.maxPixels * 0.7) {
            warnings.add("⚠️ 高分修复 + 高分辨率同时开启，内存压力较大")
        }

        if (ratio.id == "21_9" && resolution.first > 1024) {
            warnings.add("⚠️ 超宽比例在大分辨率下可能出现构图问题")
        }

        if (ratio.id == "9_21" && resolution.second > 1536) {
            warnings.add("⚠️ 超竖比例在大分辨率下可能超出显示范围")
        }

        return warnings
    }

    private fun estimateVramUsage(width: Int, height: Int, isHiresFix: Boolean): Long {
        // 粗略估算：每百万像素约需 100MB VRAM
        val basePixels = width.toLong() * height
        var estimate = basePixels * 100L / 1_000_000L  // MB

        if (isHiresFix) {
            // 高分修复约增加 50% 显存
            estimate = (estimate * 1.5f).toLong()
        }

        return estimate.coerceAtLeast(100L)
    }

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    private fun containsAny(text: String, keywords: List<String>): Boolean {
        return keywords.any { text.contains(it, ignoreCase = true) }
    }

    fun release() {
        INSTANCE = null
    }
}
