package com.kehuiai.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 图像合成服务
 * 提供图层混合、拼图、抠图等功能
 */
class ImageCompositionService(private val context: Context) {

    companion object {
        private const val TAG = "ImageComposition"
    }
    
    // ========== 混合模式 ==========
    
    enum class BlendMode(val displayName: String) {
        NORMAL("正常"),
        MULTIPLY("正片叠底"),
        SCREEN("屏幕"),
        OVERLAY("叠加"),
        SOFT_LIGHT("柔光"),
        HARD_LIGHT("硬光"),
        DIFFERENCE("差值"),
        ADD("添加"),
        SUBTRACT("减去"),
        DARKEN("变暗"),
        LIGHTEN("变亮"),
        COLOR_DODGE("颜色减淡"),
        COLOR_BURN("颜色加深")
    }
    
    // ========== 图层 ==========
    
    data class Layer(
        val bitmap: Bitmap,
        var x: Float = 0f,
        var y: Float = 0f,
        var alpha: Float = 1f,
        var blendMode: BlendMode = BlendMode.NORMAL,
        var scale: Float = 1f,
        var rotation: Float = 0f,
        var isVisible: Boolean = true,
        var name: String = "Layer"
    )
    
    // ========== 主合成方法 ==========
    
    suspend fun compose(
        layers: List<Layer>,
        outputWidth: Int,
        outputHeight: Int,
        backgroundColor: Int = Color.WHITE
    ): Bitmap = withContext(Dispatchers.Default) {
        val result = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        
        // 绘制背景
        canvas.drawColor(backgroundColor)
        
        // 绘制每个图层
        for (layer in layers) {
            if (!layer.isVisible) continue
            
            val paint = Paint().apply {
                alpha = (layer.alpha * 255).toInt()
                this@apply.isAntiAlias = true
            }
            
            // 应用混合模式
            paint.xfermode = getPorterDuffXfermode(layer.blendMode)
            
            // 保存状态
            canvas.save()
            
            // 应用变换
            if (layer.rotation != 0f) {
                val centerX = layer.x + layer.bitmap.width * layer.scale / 2
                val centerY = layer.y + layer.bitmap.height * layer.scale / 2
                canvas.rotate(layer.rotation, centerX, centerY)
            }
            
            // 缩放
            val scaledWidth = (layer.bitmap.width * layer.scale).toInt()
            val scaledHeight = (layer.bitmap.height * layer.scale).toInt()
            
            // 绘制
            val destRect = RectF(
                layer.x,
                layer.y,
                layer.x + scaledWidth,
                layer.y + scaledHeight
            )
            canvas.drawBitmap(layer.bitmap, null, destRect, paint)
            
            // 恢复状态
            canvas.restore()
        }
        
        result
    }
    
    // ========== 拼图 ==========
    
    enum class CollageLayout(val displayName: String, val rows: Int, val cols: Int) {
        GRID_2X2("2x2网格", 2, 2),
        GRID_3X3("3x3网格", 3, 3),
        GRID_2X3("2x3网格", 2, 3),
        GRID_3X2("3x2网格", 3, 2),
        GRID_4X4("4x4网格", 4, 4),
        HORIZONTAL_2("水平两张", 1, 2),
        VERTICAL_2("垂直两张", 2, 1),
        HORIZONTAL_3("水平三张", 1, 3),
        VERTICAL_3("垂直三张", 3, 1),
        ONE_LARGE_TWO_SMALL("一大两小", 2, 2),
        MOSAIC("马赛克", 3, 3)
    }
    
    data class CollageConfig(
        val layout: CollageLayout,
        val spacing: Int = 8,        // 间距(px)
        val backgroundColor: Int = Color.WHITE,
        val cornerRadius: Float = 0f,  // 圆角(px)
        val images: List<Bitmap> = emptyList()
    )
    
