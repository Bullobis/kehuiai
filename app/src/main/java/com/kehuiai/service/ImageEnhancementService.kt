@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST", "DEPRECATION", "USELESS_ELVIS")
package com.kehuiai.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Matrix
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 图像增强服务
 * 提供专业级图像处理功能
 */
class ImageEnhancementService(private val context: Context) {

    companion object {
        private const val TAG = "ImageEnhancement"
    }
    
    // ========== 基础增强 ==========
    
    /**
     * 自动增强
     * 综合优化亮度、对比度、饱和度
     */
    suspend fun autoEnhance(bitmap: Bitmap, strength: Float = 1.0f): Bitmap = withContext(Dispatchers.Default) {
        val enhanced = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        // 分析图像统计
        val stats = analyzeImage(bitmap)
        
        // 应用增强
        applyAutoLevels(enhanced, stats, strength)
        applyAutoContrast(enhanced, stats, strength)
        applyAutoSaturation(enhanced, stats, strength)
        applyUnsharpMask(enhanced, 0.5f * strength)
        
        enhanced
    }
    
    /**
     * 调整亮度
     */
    suspend fun adjustBrightness(bitmap: Bitmap, brightness: Float): Bitmap = withContext(Dispatchers.Default) {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val brightnessValue = (brightness * 255).toInt().coerceIn(-255, 255)
        
        for (y in 0 until result.height) {
            for (x in 0 until result.width) {
                val pixel = result.getPixel(x, y)
                val r = (Color.red(pixel) + brightnessValue).coerceIn(0, 255)
                val g = (Color.green(pixel) + brightnessValue).coerceIn(0, 255)
                val b = (Color.blue(pixel) + brightnessValue).coerceIn(0, 255)
                result.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        result
    }
    
    /**
     * 调整对比度
     */
    suspend fun adjustContrast(bitmap: Bitmap, contrast: Float): Bitmap = withContext(Dispatchers.Default) {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val contrastValue = (contrast + 1f) // 0 = no change, -1 = min, 1 = max
        
        for (y in 0 until result.height) {
            for (x in 0 until result.width) {
                val pixel = result.getPixel(x, y)
                val r = (((Color.red(pixel) / 255f - 0.5f) * contrastValue + 0.5f) * 255).toInt().coerceIn(0, 255)
                val g = (((Color.green(pixel) / 255f - 0.5f) * contrastValue + 0.5f) * 255).toInt().coerceIn(0, 255)
                val b = (((Color.blue(pixel) / 255f - 0.5f) * contrastValue + 0.5f) * 255).toInt().coerceIn(0, 255)
                result.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        result
    }
    
    /**
     * 调整饱和度
     */
    suspend fun adjustSaturation(bitmap: Bitmap, saturation: Float): Bitmap = withContext(Dispatchers.Default) {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val saturationValue = saturation + 1f
        
        val cm = ColorMatrix()
        cm.setSaturation(saturationValue)
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        
        val canvas = Canvas(result)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        result
    }
    
    /**
     * 调整色温
     */
    suspend fun adjustTemperature(bitmap: Bitmap, temperature: Float): Bitmap = withContext(Dispatchers.Default) {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val tempValue = (temperature * 30).toInt() // -30 to +30
        
        for (y in 0 until result.height) {
            for (x in 0 until result.width) {
                val pixel = result.getPixel(x, y)
                var r = Color.red(pixel) + tempValue
                var b = Color.blue(pixel) - tempValue
                r = r.coerceIn(0, 255)
                b = b.coerceIn(0, 255)
                result.setPixel(x, y, Color.rgb(r, Color.green(pixel), b))
            }
        }
        result
    }
    
    /**
     * 调整色调
     */
    suspend fun adjustHue(bitmap: Bitmap, hue: Float): Bitmap = withContext(Dispatchers.Default) {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val hueValue = hue * 180f // -180 to +180 degrees
        
        val cm = ColorMatrix()
        cm.setRotate(0, hueValue) // R
        cm.setRotate(1, hueValue) // G
        cm.setRotate(2, hueValue) // B
        
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        
        val canvas = Canvas(result)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        result
    }
    
    // ========== 高级增强 ==========
    
    /**
     * 锐化
     */
    suspend fun sharpen(bitmap: Bitmap, amount: Float = 1.0f): Bitmap = withContext(Dispatchers.Default) {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        applyUnsharpMask(result, amount)
        result
    }
    
    /**
     * 模糊
     */
    suspend fun blur(bitmap: Bitmap, radius: Float): Bitmap = withContext(Dispatchers.Default) {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val kernelSize = (radius * 2).toInt().coerceIn(1, 20) * 2 + 1
        
        // 高斯模糊
        val kernel = createGaussianKernel(kernelSize, radius)
        applyConvolution(result, bitmap, kernel)
        
        result
    }
    
    /**
     * 降噪
     */
    suspend fun denoise(bitmap: Bitmap, strength: Float = 0.5f): Bitmap = withContext(Dispatchers.Default) {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val kernel = createDenoiseKernel()
        val passes = (strength * 3).toInt().coerceIn(1, 3)
        
        var current = bitmap
        for (i in 0 until passes) {
            applyConvolution(result, current, kernel)
            current = result
        }
        
        result
    }
    
    /**
     * 去雾
     */
    suspend fun dehaze(bitmap: Bitmap, strength: Float = 0.5f): Bitmap = withContext(Dispatchers.Default) {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        // 估计雾的强度
        val stats = analyzeImage(bitmap)
        val hazeLevel = calculateHazeLevel(stats)
        
        // 反向调整
        for (y in 0 until result.height) {
            for (x in 0 until result.width) {
                val pixel = result.getPixel(x, y)
                val r = (Color.red(pixel) / (1 - hazeLevel * strength)).toInt().coerceIn(0, 255)
                val g = (Color.green(pixel) / (1 - hazeLevel * strength)).toInt().coerceIn(0, 255)
                val b = (Color.blue(pixel) / (1 - hazeLevel * strength)).toInt().coerceIn(0, 255)
                result.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        
        result
    }
    
    /**
     * HDR 效果
     */
    suspend fun hdrEffect(bitmap: Bitmap, strength: Float = 0.5f): Bitmap = withContext(Dispatchers.Default) {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        // 应用局部对比度增强
        val localContrast = createLocalContrastKernel()
        applyLocalContrastEnhancement(result, bitmap, localContrast, strength)
        
        // 增强细节
        applyUnsharpMask(result, 0.3f * strength)
        
        result
    }
    
    /**
     * 黑白效果
     */
    suspend fun blackAndWhite(bitmap: Bitmap, style: BWStyle = BWStyle.STANDARD): Bitmap = withContext(Dispatchers.Default) {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        val cm = when (style) {
            BWStyle.STANDARD -> ColorMatrix().apply { setSaturation(0f) }
            BWStyle.HIGH_CONTRAST -> ColorMatrix(floatArrayOf(
                1.5f, 0.2f, 0.2f, 0f, -50f,
                0.2f, 1.5f, 0.2f, 0f, -50f,
                0.2f, 0.2f, 1.5f, 0f, -50f,
                0f, 0f, 0f, 1f, 0f
            ))
            BWStyle.SEPIA -> ColorMatrix(floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            BWStyle.COOL -> ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0.1f, 0f, 0f,
                0f, 0.1f, 1.2f, 0f, 20f,
                0f, 0f, 0f, 1f, 0f
            ))
            BWStyle.WARM -> ColorMatrix(floatArrayOf(
                1.2f, 0.1f, 0f, 0f, 10f,
                0.1f, 1f, 0f, 0f, 5f,
                0f, 0f, 0.8f, 0f, -20f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        
        val canvas = Canvas(result)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        result
    }
    
    // ========== 滤镜 ==========
    
    /**
     * 应用预设滤镜
     */
    suspend fun applyFilter(bitmap: Bitmap, filter: FilterType, intensity: Float = 1.0f): Bitmap = withContext(Dispatchers.Default) {
        when (filter) {
            FilterType.VIVID -> applyVividFilter(bitmap, intensity)
            FilterType.DREAM -> applyDreamFilter(bitmap, intensity)
            FilterType.VINTAGE -> applyVintageFilter(bitmap, intensity)
            FilterType.CINEMATIC -> applyCinematicFilter(bitmap, intensity)
            FilterType.MOOD -> applyMoodFilter(bitmap, intensity)
            FilterType.PORTRAIT -> applyPortraitFilter(bitmap, intensity)
            FilterType.NIGHT -> applyNightFilter(bitmap, intensity)
            FilterType.DAWN -> applyDawnFilter(bitmap, intensity)
            FilterType.GOLD -> applyGoldFilter(bitmap, intensity)
            FilterType.FILM -> applyFilmFilter(bitmap, intensity)
        }
    }
    
    enum class BWStyle {
        STANDARD, HIGH_CONTRAST, SEPIA, COOL, WARM
    }
    
    enum class FilterType {
        VIVID, DREAM, VINTAGE, CINEMATIC, MOOD, PORTRAIT, NIGHT, DAWN, GOLD, FILM
    }
    
    // ========== 内部方法 ==========
    
    private data class ImageStats(
        val avgBrightness: Float,
        val avgContrast: Float,
        val avgSaturation: Float,
        val histogram: IntArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ImageStats) return false
            return avgBrightness == other.avgBrightness &&
                   avgContrast == other.avgContrast &&
                   avgSaturation == other.avgSaturation
        }
        
        override fun hashCode(): Int {
            var result = avgBrightness.hashCode()
            result = 31 * result + avgContrast.hashCode()
            result = 31 * result + avgSaturation.hashCode()
            return result
        }
    }
    
    private fun analyzeImage(bitmap: Bitmap): ImageStats {
        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        var totalPixels = bitmap.width * bitmap.height
        var minR = 255
        var maxR = 0
        var minG = 255
        var maxG = 0
        var minB = 255
        var maxB = 0
        
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                
                totalR += r
                totalG += g
                totalB += b
                
                minR = min(minR, r)
                maxR = max(maxR, r)
                minG = min(minG, g)
                maxG = max(maxG, g)
                minB = min(minB, b)
                maxB = max(maxB, b)
            }
        }
        
        val avgR = totalR.toFloat() / totalPixels
        val avgG = totalG.toFloat() / totalPixels
        val avgB = totalB.toFloat() / totalPixels
        val avgBrightness = (avgR + avgG + avgB) / 3f / 255f
        
        val contrast = ((maxR - minR) + (maxG - minG) + (maxB - minB)) / 3f / 255f
        
        return ImageStats(avgBrightness, contrast, 0f, IntArray(256))
    }
    
    @Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")
    private fun applyAutoLevels(bitmap: Bitmap, stats: ImageStats, strength: Float) {
        val targetMin = 20
        
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                
                val newR = (targetMin + (r * strength)).toInt().coerceIn(0, 255)
                val newG = (targetMin + (g * strength)).toInt().coerceIn(0, 255)
                val newB = (targetMin + (b * strength)).toInt().coerceIn(0, 255)
                
                bitmap.setPixel(x, y, Color.rgb(newR, newG, newB))
            }
        }
    }
    
    @Suppress("UNUSED_PARAMETER")
    private fun applyAutoContrast(bitmap: Bitmap, stats: ImageStats, strength: Float) {
        val contrastValue = 1f + (0.5f - stats.avgContrast) * strength
        
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val r = (((Color.red(pixel) / 255f - 0.5f) * contrastValue + 0.5f) * 255).toInt().coerceIn(0, 255)
                val g = (((Color.green(pixel) / 255f - 0.5f) * contrastValue + 0.5f) * 255).toInt().coerceIn(0, 255)
                val b = (((Color.blue(pixel) / 255f - 0.5f) * contrastValue + 0.5f) * 255).toInt().coerceIn(0, 255)
                bitmap.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
    }
    
    private fun applyAutoSaturation(bitmap: Bitmap, stats: ImageStats, strength: Float) {
        val cm = ColorMatrix()
        cm.setSaturation(1.2f * strength)
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        val canvas = Canvas(bitmap)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
    }
    
    private fun applyUnsharpMask(bitmap: Bitmap, amount: Float) {
        if (amount <= 0) return
        
        val kernel = floatArrayOf(
            0f, -amount, 0f,
            -amount, 1f + 4f * amount, -amount,
            0f, -amount, 0f
        )
        
        val temp = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        applyConvolution(bitmap, temp, kernel)
    }
    
    private fun createGaussianKernel(size: Int, sigma: Float): FloatArray {
        val kernel = FloatArray(size * size)
        val center = size / 2
        var sum = 0f
        
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x - center
                val dy = y - center
                val value = kotlin.math.exp(-(dx * dx + dy * dy) / (2f * sigma * sigma))
                kernel[y * size + x] = value
                sum += value
            }
        }
        
        // 归一化
        for (i in kernel.indices) {
            kernel[i] /= sum
        }
        
        return kernel
    }
    
