@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST", "DEPRECATION", "USELESS_ELVIS")
package com.kehuiai.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * 专业色彩分级服务
 * 提供电影级调色功能
 */
class ColorGradingService(private val context: Context) {

    companion object {
        private const val TAG = "ColorGrading"
        @Volatile
        private var instance: ColorGradingService? = null
        
        fun getInstance(): ColorGradingService {
            return instance ?: synchronized(this) {
                instance ?: throw IllegalStateException("Context required")
            }
        }
    }
    
    // ========== 预设调色方案 ==========
    
    enum class ColorGrade(val displayName: String, val emoji: String) {
        NONE("原片", "📷"),
        CINEMATIC("电影感", "🎬"),
        TEAL_ORANGE("青橙色", "🟠"),
        FUJI("富士胶片", "🍃"),
        KODAK("柯达胶片", "🎞️"),
        PORTRAIT("人像优化", "👤"),
        MOODY("忧郁暗调", "🌑"),
        VIBRANT("活力饱和", "🌈"),
        PASTEL("马卡龙", "🧁"),
        NOIR("黑白电影", "🎭"),
        SEPIA("棕褐怀旧", "📜"),
        CROSS_PROCESS("交叉冲洗", "🔀")
    }
    
    data class ColorGradeParams(
        val shadows: Float = 0f,      // 阴影调整 -1~1
        val midtones: Float = 0f,     // 中间调调整 -1~1
        val highlights: Float = 0f,    // 高光调整 -1~1
        val temperature: Float = 0f,   // 色温 -1~1 (冷~暖)
        val tint: Float = 0f,         // 色调 -1~1 (绿~紫)
        val vibrance: Float = 0f,     // 自然饱和度 -1~1
        val saturation: Float = 0f,   // 饱和度 -1~1
        val contrast: Float = 0f,     // 对比度 -1~1
        val gamma: Float = 0f,        // 伽马 -1~1
        val lift: Float = 0f,         // 提升 -1~1
        val grain: Float = 0f,        // 颗粒 0~1
        val vignette: Float = 0f,     // 暗角 0~1
        val fade: Float = 0f,          // 褪色 0~1
        val crushBlacks: Boolean = false,  // 压黑
        val crushWhites: Boolean = false   // 压白
    )
    
    // ========== 应用调色 ==========
    
    suspend fun applyGrade(bitmap: Bitmap, grade: ColorGrade): Bitmap = withContext(Dispatchers.Default) {
        val params = when (grade) {
            ColorGrade.NONE -> ColorGradeParams()
            ColorGrade.CINEMATIC -> ColorGradeParams(
                shadows = -0.05f,
                highlights = -0.1f,
                temperature = -0.1f,
                vibrance = 0.1f,
                contrast = 0.15f,
                vignette = 0.3f
            )
            ColorGrade.TEAL_ORANGE -> ColorGradeParams(
                shadows = -0.15f,
                midtones = 0.1f,
                highlights = 0.1f,
                temperature = 0f,
                tint = 0.05f,
                vibrance = 0.15f,
                contrast = 0.1f,
                vignette = 0.25f
            )
            ColorGrade.FUJI -> ColorGradeParams(
                shadows = 0.05f,
                highlights = 0.1f,
                temperature = 0.05f,
                vibrance = 0.05f,
                contrast = -0.05f,
                fade = 0.1f
            )
            ColorGrade.KODAK -> ColorGradeParams(
                shadows = 0.08f,
                midtones = 0.05f,
                temperature = 0.08f,
                vibrance = 0.08f,
                contrast = 0.05f,
                grain = 0.08f
            )
            ColorGrade.PORTRAIT -> ColorGradeParams(
                shadows = 0.05f,
                midtones = 0.08f,
                temperature = 0.02f,
                vibrance = 0.05f,
                contrast = 0.03f
            )
            ColorGrade.MOODY -> ColorGradeParams(
                shadows = -0.15f,
                midtones = -0.05f,
                highlights = -0.1f,
                temperature = -0.1f,
                contrast = 0.2f,
                vignette = 0.4f,
                crushBlacks = true
            )
            ColorGrade.VIBRANT -> ColorGradeParams(
                vibrance = 0.3f,
                saturation = 0.25f,
                contrast = 0.1f
            )
            ColorGrade.PASTEL -> ColorGradeParams(
                shadows = 0.1f,
                midtones = 0.15f,
                highlights = 0.1f,
                vibrance = 0.2f,
                saturation = -0.15f,
                contrast = -0.15f,
                fade = 0.15f
            )
            ColorGrade.NOIR -> ColorGradeParams(
                saturation = -1f,
                contrast = 0.3f,
                vignette = 0.5f,
                crushBlacks = true,
                crushWhites = true
            )
            ColorGrade.SEPIA -> ColorGradeParams(
                saturation = -0.9f,
                temperature = 0.3f,
                contrast = 0.05f,
                vignette = 0.2f
            )
            ColorGrade.CROSS_PROCESS -> ColorGradeParams(
                shadows = 0.1f,
                highlights = -0.1f,
                midtones = 0.15f,
                temperature = -0.15f,
                tint = 0.1f,
                vibrance = 0.2f,
                contrast = 0.1f
            )
        }
        applyGradeWithParams(bitmap, params)
    }
    
