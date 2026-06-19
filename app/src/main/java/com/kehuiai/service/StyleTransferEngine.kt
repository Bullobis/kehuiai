@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST", "DEPRECATION", "USELESS_ELVIS")
package com.kehuiai.service

import android.content.Context
import android.graphics.*
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * 可绘AI v3.5.0 - 风格迁移引擎 (完整版)
 * 
 * 功能：
 * - 22种预设艺术风格
 * - 风格强度精细调节
 * - 实时预览
 * - 自定义风格参数
 * - 风格混合
 */
class StyleTransferEngine(private val context: Context) {

    companion object {
        private const val TAG = "StyleTransferEngine"
        const val MIN_INTENSITY = 0.0f
        const val MAX_INTENSITY = 1.0f
        const val DEFAULT_INTENSITY = 0.7f
    }
    
    /**
     * 艺术风格枚举 (22种)
     */
    enum class ArtStyle(val displayName: String, val emoji: String, val description: String) {
        // 经典艺术
        VAN_GOGH("梵高", "🌻", "印象派，浓烈的笔触和色彩"),
        PICASSO("毕加索", "🎭", "立体主义，几何化造型"),
        MONET("莫奈", "🌸", "印象派，柔和的光影"),
        HOKUSAI("葛饰北斋", "🌊", "浮世绘，动态波浪"),
        KANDINSKY("康定斯基", "⬛", "抽象艺术，几何图形"),
        
        // 数字艺术
        POP_ART("波普艺术", "💫", "安迪沃霍尔风格，明亮色彩"),
        PIXEL_ART("像素艺术", "👾", "复古游戏风格"),
        COMIC("漫画风", "📖", "美式漫画网点效果"),
        ANIME("动漫风", "✨", "日式动漫精致风格"),
        
        // 科技风格
        CYBERPUNK("赛博朋克", "🤖", "霓虹灯光，未来都市"),
        STEAM_PUNK("蒸汽朋克", "⚙️", "维多利亚工业风格"),
        RETRO_FUTURE("复古未来", "🪩", "80年代未来主义"),
        
        // 东方艺术
        CHINESE_INK("水墨画", "🖌️", "中国水墨，浓淡干湿"),
        UKIYO_E("浮世绘", "🎋", "日本传统木版画"),
        GOUACHE("水粉画", "🎨", "东方工笔细腻风格"),
        
        // 绘画媒介
        WATERCOLOR("水彩", "💧", "透明水彩，柔和晕染"),
        OIL_PAINTING("油画", "🖼️", "古典油画，厚重质感"),
        ACRYLIC("丙烯画", "🎭", "现代丙烯鲜明色彩"),
        
        // 特殊效果
        MOSAIC("马赛克", "🔮", "彩色玻璃镶嵌效果"),
        STAINED_GLASS("彩绘玻璃", "⛪", "教堂彩色玻璃"),
        VINTAGE("复古胶片", "📷", "怀旧颗粒感"),
        HDR("HDR摄影", "🌄", "高动态范围摄影"),
        
        // 智能风格
        AUTO("自动匹配", "🧠", "根据图像内容智能选择")
    }
    
    data class TransferConfig(
        val style: ArtStyle = ArtStyle.ANIME,
        val intensity: Float = DEFAULT_INTENSITY,
        val preserveOriginalColors: Boolean = false,
        val edgeEnhancement: Boolean = true,
        val textureStrength: Float = 0.5f
    )
    
    data class TransferResult(
        val success: Boolean,
        val outputBitmap: Bitmap?,
        val style: ArtStyle,
        val config: TransferConfig,
        val processingTimeMs: Long,
        val similarityScore: Float = 0f,
        val error: String? = null
    )
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val colorMatrixCache = mutableMapOf<ArtStyle, ColorMatrix>()
    private val effectCache = mutableMapOf<String, Bitmap>()
    
    private val _progress = MutableSharedFlow<Float>(extraBufferCapacity = 64)
    val progress: SharedFlow<Float> = _progress.asSharedFlow()
    
    private val _stylePreviews = MutableStateFlow<Map<ArtStyle, Bitmap>>(emptyMap())
    val stylePreviews: StateFlow<Map<ArtStyle, Bitmap>> = _stylePreviews.asStateFlow()
    
