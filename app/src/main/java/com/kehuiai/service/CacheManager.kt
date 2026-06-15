package com.kehuiai.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 缓存管理器
 * 管理图像缓存、模型缓存、临时文件
 */
class CacheManager(private val context: Context) {

    companion object {
        private const val TAG = "CacheManager"
        
        // 缓存类型
        const val TYPE_IMAGE = "image"
        const val TYPE_MODEL = "model"
        const val TYPE_TEMP = "temp"
        const val TYPE_THUMBNAIL = "thumbnail"
        const val TYPE_PREVIEW = "preview"
        
        // 默认缓存大小 (MB)
        const val DEFAULT_MAX_SIZE = 500L
    }

    // 缓存目录
    private val cacheDir = File(context.cacheDir, "kuaihui")
    private val imageCacheDir = File(cacheDir, "images")
    private val modelCacheDir = File(cacheDir, "models")
    private val tempCacheDir = File(cacheDir, "temp")
    private val thumbnailCacheDir = File(cacheDir, "thumbnails")
    private val previewCacheDir = File(cacheDir, "previews")
    
    private var maxCacheSize = DEFAULT_MAX_SIZE * 1024 * 1024  // 字节

    init {
        createDirectories()
    }

    /**
     * 获取缓存信息
     */
    fun getCacheInfo(): CacheInfo {
        val imageSize = calculateDirSize(imageCacheDir)
        val modelSize = calculateDirSize(modelCacheDir)
        val tempSize = calculateDirSize(tempCacheDir)
        val thumbnailSize = calculateDirSize(thumbnailCacheDir)
        val previewSize = calculateDirSize(previewCacheDir)
        
        val totalSize = imageSize + modelSize + tempSize + thumbnailSize + previewSize
        
        return CacheInfo(
            totalSize = totalSize,
            maxSize = maxCacheSize,
            imageSize = imageSize,
            modelSize = modelSize,
            tempSize = tempSize,
            thumbnailSize = thumbnailSize,
            previewSize = previewSize,
            imageCount = imageCacheDir.listFiles()?.size ?: 0,
            modelCount = modelCacheDir.listFiles()?.size ?: 0,
            tempCount = tempCacheDir.listFiles()?.size ?: 0,
            thumbnailCount = thumbnailCacheDir.listFiles()?.size ?: 0,
            previewCount = previewCacheDir.listFiles()?.size ?: 0
        )
    }

    /**
     * 获取缓存使用百分比
     */
    fun getUsagePercentage(): Float {
        val info = getCacheInfo()
        return (info.totalSize.toFloat() / info.maxSize * 100).coerceIn(0f, 100f)
    }

    /**
     * 保存到缓存
     */
    suspend fun saveToCache(
        type: String,
        key: String,
        data: ByteArray,
        ttl: Long = 0  // 0 = 永不过期
    ): File? = withContext(Dispatchers.IO) {
        try {
            val dir = getCacheDirectory(type)
            val file = File(dir, key)
            
            file.writeBytes(data)
            
            // 设置过期时间
            if (ttl > 0) {
                val metadataFile = File(dir, "$key.meta")
                val expiryTime = System.currentTimeMillis() + ttl
                metadataFile.writeText(expiryTime.toString())
            }
            
            Log.i(TAG, "Saved to cache: $type/$key (${data.size} bytes)")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Cache save error: ${e.message}")
            null
        }
    }

