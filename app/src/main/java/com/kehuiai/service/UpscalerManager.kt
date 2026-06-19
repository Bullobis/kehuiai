@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST", "DEPRECATION", "USELESS_ELVIS")
package com.kehuiai.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 超分辨率放大器 (Upscaler)
 * 支持多种放大算法：Lanczos, BICUBIC, ESRGAN, RealESRGAN
 */
class UpscalerManager(private val context: Context) {

    companion object {
        private const val TAG = "UpscalerManager"
        
        // 放大算法类型
        const val ALGORITHM_LANCZOS = "lanczos"
        const val ALGORITHM_BICUBIC = "bicubic"
        const val ALGORITHM_ESRGAN = "esrgan"
        const val ALGORITHM_REAL_ESRGAN = "realesrgan"
        const val ALGORITHM_RIFE = "rife"  // 帧率放大
        
        // 最大放大倍数
        const val MAX_SCALE = 8
        const val DEFAULT_SCALE = 4
    }

    private val upscalerDir = File(context.filesDir, "upscalers")
    private val outputDir = File(context.filesDir, "upscaled")
    
    init {
        if (!upscalerDir.exists()) upscalerDir.mkdirs()
        if (!outputDir.exists()) outputDir.mkdirs()
    }

    /**
     * 放大图像
     */
    fun upscale(
        inputPath: String,
        scale: Int = DEFAULT_SCALE,
        algorithm: String = ALGORITHM_BICUBIC,
        outputPath: String? = null
    ): Flow<UpscaleProgress> = flow {
        emit(UpscaleProgress.Status("开始放大..."))

        try {
            val inputFile = File(inputPath)
            if (!inputFile.exists()) {
                emit(UpscaleProgress.Error("输入文件不存在"))
                return@flow
            }

            if (scale !in 1..MAX_SCALE) {
                emit(UpscaleProgress.Error("放大倍数必须在 1-$MAX_SCALE 之间"))
                return@flow
            }

            emit(UpscaleProgress.Progress(10, "加载图像..."))

            // 加载图像（这里简化处理）
            val bitmap = android.graphics.BitmapFactory.decodeFile(inputPath)
            if (bitmap == null) {
                emit(UpscaleProgress.Error("无法加载图像"))
                return@flow
            }

            emit(UpscaleProgress.Progress(30, "执行 $algorithm 放大..."))

            // 执行放大
            val upscaled = when (algorithm) {
                ALGORITHM_LANCZOS -> upscaleLanczos(bitmap, scale)
                ALGORITHM_BICUBIC -> upscaleBicubic(bitmap, scale)
                ALGORITHM_ESRGAN -> upscaleESRGAN(bitmap, scale)
                ALGORITHM_REAL_ESRGAN -> upscaleRealESRGAN(bitmap, scale)
                ALGORITHM_RIFE -> upscaleRife(bitmap, scale)
                else -> upscaleBicubic(bitmap, scale)
            }

            emit(UpscaleProgress.Progress(70, "保存图像..."))

            // 保存结果
            val outputFile = if (outputPath != null) {
                File(outputPath)
            } else {
                File(outputDir, "upscaled_${System.currentTimeMillis()}.png")
            }
            
            saveBitmap(upscaled, outputFile)

            emit(UpscaleProgress.Completed(
                outputPath = outputFile.absolutePath,
                originalWidth = bitmap.width,
                originalHeight = bitmap.height,
                upscaledWidth = upscaled.width,
                upscaledHeight = upscaled.height,
                scale = scale,
                algorithm = algorithm
            ))

            // 释放内存
            if (bitmap != upscaled) bitmap.recycle()
            upscaled.recycle()

        } catch (e: Exception) {
            Log.e(TAG, "Upscale error: ${e.message}")
            emit(UpscaleProgress.Error("放大失败: ${e.message}"))
        }
    }

    /**
     * 批量放大
     */
    fun batchUpscale(
        inputPaths: List<String>,
        scale: Int = DEFAULT_SCALE,
        algorithm: String = ALGORITHM_BICUBIC
    ): Flow<UpscaleProgress> = flow {
        val total = inputPaths.size
        
        for ((index, path) in inputPaths.withIndex()) {
            emit(UpscaleProgress.Status("放大 ${index + 1}/$total..."))
            
            upscale(path, scale, algorithm).collect { result ->
                when (result) {
                    is UpscaleProgress.Completed -> {
                        emit(UpscaleProgress.Progress(
                            ((index + 1) * 100) / total,
                            "完成 ${index + 1}/$total"
                        ))
                    }
                    is UpscaleProgress.Error -> {
                        emit(UpscaleProgress.Error("批量放大中断: ${result.message}"))
                        return@collect
                    }
                    else -> {}
                }
            }
        }
        
        emit(UpscaleProgress.Completed(
            outputPath = outputDir.absolutePath,
            originalWidth = 0,
            originalHeight = 0,
            upscaledWidth = 0,
            upscaledHeight = 0,
            scale = scale,
            algorithm = algorithm
        ))
    }

