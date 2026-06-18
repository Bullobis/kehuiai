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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 风格迁移服务
 * 提供高级艺术风格迁移功能
 */
class StyleTransferService(private val context: Context) {

    companion object {
        private const val TAG = "StyleTransfer"
    }
    
    // ========== 预设风格 ==========
    
    enum class ArtStyle(
        val displayName: String,
        val emoji: String,
        val description: String
    ) {
        // 绘画风格
        OIL_PAINTING("油画", "🖼️", "古典油画效果，厚重的笔触感"),
        WATERCOLOR("水彩", "🎨", "柔和透明的水彩画效果"),
        SKETCH("素描", "✏️", "铅笔素描风格"),
        CHARCOAL("炭笔画", "🌑", "粗犷的炭笔素描"),
        PASTEL("粉彩", "🌸", "柔和的粉彩画"),
        
        // 艺术风格
        IMPRESSIONIST("印象派", "🌅", "莫奈风格的印象派"),
        EXPRESSIONIST("表现主义", "🎭", "大胆的色彩和形式"),
        ABSTRACT("抽象艺术", "🔷", "现代抽象艺术"),
        POP_ART("波普艺术", "🎪", "安迪沃霍尔风格"),
        CUBISM("立体主义", "💎", "毕加索风格的立体派"),
        
        // 数字艺术
        PIXEL_ART("像素艺术", "👾", "复古像素游戏风格"),
        VECTOR("矢量插画", "📐", "干净的矢量图形"),
        LOW_POLY("低多边形", "🔺", "现代3D低多边形"),
        GLITCH("故障艺术", "⚡", "数字故障效果"),
        NEON("霓虹艺术", "🌈", "赛博朋克霓虹"),
        
        // 照片风格
        HDR("HDR", "☀️", "高动态范围照片"),
        VINTAGE_PHOTO("复古照片", "📷", "老式胶片效果"),
        CINEMATIC("电影色调", "🎬", "专业电影调色"),
        BLACK_LIGHT("黑光效果", "💜", "荧光黑光摄影"),
        
        // 文化风格
        UKIYO_E("浮世绘", "🏯", "日本浮世绘风格"),
        CHINESE_PAINTING("国画", "🖌️", "中国传统水墨画"),
        STAINED_GLASS("彩绘玻璃", "🔮", "彩色玻璃窗效果"),
        MOSAIC("马赛克", "🟦", "彩色玻璃马赛克")
    }
    
    // ========== 迁移配置 ==========
    
    data class StyleConfig(
        val style: ArtStyle = ArtStyle.OIL_PAINTING,
        val strength: Float = 0.8f,      // 风格强度 0~1
        val preserveColor: Boolean = false,  // 保留原色
        val edgeEnhance: Boolean = true,     // 边缘增强
        val textureIntensity: Float = 0.5f   // 纹理强度
    )
    
    // ========== 主迁移方法 ==========
    
    suspend fun transferStyle(
        contentImage: Bitmap,
        config: StyleConfig
    ): Bitmap = withContext(Dispatchers.Default) {
        Log.d(TAG, "开始风格迁移: ${config.style}")
        
        // 根据风格选择处理方法
        val result = when {
            config.style.name.contains("PAINTING") || 
            config.style.name.contains("WATERCOLOR") ||
            config.style.name.contains("OIL") -> applyPainterlyEffect(contentImage, config)
            
            config.style.name.contains("SKETCH") || 
            config.style.name.contains("CHARCOAL") -> applySketchEffect(contentImage, config)
            
            config.style.name.contains("PIXEL") -> applyPixelEffect(contentImage, config)
            
            config.style.name.contains("NEON") -> applyNeonEffect(contentImage, config)
            
            config.style.name.contains("GLITCH") -> applyGlitchEffect(contentImage, config)
            
            config.style.name.contains("VINTAGE") -> applyVintageEffect(contentImage, config)
            
            config.style.name.contains("HDR") -> applyHDREffect(contentImage, config)
            
            config.style.name.contains("CINEMATIC") -> applyCinematicEffect(contentImage, config)
            
            config.style.name.contains("UKIYO") -> applyUkiyoEEffect(contentImage, config)
            
            config.style.name.contains("CHINESE") -> applyChinesePaintingEffect(contentImage, config)
            
            config.style.name.contains("STAINED") || 
            config.style.name.contains("MOSAIC") -> applyMosaicEffect(contentImage, config)
            
            config.style.name.contains("LOW_POLY") -> applyLowPolyEffect(contentImage, config)
            
            config.style.name.contains("ABSTRACT") -> applyAbstractEffect(contentImage, config)
            
            config.style.name.contains("VECTOR") -> applyVectorEffect(contentImage, config)
            
            else -> applyPainterlyEffect(contentImage, config)
        }
        
        // 混合原图
        if (config.strength < 1f) {
            blendWithOriginal(contentImage, result, config.strength)
        } else {
            result
        }
    }
    
