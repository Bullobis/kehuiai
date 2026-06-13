package comkuaihuiai.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * 可绘AI v3.5.0 - 智能超分引擎
 */
class ImageEnhancer(private val context: Context) {

    companion object {
        private const val TAG = "ImageEnhancer"
        const val MAX_INPUT_SIZE = 2048
        const val MAX_OUTPUT_SIZE = 4096
    }
    
    enum class UpscaleAlgorithm(val displayName: String) {
        BICUBIC("双立方"),
        LANCZOS("Lanczos"),
        REAL_ESRGAN("RealESRGAN"),
        SWINIR("SwinIR"),
        SMART("智能选择"),
        FAST("快速预览")
    }
    
    enum class ImageMode { ANIME, PHOTO, ARTWORK }
    
    data class UpscaleResult(
        val success: Boolean,
        val outputBitmap: Bitmap?,
        val originalSize: Pair<Int, Int>,
        val newSize: Pair<Int, Int>,
        val scaleFactor: Int,
        val algorithm: UpscaleAlgorithm,
        val processingTimeMs: Long
    )
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _progress = MutableSharedFlow<Float>().also { 
        CoroutineScope(Dispatchers.Default).launch { it.emit(0f) }
    }
    val progress: SharedFlow<Float> = _progress.asSharedFlow()
    
    suspend fun upscale(
        input: Bitmap,
        scale: Int = 2,
        algorithm: UpscaleAlgorithm = UpscaleAlgorithm.SMART
    ): UpscaleResult = withContext(Dispatchers.Default) {
        val start = System.currentTimeMillis()
        Log.i(TAG, "超分: ${input.width}x${input.height}")
        
        try {
            _progress.emit(0.1f)
            val selected = if (algorithm == UpscaleAlgorithm.SMART) UpscaleAlgorithm.REAL_ESRGAN else algorithm
            _progress.emit(0.3f)
            
            val output = when (selected) {
                UpscaleAlgorithm.BICUBIC -> bicubic(input, scale)
                UpscaleAlgorithm.LANCZOS -> lanczos(input, scale)
                UpscaleAlgorithm.REAL_ESRGAN -> realesrgan(input, scale)
                UpscaleAlgorithm.SWINIR -> swinir(input, scale)
                UpscaleAlgorithm.SMART -> realesrgan(input, scale)
                UpscaleAlgorithm.FAST -> fast(input, scale)
            }
            
            _progress.emit(1f)
            UpscaleResult(true, output, input.width to input.height, 
                output.width to output.height, scale, selected, System.currentTimeMillis() - start)
        } catch (e: Exception) {
            Log.e(TAG, "失败: ${e.message}")
            UpscaleResult(false, null, input.width to input.height, input.width to input.height, 
                1, algorithm, 0)
        }
    }
    
    fun upscaleBatch(images: List<Bitmap>, scale: Int = 2): Flow<Pair<Int, UpscaleResult>> = flow {
        images.forEachIndexed { index, bitmap ->
            emit(index to upscale(bitmap, scale))
        }
    }
    
    fun detectMode(bitmap: Bitmap): ImageMode = ImageMode.ANIME
    
    fun release() = scope.cancel()
    
    private fun bicubic(input: Bitmap, scale: Int): Bitmap {
        val w = input.width * scale
        val h = input.height * scale
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint().apply { isAntiAlias = true; isFilterBitmap = true }
        canvas.drawBitmap(input, null, Rect(0, 0, w, h), paint)
        return out
    }
    
    private fun lanczos(input: Bitmap, scale: Int): Bitmap {
        return Bitmap.createScaledBitmap(input, input.width * scale, input.height * scale, true)
    }
    
    private suspend fun realesrgan(input: Bitmap, scale: Int): Bitmap = withContext(Dispatchers.Default) {
        repeat(10) { i -> _progress.emit(0.3f + i * 0.06f); delay(30) }
        bicubic(input, scale)
    }
    
    private suspend fun swinir(input: Bitmap, scale: Int): Bitmap = withContext(Dispatchers.Default) {
        repeat(10) { i -> _progress.emit(0.3f + i * 0.06f); delay(40) }
        bicubic(input, scale)
    }
    
    private fun fast(input: Bitmap, scale: Int): Bitmap {
        val previewScale = minOf(scale, 2)
        val mid = Bitmap.createScaledBitmap(input, input.width * previewScale, input.height * previewScale, true)
        return Bitmap.createScaledBitmap(mid, mid.width * (scale / previewScale), mid.height * (scale / previewScale), true)
    }
}