    suspend fun applyGradeWithParams(bitmap: Bitmap, params: ColorGradeParams): Bitmap = withContext(Dispatchers.Default) {
        var result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        // 1. 基础色彩调整
        result = applyBasicAdjustments(result, params)
        
        // 2. 分离色调（阴影/高光）
        result = applySplitToning(result, params)
        
        // 3. 应用 LUT 效果（通过矩阵模拟）
        result = applyLUTEffect(result, params)
        
        // 4. 颗粒感
        if (params.grain > 0f) {
            result = applyGrain(result, params.grain)
        }
        
        // 5. 暗角
        if (params.vignette > 0f) {
            result = applyVignette(result, params.vignette)
        }
        
        // 6. 褪色效果
        if (params.fade > 0f) {
            result = applyFade(result, params.fade)
        }
        
        // 7. 压黑/压白
        if (params.crushBlacks) {
            result = crushBlacks(result)
        }
        if (params.crushWhites) {
            result = crushWhites(result)
        }
        
        result
    }
    
    // ========== 基础调整 ==========
    
    private fun applyBasicAdjustments(bitmap: Bitmap, params: ColorGradeParams): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        // 构建颜色矩阵
        val cm = ColorMatrix()
        
        // 饱和度
        if (params.saturation != 0f) {
            val satMatrix = ColorMatrix()
            satMatrix.setSaturation(1f + params.saturation)
            cm.postConcat(satMatrix)
        }
        
        // 自然饱和度
        if (params.vibrance != 0f) {
            // 简化实现
            val vibranceMatrix = ColorMatrix()
            vibranceMatrix.setSaturation(1f + params.vibrance * 0.5f)
            cm.postConcat(vibranceMatrix)
        }
        
