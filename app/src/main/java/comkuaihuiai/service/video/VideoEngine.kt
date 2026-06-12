package comkuaihuiai.service.video

import android.content.Context
import android.graphics.*
import android.media.*
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.*

/**
 * 快绘AI v3.1.0 视频生成引擎
 */
class VideoEngine(private val context: Context) {

    companion object {
        private const val TAG = "VideoEngine"
        
        const val DEFAULT_FRAME_RATE = 30
        const val MAX_FRAME_RATE = 60
        const val MIN_FRAME_RATE = 15
        
        const val DEFAULT_BIT_RATE = 8000000
        const val HIGH_BIT_RATE = 16000000
        const val LOW_BIT_RATE = 4000000
        
        const val I_FRAME_INTERVAL = 1
        
        const val RESOLUTION_480P = 0
        const val RESOLUTION_720P = 1
        const val RESOLUTION_1080P = 2
        const val RESOLUTION_2K = 3
        const val RESOLUTION_4K = 4
        
        val RESOLUTION_MAP = mapOf(
            RESOLUTION_480P to Pair(854, 480),
            RESOLUTION_720P to Pair(1280, 720),
            RESOLUTION_1080P to Pair(1920, 1080),
            RESOLUTION_2K to Pair(2560, 1440),
            RESOLUTION_4K to Pair(3840, 2160)
        )
        
        const val STYLE_NORMAL = 0
        const val STYLE_ANIME = 1
        const val STYLE_PIXEL = 2
        const val STYLE_GLITCH = 4
        const val STYLE_CYBERPUNK = 5
        const val STYLE_VINTAGE = 6
        const val STYLE_DREAM = 7
        
        const val MOTION_ZOOM_IN = "zoom_in"
        const val MOTION_ZOOM_OUT = "zoom_out"
        const val MOTION_PAN_LEFT = "pan_left"
        const val MOTION_PAN_RIGHT = "pan_right"
        const val MOTION_SHAKE = "shake"
        const val MOTION_FLOAT = "float"
    }

    private val isGenerating = AtomicBoolean(false)
    private val isCancelled = AtomicBoolean(false)
    private val currentProgress = AtomicInteger(0)
    
    private var currentResolution = RESOLUTION_720P
    private var currentStyle = STYLE_NORMAL
    private var currentFrameRate = DEFAULT_FRAME_RATE
    private var currentDuration = 5
    private var currentMotion = MOTION_FLOAT
    
    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val videoDir = File(context.filesDir, "videos")
    private val framesDir = File(context.filesDir, "frames")
    private val tempDir = File(context.cacheDir, "video_temp")
    
    init {
        listOf(videoDir, framesDir, tempDir).forEach { if (!it.exists()) it.mkdirs() }
    }

    fun setResolution(resolution: Int) {
        currentResolution = resolution.coerceIn(RESOLUTION_480P, RESOLUTION_4K)
    }

    fun getResolution(): Pair<Int, Int> {
        return RESOLUTION_MAP[currentResolution] ?: Pair(1280, 720)
    }

    fun setStyle(style: Int) {
        currentStyle = style.coerceIn(STYLE_NORMAL, STYLE_DREAM)
    }

    fun setFrameRate(fps: Int) {
        currentFrameRate = fps.coerceIn(MIN_FRAME_RATE, MAX_FRAME_RATE)
    }

    fun setDuration(seconds: Int) {
        currentDuration = seconds.coerceIn(1, 120)
    }

    fun setMotion(motion: String) {
        currentMotion = motion
    }

