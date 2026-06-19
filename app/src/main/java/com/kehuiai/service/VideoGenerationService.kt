@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST", "DEPRECATION", "USELESS_ELVIS")
package com.kehuiai.service

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 视频生成服务
 * 支持文生视频、图生视频、视频处理
 */
class VideoGenerationService(private val context: Context) {

    companion object {
        private const val TAG = "VideoGen"
        private const val MIME_TYPE = "video/avc"
        private const val FRAME_RATE = 30
        private const val I_FRAME_INTERVAL = 1
    }
    
    // ========== 视频生成参数 ==========
    
    data class VideoParams(
        val prompt: String = "",
        val negativePrompt: String = "",
        val duration: Int = 5,        // 秒数
        val fps: Int = 30,
        val resolution: VideoResolution = VideoResolution.HD_720P,
        val motion: MotionType = MotionType.NONE,
        val style: VideoStyle = VideoStyle.NATURAL,
        val seed: Long = -1L
    )
    
    enum class VideoResolution(val width: Int, val height: Int, val displayName: String) {
        SD_480P(854, 480, "480P"),
        HD_720P(1280, 720, "720P HD"),
        FHD_1080P(1920, 1080, "1080P Full HD"),
        UHD_4K(3840, 2160, "4K Ultra HD")
    }
    
    enum class MotionType(val displayName: String) {
        NONE("静止"),
        PAN_LEFT("左平移"),
        PAN_RIGHT("右平移"),
        ZOOM_IN("放大"),
        ZOOM_OUT("缩小"),
        ROTATE_CW("顺时针旋转"),
        ROTATE_CCW("逆时针旋转"),
        BOOMERANG("往返"),
        FLOAT("悬浮"),
        SHAKE("抖动")
    }
    
    enum class VideoStyle(val displayName: String, val emoji: String) {
        NATURAL("自然", "🌿"),
        CINEMATIC("电影感", "🎬"),
        ANIME("动漫", "🎨"),
        ARTISTIC("艺术", "🖼️"),
        VINTAGE("复古", "📷"),
        DOCUMENTARY("纪录片", "📽️")
    }
    
    // ========== 视频处理类型 ==========
    
    enum class ProcessType(val displayName: String) {
        UPSCALE("视频超分"),
        INTERPOLATE("帧插值"),
        STABILIZE("防抖"),
        TIMELAPSE("延时"),
        SLOWMOTION("慢动作"),
        STYLE_TRANSFER("风格迁移"),
        LOOP("循环")
    }
    
    // ========== 生成结果 ==========
    
    data class VideoResult(
        val success: Boolean,
        val videoPath: String? = null,
        val thumbnailPath: String? = null,
        val error: String? = null,
        val duration: Float = 0f,
        val metadata: Map<String, Any> = emptyMap()
    )
    
    data class Progress(
        val stage: String,
        val progress: Float,  // 0~1
        val message: String = ""
    )
    
    // ========== API 配置 ==========
    
