@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST", "DEPRECATION", "USELESS_ELVIS")
package com.kehuiai.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 图像修复管理器 (Inpainting)
 * 支持：局部重绘、物体移除、瑕疵修复
 */
class InpaintingManager(private val context: Context) {

    companion object {
        private const val TAG = "InpaintingManager"
        
        // 修复模式
        const val MODE_INPAINT = "inpaint"           // 局部重绘
        const val MODE_OUTPAINT = "outpaint"         // 图像外扩
        const val MODE_REMOVE_OBJECT = "remove"      // 物体移除
        const val MODE_FIX_FLAWS = "fix_flaws"       // 瑕疵修复
        const val MODE_SKETCH = "sketch"            // 涂鸦重绘
        const val MODE_FACE_RESTORE = "face"         // 面部修复
    }

    private val outputDir = File(context.filesDir, "inpainted")
    
    init {
        if (!outputDir.exists()) outputDir.mkdirs()
    }

    /**
     * 图像修复（局部重绘）
     */
    fun inpaint(
        imagePath: String,
        maskPath: String? = null,
        prompt: String = "",
        negativePrompt: String = "",
        maskBlur: Int = 8,
        inpaintFull: Boolean = false,
        outputPath: String? = null
    ): Flow<InpaintProgress> = flow {
        emit(InpaintProgress.Status("开始修复..."))

        try {
            val imageFile = File(imagePath)
            if (!imageFile.exists()) {
                emit(InpaintProgress.Error("图像文件不存在"))
                return@flow
            }

            emit(InpaintProgress.Progress(10, "加载图像..."))

            // 加载图像
            val bitmap = android.graphics.BitmapFactory.decodeFile(imagePath)
                ?: throw Exception("无法加载图像")

            emit(InpaintProgress.Progress(30, "处理蒙版..."))

            // 加载或生成蒙版
            val mask = if (maskPath != null) {
                android.graphics.BitmapFactory.decodeFile(maskPath)
            } else {
                generateDefaultMask(bitmap.width, bitmap.height)
            }

            if (mask == null) {
                bitmap.recycle()
                emit(InpaintProgress.Error("无法创建蒙版"))
                return@flow
            }

            emit(InpaintProgress.Progress(50, "执行修复..."))

            // 执行修复（模拟）
            val result = performInpainting(bitmap, mask, prompt, inpaintFull)

            emit(InpaintProgress.Progress(80, "保存结果..."))

            // 保存结果
            val outputFile = if (outputPath != null) {
                File(outputPath)
            } else {
                File(outputDir, "inpainted_${System.currentTimeMillis()}.png")
            }
            
            saveBitmap(result, outputFile)

            emit(InpaintProgress.Completed(
                outputPath = outputFile.absolutePath,
                width = result.width,
                height = result.height,
                mode = if (inpaintFull) "full" else "masked"
            ))

            // 释放内存
            bitmap.recycle()
            mask.recycle()
            result.recycle()

        } catch (e: Exception) {
            Log.e(TAG, "Inpaint error: ${e.message}")
            emit(InpaintProgress.Error("修复失败: ${e.message}"))
        }
    }

