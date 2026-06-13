package comkuaihuiai.service

import android.content.Context
import android.graphics.*
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * 可绘AI v3.5.0 - 面部修复专家
 */
class FaceEnhancer(private val context: Context) {

    companion object {
        private const val TAG = "FaceEnhancer"
    }
    
    enum class EnhancementType(val displayName: String) {
        FACE_REPAIR("人脸修复"),
        EYE_ENHANCE("眼部增强"),
        SKIN_SMOOTH("皮肤平滑"),
        BRIGHTEN("面部提亮"),
        AUTO_BEAUTIFY("一键美颜")
    }
    
    data class FaceDetection(val bounds: RectF, val confidence: Float = 1f)
    
    data class EnhancementResult(
        val success: Boolean,
        val outputBitmap: Bitmap?,
        val detectedFaces: Int,
        val processingTimeMs: Long
    )
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    suspend fun detectFaces(bitmap: Bitmap): List<FaceDetection> = withContext(Dispatchers.Default) {
        listOf(FaceDetection(RectF(bitmap.width * 0.25f, bitmap.height * 0.2f, 
            bitmap.width * 0.75f, bitmap.height * 0.8f), 0.95f))
    }
    
    suspend fun enhance(bitmap: Bitmap, type: EnhancementType, intensity: Float = 0.5f): EnhancementResult = 
        withContext(Dispatchers.Default) {
            val start = System.currentTimeMillis()
            Log.i(TAG, "增强: ${type.displayName}")
            try {
                val faces = detectFaces(bitmap)
                if (faces.isEmpty()) return@withContext EnhancementResult(false, null, 0, 0)
                val output = when (type) {
                    EnhancementType.FACE_REPAIR -> faceRepair(bitmap, faces, intensity)
                    EnhancementType.EYE_ENHANCE -> eyeEnhance(bitmap, faces, intensity)
                    EnhancementType.SKIN_SMOOTH -> skinSmooth(bitmap, faces, intensity)
                    EnhancementType.BRIGHTEN -> brighten(bitmap, faces, intensity)
                    EnhancementType.AUTO_BEAUTIFY -> autoBeautify(bitmap, faces, intensity)
                }
                EnhancementResult(true, output, faces.size, System.currentTimeMillis() - start)
            } catch (e: Exception) {
                EnhancementResult(false, null, 0, 0)
            }
        }
    
    fun autoBeautify(bitmap: Bitmap, intensity: Float = 0.5f): EnhancementResult {
        var result: EnhancementResult? = null
        scope.launch { result = enhance(bitmap, EnhancementType.AUTO_BEAUTIFY, intensity) }
        return result ?: EnhancementResult(false, null, 0, 0)
    }
    
    fun release() = scope.cancel()
    
    private fun faceRepair(bmp: Bitmap, faces: List<FaceDetection>, intensity: Float): Bitmap {
        val out = bmp.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        faces.forEach { face ->
            val paint = Paint().apply {
                isAntiAlias = true
                maskFilter = BlurMaskFilter(8f * intensity, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawOval(face.bounds, paint)
        }
        return out
    }
    
    private fun eyeEnhance(bmp: Bitmap, faces: List<FaceDetection>, intensity: Float): Bitmap {
        val out = bmp.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        faces.forEach { face ->
            val cx = face.bounds.centerX()
            val cy = face.bounds.top + face.bounds.height() * 0.3f
            val paint = Paint().apply { color = Color.WHITE; alpha = (intensity * 180).toInt(); isAntiAlias = true }
            canvas.drawCircle(cx - face.bounds.width() * 0.15f, cy, 3f * intensity, paint)
            canvas.drawCircle(cx + face.bounds.width() * 0.15f, cy, 3f * intensity, paint)
        }
        return out
    }
    
    private fun skinSmooth(bmp: Bitmap, faces: List<FaceDetection>, intensity: Float): Bitmap {
        val out = bmp.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        faces.forEach { face ->
            val paint = Paint().apply {
                isAntiAlias = true
                maskFilter = BlurMaskFilter(15f * intensity, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawOval(face.bounds, paint)
        }
        return out
    }
    
    private fun brighten(bmp: Bitmap, faces: List<FaceDetection>, intensity: Float): Bitmap {
        val out = bmp.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val matrix = ColorMatrix().apply { setScale(1f + intensity * 0.1f, 1f + intensity * 0.1f, 1f + intensity * 0.08f, 1f) }
        faces.forEach { face ->
            val expanded = RectF(face.bounds).apply { inset(-10f, -10f) }
            val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(matrix); isAntiAlias = true }
            canvas.drawOval(expanded, paint)
        }
        return out
    }
    
    private fun autoBeautify(bmp: Bitmap, faces: List<FaceDetection>, intensity: Float): Bitmap {
        var out = skinSmooth(bmp, faces, intensity * 0.6f)
        out = brighten(out, faces, intensity * 0.4f)
        return eyeEnhance(out, faces, intensity * 0.5f)
    }
}
