package com.kehuiai.service

import android.content.Context
import android.graphics.*
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * 背景移除服务
 * 使用多种方法移除背景：颜色分割、边缘检测、深度学习模拟
 */
class BackgroundRemovalService(private val context: Context) {

    companion object {
        private const val TAG = "BackgroundRemoval"
    }

    // ========== 移除模式 ==========

    enum class RemovalMode {
        COLOR_KEY,           // 颜色键控（绿屏/蓝屏）
        EDGE_DETECTION,      // 边缘检测
        INTELLIGENT_CUTOUT,  // 智能抠图
        PORTRAIT_CUTOUT,     // 人像抠图
        OBJECT_DETECTION,    // 物体检测
        MANUAL_CUTOUT,       // 手动抠图
    }

    // ========== 配置 ==========

    data class RemovalConfig(
        val mode: RemovalMode = RemovalMode.INTELLIGENT_CUTOUT,
        val tolerance: Int = 30,           // 颜色容差
        val targetColor: Int = Color.GREEN, // 目标颜色
        val featherRadius: Int = 5,         // 羽化半径
        val edgeSmoothness: Int = 2,        // 边缘平滑度
        val maskExpansion: Int = 0,         // 蒙版扩展
        val refineEdges: Boolean = true,     // 细化边缘
        val fillHoles: Boolean = true,      // 填充孔洞
        val outputFormat: OutputFormat = OutputFormat.MASK_ALPHA,
        val backgroundColor: Int = Color.TRANSPARENT
    )

    enum class OutputFormat {
        TRANSPARENT_PNG,     // 透明PNG
        MASK_ALPHA,          // Alpha蒙版
        BLACK_WHITE_MASK,    // 黑白蒙版
        EDGE_OUTLINE         // 边缘轮廓
    }

    // ========== 结果 ==========

    data class RemovalResult(
        val foreground: Bitmap?,
        val mask: Bitmap?,
        val success: Boolean,
        val confidence: Float,
        val processingTimeMs: Long
    )

    // ========== 主移除方法 ==========

    suspend fun removeBackground(
        bitmap: Bitmap,
        config: RemovalConfig
    ): RemovalResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        Log.d(TAG, "开始背景移除: ${config.mode}")

        val mask = when (config.mode) {
            RemovalMode.COLOR_KEY -> colorKeying(bitmap, config)
            RemovalMode.EDGE_DETECTION -> edgeDetection(bitmap, config)
            RemovalMode.INTELLIGENT_CUTOUT -> intelligentCutout(bitmap, config)
            RemovalMode.PORTRAIT_CUTOUT -> portraitCutout(bitmap, config)
            RemovalMode.OBJECT_DETECTION -> objectDetection(bitmap, config)
            RemovalMode.MANUAL_CUTOUT -> bitmap.copy(Bitmap.Config.ARGB_8888, true)
        }

        // 应用蒙版
        val foreground = mask?.let { applyMask(bitmap, it, config) }

        // 计算置信度
        val confidence = calculateConfidence(mask)