    fun generateText2Video(
        prompt: String,
        negativePrompt: String = "",
        duration: Int = currentDuration,
        fps: Int = currentFrameRate,
        resolution: Int = currentResolution,
        style: Int = currentStyle,
        motion: String = currentMotion
    ): Flow<VideoProgress> = flow {
        isGenerating.set(true)
        isCancelled.set(false)
        
        try {
            val (width, height) = RESOLUTION_MAP[resolution] ?: Pair(1280, 720)
            val totalFrames = duration * fps
            
            Log.i(TAG, "🎬 准备生成视频: ${width}x$height, ${duration}秒, ${fps}fps")
            
            emit(VideoProgress.Status("🎬 初始化视频生成..."))
            emit(VideoProgress.Resolution(width, height))
            emit(VideoProgress.FrameInfo(0, totalFrames))
            
            clearTempDir()
            
            emit(VideoProgress.Status("🖼️ 生成视频帧..."))
            
            val frames = mutableListOf<Bitmap>()
            val startTime = System.currentTimeMillis()
            
            for (frameIndex in 0 until totalFrames) {
                if (isCancelled.get()) {
                    emit(VideoProgress.Error("生成已取消"))
                    return@flow
                }
                
                val progress = frameIndex.toFloat() / totalFrames
                currentProgress.set((progress * 100).toInt())
                
                emit(VideoProgress.Progress(frameIndex, totalFrames, progress))
                
                val frameProgress = frameIndex.toFloat() / totalFrames
                val motionParams = calculateMotionParams(motion, frameProgress, width, height)
                
                val frame = generateVideoFrame(
                    prompt = prompt,
                    width = width,
                    height = height,
                    frameIndex = frameIndex,
                    totalFrames = totalFrames,
                    frameProgress = frameProgress,
                    motionParams = motionParams,
                    style = style
                )
                
                frames.add(frame)
                
                if (frameIndex % 30 == 0) {
                    saveFrameToDisk(frame, frameIndex)
                }
                
                delay(20)
            }
            
            emit(VideoProgress.Status("🎞️ 编码视频..."))
            
            val outputPath = File(videoDir, "text2video_${System.currentTimeMillis()}.mp4").absolutePath
            encodeVideo(frames, outputPath, fps, width, height)
            
            frames.forEach { it.recycle() }
            clearTempDir()
            
            val elapsed = System.currentTimeMillis() - startTime
            emit(VideoProgress.Completed(outputPath))
            
            Log.i(TAG, "✅ 视频生成完成: ${outputPath}, 耗时: ${elapsed}ms")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 视频生成失败: ${e.message}")
            emit(VideoProgress.Error(e.message ?: "未知错误"))
        } finally {
            isGenerating.set(false)
        }
        
    }.flowOn(Dispatchers.Default)

    fun generateImage2Video(
        sourceImage: Bitmap,
        prompt: String = "",
        duration: Int = currentDuration,
        fps: Int = currentFrameRate,
        resolution: Int = currentResolution,
        style: Int = currentStyle,
        motion: String = currentMotion,
        motionStrength: Float = 0.5f
    ): Flow<VideoProgress> = flow {
        isGenerating.set(true)
        isCancelled.set(false)
        
        try {
            val (width, height) = RESOLUTION_MAP[resolution] ?: Pair(1280, 720)
            val totalFrames = duration * fps
            
            emit(VideoProgress.Status("🎬 准备图生视频..."))
            
            val scaledSource = Bitmap.createScaledBitmap(sourceImage, width, height, true)
            val frames = mutableListOf<Bitmap>()
            
            for (frameIndex in 0 until totalFrames) {
                if (isCancelled.get()) {
                    emit(VideoProgress.Error("生成已取消"))
                    return@flow
                }
                
                val progress = frameIndex.toFloat() / totalFrames
                val frameProgress = frameIndex.toFloat() / totalFrames
                
                emit(VideoProgress.Progress(frameIndex, totalFrames, progress))
                
                val motionParams = calculateMotionParams(motion, frameProgress, width, height)
                
                val frame = applyMotionToFrame(
                    source = scaledSource,
                    frameIndex = frameIndex,
                    totalFrames = totalFrames,
                    motionParams = motionParams,
                    motionStrength = motionStrength,
                    style = style
                )
                
                frames.add(frame)
                delay(15)
            }
            
            emit(VideoProgress.Status("🎞️ 编码视频..."))
            
            val outputPath = File(videoDir, "img2video_${System.currentTimeMillis()}.mp4").absolutePath
            encodeVideo(frames, outputPath, fps, width, height)
            
            frames.forEach { it.recycle() }
            if (scaledSource !== sourceImage) scaledSource.recycle()
            
            emit(VideoProgress.Completed(outputPath))
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 图生视频失败: ${e.message}")
            emit(VideoProgress.Error(e.message ?: "未知错误"))
        } finally {
            isGenerating.set(false)
        }
        
    }.flowOn(Dispatchers.Default)

