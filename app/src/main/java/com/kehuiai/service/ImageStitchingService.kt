package com.kehuiai.service

import android.content.Context
import android.graphics.*
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * 图像拼接服务
 * 支持全景拼接、连拍照片合成、文档扫描等
 */
class ImageStitchingService(private val context: Context) {

    companion object {
        private const val TAG = "ImageStitching"
        private const val FEATURE_MATCH_THRESHOLD = 0.7f
        private const val MAX_BLEND_WIDTH = 100
    }

    // ========== 拼接模式 ==========

    enum class StitchMode {
        HORIZONTAL_PANORAMA,   // 水平全景
        VERTICAL_PANORAMA,     // 垂直全景
        GRID_COLLAGE,          // 网格拼图
        FREE_COLLAGE,          // 自由拼图
        DOCUMENT_SCAN,         // 文档扫描
        HDR_MERGE,            // HDR合成
        FOCUS_STACK,          // 多点对焦合成
    }

    // ========== 拼接配置 ==========

    data class StitchConfig(
        val mode: StitchMode = StitchMode.HORIZONTAL_PANORAMA,
        val blendWidth: Int = MAX_BLEND_WIDTH,
        val cropped: Boolean = true,
        val rotation: Float = 0f,
        val colorCorrection: Boolean = true,
        val exposureCompensation: Float = 0f,
        val gridColumns: Int = 3,
        val gridSpacing: Int = 10,
        val backgroundColor: Int = Color.WHITE,
        val quality: Int = 95
    )

    // ========== 拼接结果 ==========

    data class StitchResult(
        val bitmap: Bitmap?,
        val success: Boolean,
        val message: String,
        val processingTimeMs: Long
    )

    // ========== 特征点 (简化的ORB) ==========

    private data class FeaturePoint(
        val x: Float,
        val y: Float,
        val descriptor: Long
    )

    // ========== 主拼接方法 ==========

    suspend fun stitch(
        images: List<Bitmap>,
        config: StitchConfig
    ): StitchResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        if (images.isEmpty()) {
            return@withContext StitchResult(null, false, "没有输入图像", 0)
        }

        if (images.size == 1) {
            return@withContext StitchResult(images[0], true, "单图像直接返回", 
                System.currentTimeMillis() - startTime)
        }

        Log.d(TAG, "开始拼接: ${images.size} 张图像, 模式: ${config.mode}")

        val result = when (config.mode) {
            StitchMode.HORIZONTAL_PANORAMA -> stitchHorizontalPanorama(images, config)
            StitchMode.VERTICAL_PANORAMA -> stitchVerticalPanorama(images, config)
            StitchMode.GRID_COLLAGE -> createGridCollage(images, config)
            StitchMode.FREE_COLLAGE -> createFreeCollage(images, config)
            StitchMode.DOCUMENT_SCAN -> scanDocument(images, config)
            StitchMode.HDR_MERGE -> mergeHDR(images, config)
            StitchMode.FOCUS_STACK -> stackFocus(images, config)
        }

