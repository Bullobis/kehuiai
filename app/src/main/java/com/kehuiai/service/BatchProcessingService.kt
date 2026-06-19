package com.kehuiai.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * 批量处理服务
 * 支持批量图片处理、格式转换、尺寸调整等
 */
class BatchProcessingService(private val context: Context) {

    companion object {
        private const val TAG = "BatchProcessing"
    }

    // ========== 批量操作类型 ==========

    enum class BatchOperation {
        RESIZE,           // 调整尺寸
        FORMAT_CONVERT,   // 格式转换
        WATERMARK,        // 添加水印
        COMPRESS,         // 压缩
        THUMBNAIL,        // 生成缩略图
        FILTER_APPLY,     // 应用滤镜
        QUALITY_ENHANCE,  // 质量增强
        BATCH_RENAME      // 批量重命名
    }

    // ========== 批量配置 ==========

    data class BatchConfig(
        val operation: BatchOperation = BatchOperation.RESIZE,
        val targetWidth: Int = 1024,
        val targetHeight: Int = 1024,
        val targetFormat: String = "png",  // png, jpg, webp
        val quality: Int = 90,
        val watermarkText: String = "",
        val watermarkPosition: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT,
        val preserveAspectRatio: Boolean = true,
        val parallelProcessing: Boolean = true,
        val maxParallelism: Int = 4
    )

    enum class WatermarkPosition {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT,
        CENTER_LEFT, CENTER, CENTER_RIGHT,
        BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
    }

    data class BatchResult(
        val totalFiles: Int,
        val successCount: Int,
        val failedCount: Int,
        val outputPaths: List<String>,
        val errors: List<String>,
        val totalTimeMs: Long
    )

    data class ProgressInfo(
        val currentIndex: Int,
        val totalCount: Int,
        val currentFile: String,
        val percentage: Int,
        val estimatedTimeRemainingMs: Long
    )

    // ========== 批量处理 ==========

    suspend fun processBatch(
        inputPaths: List<String>,
        outputDir: String,
        config: BatchConfig
    ): BatchResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val successPaths = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val counter = AtomicInteger(0)

        // 创建输出目录
        File(outputDir).mkdirs()

        // 确定并发数量
        val parallelism = if (config.parallelProcessing) {
            minOf(config.maxParallelism, inputPaths.size)
        } else {
            1
        }

        Log.d(TAG, "开始批量处理: ${inputPaths.size} 个文件, 并发数: $parallelism")

        // 并行处理
        val jobs = inputPaths.mapIndexed { index, inputPath ->
            async {
                try {
                    processSingleFile(inputPath, outputDir, config, index)
                } catch (e: Exception) {
                    Log.e(TAG, "处理失败: $inputPath", e)
                    errors.add("$inputPath: ${e.message}")
                    null
                }
            }
        }

        // 收集结果
        jobs.awaitAll().forEach { result ->
            result?.let {
                successPaths.add(it)
                counter.incrementAndGet()
            }
        }

        val totalTime = System.currentTimeMillis() - startTime

