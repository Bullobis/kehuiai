package comkuaihuiai.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * 可绘AI v3.5.0 - 风格迁移引擎
 */
class StyleTransferEngine(private val context: Context) {

    companion object {
        private const val TAG = "StyleTransferEngine"
        const val DEFAULT_INTENSITY = 0.7f
    }
    
    enum class ArtStyle(val displayName: String, val emoji: String) {
        VAN_GOGH("梵高", "🌻"),
        PICASSO("毕加索", "🎭"),
        MONET("莫奈", "🌸"),
        HOKUSAI("葛饰北斋", "🌊"),
        KANDINSKY("康定斯基", "⬛"),
        POP_ART("波普艺术", "💫"),
        CYBERPUNK("赛博朋克", "🤖"),
        STEAM_PUNK("蒸汽朋克", "⚙️"),
        CHINESE_INK("水墨画", "🖌️"),
        UKIYO_E("浮世绘", "🎋"),
        COMIC("漫画风", "📖"),
        ANIME("动漫风", "✨"),
        OIL_PAINTING("油画", "🖼️"),
        VINTAGE("复古胶片", "📷"),
        HDR("HDR摄影", "🌄")
    }
    
    data class TransferResult(
        val success: Boolean,
        val outputBitmap: Bitmap?,
        val style: ArtStyle,
        val intensity: Float,
        val processingTimeMs: Long
    )
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val matrixCache = mutableMapOf<ArtStyle, ColorMatrix>()
    
    suspend fun applyStyle(
        input: Bitmap,
        style: ArtStyle,
        intensity: Float = DEFAULT_INTENSITY
    ): TransferResult = withContext(Dispatchers.Default) {
        val start = System.currentTimeMillis()
        Log.i(TAG, "🎨 风格: ${style.displayName}")
        
        try {
            val matrix = getStyleMatrix(style, intensity)
            val output = applyMatrix(input, matrix)
            TransferResult(true, output, style, intensity, System.currentTimeMillis() - start)
        } catch (e: Exception) {
            Log.e(TAG, "失败: ${e.message}")
            TransferResult(false, null, style, intensity, 0)
        }
    }
    
    fun recommendStyle(): ArtStyle = ArtStyle.ANIME
    
    fun getAllStyles(): List<ArtStyle> = ArtStyle.entries.toList()
    
    fun release() = scope.cancel()
    
    private fun getStyleMatrix(style: ArtStyle, intensity: Float): ColorMatrix {
        matrixCache[style]?.let { return it }
        
        val matrix = when (style) {
            ArtStyle.VAN_GOGH -> ColorMatrix(floatArrayOf(1.3f, 0.1f, 0.1f, 0f, 10f, 0.1f, 1.2f, 0.1f, 0f, 5f, 0.1f, 0.2f, 1.4f, 0f, 20f, 0f, 0f, 0f, 1f, 0f))
            ArtStyle.PICASSO -> ColorMatrix(floatArrayOf(1.5f, 0f, 0f, 0f, -30f, 0f, 1.2f, 0f, 0f, -10f, 0f, 0f, 1.1f, 0f, 10f, 0f, 0f, 0f, 1f, 0f))
            ArtStyle.MONET -> ColorMatrix(floatArrayOf(1.1f, 0.1f, 0.1f, 0f, 15f, 0.1f, 1.1f, 0.1f, 0f, 10f, 0.1f, 0.1f, 1.0f, 0f, 20f, 0f, 0f, 0f, 1f, 0f))
            ArtStyle.HOKUSAI -> ColorMatrix(floatArrayOf(1.2f, 0.1f, 0.1f, 0f, 0f, 0.1f, 1.1f, 0.2f, 0f, 10f, 0.1f, 0.2f, 1.0f, 0f, 30f, 0f, 0f, 0f, 1f, 0f))
            ArtStyle.KANDINSKY -> ColorMatrix(floatArrayOf(1.5f, 0f, 0f, 0f, -20f, 0f, 1.5f, 0f, 0f, -10f, 0f, 0f, 1.5f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
            ArtStyle.POP_ART -> ColorMatrix(floatArrayOf(1.8f, 0f, 0f, 0f, -50f, 0f, 1.8f, 0f, 0f, -50f, 0f, 0f, 1.8f, 0f, -50f, 0f, 0f, 0f, 1f, 0f))
            ArtStyle.CYBERPUNK -> ColorMatrix(floatArrayOf(1.2f, 0f, 0.3f, 0f, 0f, 0f, 1.0f, 0.2f, 0f, 20f, 0.3f, 0.2f, 1.3f, 0f, 40f, 0f, 0f, 0f, 1f, 0f))
            ArtStyle.STEAM_PUNK -> ColorMatrix(floatArrayOf(1.2f, 0.2f, 0.1f, 0f, 20f, 0.1f, 1.0f, 0.1f, 0f, 10f, 0f, 0f, 0.8f, 0f, -10f, 0f, 0f, 0f, 1f, 0f))
            ArtStyle.CHINESE_INK -> ColorMatrix(floatArrayOf(0.3f, 0.3f, 0.3f, 0f, 120f, 0.3f, 0.3f, 0.3f, 0f, 120f, 0.3f, 0.3f, 0.3f, 0f, 120f, 0f, 0f, 0f, 1f, 0f))
            ArtStyle.UKIYO_E -> ColorMatrix(floatArrayOf(1.1f, 0.2f, 0.1f, 0f, 10f, 0.1f, 1.0f, 0.2f, 0f, 15f, 0f, 0.1f, 0.9f, 0f, 25f, 0f, 0f, 0f, 1f, 0f))
            ArtStyle.COMIC -> ColorMatrix(floatArrayOf(1.5f, 0f, 0f, 0f, -30f, 0f, 1.5f, 0f, 0f, -30f, 0f, 0f, 1.5f, 0f, -30f, 0f, 0f, 0f, 1f, 0f))
            ArtStyle.ANIME -> ColorMatrix(floatArrayOf(1.1f, 0f, 0f, 0f, 10f, 0f, 1.1f, 0f, 0f, 10f, 0f, 0f, 1.2f, 0f, 20f, 0f, 0f, 0f, 1f, 0f)).apply { setSaturation(1.4f) }
            ArtStyle.OIL_PAINTING -> ColorMatrix(floatArrayOf(1.1f, 0f, 0f, 0f, 5f, 0f, 1.1f, 0f, 0f, 5f, 0f, 0f, 1.1f, 0f, 5f, 0f, 0f, 0f, 1f, 0f)).apply { setSaturation(1.2f) }
            ArtStyle.VINTAGE -> ColorMatrix(floatArrayOf(1.1f, 0.1f, 0.1f, 0f, 20f, 0.1f, 1.0f, 0.1f, 0f, 10f, 0f, 0.1f, 0.9f, 0f, -10f, 0f, 0f, 0f, 1f, 0f))
            ArtStyle.HDR -> ColorMatrix(floatArrayOf(1.3f, 0f, 0f, 0f, 0f, 0f, 1.3f, 0f, 0f, 0f, 0f, 0f, 1.3f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
        }
        
        matrixCache[style] = matrix
        return matrix
    }
    
    private fun applyMatrix(bitmap: Bitmap, matrix: ColorMatrix): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }
}