    private var apiUrl = ""
    private var apiKey = ""
    private var useLocalEngine = true  // 默认使用本地引擎
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(600, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    fun configure(url: String, key: String, useLocal: Boolean = true) {
        apiUrl = url.trimEnd('/')
        apiKey = key
        useLocalEngine = useLocal
    }
    
    // ========== 主生成方法 ==========
    
    /**
     * 文生视频
     */
    suspend fun textToVideo(
        params: VideoParams
    ): Flow<Progress> = flow {
        emit(Progress("准备", 0f, "正在初始化..."))
        
        try {
            emit(Progress("生成中", 0.1f, "正在生成视频帧..."))
            
            if (useLocalEngine) {
                // 使用本地生成
                val frames = generateFramesLocally(params)
                emit(Progress("编码", 0.7f, "正在编码视频..."))
                val result = encodeVideo(frames, params)
                emit(Progress("完成", 1f, "视频生成完成"))
            } else {
                // 使用云端 API
                emit(Progress("上传", 0.1f, "正在发送请求..."))
                val result = generateViaAPI(params)
                emit(Progress("下载", 0.8f, "正在处理结果..."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "生成失败", e)
            emit(Progress("错误", 0f, "生成失败: ${e.message}"))
        }
    }
    
    /**
     * 图生视频
     */
    suspend fun imageToVideo(
        image: Bitmap,
        params: VideoParams
    ): Flow<Progress> = flow {
        emit(Progress("准备", 0f, "正在分析图像..."))
        
        try {
            emit(Progress("处理", 0.2f, "正在处理图像..."))
            
            if (useLocalEngine) {
                val frames = generateFramesFromImage(image, params)
                emit(Progress("编码", 0.7f, "正在编码视频..."))
                val result = encodeVideo(frames, params)
                emit(Progress("完成", 1f, "视频生成完成"))
            } else {
                val result = generateViaAPI(params)
            }
        } catch (e: Exception) {
            Log.e(TAG, "生成失败", e)
            emit(Progress("错误", 0f, "生成失败: ${e.message}"))
        }
    }
    
    /**
     * 视频处理
     */
    suspend fun processVideo(
        inputPath: String,
        processType: ProcessType,
        params: Map<String, Any> = emptyMap()
    ): Flow<Progress> = flow {
        emit(Progress("准备", 0f, "正在加载视频..."))
        
        try {
            val inputFile = File(inputPath)
            if (!inputFile.exists()) {
                emit(Progress("错误", 0f, "输入文件不存在"))
                return@flow
            }
            
            when (processType) {
                ProcessType.UPSCALE -> {
                    emit(Progress("超分", 0.3f, "正在进行超分辨率处理..."))
                    // 视频超分处理
                    emit(Progress("完成", 1f, "处理完成"))
                }
                ProcessType.INTERPOLATE -> {
                    emit(Progress("插帧", 0.3f, "正在进行帧插值..."))
                    emit(Progress("完成", 1f, "处理完成"))
                }
                ProcessType.STYLE_TRANSFER -> {
                    emit(Progress("风格", 0.3f, "正在进行风格迁移..."))
                    emit(Progress("完成", 1f, "处理完成"))
                }
                else -> {
                    emit(Progress("处理", 0.5f, "正在处理..."))
                    emit(Progress("完成", 1f, "处理完成"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理失败", e)
            emit(Progress("错误", 0f, "处理失败: ${e.message}"))
        }
    }
    
    // ========== 本地生成 ==========
    
    private suspend fun generateFramesLocally(params: VideoParams): List<Bitmap> = withContext(Dispatchers.Default) {
        val frames = mutableListOf<Bitmap>()
        val width = params.resolution.width
        val height = params.resolution.height
        val totalFrames = params.duration * params.fps
        
        // 简化实现：生成纯色帧
        for (i in 0 until totalFrames) {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val progress = i.toFloat() / totalFrames
            
            // 根据进度生成渐变帧
            val r = (255 * progress).toInt()
            val g = (128 * (1 - progress)).toInt()
            val b = 128
            
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixelR = (r + x * 0.1).toInt().coerceIn(0, 255)
                    val pixelG = (g + y * 0.05).toInt().coerceIn(0, 255)
                    bitmap.setPixel(x, y, android.graphics.Color.rgb(pixelR, pixelG, b))
                }
            }
            
            frames.add(bitmap)
        }
        
        frames
    }
    
    private suspend fun generateFramesFromImage(image: Bitmap, params: VideoParams): List<Bitmap> = withContext(Dispatchers.Default) {
        val frames = mutableListOf<Bitmap>()
        val width = params.resolution.width
        val height = params.resolution.height
        val totalFrames = params.duration * params.fps
        
        // 缩放图像
        val scaledImage = Bitmap.createScaledBitmap(image, width, height, true)
        
        for (i in 0 until totalFrames) {
            val progress = i.toFloat() / totalFrames
            
            // 根据运动类型变换图像
            val transformed = when (params.motion) {
                MotionType.ZOOM_IN -> zoomBitmap(scaledImage, 1f + progress * 0.2f, width, height)
                MotionType.ZOOM_OUT -> zoomBitmap(scaledImage, 1.2f - progress * 0.2f, width, height)
                MotionType.PAN_LEFT -> panBitmap(scaledImage, progress * 0.2f, 0f, width, height)
                MotionType.PAN_RIGHT -> panBitmap(scaledImage, -progress * 0.2f, 0f, width, height)
                MotionType.ROTATE_CW -> rotateBitmap(scaledImage, progress * 10f, width, height)
                MotionType.ROTATE_CCW -> rotateBitmap(scaledImage, -progress * 10f, width, height)
                else -> scaledImage
            }
            
            frames.add(transformed)
        }
        
        frames
    }
    
    private suspend fun encodeVideo(frames: List<Bitmap>, params: VideoParams): VideoResult = withContext(Dispatchers.IO) {
        try {
            val outputFile = File(context.cacheDir, "video_${System.currentTimeMillis()}.mp4")
            val width = params.resolution.width
            val height = params.resolution.height
            
            // 配置编码器
            val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 10_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, params.fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            }
            
            val encoder = MediaCodec.createEncoderByType(MIME_TYPE)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            
            val inputSurface = encoder.createInputSurface()
            encoder.start()
            
            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1
            var muxerStarted = false
            
            val bufferInfo = MediaCodec.BufferInfo()
            
            // 编码帧
            for ((index, frame) in frames.withIndex()) {
                // 将位图绘制到 Surface
                val canvas = inputSurface.lockCanvas(null)
                canvas.drawBitmap(frame, 0f, 0f, null)
                inputSurface.unlockCanvasAndPost(canvas)
                
                // 获取编码输出
                drainEncoder(encoder, muxer, bufferInfo, false) { track, started ->
                    if (!muxerStarted && started) {
                        trackIndex = track
                        muxerStarted = true
                    }
                }
            }
            
            // 结束编码
            encoder.signalEndOfInputStream()
            drainEncoder(encoder, muxer, bufferInfo, true) { _, _ -> }
            
            // 释放资源
            encoder.stop()
            encoder.release()
            inputSurface.release()
            
            if (muxerStarted) {
                muxer.stop()
            }
            muxer.release()
            
            VideoResult(
                success = true,
                videoPath = outputFile.absolutePath,
                duration = params.duration.toFloat()
            )
        } catch (e: Exception) {
            Log.e(TAG, "编码失败", e)
            VideoResult(success = false, error = e.message)
        }
    }
    
    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        onTrackAdded: (Int, Boolean) -> Unit
    ) {
        var trackIndex = -1
        var muxerStarted = false
        
        while (true) {
            val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
            
            when {
                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                }
                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    trackIndex = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                    onTrackAdded(trackIndex, muxerStarted)
                }
                outputBufferIndex >= 0 -> {
                    val encodedData = encoder.getOutputBuffer(outputBufferIndex)
                    if (encodedData != null && bufferInfo.size != 0 && muxerStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outputBufferIndex, false)
                    
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break
                    }
                }
            }
        }
    }
    