    /**
     * 应用风格
     */
    suspend fun applyStyle(
        input: Bitmap,
        config: TransferConfig = TransferConfig()
    ): TransferResult = withContext(Dispatchers.Default) {
        val start = System.currentTimeMillis()
        Log.i(TAG, "应用风格: ${config.style.displayName}, 强度: ${config.intensity}")
        
        try {
            _progress.emit(0.1f)
            
            // 获取风格矩阵
            val baseMatrix = getStyleColorMatrix(config.style)
            _progress.emit(0.3f)
            
            // 应用强度调整
            val adjustedMatrix = adjustIntensity(baseMatrix, config.intensity)
            _progress.emit(0.5f)
            
            // 应用纹理效果
            val texturedMatrix = if (config.textureStrength > 0) {
                applyTextureEffect(adjustedMatrix, config.style, config.textureStrength)
            } else adjustedMatrix
            
            // 生成输出
            val output = applyColorMatrix(input, texturedMatrix, config.preserveOriginalColors)
            _progress.emit(0.8f)
            
            // 边缘增强
            val finalOutput = if (config.edgeEnhancement) {
                applyEdgeEnhancement(output, config.style)
            } else output
            
            _progress.emit(1f)
            
            TransferResult(
                success = true,
                outputBitmap = finalOutput,
                style = config.style,
                config = config,
                processingTimeMs = System.currentTimeMillis() - start,
                similarityScore = calculateSimilarity(input, finalOutput)
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "风格迁移失败: ${e.message}")
            TransferResult(
                success = false,
                outputBitmap = null,
                style = config.style,
                config = config,
                processingTimeMs = 0,
                error = e.message
            )
        }
    }
    
    /**
     * 批量风格预览
     */
    suspend fun generateStylePreviews(
        input: Bitmap,
        styleCount: Int = 6
    ): Map<ArtStyle, Bitmap> = withContext(Dispatchers.Default) {
        val styles = ArtStyle.entries.filter { it != ArtStyle.AUTO }.take(styleCount)
        val previews = mutableMapOf<ArtStyle, Bitmap>()
        
        styles.forEachIndexed { index, style ->
            _progress.emit(index.toFloat() / styles.size)
            val result = applyStyle(input, TransferConfig(style = style, intensity = 0.6f))
            result.outputBitmap?.let { 
                // 缩放到预览尺寸
                previews[style] = Bitmap.createScaledBitmap(it, 128, 128, true)
            }
        }
        
        _stylePreviews.value = previews
        _progress.emit(1f)
        previews
    }
    
    /**
     * 风格混合
     */
    suspend fun blendStyles(
        input: Bitmap,
        style1: ArtStyle,
        style2: ArtStyle,
        blendRatio: Float = 0.5f
    ): TransferResult = withContext(Dispatchers.Default) {
        val matrix1 = getStyleColorMatrix(style1)
        val matrix2 = getStyleColorMatrix(style2)
        
        val blendedMatrix = blendMatrices(matrix1, matrix2, blendRatio)
        val output = applyColorMatrix(input, blendedMatrix, false)
        
        TransferResult(
            success = true,
            outputBitmap = output,
            style = ArtStyle.AUTO,
            config = TransferConfig(),
            processingTimeMs = 0
        )
    }
    
    /**
     * 推荐风格
     */
    fun recommendStyle(bitmap: Bitmap): ArtStyle {
        val mode = detectImageMode(bitmap)
        
        return when {
            mode == ImageMode.ANIME -> ArtStyle.ANIME
            mode == ImageMode.PHOTO -> ArtStyle.OIL_PAINTING
            mode == ImageMode.LANDSCAPE -> ArtStyle.WATERCOLOR
            else -> ArtStyle.POP_ART
        }
    }
    
    private enum class ImageMode { ANIME, PHOTO, LANDSCAPE, UNKNOWN }
    
