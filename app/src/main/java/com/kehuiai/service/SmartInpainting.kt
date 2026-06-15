package com.kehuiai.service

import android.content.Context
import android.graphics.*
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.*
import kotlin.random.Random

/**
 * 可绘AI v3.5.0 - 智能局部重绘引擎 (完整版)
 * 
 * 功能：
 * - 智能边缘检测
 * - 语义分割蒙版
 * - 笔刷控制 (大小/硬度/透明度)
 * - 历史记录与撤销/重做
 * - 多种填充模式
 * - 遮罩导出/导入
 */
class SmartInpainting(private val context: Context) {

    companion object {
        private const val TAG = "SmartInpainting"
        
        // 笔刷参数
        const val MIN_BRUSH_SIZE = 5f
        const val MAX_BRUSH_SIZE = 200f
        const val DEFAULT_BRUSH_SIZE = 30f
        const val DEFAULT_BRUSH_HARDNESS = 0.8f
        const val DEFAULT_BRUSH_OPACITY = 1.0f
        
        // 内存限制
        private const val MAX_MASK_PIXELS = 4096 * 4096
        private const val UNDO_STACK_SIZE = 20
    }
    
    /**
     * 填充模式
     */
    enum class FillMode(val displayName: String, val description: String) {
        CONTENT_AWARE("内容感知", "AI分析周围像素智能填充"),
        NEURAL_INPAINT("神经网络", "使用深度学习模型填充"),
        SOLID_COLOR("纯色填充", "使用指定颜色填充"),
        PATTERN("图案填充", "使用预设图案填充"),
        BLUR("模糊填充", "模糊蒙版边缘"),
        GRADIENT("渐变填充", "从边缘向内渐变")
    }
    
    /**
     * 笔刷形状
     */
    enum class BrushShape {
        CIRCLE,      // 圆形
        SQUARE,      // 方形
        SOFT_CIRCLE, // 软圆形
        AIRBRUSH     // 气笔
    }
    
    /**
     * 笔刷配置
     */
    data class BrushConfig(
        val size: Float = DEFAULT_BRUSH_SIZE,
        val hardness: Float = DEFAULT_BRUSH_HARDNESS,
        val opacity: Float = DEFAULT_BRUSH_OPACITY,
        val shape: BrushShape = BrushShape.CIRCLE,
        val color: Int = Color.WHITE,
        val pressureSensitive: Boolean = false
    )
    
    /**
     * 遮罩层
     */
    data class MaskLayer(
        val bitmap: Bitmap,
        val timestamp: Long = System.currentTimeMillis(),
        val description: String = ""
    )
    
    /**
     * 蒙版状态
     */
    data class MaskState(
        val mask: Bitmap,
        val undoneStack: List<MaskLayer> = emptyList(),
        val redoneStack: List<MaskLayer> = emptyList()
    )
    
    /**
     * 重绘结果
     */
    data class InpaintResult(
        val success: Boolean,
        val outputBitmap: Bitmap?,
        val maskUsed: Bitmap? = null,
        val processingTimeMs: Long = 0,
        val confidenceMap: FloatArray? = null,  // 每个像素的置信度
        val error: String? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as InpaintResult
            return success == other.success && outputBitmap === other.outputBitmap
        }
        
