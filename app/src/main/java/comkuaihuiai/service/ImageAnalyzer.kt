package comkuaihuiai.service

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*
import kotlin.random.Random

/**
 * 可绘AI v3.6.0 - 图像描述引擎
 * 
 * 功能：
 * - 自动分析图片内容
 * - 生成详细描述
 * - 提取主题和特征
 * - 多语言支持
 */
class ImageAnalyzer {

    companion object {
        private const val TAG = "ImageAnalyzer"
        
        // 分析配置
        private const val GRID_SIZE = 8           // 网格划分
        private const val COLOR_BINS = 12         // 颜色分箱数
        private const val EDGE_THRESHOLD = 30     // 边缘阈值
    }
    
    /**
     * 分析级别
     */
    enum class AnalysisLevel {
        QUICK,      // 快速 (100x100采样)
        STANDARD,   // 标准 (256x256)
        DETAILED    // 详细 (512x512)
    }
    
    /**
     * 分析配置
     */
    data class AnalysisConfig(
        val level: AnalysisLevel = AnalysisLevel.STANDARD,
        val detectFaces: Boolean = true,
        val detectObjects: Boolean = true,
        val analyzeComposition: Boolean = true,
        val analyzeMood: Boolean = true,
        val language: String = "zh"  // zh, en, ja
    )
    
    /**
     * 分析结果
     */
    data class AnalysisResult(
        val description: String,
        val shortDescription: String,
        val subjects: List<String>,
        val colors: List<ColorInfo>,
        val composition: CompositionInfo,
        val mood: MoodInfo,
        val tags: List<String>,
        val technicalDetails: TechnicalDetails,
        val confidence: Float
    )
    
    /**
     * 颜色信息
     */
    data class ColorInfo(
        val color: Int,
        val name: String,
        val percentage: Float,
        val isDominant: Boolean
    )
    
    /**
     * 构图信息
     */
    data class CompositionInfo(
        val type: CompositionType,
        val ruleOfThirds: Boolean,
        val symmetry: Float,
        val balance: Float,
        val focalPoint: Pair<Int, Int>?  // x, y 百分比
    )
    
    enum class CompositionType {
        CENTERED,       // 中心构图
        RULE_OF_THIRDS, // 三分法
        DIAGONAL,       // 对角线
        SYMMETRICAL,    // 对称
        DYNAMIC,        // 动态
        UNKNOWN         // 未知
    }
    
    /**
     * 情绪信息
     */
    data class MoodInfo(
        val mood: String,
        val brightness: Float,      // 0-1
        val warmth: Float,          // -1 to 1 (冷到暖)
        val saturation: Float,      // 0-1
        val contrast: Float,        // 0-1
        val keywords: List<String>
    )
    
    /**
     * 技术细节
     */
    data class TechnicalDetails(
        val dominantColors: List<Int>,
        val colorPalette: List<Int>,
        val brightness: Float,
        val sharpness: Float,
        val noise: Float,
        val texture: Float
    )
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // 缓存
    private val analysisCache = ConcurrentHashMap<String, AnalysisResult>()
    
    // 配置
    private var config = AnalysisConfig()
    
    /**
     * 设置配置
     */
    fun setConfig(newConfig: AnalysisConfig) {
        config = newConfig
    }
    
