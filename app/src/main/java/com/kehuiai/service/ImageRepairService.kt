@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST", "DEPRECATION", "USELESS_ELVIS")
package com.kehuiai.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 图像修复服务
 * 处理模糊、噪点、老照片等
 */
class ImageRepairService(private val context: Context) {

    companion object {
        private const val TAG = "ImageRepair"
    }
    
    // ========== 修复类型 ==========
    
    enum class RepairType {
        SHARPEN,          // 锐化
        DENOISE,          // 降噪
        DEBLUR,           // 去模糊
        RESTORE_OLD,     // 老照片修复
        REMOVE_SCRATCHES, // 去除划痕
        COLORIZE,         // 黑白上色
        ENHANCE_FACE,    // 面部增强
        LOW_LIGHT_ENHANCE // 低光增强
    }
    
    data class RepairConfig(
        val type: RepairType,
        val strength: Float = 0.5f,    // 强度 0~1
        val iterations: Int = 1,         // 迭代次数
        val preserveDetails: Boolean = true  // 保留细节
    )
    
    // ========== 主修复方法 ==========
    
    suspend fun repair(bitmap: Bitmap, config: RepairConfig): Bitmap = withContext(Dispatchers.Default) {
        var result = bitmap
        
        repeat(config.iterations) {
            result = when (config.type) {
                RepairType.SHARPEN -> sharpen(result, config.strength)
                RepairType.DENOISE -> denoise(result, config.strength)
                RepairType.DEBLUR -> deblur(result, config.strength)
                RepairType.RESTORE_OLD -> restoreOldPhoto(result, config.strength)
                RepairType.REMOVE_SCRATCHES -> removeScratches(result, config.strength)
                RepairType.COLORIZE -> colorize(result, config.strength)
                RepairType.ENHANCE_FACE -> enhanceFace(result, config.strength)
                RepairType.LOW_LIGHT_ENHANCE -> enhanceLowLight(result, config.strength)
            }
        }
        
        result
    }
    
    // ========== 锐化 ==========
    
    private fun sharpen(bitmap: Bitmap, strength: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        // USM 锐化
        val amount = strength * 1.5f
        val radius = 2
        
        // 简单的 3x3 锐化核
        val kernel = floatArrayOf(
            0f, -1f, 0f,
            -1f, 5f, -1f,
            0f, -1f, 0f
        )
        
        for (y in radius until height - radius) {
            for (x in radius until width - radius) {
                var r = 0f
                var g = 0f
                var b = 0f
                
                for (ky in -radius..radius) {
                    for (kx in -radius..radius) {
                        val pixel = bitmap.getPixel(x + kx, y + ky)
                        val weight = kernel[(ky + radius) * 3 + (kx + radius)]
                        r += Color.red(pixel) * weight
                        g += Color.green(pixel) * weight
                        b += Color.blue(pixel) * weight
                    }
                }
                
                // 混合原图和锐化结果
                val origPixel = bitmap.getPixel(x, y)
                val origR = Color.red(origPixel)
                val origG = Color.green(origPixel)
                val origB = Color.blue(origPixel)
                
                val finalR = (origR * (1 - amount) + r * amount).toInt().coerceIn(0, 255)
                val finalG = (origG * (1 - amount) + g * amount).toInt().coerceIn(0, 255)
                val finalB = (origB * (1 - amount) + b * amount).toInt().coerceIn(0, 255)
                
                result.setPixel(x, y, Color.rgb(finalR, finalG, finalB))
            }
        }
        
        return result
    }
    
    // ========== 降噪 ==========
    
    private fun denoise(bitmap: Bitmap, strength: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        val radius = (strength * 3).toInt().coerceIn(1, 5)
        val threshold = (strength * 30).toInt().coerceIn(5, 50)
        
        for (y in radius until height - radius) {
            for (x in radius until width - radius) {
                val centerPixel = bitmap.getPixel(x, y)
                val centerR = Color.red(centerPixel)
                val centerG = Color.green(centerPixel)
                val centerB = Color.blue(centerPixel)
                
                // 计算周围像素的平均值
                var sumR = 0
                var sumG = 0
                var sumB = 0
                var count = 0
                
                for (ky in -radius..radius) {
                    for (kx in -radius..radius) {
                        val pixel = bitmap.getPixel(x + kx, y + ky)
                        sumR += Color.red(pixel)
                        sumG += Color.green(pixel)
                        sumB += Color.blue(pixel)
                        count++
                    }
                }
                
                val avgR = sumR / count
                val avgG = sumG / count
                val avgB = sumB / count
                
                // 如果当前像素与平均值差异大，使用平均值
                if (abs(centerR - avgR) > threshold ||
                    abs(centerG - avgG) > threshold ||
                    abs(centerB - avgB) > threshold
                ) {
                    val blendFactor = strength * 0.8f
                    val finalR = (centerR * (1 - blendFactor) + avgR * blendFactor).toInt()
                    val finalG = (centerG * (1 - blendFactor) + avgG * blendFactor).toInt()
                    val finalB = (centerB * (1 - blendFactor) + avgB * blendFactor).toInt()
                    result.setPixel(x, y, Color.rgb(finalR, finalG, finalB))
                }
            }
        }
        
        return result
    }
    