    /**
     * 从缓存读取
     */
    suspend fun readFromCache(type: String, key: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val dir = getCacheDirectory(type)
            val file = File(dir, key)
            
            if (!file.exists()) return@withContext null
            
            // 检查过期
            val metadataFile = File(dir, "$key.meta")
            if (metadataFile.exists()) {
                val expiryTime = metadataFile.readText().toLongOrNull() ?: 0
                if (expiryTime > 0 && System.currentTimeMillis() > expiryTime) {
                    file.delete()
                    metadataFile.delete()
                    return@withContext null
                }
            }
            
            file.readBytes()
        } catch (e: Exception) {
            Log.e(TAG, "Cache read error: ${e.message}")
            null
        }
    }

    /**
     * 删除缓存项
     */
    fun deleteFromCache(type: String, key: String): Boolean {
        val dir = getCacheDirectory(type)
        val file = File(dir, key)
        val metaFile = File(dir, "$key.meta")
        
        val deleted = file.delete()
        metaFile.delete()
        
        return deleted
    }

    /**
     * 清空指定类型缓存
     */
    fun clearCache(type: String): Flow<ClearProgress> = flow {
        emit(ClearProgress.Status("开始清理..."))
        
        val dir = getCacheDirectory(type)
        val files = dir.listFiles() ?: return@flow
        
        var cleared = 0
        var total = files.size
        
        files.forEach { file ->
            if (file.isFile && !file.name.endsWith(".meta")) {
                file.delete()
                cleared++
                emit(ClearProgress.Progress((cleared * 100) / total, "已清理 $cleared/$total"))
            }
        }
        
        emit(ClearProgress.Completed(cleared))
    }

    /**
     * 清空所有缓存
     */
    fun clearAllCache(): Flow<ClearProgress> = flow {
        emit(ClearProgress.Status("清空所有缓存..."))
        
        var totalCleared = 0
        
        listOf(TYPE_IMAGE, TYPE_MODEL, TYPE_TEMP, TYPE_THUMBNAIL, TYPE_PREVIEW).forEach { type ->
            val dir = getCacheDirectory(type)
            val files = dir.listFiles() ?: return@forEach
            
            files.forEach { file ->
                if (file.isFile && !file.name.endsWith(".meta")) {
                    file.delete()
                    totalCleared++
                }
            }
        }
        
        emit(ClearProgress.Completed(totalCleared))
    }

    /**
     * 自动清理（当缓存超过阈值时）
     */
    suspend fun autoCleanup(threshold: Float = 0.9f) = withContext(Dispatchers.IO) {
        val info = getCacheInfo()
        if (info.totalSize > info.maxSize * threshold) {
            // 清理最老的临时文件
            cleanupOldest(tempCacheDir, info.tempSize / 2)
            cleanupOldest(thumbnailCacheDir, info.thumbnailSize / 2)
            
            Log.i(TAG, "Auto cleanup completed")
        }
    }

    /**
     * 设置最大缓存大小
     */
    fun setMaxCacheSize(sizeMB: Long) {
        maxCacheSize = sizeMB * 1024 * 1024
    }

    /**
     * 获取缓存目录
     */
    fun getCacheDirectory(type: String): File {
        return when (type) {
            TYPE_IMAGE -> imageCacheDir
            TYPE_MODEL -> modelCacheDir
            TYPE_TEMP -> tempCacheDir
            TYPE_THUMBNAIL -> thumbnailCacheDir
            TYPE_PREVIEW -> previewCacheDir
            else -> cacheDir
        }
    }

    /**
     * 列出缓存文件
     */
    fun listCacheFiles(type: String): List<CacheFile> {
        val dir = getCacheDirectory(type)
        return dir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".meta") }
            ?.map { file ->
                val metaFile = File(dir, "${file.name}.meta")
                val expiryTime = metaFile.readText().toLongOrNull() ?: 0
                
                CacheFile(
                    name = file.name,
                    path = file.absolutePath,
                    size = file.length(),
                    lastModified = file.lastModified(),
                    expiresAt = if (expiryTime > 0) Date(expiryTime) else null
                )
            }
            ?.sortedByDescending { it.lastModified }
            ?: emptyList()
    }

    // ==================== 内部方法 ====================

    private fun createDirectories() {
        listOf(cacheDir, imageCacheDir, modelCacheDir, tempCacheDir, 
               thumbnailCacheDir, previewCacheDir).forEach { dir ->
            if (!dir.exists()) dir.mkdirs()
        }
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0
        return dir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0
    }

    private fun cleanupOldest(dir: File, targetSize: Long) {
        val files = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
        
        var currentSize = files.sumOf { it.length() }
        
        for (file in files) {
            if (currentSize <= targetSize) break
            currentSize -= file.length()
            file.delete()
        }
    }
}

/**
 * 缓存信息
 */
data class CacheInfo(
    val totalSize: Long,
    val maxSize: Long,
    val imageSize: Long,
    val modelSize: Long,
    val tempSize: Long,
    val thumbnailSize: Long,
    val previewSize: Long,
    val imageCount: Int,
    val modelCount: Int,
    val tempCount: Int,
    val thumbnailCount: Int,
    val previewCount: Int
) {
    fun getTotalSizeFormatted(): String = formatSize(totalSize)
    fun getMaxSizeFormatted(): String = formatSize(maxSize)
    
    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
}

/**
 * 缓存文件信息
 */
data class CacheFile(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val expiresAt: Date?
)

/**
 * 清理进度
 */
sealed class ClearProgress {
    data class Status(val message: String) : ClearProgress()
    data class Progress(val percent: Int, val message: String) : ClearProgress()
    data class Completed(val filesCleared: Int) : ClearProgress()
}