        RemovalResult(
            foreground = foreground,
            mask = mask,
            success = mask != null,
            confidence = confidence,
            processingTimeMs = System.currentTimeMillis() - startTime
        )
    }

    // ========== 颜色键控 ==========

    private suspend fun colorKeying(bitmap: Bitmap, config: RemovalConfig): Bitmap? = 
        withContext(Dispatchers.Default) {
            val width = bitmap.width
            val height = bitmap.height
            val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            val targetR = Color.red(config.targetColor)
            val targetG = Color.green(config.targetColor)
            val targetB = Color.blue(config.targetColor)

            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)

                    // 计算颜色距离
                    val distance = sqrt(
                        ((r - targetR).toFloat().pow(2) +
                        (g - targetG).toFloat().pow(2) +
                        (b - targetB).toFloat().pow(2))
                    )

                    // 判断是否在容差范围内
                    val alpha = if (distance > config.tolerance) 255 else 0
                    mask.setPixel(x, y, Color.argb(alpha, 255, 255, 255))
                }
            }

            refineMask(mask, config)
        }

    // ========== 边缘检测 ==========

    private suspend fun edgeDetection(bitmap: Bitmap, config: RemovalConfig): Bitmap? =
        withContext(Dispatchers.Default) {
            val width = bitmap.width
            val height = bitmap.height
            val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            // Sobel边缘检测
            for (y in 1 until height - 1) {
                for (x in 1 until width - 1) {
                    val gx = calculateGradientX(bitmap, x, y)
                    val gy = calculateGradientY(bitmap, x, y)
                    val magnitude = sqrt(gx * gx + gy * gy).toInt().coerceIn(0, 255)

                    mask.setPixel(x, y, Color.argb(magnitude, 255, 255, 255))
                }
            }

            // 边缘内填充
            fillInsideEdges(mask)

            refineMask(mask, config)
        }

    private fun calculateGradientX(bitmap: Bitmap, x: Int, y: Int): Float {
        val left = bitmap.getPixel(x - 1, y)
        val right = bitmap.getPixel(x + 1, y)
        return (Color.red(right) - Color.red(left)).toFloat()
    }

    private fun calculateGradientY(bitmap: Bitmap, x: Int, y: Int): Float {
        val top = bitmap.getPixel(x, y - 1)
        val bottom = bitmap.getPixel(x, y + 1)
        return (Color.red(bottom) - Color.red(top)).toFloat()
    }

    // ========== 智能抠图 ==========

    private suspend fun intelligentCutout(bitmap: Bitmap, config: RemovalConfig): Bitmap? =
        withContext(Dispatchers.Default) {
            // 结合多种方法
            val width = bitmap.width
            val height = bitmap.height
            val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            // 分析图像中心区域作为主体
            val centerX = width / 2
            val centerY = height / 2
            val centerPixel = bitmap.getPixel(centerX, centerY)
            
            val centerR = Color.red(centerPixel)
            val centerG = Color.green(centerPixel)
            val centerB = Color.blue(centerPixel)

            // 基于中心颜色扩散
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)

                    // 计算与中心颜色的相似度
                    val similarity = 1f - (
                        abs(r - centerR) + abs(g - centerG) + abs(b - centerB)
                    ) / (255f * 3)

                    val alpha = (similarity * 255).toInt().coerceIn(0, 255)
                    mask.setPixel(x, y, Color.argb(alpha, 255, 255, 255))
                }
            }

            refineMask(mask, config)
        }

    // ========== 人像抠图 ==========

    private suspend fun portraitCutout(bitmap: Bitmap, config: RemovalConfig): Bitmap? =
        withContext(Dispatchers.Default) {
            // 简化人像检测：假设人在中心
            val width = bitmap.width
            val height = bitmap.height
            val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            val centerX = width / 2
            val centerY = height / 2
            val maxRadius = minOf(width, height) / 2

            // 创建椭圆形蒙版（人像形状）
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val dx = (x - centerX).toFloat() / (maxRadius * 0.7f)
                    val dy = (y - centerY).toFloat() / (maxRadius * 0.9f)
                    val distance = sqrt(dx * dx + dy * dy)

                    val alpha = if (distance < 1f) 255 else 0
                    mask.setPixel(x, y, Color.argb(alpha, 255, 255, 255))
                }
            }

            refineMask(mask, config)
        }

    // ========== 物体检测 ==========

    private suspend fun objectDetection(bitmap: Bitmap, config: RemovalConfig): Bitmap? =
        withContext(Dispatchers.Default) {
            // 使用颜色分割作为简化方案
            intelligentCutout(bitmap, config)
        }

    // ========== 蒙版细化 ==========

    private fun refineMask(mask: Bitmap, config: RemovalConfig): Bitmap {
        if (!config.refineEdges) return mask

        val width = mask.width
        val height = mask.height
        val result = mask.copy(Bitmap.Config.ARGB_8888, true)

        // 高斯模糊边缘
        if (config.featherRadius > 0) {
            for (y in config.featherRadius until height - config.featherRadius) {
                for (x in config.featherRadius until width - config.featherRadius) {
                    var totalAlpha = 0
                    var count = 0

                    for (fy in -config.featherRadius..config.featherRadius) {
                        for (fx in -config.featherRadius..config.featherRadius) {
                            totalAlpha += Color.alpha(mask.getPixel(x + fx, y + fy))
                            count++
                        }
                    }

                    val avgAlpha = totalAlpha / count
                    val pixel = mask.getPixel(x, y)
                    result.setPixel(x, y, Color.argb(avgAlpha, 255, 255, 255))
                }
            }
        }

        return result
    }

    private fun fillInsideEdges(mask: Bitmap) {
        // 简单的洪水填充
        val width = mask.width
        val height = mask.height
        val visited = Array(height) { BooleanArray(width) }

        // 从中心开始填充
        val centerX = width / 2
        val centerY = height / 2

        if (Color.alpha(mask.getPixel(centerX, centerY)) > 128) {
            val queue = ArrayDeque<Pair<Int, Int>>()
            queue.add(centerX to centerY)

            while (queue.isNotEmpty()) {
                val head = queue.removeFirstOrNull()
                if (head == null) break
                val (x, y) = head
                if (x < 0 || x >= width || y < 0 || y >= height) continue
                if (visited[y][x]) continue
                if (Color.alpha(mask.getPixel(x, y)) > 128) continue

                visited[y][x] = true
                mask.setPixel(x, y, Color.argb(255, 255, 255, 255))

                queue.add(x + 1 to y)
                queue.add(x - 1 to y)
                queue.add(x to y + 1)
                queue.add(x to y - 1)
            }
        }
    }

    // ========== 应用蒙版 ==========

    private fun applyMask(bitmap: Bitmap, mask: Bitmap?, config: RemovalConfig): Bitmap? {
        if (mask == null) return null

        val result = when (config.outputFormat) {
            OutputFormat.TRANSPARENT_PNG -> createTransparentImage(bitmap, mask)
            OutputFormat.MASK_ALPHA -> bitmap.copy(Bitmap.Config.ARGB_8888, true).apply {
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val maskAlpha = Color.alpha(mask.getPixel(x, y))
                        val pixel = getPixel(x, y)
                        setPixel(x, y, Color.argb(maskAlpha, 
                            Color.red(pixel), Color.green(pixel), Color.blue(pixel)))
                    }
                }
            }
            OutputFormat.BLACK_WHITE_MASK -> mask
            OutputFormat.EDGE_OUTLINE -> createEdgeOutline(bitmap, mask)
        }

        return result
    }

    private fun createTransparentImage(bitmap: Bitmap, mask: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)

        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val maskAlpha = Color.alpha(mask.getPixel(x, y))
                val pixel = bitmap.getPixel(x, y)

                result.setPixel(x, y, Color.argb(
                    maskAlpha,
                    Color.red(pixel),
                    Color.green(pixel),
                    Color.blue(pixel)
                ))
            }
        }

        return result
    }

    private fun createEdgeOutline(bitmap: Bitmap, mask: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 绘制原图
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        // 绘制边缘
        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        for (y in 1 until mask.height - 1) {
            for (x in 1 until mask.width - 1) {
                val alpha = Color.alpha(mask.getPixel(x, y))
                val leftAlpha = Color.alpha(mask.getPixel(x - 1, y))
                val rightAlpha = Color.alpha(mask.getPixel(x + 1, y))
                val topAlpha = Color.alpha(mask.getPixel(x, y - 1))
                val bottomAlpha = Color.alpha(mask.getPixel(x, y + 1))

                // 边缘检测
                if (alpha > 128 && (leftAlpha < 128 || rightAlpha < 128 || 
                    topAlpha < 128 || bottomAlpha < 128)) {
                    result.setPixel(x, y, Color.RED)
                }
            }
        }

        return result
    }

    // ========== 置信度计算 ==========

    private fun calculateConfidence(mask: Bitmap?): Float {
        if (mask == null) return 0f
        var edgePixels = 0
        var totalPixels = 0

        for (y in 1 until mask.height - 1) {
            for (x in 1 until mask.width - 1) {
                val alpha = Color.alpha(mask.getPixel(x, y))
                if (alpha > 0 && alpha < 255) {
                    edgePixels++
                }
                totalPixels++
            }
        }

        // 边缘越少，置信度越高
        val edgeRatio = edgePixels.toFloat() / totalPixels
        return (1f - edgeRatio * 10).coerceIn(0f, 1f)
    }

    // ========== 工具方法 ==========

    fun getRemovalModes(): List<RemovalMode> = RemovalMode.entries

    fun getModeInfo(mode: RemovalMode): Pair<String, String> {
        return when (mode) {
            RemovalMode.COLOR_KEY -> "颜色键控" to "移除指定颜色背景（如绿幕）"
            RemovalMode.EDGE_DETECTION -> "边缘检测" to "基于边缘检测的抠图"
            RemovalMode.INTELLIGENT_CUTOUT -> "智能抠图" to "智能分析主体并移除背景"
            RemovalMode.PORTRAIT_CUTOUT -> "人像抠图" to "专门针对人像的抠图"
            RemovalMode.OBJECT_DETECTION -> "物体检测" to "检测并抠出特定物体"
            RemovalMode.MANUAL_CUTOUT -> "手动抠图" to "提供蒙版进行手动编辑"
        }
    }
}