    /**
     * 图像外扩 (Outpainting)
     */
    fun outpaint(
        imagePath: String,
        direction: String = "all", // all, left, right, top, bottom
        pixels: Int = 512,
        prompt: String = ""
    ): Flow<InpaintProgress> = flow {
        emit(InpaintProgress.Status("开始外扩..."))

        try {
            val imageFile = File(imagePath)
            if (!imageFile.exists()) {
                emit(InpaintProgress.Error("图像文件不存在"))
                return@flow
            }

            emit(InpaintProgress.Progress(10, "加载图像..."))

            val bitmap = android.graphics.BitmapFactory.decodeFile(imagePath)
                ?: throw Exception("无法加载图像")

            emit(InpaintProgress.Progress(40, "生成外扩区域..."))

            // 计算新尺寸
            val (newWidth, newHeight) = when (direction) {
                "all" -> Pair(bitmap.width + pixels * 2, bitmap.height + pixels * 2)
                "left" -> Pair(bitmap.width + pixels, bitmap.height)
                "right" -> Pair(bitmap.width + pixels, bitmap.height)
                "top" -> Pair(bitmap.width, bitmap.height + pixels)
                "bottom" -> Pair(bitmap.width, bitmap.height + pixels)
                else -> Pair(bitmap.width + pixels * 2, bitmap.height + pixels * 2)
            }

            emit(InpaintProgress.Progress(60, "执行外扩..."))

            // 执行外扩（模拟）
            val result = performOutpainting(bitmap, direction, pixels, prompt)

            emit(InpaintProgress.Progress(80, "保存结果..."))

            val outputFile = File(outputDir, "outpainted_${System.currentTimeMillis()}.png")
            saveBitmap(result, outputFile)

            emit(InpaintProgress.Completed(
                outputPath = outputFile.absolutePath,
                width = result.width,
                height = result.height,
                mode = "outpaint"
            ))

            bitmap.recycle()
            result.recycle()

        } catch (e: Exception) {
            emit(InpaintProgress.Error("外扩失败: ${e.message}"))
        }
    }

    /**
     * 物体移除
     */
    fun removeObject(
        imagePath: String,
        maskPath: String
    ): Flow<InpaintProgress> = flow {
        emit(InpaintProgress.Status("移除物体..."))

        try {
            emit(InpaintProgress.Progress(10, "加载图像..."))

            val imageBitmap = android.graphics.BitmapFactory.decodeFile(imagePath)
                ?: throw Exception("无法加载图像")

            emit(InpaintProgress.Progress(30, "加载蒙版..."))

            val maskBitmap = android.graphics.BitmapFactory.decodeFile(maskPath)
                ?: throw Exception("无法加载蒙版")

            emit(InpaintProgress.Progress(50, "移除物体..."))

            // 使用 inpaint 功能
            val result = performInpainting(imageBitmap, maskBitmap, "", false)

            emit(InpaintProgress.Progress(80, "保存结果..."))

            val outputFile = File(outputDir, "removed_${System.currentTimeMillis()}.png")
            saveBitmap(result, outputFile)

            emit(InpaintProgress.Completed(
                outputPath = outputFile.absolutePath,
                width = result.width,
                height = result.height,
                mode = "remove"
            ))

            imageBitmap.recycle()
            maskBitmap.recycle()
            result.recycle()

        } catch (e: Exception) {
            emit(InpaintProgress.Error("移除失败: ${e.message}"))
        }
    }

    /**
     * 瑕疵修复
     */
    fun fixFlaws(
        imagePath: String,
        strength: Float = 0.5f
    ): Flow<InpaintProgress> = flow {
        emit(InpaintProgress.Status("修复瑕疵..."))

        try {
            emit(InpaintProgress.Progress(20, "加载图像..."))

            val bitmap = android.graphics.BitmapFactory.decodeFile(imagePath)
                ?: throw Exception("无法加载图像")

            emit(InpaintProgress.Progress(50, "检测瑕疵..."))

            // 自动检测并修复小瑕疵
            val result = fixFlawsInImage(bitmap, strength)

            emit(InpaintProgress.Progress(80, "保存结果..."))

            val outputFile = File(outputDir, "fixed_${System.currentTimeMillis()}.png")
            saveBitmap(result, outputFile)

            emit(InpaintProgress.Completed(
                outputPath = outputFile.absolutePath,
                width = result.width,
                height = result.height,
                mode = "fix_flaws"
            ))

            bitmap.recycle()
            result.recycle()

        } catch (e: Exception) {
            emit(InpaintProgress.Error("修复失败: ${e.message}"))
        }
    }

    // ==================== 内部方法 ====================

    private fun generateDefaultMask(width: Int, height: Int): Bitmap {
        // 创建圆形蒙版（中心区域）
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mask)
        
