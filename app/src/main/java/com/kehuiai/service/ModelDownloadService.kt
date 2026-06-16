package com.kehuiai.service

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 可绘AI v3.0 模型下载服务
 */
class ModelDownloadService private constructor(private val context: Context) {
    
    companion object {
        const val ACTION_START = "com.kehui.ai.ACTION_DOWNLOAD_START"
        const val ACTION_PROGRESS = "com.kehui.ai.ACTION_DOWNLOAD_PROGRESS"
        const val ACTION_COMPLETE = "com.kehui.ai.ACTION_DOWNLOAD_COMPLETE"
        const val EXTRA_MODEL_ID = "model_id"
        const val EXTRA_MODEL_NAME = "model_name"
        const val EXTRA_DOWNLOAD_URL = "download_url"
        const val EXTRA_IS_ZIP = "is_zip"
        
        private const val TAG = "ModelDownloadService"
        private const val BUFFER_SIZE = 8192
        private const val MAX_RETRY = 3
        
        @Volatile
        private var INSTANCE: ModelDownloadService? = null
        
        fun getInstance(context: Context): ModelDownloadService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModelDownloadService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloadJobs = mutableMapOf<String, Job>()
    
    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()
    
    private val modelsDir: File by lazy {
        File(context.filesDir, "models").also { it.mkdirs() }
    }
    
    /**
     * 下载模型文件
     */
    fun downloadModel(
        modelId: String,
        url: String,
        fileName: String = url.substringAfterLast("/"),
        expectedHash: String? = null,
        onProgress: (Float) -> Unit,
        onComplete: (Boolean, String?) -> Unit
    ) {
        // 如果已经在下载中，则跳过
        if (downloadJobs.containsKey(modelId)) {
            onComplete(false, "下载任务已在进行中")
            return
        }
        
        val job = scope.launch {
            var retryCount = 0
            var lastError: String? = null
            
            while (retryCount < MAX_RETRY) {
                try {
                    val result = downloadFile(modelId, url, fileName, expectedHash, onProgress)
                    if (result) {
                        withContext(Dispatchers.Main) {
                            onComplete(result, null)
                        }
                        return@launch
                    } else {
                        lastError = "下载失败或验证失败"
                        retryCount++
                    }
                } catch (e: CancellationException) {
                    withContext(Dispatchers.Main) {
                        onComplete(false, "下载已取消")
                    }
                    return@launch
                } catch (e: Exception) {
                    lastError = e.message ?: "未知错误"
                    Log.e(TAG, "下载失败 (${retryCount + 1}/$MAX_RETRY): ${e.message}")
                    retryCount++
                    if (retryCount < MAX_RETRY) {
                        delay(2000 * retryCount.toLong()) // 指数退避
                    }
                }
            }
            
            withContext(Dispatchers.Main) {
                onComplete(false, lastError)
            }
        }
        
        downloadJobs[modelId] = job
    }
    
    private suspend fun downloadFile(
        modelId: String,
        urlString: String,
        fileName: String,
        expectedHash: String?,
        onProgress: (Float) -> Unit
    ): Boolean {
        val outputFile = File(modelsDir, fileName)
        val tempFile = File(modelsDir, "$fileName.tmp")
        
        var connection: HttpURLConnection? = null
        var inputStream: java.io.InputStream? = null
        var outputStream: FileOutputStream? = null
        
        try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "KehuiAI/3.6")
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "服务器返回错误码: $responseCode")
                return false
            }
            
            val fileSize = connection.contentLength.toLong()
            Log.i(TAG, "开始下载: $fileName (${formatFileSize(fileSize)})")
            
            inputStream = connection.inputStream
            outputStream = FileOutputStream(tempFile)
            
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var totalBytesRead = 0L
            var lastProgressUpdate = 0L
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                // 检查是否被取消
                yield()
                
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                
                // 更新进度（限制更新频率）
                val now = System.currentTimeMillis()
                if (now - lastProgressUpdate > 100) {
                    val progress = if (fileSize > 0) totalBytesRead.toFloat() / fileSize else 0f
                    updateProgress(modelId, progress)
                    withContext(Dispatchers.Main) {
                        onProgress(progress)
                    }
                    lastProgressUpdate = now
                }
            }
            
            outputStream.flush()
            outputStream.close()
            inputStream.close()
            connection.disconnect()
            
            // 验证文件
            if (expectedHash != null) {
                val actualHash = calculateMD5(tempFile)
                if (actualHash != expectedHash) {
                    Log.e(TAG, "文件校验失败: 期望 $expectedHash, 实际 $actualHash")
                    tempFile.delete()
                    return false
                }
                Log.i(TAG, "文件校验通过: $actualHash")
            }
            
            // 移动到正式路径
            if (outputFile.exists()) {
                outputFile.delete()
            }
            tempFile.renameTo(outputFile)
            
            updateProgress(modelId, 1f)
            Log.i(TAG, "✅ 下载完成: ${outputFile.absolutePath}")
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "下载异常: ${e.message}")
            tempFile.delete()
            throw e
        } finally {
            try { outputStream?.close() } catch (e: Exception) {}
            try { inputStream?.close() } catch (e: Exception) {}
            try { connection?.disconnect() } catch (e: Exception) {}
            downloadJobs.remove(modelId)
        }
    }
    
    /**
     * 计算文件MD5
     */
    private fun calculateMD5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { fis ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "%.1f KB".format(size / 1024.0)
            size < 1024 * 1024 * 1024 -> "%.1f MB".format(size / (1024.0 * 1024))
            else -> "%.2f GB".format(size / (1024.0 * 1024 * 1024))
        }
    }
    
    private fun updateProgress(modelId: String, progress: Float) {
        val current = _downloadProgress.value.toMutableMap()
        current[modelId] = progress.coerceIn(0f, 1f)
        _downloadProgress.value = current
    }
    
    /**
     * 取消下载
     */
    fun cancelDownload(modelId: String) {
        downloadJobs[modelId]?.cancel()
        downloadJobs.remove(modelId)
        val current = _downloadProgress.value.toMutableMap()
        current.remove(modelId)
        _downloadProgress.value = current
        Log.i(TAG, "已取消下载: $modelId")
    }
    
    /**
     * 获取下载状态
     */
    fun isDownloading(modelId: String): Boolean {
        return downloadJobs.containsKey(modelId)
    }
    
    /**
     * 获取已下载的模型文件
     */
    fun getDownloadedModels(): List<File> {
        return modelsDir.listFiles()?.toList() ?: emptyList()
    }
    
    /**
     * 删除模型文件
     */
    fun deleteModel(fileName: String): Boolean {
        val file = File(modelsDir, fileName)
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }
    
    /**
     * 清理所有下载
     */
    fun cancelAllDownloads() {
        downloadJobs.values.forEach { it.cancel() }
        downloadJobs.clear()
        _downloadProgress.value = emptyMap()
    }
    
    /**
     * 释放资源
     */
    fun release() {
        cancelAllDownloads()
        scope.cancel()
        INSTANCE = null
    }
}
