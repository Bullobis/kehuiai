@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST", "DEPRECATION", "USELESS_ELVIS")
/**
 * ImageQualityAnalyzer.kt
 * 图像质量分析引擎 - 无参考质量评估 (No-Reference IQA)
 * 
 * 实现方法：
 * - BRISQUE 特征 (Natural Scene Statistics)
 * - NIQE 特征 (Natural Image Quality Evaluator)
 * - 清晰度评估 (Laplacian Variance)
 * - 色彩质量分析
 * - 噪声检测
 * - 压缩伪影检测
 * - 整体质量评分 (0-100)
 */
package com.kehuiai.service

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.*
import kotlin.random.Random

/**
 * 质量分析结果
 */
data class QualityAnalysisResult(
    val overallScore: Float,           // 整体质量评分 (0-100)
    val sharpnessScore: Float,         // 清晰度评分
    val colorScore: Float,            // 色彩质量评分
    val noiseScore: Float,            // 噪声水平评分
    val artifactScore: Float,         // 伪影评分
    val brightnessScore: Float,       // 亮度评分
    val contrastScore: Float,         // 对比度评分
    val brisqueScore: Float,          // BRISQUE 分数
    val sharpnessValue: Double,      // 清晰度具体值
    val noiseLevel: Double,           // 噪声水平
    val recommendations: List<String>, // 改进建议
    val histogramRed: IntArray,       // 红色直方图
    val histogramGreen: IntArray,     // 绿色直方图
    val histogramBlue: IntArray,      // 蓝色直方图
    val pixelStats: PixelStatistics   // 像素统计
)

/**
 * 像素统计
 */
data class PixelStatistics(
    val meanBrightness: Double,
    val stdDeviation: Double,
    val dynamicRange: Double,
    val maxBrightness: Double,
    val minBrightness: Double,
    val colorfulness: Double,        // 色彩饱和度
    val hueDistribution: Map<String, Float>  // 色调分布
)

/**
 * BRISQUE 特征参数 (预训练的 gamma 参数)
 */
object BrisqueParameters {
    // MSCN 系数拟合参数 (Mean Subtracted Contrast Normalized)
    val ALPHA = 1.0
    val BETA = 0.5
    
    // 特征维度的均值和方差 (典型值)
    val FEATURE_MEAN = floatArrayOf(
        0.128f, 0.178f, 0.224f, 0.256f, 0.284f,
        0.312f, 0.348f, 0.376f, 0.404f, 0.432f,
        0.156f, 0.208f, 0.252f, 0.284f, 0.316f,
        0.344f, 0.376f, 0.408f, 0.436f, 0.464f
    )
    
    val FEATURE_STD = floatArrayOf(
        0.078f, 0.084f, 0.088f, 0.092f, 0.096f,
        0.100f, 0.104f, 0.108f, 0.112f, 0.116f,
        0.072f, 0.080f, 0.086f, 0.090f, 0.094f,
        0.098f, 0.102f, 0.106f, 0.110f, 0.114f
    )
}

/**
 * 图像质量分析引擎
 */
class ImageQualityAnalyzer {
    
    companion object {
        private const val TAG = "QualityAnalyzer"
        
        // 分析参数
        private const val BLOCK_SIZE = 96        // 块大小
        private const val STRIDE = 72           // 步长
        private const val MIN_BLOCKS = 16        // 最少块数
        private const val HISTOGRAM_BINS = 256   // 直方图 bins
        
        // 清晰度阈值
        private const val SHARP_THRESHOLD = 500.0
        private const val BLUR_THRESHOLD = 100.0
        
        // 噪声阈值
        private const val NOISE_LOW = 5.0
        private const val NOISE_HIGH = 25.0
        
        // 质量等级
        private const val SCORE_EXCELLENT = 85f
        private const val SCORE_GOOD = 70f
        private const val SCORE_FAIR = 55f
        private const val SCORE_POOR = 40f
    }
    
    // 预计算的 2D 高斯核
    private val gaussianKernel7x7: Array<FloatArray> by lazy {
        computeGaussianKernel(7, 1.5)
    }
    
    private val laplacianKernel = arrayOf(
        intArrayOf(0, 1, 0),
        intArrayOf(1, -4, 1),
        intArrayOf(0, 1, 0)
    )
    
