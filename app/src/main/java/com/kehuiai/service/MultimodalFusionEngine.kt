package com.kehuiai.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*
import kotlin.random.Random

/**
 * 可绘AI v3.6.0 - 多模态融合引擎
 * 
 * 功能：
 * - 文本+图像+风格联合推理
 * - 智能内容分析
 * - 跨模态特征提取
 * - 创意融合生成
 */
class MultimodalFusionEngine(private val context: Context) {

    companion object {
        private const val TAG = "MultimodalFusion"
        
        // 融合模式
        const val FUSION_BLEND = 0          // 简单混合
        const val FUSION_INTERLEAVE = 1     // 交错融合
        const val FUSION_ATTENTION = 2     // 注意力融合
        const val FUSION_STYLE_TRANSFER = 3 // 风格迁移融合
        
        // 特征维度
        private const val TEXT_FEATURE_DIM = 768
        private const val IMAGE_FEATURE_DIM = 512
        private const val STYLE_FEATURE_DIM = 256
    }
    
    /**
     * 多模态输入
     */
    data class MultimodalInput(
        val text: String? = null,
        val image: Bitmap? = null,
        val style: String? = null,
        val strength: Float = 0.5f  // 各模态融合强度
    )
    
    /**
     * 融合配置
     */
    data class FusionConfig(
        val mode: Int = FUSION_ATTENTION,
        val textWeight: Float = 0.4f,
        val imageWeight: Float = 0.4f,
        val styleWeight: Float = 0.2f,
        val crossAttentionLayers: Int = 12,
        val temperature: Float = 0.8f,
        val topK: Int = 50
    )
    
