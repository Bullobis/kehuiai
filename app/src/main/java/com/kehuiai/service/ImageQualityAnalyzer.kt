package com.kehuiai.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 可绘AI v3.6.6 图像质量评估引擎
 * 支持无参考图像质量评估 (No-Reference IQA)
 */
class ImageQualityAnalyzer(private val context: Context) {
    
    companion object {
        private const val TAG = "ImageQualityAnalyzer"
        
        @Volatile
        private var INSTANCE: ImageQualityAnalyzer? = null
        
        fun getInstance(context: Context): ImageQualityAnalyzer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ImageQualityAnalyzer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * 质量评估结果
     */
    data class QualityResult(
        val overallScore: Float,           // 总体评分 0-100
        val sharpness: Float,               // 清晰度 0-100
        val brightness: Float,             // 亮度均衡度 0-100
        val contrast: Float,               // 对比度 0-100
        val noiseLevel: Float,             // 噪点水平 0-100 (越高越好)
        val colorfulness: Float,           // 色彩饱和度 0-100
        val compressionArtifacts: Float,   // 压缩伪影 0-100 (越高越好)
        val recommendation: String         // 建议
    )
    
    // 评估状态
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()
    
    /**
     * 分析图像质量
     */
    suspend fun analyze(bitmap: Bitmap): QualityResult = withContext(Dispatchers.Default) {
        _isAnalyzing.value = true
        
        try {
            Log.i(TAG, "开始分析图像质量: ${bitmap.width}x${bitmap.height}")
            
            // 清晰度分析 (Laplacian 方差)
            val sharpness = analyzeSharpness(bitmap)
            
            // 亮度分析
            val brightness = analyzeBrightness(bitmap)
            
            // 对比度分析
            val contrast = analyzeContrast(bitmap)
            
            // 噪点分析
            val noiseLevel = analyzeNoise(bitmap)
            
            // 色彩饱和度分析
            val colorfulness = analyzeColorfulness(bitmap)
            
            // 压缩伪影分析
            val compressionArtifacts = analyzeCompressionArtifacts(bitmap)
            
            // 计算总体评分
            val overallScore = calculateOverallScore(
                sharpness, brightness, contrast, noiseLevel, colorfulness, compressionArtifacts
            )
            
            // 生成建议
            val recommendation = generateRecommendation(
                overallScore, sharpness, brightness, contrast, noiseLevel
            )
            
            Log.i(TAG, "质量评估完成: 总体=$overallScore, 清晰度=$sharpness, 噪点=$noiseLevel")
            
            QualityResult(
                overallScore = overallScore,
                sharpness = sharpness,
                brightness = brightness,
                contrast = contrast,
                noiseLevel = noiseLevel,
                colorfulness = colorfulness,
                compressionArtifacts = compressionArtifacts,
                recommendation = recommendation
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "质量评估失败", e)
            QualityResult(
                overallScore = 0f,
                sharpness = 0f,
                brightness = 0f,
                contrast = 0f,
                noiseLevel = 0f,
                colorfulness = 0f,
                compressionArtifacts = 0f,
                recommendation = "评估失败: ${e.message}"
            )
        } finally {
            _isAnalyzing.value = false
        }
    }
    
    /**
     * 分析图像文件
     */
    suspend fun analyze(filePath: String): QualityResult? {
        return try {
            val bitmap = BitmapFactory.decodeFile(filePath)
            if (bitmap != null) {
                val result = analyze(bitmap)
                bitmap.recycle()
                result
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "文件分析失败: $filePath", e)
            null
        }
    }
    
    /**
     * 清晰度分析 - 使用 Laplacian 方差
     * 原理：清晰的图像边缘清晰，Laplacian 变换后方差大
     */
    private fun analyzeSharpness(bitmap: Bitmap): Float {
        // 缩放到较小尺寸加快处理
        val scaled = scaleBitmap(bitmap, 256)
        
        // 获取像素数据
        val width = scaled.width
        val height = scaled.height
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // 转换为灰度并计算 Laplacian
        val grayPixels = IntArray(width * height)
        for (i in pixels.indices) {
            val r = (pixels[i] shr 16) and 0xFF
            val g = (pixels[i] shr 8) and 0xFF
            val b = pixels[i] and 0xFF
            grayPixels[i] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }
        
        // Laplacian 核 [0, 1, 0; 1, -4, 1; 0, 1, 0]
        val laplacianValues = mutableListOf<Double>()
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                val laplacian = -4 * grayPixels[idx] +
                        grayPixels[idx - 1] +
                        grayPixels[idx + 1] +
                        grayPixels[idx - width] +
                        grayPixels[idx + width]
                laplacianValues.add(laplacian.toDouble())
            }
        }
        