    fun stylizeVideo(
        sourcePath: String,
        style: Int = currentStyle,
        strength: Float = 0.7f
    ): Flow<VideoProgress> = flow {
        isGenerating.set(true)
        isCancelled.set(false)
        
        try {
            emit(VideoProgress.Status("🎨 视频风格化..."))
            
            val frames = extractFrames(sourcePath)
            if (frames.isEmpty()) {
                emit(VideoProgress.Error("无法提取视频帧"))
                return@flow
            }
            
            val totalFrames = frames.size
            val styledFrames = mutableListOf<Bitmap>()
            
            for ((index, frame) in frames.withIndex()) {
                if (isCancelled.get()) {
                    emit(VideoProgress.Error("已取消"))
                    return@flow
                }
                
                val progress = index.toFloat() / totalFrames
                emit(VideoProgress.Progress(index, totalFrames, progress))
                
                val styledFrame = applyStyle(frame, style, strength)
                styledFrames.add(styledFrame)
                
                if (index % 10 == 0) frame.recycle()
                delay(10)
            }
            
            emit(VideoProgress.Status("🎞️ 重新编码..."))
            
            val outputPath = File(videoDir, "styled_${System.currentTimeMillis()}.mp4").absolutePath
            encodeVideo(styledFrames, outputPath, currentFrameRate, styledFrames[0].width, styledFrames[0].height)
            
            frames.forEach { it.recycle() }
            styledFrames.forEach { it.recycle() }
            
            emit(VideoProgress.Completed(outputPath))
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 风格化失败: ${e.message}")
            emit(VideoProgress.Error(e.message ?: "未知错误"))
        } finally {
            isGenerating.set(false)
        }
        
    }.flowOn(Dispatchers.Default)

    fun upscaleVideo(
        sourcePath: String,
        scale: Int = 2,
        denoiseStrength: Float = 0.3f
    ): Flow<VideoProgress> = flow {
        isGenerating.set(true)
        isCancelled.set(false)
        
        try {
            emit(VideoProgress.Status("🔍 视频超分辨率..."))
            
            val frames = extractFrames(sourcePath)
            if (frames.isEmpty()) {
                emit(VideoProgress.Error("无法提取视频帧"))
                return@flow
            }
            
            val totalFrames = frames.size
            val upscaledFrames = mutableListOf<Bitmap>()
            
            for ((index, frame) in frames.withIndex()) {
                if (isCancelled.get()) {
                    emit(VideoProgress.Error("已取消"))
                    return@flow
                }
                
                val progress = index.toFloat() / totalFrames
                emit(VideoProgress.Progress(index, totalFrames, progress))
                
                val newWidth = (frame.width * scale).coerceAtMost(3840)
                val newHeight = (frame.height * scale).coerceAtMost(2160)
                val upscaled = Bitmap.createScaledBitmap(frame, newWidth, newHeight, true)
                upscaledFrames.add(upscaled)
                
                if (index % 10 == 0) frame.recycle()
                delay(30)
            }
            
            emit(VideoProgress.Status("🎞️ 编码视频..."))
            
            val outputPath = File(videoDir, "upscaled_${System.currentTimeMillis()}.mp4").absolutePath
            val upscaledFrame = upscaledFrames[0]
            encodeVideo(upscaledFrames, outputPath, currentFrameRate, upscaledFrame.width, upscaledFrame.height)
            
            frames.forEach { it.recycle() }
            upscaledFrames.forEach { it.recycle() }
            
            emit(VideoProgress.Completed(outputPath))
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 超分失败: ${e.message}")
            emit(VideoProgress.Error(e.message ?: "未知错误"))
        } finally {
            isGenerating.set(false)
        }
        
    }.flowOn(Dispatchers.Default)

    private fun calculateMotionParams(motion: String, progress: Float, width: Int, height: Int): MotionParams {
        val amplitude = minOf(width, height) * 0.1f
        
        return when (motion) {
            MOTION_ZOOM_IN -> MotionParams(1f + progress * 0.2f, 1f + progress * 0.2f, 0f, 0f, 0f)
            MOTION_ZOOM_OUT -> MotionParams(1.2f - progress * 0.2f, 1.2f - progress * 0.2f, 0f, 0f, 0f)
            MOTION_PAN_LEFT -> MotionParams(1.1f, 1.1f, -amplitude * progress, 0f, 0f)
            MOTION_PAN_RIGHT -> MotionParams(1.1f, 1.1f, amplitude * progress, 0f, 0f)
            MOTION_SHAKE -> MotionParams(1f, 1f, sin(progress * 10 * PI).toFloat() * amplitude * 0.1f, cos(progress * 10 * PI).toFloat() * amplitude * 0.1f, 0f)
            MOTION_FLOAT -> MotionParams(1f, 1f, sin(progress * 2 * PI).toFloat() * amplitude * 0.2f, cos(progress * 4 * PI).toFloat() * amplitude * 0.1f, sin(progress * 2 * PI).toFloat() * 5f)
            else -> MotionParams(1f, 1f, 0f, 0f, 0f)
        }
    }

    data class MotionParams(val scaleX: Float, val scaleY: Float, val translateX: Float, val translateY: Float, val rotation: Float)