    /**
     * 融合特征
     */
    data class FusionFeatures(
        val textFeatures: FloatArray,
        val imageFeatures: FloatArray,
        val styleFeatures: FloatArray,
        val fusedFeatures: FloatArray,
        val confidence: Float
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FusionFeatures) return false
            return textFeatures.contentEquals(other.textFeatures) &&
                   imageFeatures.contentEquals(other.imageFeatures) &&
                   styleFeatures.contentEquals(other.styleFeatures) &&
                   fusedFeatures.contentEquals(other.fusedFeatures)
        }
        
        override fun hashCode(): Int {
            var result = textFeatures.contentHashCode()
            result = 31 * result + imageFeatures.contentHashCode()
            result = 31 * result + styleFeatures.contentHashCode()
            result = 31 * result + fusedFeatures.contentHashCode()
            return result
        }
    }
    
    /**
     * 生成结果
     */
    sealed class FusionResult {
        data class Progress(val percent: Int, val message: String) : FusionResult()
        data class Success(
            val output: Bitmap,
            val features: FusionFeatures,
            val metadata: Map<String, Any>
        ) : FusionResult()
        data class Error(val message: String) : FusionResult()
    }
    
    /**
     * 内容分析
     */
    data class ContentAnalysis(
        val dominantColors: List<Int>,
        val sceneType: String,
        val objectLabels: List<String>,
        val mood: String,
        val style: String,
        val composition: String
    )
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val analysisCache = ConcurrentHashMap<String, ContentAnalysis>()
    private val featureCache = ConcurrentHashMap<String, FloatArray>()
    
    private val isProcessing = AtomicBoolean(false)
    
    private val _progress = MutableSharedFlow<FusionResult>(extraBufferCapacity = 64)
    val progress: SharedFlow<FusionResult> = _progress.asSharedFlow()
    
    /**
     * 分析图像内容
     */
    suspend fun analyzeImage(bitmap: Bitmap): ContentAnalysis = withContext(Dispatchers.Default) {
        // 生成缓存键
        val cacheKey = "${bitmap.width}x${bitmap.height}_${bitmap.hashCode()}"
        
        // 检查缓存
        analysisCache[cacheKey]?.let { return@withContext it }
        
        Log.i(TAG, "分析图像内容...")
        
        // 分析颜色
        val colors = analyzeDominantColors(bitmap)
        
        // 分析场景
        val sceneType = classifyScene(bitmap)
        
        // 识别对象
        val objects = detectObjects(bitmap)
        
        // 判断情绪
        val mood = analyzeMood(bitmap, colors)
        
        // 判断风格
        val style = analyzeStyle(bitmap)
        
        // 分析构图
        val composition = analyzeComposition(bitmap)
        
        val analysis = ContentAnalysis(
            dominantColors = colors,
            sceneType = sceneType,
            objectLabels = objects,
            mood = mood,
            style = style,
            composition = composition
        )
        
        analysisCache[cacheKey] = analysis
        analysis
    }
    
    /**
     * 提取文本特征
     */
    suspend fun extractTextFeatures(text: String): FloatArray = withContext(Dispatchers.Default) {
        // 缓存键
        val cacheKey = "text_${text.hashCode()}"
        
        featureCache[cacheKey]?.let { return@withContext it }
        
        Log.i(TAG, "提取文本特征: ${text.take(50)}...")
        
        // 简单的词嵌入模拟
        val words = text.lowercase().split(Regex("[\\s,.!?]+")).filter { it.isNotEmpty() }
        val features = FloatArray(TEXT_FEATURE_DIM)
        
        words.forEachIndexed { index, word ->
            word.toCharArray().forEachIndexed { charIndex, char ->
                val idx = (char.code * (index + 1) + charIndex) % TEXT_FEATURE_DIM
                features[idx] += char.code.toFloat() / 255f
            }
        }
        
        // L2 归一化
        val norm = sqrt(features.map { it * it }.sum())
        if (norm > 0) {
            for (i in features.indices) {
                features[i] /= norm
            }
        }
        
        featureCache[cacheKey] = features
        features
    }
    
    /**
     * 提取图像特征
     */
    suspend fun extractImageFeatures(bitmap: Bitmap): FloatArray = withContext(Dispatchers.Default) {
        val cacheKey = "img_${bitmap.width}x${bitmap.height}_${bitmap.hashCode()}"
        
        featureCache[cacheKey]?.let { return@withContext it }
        
        Log.i(TAG, "提取图像特征...")
        
        // 简化特征提取：使用颜色直方图 + 边缘密度
        val features = FloatArray(IMAGE_FEATURE_DIM)
        
        // 缩小图像以加快处理
        val smallBitmap = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
        val pixels = IntArray(32 * 32)
        smallBitmap.getPixels(pixels, 0, 32, 0, 0, 32, 32)
        
        // 颜色直方图 (RGB 各 64 bins)
        val histR = IntArray(64)
        val histG = IntArray(64)
        val histB = IntArray(64)
        
        pixels.forEach { pixel ->
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            histR[r / 4]++
            histG[g / 4]++
            histB[b / 4]++
        }
        
        // 转换为特征 (前 192 维)
        for (i in 0 until 64) {
            features[i] = histR[i].toFloat() / pixels.size
            features[64 + i] = histG[i].toFloat() / pixels.size
            features[128 + i] = histB[i].toFloat() / pixels.size
        }
        
        // 边缘密度 (采样)
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val idx = 192 + y * 8 + x
                if (idx < IMAGE_FEATURE_DIM) {
                    val sx = x * 4
                    val sy = y * 4
                    if (sx + 4 < 32 && sy + 4 < 32) {
                        val edgeDensity = calculateEdgeDensity(pixels, sx, sy, 4)
                        features[idx] = edgeDensity
                    }
                }
            }
        }
        
        // 归一化
        val norm = sqrt(features.map { it * it }.sum())
        if (norm > 0) {
            for (i in features.indices) {
                features[i] /= norm
            }
        }
        
        if (smallBitmap != bitmap) smallBitmap.recycle()
        featureCache[cacheKey] = features
        features
    }
    
    /**
     * 提取风格特征
     */
    suspend fun extractStyleFeatures(styleName: String): FloatArray = withContext(Dispatchers.Default) {
        val cacheKey = "style_$styleName"
        
        featureCache[cacheKey]?.let { return@withContext it }
        
        Log.i(TAG, "提取风格特征: $styleName")
        
        // 预定义风格特征
        val features = when (styleName.lowercase()) {
            "anime", "动漫" -> generateStyleFeatures(0.8f, 0.2f, 0.6f, 0.9f)
            "realistic", "写实" -> generateStyleFeatures(0.5f, 0.8f, 0.9f, 0.3f)
            "oil painting", "油画" -> generateStyleFeatures(0.9f, 0.3f, 0.4f, 0.7f)
            "watercolor", "水彩" -> generateStyleFeatures(0.6f, 0.4f, 0.5f, 0.8f)
            "sketch", "素描" -> generateStyleFeatures(0.3f, 0.7f, 0.8f, 0.5f)
            "pixel art", "像素" -> generateStyleFeatures(0.2f, 0.5f, 0.3f, 0.4f)
            "cyberpunk", "赛博朋克" -> generateStyleFeatures(0.7f, 0.6f, 0.9f, 0.8f)
            "fantasy", "奇幻" -> generateStyleFeatures(0.8f, 0.4f, 0.7f, 0.9f)
            else -> generateStyleFeatures(0.5f, 0.5f, 0.5f, 0.5f)
        }
        
        featureCache[cacheKey] = features
        features
    }
    
    /**
     * 多模态融合
     */
    suspend fun fuse(
        input: MultimodalInput,
        config: FusionConfig = FusionConfig()
    ): Flow<FusionResult> = flow {
        try {
            if (isProcessing.get()) {
                emit(FusionResult.Error("引擎正忙"))
                return@flow
            }
            isProcessing.set(true)
            
            emit(FusionResult.Progress(0, "开始融合处理..."))
            
            // 1. 提取各模态特征
            emit(FusionResult.Progress(10, "提取文本特征..."))
            val textFeatures = input.text?.let { extractTextFeatures(it) } 
                ?: FloatArray(TEXT_FEATURE_DIM)
            
            emit(FusionResult.Progress(30, "提取图像特征..."))
            val imageFeatures = input.image?.let { extractImageFeatures(it) }
                ?: FloatArray(IMAGE_FEATURE_DIM)
            
            emit(FusionResult.Progress(50, "提取风格特征..."))
            val styleFeatures = input.style?.let { extractStyleFeatures(it) }
                ?: FloatArray(STYLE_FEATURE_DIM)
            
            // 2. 特征融合
            emit(FusionResult.Progress(60, "融合特征..."))
            val fusedFeatures = when (config.mode) {
                FUSION_BLEND -> blendFeatures(textFeatures, imageFeatures, styleFeatures, config)
                FUSION_INTERLEAVE -> interleaveFeatures(textFeatures, imageFeatures, styleFeatures, config)
                FUSION_ATTENTION -> attentionFusion(textFeatures, imageFeatures, styleFeatures, config)
                FUSION_STYLE_TRANSFER -> styleTransferFusion(imageFeatures, styleFeatures, config)
                else -> blendFeatures(textFeatures, imageFeatures, styleFeatures, config)
            }
            
            // 3. 生成结果
            emit(FusionResult.Progress(80, "生成图像..."))
            val output = generateFromFeatures(fusedFeatures, input, config)
            
            // 4. 构建完整特征
            val fusionFeatures = FusionFeatures(
                textFeatures = textFeatures,
                imageFeatures = imageFeatures,
                styleFeatures = styleFeatures,
                fusedFeatures = fusedFeatures,
                confidence = calculateConfidence(fusedFeatures)
            )
            
            emit(FusionResult.Progress(100, "完成"))
            
            emit(FusionResult.Success(
                output = output,
                features = fusionFeatures,
                metadata = mapOf(
                    "mode" to config.mode,
                    "temperature" to config.temperature,
                    "confidence" to fusionFeatures.confidence
                )
            ))
            
        } catch (e: Exception) {
            Log.e(TAG, "融合失败: ${e.message}")
            emit(FusionResult.Error(e.message ?: "未知错误"))
        } finally {
            isProcessing.set(false)
        }
    }
    
    /**
     * 图像到图像融合
     */
    suspend fun img2imgFusion(
        source: Bitmap,
        target: Bitmap,
        strength: Float = 0.5f
    ): Flow<FusionResult> = flow {
        try {
            emit(FusionResult.Progress(0, "分析源图像..."))
            val sourceFeatures = extractImageFeatures(source)
            
            emit(FusionResult.Progress(30, "分析目标图像..."))
            val targetFeatures = extractImageFeatures(target)
            
            emit(FusionResult.Progress(50, "混合特征..."))
            val fused = FloatArray(minOf(sourceFeatures.size, targetFeatures.size))
            for (i in fused.indices) {
                fused[i] = sourceFeatures[i] * (1 - strength) + targetFeatures[i] * strength
            }
            
            emit(FusionResult.Progress(80, "生成结果..."))
            val output = generateFromFeatures(fused, MultimodalInput(image = source), FusionConfig())
            
            emit(FusionResult.Progress(100, "完成"))
            emit(FusionResult.Success(
                output = output,
                features = FusionFeatures(
                    textFeatures = FloatArray(0),
                    imageFeatures = fused,
                    styleFeatures = FloatArray(0),
                    fusedFeatures = fused,
                    confidence = strength
                ),
                metadata = mapOf("strength" to strength)
            ))
        } catch (e: Exception) {
            emit(FusionResult.Error(e.message ?: "未知错误"))
        }
    }
    
    /**
     * 清理缓存
     */
    fun clearCache() {
        analysisCache.clear()
        featureCache.clear()
        Log.i(TAG, "缓存已清空")
    }
    
    /**
     * 释放资源
     */
    fun release() {
        scope.cancel()
        clearCache()
        Log.i(TAG, "MultimodalFusionEngine 已释放")
    }
    
    // ==================== 私有方法 ====================
    
    private fun analyzeDominantColors(bitmap: Bitmap): List<Int> {
        val small = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
        val pixels = IntArray(64 * 64)
        small.getPixels(pixels, 0, 64, 0, 0, 64, 64)
        
        // K-Means 简化版
        val clusters = mutableListOf<Pair<Int, Int>>() // (color, count)
        
        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 32
            val g = ((pixel shr 8) and 0xFF) / 32
            val b = (pixel and 0xFF) / 32
            val quantized = (r shl 10) or (g shl 5) or b
            
            val existing = clusters.indexOfFirst { (it.first shr 16) / 32 == r && 
                                                    ((it.first shr 8) and 0xFF) / 32 == g && 
                                                    (it.first and 0xFF) / 32 == b }
            if (existing >= 0) {
                clusters[existing] = clusters[existing].first to (clusters[existing].second + 1)
            } else if (clusters.size < 5) {
                clusters.add(pixel to 1)
            }
        }
        
        if (small != bitmap) small.recycle()
        
        return clusters.sortedByDescending { it.second }.take(5).map { it.first }
    }
    
    private fun classifyScene(bitmap: Bitmap): String {
        val analysis = analyzeDominantColors(bitmap)
        if (analysis.isEmpty()) return "unknown"
        
        val avgSaturation = analysis.map { 
            val r = (it shr 16) and 0xFF
            val g = (it shr 8) and 0xFF
            val b = it and 0xFF
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            if (max == 0) 0 else (max - min) * 100 / max
        }.average()
        
        return when {
            avgSaturation > 50 -> "vibrant"
            avgSaturation > 25 -> "natural"
            else -> "muted"
        }
    }
    
    private fun detectObjects(bitmap: Bitmap): List<String> {
        // 简化版对象检测
        val objects = mutableListOf<String>()
        
        // 基于颜色分布猜测
        val colors = analyzeDominantColors(bitmap)
        if (colors.isNotEmpty()) {
            val mainColor = colors.first()
            val r = (mainColor shr 16) and 0xFF
            val g = (mainColor shr 8) and 0xFF
            val b = mainColor and 0xFF
            
            // 简单规则
            if (g > r && g > b) objects.add("nature")
            if (r > 200 && g < 100 && b < 100) objects.add("warm")
            if (r > 150 && g > 150 && b > 150) objects.add("light")
            if (r < 50 && g < 50 && b < 50) objects.add("dark")
        }
        
        return objects
    }
    
    private fun analyzeMood(bitmap: Bitmap, colors: List<Int>): String {
        if (colors.size < 2) return "neutral"
        
        val warmCount = colors.count { 
            val r = (it shr 16) and 0xFF
            r > 150 && r > ((it shr 8) and 0xFF) && r > (it and 0xFF)
        }
        
        val coolCount = colors.count { 
            val b = it and 0xFF
            b > 100 && b > ((it shr 16) and 0xFF)
        }
        
        return when {
            warmCount > coolCount -> "warm"
            coolCount > warmCount -> "cool"
            else -> "neutral"
        }
    }
    
    private fun analyzeStyle(bitmap: Bitmap): String {
        // 简化风格分析
        val colors = analyzeDominantColors(bitmap)
        
        if (colors.isEmpty()) return "realistic"
        
        val saturation = colors.map { c ->
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            if (max == 0) 0 else (max - min) * 100 / max
        }.average()
        
        return when {
            saturation > 60 -> "vibrant"
            saturation > 30 -> "natural"
            else -> "muted"
        }
    }
    
    private fun analyzeComposition(bitmap: Bitmap): String {
        return when {
            bitmap.width > bitmap.height * 1.5 -> "landscape"
            bitmap.height > bitmap.width * 1.5 -> "portrait"
            else -> "square"
        }
    }
    
    private fun calculateEdgeDensity(pixels: IntArray, startX: Int, startY: Int, size: Int): Float {
        // 简化边缘检测
        var edges = 0
        val total = size * size
        
        for (y in startY until minOf(startY + size, 32)) {
            for (x in startX until minOf(startX + size, 32)) {
                val idx = y * 32 + x
                if (idx < pixels.size) {
                    val pixel = pixels[idx]
                    val r = (pixel shr 16) and 0xFF
                    if (r > 128) edges++
                }
            }
        }
        
        return edges.toFloat() / total
    }
    
    private fun generateStyleFeatures(
        saturation: Float,
        brightness: Float,
        contrast: Float,
        sharpness: Float
    ): FloatArray {
        val features = FloatArray(STYLE_FEATURE_DIM)
        features[0] = saturation
        features[1] = brightness
        features[2] = contrast
        features[3] = sharpness
        
        // 填充其余维度
        for (i in 4 until STYLE_FEATURE_DIM) {
            features[i] = (features[i % 4] + i % 10 * 0.01f) % 1f
        }
        
        return features
    }
    
    private fun blendFeatures(
        text: FloatArray,
        image: FloatArray,
        style: FloatArray,
        config: FusionConfig
    ): FloatArray {
        val dim = maxOf(text.size, image.size, style.size)
        val result = FloatArray(dim)
        
        for (i in 0 until dim) {
            val tv = if (i < text.size) text[i] else 0f
            val iv = if (i < image.size) image[i] else 0f
            val sv = if (i < style.size) style[i] else 0f
            
            result[i] = tv * config.textWeight + 
                       iv * config.imageWeight + 
                       sv * config.styleWeight
        }
        
        return result
    }
    
    private fun interleaveFeatures(
        text: FloatArray,
        image: FloatArray,
        style: FloatArray,
        config: FusionConfig
    ): FloatArray {
        val result = FloatArray(text.size + image.size + style.size)
        var idx = 0
        
        for (i in 0 until maxOf(text.size, image.size, style.size)) {
            if (i < text.size) result[idx++] = text[i] * config.textWeight
            if (i < image.size) result[idx++] = image[i] * config.imageWeight
            if (i < style.size) result[idx++] = style[i] * config.styleWeight
        }
        
        return result
    }
    
    private fun attentionFusion(
        text: FloatArray,
        image: FloatArray,
        style: FloatArray,
        config: FusionConfig
    ): FloatArray {
        // 简化的注意力机制
        val dim = maxOf(text.size, image.size, style.size)
        val result = FloatArray(dim)
        
        // 计算注意力权重
        val attentionT = FloatArray(dim) { config.textWeight }
        val attentionI = FloatArray(dim) { config.imageWeight }
        val attentionS = FloatArray(dim) { config.styleWeight }
        
        // 自适应调整
        for (i in 0 until dim) {
            val t = if (i < text.size) text[i] else 0f
            val im = if (i < image.size) image[i] else 0f
            val s = if (i < style.size) style[i] else 0f
            
            val sum = kotlin.math.abs(t) + kotlin.math.abs(im) + kotlin.math.abs(s)
            if (sum > 0) {
                attentionT[i] *= kotlin.math.abs(t) / sum
                attentionI[i] *= kotlin.math.abs(im) / sum
                attentionS[i] *= kotlin.math.abs(s) / sum
            }
            
            result[i] = t * attentionT[i] + im * attentionI[i] + s * attentionS[i]
        }
        
        return result
    }
    
    private fun styleTransferFusion(
        image: FloatArray,
        style: FloatArray,
        config: FusionConfig
    ): FloatArray {
        // 风格迁移专用融合
        val result = FloatArray(image.size)
        
        for (i in image.indices) {
            // 内容保持 + 风格增强
            val contentWeight = 1f - config.styleWeight * 0.5f
            val styleWeight = config.styleWeight * 0.5f
            
            result[i] = image[i] * contentWeight + 
                       (style[i % style.size] - 0.5f) * styleWeight
        }
        
        return result
    }
    
    private suspend fun generateFromFeatures(
        features: FloatArray,
        input: MultimodalInput,
        config: FusionConfig
    ): Bitmap = withContext(Dispatchers.Default) {
        // 基于特征生成图像 (模拟)
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)
        
        // 使用特征控制颜色生成
        val baseHue = (features.take(10).map { (it * 360).toInt() }.average() % 360).toFloat()
        
        for (y in 0 until size) {
            for (x in 0 until size) {
                val idx = y * size + x
                
                // 基于位置的混合
                val h = (baseHue + x * 0.1f + y * 0.1f) % 360
                val s = 0.5f + features[x % features.size] * 0.3f
                val v = 0.7f + features[(y % features.size)] * 0.2f
                
                pixels[idx] = hsvToRgb(h.toInt(), s, v)
            }
        }
        
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        bitmap
    }
    
    private fun hsvToRgb(h: Int, s: Float, v: Float): Int {
        val c = v * s
        val x = c * (1 - kotlin.math.abs((h / 60) % 2 - 1))
        val m = v - c
        
        val (r1, g1, b1) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        
        val r = ((r1 + m) * 255).toInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255).toInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255).toInt().coerceIn(0, 255)
        
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
    
    private fun calculateConfidence(features: FloatArray): Float {
        // 基于特征分布计算置信度
        val mean = features.average().toFloat()
        val variance = features.map { (it - mean) * (it - mean) }.average().toFloat()
        val stdDev = sqrt(variance)
        
        // 低方差 = 高置信度
        return (1f - stdDev.coerceIn(0f, 1f)).coerceIn(0.3f, 0.95f)
    }
}