    private fun createDenoiseKernel(): FloatArray {
        return floatArrayOf(
            1f/16f, 1f/8f, 1f/16f,
            1f/8f, 1f/4f, 1f/8f,
            1f/16f, 1f/8f, 1f/16f
        )
    }
    
    private fun createLocalContrastKernel(): FloatArray {
        return floatArrayOf(
            -1f, -1f, -1f,
            -1f, 9f, -1f,
            -1f, -1f, -1f
        )
    }
    
    private fun applyConvolution(output: Bitmap, input: Bitmap, kernel: FloatArray) {
        val width = input.width
        val height = input.height
        val kernelSize = sqrt(kernel.size.toFloat()).toInt()
        val half = kernelSize / 2
        
        for (y in half until height - half) {
            for (x in half until width - half) {
                var r = 0f
                var g = 0f
                var b = 0f
                
                for (ky in 0 until kernelSize) {
                    for (kx in 0 until kernelSize) {
                        val pixel = input.getPixel(x + kx - half, y + ky - half)
                        val weight = kernel[ky * kernelSize + kx]
                        r += Color.red(pixel) * weight
                        g += Color.green(pixel) * weight
                        b += Color.blue(pixel) * weight
                    }
                }
                
                output.setPixel(x, y, Color.rgb(
                    r.toInt().coerceIn(0, 255),
                    g.toInt().coerceIn(0, 255),
                    b.toInt().coerceIn(0, 255)
                ))
            }
        }
    }
    