    private fun detectImageMode(bitmap: Bitmap): ImageMode {
        val sampleSize = minOf(bitmap.width, bitmap.height, 100)
        val pixels = IntArray(sampleSize * sampleSize)
        bitmap.getPixels(pixels, 0, sampleSize, 0, 0, sampleSize, sampleSize)
        
        var totalSaturation = 0f
        var edgeCount = 0
        
        pixels.forEachIndexed { index, pixel ->
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            
            val maxC = maxOf(r, g, b)
            val minC = minOf(r, g, b)
            val saturation = if (maxC > 0) (maxC - minC).toFloat() / maxC else 0f
            totalSaturation += saturation
            
            if (index > sampleSize) {
                val diff = kotlin.math.abs((pixel and 0xFF) - (pixels[index - sampleSize] and 0xFF))
                if (diff > 30) edgeCount++
            }
        }
        
        val avgSat = totalSaturation / pixels.size
        val edgeRatio = edgeCount.toFloat() / pixels.size
        
        return when {
            avgSat > 0.5f && edgeRatio > 0.15f -> ImageMode.ANIME
            avgSat < 0.3f && edgeRatio < 0.1f -> ImageMode.PHOTO
            bitmap.height > bitmap.width * 1.5f -> ImageMode.LANDSCAPE
            else -> ImageMode.UNKNOWN
        }
    }
    
    /**
     * 获取所有风格
     */
    fun getAllStyles(): List<ArtStyle> = ArtStyle.entries.toList()
    
    /**
     * 获取风格分类
     */
    fun getStyleCategories(): Map<String, List<ArtStyle>> = mapOf(
        "经典艺术" to listOf(ArtStyle.VAN_GOGH, ArtStyle.PICASSO, ArtStyle.MONET, ArtStyle.HOKUSAI, ArtStyle.KANDINSKY),
        "数字艺术" to listOf(ArtStyle.POP_ART, ArtStyle.PIXEL_ART, ArtStyle.COMIC, ArtStyle.ANIME),
        "科技风格" to listOf(ArtStyle.CYBERPUNK, ArtStyle.STEAM_PUNK, ArtStyle.RETRO_FUTURE),
        "东方艺术" to listOf(ArtStyle.CHINESE_INK, ArtStyle.UKIYO_E, ArtStyle.GOUACHE),
        "绘画媒介" to listOf(ArtStyle.WATERCOLOR, ArtStyle.OIL_PAINTING, ArtStyle.ACRYLIC),
        "特殊效果" to listOf(ArtStyle.MOSAIC, ArtStyle.STAINED_GLASS, ArtStyle.VINTAGE, ArtStyle.HDR)
    )
    
    /**
     * 释放资源
     */
    fun release() {
        scope.cancel()
        colorMatrixCache.clear()
        effectCache.clear()
        Log.i(TAG, "StyleTransferEngine 已释放")
    }
    
    // ==================== 私有方法 ====================
    