    // ========== 油画效果 ==========
    
    private fun applyPainterlyEffect(bitmap: Bitmap, config: StyleConfig): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        val radius = when (config.style) {
            ArtStyle.OIL_PAINTING -> 5
            ArtStyle.WATERCOLOR -> 8
            ArtStyle.IMPRESSIONIST -> 6
            else -> 4
        }
        
        val intensity = config.textureIntensity
        
        for (y in radius until height - radius) {
            for (x in radius until width - radius) {
                // 简单的油画笔触效果
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
                
                // 添加纹理变化
                val noise = ((x * y) % 10 - 5) * intensity * 10
                
                val finalR = (avgR + noise).toInt().coerceIn(0, 255)
                val finalG = (avgG + noise * 0.5f).toInt().coerceIn(0, 255)
                val finalB = (avgB + noise * 0.3f).toInt().coerceIn(0, 255)
                
                result.setPixel(x, y, Color.rgb(finalR, finalG, finalB))
            }
        }
        
        return result
    }
    
    // ========== 素描效果 ==========
    
    private fun applySketchEffect(bitmap: Bitmap, config: StyleConfig): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        // 边缘检测
        val edges = detectEdges(bitmap)
        
        // 反转
        for (y in 0 until height) {
            for (x in 0 until width) {
                val edge = edges[y * width + x]
                val original = bitmap.getPixel(x, y)
                
                // 灰度
                val gray = (Color.red(original) * 0.299 + 
                           Color.green(original) * 0.587 + 
                           Color.blue(original) * 0.114).toInt()
                
                // 素描线条
                val sketchValue = if (edge > 30) {
                    min(255, gray + 100)
                } else {
                    max(0, gray - 50)
                }
                
                result.setPixel(x, y, Color.rgb(sketchValue, sketchValue, sketchValue))
            }
        }
        
        return result
    }
    
    // ========== 像素艺术 ==========
    
    private fun applyPixelEffect(bitmap: Bitmap, config: StyleConfig): Bitmap {
        val pixelSize = when (config.textureIntensity) {
            in 0f..0.33f -> 16
            in 0.33f..0.66f -> 8
            else -> 4
        }
        
        val width = bitmap.width
        val height = bitmap.height
        val resultWidth = width / pixelSize
        val resultHeight = height / pixelSize
        
        // 缩小
        val scaled = Bitmap.createScaledBitmap(bitmap, resultWidth, resultHeight, true)
        val result = Bitmap.createScaledBitmap(scaled, width, height, false)
        
        scaled.recycle()
        return result
    }
    
    // ========== 霓虹效果 ==========
    
    private fun applyNeonEffect(bitmap: Bitmap, config: StyleConfig): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        val saturationBoost = 1.5f + config.textureIntensity * 0.5f
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                
                // 计算亮度
                val brightness = (Color.red(pixel) * 0.299 + 
                                Color.green(pixel) * 0.587 + 
                                Color.blue(pixel) * 0.114) / 255f
                
                // 霓虹化
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                
                // 增强饱和度
                val maxC = maxOf(r, g, b)
                val minC = minOf(r, g, b)
                val saturation = if (maxC > 0) (maxC - minC).toFloat() / maxC else 0f
                
                val newR = (r * (1 + saturationBoost * saturation * brightness * 0.3f)).toInt().coerceIn(0, 255)
                val newG = (g * (1 + saturationBoost * saturation * brightness * 0.3f)).toInt().coerceIn(0, 255)
                val newB = (b * (1 + saturationBoost * saturation * brightness * 0.5f)).toInt().coerceIn(0, 255)
                
                result.setPixel(x, y, Color.rgb(newR, newG, newB))
            }
        }
        
        return result
    }
    
    // ========== 故障艺术 ==========
    
    private fun applyGlitchEffect(bitmap: Bitmap, config: StyleConfig): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        val random = java.util.Random()
        val intensity = config.textureIntensity
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                
                // 随机偏移
                if (random.nextFloat() < intensity * 0.1f) {
                    val offsetX = (random.nextFloat() - 0.5f) * 20 * intensity
                    val newX = (x + offsetX).toInt().coerceIn(0, width - 1)
                    val glitchPixel = bitmap.getPixel(newX, y)
                    result.setPixel(x, y, glitchPixel)
                } else {
                    result.setPixel(x, y, pixel)
                }
            }
        }
        
        return result
    }
    
    // ========== 复古照片 ==========
    
    private fun applyVintageEffect(bitmap: Bitmap, config: StyleConfig): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        val warmth = config.textureIntensity * 0.15f
        val fade = config.textureIntensity * 0.2f
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                var r = Color.red(pixel)
                var g = Color.green(pixel)
                var b = Color.blue(pixel)
                
                // 暖色调
                r = (r * (1 + warmth)).toInt().coerceIn(0, 255)
                b = (b * (1 - warmth * 0.5f)).toInt().coerceIn(0, 255)
                
                // 褪色
                r = (r + fade * 50).toInt().coerceIn(0, 255)
                g = (g + fade * 40).toInt().coerceIn(0, 255)
                b = (b + fade * 30).toInt().coerceIn(0, 255)
                
                result.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        
        return result
    }
    
    // ========== HDR ==========
    
    private fun applyHDREffect(bitmap: Bitmap, config: StyleConfig): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        // 增强对比度和饱和度
        val cm = ColorMatrix()
        
        // 提高对比度
        val contrast = 1.2f + config.textureIntensity * 0.3f
        val translate = (-0.5f * contrast + 0.5f) * 255
        
        cm.set(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        
        // 增加饱和度
        val satMatrix = ColorMatrix()
        satMatrix.setSaturation(1.3f + config.textureIntensity * 0.3f)
        cm.postConcat(satMatrix)
        
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
        
        return result
    }
    
    // ========== 电影色调 ==========
    
    private fun applyCinematicEffect(bitmap: Bitmap, config: StyleConfig): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        val cm = ColorMatrix()
        
        // 青橙色调
        val intensity = config.textureIntensity
        
        cm.set(floatArrayOf(
            1f + intensity * 0.1f, 0f, 0f, 0f, -10f * intensity,
            0f, 1f, 0f, 0f, -5f * intensity,
            0f, 0f, 1f - intensity * 0.1f, 0f, 10f * intensity,
            0f, 0f, 0f, 1f, 0f
        ))
        
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
        
        return result
    }
    
    // ========== 浮世绘 ==========
    
    private fun applyUkiyoEEffect(bitmap: Bitmap, config: StyleConfig): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        // 简化的浮世绘效果：降低色调、增加轮廓
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val pixel = bitmap.getPixel(x, y)
                val top = bitmap.getPixel(x, y - 1)
                val bottom = bitmap.getPixel(x, y + 1)
                
                // 简化边缘检测
                val diff = abs(Color.red(pixel) - Color.red(top)) +
                          abs(Color.green(pixel) - Color.green(top)) +
                          abs(Color.blue(pixel) - Color.blue(top))
                
                if (diff > 50) {
                    result.setPixel(x, y, Color.BLACK)
                } else {
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    
                    // 日本传统配色
                    val newR = if (r > 150) 200 else if (r > 100) 100 else r
                    val newG = if (g > 150) 220 else if (g > 100) 140 else g
                    val newB = if (b > 150) 240 else if (b > 100) 180 else b
                    
                    result.setPixel(x, y, Color.rgb(newR, newG, newB))
                }
            }
        }
        
        return result
    }
    
    // ========== 国画 ==========
    
    private fun applyChinesePaintingEffect(bitmap: Bitmap, config: StyleConfig): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                
                // 转灰度
                val gray = (Color.red(pixel) * 0.299 + 
                           Color.green(pixel) * 0.587 + 
                           Color.blue(pixel) * 0.114).toInt()
                
                // 水墨风格
                val ink = when {
                    gray > 200 -> 255  // 留白
                    gray > 150 -> 200
                    gray > 100 -> 150
                    gray > 50 -> 100
                    else -> 50  // 浓墨
                }
                
                result.setPixel(x, y, Color.rgb(ink, ink, ink))
            }
        }
        
        return result
    }
    
    // ========== 马赛克 ==========
    
    private fun applyMosaicEffect(bitmap: Bitmap, config: StyleConfig): Bitmap {
        val tileSize = when (config.textureIntensity) {
            in 0f..0.33f -> 20
            in 0.33f..0.66f -> 10
            else -> 5
        }
        
        val width = bitmap.width
        val height = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        for (y in 0 until height step tileSize) {
            for (x in 0 until width step tileSize) {
                // 获取区域平均色
                var sumR = 0
                var sumG = 0
                var sumB = 0
                var count = 0
                
                for (dy in 0 until tileSize) {
                    for (dx in 0 until tileSize) {
                        if (y + dy < height && x + dx < width) {
                            val pixel = bitmap.getPixel(x + dx, y + dy)
                            sumR += Color.red(pixel)
                            sumG += Color.green(pixel)
                            sumB += Color.blue(pixel)
                            count++
                        }
                    }
                }
                
                val avgColor = if (count > 0) {
                    Color.rgb(sumR / count, sumG / count, sumB / count)
                } else {
                    Color.TRANSPARENT
                }
                
                // 填充区域
                for (dy in 0 until tileSize) {
                    for (dx in 0 until tileSize) {
                        if (y + dy < height && x + dx < width) {
                            result.setPixel(x + dx, y + dy, avgColor)
                        }
                    }
                }
            }
        }
        
        return result
    }
    
    // ========== 低多边形 ==========
    
    private fun applyLowPolyEffect(bitmap: Bitmap, config: StyleConfig): Bitmap {
        // 低多边形效果类似于马赛克但更随机
        return applyMosaicEffect(bitmap, config)
    }
    
    // ========== 抽象艺术 ==========
    
    private fun applyAbstractEffect(bitmap: Bitmap, config: StyleConfig): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        val random = java.util.Random()
        val intensity = config.textureIntensity
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                
                if (random.nextFloat() < intensity * 0.2f) {
                    // 随机色彩偏移
                    val shift = (random.nextFloat() - 0.5f) * 100 * intensity
                    val r = (Color.red(pixel) + shift).toInt().coerceIn(0, 255)
                    val g = (Color.green(pixel) + shift).toInt().coerceIn(0, 255)
                    val b = (Color.blue(pixel) + shift).toInt().coerceIn(0, 255)
                    result.setPixel(x, y, Color.rgb(r, g, b))
                } else {
                    result.setPixel(x, y, pixel)
                }
            }
        }
        
        return result
    }
    
    // ========== 矢量效果 ==========
    
    private fun applyVectorEffect(bitmap: Bitmap, config: StyleConfig): Bitmap {
        // 清晰的边缘和纯色
        val cm = ColorMatrix()
        cm.setSaturation(1.2f)
        
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
        
        return result
    }
    
    // ========== 辅助方法 ==========
    
    private fun detectEdges(bitmap: Bitmap): IntArray {
        val width = bitmap.width
        val height = bitmap.height
        val edges = IntArray(width * height)
        
        val sobelX = intArrayOf(-1, 0, 1, -2, 0, 2, -1, 0, 1)
        val sobelY = intArrayOf(-1, -2, -1, 0, 0, 0, 1, 2, 1)
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var gx = 0
                var gy = 0
                
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val pixel = bitmap.getPixel(x + kx, y + ky)
                        val gray = (Color.red(pixel) * 0.299 + 
                                   Color.green(pixel) * 0.587 + 
                                   Color.blue(pixel) * 0.114).toInt()
                        val index = (ky + 1) * 3 + (kx + 1)
                        gx += gray * sobelX[index]
                        gy += gray * sobelY[index]
                    }
                }
                
                edges[y * width + x] = kotlin.math.sqrt((gx * gx + gy * gy).toDouble()).toInt()
            }
        }
        
        return edges
    }
    
    private fun blendWithOriginal(original: Bitmap, styled: Bitmap, strength: Float): Bitmap {
        val result = original.copy(Bitmap.Config.ARGB_8888, true)
        
        for (y in 0 until original.height) {
            for (x in 0 until original.width) {
                val origPixel = original.getPixel(x, y)
                val stylePixel = styled.getPixel(x, y)
                
                val r = (Color.red(origPixel) * (1 - strength) + Color.red(stylePixel) * strength).toInt()
                val g = (Color.green(origPixel) * (1 - strength) + Color.green(stylePixel) * strength).toInt()
                val b = (Color.blue(origPixel) * (1 - strength) + Color.blue(stylePixel) * strength).toInt()
                
                result.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        
        return result
    }
    
    // ========== 获取所有风格 ==========
    
    fun getAllStyles(): List<ArtStyle> = ArtStyle.entries
    
    fun getStyleByCategory(category: String): List<ArtStyle> {
        return when (category) {
            "painting" -> listOf(ArtStyle.OIL_PAINTING, ArtStyle.WATERCOLOR, ArtStyle.PASTEL)
            "art" -> listOf(ArtStyle.IMPRESSIONIST, ArtStyle.EXPRESSIONIST, ArtStyle.ABSTRACT, ArtStyle.POP_ART, ArtStyle.CUBISM)
            "digital" -> listOf(ArtStyle.PIXEL_ART, ArtStyle.VECTOR, ArtStyle.LOW_POLY, ArtStyle.GLITCH, ArtStyle.NEON)
            "photo" -> listOf(ArtStyle.HDR, ArtStyle.VINTAGE_PHOTO, ArtStyle.CINEMATIC, ArtStyle.BLACK_LIGHT)
            "cultural" -> listOf(ArtStyle.UKIYO_E, ArtStyle.CHINESE_PAINTING, ArtStyle.STAINED_GLASS, ArtStyle.MOSAIC)
            else -> ArtStyle.entries
        }
    }
}