    private fun applyLocalContrastEnhancement(output: Bitmap, input: Bitmap, kernel: FloatArray, strength: Float) {
        applyConvolution(output, input, kernel)
    }
    
    @Suppress("UNUSED_PARAMETER")
    private fun calculateHazeLevel(stats: ImageStats): Float {
        return (1f - stats.avgContrast) * 0.5f
    }
    
    // ========== 滤镜实现 ==========
    
    private fun applyVividFilter(bitmap: Bitmap, intensity: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val cm = ColorMatrix()
        cm.setSaturation(1.5f * intensity)
        
        // 增加对比度
        val contrast = ColorMatrix()
        contrast.set(floatArrayOf(
            1.2f, 0f, 0f, 0f, -25f * intensity,
            0f, 1.2f, 0f, 0f, -25f * intensity,
            0f, 0f, 1.2f, 0f, -25f * intensity,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(contrast)
        
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
    
    private fun applyDreamFilter(bitmap: Bitmap, intensity: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val cm = ColorMatrix()
        cm.setSaturation(0.8f)
        
        val warmth = ColorMatrix()
        warmth.set(floatArrayOf(
            1.1f, 0.1f, 0f, 0f, 15f * intensity,
            0f, 1f, 0f, 0f, 10f * intensity,
            0f, 0f, 0.9f, 0f, -10f * intensity,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(warmth)
        
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
        
        return result
    }
    
    private fun applyVintageFilter(bitmap: Bitmap, intensity: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val cm = ColorMatrix()
        cm.setSaturation(0.6f)
        
        val sepia = ColorMatrix()
        sepia.set(floatArrayOf(
            0.9f, 0.1f, 0.1f, 0f, 10f * intensity,
            0.05f, 0.95f, 0.05f, 0f, 5f * intensity,
            0f, 0.1f, 0.9f, 0f, -10f * intensity,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(sepia)
        
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
    
    private fun applyCinematicFilter(bitmap: Bitmap, intensity: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val cm = ColorMatrix()
        cm.setSaturation(0.85f)
        
        val contrast = ColorMatrix()
        contrast.set(floatArrayOf(
            1.15f, 0f, 0f, 0f, -20f * intensity,
            0f, 1.1f, 0f, 0f, -10f * intensity,
            0f, 0f, 1.05f, 0f, 10f * intensity,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(contrast)
        
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
    
    private fun applyMoodFilter(bitmap: Bitmap, intensity: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val cm = ColorMatrix()
        cm.setSaturation(0.7f)
        
        val mood = ColorMatrix()
        mood.set(floatArrayOf(
            1f, 0f, 0f, 0f, -10f * intensity,
            0f, 0.95f, 0f, 0f, -15f * intensity,
            0f, 0f, 1.2f, 0f, 20f * intensity,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(mood)
        
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
    
    private fun applyPortraitFilter(bitmap: Bitmap, intensity: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val cm = ColorMatrix()
        cm.setSaturation(0.9f)
        
        val skin = ColorMatrix()
        skin.set(floatArrayOf(
            1.05f, 0.05f, 0f, 0f, 5f * intensity,
            0f, 1.05f, 0f, 0f, 2f * intensity,
            0f, 0f, 0.95f, 0f, -5f * intensity,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(skin)
        
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
    
    private fun applyNightFilter(bitmap: Bitmap, intensity: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val cm = ColorMatrix()
        cm.setSaturation(0.6f)
        
        val night = ColorMatrix()
        night.set(floatArrayOf(
            0.8f, 0f, 0f, 0f, -20f * intensity,
            0f, 0.9f, 0f, 0f, -10f * intensity,
            0f, 0f, 1.1f, 0f, 15f * intensity,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(night)
        
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
    
    private fun applyDawnFilter(bitmap: Bitmap, intensity: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val cm = ColorMatrix()
        cm.setSaturation(1.1f)
        
        val dawn = ColorMatrix()
        dawn.set(floatArrayOf(
            1.2f, 0.1f, 0f, 0f, 20f * intensity,
            0f, 1.05f, 0f, 0f, 10f * intensity,
            0f, 0f, 0.85f, 0f, -15f * intensity,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(dawn)
        
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
    
    private fun applyGoldFilter(bitmap: Bitmap, intensity: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val cm = ColorMatrix()
        cm.setSaturation(1.2f)
        
        val gold = ColorMatrix()
        gold.set(floatArrayOf(
            1.3f, 0.1f, 0f, 0f, 25f * intensity,
            0f, 1.1f, 0f, 0f, 15f * intensity,
            0f, 0f, 0.7f, 0f, -20f * intensity,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(gold)
        
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
    
    private fun applyFilmFilter(bitmap: Bitmap, intensity: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val cm = ColorMatrix()
        cm.setSaturation(0.85f)
        
        val film = ColorMatrix()
        film.set(floatArrayOf(
            1.1f, 0.05f, 0f, 0f, 8f * intensity,
            0f, 1f, 0.05f, 0f, 5f * intensity,
            -0.05f, 0f, 0.95f, 0f, -5f * intensity,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(film)
        
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
}