    private suspend fun generateVideoFrame(
        prompt: String,
        width: Int,
        height: Int,
        frameIndex: Int,
        totalFrames: Int,
        frameProgress: Float,
        motionParams: MotionParams,
        style: Int
    ): Bitmap = withContext(Dispatchers.Default) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        val colorScheme = generateColorScheme(prompt, frameIndex)
        canvas.drawColor(colorScheme.background)
        
        val paint = Paint().apply { isAntiAlias = true }
        
        val shapes = 20 + (frameIndex % 10)
        repeat(shapes) { i ->
            val t = (frameIndex.toFloat() / totalFrames + i.toFloat() / shapes) % 1f
            
            val x = width * (0.2f + 0.6f * sin(t * 2 * PI + i).toFloat())
            val y = height * (0.2f + 0.6f * cos(t * 3 * PI + i).toFloat())
            val radius = 20f + 40f * sin(t * PI).toFloat()
            
            paint.color = Color.argb(
                (150 + 50 * sin(t * PI).toFloat()).toInt(),
                Color.red(colorScheme.accent),
                Color.green(colorScheme.accent),
                Color.blue(colorScheme.accent)
            )
            
            canvas.drawCircle(x, y, radius, paint)
        }
        
        bitmap
    }

    private suspend fun applyMotionToFrame(
        source: Bitmap,
        frameIndex: Int,
        totalFrames: Int,
        motionParams: MotionParams,
        motionStrength: Float,
        style: Int
    ): Bitmap = withContext(Dispatchers.Default) {
        val width = source.width
        val height = source.height
        
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        
        canvas.save()
        canvas.translate(width / 2f + motionParams.translateX * motionStrength, height / 2f + motionParams.translateY * motionStrength)
        canvas.rotate(motionParams.rotation * motionStrength)
        canvas.scale(motionParams.scaleX, motionParams.scaleY)
        canvas.translate(-width / 2f, -height / 2f)
        canvas.drawBitmap(source, 0f, 0f, null)
        canvas.restore()
        
        result
    }

    private fun applyStyle(frame: Bitmap, style: Int, strength: Float): Bitmap {
        return when (style) {
            STYLE_ANIME -> applyAnimeStyle(frame, strength)
            STYLE_PIXEL -> applyPixelStyle(frame, strength)
            STYLE_GLITCH -> applyGlitchStyle(frame, strength)
            STYLE_CYBERPUNK -> applyCyberpunkStyle(frame, strength)
            STYLE_DREAM -> applyDreamStyle(frame, strength)
            STYLE_VINTAGE -> applyVintageStyle(frame, strength)
            else -> frame.copy(frame.config, false)
        }
    }

    private fun applyAnimeStyle(source: Bitmap, strength: Float): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val saturation = 1.3f + strength * 0.3f
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(saturation) })
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    private fun applyPixelStyle(source: Bitmap, strength: Float): Bitmap {
        val pixelSize = (4 + strength * 12).toInt()
        val result = Bitmap.createBitmap(
            source.width / pixelSize * pixelSize,
            source.height / pixelSize * pixelSize,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(result)
        val paint = Paint()
        
        for (x in 0 until result.width step pixelSize) {
            for (y in 0 until result.height step pixelSize) {
                val sx = (x / pixelSize * pixelSize).coerceIn(0, source.width - 1)
                val sy = (y / pixelSize * pixelSize).coerceIn(0, source.height - 1)
                paint.color = source.getPixel(sx, sy)
                canvas.drawRect(x.toFloat(), y.toFloat(), (x + pixelSize).toFloat(), (y + pixelSize).toFloat(), paint)
            }
        }
        return result
    }

    private fun applyGlitchStyle(source: Bitmap, strength: Float): Bitmap {
        val result = source.copy(source.config, true)
        val width = result.width
        val height = result.height
        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val glitchLines = (strength * 10).toInt()
        repeat(glitchLines) {
            val y = (Math.random() * height).toInt()
            val offset = ((Math.random() - 0.5) * strength * 50).toInt()
            for (x in 0 until width) {
                val srcX = (x + offset).coerceIn(0, width - 1)
                pixels[y * width + x] = pixels[y * width + srcX]
            }
        }
        
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun applyCyberpunkStyle(source: Bitmap, strength: Float): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.5f) })
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    private fun applyDreamStyle(source: Bitmap, strength: Float): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(source, 0f, 0f, null)
        val blurPaint = Paint().apply {
            maskFilter = BlurMaskFilter(30f * strength, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRect(0f, 0f, source.width.toFloat(), source.height.toFloat(), blurPaint)
        return result
    }

    private fun applyVintageStyle(source: Bitmap, strength: Float): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0.7f) })
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    private fun blendFrames(frame1: Bitmap, frame2: Bitmap, ratio: Float): Bitmap {
        val result = Bitmap.createBitmap(frame1.width, frame1.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(frame1, 0f, 0f, null)
        val paint = Paint().apply { alpha = ((1 - ratio) * 255).toInt().coerceIn(0, 255) }
        canvas.drawBitmap(frame2, 0f, 0f, paint)
        return result
    }

    private fun generateColorScheme(prompt: String, seed: Int): ColorSchemeV2 {
        val hue = (seed % 360).toFloat()
        return ColorSchemeV2(
            Color.HSVToColor(floatArrayOf(hue, 0.15f, 0.95f)),
            Color.HSVToColor(floatArrayOf(hue, 0.35f, 0.45f)),
            Color.HSVToColor(floatArrayOf((hue + 30) % 360, 0.6f, 0.85f))
        )
    }

    data class ColorSchemeV2(val background: Int, val foreground: Int, val accent: Int)

    private suspend fun encodeVideo(
        frames: List<Bitmap>,
        outputPath: String,
        fps: Int,
        width: Int,
        height: Int
    ) = withContext(Dispatchers.IO) {
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, DEFAULT_BIT_RATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            }
            
            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            
            val inputSurface = encoder.createInputSurface()
            encoder.start()
            
            val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var videoTrackIndex = -1
            var muxerStarted = false
            val bufferInfo = MediaCodec.BufferInfo()
            
            for ((index, frame) in frames.withIndex()) {
                val canvas = inputSurface.lockCanvas(null)
                canvas.drawBitmap(frame, 0f, 0f, null)
                inputSurface.unlockCanvasAndPost(canvas)
                
                var outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                while (outputBufferIndex >= 0) {
                    val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size > 0 && muxerStarted) {
                        outputBuffer?.position(bufferInfo.offset)
                        outputBuffer?.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(videoTrackIndex, outputBuffer!!, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outputBufferIndex, false)
                    outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
                }
                
                if (!muxerStarted) {
                    val newFormat = encoder.outputFormat
                    videoTrackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                }
            }
            
            val inputBufferIndex = encoder.dequeueInputBuffer(10000)
            if (inputBufferIndex >= 0) {
                encoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            
            var outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
            while (outputBufferIndex != MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (outputBufferIndex >= 0) {
                    val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break
                    }
                    if (bufferInfo.size > 0 && muxerStarted) {
                        outputBuffer?.position(bufferInfo.offset)
                        outputBuffer?.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(videoTrackIndex, outputBuffer!!, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outputBufferIndex, false)
                }
                outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
            }
            
            encoder.stop()
            encoder.release()
            inputSurface.release()
            muxer.stop()
            muxer.release()
            
            Log.i(TAG, "✅ 视频编码完成: $outputPath")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 视频编码失败: ${e.message}")
            throw e
        }
    }

    private suspend fun extractFrames(videoPath: String): List<Bitmap> = withContext(Dispatchers.IO) {
        val frames = mutableListOf<Bitmap>()
        // 简化实现，实际应用中需要使用 MediaCodec/MediaExtractor
        frames
    }

    private fun saveFrameToDisk(bitmap: Bitmap, index: Int) {
        val file = File(framesDir, "frame_${String.format("%06d", index)}.png")
        try {
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        } catch (e: Exception) {
            Log.w(TAG, "帧保存失败: ${e.message}")
        }
    }

    private fun clearTempDir() {
        tempDir.listFiles()?.forEach { it.delete() }
        framesDir.listFiles()?.forEach { it.delete() }
    }

    fun cancel() {
        isCancelled.set(true)
        Log.i(TAG, "🚫 视频生成已取消")
    }

    fun isGenerating(): Boolean = isGenerating.get()

    fun release() {
        cancel()
        engineScope.cancel()
        clearTempDir()
        Log.i(TAG, "♻️ VideoEngine 已释放")
    }
}

sealed class VideoProgress {
    data class Status(val message: String) : VideoProgress()
    data class Resolution(val width: Int, val height: Int) : VideoProgress()
    data class FrameInfo(val current: Int, val total: Int) : VideoProgress()
    data class Progress(val currentFrame: Int, val totalFrames: Int, val progress: Float) : VideoProgress()
    data class Completed(val outputPath: String) : VideoProgress()
    data class Error(val message: String) : VideoProgress()
}