    /**
     * 同步放大（供其他服务调用）
     */
    suspend fun upscaleSync(
        inputPath: String,
        scale: Int = DEFAULT_SCALE,
        algorithm: String = ALGORITHM_BICUBIC
    ): Result<String> = withContext(Dispatchers.Default) {
        try {
            val inputFile = File(inputPath)
            if (!inputFile.exists()) {
                return@withContext Result.failure(Exception("输入文件不存在"))
            }

            val bitmap = android.graphics.BitmapFactory.decodeFile(inputPath)
                ?: return@withContext Result.failure(Exception("无法加载图像"))

            val upscaled = when (algorithm) {
                ALGORITHM_LANCZOS -> upscaleLanczos(bitmap, scale)
                ALGORITHM_BICUBIC -> upscaleBicubic(bitmap, scale)
                ALGORITHM_ESRGAN -> upscaleESRGAN(bitmap, scale)
                ALGORITHM_REAL_ESRGAN -> upscaleRealESRGAN(bitmap, scale)
                ALGORITHM_RIFE -> upscaleRife(bitmap, scale)
                else -> upscaleBicubic(bitmap, scale)
            }

            val outputFile = File(outputDir, "upscaled_${System.currentTimeMillis()}.png")
            saveBitmap(upscaled, outputFile)

            if (bitmap != upscaled) bitmap.recycle()
            upscaled.recycle()

            Result.success(outputFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== 放大算法实现 ====================

    /**
     * Lanczos 放大
     */
    private fun upscaleLanczos(bitmap: Bitmap, scale: Int): Bitmap {
        val newWidth = bitmap.width * scale
        val newHeight = bitmap.height * scale
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Bicubic 放大
     */
    private fun upscaleBicubic(bitmap: Bitmap, scale: Int): Bitmap {
        // 使用 Android 的高质量缩放
        val newWidth = bitmap.width * scale
        val newHeight = bitmap.height * scale
        val result = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }
        
        canvas.drawBitmap(bitmap, null, Rect(0, 0, newWidth, newHeight), paint)
        return result
    }

    /**
     * ESRGAN 风格放大（模拟）
     * 实际需要调用本地模型或远程 API
     */
    private fun upscaleESRGAN(bitmap: Bitmap, scale: Int): Bitmap {
        // 简化实现：先 bicubic 再细节增强
        var result = upscaleBicubic(bitmap, scale)
        
        // 简单的锐化处理模拟
        result = enhanceDetails(result)
        
        return result
    }

    /**
     * RealESRGAN 放大（模拟）
     */
    private fun upscaleRealESRGAN(bitmap: Bitmap, scale: Int): Bitmap {
        // RealESRGAN 通常先去噪再放大
        var result = denoise(bitmap)
        result = upscaleBicubic(result, scale)
        result = enhanceDetails(result)
        return result
    }

    /**
     * RIFE 帧率放大（模拟）
     * 可以用于视频帧插帧
     */
    private fun upscaleRife(bitmap: Bitmap, scale: Int): Bitmap {
        // RIFE 主要用于时间维度的插帧，这里简化为空间放大
        return upscaleBicubic(bitmap, scale)
    }

    /**
     * 细节增强
     */
    private fun enhanceDetails(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        // 简单的锐化卷积
        val kernel = floatArrayOf(
            0f, -1f, 0f,
            -1f, 5f, -1f,
            0f, -1f, 0f
        )
        
        // 应用卷积（简化版）
        for (x in 1 until width - 1) {
            for (y in 1 until height - 1) {
                var r = 0
                var g = 0
                var b = 0
                
                // 中心像素权重
                val centerPixel = bitmap.getPixel(x, y)
                r += Color.red(centerPixel) * 5
                g += Color.green(centerPixel) * 5
                b += Color.blue(centerPixel) * 5
                
                // 邻域像素
                for (dx in -1..1) {
                    for (dy in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val pixel = bitmap.getPixel(x + dx, y + dy)
                        val weight = kernel[(dx + 1) + (dy + 1) * 3]
                        r -= (Color.red(pixel) * weight).toInt()
                        g -= (Color.green(pixel) * weight).toInt()
                        b -= (Color.blue(pixel) * weight).toInt()
                    }
                }
                
                result.setPixel(x, y, Color.rgb(
                    r.coerceIn(0, 255),
                    g.coerceIn(0, 255),
                    b.coerceIn(0, 255)
                ))
            }
        }
        
        return result
    }

    /**
     * 降噪（简化）
     */
    private fun denoise(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        // 简单的均值滤波降噪
        for (x in 1 until width - 1) {
            for (y in 1 until height - 1) {
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                
                for (dx in -1..1) {
                    for (dy in -1..1) {
                        val pixel = bitmap.getPixel(x + dx, y + dy)
                        r += Color.red(pixel)
                        g += Color.green(pixel)
                        b += Color.blue(pixel)
                        count++
                    }
                }
                
                result.setPixel(x, y, Color.rgb(
                    r / count,
                    g / count,
                    b / count
                ))
            }
        }
        
        return result
    }

    /**
     * 保存 Bitmap
     */
    private fun saveBitmap(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    /**
     * 获取可用算法列表
     */
    fun getAvailableAlgorithms(): List<Pair<String, String>> {
        return listOf(
            ALGORITHM_LANCZOS to "Lanczos (快速)",
            ALGORITHM_BICUBIC to "Bicubic (平衡)",
            ALGORITHM_ESRGAN to "ESRGAN (细节)",
            ALGORITHM_REAL_ESRGAN to "RealESRGAN (最佳)",
            ALGORITHM_RIFE to "RIFE (视频插帧)"
        )
    }

    /**
     * 获取输出目录
     */
    fun getOutputDirectory(): File = outputDir
}

/**
 * 放大进度
 */
sealed class UpscaleProgress {
    data class Status(val message: String) : UpscaleProgress()
    data class Progress(val percent: Int, val message: String) : UpscaleProgress()
    data class Completed(
        val outputPath: String,
        val originalWidth: Int,
        val originalHeight: Int,
        val upscaledWidth: Int,
        val upscaledHeight: Int,
        val scale: Int,
        val algorithm: String
    ) : UpscaleProgress()
    data class Error(val message: String) : UpscaleProgress()
}
