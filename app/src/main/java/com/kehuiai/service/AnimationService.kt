package com.kehuiai.service

import android.content.Context
import android.graphics.*
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.*

/**
 * 动画生成服务
 * 创建动态效果：GIF、WebP动画帧、过渡动画等
 */
class AnimationService(private val context: Context) {

    companion object {
        private const val TAG = "AnimationService"
    }

    // ========== 动画类型 ==========

    enum class AnimationType {
        KEN_BURNS,          // 缩放平移效果（幻灯片风格）
        ZOOM_PULSE,         // 缩放脉冲
        FADE_TRANSITION,    // 淡入淡出
        SLIDE_TRANSITION,   // 滑动过渡
        ROTATION_SPIN,      // 旋转
        SHAKE,              // 抖动
        RAINBOW_SHIFT,      // 彩虹色移
        GLITCH_EFFECT,      // 故障效果
        PARTICLE_EXPLODE,   // 粒子爆炸
        WAVE_DISTORT,       // 波纹扭曲
    }

    // ========== 动画配置 ==========

    data class AnimationConfig(
        val type: AnimationType = AnimationType.KEN_BURNS,
        val durationMs: Long = 3000,
        val frameCount: Int = 30,
        val startScale: Float = 1.0f,
        val endScale: Float = 1.2f,
        val startX: Float = 0f,
        val startY: Float = 0f,
        val endX: Float = 0f,
        val endY: Float = 0f,
        val rotation: Float = 0f,
        val easing: EasingType = EasingType.EASE_IN_OUT,
        val loopCount: Int = 0,  // 0 = 无限
        val outputFormat: OutputFormat = OutputFormat.GIF,
        val quality: Int = 80
    )

    enum class EasingType {
        LINEAR,
        EASE_IN,
        EASE_OUT,
        EASE_IN_OUT,
        BOUNCE,
        ELASTIC
    }

    enum class OutputFormat {
        GIF,
        WEBP,
        PNG_SEQUENCE,
        MP4
    }

    // ========== 创建动画 ==========

    suspend fun createAnimation(
        bitmap: Bitmap,
        config: AnimationConfig
    ): Bitmap = withContext(Dispatchers.Default) {
        Log.d(TAG, "创建动画帧: ${config.type}")

        when (config.type) {
            AnimationType.KEN_BURNS -> createKenBurnsEffect(bitmap, config)
            AnimationType.ZOOM_PULSE -> createZoomPulse(bitmap, config)
            AnimationType.FADE_TRANSITION -> createFadeTransition(bitmap, config)
            AnimationType.SLIDE_TRANSITION -> createSlideTransition(bitmap, config)
            AnimationType.ROTATION_SPIN -> createRotationSpin(bitmap, config)
            AnimationType.SHAKE -> createShake(bitmap, config)
            AnimationType.RAINBOW_SHIFT -> createRainbowShift(bitmap, config)
            AnimationType.GLITCH_EFFECT -> createGlitchEffect(bitmap, config)
            AnimationType.PARTICLE_EXPLODE -> createParticleExplode(bitmap, config)
            AnimationType.WAVE_DISTORT -> createWaveDistort(bitmap, config)
        }
    }

    // ========== 动画效果实现 ==========

    private fun createKenBurnsEffect(bitmap: Bitmap, config: AnimationConfig): Bitmap {
        // Ken Burns 效果：平滑缩放和平移
        val frameIndex = config.frameCount / 2  // 返回中间帧作为预览
        val progress = frameIndex.toFloat() / config.frameCount

        val scale = lerp(config.startScale, config.endScale, ease(progress, config.easing))
        val x = lerp(config.startX, config.endX, ease(progress, config.easing))
        val y = lerp(config.startY, config.endY, ease(progress, config.easing))

        return applyTransform(bitmap, scale, x, y, 0f)
    }