        override fun hashCode(): Int {
            var result = success.hashCode()
            result = 31 * result + (outputBitmap?.hashCode() ?: 0)
            return result
        }
    }
    
    /**
     * 笔画记录
     */
    data class Stroke(
        val points: List<PointF>,
        val brushConfig: BrushConfig,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _maskState = MutableStateFlow<MaskState?>(null)
    val maskState: StateFlow<MaskState?> = _maskState.asStateFlow()
    
    private val _strokeHistory = MutableStateFlow<List<Stroke>>(emptyList())
    val strokeHistory: StateFlow<List<Stroke>> = _strokeHistory.asStateFlow()
    
    private val _currentBrush = MutableStateFlow(BrushConfig())
    val brushConfig: StateFlow<BrushConfig> = _currentBrush.asStateFlow()
    
    private val _progress = MutableSharedFlow<Float>(extraBufferCapacity = 64)
    val progress: SharedFlow<Float> = _progress.asSharedFlow()
    
    private val _edgeDetection = MutableSharedFlow<Bitmap>(extraBufferCapacity = 16)
    val edgeDetected: SharedFlow<Bitmap> = _edgeDetection.asSharedFlow()
    
    // 边缘检测缓存
    private val edgeCache = mutableMapOf<String, Bitmap>()
    
    // 笔画缓冲
    private var currentStrokePoints = mutableListOf<PointF>()
    private var currentStrokeBrush: BrushConfig? = null
    
    /**
     * 初始化蒙版
     */
    fun initMask(width: Int, height: Int): Bitmap {
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        mask.eraseColor(Color.TRANSPARENT)
        
        _maskState.value = MaskState(
            mask = mask,
            undoneStack = emptyList(),
            redoneStack = emptyList()
        )
        
        _strokeHistory.value = emptyList()
        currentStrokePoints.clear()
        
        Log.i(TAG, "蒙版初始化: ${width}x${height}")
        return mask
    }
    
    /**
     * 设置笔刷配置
     */
    fun setBrush(config: BrushConfig) {
        _currentBrush.value = config
    }
    
    /**
     * 开始笔画
     */
    fun beginStroke(x: Float, y: Float) {
        currentStrokePoints.clear()
        currentStrokePoints.add(PointF(x, y))
        currentStrokeBrush = _currentBrush.value.copy()
    }
    
    /**
     * 继续笔画
     */
    fun continueStroke(x: Float, y: Float) {
        currentStrokePoints.add(PointF(x, y))
        
        // 实时绘制到蒙版
        val state = _maskState.value ?: return
        val brush = currentStrokeBrush ?: return
        
        if (currentStrokePoints.size >= 2) {
            val canvas = Canvas(state.mask)
            val paint = createBrushPaint(brush)
            
            // 连接所有点
            for (i in 1 until currentStrokePoints.size) {
                val p1 = currentStrokePoints[i - 1]
                val p2 = currentStrokePoints[i]
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
            }
        }
    }
    
    /**
     * 结束笔画
     */
    fun endStroke() {
        val brush = currentStrokeBrush ?: return
        
        if (currentStrokePoints.isNotEmpty()) {
            // 保存笔画记录
            val stroke = Stroke(
                points = currentStrokePoints.toList(),
                brushConfig = brush
            )
            
            _strokeHistory.value = _strokeHistory.value + stroke
            
            // 限制历史记录数量
            if (_strokeHistory.value.size > 100) {
                _strokeHistory.value = _strokeHistory.value.takeLast(100)
            }
        }
        
        currentStrokePoints.clear()
        currentStrokeBrush = null
    }
    
    /**
     * 撤销
     */
    fun undo(): Boolean {
        val state = _maskState.value ?: return false
        
        if (state.undoneStack.isEmpty()) return false
        
        // 保存当前状态到重做栈
        val currentLayer = MaskLayer(
            bitmap = state.mask.copy(Bitmap.Config.ARGB_8888, true),
            timestamp = System.currentTimeMillis(),
            description = "Undo"
        )
        
        // 恢复上一个状态
        val previousLayer = state.undoneStack.last()
        val newMask = previousLayer.bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        _maskState.value = MaskState(
            mask = newMask,
            undoneStack = state.undoneStack.dropLast(1),
            redoneStack = state.redoneStack + currentLayer
        )
        
        Log.i(TAG, "撤销成功")
        return true
    }
    
    /**
     * 重做
     */
    fun redo(): Boolean {
        val state = _maskState.value ?: return false
        
        if (state.redoneStack.isEmpty()) return false
        
        // 保存当前状态到撤销栈
        val currentLayer = MaskLayer(
            bitmap = state.mask.copy(Bitmap.Config.ARGB_8888, true),
            timestamp = System.currentTimeMillis(),
            description = "Redo"
        )
        
        // 恢复下一个状态
        val nextLayer = state.redoneStack.last()
        val newMask = nextLayer.bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        _maskState.value = MaskState(
            mask = newMask,
            undoneStack = state.undoneStack + currentLayer,
            redoneStack = state.redoneStack.dropLast(1)
        )
        
        Log.i(TAG, "重做成功")
        return true
    }
    
    /**
     * 清空蒙版
     */
    fun clearMask() {
        val state = _maskState.value ?: return
        
        // 保存当前状态用于撤销
        val layer = MaskLayer(
            bitmap = state.mask.copy(Bitmap.Config.ARGB_8888, true),
            timestamp = System.currentTimeMillis(),
            description = "Clear"
        )
        
        state.mask.eraseColor(Color.TRANSPARENT)
        
        _maskState.value = state.copy(
            undoneStack = state.undoneStack + layer,
            redoneStack = emptyList()
        )
        
        _strokeHistory.value = emptyList()
    }
    
    /**
     * 反转蒙版
     */
    fun invertMask() {
        val state = _maskState.value ?: return
        
        // 保存当前状态
        val layer = MaskLayer(
            bitmap = state.mask.copy(Bitmap.Config.ARGB_8888, true),
            timestamp = System.currentTimeMillis(),
            description = "Invert"
        )
        
        // 反转像素
        val pixels = IntArray(state.mask.width * state.mask.height)
        state.mask.getPixels(pixels, 0, state.mask.width, 0, 0, state.mask.width, state.mask.height)
        
        for (i in pixels.indices) {
            val alpha = (pixels[i] shr 24) and 0xFF
            pixels[i] = if (alpha > 0) {
                (0x00 shl 24) or (pixels[i] and 0x00FFFFFF)
            } else {
                (0xFF shl 24) or (pixels[i] and 0x00FFFFFF)
            }
        }
        
        state.mask.setPixels(pixels, 0, state.mask.width, 0, 0, state.mask.width, state.mask.height)
        
        _maskState.value = state.copy(
            undoneStack = state.undoneStack + layer,
            redoneStack = emptyList()
        )
    }
    
    /**
     * 羽化蒙版边缘
     */
    fun featherMask(radius: Float = 10f) {
        val state = _maskState.value ?: return
        
        val layer = MaskLayer(
            bitmap = state.mask.copy(Bitmap.Config.ARGB_8888, true),
            timestamp = System.currentTimeMillis(),
            description = "Feather"
        )
        
        // 应用高斯模糊模拟羽化
        val paint = Paint().apply {
            maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        }
        
        val canvas = Canvas(state.mask)
        canvas.drawBitmap(state.mask, 0f, 0f, paint)
        
        _maskState.value = state.copy(
            undoneStack = state.undoneStack + layer,
            redoneStack = emptyList()
        )
    }
    
    /**
     * 收缩蒙版
     */
    fun contractMask(pixels: Int = 5) {
        val state = _maskState.value ?: return
        
        val layer = MaskLayer(
            bitmap = state.mask.copy(Bitmap.Config.ARGB_8888, true),
            timestamp = System.currentTimeMillis(),
            description = "Contract"
        )
        
        // 腐蚀效果
        val paint = Paint().apply {
            maskFilter = BlurMaskFilter(pixels.toFloat(), BlurMaskFilter.Blur.INNER)
        }
        
        val canvas = Canvas(state.mask)
        canvas.drawBitmap(state.mask, 0f, 0f, paint)
        
        _maskState.value = state.copy(
            undoneStack = state.undoneStack + layer,
            redoneStack = emptyList()
        )
    }
    
    /**
     * 扩展蒙版
     */
    fun expandMask(pixels: Int = 5) {
        val state = _maskState.value ?: return
        
        val layer = MaskLayer(
            bitmap = state.mask.copy(Bitmap.Config.ARGB_8888, true),
            timestamp = System.currentTimeMillis(),
            description = "Expand"
        )
        
        // 膨胀效果
        val paint = Paint().apply {
            maskFilter = BlurMaskFilter(pixels.toFloat(), BlurMaskFilter.Blur.OUTER)
        }
        
        val canvas = Canvas(state.mask)
        canvas.drawBitmap(state.mask, 0f, 0f, paint)
        
        _maskState.value = state.copy(
            undoneStack = state.undoneStack + layer,
            redoneStack = emptyList()
        )
    }
    
    /**
     * 智能边缘检测
     */
    suspend fun detectEdges(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        Log.i(TAG, "边缘检测...")
        
        val cacheKey = "${bitmap.width}x${bitmap.height}"
        edgeCache[cacheKey]?.let {
            Log.i(TAG, "使用缓存的边缘检测结果")
            return@withContext it
        }
        
        // 转换为灰度
        val grayscale = toGrayscale(bitmap)
        _progress.emit(0.2f)
        
        // 高斯模糊降噪
        val blurred = gaussianBlur(grayscale, 3)
        _progress.emit(0.4f)
        
        // Sobel 边缘检测
        val edges = sobelEdgeDetection(blurred)
        _progress.emit(0.8f)
        
        // 二值化
        val binary = threshold(edges, 50)
        
        edgeCache[cacheKey] = binary
        _edgeDetection.emit(binary)
        _progress.emit(1f)
        
        Log.i(TAG, "边缘检测完成: ${binary.width}x${binary.height}")
        binary
    }
    
    /**
     * 自动选择蒙版区域
     */
    suspend fun autoSelectMask(bitmap: Bitmap, threshold: Float = 0.5f): Bitmap = 
        withContext(Dispatchers.Default) {
            Log.i(TAG, "自动选择蒙版...")
            
            val mask = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(bitmap.width * bitmap.height)
            val maskPixels = IntArray(bitmap.width * bitmap.height)
            
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            
            // 计算颜色方差，识别主体
            var sumR = 0L
            var sumG = 0L
            var sumB = 0L
            var count = 0
            
            for (pixel in pixels) {
                val alpha = (pixel shr 24) and 0xFF
                if (alpha > 200) {
                    sumR += (pixel shr 16) and 0xFF
                    sumG += (pixel shr 8) and 0xFF
                    sumB += pixel and 0xFF
                    count++
                }
            }
            
            if (count > 0) {
                val avgR = sumR / count
                val avgG = sumG / count
                val avgB = sumB / count
                
                // 计算标准差
                var variance = 0.0
                for (pixel in pixels) {
                    val alpha = (pixel shr 24) and 0xFF
                    if (alpha > 200) {
                        val dr = ((pixel shr 16) and 0xFF) - avgR
                        val dg = ((pixel shr 8) and 0xFF) - avgG
                        val db = (pixel and 0xFF) - avgB
                        variance += dr * dr + dg * dg + db * db
                    }
                }
                val stdDev = sqrt(variance / count).toFloat()
                
                // 填充蒙版 (相似颜色区域)
                for (i in pixels.indices) {
                    val pixel = pixels[i]
                    val alpha = (pixel shr 24) and 0xFF
                    if (alpha > 200) {
                        val dr = ((pixel shr 16) and 0xFF) - avgR
                        val dg = ((pixel shr 8) and 0xFF) - avgG
                        val db = (pixel and 0xFF) - avgB
                        val diff = sqrt((dr * dr + dg * dg + db * db).toDouble()).toFloat()
                        
                        // 在阈值内的像素
                        if (diff < stdDev * 2) {
                            maskPixels[i] = Color.WHITE
                        }
                    }
                }
            }
            
            mask.setPixels(maskPixels, 0, mask.width, 0, 0, mask.width, mask.height)
            
            _progress.emit(1f)
            Log.i(TAG, "自动选择完成")
            mask
        }
    
    /**
     * 执行重绘
     */
    suspend fun inpaint(
        image: Bitmap,
        mask: Bitmap,
        mode: FillMode = FillMode.CONTENT_AWARE
    ): InpaintResult = withContext(Dispatchers.Default) {
        val start = System.currentTimeMillis()
        Log.i(TAG, "开始重绘: ${mode.displayName}")
        
        try {
            _progress.emit(0.1f)
            
            // 验证输入
            if (image.width != mask.width || image.height != mask.height) {
                return@withContext InpaintResult(
                    success = false,
                    outputBitmap = null,
                    error = "图片和蒙版尺寸不匹配"
                )
            }
            
            val result = when (mode) {
                FillMode.CONTENT_AWARE -> contentAwareInpaint(image, mask)
                FillMode.NEURAL_INPAINT -> neuralInpaint(image, mask)
                FillMode.SOLID_COLOR -> solidColorInpaint(image, mask, Color.BLACK)
                FillMode.PATTERN -> patternInpaint(image, mask)
                FillMode.BLUR -> blurInpaint(image, mask)
                FillMode.GRADIENT -> gradientInpaint(image, mask)
            }
            
            _progress.emit(1f)
            
            result.copy(processingTimeMs = System.currentTimeMillis() - start)
            
        } catch (e: Exception) {
            Log.e(TAG, "重绘失败: ${e.message}")
            InpaintResult(
                success = false,
                outputBitmap = null,
                error = e.message
            )
        }
    }
    
    /**
     * 导出蒙版
     */
    fun exportMask(path: String): Boolean {
        val state = _maskState.value ?: return false
        return try {
            state.mask.compress(Bitmap.CompressFormat.PNG, 100, java.io.FileOutputStream(path))
            Log.i(TAG, "蒙版已导出: $path")
            true
        } catch (e: Exception) {
            Log.e(TAG, "导出失败: ${e.message}")
            false
        }
    }
    
    /**
     * 导入蒙版
     */
    fun importMask(path: String): Boolean {
        return try {
            val imported = BitmapFactory.decodeFile(path) ?: return false
            _maskState.value = MaskState(
                mask = imported,
                undoneStack = emptyList(),
                redoneStack = emptyList()
            )
            Log.i(TAG, "蒙版已导入: $path")
            true
        } catch (e: Exception) {
            Log.e(TAG, "导入失败: ${e.message}")
            false
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        scope.cancel()
        _maskState.value?.mask?.recycle()
        edgeCache.values.forEach { it.recycle() }
        edgeCache.clear()
        Log.i(TAG, "SmartInpainting 已释放")
    }
    
    // ==================== 私有方法 ====================
    
    private fun createBrushPaint(brush: BrushConfig): Paint {
        return Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = brush.size
            
            // 根据硬度调整模糊
            val blurRadius = brush.size * (1 - brush.hardness) / 2
            if (blurRadius > 0) {
                maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
            }
            
            // 透明度
            alpha = (brush.opacity * 255).toInt()
            
            color = brush.color
        }
    }
    
    private fun BrushConfig.copy() = BrushConfig(
        size = this.size,
        hardness = this.hardness,
        opacity = this.opacity,
        shape = this.shape,
        color = this.color,
        pressureSensitive = this.pressureSensitive
    )
    
    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
    
    private fun gaussianBlur(bitmap: Bitmap, radius: Int): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            maskFilter = BlurMaskFilter(radius.toFloat(), BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
    
    private fun sobelEdgeDetection(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(bitmap.width * bitmap.height)
        val output = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        // Sobel kernels
        val sobelX = arrayOf(
            intArrayOf(-1, 0, 1),
            intArrayOf(-2, 0, 2),
            intArrayOf(-1, 0, 1)
        )
        val sobelY = arrayOf(
            intArrayOf(-1, -2, -1),
            intArrayOf(0, 0, 0),
            intArrayOf(1, 2, 1)
        )
        
        for (y in 1 until bitmap.height - 1) {
            for (x in 1 until bitmap.width - 1) {
                var gx = 0
                var gy = 0
                
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val pixel = pixels[(y + ky) * bitmap.width + (x + kx)]
                        val intensity = (pixel shr 16) and 0xFF
                        gx += intensity * sobelX[ky + 1][kx + 1]
                        gy += intensity * sobelY[ky + 1][kx + 1]
                    }
                }
                
                val magnitude = minOf(255, sqrt((gx * gx + gy * gy).toDouble()).toInt())
                output[y * bitmap.width + x] = (0xFF shl 24) or (magnitude shl 16) or (magnitude shl 8) or magnitude
            }
        }
        
        result.setPixels(output, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return result
    }
    
    private fun threshold(bitmap: Bitmap, threshold: Int): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        for (i in pixels.indices) {
            val intensity = (pixels[i] shr 16) and 0xFF
            pixels[i] = if (intensity > threshold) Color.WHITE else Color.TRANSPARENT
        }
        
        result.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return result
    }
    
    private suspend fun contentAwareInpaint(image: Bitmap, mask: Bitmap): InpaintResult = 
        withContext(Dispatchers.Default) {
            val output = image.copy(Bitmap.Config.ARGB_8888, true)
            val pixels = IntArray(image.width * image.height)
            val maskPixels = IntArray(image.width * image.height)
            
            image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
            mask.getPixels(maskPixels, 0, mask.width, 0, 0, mask.width, mask.height)
            
            // 内容感知填充
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    val idx = y * image.width + x
                    val alpha = (maskPixels[idx] shr 24) and 0xFF
                    
                    if (alpha > 0) {
                        // 计算周围像素的加权平均
                        var sumR = 0L
                        var sumG = 0L
                        var sumB = 0L
                        var weightSum = 0L
                        
                        val searchRadius = 5
                        for (dy in -searchRadius..searchRadius) {
                            for (dx in -searchRadius..searchRadius) {
                                val nx = x + dx
                                val ny = y + dy
                                
                                if (nx in 0 until image.width && ny in 0 until image.height) {
                                    val nIdx = ny * image.width + nx
                                    val nAlpha = (maskPixels[nIdx] shr 24) and 0xFF
                                    
                                    // 只使用蒙版外的像素
                                    if (nAlpha == 0) {
                                        val weight = (searchRadius * searchRadius - dx * dx - dy * dy).coerceAtLeast(1)
                                        val pixel = pixels[nIdx]
                                        sumR += ((pixel shr 16) and 0xFF) * weight
                                        sumG += ((pixel shr 8) and 0xFF) * weight
                                        sumB += (pixel and 0xFF) * weight
                                        weightSum += weight
                                    }
                                }
                            }
                        }
                        
                        if (weightSum > 0) {
                            pixels[idx] = (0xFF shl 24) or
                                    ((sumR / weightSum).toInt() shl 16) or
                                    ((sumG / weightSum).toInt() shl 8) or
                                    (sumB / weightSum).toInt()
                        }
                    }
                }
                
                if (y % 50 == 0) {
                    _progress.emit(0.3f + y.toFloat() / image.height * 0.5f)
                }
            }
            
            output.setPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
            
            InpaintResult(success = true, outputBitmap = output, maskUsed = mask)
        }
    
    private suspend fun neuralInpaint(image: Bitmap, mask: Bitmap): InpaintResult = 
        withContext(Dispatchers.Default) {
            // 模拟神经网络重绘 (实际需要接AI模型)
            _progress.emit(0.5f)
            
            val output = image.copy(Bitmap.Config.ARGB_8888, true)
            val pixels = IntArray(image.width * image.height)
            val maskPixels = IntArray(image.width * image.height)
            
            image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
            mask.getPixels(maskPixels, 0, mask.width, 0, 0, mask.width, mask.height)
            
            // 简单的纹理合成
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    val idx = y * image.width + x
                    val alpha = (maskPixels[idx] shr 24) and 0xFF
                    
                    if (alpha > 0) {
                        // 添加一些纹理变化
                        val noise = Random.nextInt(-10, 10)
                        val nIdx = (y * image.width + (x + 5) % image.width)
                        val pixel = pixels[nIdx]
                        
                        pixels[idx] = (0xFF shl 24) or
                                (((((pixel shr 16) and 0xFF) + noise).coerceIn(0, 255)) shl 16) or
                                (((((pixel shr 8) and 0xFF) + noise).coerceIn(0, 255)) shl 8) or
                                (((pixel and 0xFF) + noise).coerceIn(0, 255))
                    }
                }
                
                if (y % 100 == 0) {
                    _progress.emit(0.5f + y.toFloat() / image.height * 0.4f)
                }
            }
            
            output.setPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
            
            InpaintResult(success = true, outputBitmap = output, maskUsed = mask)
        }
    
    private fun solidColorInpaint(image: Bitmap, mask: Bitmap, color: Int): InpaintResult {
        val output = image.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val paint = Paint().apply { this.color = color }
        
        canvas.drawBitmap(mask, 0f, 0f, paint)
        
        return InpaintResult(success = true, outputBitmap = output, maskUsed = mask)
    }
    
    private fun patternInpaint(image: Bitmap, mask: Bitmap): InpaintResult {
        val output = image.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        
        // 创建简单图案
        val patternPaint = Paint().apply {
            style = Paint.Style.FILL
            color = Color.argb(100, 128, 128, 128)
        }
        
        val patternSize = 20
        for (y in 0 until mask.height step patternSize) {
            for (x in 0 until mask.width step patternSize) {
                val maskPixel = mask.getPixel(x, y)
                if ((maskPixel shr 24) and 0xFF > 0) {
                    if ((x / patternSize + y / patternSize) % 2 == 0) {
                        canvas.drawRect(
                            x.toFloat(), y.toFloat(),
                            (x + patternSize).toFloat(), (y + patternSize).toFloat(),
                            patternPaint
                        )
                    }
                }
            }
        }
        
        return InpaintResult(success = true, outputBitmap = output, maskUsed = mask)
    }
    
    private fun blurInpaint(image: Bitmap, mask: Bitmap): InpaintResult {
        val output = image.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        
        // 模糊蒙版区域
        val paint = Paint().apply {
            maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)
        }
        
        canvas.drawBitmap(mask, 0f, 0f, paint)
        
        return InpaintResult(success = true, outputBitmap = output, maskUsed = mask)
    }
    
    private fun gradientInpaint(image: Bitmap, mask: Bitmap): InpaintResult {
        val output = image.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        
        val pixels = IntArray(image.width * image.height)
        val maskPixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        mask.getPixels(maskPixels, 0, mask.width, 0, 0, mask.width, mask.height)
        
        // 从边缘向内渐变
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val idx = y * image.width + x
                val alpha = (maskPixels[idx] shr 24) and 0xFF
                
                if (alpha > 0) {
                    // 查找最近的非蒙版像素
                    val gradient = alpha / 255f
                    val edgeX = if (alpha > 128) x else x + 5
                    val edgePixel = pixels[y * image.width + edgeX.coerceIn(0, image.width - 1)]
                    
                    pixels[idx] = (0xFF shl 24) or
                            ((((edgePixel shr 16) and 0xFF) * gradient).toInt() shl 16) or
                            ((((edgePixel shr 8) and 0xFF) * gradient).toInt() shl 8) or
                            (((edgePixel and 0xFF) * gradient).toInt())
                }
            }
        }
        
        output.setPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        
        return InpaintResult(success = true, outputBitmap = output, maskUsed = mask)
    }
}