    suspend fun createCollage(config: CollageConfig): Bitmap = withContext(Dispatchers.Default) {
        val images = config.images
        if (images.isEmpty()) {
            return@withContext Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        }
        
        val rows = config.layout.rows
        val cols = config.layout.cols
        val cellWidth = 400
        val cellHeight = 400
        val spacing = config.spacing
        
        val width = cols * cellWidth + (cols + 1) * spacing
        val height = rows * cellHeight + (rows + 1) * spacing
        
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(config.backgroundColor)
        
        // 根据布局放置图片
        var imageIndex = 0
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                if (imageIndex >= images.size || imageIndex >= rows * cols) break
                
                val x = spacing + col * (cellWidth + spacing)
                val y = spacing + row * (cellHeight + spacing)
                
                val srcBitmap = images[imageIndex]
                val destRect = RectF(
                    x.toFloat(), y.toFloat(),
                    (x + cellWidth).toFloat(), (y + cellHeight).toFloat()
                )
                
                // 缩放图片以填满格子
                val scaledBitmap = scaleBitmapToFill(srcBitmap, cellWidth, cellHeight)
                
                val paint = Paint().apply {
                    isAntiAlias = true
                }
                
                // 绘制
                if (config.cornerRadius > 0) {
                    // 圆角裁剪
                    val path = android.graphics.Path()
                    path.addRoundRect(
                        RectF(
                            x.toFloat(), y.toFloat(),
                            (x + cellWidth).toFloat(), (y + cellHeight).toFloat()
                        ),
                        config.cornerRadius,
                        config.cornerRadius,
                        android.graphics.Path.Direction.CW
                    )
                    canvas.save()
                    canvas.clipPath(path)
                    canvas.drawBitmap(scaledBitmap, x.toFloat(), y.toFloat(), paint)
                    canvas.restore()
                } else {
                    canvas.drawBitmap(scaledBitmap, x.toFloat(), y.toFloat(), paint)
                }
                
                imageIndex++
            }
        }
        
        result
    }
    
    // ========== 抠图 ==========
    
    suspend fun removeBackground(
        bitmap: Bitmap,
        bgColor: Int = Color.WHITE
    ): Bitmap = withContext(Dispatchers.Default) {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        // 获取主色调作为背景
        val dominantColor = getDominantColor(bitmap)
        
        val threshold = 60
        
        for (y in 0 until result.height) {
            for (x in 0 until result.width) {
                val pixel = result.getPixel(x, y)
                val dr = Color.red(pixel) - Color.red(dominantColor)
                val dg = Color.green(pixel) - Color.green(dominantColor)
                val db = Color.blue(pixel) - Color.blue(dominantColor)
                
                val diff = kotlin.math.sqrt((dr * dr + dg * dg + db * db).toDouble())
                
                if (diff < threshold) {
                    // 接近背景色，设为透明
                    result.setPixel(x, y, Color.TRANSPARENT)
                }
            }
        }
        
        result
    }
    
    suspend fun cutout(
        bitmap: Bitmap,
        mask: Bitmap
    ): Bitmap = withContext(Dispatchers.Default) {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        val maskWidth = mask.width
        val maskHeight = mask.height
        
        for (y in 0 until result.height) {
            for (x in 0 until result.width) {
                val maskX = (x * maskWidth / result.width).coerceIn(0, maskWidth - 1)
                val maskY = (y * maskHeight / result.height).coerceIn(0, maskHeight - 1)
                val maskPixel = mask.getPixel(maskX, maskY)
                
                // 根据蒙版灰度决定透明度
                val gray = (Color.red(maskPixel) + Color.green(maskPixel) + Color.blue(maskPixel)) / 3
                
                if (gray < 128) {
                    result.setPixel(x, y, Color.TRANSPARENT)
                }
            }
        }
        
        result
    }
    
    // ========== 工具方法 ==========
    
    private fun getPorterDuffXfermode(blendMode: BlendMode): PorterDuffXfermode? {
        return when (blendMode) {
            BlendMode.MULTIPLY -> PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
            BlendMode.SCREEN -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            BlendMode.OVERLAY -> PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
            BlendMode.DARKEN -> PorterDuffXfermode(PorterDuff.Mode.DARKEN)
            BlendMode.LIGHTEN -> PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
            else -> null
        }
    }
    
    private fun scaleBitmapToFill(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val widthRatio = targetWidth.toFloat() / bitmap.width
        val heightRatio = targetHeight.toFloat() / bitmap.height
        
        val scale = maxOf(widthRatio, heightRatio)
        
        val scaledWidth = (bitmap.width * scale).toInt()
        val scaledHeight = (bitmap.height * scale).toInt()
        
        val scaled = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        
        // 裁剪到目标尺寸
        val x = (scaledWidth - targetWidth) / 2
        val y = (scaledHeight - targetHeight) / 2
        
        return Bitmap.createBitmap(scaled, x, y, targetWidth, targetHeight)
    }
    
    private fun getDominantColor(bitmap: Bitmap): Int {
        val colorCount = mutableMapOf<Int, Int>()
        
        // 采样
        val step = 10
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                val pixel = bitmap.getPixel(x, y)
                colorCount[pixel] = colorCount.getOrDefault(pixel, 0) + 1
            }
        }
        
        // 返回最多的颜色
        return colorCount.maxByOrNull { it.value }?.key ?: Color.WHITE
    }
    
    // ========== 混合两张图片 ==========
    
    suspend fun blendImages(
        base: Bitmap,
        overlay: Bitmap,
        blendMode: BlendMode = BlendMode.NORMAL,
        opacity: Float = 1f
    ): Bitmap = withContext(Dispatchers.Default) {
        val result = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        
        val paint = Paint().apply {
            this.alpha = (opacity * 255).toInt()
            xfermode = getPorterDuffXfermode(blendMode)
            isAntiAlias = true
        }
        
        // 缩放 overlay 到 base 的大小
        val scaledOverlay = Bitmap.createScaledBitmap(overlay, base.width, base.height, true)
        canvas.drawBitmap(scaledOverlay, 0f, 0f, paint)
        
        result
    }
    
    // ========== 添加水印 ==========
    
    suspend fun addWatermark(
        bitmap: Bitmap,
        watermark: Bitmap,
        position: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT,
        padding: Int = 20,
        alpha: Float = 0.5f
    ): Bitmap = withContext(Dispatchers.Default) {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        
        val paint = Paint().apply {
            this.alpha = (alpha * 255).toInt()
            isAntiAlias = true
        }
        
        val wmWidth = watermark.width
        val wmHeight = watermark.height
        
        // 缩小水印
        val maxWidth = bitmap.width * 0.3f
        val maxHeight = bitmap.height * 0.2f
        val scale = minOf(maxWidth / wmWidth, maxHeight / wmHeight, 1f)
        
        val scaledWmWidth = (wmWidth * scale).toInt()
        val scaledWmHeight = (wmHeight * scale).toInt()
        
        val x: Float
        val y: Float
        
        when (position) {
            WatermarkPosition.TOP_LEFT -> {
                x = padding.toFloat()
                y = padding.toFloat()
            }
            WatermarkPosition.TOP_RIGHT -> {
                x = (bitmap.width - scaledWmWidth - padding).toFloat()
                y = padding.toFloat()
            }
            WatermarkPosition.BOTTOM_LEFT -> {
                x = padding.toFloat()
                y = (bitmap.height - scaledWmHeight - padding).toFloat()
            }
            WatermarkPosition.BOTTOM_RIGHT -> {
                x = (bitmap.width - scaledWmWidth - padding).toFloat()
                y = (bitmap.height - scaledWmHeight - padding).toFloat()
            }
            WatermarkPosition.CENTER -> {
                x = (bitmap.width - scaledWmWidth) / 2f
                y = (bitmap.height - scaledWmHeight) / 2f
            }
            WatermarkPosition.TILE -> {
                // 平铺水印
                val scaledWm = Bitmap.createScaledBitmap(watermark, scaledWmWidth, scaledWmHeight, true)
                for (ty in 0 until bitmap.height step scaledWmHeight + 50) {
                    for (tx in 0 until bitmap.width step scaledWmWidth + 50) {
                        canvas.drawBitmap(scaledWm, tx.toFloat(), ty.toFloat(), paint)
                    }
                }
                return@withContext result
            }
        }
        
        val scaledWatermark = Bitmap.createScaledBitmap(watermark, scaledWmWidth, scaledWmHeight, true)
        canvas.drawBitmap(scaledWatermark, x, y, paint)
        
        result
    }
    
    enum class WatermarkPosition {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER, TILE
    }
}
