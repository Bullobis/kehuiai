package com.kehuiai.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * 高性能图片缓存管理器
 * 使用 LRU 内存缓存 + 磁盘缓存
 */
class ImageCacheManager(private val context: Context) {
    
    companion object {
        // 内存缓存大小 - 使用可用内存的 1/8
        private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        private val cacheSize = maxMemory / 8
        
        private const val DISK_CACHE_DIR = "image_cache"
        private const val DISK_CACHE_SIZE = 100L * 1024 * 1024 // 100MB
    }
    
    // 内存缓存
    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
        
        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            // 可选：对于强引用的 Bitmap，可以在这里进行软引用转换
        }
    }
    
    // 磁盘缓存目录
    private val diskCacheDir = File(context.cacheDir, DISK_CACHE_DIR).apply {
        if (!exists()) mkdirs()
    }
    
    /**
     * 获取缓存图片
     */
    fun getBitmap(key: String): Bitmap? {
        // 先从内存缓存获取
        val memBitmap = memoryCache.get(key)
        if (memBitmap != null) {
            return memBitmap
        }
        
        // 再从磁盘缓存获取
        val diskFile = getDiskCacheFile(key)
        if (diskFile.exists()) {
            val bitmap = loadBitmapFromDisk(diskFile)
            if (bitmap != null) {
                // 放入内存缓存
                memoryCache.put(key, bitmap)
                return bitmap
            }
        }
        
        return null
    }
    
    /**
     * 缓存图片
     */
    fun putBitmap(key: String, bitmap: Bitmap) {
        // 放入内存缓存
        memoryCache.put(key, bitmap)
        
        // 异步写入磁盘缓存
        asyncPutToDisk(key, bitmap)
    }
    
    private fun asyncPutToDisk(key: String, bitmap: Bitmap) {
        Thread {
            try {
                val file = getDiskCacheFile(key)
                // 检查磁盘缓存总大小，必要时清理
                cleanDiskCacheIfNeeded()
                // 保存到磁盘
                saveBitmapToDisk(bitmap, file)
            } catch (e: Exception) {
                // 忽略磁盘缓存错误
            }
        }.start()
    }
    
    private fun getDiskCacheFile(key: String): File {
        val hashKey = md5(key)
        return File(diskCacheDir, "$hashKey.jpg")
    }
    
    private fun loadBitmapFromDisk(file: File): Bitmap? {
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun saveBitmapToDisk(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
    }
    
    /**
     * 清理磁盘缓存
     */
    private fun cleanDiskCacheIfNeeded() {
        val totalSize = diskCacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        if (totalSize > DISK_CACHE_SIZE) {
            // 删除最旧的文件
            val files = diskCacheDir.listFiles()?.sortedBy { it.lastModified() } ?: return
            var currentSize = totalSize
            for (file in files) {
                if (currentSize <= DISK_CACHE_SIZE * 0.8) break
                val size = file.length()
                if (file.delete()) {
                    currentSize -= size
                }
            }
        }
    }
    
    /**
     * 清理所有缓存
     */
    fun clearCache() {
        memoryCache.evictAll()
        diskCacheDir.listFiles()?.forEach { it.delete() }
    }
    
    /**
     * 预加载图片到缓存
     */
    suspend fun preloadImage(path: String): Bitmap? = withContext(Dispatchers.IO) {
        val key = md5(path)
        if (getBitmap(key) != null) {
            return@withContext getBitmap(key)
        }
        
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, options)
            
            // 计算采样率
            val sampleSize = calculateInSampleSize(options, 512, 512)
            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize
            
            val bitmap = BitmapFactory.decodeFile(path, options)
            if (bitmap != null) {
                putBitmap(key, bitmap)
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }
    
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
    
    private fun md5(string: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(string.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}

/**
 * 性能监控工具
 */
object PerformanceMonitor {
    private var startTime: Long = 0
    private val measurements = mutableMapOf<String, MutableList<Long>>()
    
    fun start(name: String) {
        startTime = System.nanoTime()
    }
    
    fun end(name: String) {
        val duration = System.nanoTime() - startTime
        measurements.getOrPut(name) { mutableListOf() }.add(duration)
    }
    
    fun getAverage(name: String): Long {
        return measurements[name]?.average()?.toLong() ?: 0
    }
    
    fun getStats(name: String): PerformanceStats? {
        val times = measurements[name] ?: return null
        if (times.isEmpty()) return null
        
        return PerformanceStats(
            count = times.size,
            average = times.average(),
            min = times.minOrNull() ?: 0,
            max = times.maxOrNull() ?: 0,
            total = times.sum()
        )
    }
    
    fun clear() {
        measurements.clear()
    }
    
    data class PerformanceStats(
        val count: Int,
        val average: Double,
        val min: Long,
        val max: Long,
        val total: Long
    ) {
        fun formatAverage(): String = "${average / 1_000_000}ms"
    }
}

/**
 * 批量操作优化
 */
object BatchProcessor {
    
    /**
     * 分批处理任务，避免阻塞主线程
     */
    suspend fun <T, R> processBatch(
        items: List<T>,
        batchSize: Int = 10,
        processItem: suspend (T) -> R
    ): List<R> = withContext(Dispatchers.Default) {
        items.chunked(batchSize).flatMap { batch ->
            batch.map { item ->
                processItem(item)
            }
        }
    }
    
    /**
     * 并行处理，带有并发限制
     */
    suspend fun <T, R> processParallel(
        items: List<T>,
        concurrency: Int = 4,
        processItem: suspend (T) -> R
    ): List<R> = withContext(Dispatchers.Default) {
        items.parMap(concurrency) { item ->
            processItem(item)
        }
    }
    
    private suspend fun <T, R> List<T>.parMap(concurrency: Int, transform: suspend (T) -> R): List<R> {
        return withContext(Dispatchers.Default) {
            this@parMap.map { item -> async { transform(item) } }.awaitAll()
        }
    }
}

/**
 * 对象池 - 复用常用对象减少 GC
 */
class ObjectPool<T>(private val factory: () -> T, private val reset: (T) -> Unit) {
    private val pool = java.util.concurrent.ConcurrentLinkedQueue<T>()
    
    fun acquire(): T {
        return pool.poll() ?: factory()
    }
    
    fun release(obj: T) {
        reset(obj)
        pool.offer(obj)
    }
    
    fun clear() {
        while (pool.poll() != null) { }
    }
}

/**
 * 字符串资源优化
 */
object StringPool {
    private val cache = LruCache<String, String>(100)
    
    fun get(key: String): String {
        return cache.get(key) ?: key.also { cache.put(key, it) }
    }
    
    fun clear() {
        cache.evictAll()
    }
}