        BatchResult(
            totalFiles = inputPaths.size,
            successCount = successPaths.size,
            failedCount = errors.size,
            outputPaths = successPaths,
            errors = errors,
            totalTimeMs = totalTime
        )
    }

    // ========== 进度流 ==========

    fun processBatchWithProgress(
        inputPaths: List<String>,
        outputDir: String,
        config: BatchConfig
    ): Flow<ProgressInfo> = flow {
        val startTime = System.currentTimeMillis()

        // 创建输出目录
        File(outputDir).mkdirs()

        inputPaths.forEachIndexed { index, inputPath ->
            val current = index + 1
            val elapsed = System.currentTimeMillis() - startTime
            val avgTimePerFile = elapsed / current
            val remaining = (inputPaths.size - current) * avgTimePerFile

            emit(ProgressInfo(
                currentIndex = current,
                totalCount = inputPaths.size,
                currentFile = File(inputPath).name,
                percentage = (current * 100) / inputPaths.size,
                estimatedTimeRemainingMs = remaining
            ))

            // 处理文件
            try {
                processSingleFile(inputPath, outputDir, config, index)
            } catch (e: Exception) {
                Log.e(TAG, "处理失败: $inputPath", e)
            }
        }
    }

    // ========== 单文件处理 ==========

    private suspend fun processSingleFile(
        inputPath: String,
        outputDir: String,
        config: BatchConfig,
        index: Int
    ): String = withContext(Dispatchers.IO) {
        val inputFile = File(inputPath)
        val originalBitmap = BitmapFactory.decodeFile(inputPath)
            ?: throw IllegalArgumentException("无法读取图片: $inputPath")

        val processedBitmap = when (config.operation) {
            BatchOperation.RESIZE -> resizeImage(originalBitmap, config)
            BatchOperation.FORMAT_CONVERT -> originalBitmap
            BatchOperation.WATERMARK -> addWatermark(originalBitmap, config)
            BatchOperation.COMPRESS -> compressImage(originalBitmap, config)
            BatchOperation.THUMBNAIL -> createThumbnail(originalBitmap, config)
            BatchOperation.FILTER_APPLY -> applyFilter(originalBitmap, config)
            BatchOperation.QUALITY_ENHANCE -> enhanceQuality(originalBitmap)
            BatchOperation.BATCH_RENAME -> originalBitmap
        }

        // 生成输出文件名
        val extension = when (config.operation) {
            BatchOperation.FORMAT_CONVERT -> config.targetFormat
            else -> inputFile.extension.ifEmpty { "png" }
        }
        val outputName = "${inputFile.nameWithoutExtension}_processed.$extension"
        val outputPath = File(outputDir, outputName).absolutePath

        // 保存
        saveBitmap(processedBitmap, outputPath, config)

        // 回收
        if (processedBitmap != originalBitmap) {
            originalBitmap.recycle()
        }
        processedBitmap.recycle()

        outputPath
    }

    // ========== 图像操作 ==========

    private fun resizeImage(bitmap: Bitmap, config: BatchConfig): Bitmap {
        val (targetW, targetH) = if (config.preserveAspectRatio) {
            calculateAspectRatioSize(bitmap.width, bitmap.height, config.targetWidth, config.targetHeight)
        } else {
            config.targetWidth to config.targetHeight
        }
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private fun calculateAspectRatioSize(srcW: Int, srcH: Int, maxW: Int, maxH: Int): Pair<Int, Int> {
        val srcRatio = srcW.toFloat() / srcH
        val maxRatio = maxW.toFloat() / maxH

        return if (srcRatio > maxRatio) {
            maxW to (maxW / srcRatio).toInt()
        } else {
            (maxH * srcRatio).toInt() to maxH
        }
    }

    private fun addWatermark(bitmap: Bitmap, config: BatchConfig): Bitmap {
        // 简化实现，实际应该用Canvas绘制文字
        return bitmap
    }

    private fun compressImage(bitmap: Bitmap, config: BatchConfig): Bitmap {
        // 压缩实际上是保存时的质量设置
        return bitmap
    }

    private fun createThumbnail(bitmap: Bitmap, config: BatchConfig): Bitmap {
        val thumbSize = minOf(config.targetWidth, config.targetHeight)
        val scale = minOf(thumbSize.toFloat() / bitmap.width, thumbSize.toFloat() / bitmap.height)
        val targetW = (bitmap.width * scale).toInt()
        val targetH = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private fun applyFilter(bitmap: Bitmap, config: BatchConfig): Bitmap {
        // 应用简单的滤镜效果
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(result.width * result.height)
        result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)

        for (i in pixels.indices) {
            val color = pixels[i]
            val r = android.graphics.Color.red(color)
            val g = android.graphics.Color.green(color)
            val b = android.graphics.Color.blue(color)

            // 增加饱和度
            val avg = (r + g + b) / 3
            val newR = minOf(255, (r + avg * 0.2f).toInt())
            val newG = minOf(255, (g + avg * 0.2f).toInt())
            val newB = minOf(255, (b + avg * 0.2f).toInt())

            pixels[i] = android.graphics.Color.rgb(newR, newG, newB)
        }

        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        return result
    }

    private fun enhanceQuality(bitmap: Bitmap): Bitmap {
        // 简单的质量增强
        return bitmap
    }

    private fun saveBitmap(bitmap: Bitmap, path: String, config: BatchConfig) {
        val format = when (config.targetFormat.lowercase()) {
            "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
            "webp" -> Bitmap.CompressFormat.WEBP_LOSSY
            else -> Bitmap.CompressFormat.PNG
        }

        val outputStream = File(path).outputStream()
        bitmap.compress(format, config.quality, outputStream)
        outputStream.close()
    }

    // ========== 队列管理 ==========

    private val processingQueue = CoroutineScope(Dispatchers.IO + SupervisorJob())

    data class QueuedTask(
        val id: String,
        val inputPaths: List<String>,
        val outputDir: String,
        val config: BatchConfig,
        val job: Job
    )

    private val activeTasks = mutableMapOf<String, QueuedTask>()

    fun enqueueTask(
        taskId: String,
        inputPaths: List<String>,
        outputDir: String,
        config: BatchConfig
    ): Flow<ProgressInfo> = flow {
        val job = processingQueue.launch {
            processBatchWithProgress(inputPaths, outputDir, config).collect { progress ->
                // 内部处理
            }
        }

        activeTasks[taskId] = QueuedTask(taskId, inputPaths, outputDir, config, job)

        emit(ProgressInfo(0, inputPaths.size, "", 0, 0))
    }

    fun cancelTask(taskId: String) {
        activeTasks[taskId]?.job?.cancel()
        activeTasks.remove(taskId)
    }

    fun getActiveTasks(): List<String> = activeTasks.keys.toList()

    fun release() {
        activeTasks.values.forEach { it.job.cancel() }
        activeTasks.clear()
        processingQueue.cancel()
    }
}