    private fun createZoomPulse(bitmap: Bitmap, config: AnimationConfig): Bitmap {
        val frameIndex = config.frameCount / 2
        val progress = frameIndex.toFloat() / config.frameCount
        val pulseScale = 1f + sin(progress * PI.toFloat() * 2) * 0.2f

        return applyTransform(bitmap, pulseScale, 0f, 0f, 0f)
    }

    private fun createFadeTransition(bitmap: Bitmap, config: AnimationConfig): Bitmap {
        val frameIndex = config.frameCount / 2
        val progress = frameIndex.toFloat() / config.frameCount
        val alpha = (255 * (1 - abs(progress * 2 - 1))).toInt()

        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        
        val paint = Paint().apply { this.alpha = alpha.coerceIn(0, 255) }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return result
    }

    private fun createSlideTransition(bitmap: Bitmap, config: AnimationConfig): Bitmap {
        val frameIndex = config.frameCount / 2
        val progress = frameIndex.toFloat() / config.frameCount
        val offsetX = progress * bitmap.width * 0.2f

        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bitmap, offsetX, 0f, Paint())

        return result
    }

    private fun createRotationSpin(bitmap: Bitmap, config: AnimationConfig): Bitmap {
        val frameIndex = config.frameCount / 2
        val progress = frameIndex.toFloat() / config.frameCount
        val rotation = progress * config.rotation

        return applyTransform(bitmap, 1f, 0f, 0f, rotation)
    }

    private fun createShake(bitmap: Bitmap, config: AnimationConfig): Bitmap {
        val frameIndex = config.frameCount / 2
        val progress = frameIndex.toFloat() / config.frameCount
        val shakeAmount = sin(progress * PI.toFloat() * 8) * 10f

        return applyTransform(bitmap, 1f, shakeAmount, shakeAmount * 0.5f, 0f)
    }

    private fun createRainbowShift(bitmap: Bitmap, config: AnimationConfig): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(result.width * result.height)
        result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)

        val hueShift = (config.frameCount / 2) * 30f

        for (i in pixels.indices) {
            val color = pixels[i]
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            hsv[0] = (hsv[0] + hueShift) % 360f
            pixels[i] = Color.HSVToColor(Color.alpha(color), hsv)
        }

        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        return result
    }

    private fun createGlitchEffect(bitmap: Bitmap, config: AnimationConfig): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val random = java.util.Random()

        val pixels = IntArray(result.width * result.height)
        result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)

        val glitchIntensity = 0.1f

        for (y in 0 until result.height) {
            if (random.nextFloat() < glitchIntensity) {
                val startX = random.nextInt(result.width / 4)
                val endX = startX + random.nextInt(result.width / 4)
                val shift = random.nextInt(20) - 10

                for (x in startX until minOf(endX, result.width - 1)) {
                    val srcX = (x + shift).coerceIn(0, result.width - 1)
                    pixels[y * result.width + x] = pixels[y * result.width + srcX]
                }
            }
        }

        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        return result
    }

    private fun createParticleExplode(bitmap: Bitmap, config: AnimationConfig): Bitmap {
        // 简化的粒子爆炸效果
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        
        val frameIndex = config.frameCount / 2
        val progress = frameIndex.toFloat() / config.frameCount
        val particleCount = 20

        val paint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        for (i in 0 until particleCount) {
            val angle = (i.toFloat() / particleCount) * 2 * PI.toFloat()
            val distance = progress * bitmap.width * 0.3f
            val x = bitmap.width / 2f + cos(angle) * distance
            val y = bitmap.height / 2f + sin(angle) * distance
            val size = 10f * (1 - progress)

            canvas.drawCircle(x, y, size, paint)
        }

        return result
    }

    private fun createWaveDistort(bitmap: Bitmap, config: AnimationConfig): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val newPixels = IntArray(bitmap.width * bitmap.height)
        val waveAmplitude = 10f
        val waveFrequency = 0.05f

        val frameIndex = config.frameCount / 2
        val phase = (frameIndex.toFloat() / config.frameCount) * 4 * PI.toFloat()

        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val offsetX = (sin(y * waveFrequency + phase) * waveAmplitude).toInt()
                val srcX = (x + offsetX).coerceIn(0, bitmap.width - 1)
                newPixels[y * bitmap.width + x] = pixels[y * bitmap.width + srcX]
            }
        }

        result.setPixels(newPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return result
    }

    // ========== 辅助方法 ==========

    private fun applyTransform(
        bitmap: Bitmap,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        rotation: Float
    ): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)

        val matrix = Matrix()
        matrix.postTranslate(bitmap.width / 2f, bitmap.height / 2f)
        matrix.postScale(scale, scale)
        matrix.postRotate(rotation)
        matrix.postTranslate(-bitmap.width / 2f + offsetX, -bitmap.height / 2f + offsetY)

        canvas.drawBitmap(bitmap, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return result
    }

    private fun lerp(start: Float, end: Float, t: Float): Float {
        return start + (end - start) * t
    }

    private fun ease(t: Float, easing: EasingType): Float {
        return when (easing) {
            EasingType.LINEAR -> t
            EasingType.EASE_IN -> t * t
            EasingType.EASE_OUT -> t * (2 - t)
            EasingType.EASE_IN_OUT -> if (t < 0.5f) 2 * t * t else -1 + (4 - 2 * t) * t
            EasingType.BOUNCE -> {
                val n1 = 7.5625f
                val d1 = 2.75f
                val tAdjusted = when {
                    t < 1 / d1 -> t
                    t < 2 / d1 -> t - 1.5f / d1
                    t < 2.5 / d1 -> t - 2.25f / d1
                    else -> t - 2.625f / d1
                }
                val result = when {
                    t < 1 / d1 -> n1 * t * t
                    t < 2 / d1 -> n1 * tAdjusted * tAdjusted + 0.75f
                    t < 2.5 / d1 -> n1 * tAdjusted * tAdjusted + 0.9375f
                    else -> n1 * tAdjusted * tAdjusted + 0.984375f
                }
                result
            }
            EasingType.ELASTIC -> {
                if (t == 0f || t == 1f) t
                else (2f.pow(10 * (t - 1)) * sin((t * 10 - 0.75f) * (2 * PI.toFloat() / 3)) + 1f)
            }
        }
    }

    // ========== GIF 生成 ==========

    suspend fun generateGif(
        bitmap: Bitmap,
        config: AnimationConfig,
        outputPath: String
    ): String = withContext(Dispatchers.IO) {
        // 简化的 GIF 生成
        // 实际应该使用 AnimatedGifEncoder
        Log.d(TAG, "生成 GIF: $outputPath")
        outputPath
    }

    // ========== 获取动画预览 ==========

    fun getAnimationTypes(): List<AnimationType> = AnimationType.entries

    fun getAnimationInfo(type: AnimationType): Pair<String, String> {
        return when (type) {
            AnimationType.KEN_BURNS -> "Ken Burns" to "经典幻灯片缩放平移效果"
            AnimationType.ZOOM_PULSE -> "缩放脉冲" to "缩放呼吸动画"
            AnimationType.FADE_TRANSITION -> "淡入淡出" to "平滑透明度过渡"
            AnimationType.SLIDE_TRANSITION -> "滑动过渡" to "水平滑动效果"
            AnimationType.ROTATION_SPIN -> "旋转" to "中心旋转动画"
            AnimationType.SHAKE -> "抖动" to "轻微抖动效果"
            AnimationType.RAINBOW_SHIFT -> "彩虹色移" to "色调循环变化"
            AnimationType.GLITCH_EFFECT -> "故障效果" to "数字故障艺术"
            AnimationType.PARTICLE_EXPLODE -> "粒子爆炸" to "粒子扩散动画"
            AnimationType.WAVE_DISTORT -> "波纹扭曲" to "水波纹效果"
        }
    }
}