        // 计算方差
        val mean = laplacianValues.average()
        val variance = laplacianValues.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)
        
        // 缩放到 0-100
        val score = (stdDev / 50.0).coerceIn(0.0, 100.0).toFloat()
        
        scaled.recycle()
        return score
    }
    
    /**
     * 亮度分析 - 检测过曝或欠曝
     */
    private fun analyzeBrightness(bitmap: Bitmap): Float {
        val scaled = scaleBitmap(bitmap, 128)
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        
        // 计算平均亮度
        var totalBrightness = 0.0
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val brightness = 0.299 * r + 0.587 * g + 0.114 * b
            totalBrightness += brightness
        }
        
        val avgBrightness = totalBrightness / pixels.size
        
        scaled.recycle()
        
        // 理想亮度约为 128 (0-255 范围)
        // 偏离越大，分数越低
        val deviation = abs(avgBrightness - 128.0) / 128.0
        return ((1.0 - deviation * 0.5).coerceIn(0.0, 1.0) * 100).toFloat()
    }
    
    /**
     * 对比度分析
     */
    private fun analyzeContrast(bitmap: Bitmap): Float {
        val scaled = scaleBitmap(bitmap, 128)
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        
        // 计算灰度直方图
        val histogram = IntArray(256)
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt().coerceIn(0, 255)
            histogram[gray]++
        }
        
        // 计算对比度 (使用 RMS 对比度)
        val total = pixels.size
        val mean = histogram.mapIndexed { i, count -> i * count }.sum().toDouble() / total
        val variance = histogram.mapIndexed { i, count -> 
            (i - mean) * (i - mean) * count 
        }.sum().toDouble() / total
        
        val contrast = sqrt(variance) / 255.0
        
        scaled.recycle()
        
        // 理想的对比度在 0.3-0.7 之间
        return when {
            contrast < 0.1 -> 30f  // 对比度太低
            contrast > 0.9 -> 70f  // 对比度太高
            else -> ((contrast / 0.6 * 100).coerceIn(0.0, 100.0)).toFloat()
        }
    }
    
    /**
     * 噪点分析
     */
    private fun analyzeNoise(bitmap: Bitmap): Float {
        val scaled = scaleBitmap(bitmap, 128)
        val width = scaled.width
        val height = scaled.height
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // 使用高频成分估计噪点
        var highFreqEnergy = 0.0
        var lowFreqEnergy = 0.0
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                val pixel = pixels[idx]
                
                // 计算高频成分 (与邻域均值的差异)
                val neighbors = listOf(
                    pixels[idx - 1],
                    pixels[idx + 1],
                    pixels[idx - width],
                    pixels[idx + width]
                )
                val neighborAvgR = neighbors.map { (it shr 16) and 0xFF }.average()
                val neighborAvgG = neighbors.map { (it shr 8) and 0xFF }.average()
                val neighborAvgB = neighbors.map { it and 0xFF }.average()
                
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                
                val diff = sqrt(
                    (r - neighborAvgR) * (r - neighborAvgR) +
                    (g - neighborAvgG) * (g - neighborAvgG) +
                    (b - neighborAvgB) * (b - neighborAvgB)
                )
                
                highFreqEnergy += diff * diff
                lowFreqEnergy += r * r + g * g + b * b
            }
        }
        
        scaled.recycle()
        
        // 噪点比例
        val noiseRatio = if (lowFreqEnergy > 0) {
            sqrt(highFreqEnergy / (width * height)) / (sqrt(lowFreqEnergy / (width * height)) + 1)
        } else 0.0
        
        // 噪点太多或太少都不好，理想值在 0.02-0.08
        return when {
            noiseRatio < 0.01 -> 60f  // 太平滑，可能是过度处理
            noiseRatio > 0.15 -> 40f  // 噪点太多
            else -> ((1.0 - (noiseRatio - 0.02) / 0.06) * 100).coerceIn(0.0, 100.0).toFloat()
        }
    }
    
    /**
     * 色彩饱和度分析
     */
    private fun analyzeColorfulness(bitmap: Bitmap): Float {
        val scaled = scaleBitmap(bitmap, 128)
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        
        var totalSaturation = 0.0
        
        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255.0
            val g = ((pixel shr 8) and 0xFF) / 255.0
            val b = (pixel and 0xFF) / 255.0
            
            val maxC = maxOf(r, g, b)
            val minC = minOf(r, g, b)
            
            // 饱和度 = (max - min) / (1 - |2*L - 1|)
            val l = 0.5 * (maxC + minC)
            val saturation = if (l == 0.0 || l == 1.0) {
                0.0
            } else {
                (maxC - minC) / (1 - abs(2 * l - 1))
            }
            
            totalSaturation += saturation
        }
        
        scaled.recycle()
        
        val avgSaturation = totalSaturation / pixels.size
        
        // 理想饱和度在 0.2-0.6 之间
        return when {
            avgSaturation < 0.1 -> 50f  // 太灰暗
            avgSaturation > 0.8 -> 60f  // 太鲜艳
            else -> (avgSaturation * 150).coerceIn(0.0, 100.0).toFloat()
        }
    }
    
    /**
     * 压缩伪影分析
     */
    private fun analyzeCompressionArtifacts(bitmap: Bitmap): Float {
        val scaled = scaleBitmap(bitmap, 128)
        val width = scaled.width
        val height = scaled.height
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // 检测块效应 (JPEG 压缩特征)
        var blockiness = 0
        val blockSize = 8
        
        for (y in blockSize until height - blockSize step blockSize) {
            for (x in blockSize until width - blockSize step blockSize) {
                for (dy in 0 until blockSize) {
                    val idx1 = (y + dy) * width + x
                    val idx2 = (y + dy) * width + x - 1
                    if (idx1 < pixels.size && idx2 < pixels.size) {
                        blockiness += abs(
                            ((pixels[idx1] shr 16) and 0xFF) - ((pixels[idx2] shr 16) and 0xFF)
                        )
                    }
                }
            }
        }
        
        scaled.recycle()
        
        // 块效应越明显，伪影越多
        val avgBlockiness = blockiness.toDouble() / ((width / blockSize) * (height / blockSize) * blockSize * 4)
        
        // 转换为分数 (伪影越少分数越高)
        return ((1.0 - avgBlockiness / 20.0).coerceIn(0.0, 1.0) * 100).toFloat()
    }
    
    /**
     * 计算总体评分
     */
    private fun calculateOverallScore(
        sharpness: Float,
        brightness: Float,
        contrast: Float,
        noiseLevel: Float,
        colorfulness: Float,
        compressionArtifacts: Float
    ): Float {
        // 加权平均
        val weights = mapOf(
            "清晰度" to 0.30f,
            "亮度" to 0.15f,
            "对比度" to 0.15f,
            "噪点" to 0.15f,
            "色彩" to 0.10f,
            "压缩" to 0.15f
        )
        
        val weightedSum = 
            sharpness * weights["清晰度"]!! +
            brightness * weights["亮度"]!! +
            contrast * weights["对比度"]!! +
            noiseLevel * weights["噪点"]!! +
            colorfulness * weights["色彩"]!! +
            compressionArtifacts * weights["压缩"]!!
        
        return weightedSum.coerceIn(0f, 100f)
    }
    
    /**
     * 生成质量建议
     */
    private fun generateRecommendation(
        overall: Float,
        sharpness: Float,
        brightness: Float,
        contrast: Float,
        noise: Float
    ): String {
        val issues = mutableListOf<String>()
        
        when {
            overall >= 80 -> issues.add("图像质量优秀！")
            overall >= 60 -> { /* 良好 */ }
            overall >= 40 -> issues.add("图像质量一般")
            else -> issues.add("图像质量较差")
        }
        
        if (sharpness < 50) issues.add("建议提高清晰度")
        if (brightness < 50) issues.add("建议调整亮度")
        if (contrast < 50) issues.add("建议增强对比度")
        if (noise < 40) issues.add("建议降低噪点")
        
        return issues.joinToString(" ")
    }
    
    /**
     * 缩放 Bitmap
     */
    private fun scaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val scale = minOf(maxSize.toFloat() / bitmap.width, maxSize.toFloat() / bitmap.height, 1f)
        val newWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val newHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * 获取质量等级
     */
    fun getQualityGrade(score: Float): String {
        return when {
            score >= 90 -> "⭐⭐⭐⭐⭐ 卓越"
            score >= 80 -> "⭐⭐⭐⭐ 优秀"
            score >= 70 -> "⭐⭐⭐ 良好"
            score >= 60 -> "⭐⭐ 中等"
            score >= 50 -> "⭐ 较差"
            else -> "❌ 很差"
        }
    }
}