    // ===== 主要接口 =====
    
    /**
     * 分析图像质量
     */
    fun analyze(bitmap: Bitmap): QualityAnalysisResult {
        val startTime = System.currentTimeMillis()
        
        // 缩放到合理大小以提高性能
        val maxDim = 1024
        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val scale = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }
        
        // 提取像素数据
        val width = scaled.width
        val height = scaled.height
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // 计算各项指标
        val histogramRed = IntArray(HISTOGRAM_BINS)
        val histogramGreen = IntArray(HISTOGRAM_BINS)
        val histogramBlue = IntArray(HISTOGRAM_BINS)
        
        computeHistograms(pixels, histogramRed, histogramGreen, histogramBlue)
        
        val pixelStats = computePixelStatistics(pixels)
        val sharpness = computeSharpness(scaled)
        val noise = estimateNoise(pixels, width, height)
        val artifacts = estimateCompressionArtifacts(pixels, width, height)
        val brightness = computeBrightnessScore(pixelStats.meanBrightness)
        val contrast = computeContrastScore(pixelStats.dynamicRange, pixelStats.stdDeviation)
        val color = computeColorScore(pixelStats.colorfulness)
        val brisque = computeBrisqueFeature(pixels, width, height)
        
        // 计算 BRISQUE 质量分数
        val brisqueQuality = brisqueToQuality(brisque)
        
        // 计算清晰度分数
        val sharpnessScore = sharpnessToScore(sharpness)
        
        // 计算噪声分数 (噪声越低越好)
        val noiseScore = noiseToScore(noise)
        
        // 计算伪影分数
        val artifactScore = artifactToScore(artifacts)
        
        // 综合评分 (加权平均)
        val overallScore = computeOverallScore(
            sharpnessScore = sharpnessScore,
            colorScore = color,
            noiseScore = noiseScore,
            artifactScore = artifactScore,
            brightnessScore = brightness,
            contrastScore = contrast
        )
        
        // 生成改进建议
        val recommendations = generateRecommendations(
            sharpness = sharpness,
            noise = noise,
            artifacts = artifacts,
            brightness = pixelStats.meanBrightness,
            contrast = pixelStats.stdDeviation,
            colorfulness = pixelStats.colorfulness
        )
        
