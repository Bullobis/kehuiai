@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST", "DEPRECATION", "USELESS_ELVIS")
package com.kehuiai.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * 图像处理工具箱
 * 提供各种图像处理功能
 */
class ImageToolbox(private val context: Context) {

    // ========== 基础变换 ==========

    /**
     * 调整图像大小
     */
    suspend fun resizeImage(
        inputPath: String,
        outputPath: String,
        targetWidth: Int,
        targetHeight: Int,
        maintainAspectRatio: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeFile(inputPath) ?: return@withContext false
            
            val scaled: Bitmap = if (maintainAspectRatio) {
                val ratio = min(targetWidth.toFloat() / bitmap.width, targetHeight.toFloat() / bitmap.height)
                val newWidth = (bitmap.width * ratio).toInt()
                val newHeight = (bitmap.height * ratio).toInt()
                Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            } else {
                Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            }
            
            saveBitmap(scaled, outputPath)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 旋转图像
     */
    suspend fun rotateImage(
        inputPath: String,
        outputPath: String,
        degrees: Float
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeFile(inputPath) ?: return@withContext false
            
            val matrix = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            
            saveBitmap(rotated, outputPath)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 翻转图像
     */
    suspend fun flipImage(
        inputPath: String,
        outputPath: String,
        horizontal: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeFile(inputPath) ?: return@withContext false
            
            val matrix = Matrix().apply {
                if (horizontal) {
                    postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
                } else {
                    postScale(1f, -1f, bitmap.width / 2f, bitmap.height / 2f)
                }
            }
            
            val flipped = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            saveBitmap(flipped, outputPath)
        } catch (e: Exception) {
            false
        }
    }

    // ========== 滤镜效果 ==========

    /**
     * 灰度滤镜
     */
    suspend fun applyGrayscale(inputPath: String, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeFile(inputPath) ?: return@withContext false
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            
            val canvas = Canvas(result)
            val paint = Paint()
            val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            
            saveBitmap(result, outputPath)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 复古/怀旧滤镜
     */
    suspend fun applyVintage(inputPath: String, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeFile(inputPath) ?: return@withContext false
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            
            val canvas = Canvas(result)
            val paint = Paint()
            
            // 降低饱和度 + 棕褐色调
            val colorMatrix = ColorMatrix(floatArrayOf(
                0.9f, 0.1f, 0.1f, 0f, 20f,
                0.1f, 0.8f, 0.1f, 0f, 10f,
                0.1f, 0.1f, 0.6f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            ))
            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            
            saveBitmap(result, outputPath)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 高对比度滤镜
     */
    suspend fun applyHighContrast(inputPath: String, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeFile(inputPath) ?: return@withContext false
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            
            val canvas = Canvas(result)
            val paint = Paint()
            
            val contrast = 1.5f
            val brightness = 10f
            val colorMatrix = ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            ))
            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            
            saveBitmap(result, outputPath)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 暖色调滤镜
     */
    suspend fun applyWarm(inputPath: String, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeFile(inputPath) ?: return@withContext false
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            
            val canvas = Canvas(result)
            val paint = Paint()
            
            val colorMatrix = ColorMatrix(floatArrayOf(
                1.2f, 0f, 0f, 0f, 10f,
                0f, 1.1f, 0f, 0f, 5f,
                0f, 0f, 0.9f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            ))
            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            
            saveBitmap(result, outputPath)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 冷色调滤镜
     */
    suspend fun applyCool(inputPath: String, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeFile(inputPath) ?: return@withContext false
            val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            
            val canvas = Canvas(result)
            val paint = Paint()
            
            val colorMatrix = ColorMatrix(floatArrayOf(
                0.9f, 0f, 0f, 0f, -10f,
                0f, 1.0f, 0f, 0f, 0f,
                0f, 0f, 1.2f, 0f, 15f,
                0f, 0f, 0f, 1f, 0f
            ))
            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            
            saveBitmap(result, outputPath)
        } catch (e: Exception) {
            false
        }
    }

    // ========== 高级处理 ==========

    /**
     * 添加水印
     */
    suspend fun addWatermark(
        inputPath: String,
        outputPath: String,
        text: String,
        position: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeFile(inputPath) ?: return@withContext false
            val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            
            val canvas = Canvas(result)
            val paint = Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = bitmap.width / 20f
                isAntiAlias = true
                setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
            }
            
            val textBounds = Rect()
            paint.getTextBounds(text, 0, text.length, textBounds)
            
            val x = when (position) {
                WatermarkPosition.TOP_LEFT -> 20f
                WatermarkPosition.TOP_RIGHT -> bitmap.width - textBounds.width() - 20f
                WatermarkPosition.BOTTOM_LEFT -> 20f
                WatermarkPosition.BOTTOM_RIGHT -> bitmap.width - textBounds.width() - 20f
                WatermarkPosition.CENTER -> (bitmap.width - textBounds.width()) / 2f
            }
            
            val y = when (position) {
                WatermarkPosition.TOP_LEFT, WatermarkPosition.TOP_RIGHT -> textBounds.height() + 20f
                WatermarkPosition.BOTTOM_LEFT, WatermarkPosition.BOTTOM_RIGHT -> bitmap.height - 20f
                WatermarkPosition.CENTER -> (bitmap.height + textBounds.height()) / 2f
            }
            
            canvas.drawText(text, x, y, paint)
            
            saveBitmap(result, outputPath)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 裁剪图像
     */
    suspend fun cropImage(
        inputPath: String,
        outputPath: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeFile(inputPath) ?: return@withContext false
            
            val cropped = Bitmap.createBitmap(
                bitmap,
                x.coerceIn(0, bitmap.width - 1),
                y.coerceIn(0, bitmap.height - 1),
                width.coerceIn(1, bitmap.width - x),
                height.coerceIn(1, bitmap.height - y)
            )
            
            saveBitmap(cropped, outputPath)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 圆角处理
     */
    suspend fun roundCorners(
        inputPath: String,
        outputPath: String,
        cornerRadius: Float = 30f
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeFile(inputPath) ?: return@withContext false
            
            val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            
            val paint = Paint().apply {
                isAntiAlias = true
            }
            
            val rect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
            
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            
            saveBitmap(output, outputPath)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 模糊效果
     */
    suspend fun blurImage(
        inputPath: String,
        outputPath: String,
        radius: Int = 15
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val bitmap = BitmapFactory.decodeFile(inputPath) ?: return@withContext false
            
            // 简单的模糊实现
            val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            
            val blurred = IntArray(pixels.size)
            for (i in pixels.indices) {
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                
                for (dx in -radius..radius) {
                    for (dy in -radius..radius) {
                        val nx = (i % bitmap.width) + dx
                        val ny = (i / bitmap.width) + dy
                        
                        if (nx in 0 until bitmap.width && ny in 0 until bitmap.height) {
                            val nIdx = ny * bitmap.width + nx
                            val pixel = pixels[nIdx]
                            r += (pixel shr 16) and 0xFF
                            g += (pixel shr 8) and 0xFF
                            b += pixel and 0xFF
                            count++
                        }
                    }
                }
                
                if (count > 0) {
                    blurred[i] = (0xFF shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
                }
            }
            
            output.setPixels(blurred, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            saveBitmap(output, outputPath)
        } catch (e: Exception) {
            false
        }
    }

    // ========== 工具方法 ==========

    private fun saveBitmap(bitmap: Bitmap, path: String): Boolean {
        return try {
            val file = File(path)
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}

enum class WatermarkPosition {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    CENTER
}
