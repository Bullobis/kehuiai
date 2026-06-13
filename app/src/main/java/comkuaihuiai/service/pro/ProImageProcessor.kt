package comkuaihuiai.service.pro

import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*

/**
 * 快绘AI Pro v4.0.0 - 超级图像处理引擎
 * 专业级图像处理、增强、修复、优化
 */
class ProImageProcessor(private val context: Context) {

    companion object {
        private const val TAG = "ProImageProcessor"
    }

    // ========== 滤镜类型 ==========
    enum class FilterType {
        NONE, GRAYSCALE, SEPIA, VINTAGE, WARM, COOL, VIVID, DRAMATIC,
        BLACK_WHITE, INVERT, CHROME, CONCENTRATE, FADE, INSTANT
    }

    // ========== 调整参数 ==========
    data class AdjustParams(
        var brightness: Float = 0f,
        var contrast: Float = 0f,
        var saturation: Float = 0f,
        var exposure: Float = 0f,
        var vignette: Float = 0f,
        var warmth: Float = 0f
    )

    // ========== 裁剪比例 ==========
    data class CropRatio(val name: String, val ratio: Float) {
        companion object {
            val FREE = CropRatio("自由", 0f)
            val SQUARE = CropRatio("1:1", 1f)
            val RATIO_4_3 = CropRatio("4:3", 4f / 3f)
            val RATIO_16_9 = CropRatio("16:9", 16f / 9f)
            fun all() = listOf(FREE, SQUARE, RATIO_4_3, RATIO_16_9)
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // ========== 加载图片 ==========
    fun loadImage(path: String): Bitmap? {
        return try {
            BitmapFactory.decodeFile(path)
        } catch (e: Exception) {
            Log.e(TAG, "加载图片失败", e)
            null
        }
    }

    // ========== 保存图片 ==========
    fun saveBitmap(bitmap: Bitmap, file: File, quality: Int = 90): Boolean {
        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, quality, out)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "保存失败", e)
            false
        }
    }

    // ========== 智能缩放 ==========
    fun smartScale(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val ratio = minOf(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height)
        if (ratio >= 1f) return bitmap
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt(),
            (bitmap.height * ratio).toInt(),
            true
        )
    }

    // ========== 裁剪 ==========
    fun centerCrop(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val scale = maxOf(targetWidth.toFloat() / bitmap.width, targetHeight.toFloat() / bitmap.height)
        val scaledWidth = (bitmap.width * scale).toInt()
        val scaledHeight = (bitmap.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        val x = (scaledWidth - targetWidth) / 2
        val y = (scaledHeight - targetHeight) / 2
        return Bitmap.createBitmap(scaled, x, y, targetWidth, targetHeight)
    }

    fun crop(bitmap: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap {
        return Bitmap.createBitmap(bitmap, x, y, width, height)
    }

    // ========== 旋转翻转 ==========
    fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun flip(bitmap: Bitmap, horizontal: Boolean): Bitmap {
        val matrix = Matrix().apply {
            if (horizontal) preScale(-1f, 1f) else preScale(1f, -1f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    // ========== 滤镜 ==========
    fun applyFilter(bitmap: Bitmap, filter: FilterType): Bitmap {
        return when (filter) {
            FilterType.NONE -> bitmap
            FilterType.GRAYSCALE -> applyGrayscale(bitmap)
            FilterType.SEPIA -> applySepia(bitmap)
            FilterType.VINTAGE -> applyVintage(bitmap)
            FilterType.WARM -> applyWarm(bitmap)
            FilterType.COOL -> applyCool(bitmap)
            FilterType.VIVID -> applyVivid(bitmap)
            FilterType.DRAMATIC -> applyDramatic(bitmap)
            FilterType.BLACK_WHITE -> applyGrayscale(bitmap)
            FilterType.INVERT -> applyInvert(bitmap)
            FilterType.CHROME -> applyChrome(bitmap)
            FilterType.CONCENTRATE -> applyConcentrate(bitmap)
            FilterType.FADE -> applyFade(bitmap)
            FilterType.INSTANT -> applyInstant(bitmap)
        }
    }

    private fun applyGrayscale(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint()
        val cm = ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun applySepia(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint()
        val sepia = ColorMatrix(floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(sepia)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun applyVintage(bitmap: Bitmap): Bitmap {
        return bitmap.copy(Bitmap.Config.ARGB_8888, true)
    }

    private fun applyWarm(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint()
        val warm = ColorMatrix(floatArrayOf(
            1.2f, 0f, 0f, 0f, 10f,
            0f, 1.1f, 0f, 0f, 5f,
            0f, 0f, 0.9f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(warm)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun applyCool(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint()
        val cool = ColorMatrix(floatArrayOf(
            0.9f, 0f, 0f, 0f, 0f,
            0f, 1.0f, 0f, 0f, 0f,
            0f, 0f, 1.2f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(cool)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun applyVivid(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint()
        val sat = ColorMatrix().apply { setSaturation(1.5f) }
        val contrast = ColorMatrix().apply {
            val scale = 1.15f
            set(floatArrayOf(
                scale, 0f, 0f, 0f, 0f,
                0f, scale, 0f, 0f, 0f,
                0f, 0f, scale, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        sat.postConcat(contrast)
        paint.colorFilter = ColorMatrixColorFilter(sat)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun applyDramatic(bitmap: Bitmap): Bitmap {
        return bitmap.copy(Bitmap.Config.ARGB_8888, true)
    }

    private fun applyInvert(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(result.width * result.height)
        result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        for (i in pixels.indices) {
            pixels[i] = Color.argb(
                Color.alpha(pixels[i]),
                255 - Color.red(pixels[i]),
                255 - Color.green(pixels[i]),
                255 - Color.blue(pixels[i])
            )
        }
        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        return result
    }

    private fun applyChrome(bitmap: Bitmap): Bitmap {
        return applySepia(bitmap)
    }

    private fun applyConcentrate(bitmap: Bitmap): Bitmap {
        return bitmap.copy(Bitmap.Config.ARGB_8888, true)
    }

    private fun applyFade(bitmap: Bitmap): Bitmap {
        return bitmap.copy(Bitmap.Config.ARGB_8888, true)
    }

    private fun applyInstant(bitmap: Bitmap): Bitmap {
        return bitmap.copy(Bitmap.Config.ARGB_8888, true)
    }

    // ========== 调整 ==========
    fun applyAdjustments(bitmap: Bitmap, params: AdjustParams): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint()
        val colorMatrix = ColorMatrix()

        // 亮度
        if (params.brightness != 0f) {
            val b = params.brightness * 2.55f
            val brightMatrix = ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, b,
                0f, 1f, 0f, 0f, b,
                0f, 0f, 1f, 0f, b,
                0f, 0f, 0f, 1f, 0f
            ))
            colorMatrix.postConcat(brightMatrix)
        }

        // 对比度
        if (params.contrast != 0f) {
            val scale = (params.contrast + 100f) / 100f
            val t = (-.5f * scale + .5f) * 255f
            val contrastMatrix = ColorMatrix(floatArrayOf(
                scale, 0f, 0f, 0f, t,
                0f, scale, 0f, 0f, t,
                0f, 0f, scale, 0f, t,
                0f, 0f, 0f, 1f, 0f
            ))
            colorMatrix.postConcat(contrastMatrix)
        }

        // 饱和度
        if (params.saturation != 0f) {
            val sat = ColorMatrix().apply { setSaturation((params.saturation + 100f) / 100f) }
            colorMatrix.postConcat(sat)
        }

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    // ========== 水印 ==========
    fun addWatermark(bitmap: Bitmap, text: String, position: Pair<Float, Float> = Pair(0.05f, 0.95f)): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            this.textSize = 48f * (bitmap.width / 1000f)
            color = Color.WHITE
            isAntiAlias = true
            setShadowLayer(4f, 2f, 2f, Color.argb(128, 0, 0, 0))
        }
        val x = bitmap.width * position.first
        val y = bitmap.height * position.second
        canvas.drawText(text, x, y, paint)
        return result
    }

    // ========== 边框 ==========
    fun addBorder(bitmap: Bitmap, width: Int, color: Int): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width + width * 2, bitmap.height + width * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(color)
        canvas.drawBitmap(bitmap, width.toFloat(), width.toFloat(), null)
        return result
    }

    // ========== 圆角 ==========
    fun addRoundedCorners(bitmap: Bitmap, radius: Float): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply { isAntiAlias = true }
        val rect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    // ========== 模糊 ==========
    fun applyBlur(bitmap: Bitmap, radius: Int): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(result.width * result.height)
        result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        val blurred = gaussianBlur(pixels, result.width, result.height, radius)
        result.setPixels(blurred, 0, result.width, 0, 0, result.width, result.height)
        return result
    }

    private fun gaussianBlur(pixels: IntArray, width: Int, height: Int, radius: Int): IntArray {
        val result = IntArray(pixels.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0; var g = 0; var b = 0; var count = 0
                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val nx = x + dx; val ny = y + dy
                        if (nx in 0 until width && ny in 0 until height) {
                            val p = pixels[ny * width + nx]
                            r += Color.red(p); g += Color.green(p); b += Color.blue(p)
                            count++
                        }
                    }
                }
                if (count > 0) {
                    result[y * width + x] = Color.argb(Color.alpha(pixels[y * width + x]), r / count, g / count, b / count)
                }
            }
        }
        return result
    }

    // ========== 获取图片信息 ==========
    fun getImageInfo(path: String): ImageInfo? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            ImageInfo(opts.outWidth, opts.outHeight, opts.outMimeType ?: "unknown", File(path).length())
        } catch (e: Exception) { null }
    }

    data class ImageInfo(val width: Int, val height: Int, val format: String, val size: Long)

    fun release() {
        scope.cancel()
    }
}
