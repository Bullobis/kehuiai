package com.kehuiai.service.pro

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 快绘AI Pro v4.0.0 - 批量处理器
 */
class ProBatchProcessor(private val context: Context) {

    companion object {
        private const val TAG = "ProBatchProcessor"
    }

    // ========== 任务类型 ==========
    enum class TaskType {
        RESIZE, FILTER, COMPRESS, WATERMARK, CONVERT, THUMBNAIL
    }

    // ========== 任务状态 ==========
    enum class TaskStatus {
        PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
    }

    // ========== 批处理任务 ==========
    data class BatchTask(
        val id: String = UUID.randomUUID().toString(),
        val type: TaskType,
        val inputPaths: List<String>,
        val outputDir: String
    ) {
        var status: TaskStatus = TaskStatus.PENDING
        var progress: Int = 0
        var successCount: Int = 0
        var failCount: Int = 0
    }

    // ========== 配置 ==========
    data class BatchConfig(
        val overwrite: Boolean = false,
        val prefix: String = "",
        val suffix: String = "_batch"
    )

    // ========== 结果 ==========
    data class BatchResult(
        val taskId: String,
        val success: Boolean,
        val outputPaths: List<String>,
        val failedCount: Int
    )

    // ========== 回调 ==========
    interface BatchCallback {
        fun onProgress(task: BatchTask, current: Int, total: Int)
        fun onComplete(task: BatchTask, result: BatchResult)
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val imageProcessor = ProImageProcessor(context)
    private val runningJobs = mutableMapOf<String, Job>()

    // ========== 创建任务 ==========
    fun createResizeTask(inputPaths: List<String>, outputDir: String, maxWidth: Int = 1920, maxHeight: Int = 1080): BatchTask {
        return BatchTask(type = TaskType.RESIZE, inputPaths = inputPaths, outputDir = outputDir)
    }

    fun createFilterTask(inputPaths: List<String>, outputDir: String, filterType: String): BatchTask {
        return BatchTask(type = TaskType.FILTER, inputPaths = inputPaths, outputDir = outputDir)
    }

    fun createCompressTask(inputPaths: List<String>, outputDir: String, quality: Int = 80): BatchTask {
        return BatchTask(type = TaskType.COMPRESS, inputPaths = inputPaths, outputDir = outputDir)
    }

    fun createWatermarkTask(inputPaths: List<String>, outputDir: String, text: String): BatchTask {
        return BatchTask(type = TaskType.WATERMARK, inputPaths = inputPaths, outputDir = outputDir)
    }

    fun createConvertTask(inputPaths: List<String>, outputDir: String, format: String = "png"): BatchTask {
        return BatchTask(type = TaskType.CONVERT, inputPaths = inputPaths, outputDir = outputDir)
    }

    // ========== 执行任务 ==========
    suspend fun executeTask(task: BatchTask, config: BatchConfig = BatchConfig(), callback: BatchCallback? = null): BatchResult = withContext(Dispatchers.IO) {
        val outputPaths = mutableListOf<String>()
        task.status = TaskStatus.RUNNING

        for ((index, inputPath) in task.inputPaths.withIndex()) {
            try {
                val outputFile = processItem(task, inputPath, index, config)
                if (outputFile != null) {
                    outputPaths.add(outputFile)
                    task.successCount++
                } else {
                    task.failCount++
                }
            } catch (e: Exception) {
                task.failCount++
            }

            task.progress = ((index + 1) * 100 / task.inputPaths.size)
            callback?.onProgress(task, index + 1, task.inputPaths.size)
        }

        task.status = if (task.failCount == 0) TaskStatus.COMPLETED else TaskStatus.FAILED

        BatchResult(
            taskId = task.id,
            success = task.status == TaskStatus.COMPLETED,
            outputPaths = outputPaths,
            failedCount = task.failCount
        ).also {
            callback?.onComplete(task, it)
        }
    }

    private fun processItem(task: BatchTask, inputPath: String, index: Int, config: BatchConfig): String? {
        val inputFile = File(inputPath)
        if (!inputFile.exists()) return null

        val outputDir = File(task.outputDir)
        if (!outputDir.exists()) outputDir.mkdirs()

        val baseName = inputFile.nameWithoutExtension
        val ext = when (task.type) {
            TaskType.CONVERT -> "png"
            else -> inputFile.extension
        }
        val outputName = "${config.prefix}${baseName}${config.suffix}.$ext"
        val outputFile = File(outputDir, outputName)

        when (task.type) {
            TaskType.RESIZE -> {
                val bitmap = imageProcessor.loadImage(inputPath) ?: return null
                val scaled = imageProcessor.smartScale(bitmap, 1920, 1080)
                return if (imageProcessor.saveBitmap(scaled, outputFile)) outputFile.absolutePath else null
            }
            TaskType.FILTER -> {
                val bitmap = imageProcessor.loadImage(inputPath) ?: return null
                val filtered = imageProcessor.applyFilter(bitmap, ProImageProcessor.FilterType.SEPIA)
                return if (imageProcessor.saveBitmap(filtered, outputFile)) outputFile.absolutePath else null
            }
            TaskType.COMPRESS -> {
                val bitmap = imageProcessor.loadImage(inputPath) ?: return null
                return if (imageProcessor.saveBitmap(bitmap, outputFile, 80)) outputFile.absolutePath else null
            }
            TaskType.WATERMARK -> {
                val bitmap = imageProcessor.loadImage(inputPath) ?: return null
                val watermarked = imageProcessor.addWatermark(bitmap, "快绘AI")
                return if (imageProcessor.saveBitmap(watermarked, outputFile)) outputFile.absolutePath else null
            }
            TaskType.CONVERT -> {
                val bitmap = imageProcessor.loadImage(inputPath) ?: return null
                return if (imageProcessor.saveBitmap(bitmap, outputFile)) outputFile.absolutePath else null
            }
            TaskType.THUMBNAIL -> {
                val bitmap = imageProcessor.loadImage(inputPath) ?: return null
                val thumb = imageProcessor.centerCrop(bitmap, 200, 200)
                return if (imageProcessor.saveBitmap(thumb, outputFile)) outputFile.absolutePath else null
            }
        }
    }

    // ========== 后台执行 ==========
    fun executeInBackground(task: BatchTask, config: BatchConfig = BatchConfig(), callback: BatchCallback? = null): Job {
        val job = scope.launch { executeTask(task, config, callback) }
        runningJobs[task.id] = job
        return job
    }

    fun cancelTask(taskId: String) {
        runningJobs[taskId]?.cancel()
    }

    fun release() {
        scope.cancel()
        runningJobs.clear()
        imageProcessor.release()
    }
}