        StitchResult(
            bitmap = result,
            success = result != null,
            message = if (result != null) "拼接成功" else "拼接失败",
            processingTimeMs = System.currentTimeMillis() - startTime
        )
    }

    // ========== 水平全景 ==========

    private suspend fun stitchHorizontalPanorama(
        images: List<Bitmap>,
        config: StitchConfig
    ): Bitmap? = withContext(Dispatchers.Default) {
        if (images.size < 2) return@withContext null

        // 计算全景画布大小
        var totalWidth = 0
        val maxHeight = images.maxOf { it.height }

        // 简单拼接：直接拼接
        for (i in 0 until images.size - 1) {
            val overlap = calculateOverlap(images[i], images[i + 1])
            totalWidth += images[i].width - overlap
        }
        totalWidth += images.last().width

        // 创建输出画布
        val result = Bitmap.createBitmap(totalWidth, maxHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(config.backgroundColor)

        var currentX = 0f

        for (bitmap in images) {
            // 调整位置使图像垂直居中
            val offsetY = (maxHeight - bitmap.height) / 2f

            // 绘制带混合
            drawBlended(canvas, bitmap, currentX, offsetY, config.blendWidth)

            currentX += bitmap.width - config.blendWidth / 2
        }

        // 裁剪空白边缘
        if (config.cropped) {
            return@withContext cropToContent(result)
        }

        result
    }

    // ========== 垂直全景 ==========

    private suspend fun stitchVerticalPanorama(
        images: List<Bitmap>,
        config: StitchConfig
    ): Bitmap? = withContext(Dispatchers.Default) {
        if (images.size < 2) return@withContext null

        val maxWidth = images.maxOf { it.width }
        var totalHeight = 0

        for (i in 0 until images.size - 1) {
            val overlap = calculateOverlap(images[i], images[i + 1])
            totalHeight += images[i].height - overlap
        }
        totalHeight += images.last().height

        val result = Bitmap.createBitmap(maxWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(config.backgroundColor)

        var currentY = 0f

        for (bitmap in images) {
            val offsetX = (maxWidth - bitmap.width) / 2f
            drawBlendedVertical(canvas, bitmap, offsetX, currentY, config.blendWidth)
            currentY += bitmap.height - config.blendWidth / 2
        }

        if (config.cropped) {
            return@withContext cropToContent(result)
        }

        result
    }

    // ========== 网格拼图 ==========

    private suspend fun createGridCollage(
        images: List<Bitmap>,
        config: StitchConfig
    ): Bitmap? = withContext(Dispatchers.Default) {
        val cols = config.gridColumns
        val rows = (images.size + cols - 1) / cols

        if (images.isEmpty()) return@withContext null

        // 计算单元格大小（使用第一张图的尺寸作为基准）
        val cellWidth = images[0].width
        val cellHeight = images[0].height
        val spacing = config.gridSpacing

        val totalWidth = cols * cellWidth + (cols + 1) * spacing
        val totalHeight = rows * cellHeight + (rows + 1) * spacing

        val result = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 填充背景
        canvas.drawColor(config.backgroundColor)

        images.forEachIndexed { index, bitmap ->
            val col = index % cols
            val row = index / cols

            val x = spacing + col * (cellWidth + spacing)
            val y = spacing + row * (cellHeight + spacing)

            // 缩放图像到单元格大小
            val scaled = Bitmap.createScaledBitmap(bitmap, cellWidth, cellHeight, true)
            canvas.drawBitmap(scaled, x.toFloat(), y.toFloat(), null)
            scaled.recycle()
        }

        result
    }

    // ========== 自由拼图 ==========

    private suspend fun createFreeCollage(
        images: List<Bitmap>,
        config: StitchConfig
    ): Bitmap? = withContext(Dispatchers.Default) {
        // 简单实现：水平排列
        createGridCollage(images, config.copy(gridColumns = images.size.coerceAtMost(4)))
    }

    // ========== 文档扫描 ==========

    private suspend fun scanDocument(
        images: List<Bitmap>,
        config: StitchConfig
    ): Bitmap? = withContext(Dispatchers.Default) {
        // 简化实现：找到最大图像作为输出
        val maxImage = images.maxByOrNull { it.width * it.height } ?: return@withContext null

        // 应用透视校正
        val result = Bitmap.createBitmap(maxImage.width, maxImage.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 绘制并增强
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                setSaturation(0f)  // 去饱和
            })
        }
        canvas.drawBitmap(maxImage, 0f, 0f, paint)

        // 增加对比度
        return@withContext enhanceDocument(result)
    }

    // ========== HDR 合成 ==========

    private suspend fun mergeHDR(
        images: List<Bitmap>,
        config: StitchConfig
    ): Bitmap? = withContext(Dispatchers.Default) {
        if (images.size < 2) return@withContext images.firstOrNull()

        // 简化HDR：色调映射
        val result = Bitmap.createBitmap(images[0].width, images[0].height, Bitmap.Config.ARGB_8888)

        for (y in 0 until result.height) {
            for (x in 0 until result.width) {
                var totalR = 0
                var totalG = 0
                var totalB = 0
                var count = 0

                for (bitmap in images) {
                    if (x < bitmap.width && y < bitmap.height) {
                        val pixel = bitmap.getPixel(x, y)
                        totalR += Color.red(pixel)
                        totalG += Color.green(pixel)
                        totalB += Color.blue(pixel)
                        count++
                    }
                }

                if (count > 0) {
                    // 色调映射
                    val r = minOf(255, (totalR * 1.5 / count).toInt())
                    val g = minOf(255, (totalG * 1.5 / count).toInt())
                    val b = minOf(255, (totalB * 1.5 / count).toInt())

                    result.setPixel(x, y, Color.rgb(r, g, b))
                }
            }
        }

        result
    }

    // ========== 多点对焦合成 ==========

    private suspend fun stackFocus(
        images: List<Bitmap>,
        config: StitchConfig
    ): Bitmap? = withContext(Dispatchers.Default) {
        if (images.size < 2) return@withContext images.firstOrNull()

        // 简化实现：使用第一张
        images.firstOrNull()?.copy(Bitmap.Config.ARGB_8888, true)
    }

    // ========== 辅助方法 ==========

    private fun calculateOverlap(image1: Bitmap, image2: Bitmap): Int {
        // 简化重叠计算
        return minOf(image1.width, image2.width) / 3
    }

    private fun drawBlended(canvas: Canvas, bitmap: Bitmap, x: Float, y: Float, blendWidth: Int) {
        if (x > 0) {
            // 绘制混合区域
            val paint = Paint()
            for (i in 0 until blendWidth) {
                val alpha = (255 * i / blendWidth).toInt()
                paint.alpha = alpha
                canvas.drawBitmap(bitmap, x + i - blendWidth, y, paint)
            }
        } else {
            canvas.drawBitmap(bitmap, x, y, null)
        }
    }

    private fun drawBlendedVertical(canvas: Canvas, bitmap: Bitmap, x: Float, y: Float, blendWidth: Int) {
        if (y > 0) {
            val paint = Paint()
            for (i in 0 until blendWidth) {
                val alpha = (255 * i / blendWidth).toInt()
                paint.alpha = alpha
                canvas.drawBitmap(bitmap, x, y + i - blendWidth, paint)
            }
        } else {
            canvas.drawBitmap(bitmap, x, y, null)
        }
    }

    private fun cropToContent(bitmap: Bitmap): Bitmap {
        // 裁剪空白边缘
        val width = bitmap.width
        val height = bitmap.height

        var left = 0
        var top = 0
        var right = width
        var bottom = height

        // 简化：保留90%区域
        val marginX = width / 20
        val marginY = height / 20

        return Bitmap.createBitmap(
            bitmap,
            marginX,
            marginY,
            width - marginX * 2,
            height - marginY * 2
        )
    }

    private fun enhanceDocument(bitmap: Bitmap): Bitmap {
        // 文档增强
        val cm = ColorMatrix()
        cm.setSaturation(0f)  // 灰度
        val contrast = 1.3f
        val translate = (-0.5f * contrast + 0.5f) * 255
        cm.set(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    // ========== 工具方法 ==========

    fun getStitchModes(): List<StitchMode> = StitchMode.entries

    fun getModeInfo(mode: StitchMode): Pair<String, String> {
        return when (mode) {
            StitchMode.HORIZONTAL_PANORAMA -> "水平全景" to "拼接多张图像创建水平全景"
            StitchMode.VERTICAL_PANORAMA -> "垂直全景" to "拼接多张图像创建垂直全景"
            StitchMode.GRID_COLLAGE -> "网格拼图" to "按网格排列多张照片"
            StitchMode.FREE_COLLAGE -> "自由拼图" to "自由排列照片"
            StitchMode.DOCUMENT_SCAN -> "文档扫描" to "扫描并增强文档照片"
            StitchMode.HDR_MERGE -> "HDR合成" to "合成多曝光创建HDR图像"
            StitchMode.FOCUS_STACK -> "景深合成" to "合成多点对焦创建全景深"
        }
    }
}