        canvas.drawColor(Color.TRANSPARENT)
        
        val paint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        
        // 中心圆形
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) / 4f
        
        canvas.drawCircle(centerX, centerY, radius, paint)
        
        return mask
    }

    private fun performInpainting(
        image: Bitmap,
        mask: Bitmap,
        prompt: String,
        inpaintFull: Boolean
    ): Bitmap {
        // 简化实现：基于周围像素填充
        val result = image.copy(Bitmap.Config.ARGB_8888, true)
        val width = result.width
        val height = result.height
        
        // 确保蒙版尺寸匹配
        val maskToUse = if (mask.width != width || mask.height != height) {
            Bitmap.createScaledBitmap(mask, width, height, true)
        } else {
            mask
        }

        // 简单的修复算法：向内填充
        for (x in 0 until width) {
            for (y in 0 until height) {
                val maskPixel = maskToUse.getPixel(x, y)
                val isMasked = Color.red(maskPixel) > 128

                if (isMasked || inpaintFull) {
                    // 从周围像素采样
                    var r = 0
                    var g = 0
                    var b = 0
                    var count = 0

                    // 采样周围像素
                    for (dx in -5..5) {
                        for (dy in -5..5) {
                            if (dx == 0 && dy == 0) continue
                            val nx = x + dx
                            val ny = y + dy
                            if (nx in 0 until width && ny in 0 until height) {
                                val neighborMask = maskToUse.getPixel(nx, ny)
                                if (Color.red(neighborMask) < 128) {
                                    val pixel = result.getPixel(nx, ny)
                                    r += Color.red(pixel)
                                    g += Color.green(pixel)
                                    b += Color.blue(pixel)
                                    count++
                                }
                            }
                        }
                    }

                    if (count > 0) {
                        result.setPixel(x, y, Color.rgb(
                            r / count,
                            g / count,
                            b / count
                        ))
                    }
                }
            }
        }

        return result
    }

    private fun performOutpainting(
        image: Bitmap,
        direction: String,
        pixels: Int,
        prompt: String
    ): Bitmap {
        val width = image.width
        val height = image.height

        val (newWidth, newHeight) = when (direction) {
            "all" -> Pair(width + pixels * 2, height + pixels * 2)
            "left" -> Pair(width + pixels, height)
            "right" -> Pair(width + pixels, height)
            "top" -> Pair(width, height + pixels)
            "bottom" -> Pair(width, height + pixels)
            else -> Pair(width + pixels * 2, height + pixels * 2)
        }

        val result = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 填充背景色
        canvas.drawColor(Color.DKGRAY)

        // 绘制原图到对应位置
        val srcX = when (direction) {
            "left" -> 0
            "right" -> pixels
            else -> pixels
        }
        val srcY = when (direction) {
            "top" -> 0
            "bottom" -> pixels
            else -> pixels
        }

        val dstRect = android.graphics.Rect(
            if (direction == "left") pixels else 0,
            if (direction == "top") pixels else 0,
            if (direction == "left") width + pixels else width,
            if (direction == "top") height + pixels else height
        )

        canvas.drawBitmap(image, null, dstRect, null)

        // 填充边缘区域（简化）
        fillEdges(result, direction, pixels)

        return result
    }

    private fun fillEdges(bitmap: Bitmap, direction: String, pixels: Int) {
        val width = bitmap.width
        val height = bitmap.height
        val canvas = Canvas(bitmap)
        val paint = Paint()

        // 简化边缘填充
        when (direction) {
            "all" -> {
                // 填充四周
                fillEdgeArea(bitmap, "top", pixels)
                fillEdgeArea(bitmap, "bottom", pixels)
                fillEdgeArea(bitmap, "left", pixels)
                fillEdgeArea(bitmap, "right", pixels)
            }
            else -> fillEdgeArea(bitmap, direction, pixels)
        }
    }

    private fun fillEdgeArea(bitmap: Bitmap, direction: String, pixels: Int) {
        val width = bitmap.width
        val height = bitmap.height
        
        for (i in 0 until pixels) {
            val alpha = ((pixels - i).toFloat() / pixels * 255).toInt()
            
            when (direction) {
                "top" -> {
                    for (x in 0 until width) {
                        if (i < height) {
                            val pixel = bitmap.getPixel(x, pixels)
                            val r = Color.red(pixel)
                            val g = Color.green(pixel)
                            val b = Color.blue(pixel)
                            bitmap.setPixel(x, i, Color.argb(alpha, r, g, b))
                        }
                    }
                }
                "bottom" -> {
                    for (x in 0 until width) {
                        val y = height - pixels + i
                        if (y >= 0) {
                            val pixel = bitmap.getPixel(x, height - pixels - 1)
                            val r = Color.red(pixel)
                            val g = Color.green(pixel)
                            val b = Color.blue(pixel)
                            bitmap.setPixel(x, y, Color.argb(alpha, r, g, b))
                        }
                    }
                }
                "left" -> {
                    for (y in 0 until height) {
                        if (i < width) {
                            val pixel = bitmap.getPixel(pixels, y)
                            val r = Color.red(pixel)
                            val g = Color.green(pixel)
                            val b = Color.blue(pixel)
                            bitmap.setPixel(i, y, Color.argb(alpha, r, g, b))
                        }
                    }
                }
                "right" -> {
                    for (y in 0 until height) {
                        val x = width - pixels + i
                        if (x >= 0) {
                            val pixel = bitmap.getPixel(width - pixels - 1, y)
                            val r = Color.red(pixel)
                            val g = Color.green(pixel)
                            val b = Color.blue(pixel)
                            bitmap.setPixel(x, y, Color.argb(alpha, r, g, b))
                        }
                    }
                }
            }
        }
    }

    private fun fixFlawsInImage(bitmap: Bitmap, strength: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val width = result.width
        val height = result.height

        // 简单去噪
        val kernelSize = (3 + strength * 4).toInt()
        
        for (x in kernelSize until width - kernelSize) {
            for (y in kernelSize until height - kernelSize) {
                var r = 0
                var g = 0
                var b = 0
                var count = 0

                for (dx in -kernelSize/2..kernelSize/2) {
                    for (dy in -kernelSize/2..kernelSize/2) {
                        val pixel = result.getPixel(x + dx, y + dy)
                        r += Color.red(pixel)
                        g += Color.green(pixel)
                        b += Color.blue(pixel)
                        count++
                    }
                }

                // 轻微混合
                val original = result.getPixel(x, y)
                val newR = (Color.red(original) * (1 - strength) + (r / count) * strength).toInt()
                val newG = (Color.green(original) * (1 - strength) + (g / count) * strength).toInt()
                val newB = (Color.blue(original) * (1 - strength) + (b / count) * strength).toInt()

                result.setPixel(x, y, Color.rgb(newR, newG, newB))
            }
        }

        return result
    }

    private fun saveBitmap(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    /**
     * 获取输出目录
     */
    fun getOutputDirectory(): File = outputDir

    /**
     * 获取可用模式
     */
    fun getAvailableModes(): List<Pair<String, String>> {
        return listOf(
            MODE_INPAINT to "局部重绘",
            MODE_OUTPAINT to "图像外扩",
            MODE_REMOVE_OBJECT to "物体移除",
            MODE_FIX_FLAWS to "瑕疵修复",
            MODE_SKETCH to "涂鸦重绘",
            MODE_FACE_RESTORE to "面部修复"
        )
    }
}

/**
 * 修复进度
 */
sealed class InpaintProgress {
    data class Status(val message: String) : InpaintProgress()
    data class Progress(val percent: Int, val message: String) : InpaintProgress()
    data class Completed(
        val outputPath: String,
        val width: Int,
        val height: Int,
        val mode: String
    ) : InpaintProgress()
    data class Error(val message: String) : InpaintProgress()
}