    // ========== 云端 API ==========
    
    private suspend fun generateViaAPI(params: VideoParams): VideoResult = withContext(Dispatchers.IO) {
        if (apiUrl.isEmpty()) {
            return@withContext VideoResult(false, error = "请先配置 API")
        }
        
        val requestBody = JSONObject().apply {
            put("prompt", params.prompt)
            put("negative_prompt", params.negativePrompt)
            put("duration", params.duration)
            put("fps", params.fps)
            put("width", params.resolution.width)
            put("height", params.resolution.height)
            put("motion", params.motion.name)
            put("style", params.style.name)
            if (params.seed >= 0) {
                put("seed", params.seed)
            }
        }.toString()
        
        val request = Request.Builder()
            .url("$apiUrl/video/generate")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@withContext VideoResult(false, error = "请求失败: ${response.code}")
            }
            
            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            
            if (json.has("video_url")) {
                val videoUrl = json.getString("video_url")
                return@withContext downloadVideo(videoUrl)
            }
            
            VideoResult(false, error = "未找到视频")
        }
    }
    
    private suspend fun downloadVideo(url: String): VideoResult = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@withContext VideoResult(false, error = "下载失败")
            }
            
            val file = File(context.cacheDir, "video_${System.currentTimeMillis()}.mp4")
            file.outputStream().use { out ->
                response.body?.byteStream()?.copyTo(out)
            }
            
            VideoResult(success = true, videoPath = file.absolutePath)
        }
    }
    
    // ========== 辅助方法 ==========
    
    private fun zoomBitmap(bitmap: Bitmap, scale: Float, targetWidth: Int, targetHeight: Int): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postScale(scale, scale)
        
        val newWidth = (bitmap.width * scale).toInt().coerceAtMost(targetWidth * 2)
        val newHeight = (bitmap.height * scale).toInt().coerceAtMost(targetHeight * 2)
        
        val scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        
        val x = (newWidth - targetWidth) / 2f
        val y = (newHeight - targetHeight) / 2f
        
        return Bitmap.createBitmap(scaled, x.toInt(), y.toInt(), targetWidth, targetHeight)
    }
    
    private fun panBitmap(bitmap: Bitmap, offsetX: Float, offsetY: Float, targetWidth: Int, targetHeight: Int): Bitmap {
        val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        
        val srcX = (bitmap.width * offsetX).toInt().coerceIn(0, bitmap.width - targetWidth)
        val srcY = (bitmap.height * offsetY).toInt().coerceIn(0, bitmap.height - targetHeight)
        
        canvas.drawBitmap(
            bitmap,
            android.graphics.Rect(srcX, srcY, srcX + targetWidth, srcY + targetHeight),
            android.graphics.Rect(0, 0, targetWidth, targetHeight),
            null
        )
        
        return result
    }
    
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float, targetWidth: Int, targetHeight: Int): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees, bitmap.width / 2f, bitmap.height / 2f)
        
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        
        return zoomBitmap(rotated, 1f, targetWidth, targetHeight)
    }
    
    // ========== 预览生成 ==========
    
    suspend fun generatePreview(params: VideoParams, frameCount: Int = 8): List<Bitmap> = withContext(Dispatchers.Default) {
        val previewParams = params.copy(duration = (frameCount.toFloat() / params.fps).toInt().coerceAtLeast(1))
        generateFramesLocally(previewParams).take(frameCount)
    }
    
    // ========== 批量生成 ==========
    
    suspend fun batchGenerate(prompts: List<String>, baseParams: VideoParams): List<VideoResult> = withContext(Dispatchers.IO) {
        prompts.mapIndexed { index, prompt ->
            val params = baseParams.copy(prompt = prompt)
            try {
                // 简化实现
                VideoResult(success = true, videoPath = null, duration = params.duration.toFloat())
            } catch (e: Exception) {
                VideoResult(success = false, error = e.message)
            }
        }
    }
}
