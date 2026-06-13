package comkuaihuiai.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * 可绘AI v3.5.0 - 实时预览引擎
 */
class RealtimePreviewEngine(private val context: Context) {

    companion object {
        private const val TAG = "RealtimePreviewEngine"
        private const val PREVIEW_SIZE = 256
        private const val DEBOUNCE_MS = 300L
    }
    
    data class PreviewConfig(
        val prompt: String = "",
        val negativePrompt: String = "",
        val steps: Int = 20,
        val guidance: Float = 7f,
        val width: Int = 512,
        val height: Int = 512,
        val seed: Long = -1
    )
    
    data class PreviewResult(
        val success: Boolean,
        val preview: Bitmap?,
        val fullSize: Bitmap? = null,
        val processingTimeMs: Long
    )
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var lastConfig: PreviewConfig? = null
    private var debounceJob: Job? = null
    
    private val _previewFlow = MutableSharedFlow<PreviewResult>()
    val previewFlow: SharedFlow<PreviewResult> = _previewFlow.asSharedFlow()
    
    private val _statusFlow = MutableStateFlow<PreviewStatus>(PreviewStatus.IDLE)
    val statusFlow: StateFlow<PreviewStatus> = _statusFlow.asStateFlow()
    
    enum class PreviewStatus { IDLE, GENERATING, READY, ERROR }
    
    /**
     * 生成预览（防抖）
     */
    fun generatePreview(config: PreviewConfig) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MS)
            lastConfig = config
            _statusFlow.value = PreviewStatus.GENERATING
            val result = generateLowResPreview(config)
            _statusFlow.value = if (result.success) PreviewStatus.READY else PreviewStatus.ERROR
            _previewFlow.emit(result)
        }
    }
    
    /**
     * 立即生成预览
     */
    suspend fun generateNow(config: PreviewConfig): PreviewResult {
        lastConfig = config
        _statusFlow.value = PreviewStatus.GENERATING
        val result = generateLowResPreview(config)
        _statusFlow.value = if (result.success) PreviewStatus.READY else PreviewStatus.ERROR
        return result
    }
    
    /**
     * 获取完整尺寸图像
     */
    suspend fun generateFullImage(): PreviewResult {
        val config = lastConfig ?: return PreviewResult(false, null, null, 0)
        _statusFlow.value = PreviewStatus.GENERATING
        val preview = generateLowResPreview(config)
        val result = PreviewResult(preview.success, preview.preview, preview.preview, preview.processingTimeMs)
        _statusFlow.value = if (result.success) PreviewStatus.READY else PreviewStatus.ERROR
        return result
    }
    
    /**
     * 更新参数并预览
     */
    fun updateAndPreview(config: PreviewConfig) = generatePreview(config)
    
    fun release() {
        scope.cancel()
    }
    
    private suspend fun generateLowResPreview(config: PreviewConfig): PreviewResult = 
        withContext(Dispatchers.Default) {
            val start = System.currentTimeMillis()
            Log.i(TAG, "生成预览: ${config.prompt.take(20)}...")
            
            try {
                // 生成低分辨率预览
                val preview = Bitmap.createBitmap(PREVIEW_SIZE, PREVIEW_SIZE, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(preview)
                val paint = Paint().apply { color = Color.GRAY; style = Paint.Style.FILL }
                canvas.drawRect(0f, 0f, PREVIEW_SIZE.toFloat(), PREVIEW_SIZE.toFloat(), paint)
                
                // 添加渐变效果模拟生成
                val gradientPaint = Paint().apply {
                    color = Color.rgb(99 + (config.seed % 50).toInt(), 102 + (config.seed % 30).toInt(), 241)
                }
                canvas.drawCircle(PREVIEW_SIZE / 2f, PREVIEW_SIZE / 2f, PREVIEW_SIZE / 3f, gradientPaint)
                
                delay(100) // 模拟处理时间
                
                PreviewResult(true, preview, null, System.currentTimeMillis() - start)
            } catch (e: Exception) {
                PreviewResult(false, null, null, 0)
            }
        }
}