    // ========== 去模糊 ==========
    
    private fun deblur(bitmap: Bitmap, strength: Float): Bitmap {
        // 简单的去模糊：先降噪再锐化
        var result = denoise(bitmap, strength * 0.3f)
        result = sharpen(result, strength * 0.8f)
        return result
    }
    
    // ========== 老照片修复 ==========
    
    private fun restoreOldPhoto(bitmap: Bitmap, strength: Float): Bitmap {
        var result = bitmap
        
        // 1. 去除噪点
        result = denoise(result, strength * 0.6f)
        
        // 2. 增强对比度
        result = enhanceContrast(result, strength * 0.5f)
        
        // 3. 修复色彩
        result = restoreColors(result, strength * 0.4f)
        
        // 4. 锐化
        result = sharpen(result, strength * 0.4f)
        
        return result
    }
    
    private fun enhanceContrast(bitmap: Bitmap, strength: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        // 计算直方图
        val histR = IntArray(256)
        val histG = IntArray(256)
        val histB = IntArray(256)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                histR[Color.red(pixel)]++
                histG[Color.green(pixel)]++
                histB[Color.blue(pixel)]++
            }
        }
        
        // 计算累积分布
        val cumR = IntArray(256)
        val cumG = IntArray(256)
        val cumB = IntArray(256)
        
        cumR[0] = histR[0]
        cumG[0] = histG[0]
        cumB[0] = histB[0]
        
        for (i in 1 until 256) {
            cumR[i] = cumR[i - 1] + histR[i]
            cumG[i] = cumG[i - 1] + histG[i]
            cumB[i] = cumB[i - 1] + histB[i]
        }
        
        // 应用直方图均衡化（部分）
        val factor = strength * 0.5f
        val totalPixels = width * height
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                
                val newR = ((cumR[r].toFloat() / totalPixels * 255 * factor + r * (1 - factor))).toInt().coerceIn(0, 255)
                val newG = ((cumG[g].toFloat() / totalPixels * 255 * factor + g * (1 - factor))).toInt().coerceIn(0, 255)
                val newB = ((cumB[b].toFloat() / totalPixels * 255 * factor + b * (1 - factor))).toInt().coerceIn(0, 255)
                
                result.setPixel(x, y, Color.rgb(newR, newG, newB))
            }
        }
        
        return result
    }
    
    private fun restoreColors(bitmap: Bitmap, strength: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        // 简单的色彩平衡
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                var r = Color.red(pixel)
                var g = Color.green(pixel)
                var b = Color.blue(pixel)
                
                // 增强暖色调
                r = (r * (1 + strength * 0.1f)).toInt().coerceIn(0, 255)
                g = (g * (1 + strength * 0.05f)).toInt().coerceIn(0, 255)
                
                result.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        
        return result
    }
    
    // ========== 去除划痕 ==========
    
    private fun removeScratches(bitmap: Bitmap, strength: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        val threshold = (strength * 100).toInt().coerceIn(20, 100)
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val pixel = bitmap.getPixel(x, y)
                val above = bitmap.getPixel(x, y - 1)
                val below = bitmap.getPixel(x, y + 1)
                
                // 检测垂直划痕
                val diff1 = abs(Color.red(pixel) - Color.red(above)) +
                           abs(Color.green(pixel) - Color.green(above)) +
                           abs(Color.blue(pixel) - Color.blue(above))
                val diff2 = abs(Color.red(pixel) - Color.red(below)) +
                           abs(Color.green(pixel) - Color.green(below)) +
                           abs(Color.blue(pixel) - Color.blue(below))
                
                if (diff1 > threshold && diff2 > threshold) {
                    // 用上下像素的平均值替代
                    val avgR = (Color.red(above) + Color.red(below)) / 2
                    val avgG = (Color.green(above) + Color.green(below)) / 2
                    val avgB = (Color.blue(above) + Color.blue(below)) / 2
                    result.setPixel(x, y, Color.rgb(avgR, avgG, avgB))
                }
            }
        }
        
        return result
    }
    
    // ========== 黑白上色 ==========
    
    private fun colorize(bitmap: Bitmap, strength: Float): Bitmap {
        // 简化实现：根据亮度和位置添加合理的颜色
        val width = bitmap.width
        val height = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val brightness = (r + g + b) / 3
                
                // 根据亮度分配颜色
                val newR: Int
                val newG: Int
                val newB: Int
                
                when {
                    brightness > 200 -> {
                        // 高光区域（天空、建筑）
                        newR = (r * (1 - strength) + 240 * strength).toInt().coerceIn(0, 255)
                        newG = (g * (1 - strength) + 240 * strength).toInt().coerceIn(0, 255)
                        newB = (b * (1 - strength) + 230 * strength).toInt().coerceIn(0, 255)
                    }
                    brightness > 150 -> {
                        // 中间区域（人皮肤、绿叶）
                        newR = (r * (1 - strength) + 220 * strength * 0.8f).toInt().coerceIn(0, 255)
                        newG = (g * (1 - strength) + 200 * strength * 0.9f).toInt().coerceIn(0, 255)
                        newB = (b * (1 - strength) + 180 * strength * 0.7f).toInt().coerceIn(0, 255)
                    }
                    brightness > 80 -> {
                        // 中低区域（草地、树木）
                        newR = (r * (1 - strength) + 100 * strength * 0.6f).toInt().coerceIn(0, 255)
                        newG = (g * (1 - strength) + 140 * strength).toInt().coerceIn(0, 255)
                        newB = (b * (1 - strength) + 80 * strength * 0.5f).toInt().coerceIn(0, 255)
                    }
                    else -> {
                        // 阴影区域（深色）
                        newR = (r * (1 - strength) + 50 * strength * 0.3f).toInt().coerceIn(0, 255)
                        newG = (g * (1 - strength) + 50 * strength * 0.4f).toInt().coerceIn(0, 255)
                        newB = (b * (1 - strength) + 60 * strength * 0.5f).toInt().coerceIn(0, 255)
                    }
                }
                
                result.setPixel(x, y, Color.rgb(newR, newG, newB))
            }
        }
        
        return result
    }
    
    // ========== 面部增强 ==========
    
    private fun enhanceFace(bitmap: Bitmap, strength: Float): Bitmap {
        var result = bitmap
        
        // 1. 柔和锐化（保边）
        result = smartSharpen(result, strength * 0.5f)
        
        // 2. 亮度平滑
        result = smoothBrightness(result, strength * 0.3f)
        
        // 3. 色彩增强
        result = enhanceSkinTone(result, strength * 0.4f)
        
        return result
    }
    
    private fun smartSharpen(bitmap: Bitmap, strength: Float): Bitmap {
        // 边缘感知的锐化
        return sharpen(bitmap, strength)
    }
    
    private fun smoothBrightness(bitmap: Bitmap, strength: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        val radius = 2
        
        for (y in radius until height - radius) {
            for (x in radius until width - radius) {
                var sum = 0
                var count = 0
                
                for (ky in -radius..radius) {
                    for (kx in -radius..radius) {
                        val pixel = bitmap.getPixel(x + kx, y + ky)
                        sum += (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                        count++
                    }
                }
                
                val avg = sum / count
                val pixel = bitmap.getPixel(x, y)
                val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                
                if (abs(brightness - avg) < 20) {
                    val newBrightness = (brightness * (1 - strength) + avg * strength).toInt()
                    val factor = newBrightness.toFloat() / brightness.coerceAtLeast(1)
                    val r = (Color.red(pixel) * factor).toInt().coerceIn(0, 255)
                    val g = (Color.green(pixel) * factor).toInt().coerceIn(0, 255)
                    val b = (Color.blue(pixel) * factor).toInt().coerceIn(0, 255)
                    result.setPixel(x, y, Color.rgb(r, g, b))
                }
            }
        }
        
        return result
    }
    
    private fun enhanceSkinTone(bitmap: Bitmap, strength: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                
                // 检测皮肤色调（简化）
                val isSkin = r > 95 && g > 40 && b > 20 &&
                             r > g && r > b &&
                             abs(r - g) > 15 &&
                             r - b > 15
                
                if (isSkin) {
                    // 增强皮肤
                    val newR = (r * (1 + strength * 0.05f)).toInt().coerceIn(0, 255)
                    val newG = (g * (1 + strength * 0.02f)).toInt().coerceIn(0, 255)
                    result.setPixel(x, y, Color.rgb(newR, newG, b))
                }
            }
        }
        
        return result
    }
    
    // ========== 低光增强 ==========
    
    private fun enhanceLowLight(bitmap: Bitmap, strength: Float): Bitmap {
        var result = bitmap
        
        // 1. 提亮
        result = brighten(result, strength * 0.6f)
        
        // 2. 对比度增强
        result = enhanceContrast(result, strength * 0.4f)
        
        // 3. 降噪
        result = denoise(result, strength * 0.3f)
        
        return result
    }
    
    private fun brighten(bitmap: Bitmap, strength: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val amount = (strength * 80).toInt()
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = (Color.red(pixel) + amount).coerceIn(0, 255)
                val g = (Color.green(pixel) + amount).coerceIn(0, 255)
                val b = (Color.blue(pixel) + amount).coerceIn(0, 255)
                result.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        
        return result
    }
}