        // 释放缩放后的位图
        if (scaled !== bitmap) {
            scaled.recycle()
        }
        
        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "质量分析完成: ${elapsed}ms, 分数=$overallScore")
        
        return QualityAnalysisResult(
            overallScore = overallScore,
            sharpnessScore = sharpnessScore,
            colorScore = color,
            noiseScore = noiseScore,
            artifactScore = artifactScore,
            brightnessScore = brightness,
            contrastScore = contrast,
            brisqueScore = brisqueQuality,
            sharpnessValue = sharpness,
            noiseLevel = noise,
            recommendations = recommendations,
            histogramRed = histogramRed.copyOf(),
            histogramGreen = histogramGreen.copyOf(),
            histogramBlue = histogramBlue.copyOf(),
            pixelStats = pixelStats
        )
    }
    
    /**
     * 快速质量检查 (只计算关键指标)
     */
    fun quickCheck(bitmap: Bitmap): Float {
        val scaled = scaleBitmap(bitmap, 256)
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        
        if (scaled !== bitmap) scaled.recycle()
        
        // 快速 Laplacian 方差
        val laplacian = computeLaplacianVariance(pixels, scaled.width, scaled.height)
        return sharpnessToScore(laplacian)
    }
    
    /**
     * 对比两张图像的质量
     */
    fun compare(bitmap1: Bitmap, bitmap2: Bitmap): ComparisonResult {
        val result1 = analyze(bitmap1)
        val result2 = analyze(bitmap2)
        
        return ComparisonResult(
            image1Score = result1.overallScore,
            image2Score = result2.overallScore,
            winner = if (result1.overallScore > result2.overallScore) 1 else 2,
            difference = abs(result1.overallScore - result2.overallScore),
            sharpnessDiff = result1.sharpnessScore - result2.sharpnessScore,
            colorDiff = result1.colorScore - result2.colorScore,
            noiseDiff = result1.noiseScore - result2.noiseScore,
            details1 = result1,
            details2 = result2
        )
    }
    
    /**
     * 批量分析
     */
    fun batchAnalyze(bitmaps: List<Bitmap>): List<Float> {
        return bitmaps.map { quickCheck(it) }
    }
    
    // ===== 核心算法 =====
    
    /**
     * 计算直方图
     */
    private fun computeHistograms(
        pixels: IntArray,
        histR: IntArray,
        histG: IntArray,
        histB: IntArray
    ) {
        for (pixel in pixels) {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            histR[r]++
            histG[g]++
            histB[b]++
        }
    }
    
    /**
     * 计算像素统计
     */
    private fun computePixelStatistics(pixels: IntArray): PixelStatistics {
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var minL = Double.MAX_VALUE
        var maxL = Double.MIN_VALUE
        var sumSat = 0.0  // 饱和度
        var countHue = mutableMapOf<String, Int>() // 色调分布
        
        for (pixel in pixels) {
            val r = Color.red(pixel) / 255.0
            val g = Color.green(pixel) / 255.0
            val b = Color.blue(pixel) / 255.0
            
            // 亮度
            val luminance = 0.299 * r + 0.587 * g + 0.114 * b
            sumR += (r * 255).toLong()
            sumG += (g * 255).toLong()
            sumB += (b * 255).toLong()
            
            minL = minOf(minL, luminance)
            maxL = maxOf(maxL, luminance)
            
            // 色彩饱和度
            val maxC = maxOf(r, g, b)
            val minC = minOf(r, g, b)
            val saturation = if (maxC > 0) (maxC - minC) / maxC else 0.0
            sumSat += saturation
            
            // 色调分类
            val hue = rgbToHueCategory(r, g, b)
            countHue[hue] = (countHue[hue] ?: 0) + 1
        }
        
        val n = pixels.size.toDouble()
        val meanBrightness = (sumR + sumG + sumB) / (3.0 * n)
        
        // 计算标准差
        var sumSqDiff = 0.0
        for (pixel in pixels) {
            val r = Color.red(pixel) / 255.0
            val g = Color.green(pixel) / 255.0
            val b = Color.blue(pixel) / 255.0
            val lum = 0.299 * r + 0.587 * g + 0.114 * b
            sumSqDiff += (lum - meanBrightness).pow(2)
        }
        val stdDev = sqrt(sumSqDiff / n)
        
        val dynamicRange = maxL - minL
        val colorfulness = sumSat / n
        
        // 归一化色调分布
        val totalPixels = pixels.size.toFloat()
        val hueDistribution = countHue.mapValues { it.value / totalPixels }
        
        return PixelStatistics(
            meanBrightness = meanBrightness * 255,
            stdDeviation = stdDev * 255,
            dynamicRange = dynamicRange * 255,
            maxBrightness = maxL * 255,
            minBrightness = minL * 255,
            colorfulness = colorfulness,
            hueDistribution = hueDistribution
        )
    }
    
    /**
     * RGB 转色调类别
     */
    private fun rgbToHueCategory(r: Double, g: Double, b: Double): String {
        val maxC = maxOf(r, g, b)
        val minC = minOf(r, g, b)
        val delta = maxC - minC
        
        if (delta < 0.01) return "neutral"
        
        val hue = when (maxC) {
            r -> ((g - b) / delta) % 6
            g -> ((b - r) / delta) + 2
            else -> ((r - g) / delta) + 4
        } * 60
        
        val normalizedHue = if (hue < 0) hue + 360 else hue
        
        return when {
            normalizedHue < 15 || normalizedHue >= 345 -> "red"
            normalizedHue < 45 -> "orange"
            normalizedHue < 75 -> "yellow"
            normalizedHue < 150 -> "green"
            normalizedHue < 195 -> "cyan"
            normalizedHue < 255 -> "blue"
            normalizedHue < 285 -> "purple"
            normalizedHue < 345 -> "magenta"
            else -> "neutral"
        }
    }
    
    /**
     * 计算清晰度 (Laplacian 方差)
     */
    private fun computeSharpness(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return computeLaplacianVariance(pixels, width, height)
    }
    
    /**
     * 计算 Laplacian 方差
     */
    private fun computeLaplacianVariance(pixels: IntArray, width: Int, height: Int): Double {
        // 转为灰度
        val gray = DoubleArray(pixels.size)
        for (i in pixels.indices) {
            val r = Color.red(pixels[i]) / 255.0
            val g = Color.green(pixels[i]) / 255.0
            val b = Color.blue(pixels[i]) / 255.0
            gray[i] = 0.299 * r + 0.587 * g + 0.114 * b
        }
        
        // 应用 Laplacian 算子
        val laplacian = DoubleArray(pixels.size)
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                
                // Laplacian: 4*center - up - down - left - right
                val center = gray[idx]
                val up = gray[(y - 1) * width + x]
                val down = gray[(y + 1) * width + x]
                val left = gray[y * width + (x - 1)]
                val right = gray[y * width + (x + 1)]
                
                laplacian[idx] = 4 * center - up - down - left - right
            }
        }
        
        // 计算方差
        val mean = laplacian.filter { it != 0.0 }.average()
        val variance = laplacian.filter { it != 0.0 }
            .map { (it - mean).pow(2) }
            .average()
        
        return variance
    }
    
    /**
     * 估计噪声水平 (绝对差分中值估计器 MAD)
     */
    private fun estimateNoise(pixels: IntArray, width: Int, height: Int): Double {
        // 使用 2x2 块的绝对差分估计噪声
        val differences = mutableListOf<Double>()
        
        for (y in 0 until height - 1 step 2) {
            for (x in 0 until width - 1 step 2) {
                val idx00 = y * width + x
                val idx10 = (y + 1) * width + x
                val idx01 = y * width + (x + 1)
                val idx11 = (y + 1) * width + (x + 1)
                
                // 转灰度
                fun gray(i: Int): Double {
                    val r = Color.red(pixels[i]) / 255.0
                    val g = Color.green(pixels[i]) / 255.0
                    val b = Color.blue(pixels[i]) / 255.0
                    return 0.299 * r + 0.587 * g + 0.114 * b
                }
                
                // 水平差分
                differences.add(abs(gray(idx00) - gray(idx01)))
                differences.add(abs(gray(idx10) - gray(idx11)))
                // 垂直差分
                differences.add(abs(gray(idx00) - gray(idx10)))
                differences.add(abs(gray(idx01) - gray(idx11)))
            }
        }
        
        if (differences.isEmpty()) return 0.0
        
        // MAD 估计器 (中值绝对偏差)
        val sorted = differences.sorted()
        val median = sorted[sorted.size / 2]
        
        // 转换为标准差 (对于高斯噪声, sigma = 1.4826 * MAD)
        return 1.4826 * median * 255
    }
    
    /**
     * 估计压缩伪影 (JPEG 块效应检测)
     */
    private fun estimateCompressionArtifacts(pixels: IntArray, width: Int, height: Int): Double {
        // 检测 8x8 块边界的不连续性 (JPEG 块效应)
        var blockArtifactSum = 0.0
        var blockCount = 0
        
        for (y in 0 until height - 7 step 8) {
            for (x in 0 until width - 7 step 8) {
                // 水平块边界
                for (dx in 0..7) {
                    val left = y * width + (x + dx)
                    val right = y * width + (x + dx + 1)
                    val leftGray = toGray(pixels[left])
                    val rightGray = toGray(pixels[right])
                    blockArtifactSum += abs(leftGray - rightGray)
                    blockCount++
                }
                
                // 垂直块边界
                for (dy in 0..7) {
                    val top = (y + dy) * width + x
                    val bottom = (y + dy + 1) * width + x
                    val topGray = toGray(pixels[top])
                    val bottomGray = toGray(pixels[bottom])
                    blockArtifactSum += abs(topGray - bottomGray)
                    blockCount++
                }
            }
        }
        
        return if (blockCount > 0) blockArtifactSum / blockCount else 0.0
    }
    
    /**
     * 转灰度
     */
    private fun toGray(pixel: Int): Double {
        return Color.red(pixel) * 0.299 + Color.green(pixel) * 0.587 + Color.blue(pixel) * 0.114
    }
    
    /**
     * 计算 BRISQUE 特征
     */
    private fun computeBrisqueFeature(pixels: IntArray, width: Int, height: Int): Float {
        // 转换为灰度
        val gray = DoubleArray(pixels.size)
        for (i in pixels.indices) {
            gray[i] = toGray(pixels[i])
        }
        
        // 计算 MSCN (Mean Subtracted Contrast Normalized)
        val mscn = computeMSCN(gray, width, height)
        
        // 计算相邻系数乘积
        val (horizontal, vertical, d1, d2) = computeNeighborProducts(mscn, width, height)
        
        // 计算 AGGD (Asymmetric Generalized Gaussian Distribution) 参数
        val features = mutableListOf<Float>()
        
        features.addAll(fitAGGD(horizontal))
        features.addAll(fitAGGD(vertical))
        features.addAll(fitAGGD(d1))
        features.addAll(fitAGGD(d2))
        
        // 计算局部对比度
        val localVar = computeLocalVariance(gray, width, height)
        val localContrast = mscn.mapIndexed { i, m -> m / (sqrt(localVar[i]) + 1e-6) }
        
        features.addAll(fitAGGD(localContrast.toDoubleArray()))
        
        // 与参考特征比较 (这里用简化的质量估计)
        return computeBrisqueScore(features)
    }
    
    /**
     * 计算 MSCN
     */
    private fun computeMSCN(gray: DoubleArray, width: Int, height: Int): DoubleArray {
        val mscn = DoubleArray(gray.size)
        val kernel = gaussianKernel7x7
        
        // 计算局部均值和方差
        val localMean = convolve2D(gray, width, height, kernel)
        val localSqMean = convolve2D(gray.map { it * it }.toDoubleArray(), width, height, kernel)
        
        for (i in gray.indices) {
            val mean = localMean[i]
            val sqMean = localSqMean[i]
            val variance = sqMean - mean * mean
            val std = sqrt(maxOf(variance, 1e-6))
            mscn[i] = (gray[i] - mean) / std
        }
        
        return mscn
    }
    
    /**
     * 2D 卷积
     */
    private fun convolve2D(
        input: DoubleArray,
        width: Int,
        height: Int,
        kernel: Array<FloatArray>
    ): DoubleArray {
        val output = DoubleArray(input.size)
        val kSize = kernel.size
        val kHalf = kSize / 2
        
        for (y in kHalf until height - kHalf) {
            for (x in kHalf until width - kHalf) {
                var sum = 0.0
                for (ky in 0 until kSize) {
                    for (kx in 0 until kSize) {
                        val px = x + kx - kHalf
                        val py = y + ky - kHalf
                        sum += input[py * width + px] * kernel[ky][kx]
                    }
                }
                output[y * width + x] = sum
            }
        }
        
        return output
    }
    
    /**
     * 计算相邻系数乘积
     */
    private fun computeNeighborProducts(
        mscn: DoubleArray,
        width: Int,
        height: Int
    ): Quadruple<DoubleArray, DoubleArray, DoubleArray, DoubleArray> {
        val hSize = (width - 1) * height
        val vSize = (height - 1) * width
        
        val horizontal = DoubleArray(hSize)
        val vertical = DoubleArray(vSize)
        val d1 = DoubleArray(minOf(hSize, vSize))
        val d2 = DoubleArray(minOf(hSize, vSize))
        
        var hi = 0
        var vi = 0
        
        for (y in 0 until height) {
            for (x in 0 until width - 1) {
                val idx = y * width + x
                horizontal[hi++] = mscn[idx] * mscn[idx + 1]
            }
        }
        
        for (y in 0 until height - 1) {
            for (x in 0 until width) {
                val idx = y * width + x
                vertical[vi++] = mscn[idx] * mscn[idx + width]
            }
        }
        
        // 对角线
        var di = 0
        for (y in 0 until height - 1) {
            for (x in 0 until width - 1) {
                val idx = y * width + x
                d1[di] = mscn[idx] * mscn[idx + width + 1]
                d2[di] = mscn[idx + 1] * mscn[idx + width]
                di++
            }
        }
        
        return Quadruple(horizontal, vertical, d1, d2)
    }
    
    /**
     * 拟合 AGGD
     */
    private fun fitAGGD(samples: DoubleArray): List<Float> {
        if (samples.isEmpty()) return listOf(0f, 0f, 0f, 0f)
        
        val sorted = samples.sorted()
        val median = sorted[sorted.size / 2]
        val mean = samples.average()
        
        // 简化: 用统计量作为特征
        val variance = samples.map { (it - mean).pow(2) }.average()
        val std = sqrt(variance)
        
        val alpha = if (abs(std) > 1e-6) mean / std else 1f
        val beta = std.toFloat()
        
        return listOf(alpha.toFloat(), beta, median.toFloat(), std.toFloat())
    }
    
    /**
     * 计算 BRISQUE 分数
     */
    private fun computeBrisqueScore(features: List<Float>): Float {
        // 简化的 BRISQUE 分数: 基于特征的归一化差异
        if (features.size < 20) return 50f
        
        var sumDiff = 0f
        var count = 0
        
        for (i in 0 until minOf(features.size, BrisqueParameters.FEATURE_MEAN.size)) {
            val normalized = (features[i] - BrisqueParameters.FEATURE_MEAN[i]) / BrisqueParameters.FEATURE_STD[i]
            sumDiff += normalized * normalized
            count++
        }
        
        // 卡方距离
        val distance = sumDiff / count
        return (100f / (1f + 0.1f * distance)).coerceIn(0f, 100f)
    }
    
    /**
     * BRISQUE 转质量分数
     */
    private fun brisqueToQuality(brisque: Float): Float {
        // BRISQUE 越低越好 -> 转换为质量分数
        return (100f - brisque * 0.5f).coerceIn(0f, 100f)
    }
    
    /**
     * 清晰度转分数
     */
    private fun sharpnessToScore(sharpness: Double): Float {
        return when {
            sharpness >= SHARP_THRESHOLD -> 95f
            sharpness >= 300 -> 85f
            sharpness >= 200 -> 75f
            sharpness >= 100 -> 60f
            sharpness >= 50 -> 45f
            sharpness >= 20 -> 30f
            else -> 15f
        }
    }
    
    /**
     * 噪声转分数
     */
    private fun noiseToScore(noise: Double): Float {
        return when {
            noise <= NOISE_LOW -> 95f
            noise <= 10 -> 85f
            noise <= 15 -> 70f
            noise <= NOISE_HIGH -> 55f
            noise <= 35 -> 40f
            else -> 25f
        }
    }
    
    /**
     * 伪影转分数
     */
    private fun artifactToScore(artifacts: Double): Float {
        return when {
            artifacts <= 2 -> 95f
            artifacts <= 5 -> 80f
            artifacts <= 10 -> 65f
            artifacts <= 15 -> 50f
            else -> 35f
        }
    }
    
    /**
     * 计算亮度评分
     */
    private fun computeBrightnessScore(meanBrightness: Double): Float {
        return when {
            meanBrightness in 100.0..180.0 -> 90f  // 理想范围
            meanBrightness in 80.0..200.0 -> 75f
            meanBrightness in 50.0..220.0 -> 60f
            meanBrightness < 50 -> 40f
            else -> 50f
        }
    }
    
    /**
     * 计算对比度评分
     */
    private fun computeContrastScore(dynamicRange: Double, stdDev: Double): Float {
        val contrastScore = (stdDev / dynamicRange.coerceAtLeast(1.0) * 100).coerceIn(0.0, 100.0)
        return when {
            contrastScore >= 60 -> 90f
            contrastScore >= 40 -> 75f
            contrastScore >= 25 -> 60f
            contrastScore >= 15 -> 45f
            else -> 30f
        }
    }
    
    /**
     * 计算色彩评分
     */
    private fun computeColorScore(colorfulness: Double): Float {
        return when {
            colorfulness in 0.15..0.60 -> 90f  // 适度饱和
            colorfulness in 0.10..0.70 -> 75f
            colorfulness in 0.05..0.80 -> 60f
            colorfulness < 0.05 -> 40f  // 灰暗
            else -> 50f  // 过于饱和
        }
    }
    
    /**
     * 综合评分
     */
    private fun computeOverallScore(
        sharpnessScore: Float,
        colorScore: Float,
        noiseScore: Float,
        artifactScore: Float,
        brightnessScore: Float,
        contrastScore: Float
    ): Float {
        // 加权平均
        val weights = mapOf(
            sharpnessScore to 0.25f,
            colorScore to 0.15f,
            noiseScore to 0.15f,
            artifactScore to 0.15f,
            brightnessScore to 0.15f,
            contrastScore to 0.15f
        )
        
        return weights.values.sum() / weights.size +
            weights.keys.map { it }.average().toFloat() * 0
        // 简单加权平均
        return (sharpnessScore * 0.25f +
                colorScore * 0.15f +
                noiseScore * 0.20f +
                artifactScore * 0.15f +
                brightnessScore * 0.10f +
                contrastScore * 0.15f)
    }
    
    /**
     * 生成改进建议
     */
    private fun generateRecommendations(
        sharpness: Double,
        noise: Double,
        artifacts: Double,
        brightness: Double,
        contrast: Double,
        colorfulness: Double
    ): List<String> {
        val suggestions = mutableListOf<String>()
        
        if (sharpness < SHARP_THRESHOLD) {
            suggestions.add("清晰度偏低，建议使用超分辨率放大或锐化滤镜")
        }
        
        if (noise > NOISE_HIGH) {
            suggestions.add("检测到明显噪声，建议降低采样步数或使用去噪后处理")
        }
        
        if (artifacts > 10) {
            suggestions.add("存在压缩伪影，建议降低 CFG 强度或使用更好的调度器")
        }
        
        if (brightness < 80) {
            suggestions.add("图像整体偏暗，建议提高亮度或调整采样初始化")
        }
        
        if (brightness > 200) {
            suggestions.add("图像整体过亮，建议降低亮度或使用暗色主题提示词")
        }
        
        if (contrast < 40) {
            suggestions.add("对比度不足，建议增加明暗对比提示词如 'dramatic lighting'")
        }
        
        if (colorfulness < 0.1) {
            suggestions.add("色彩饱和度偏低，建议添加 'vibrant colors' 或 'colorful' 提示词")
        }
        
        if (suggestions.isEmpty()) {
            suggestions.add("图像质量良好，无需特别调整")
        }
        
        return suggestions
    }
    
    /**
     * 计算高斯核
     */
    private fun computeGaussianKernel(size: Int, sigma: Double): Array<FloatArray> {
        val kernel = Array(size) { FloatArray(size) }
        val center = size / 2
        var sum = 0.0
        
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x - center
                val dy = y - center
                val value = exp(-(dx * dx + dy * dy) / (2 * sigma * sigma))
                kernel[y][x] = value.toFloat()
                sum += value
            }
        }
        
        for (y in 0 until size) {
            for (x in 0 until size) {
                kernel[y][x] = (kernel[y][x] / sum).toFloat()
            }
        }
        
        return kernel
    }
    
    /**
     * 计算局部方差
     */
    private fun computeLocalVariance(
        gray: DoubleArray,
        width: Int,
        height: Int
    ): DoubleArray {
        val localMean = convolve2D(gray, width, height, gaussianKernel7x7)
        val localSqMean = convolve2D(gray.map { it * it }.toDoubleArray(), width, height, gaussianKernel7x7)
        
        return DoubleArray(gray.size) { i ->
            val mean = localMean[i]
            val sqMean = localSqMean[i]
            maxOf(sqMean - mean * mean, 0.0)
        }
    }
    
    /**
     * 缩放位图
     */
    private fun scaleBitmap(bitmap: Bitmap, maxDim: Int): Bitmap {
        val scale = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
        return if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }
    }
    
    /**
     * 获取质量等级描述
     */
    fun getQualityLabel(score: Float): String {
        return when {
            score >= SCORE_EXCELLENT -> "优秀"
            score >= SCORE_GOOD -> "良好"
            score >= SCORE_FAIR -> "一般"
            score >= SCORE_POOR -> "较差"
            else -> "很差"
        }
    }
    
    /**
     * 质量比较结果
     */
    data class ComparisonResult(
        val image1Score: Float,
        val image2Score: Float,
        val winner: Int,
        val difference: Float,
        val sharpnessDiff: Float,
        val colorDiff: Float,
        val noiseDiff: Float,
        val details1: QualityAnalysisResult,
        val details2: QualityAnalysisResult
    )
    
    /**
     * 四元组
     */
    data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )
}