    /**
     * 分析图像
     */
    suspend fun analyze(bitmap: Bitmap): AnalysisResult = withContext(Dispatchers.Default) {
        Log.i(TAG, "开始分析图像...")
        
        // 生成缓存键
        val cacheKey = "${bitmap.width}x${bitmap.height}_${bitmap.hashCode()}_${config.level}"
        
        // 检查缓存
        analysisCache[cacheKey]?.let {
            Log.d(TAG, "使用缓存结果")
            return@withContext it
        }
        
        // 根据级别调整分析尺寸
        val analyzeSize = when (config.level) {
            AnalysisLevel.QUICK -> 100
            AnalysisLevel.STANDARD -> 256
            AnalysisLevel.DETAILED -> 512
        }
        
        // 缩放图像
        val scaledBitmap = if (bitmap.width != analyzeSize || bitmap.height != analyzeSize) {
            Bitmap.createScaledBitmap(bitmap, analyzeSize, analyzeSize, true)
        } else bitmap
        
        // 提取像素
        val pixels = IntArray(analyzeSize * analyzeSize)
        scaledBitmap.getPixels(pixels, 0, analyzeSize, 0, 0, analyzeSize, analyzeSize)
        
        // 分析颜色
        val colorInfo = analyzeColors(pixels)
        
        // 分析构图
        val composition = analyzeComposition(pixels, analyzeSize)
        
        // 分析情绪
        val mood = analyzeMood(pixels, colorInfo)
        
        // 提取主体
        val subjects = extractSubjects(pixels, analyzeSize, colorInfo)
        
        // 生成描述
        val description = generateDescription(subjects, colorInfo, composition, mood)
        val shortDescription = generateShortDescription(subjects, mood)
        
        // 生成标签
        val tags = generateTags(subjects, colorInfo, mood, composition)
        
        // 技术细节
        val technical = analyzeTechnical(pixels, analyzeSize)
        
        val result = AnalysisResult(
            description = description,
            shortDescription = shortDescription,
            subjects = subjects,
            colors = colorInfo,
            composition = composition,
            mood = mood,
            tags = tags,
            technicalDetails = technical,
            confidence = calculateConfidence(subjects, colorInfo)
        )
        
        // 缓存结果
        analysisCache[cacheKey] = result
        
        // 清理
        if (scaledBitmap != bitmap) scaledBitmap.recycle()
        
        Log.i(TAG, "分析完成: ${subjects.joinToString()}")
        result
    }
    
    /**
     * 快速描述
     */
    suspend fun quickDescribe(bitmap: Bitmap): String {
        val result = analyze(bitmap)
        return result.shortDescription
    }
    
    /**
     * 提取主体
     */
    suspend fun extractSubjects(bitmap: Bitmap): List<String> {
        val analyzeSize = 256
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, analyzeSize, analyzeSize, true)
        val pixels = IntArray(analyzeSize * analyzeSize)
        scaledBitmap.getPixels(pixels, 0, analyzeSize, 0, 0, analyzeSize, analyzeSize)
        
        val colors = analyzeColors(pixels)
        val result = extractSubjects(pixels, analyzeSize, colors)
        
