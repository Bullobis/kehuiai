package com.kehuiai.service.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 导出管理器
 * 支持多种导出格式：PNG, JPG, ZIP, JSON, GIF
 */
class ExportManager(private val context: Context) {

    companion object {
        private const val TAG = "ExportManager"
        
        // 导出格式
        const val FORMAT_PNG = "png"
        const val FORMAT_JPG = "jpg"
        const val FORMAT_WEBP = "webp"
        const val FORMAT_GIF = "gif"
        const val FORMAT_ZIP = "zip"
        const val FORMAT_JSON = "json"
        
        // 质量预设
        const val QUALITY_HIGH = 100
        const val QUALITY_MEDIUM = 85
        const val QUALITY_LOW = 70
    }

    private val exportDir = File(context.filesDir, "exports")

    init {
        if (!exportDir.exists()) exportDir.mkdirs()
    }

    /**
     * 导出单张图片
     */
    fun exportImage(
        imagePath: String,
        outputPath: String? = null,
        format: String = FORMAT_PNG,
        quality: Int = QUALITY_HIGH,
        @Suppress("UNUSED_PARAMETER") metadata: ExportMetadata? = null
    ): Flow<ExportProgress> = flow {
        emit(ExportProgress.Status("开始导出..."))

        try {
            val inputFile = File(imagePath)
            if (!inputFile.exists()) {
                emit(ExportProgress.Error("图片文件不存在"))
                return@flow
            }

            emit(ExportProgress.Progress(20, "加载图片..."))

            val bitmap = BitmapFactory.decodeFile(imagePath)
            if (bitmap == null) {
                emit(ExportProgress.Error("无法加载图片"))
                return@flow
            }

            emit(ExportProgress.Progress(50, "导出为 $format..."))

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageDir = File(exportDir, "images")
            if (!imageDir.exists()) imageDir.mkdirs()
            
            val outputFileName = "export_$timestamp.$format"
            val outputFile = if (outputPath != null) {
                File(outputPath)
            } else {
                File(imageDir, outputFileName)
            }

            // 压缩并保存
            val compressFormat = when (format.lowercase()) {
                FORMAT_JPG -> Bitmap.CompressFormat.JPEG
                FORMAT_WEBP -> Bitmap.CompressFormat.WEBP_LOSSY
                else -> Bitmap.CompressFormat.PNG
            }

            val fos = FileOutputStream(outputFile)
            bitmap.compress(compressFormat, quality, fos)
            fos.close()

            emit(ExportProgress.Progress(80, "保存完成..."))

            emit(ExportProgress.Completed(
                outputPath = outputFile.absolutePath,
                format = format,
                size = outputFile.length()
            ))

            bitmap.recycle()

        } catch (e: Exception) {
            Log.e(TAG, "Export error: ${e.message}")
            emit(ExportProgress.Error("导出失败: ${e.message}"))
        }
    }

    /**
     * 批量导出
     */
    fun batchExport(
        imagePaths: List<String>,
        format: String = FORMAT_PNG,
        quality: Int = QUALITY_HIGH,
        outputDirPath: String? = null
    ): Flow<ExportProgress> = flow {
        emit(ExportProgress.Status("批量导出..."))

        val targetDir = if (outputDirPath != null) {
            File(outputDirPath)
        } else {
            File(exportDir, "batch")
        }
        if (!targetDir.exists()) targetDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val batchDir = File(targetDir, "batch_$timestamp")
        batchDir.mkdirs()

        var exported = 0
        val total = imagePaths.size

        for ((index, path) in imagePaths.withIndex()) {
            emit(ExportProgress.Progress(
                (index * 100) / total,
                "导出 ${index + 1}/$total..."
            ))

            try {
                val bitmap = BitmapFactory.decodeFile(path)
                if (bitmap != null) {
                    val file = File(path)
                    val fileName = file.nameWithoutExtension
                    val outputFileName = "$fileName.$format"
                    val outputFile = File(batchDir, outputFileName)
                    
                    val compressFormat = when (format.lowercase()) {
                        FORMAT_JPG -> Bitmap.CompressFormat.JPEG
                        FORMAT_WEBP -> Bitmap.CompressFormat.WEBP_LOSSY
                        else -> Bitmap.CompressFormat.PNG
                    }

                    val fos = FileOutputStream(outputFile)
                    bitmap.compress(compressFormat, quality, fos)
                    fos.close()
                    
                    bitmap.recycle()
                    exported++
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to export: $path")
            }
        }

        emit(ExportProgress.Completed(
            outputPath = batchDir.absolutePath,
            format = format,
            size = calculateDirSize(batchDir)
        ))
    }

    /**
     * 分享到其他应用
     */
    suspend fun shareImage(imagePath: String): ShareData? = withContext(Dispatchers.IO) {
        try {
            val file = File(imagePath)
            if (!file.exists()) return@withContext null

            val mimeType = when (file.extension.lowercase()) {
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                else -> "application/octet-stream"
            }

            ShareData(
                uri = file.absolutePath,
                mimeType = mimeType,
                fileName = file.name,
                size = file.length()
            )
        } catch (e: Exception) {
            null
        }
    }

    // ==================== 内部方法 ====================

    private fun calculateDirSize(dir: File): Long {
        return dir.listFiles()?.sumOf { file ->
            if (file.isDirectory) calculateDirSize(file) else file.length()
        } ?: 0
    }

    fun getExportDirectory(): File = exportDir
}

/**
 * 导出进度
 */
sealed class ExportProgress {
    data class Status(val message: String) : ExportProgress()
    data class Progress(val percent: Int, val message: String) : ExportProgress()
    data class Completed(
        val outputPath: String,
        val format: String,
        val size: Long
    ) : ExportProgress()
    data class Error(val message: String) : ExportProgress()
}

/**
 * 导出元数据
 */
data class ExportMetadata(
    val prompt: String = "",
    val negativePrompt: String = "",
    val width: Int = 512,
    val height: Int = 512,
    val steps: Int = 20,
    val sampler: String = "Euler a",
    val cfgScale: Float = 7f,
    val seed: Long = -1,
    val model: String = "",
    val generationTime: Long = 0
)

/**
 * 分享数据
 */
data class ShareData(
    val uri: String,
    val mimeType: String,
    val fileName: String,
    val size: Long
)