        // 对比度
        if (params.contrast != 0f) {
            val contrastValue = 1f + params.contrast
            val translate = (-0.5f * contrastValue + 0.5f) * 255
            val contrastMatrix = ColorMatrix(floatArrayOf(
                contrastValue, 0f, 0f, 0f, translate,
                0f, contrastValue, 0f, 0f, translate,
                0f, 0f, contrastValue, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.postConcat(contrastMatrix)
        }
        
        // 亮度（通过伽马模拟）
        if (params.gamma != 0f) {
            val gammaValue = 1f - params.gamma * 0.5f
            val r = 1f / gammaValue
            val g = 1f / gammaValue
            val b = 1f / gammaValue
            val gammaMatrix = ColorMatrix(floatArrayOf(
                r, 0f, 0f, 0f, 0f,
                0f, g, 0f, 0f, 0f,
                0f, 0f, b, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.postConcat(gammaMatrix)
        }
        
        // 色温
        if (params.temperature != 0f) {
            val tempValue = params.temperature * 30f
            val tempMatrix = ColorMatrix(floatArrayOf(
                1f + tempValue / 255f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f - tempValue / 255f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.postConcat(tempMatrix)
        }
        
        // 色调
        if (params.tint != 0f) {
            val tintValue = params.tint * 20f
            val tintMatrix = ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f - tintValue / 255f, tintValue / 255f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.postConcat(tintMatrix)
        }
        
        // 应用
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
        
        return result
    }
    
    // ========== 分离色调 ==========
    
    private fun applySplitToning(bitmap: Bitmap, params: ColorGradeParams): Bitmap {
        if (params.shadows == 0f && params.midtones == 0f && params.highlights == 0f) {
            return bitmap
        }
        
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        // 简化实现：对整体应用中间调调整
        if (params.midtones != 0f) {
            val cm = ColorMatrix()
            val midValue = params.midtones * 0.1f
            
            // 在中间色调区域添加色彩
            val r = if (params.midtones > 0) 1f + midValue else 1f
            val b = if (params.midtones < 0) 1f + midValue else 1f
            
            cm.set(floatArrayOf(
                r, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, b, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            
            val paint = Paint()
            paint.colorFilter = ColorMatrixColorFilter(cm)
            Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
        }
        
        return result
    }
    
    // ========== LUT 效果 ==========
    
    private fun applyLUTEffect(bitmap: Bitmap, params: ColorGradeParams): Bitmap {
        // 这里可以加载真实的 3D LUT 文件
        // 暂时通过颜色矩阵模拟简单的 LUT 效果
        return bitmap
    }
    
    // ========== 颗粒感 ==========
    
    private fun applyGrain(bitmap: Bitmap, intensity: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val random = java.util.Random()
        
        for (y in 0 until result.height) {
            for (x in 0 until result.width) {
                if (random.nextFloat() < intensity * 0.3f) {
                    val noise = (random.nextFloat() - 0.5f) * intensity * 40
                    val pixel = result.getPixel(x, y)
                    val r = (Color.red(pixel) + noise).toInt().coerceIn(0, 255)
                    val g = (Color.green(pixel) + noise).toInt().coerceIn(0, 255)
                    val b = (Color.blue(pixel) + noise).toInt().coerceIn(0, 255)
                    result.setPixel(x, y, Color.rgb(r, g, b))
                }
            }
        }
        
        return result
    }
    
    // ========== 暗角 ==========
    
    private fun applyVignette(bitmap: Bitmap, intensity: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val width = bitmap.width
        val height = bitmap.height
        val centerX = width / 2f
        val centerY = height / 2f
        val maxRadius = kotlin.math.sqrt(centerX * centerX + centerY * centerY)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - centerX
                val dy = y - centerY
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                val normalizedDist = distance / maxRadius
                
                if (normalizedDist > 0.5f) {
                    val factor = 1f - (normalizedDist - 0.5f) * 2f * intensity
                    val pixel = result.getPixel(x, y)
                    val r = (Color.red(pixel) * factor).toInt().coerceIn(0, 255)
                    val g = (Color.green(pixel) * factor).toInt().coerceIn(0, 255)
                    val b = (Color.blue(pixel) * factor).toInt().coerceIn(0, 255)
                    result.setPixel(x, y, Color.rgb(r, g, b))
                }
            }
        }
        
        return result
    }
    
    // ========== 褪色 ==========
    
    private fun applyFade(bitmap: Bitmap, intensity: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val fadeAmount = intensity * 30f
        
        for (y in 0 until result.height) {
            for (x in 0 until result.width) {
                val pixel = result.getPixel(x, y)
                val r = (Color.red(pixel) + fadeAmount).toInt().coerceIn(0, 255)
                val g = (Color.green(pixel) + fadeAmount).toInt().coerceIn(0, 255)
                val b = (Color.blue(pixel) + fadeAmount).toInt().coerceIn(0, 255)
                result.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        
        return result
    }
    
    // ========== 压黑 ==========
    
    private fun crushBlacks(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val threshold = 20
        
        for (y in 0 until result.height) {
            for (x in 0 until result.width) {
                val pixel = result.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                
                if (r < threshold && g < threshold && b < threshold) {
                    result.setPixel(x, y, Color.BLACK)
                }
            }
        }
        
        return result
    }
    
    // ========== 压白 ==========
    
    private fun crushWhites(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val threshold = 245
        
        for (y in 0 until result.height) {
            for (x in 0 until result.width) {
                val pixel = result.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                
                if (r > threshold && g > threshold && b > threshold) {
                    result.setPixel(x, y, Color.WHITE)
                }
            }
        }
        
        return result
    }
    
    // ========== 工具方法 ==========
    
    fun getAllGrades(): List<ColorGrade> = ColorGrade.entries
    
    suspend fun previewGrades(bitmap: Bitmap): Map<ColorGrade, Bitmap> {
        return ColorGrade.entries.associateWith { grade ->
            applyGrade(bitmap, grade)
        }
    }
}
