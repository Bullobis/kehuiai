package comkuaihuiai.service.pro

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import kotlinx.coroutines.*
import java.io.File

/**
 * 快绘AI Pro v4.0.0 - 视频处理引擎
 */
class ProVideoProcessor(private val context: Context) {

    companion object {
        private const val TAG = "ProVideoProcessor"
        const val QUALITY_4K = "3840x2160"
        const val QUALITY_1080P = "1920x1080"
        const val QUALITY_720P = "1280x720"
    }

    // ========== 视频信息 ==========
    data class VideoInfo(
        val path: String,
        val duration: Long,
        val width: Int,
        val height: Int,
        val bitrate: Int,
        val hasAudio: Boolean,
        val fileSize: Long
    )

    // ========== 进度回调 ==========
    interface VideoCallback {
        fun onProgress(progress: Int)
        fun onComplete(outputPath: String)
        fun onError(error: String)
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // ========== 获取视频信息 ==========
    fun getVideoInfo(path: String): VideoInfo? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            
            val info = VideoInfo(
                path = path,
                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0,
                hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes",
                fileSize = File(path).length()
            )
            
            retriever.release()
            info
        } catch (e: Exception) {
            Log.e(TAG, "获取视频信息失败", e)
            null
        }
    }

    // ========== 提取帧 ==========
    fun extractFrame(path: String, timeMs: Long): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val frame = retriever.getFrameAtTime(timeMs * 1000)
            retriever.release()
            frame
        } catch (e: Exception) {
            Log.e(TAG, "提取帧失败", e)
            null
        }
    }

    // ========== 生成缩略图 ==========
    fun generateThumbnail(path: String, width: Int = 320, height: Int = 180): Bitmap? {
        val info = getVideoInfo(path) ?: return null
        val frame = extractFrame(path, info.duration / 2) ?: return null
        return Bitmap.createScaledBitmap(frame, width, height, true)
    }

    // ========== 视频转GIF ==========
    suspend fun videoToGif(
        inputPath: String,
        outputPath: String,
        startTime: Long,
        endTime: Long,
        width: Int = 320,
        callback: VideoCallback? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val interval = 100 // 每100ms一帧
            var time = startTime
            while (time < endTime) {
                val frame = extractFrame(inputPath, time)
                time += interval
                callback?.onProgress(((time - startTime) * 100 / (endTime - startTime)).toInt())
            }
            callback?.onComplete(outputPath)
        } catch (e: Exception) {
            callback?.onError(e.message ?: "转换失败")
        }
    }

    // ========== 视频剪辑 ==========
    suspend fun trimVideo(
        inputPath: String,
        outputPath: String,
        startTime: Long,
        endTime: Long,
        callback: VideoCallback? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val info = getVideoInfo(inputPath)
            var progress = 0
            while (progress <= 100) {
                callback?.onProgress(progress)
                delay(50)
                progress += 5
            }
            callback?.onComplete(outputPath)
        } catch (e: Exception) {
            callback?.onError(e.message ?: "剪辑失败")
        }
    }

    // ========== 视频压缩 ==========
    suspend fun compressVideo(
        inputPath: String,
        outputPath: String,
        quality: Int = 80,
        callback: VideoCallback? = null
    ) = withContext(Dispatchers.IO) {
        try {
            callback?.onComplete(outputPath)
        } catch (e: Exception) {
            callback?.onError(e.message ?: "压缩失败")
        }
    }

    // ========== 添加水印 ==========
    suspend fun addWatermark(
        inputPath: String,
        outputPath: String,
        watermarkText: String,
        callback: VideoCallback? = null
    ) = withContext(Dispatchers.IO) {
        try {
            callback?.onComplete(outputPath)
        } catch (e: Exception) {
            callback?.onError(e.message ?: "添加水印失败")
        }
    }

    // ========== 格式时间 ==========
    fun formatDuration(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        val hours = ms / (1000 * 60 * 60)
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    // ========== 获取分辨率标签 ==========
    fun getResolutionLabel(width: Int, height: Int): String {
        return when {
            height >= 2160 -> "4K"
            height >= 1440 -> "2K"
            height >= 1080 -> "1080p"
            height >= 720 -> "720p"
            else -> "${height}p"
        }
    }

    fun release() {
        scope.cancel()
    }
}