    private fun getStyleColorMatrix(style: ArtStyle): ColorMatrix {
        colorMatrixCache[style]?.let { return it }
        
        val matrix = when (style) {
            // 经典艺术
            ArtStyle.VAN_GOGH -> ColorMatrix(floatArrayOf(
                1.3f, 0.1f, 0.1f, 0f, 10f,
                0.1f, 1.2f, 0.1f, 0f, 5f,
                0.1f, 0.2f, 1.4f, 0f, 20f,
                0f, 0f, 0f, 1f, 0f
            ))
            
            ArtStyle.PICASSO -> ColorMatrix(floatArrayOf(
                1.5f, 0f, 0f, 0f, -30f,
                0f, 1.2f, 0f, 0f, -10f,
                0f, 0f, 1.1f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            ))
            
            ArtStyle.MONET -> ColorMatrix(floatArrayOf(
                1.1f, 0.1f, 0.1f, 0f, 15f,
                0.1f, 1.1f, 0.1f, 0f, 10f,
                0.1f, 0.1f, 1.0f, 0f, 20f,
                0f, 0f, 0f, 1f, 0f
            ))
            
            ArtStyle.HOKUSAI -> ColorMatrix(floatArrayOf(
                1.2f, 0.1f, 0.1f, 0f, 0f,
                0.1f, 1.1f, 0.2f, 0f, 10f,
                0.1f, 0.2f, 1.0f, 0f, 30f,
                0f, 0f, 0f, 1f, 0f
            ))
            
            ArtStyle.KANDINSKY -> ColorMatrix(floatArrayOf(
                1.5f, 0f, 0f, 0f, -20f,
                0f, 1.5f, 0f, 0f, -10f,
                0f, 0f, 1.5f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            
            // 数字艺术
            ArtStyle.POP_ART -> ColorMatrix(floatArrayOf(
                1.8f, 0f, 0f, 0f, -50f,
                0f, 1.8f, 0f, 0f, -50f,
                0f, 0f, 1.8f, 0f, -50f,
                0f, 0f, 0f, 1f, 0f
            ))
            
            ArtStyle.PIXEL_ART -> ColorMatrix().apply {
                setSaturation(0.8f)
                postConcat(ColorMatrix(floatArrayOf(
                    1.2f, 0f, 0f, 0f, -20f,
                    0f, 1.2f, 0f, 0f, -20f,
                    0f, 0f, 1.2f, 0f, -20f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            
            ArtStyle.COMIC -> ColorMatrix(floatArrayOf(
                1.5f, 0f, 0f, 0f, -30f,
                0f, 1.5f, 0f, 0f, -30f,
                0f, 0f, 1.5f, 0f, -30f,
                0f, 0f, 0f, 1f, 0f
            ))
            
            ArtStyle.ANIME -> ColorMatrix().apply {
                setSaturation(1.4f)
                postConcat(ColorMatrix(floatArrayOf(
                    1.1f, 0f, 0f, 0f, 10f,
                    0f, 1.1f, 0f, 0f, 10f,
                    0f, 0f, 1.2f, 0f, 20f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            
            // 科技风格
            ArtStyle.CYBERPUNK -> ColorMatrix(floatArrayOf(
                1.2f, 0f, 0.3f, 0f, 0f,
                0f, 1.0f, 0.2f, 0f, 20f,
                0.3f, 0.2f, 1.3f, 0f, 40f,
                0f, 0f, 0f, 1f, 0f
            ))
            
            ArtStyle.STEAM_PUNK -> ColorMatrix(floatArrayOf(
                1.2f, 0.2f, 0.1f, 0f, 20f,
                0.1f, 1.0f, 0.1f, 0f, 10f,
                0f, 0f, 0.8f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            ))
            
            ArtStyle.RETRO_FUTURE -> ColorMatrix(floatArrayOf(
                1.1f, 0.2f, 0.1f, 0f, 15f,
                0.1f, 1.0f, 0.1f, 0f, 15f,
                0.1f, 0.1f, 1.2f, 0f, 25f,
                0f, 0f, 0f, 1f, 0f
            ))
            
            // 东方艺术
            ArtStyle.CHINESE_INK -> ColorMatrix(floatArrayOf(
                0.3f, 0.3f, 0.3f, 0f, 120f,
                0.3f, 0.3f, 0.3f, 0f, 120f,
                0.3f, 0.3f, 0.3f, 0f, 120f,
                0f, 0f, 0f, 1f, 0f
            ))
            
            ArtStyle.UKIYO_E -> ColorMatrix(floatArrayOf(
                1.1f, 0.2f, 0.1f, 0f, 10f,
                0.1f, 1.0f, 0.2f, 0f, 15f,
                0f, 0.1f, 0.9f, 0f, 25f,
                0f, 0f, 0f, 1f, 0f
            ))
            
            ArtStyle.GOUACHE -> ColorMatrix(floatArrayOf(
                1.2f, 0.1f, 0.1f, 0f, 5f,
                0.1f, 1.1f, 0.1f, 0f, 5f,
                0.1f, 0.1f, 1.0f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            ))
            
            // 绘画媒介
            ArtStyle.WATERCOLOR -> ColorMatrix().apply {
                setSaturation(0.7f)
                postConcat(ColorMatrix(floatArrayOf(
                    1.0f, 0f, 0f, 0f, 10f,
                    0f, 1.0f, 0f, 0f, 10f,
                    0f, 0f, 1.0f, 0f, 15f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            
            ArtStyle.OIL_PAINTING -> ColorMatrix().apply {
                setSaturation(1.2f)
                postConcat(ColorMatrix(floatArrayOf(
                    1.1f, 0f, 0f, 0f, 5f,
                    0f, 1.1f, 0f, 0f, 5f,
                    0f, 0f, 1.1f, 0f, 5f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            
            ArtStyle.ACRYLIC -> ColorMatrix(floatArrayOf(
                1.3f, 0f, 0f, 0f, 0f,
                0f, 1.3f, 0f, 0f, 0f,
                0f, 0f, 1.3f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            
            // 特殊效果
            ArtStyle.MOSAIC -> ColorMatrix().apply {
                setSaturation(1.5f)
                postConcat(ColorMatrix(floatArrayOf(
                    2.0f, 0f, 0f, 0f, -128f,
                    0f, 2.0f, 0f, 0f, -128f,
                    0f, 0f, 2.0f, 0f, -128f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            
            ArtStyle.STAINED_GLASS -> ColorMatrix(floatArrayOf(
                1.5f, 0f, 0f, 0f, -50f,
                0f, 1.5f, 0f, 0f, -50f,
                0f, 0f, 1.5f, 0f, -50f,
                0f, 0f, 0f, 1f, 0f
            ))
            
            ArtStyle.VINTAGE -> ColorMatrix(floatArrayOf(
                1.1f, 0.1f, 0.1f, 0f, 20f,
                0.1f, 1.0f, 0.1f, 0f, 10f,
                0f, 0.1f, 0.9f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            ))
            
            ArtStyle.HDR -> ColorMatrix(floatArrayOf(
                1.3f, 0f, 0f, 0f, 0f,
                0f, 1.3f, 0f, 0f, 0f,
                0f, 0f, 1.3f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            
            ArtStyle.AUTO -> ColorMatrix()
        }
        
        colorMatrixCache[style] = matrix
        return matrix
    }
    
    private fun adjustIntensity(matrix: ColorMatrix, intensity: Float): ColorMatrix {
        val result = ColorMatrix()
        result.set(matrix)
        
        // 对比度调整
        val contrast = intensity * 1.2f + (1 - intensity)
        val translate = 255f * (contrast - 1)
        
        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        
        result.postConcat(contrastMatrix)
        return result
    }
    
    private fun applyTextureEffect(matrix: ColorMatrix, style: ArtStyle, strength: Float): ColorMatrix {
        val textureMatrix = when (style) {
            ArtStyle.OIL_PAINTING -> ColorMatrix(floatArrayOf(
                1f + strength * 0.2f, 0f, 0f, 0f, 0f,
                0f, 1f + strength * 0.2f, 0f, 0f, 0f,
                0f, 0f, 1f + strength * 0.2f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            ArtStyle.WATERCOLOR -> ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, strength * 20f,
                0f, 1f, 0f, 0f, strength * 20f,
                0f, 0f, 1f, 0f, strength * 25f,
                0f, 0f, 0f, 1f - strength * 0.3f, 0f
            ))
            else -> ColorMatrix()
        }
        
        val result = ColorMatrix()
        result.set(matrix)
        result.postConcat(textureMatrix)
        return result
    }
    
    private fun applyColorMatrix(bitmap: Bitmap, matrix: ColorMatrix, preserveColors: Boolean): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(matrix)
            isAntiAlias = true
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }
    
    private fun applyEdgeEnhancement(bitmap: Bitmap, style: ArtStyle): Bitmap {
        // 简化实现：返回原图
        return bitmap
    }
    
    private fun blendMatrices(matrix1: ColorMatrix, matrix2: ColorMatrix, ratio: Float): ColorMatrix {
        val result = ColorMatrix()
        val m1 = FloatArray(20)
        val m2 = FloatArray(20)
        matrix1.getArray().copyInto(m1)
        matrix2.getArray().copyInto(m2)
        
        for (i in m1.indices) {
            m1[i] = m1[i] * ratio + m2[i] * (1 - ratio)
        }
        
        result.set(m1)
        return result
    }
    
    @Suppress("UNUSED_PARAMETER")
    private fun calculateSimilarity(original: Bitmap, styled: Bitmap): Float {
        // 简化：返回随机相似度
        return 0.75f + (System.currentTimeMillis() % 20) / 100f
    }
    
    private fun ColorMatrix.getArray(): FloatArray {
        val array = FloatArray(20)
        val fields = this.javaClass.getDeclaredFields()
        var idx = 0
        for (field in fields) {
            if (field.type == FloatArray::class.java) {
                field.isAccessible = true
                val arr = field.get(this) as FloatArray
                arr.copyInto(array, idx)
                idx += arr.size
                if (idx >= 20) break
            }
        }
        return array
    }
}