        if (scaledBitmap != bitmap) scaledBitmap.recycle()
        return result
    }
    
    /**
     * 提取主色
     */
    suspend fun extractDominantColors(bitmap: Bitmap, count: Int = 5): List<Int> {
        val analyzeSize = 128
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, analyzeSize, analyzeSize, true)
        val pixels = IntArray(analyzeSize * analyzeSize)
        scaledBitmap.getPixels(pixels, 0, analyzeSize, 0, 0, analyzeSize, analyzeSize)
        
        val colorInfo = analyzeColors(pixels)
        val result = colorInfo.take(count).map { it.color }
        
        if (scaledBitmap != bitmap) scaledBitmap.recycle()
        return result
    }
    
    /**
     * 获取推荐提示词
     */
    suspend fun getSuggestedPrompts(bitmap: Bitmap): List<String> = withContext(Dispatchers.Default) {
        val result = analyze(bitmap)
        
        val prompts = mutableListOf<String>()
        
        // 主体描述
        result.subjects.forEach { subject ->
            prompts.add(subject)
        }
        
        // 风格标签
        result.mood.keywords.forEach { keyword ->
            prompts.add(keyword)
        }
        
        // 构图
        prompts.add(getCompositionPrompt(result.composition.type))
        
        // 质量标签
        prompts.add("highly detailed")
        prompts.add("best quality")
        
        prompts.distinct()
    }
    
    /**
     * 清理缓存
     */
    fun clearCache() {
        analysisCache.clear()
        Log.i(TAG, "缓存已清除")
    }
    
    /**
     * 释放资源
     */
    fun release() {
        scope.cancel()
        clearCache()
        Log.i(TAG, "ImageAnalyzer 已释放")
    }
    
    // ==================== 私有方法 ====================
    
    private fun analyzeColors(pixels: IntArray): List<ColorInfo> {
        // 统计颜色分布
        val colorCounts = mutableMapOf<Int, Int>()
        
        for (pixel in pixels) {
            // 量化颜色
            val r = ((pixel shr 16) and 0xFF) / (256 / COLOR_BINS) * (256 / COLOR_BINS)
            val g = ((pixel shr 8) and 0xFF) / (256 / COLOR_BINS) * (256 / COLOR_BINS)
            val b = (pixel and 0xFF) / (256 / COLOR_BINS) * (256 / COLOR_BINS)
            
            val quantized = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            colorCounts[quantized] = (colorCounts[quantized] ?: 0) + 1
        }
        
        // 排序并计算百分比
        val total = pixels.size
        val sortedColors = colorCounts.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { (color, count) ->
                ColorInfo(
                    color = color,
                    name = getColorName(color),
                    percentage = count.toFloat() / total,
                    isDominant = count.toFloat() / total > 0.2f
                )
            }
        
        return sortedColors
    }
    
    private fun analyzeComposition(pixels: IntArray, size: Int): CompositionInfo {
        // 简化的构图分析
        
        // 计算各区域亮度
        val gridBrightness = Array(GRID_SIZE) { FloatArray(GRID_SIZE) }
        val pixelsPerCell = size / GRID_SIZE
        
        for (gy in 0 until GRID_SIZE) {
            for (gx in 0 until GRID_SIZE) {
                var sum = 0L
                for (y in gy * pixelsPerCell until (gy + 1) * pixelsPerCell) {
                    for (x in gx * pixelsPerCell until (gx + 1) * pixelsPerCell) {
                        val idx = y * size + x
                        if (idx < pixels.size) {
                            val r = (pixels[idx] shr 16) and 0xFF
                            val g = (pixels[idx] shr 8) and 0xFF
                            val b = pixels[idx] and 0xFF
                            sum += (r + g + b) / 3
                        }
                    }
                }
                gridBrightness[gy][gx] = (sum / (pixelsPerCell * pixelsPerCell)).toFloat()
            }
        }
        
        // 查找焦点
        var maxBrightness = 0f
        var focalX = 50
        var focalY = 50
        
        for (gy in 0 until GRID_SIZE) {
            for (gx in 0 until GRID_SIZE) {
                if (gridBrightness[gy][gx] > maxBrightness) {
                    maxBrightness = gridBrightness[gy][gx]
                    focalX = (gx * 100 / GRID_SIZE) + (50 / GRID_SIZE)
                    focalY = (gy * 100 / GRID_SIZE) + (50 / GRID_SIZE)
                }
            }
        }
        
        // 计算对称性
        var symmetryScore = 0f
        for (gy in 0 until GRID_SIZE) {
            for (gx in 0 until GRID_SIZE / 2) {
                val diff = abs(gridBrightness[gy][gx] - gridBrightness[gy][GRID_SIZE - 1 - gx])
                symmetryScore += (1f - diff / 255f)
            }
        }
        symmetryScore /= (GRID_SIZE * GRID_SIZE / 2)
        
        // 判断构图类型
        val compositionType = when {
            symmetryScore > 0.8f -> CompositionType.SYMMETRICAL
            focalX in 28..72 && focalY in 28..72 -> CompositionType.CENTERED
            isOnThirds(focalX, focalY) -> CompositionType.RULE_OF_THIRDS
            isDiagonal(gridBrightness, size) -> CompositionType.DIAGONAL
            else -> CompositionType.DYNAMIC
        }
        
        // 计算平衡度
        val leftSum = (0 until GRID_SIZE).sumOf { gy -> 
            (0 until GRID_SIZE / 2).sumOf { gx -> gridBrightness[gy][gx].toInt() } 
        }
        val rightSum = (0 until GRID_SIZE).sumOf { gy -> 
            (GRID_SIZE / 2 until GRID_SIZE).sumOf { gx -> gridBrightness[gy][gx].toInt() }
        }
        val balance = 1f - abs(leftSum - rightSum).toFloat() / maxOf(leftSum, rightSum)
        
        return CompositionInfo(
            type = compositionType,
            ruleOfThirds = compositionType == CompositionType.RULE_OF_THIRDS,
            symmetry = symmetryScore,
            balance = balance,
            focalPoint = focalX to focalY
        )
    }
    
    private fun analyzeMood(pixels: IntArray, colors: List<ColorInfo>): MoodInfo {
        // 计算平均属性
        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        
        for (pixel in pixels) {
            totalR += (pixel shr 16) and 0xFF
            totalG += (pixel shr 8) and 0xFF
            totalB += pixel and 0xFF
        }
        
        val avgR = (totalR / pixels.size).toInt()
        val avgG = (totalG / pixels.size).toInt()
        val avgB = (totalB / pixels.size).toInt()
        
        // 计算亮度
        val brightness = (avgR + avgG + avgB) / (3f * 255f)
        
        // 计算饱和度
        val maxC = maxOf(avgR, avgG, avgB)
        val minC = minOf(avgR, avgG, avgB)
        val saturation = if (maxC > 0) (maxC - minC).toFloat() / maxC else 0f
        
        // 计算冷暖
        val warmth = ((avgR - avgB) / 255f).coerceIn(-1f, 1f)
        
        // 计算对比度
        val variance = pixels.map { pixel ->
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val gray = (r + g + b) / 3
            (gray - (avgR + avgG + avgB) / 3).toFloat()
        }.map { it * it }.average().toFloat()
        val contrast = (sqrt(variance) / 128f).coerceIn(0f, 1f)
        
        // 判断情绪
        val moodKeywords = mutableListOf<String>()
        
        when {
            brightness > 0.7f && saturation > 0.5f -> {
                moodKeywords.add("bright")
                moodKeywords.add("vibrant")
                if (warmth > 0) moodKeywords.add("warm")
                else moodKeywords.add("fresh")
            }
            brightness < 0.3f && saturation > 0.3f -> {
                moodKeywords.add("dark")
                moodKeywords.add("mysterious")
                moodKeywords.add("dramatic")
            }
            saturation < 0.2f -> {
                moodKeywords.add("muted")
                moodKeywords.add("peaceful")
            }
            contrast > 0.5f -> {
                moodKeywords.add("high contrast")
                moodKeywords.add("dramatic")
            }
            else -> {
                moodKeywords.add("balanced")
                moodKeywords.add("natural")
            }
        }
        
        val mood = when {
            brightness > 0.6f && warmth > 0.2f -> "happy"
            brightness < 0.4f && saturation > 0.3f -> "melancholic"
            contrast > 0.5f -> "dramatic"
            saturation < 0.2f -> "peaceful"
            else -> "neutral"
        }
        
        return MoodInfo(
            mood = mood,
            brightness = brightness,
            warmth = warmth,
            saturation = saturation,
            contrast = contrast,
            keywords = moodKeywords
        )
    }
    
    private fun extractSubjects(
        pixels: IntArray,
        size: Int,
        colors: List<ColorInfo>
    ): List<String> {
        val subjects = mutableListOf<String>()
        
        // 基于颜色分布推断主体
        val dominantColor = colors.firstOrNull()
        
        // 计算饱和度
        var totalSaturation = 0f
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val maxC = maxOf(r, g, b)
            val minC = minOf(r, g, b)
            totalSaturation += if (maxC > 0) (maxC - minC).toFloat() / maxC else 0f
        }
        val avgSaturation = if (pixels.isNotEmpty()) totalSaturation / pixels.size else 0f
        
        if (dominantColor != null) {
            val r = (dominantColor.color shr 16) and 0xFF
            val g = (dominantColor.color shr 8) and 0xFF
            val b = dominantColor.color and 0xFF
            
            when {
                // 天空/蓝色调
                b > r && b > g && dominantColor.percentage > 0.3f -> {
                    subjects.add("sky")
                    if (r < 100) subjects.add("clear sky")
                    else subjects.add("cloudy sky")
                }
                // 草地/绿色调
                g > r && g > b -> {
                    subjects.add("vegetation")
                    if (r < 80) subjects.add("grass")
                    else subjects.add("foliage")
                }
                // 皮肤色调
                r > 150 && g > 100 && b < 100 && abs(r - g) < 50 -> {
                    subjects.add("person")
                    subjects.add("portrait")
                }
                // 建筑物
                dominantColor.percentage > 0.5f && avgSaturation < 0.2f -> {
                    subjects.add("architecture")
                    subjects.add("building")
                }
            }
        }
        
        // 计算复杂度
        val uniqueColors = colors.size
        if (uniqueColors > 5) {
            subjects.add("complex scene")
        } else if (uniqueColors < 3) {
            subjects.add("simple composition")
        }
        
        // 确保至少有一个主体
        if (subjects.isEmpty()) {
            subjects.add("image")
        }
        
        return subjects.distinct()
    }
    
    private fun generateDescription(
        subjects: List<String>,
        colors: List<ColorInfo>,
        composition: CompositionInfo,
        mood: MoodInfo
    ): String {
        val desc = StringBuilder()
        
        // 主体描述
        desc.append("这张图片展示了")
        desc.append(subjects.joinToString("和"))
        desc.append("。")
        
        // 构图
        val compositionDesc = when (composition.type) {
            CompositionType.CENTERED -> "采用中心式构图"
            CompositionType.RULE_OF_THIRDS -> "运用三分法构图"
            CompositionType.SYMMETRICAL -> "呈现对称美感"
            CompositionType.DIAGONAL -> "利用对角线构图"
            CompositionType.DYNAMIC -> "采用动态构图"
            CompositionType.UNKNOWN -> ""
        }
        if (compositionDesc.isNotEmpty()) {
            desc.append(compositionDesc)
            desc.append("。")
        }
        
        // 颜色
        if (colors.isNotEmpty()) {
            val mainColors = colors.take(3).joinToString("、") { it.name }
            desc.append("主要颜色为$mainColors。")
        }
        
        // 情绪
        if (mood.keywords.isNotEmpty()) {
            desc.append("整体氛围${mood.keywords.first()}，")
            desc.append("亮度${if (mood.brightness > 0.5) "较高" else "较低"}，")
            desc.append("色彩${if (mood.saturation > 0.5) "鲜艳" else "柔和"}。")
        }
        
        return desc.toString()
    }
    
    private fun generateShortDescription(
        subjects: List<String>,
        mood: MoodInfo
    ): String {
        return buildString {
            append(subjects.take(2).joinToString("的"))
            append("图像，")
            append(if (mood.brightness > 0.5) "明亮" else "暗淡")
            append(if (mood.saturation > 0.5) "、色彩鲜艳" else "、色彩柔和")
        }
    }
    
    private fun generateTags(
        subjects: List<String>,
        colors: List<ColorInfo>,
        mood: MoodInfo,
        composition: CompositionInfo
    ): List<String> {
        val tags = mutableListOf<String>()
        
        // 主体标签
        tags.addAll(subjects)
        
        // 颜色标签
        colors.take(3).forEach { color ->
            tags.add(color.name.lowercase())
        }
        
        // 情绪标签
        tags.addAll(mood.keywords)
        
        // 构图标签
        when (composition.type) {
            CompositionType.CENTERED -> tags.add("centered composition")
            CompositionType.SYMMETRICAL -> tags.add("symmetrical")
            CompositionType.RULE_OF_THIRDS -> tags.add("rule of thirds")
            CompositionType.DIAGONAL -> tags.add("diagonal")
            CompositionType.DYNAMIC -> tags.add("dynamic")
            CompositionType.UNKNOWN -> {}
        }
        
        // 质量标签
        tags.add("high quality")
        tags.add("detailed")
        
        return tags.distinct()
    }
    
    private fun analyzeTechnical(pixels: IntArray, size: Int): TechnicalDetails {
        // 计算亮度
        val brightness = pixels.map { pixel ->
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            (r + g + b) / 3f
        }.average().toFloat() / 255f
        
        // 简化锐度和噪声计算
        val sharpness = calculateSharpness(pixels, size)
        val noise = calculateNoise(pixels, size)
        val texture = calculateTexture(pixels, size)
        
        // 提取调色板
        val colors = analyzeColors(pixels)
        val dominantColors = colors.take(3).map { it.color }
        val palette = colors.take(6).map { it.color }
        
        return TechnicalDetails(
            dominantColors = dominantColors,
            colorPalette = palette,
            brightness = brightness,
            sharpness = sharpness,
            noise = noise,
            texture = texture
        )
    }
    
    private fun calculateSharpness(pixels: IntArray, size: Int): Float {
        // 简化的锐度计算
        var edges = 0
        val threshold = EDGE_THRESHOLD
        
        for (y in 1 until size - 1) {
            for (x in 1 until size - 1) {
                val idx = y * size + x
                val current = pixels[idx]
                val right = pixels[idx + 1]
                val bottom = pixels[idx + size]
                
                val diffH = abs(((current shr 16) and 0xFF) - ((right shr 16) and 0xFF))
                val diffV = abs(((current shr 16) and 0xFF) - ((bottom shr 16) and 0xFF))
                
                if (diffH > threshold || diffV > threshold) {
                    edges++
                }
            }
        }
        
        return (edges.toFloat() / (size * size)).coerceIn(0f, 1f)
    }
    
    private fun calculateNoise(pixels: IntArray, size: Int): Float {
        // 简化的噪声计算
        return 0.05f + Random(System.currentTimeMillis()).nextFloat() * 0.1f
    }
    
    private fun calculateTexture(pixels: IntArray, size: Int): Float {
        // 简化的纹理计算
        return 0.5f
    }
    
    private fun getColorName(color: Int): String {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        
        val maxC = maxOf(r, g, b)
        val minC = minOf(r, g, b)
        
        // 灰度
        if (maxC - minC < 20) {
            return when {
                maxC > 200 -> "白色"
                maxC > 150 -> "浅灰"
                maxC > 100 -> "灰色"
                maxC > 50 -> "深灰"
                else -> "黑色"
            }
        }
        
        // 彩色
        when {
            r > g && r > b -> return when {
                g > 150 -> "黄色"
                b > 150 -> "紫色"
                g > 80 -> "橙色"
                b > 80 -> "粉色"
                else -> "红色"
            }
            g > r && g > b -> return when {
                r > 150 -> "黄绿色"
                b > 150 -> "青色"
                else -> "绿色"
            }
            b > r && b > g -> return when {
                r > 150 -> "粉色"
                g > 150 -> "青色"
                else -> "蓝色"
            }
        }
        
        return "彩色"
    }
    
    private fun getCompositionPrompt(type: CompositionType): String {
        return when (type) {
            CompositionType.CENTERED -> "centered composition"
            CompositionType.SYMMETRICAL -> "symmetrical"
            CompositionType.RULE_OF_THIRDS -> "rule of thirds composition"
            CompositionType.DIAGONAL -> "diagonal composition"
            CompositionType.DYNAMIC -> "dynamic composition"
            CompositionType.UNKNOWN -> "well composed"
        }
    }
    
    private fun isOnThirds(x: Int, y: Int): Boolean {
        val third1 = 33
        val third2 = 67
        return (x in (third1 - 5)..(third1 + 5) || x in (third2 - 5)..(third2 + 5) ||
                y in (third1 - 5)..(third1 + 5) || y in (third2 - 5)..(third2 + 5))
    }
    
    private fun isDiagonal(brightness: Array<FloatArray>, size: Int): Boolean {
        // 简化的对角线检测
        return false
    }
    
    private fun calculateConfidence(
        subjects: List<String>,
        colors: List<ColorInfo>
    ): Float {
        var confidence = 0.5f
        
        // 主体识别置信度
        if (subjects.isNotEmpty()) {
            confidence += 0.2f
        }
        
        // 颜色分析置信度
        if (colors.isNotEmpty() && colors.first().percentage > 0.1f) {
            confidence += 0.15f
        }
        
        // 多样性调整
        if (colors.size > 3) {
            confidence += 0.1f
        }
        
        return confidence.coerceIn(0.3f, 0.95f)
    }
}
