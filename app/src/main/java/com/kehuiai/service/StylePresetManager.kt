@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST", "DEPRECATION", "USELESS_ELVIS")
package com.kehuiai.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

/**
 * 风格预设管理器
 * 提供丰富的预设风格
 */
class StylePresetManager(private val context: Context) {

    companion object {
        private const val TAG = "StylePreset"
    }
    
    // ========== 预设风格 ==========
    
    data class StylePreset(
        val id: String,
        val name: String,
        val description: String,
        val emoji: String,
        val colorMatrix: FloatArray,
        val saturation: Float = 1f,
        val brightness: Float = 0f,
        val contrast: Float = 1f,
        val vignette: Float = 0f,
        val grain: Float = 0f
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is StylePreset) return false
            return id == other.id
        }
        
        override fun hashCode(): Int = id.hashCode()
    }
    
    // 获取所有预设
    fun getAllPresets(): List<StylePreset> = listOf(
        // 经典风格
        StylePreset(
            id = "natural",
            name = "自然",
            description = "保留原始色彩，自然真实",
            emoji = "🌿",
            colorMatrix = identityMatrix(),
            saturation = 1.05f,
            contrast = 1.05f
        ),
        StylePreset(
            id = "vivid",
            name = "鲜艳",
            description = "增强色彩饱和度，活力四射",
            emoji = "🎨",
            colorMatrix = identityMatrix(),
            saturation = 1.4f,
            contrast = 1.15f
        ),
        StylePreset(
            id = "soft",
            name = "柔和",
            description = "降低对比度，柔美风格",
            emoji = "🌸",
            colorMatrix = identityMatrix(),
            saturation = 0.9f,
            contrast = 0.92f,
            vignette = 0.2f
        ),
        
        // 复古风格
        StylePreset(
            id = "vintage",
            name = "复古",
            description = "怀旧色调，温暖时光",
            emoji = "📷",
            colorMatrix = vintageMatrix(),
            saturation = 0.75f,
            vignette = 0.4f,
            grain = 0.15f
        ),
        StylePreset(
            id = "faded",
            name = "褪色",
            description = "淡雅的褪色效果",
            emoji = "🖼️",
            colorMatrix = fadedMatrix(),
            saturation = 0.6f,
            contrast = 0.9f,
            vignette = 0.3f
        ),
        StylePreset(
            id = "retro_blue",
            name = "复古蓝调",
            description = "蓝色调的复古风格",
            emoji = "💙",
            colorMatrix = blueRetroMatrix(),
            saturation = 0.8f,
            vignette = 0.35f
        ),
        StylePreset(
            id = "retro_warm",
            name = "复古暖调",
            description = "暖色调的复古风格",
            emoji = "🍂",
            colorMatrix = warmRetroMatrix(),
            saturation = 0.85f,
            vignette = 0.4f
        ),
        
        // 电影风格
        StylePreset(
            id = "cinema",
            name = "电影",
            description = "专业电影调色",
            emoji = "🎬",
            colorMatrix = cinemaMatrix(),
            saturation = 0.9f,
            contrast = 1.2f,
            vignette = 0.5f
        ),
        StylePreset(
            id = "teal_orange",
            name = "青橙",
            description = "好莱坞大片色调",
            emoji = "🌴",
            colorMatrix = tealOrangeMatrix(),
            saturation = 1.1f,
            contrast = 1.15f,
            vignette = 0.45f
        ),
        StylePreset(
            id = "golden_hour",
            name = "黄金时刻",
            description = "夕阳金色调",
            emoji = "🌅",
            colorMatrix = goldenHourMatrix(),
            saturation = 1.2f,
            brightness = 0.05f,
            contrast = 1.1f,
            vignette = 0.3f
        ),
        
        // 艺术风格
        StylePreset(
            id = "portrait",
            name = "人像",
            description = "优化肤色，人像专用",
            emoji = "👤",
            colorMatrix = portraitMatrix(),
            saturation = 0.95f,
            contrast = 1.05f,
            vignette = 0.25f
        ),
        StylePreset(
            id = "beauty",
            name = "美颜",
            description = "磨皮美白效果",
            emoji = "✨",
            colorMatrix = beautyMatrix(),
            saturation = 0.9f,
            contrast = 1.08f,
            vignette = 0.2f
        ),
        StylePreset(
            id = "dramatic",
            name = "戏剧",
            description = "高对比度戏剧效果",
            emoji = "🎭",
            colorMatrix = dramaticMatrix(),
            saturation = 1.05f,
            contrast = 1.4f,
            vignette = 0.55f
        ),
        
        // 黑白风格
        StylePreset(
            id = "bw_classic",
            name = "经典黑白",
            description = "传统黑白照片",
            emoji = "⬛",
            colorMatrix = bwClassicMatrix(),
            saturation = 0f
        ),
        StylePreset(
            id = "bw_high_contrast",
            name = "高对比黑白",
            description = "强烈对比的黑白",
            emoji = "◼️",
            colorMatrix = bwHighContrastMatrix(),
            saturation = 0f,
            contrast = 1.5f
        ),
        StylePreset(
            id = "sepia",
            name = "棕褐",
            description = "怀旧棕褐色调",
            emoji = "🟤",
            colorMatrix = sepiaMatrix(),
            saturation = 0f
        ),
        
        // 创意风格
        StylePreset(
            id = "dream",
            name = "梦幻",
            description = "柔和梦幻效果",
            emoji = "💭",
            colorMatrix = dreamMatrix(),
            saturation = 1.15f,
            brightness = 0.08f,
            contrast = 0.95f,
            vignette = 0.35f
        ),
        StylePreset(
            id = "neon",
            name = "霓虹",
            description = "赛博朋克霓虹",
            emoji = "🌈",
            colorMatrix = neonMatrix(),
            saturation = 1.5f,
            contrast = 1.2f
        ),
        StylePreset(
            id = "cross_process",
            name = "交叉冲洗",
            description = "特殊冲洗效果",
            emoji = "🔮",
            colorMatrix = crossProcessMatrix(),
            saturation = 1.25f,
            contrast = 1.15f,
            vignette = 0.3f
        ),
        StylePreset(
            id = "slide",
            name = "幻灯片",
            description = "彩色幻灯片效果",
            emoji = "📽️",
            colorMatrix = slideFilmMatrix(),
            saturation = 1.1f,
            contrast = 1.05f,
            vignette = 0.25f
        ),
        
        // 专业风格
        StylePreset(
            id = "neutral",
            name = "中性",
            description = "中性灰度精确色彩",
            emoji = "⚖️",
            colorMatrix = neutralMatrix(),
            saturation = 1f,
            contrast = 1f
        ),
        StylePreset(
            id = "flat",
            name = "平面",
            description = "低对比度平面设计",
            emoji = "📐",
            colorMatrix = flatMatrix(),
            saturation = 0.95f,
            contrast = 0.92f
        ),
        StylePreset(
            id = "moody",
            name = "忧郁",
            description = "暗调忧郁氛围",
            emoji = "🌑",
            colorMatrix = moodyMatrix(),
            saturation = 0.85f,
            brightness = -0.1f,
            contrast = 1.15f,
            vignette = 0.5f
        )
    )
    
    // ========== 应用预设 ==========
    
    suspend fun applyPreset(bitmap: Bitmap, preset: StylePreset): Bitmap = withContext(Dispatchers.Default) {
        var result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        // 应用颜色矩阵
        result = applyColorMatrix(result, preset.colorMatrix)
        
        // 调整饱和度
        if (preset.saturation != 1f) {
            result = adjustSaturation(result, preset.saturation)
        }
        
        // 调整对比度
        if (preset.contrast != 1f) {
            result = adjustContrast(result, preset.contrast)
        }
        
        // 调整亮度
        if (preset.brightness != 0f) {
            result = adjustBrightness(result, preset.brightness)
        }
        
        // 添加暗角
        if (preset.vignette > 0f) {
            result = applyVignette(result, preset.vignette)
        }
        
        // 添加颗粒
        if (preset.grain > 0f) {
            result = applyGrain(result, preset.grain)
        }
        
        result
    }
    
    // ========== 颜色矩阵 ==========
    
    private fun identityMatrix() = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
    
    private fun vintageMatrix() = floatArrayOf(
        1.1f, 0.1f, 0.0f, 0f, 10f,
        0.0f, 1.0f, 0.0f, 0f, 5f,
        0.0f, 0.0f, 0.8f, 0f, -10f,
        0f, 0f, 0f, 1f, 0f
    )
    
    private fun fadedMatrix() = floatArrayOf(
        1.0f, 0.0f, 0.0f, 0f, 30f,
        0.0f, 1.0f, 0.0f, 0f, 30f,
        0.0f, 0.0f, 1.0f, 0f, 30f,
        0f, 0f, 0f, 0.9f, 0f
    )
    
    private fun blueRetroMatrix() = floatArrayOf(
        0.9f, 0.0f, 0.1f, 0f, -10f,
        0.0f, 0.95f, 0.1f, 0f, 0f,
        0.1f, 0.1f, 1.2f, 0f, 20f,
        0f, 0f, 0f, 1f, 0f
    )
    
    private fun warmRetroMatrix() = floatArrayOf(
        1.2f, 0.1f, 0.0f, 0f, 15f,
        0.0f, 1.05f, 0.0f, 0f, 10f,
        0.0f, 0.0f, 0.85f, 0f, -15f,
        0f, 0f, 0f, 1f, 0f
    )
    
    private fun cinemaMatrix() = floatArrayOf(
        1.1f, 0.0f, 0.0f, 0f, -15f,
        0.0f, 1.05f, 0.0f, 0f, -10f,
        0.0f, 0.0f, 1.1f, 0f, 10f,
        0f, 0f, 0f, 1f, 0f
    )
    
    private fun tealOrangeMatrix() = floatArrayOf(
        1.2f, 0.0f, 0.0f, 0f, 0f,
        0.0f, 0.95f, 0.1f, 0f, 0f,
        0.0f, 0.1f, 1.1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
    
    private fun goldenHourMatrix() = floatArrayOf(
        1.25f, 0.1f, 0.0f, 0f, 20f,
        0.0f, 1.1f, 0.0f, 0f, 15f,
        0.0f, 0.0f, 0.85f, 0f, -20f,
        0f, 0f, 0f, 1f, 0f
    )
    
    private fun portraitMatrix() = floatArrayOf(
        1.05f, 0.05f, 0.0f, 0f, 3f,
        0.0f, 1.05f, 0.0f, 0f, 2f,
        0.0f, 0.0f, 0.95f, 0f, -3f,
        0f, 0f, 0f, 1f, 0f
    )
    
    private fun beautyMatrix() = floatArrayOf(
        1.08f, 0.03f, 0.0f, 0f, 5f,
        0.0f, 1.08f, 0.0f, 0f, 5f,
        0.0f, 0.0f, 1.05f, 0f, 5f,
        0f, 0f, 0f, 1f, 0f
    )
    
    private fun dramaticMatrix() = floatArrayOf(
        1.3f, 0.0f, 0.0f, 0f, -30f,
        0.0f, 1.3f, 0.0f, 0f, -30f,
        0.0f, 0.0f, 1.3f, 0f, -30f,
        0f, 0f, 0f, 1f, 0f
    )
    
    private fun bwClassicMatrix() = floatArrayOf(
        0.299f, 0.587f, 0.114f, 0f, 0f,
        0.299f, 0.587f, 0.114f, 0f, 0f,
        0.299f, 0.587f, 0.114f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
    
    private fun bwHighContrastMatrix() = floatArrayOf(
        0.35f, 0.6f, 0.05f, 0f, -50f,
        0.35f, 0.6f, 0.05f, 0f, -50f,
        0.35f, 0.6f, 0.05f, 0f, -50f,
        0f, 0f, 0f, 1f, 0f
    )
    
    private fun sepiaMatrix() = floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
    
    private fun dreamMatrix() = floatArrayOf(
        1.1f, 0.1f, 0.1f, 0f, 15f,
        0.0f, 1.05f, 0.1f, 0f, 10f,
        0.0f, 0.0f, 1.15f, 0f, 20f,
        0f, 0f, 0f, 1f, 0f
    )
    
    private fun neonMatrix() = floatArrayOf(
        1.2f, 0.0f, 0.2f, 0f, 20f,
        0.0f, 1.1f, 0.2f, 0f, 15f,
        0.2f, 0.0f, 1.3f, 0f, 30f,
        0f, 0f, 0f, 1f, 0f
    )
    
    private fun crossProcessMatrix() = floatArrayOf(
        1.2f, 0.0f, -0.2f, 0f, 0f,
        0.0f, 1.1f, 0.1f, 0f, 0f,
        -0.1f, 0.0f, 1.15f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
    
    private fun slideFilmMatrix() = floatArrayOf(
        1.1f, 0.05f, 0.0f, 0f, 5f,
        0.0f, 1.05f, 0.0f, 0f, 5f,
        0.0f, 0.0f, 1.1f, 0f, 5f,
        0f, 0f, 0f, 1f, 0f
    )
    
    private fun neutralMatrix() = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
    
    private fun flatMatrix() = floatArrayOf(
        0.95f, 0.0f, 0.0f, 0f, 0f,
        0.0f, 0.95f, 0.0f, 0f, 0f,
        0.0f, 0.0f, 0.95f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
    
    private fun moodyMatrix() = floatArrayOf(
        0.9f, 0.0f, 0.0f, 0f, -30f,
        0.0f, 0.85f, 0.0f, 0f, -35f,
        0.0f, 0.0f, 0.95f, 0f, -20f,
        0f, 0f, 0f, 1f, 0f
    )
    
    // ========== 辅助方法 ==========
    
    private fun applyColorMatrix(bitmap: Bitmap, matrix: FloatArray): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val cm = ColorMatrix(matrix)
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
    
    private fun adjustSaturation(bitmap: Bitmap, saturation: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val cm = ColorMatrix()
        cm.setSaturation(saturation)
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
    
    private fun adjustContrast(bitmap: Bitmap, contrast: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val contrastValue = contrast
        val translate = (-0.5f * contrastValue + 0.5f) * 255
        
        val cm = ColorMatrix(floatArrayOf(
            contrastValue, 0f, 0f, 0f, translate,
            0f, contrastValue, 0f, 0f, translate,
            0f, 0f, contrastValue, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
    
    private fun adjustBrightness(bitmap: Bitmap, brightness: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val brightnessValue = brightness * 255
        
        val cm = ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, brightnessValue,
            0f, 1f, 0f, 0f, brightnessValue,
            0f, 0f, 1f, 0f, brightnessValue,
            0f, 0f, 0f, 1f, 0f
        ))
        
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
    
    private fun applyVignette(bitmap: Bitmap, intensity: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val centerX = bitmap.width / 2f
        val centerY = bitmap.height / 2f
        val radius = maxOf(centerX, centerY) * 1.5f
        
        val gradient = RadialGradient(
            centerX, centerY, radius,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb((intensity * 180).toInt(), 0, 0, 0)
            ),
            floatArrayOf(0.3f, 1f),
            Shader.TileMode.CLAMP
        )
        
        val paint = Paint()
        paint.shader = gradient
        Canvas(result).drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
        
        // 混合
        val original = android.graphics.PorterDuff.Mode.SRC_OVER
        val canvas = Canvas(bitmap)
        canvas.drawBitmap(result, 0f, 0f, null)
        
        return bitmap
    }
    
    private fun applyGrain(bitmap: Bitmap, intensity: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val random = java.util.Random()
        
        for (y in 0 until result.height step 2) {
            for (x in 0 until result.width step 2) {
                val noise = (random.nextFloat() - 0.5f) * intensity * 60
                val pixel = result.getPixel(x, y)
                val r = (Color.red(pixel) + noise).toInt().coerceIn(0, 255)
                val g = (Color.green(pixel) + noise).toInt().coerceIn(0, 255)
                val b = (Color.blue(pixel) + noise).toInt().coerceIn(0, 255)
                result.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        
        return result
    }
}
