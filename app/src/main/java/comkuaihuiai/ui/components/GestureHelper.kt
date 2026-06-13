package comkuaihuiai.ui.components

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.*

/**
 * 可绘AI v3.5.0 - 手势操作助手
 * 
 * 👆 功能：
 * ✅ 双指缩放预览
 * ✅ 长按快速操作
 * ✅ 拖拽平移
 * ✅ 摇一摇随机
 * ✅ 滑动手势
 */
class GestureHelper(
    private val context: Context,
    private val listener: GestureListener
) {
    
    companion object {
        const val DOUBLE_TAP_TIMEOUT = 300L
        const val LONG_PRESS_TIMEOUT = 500L
        const val SWIPE_THRESHOLD = 100f
        const val SWIPE_VELOCITY_THRESHOLD = 100f
    }
    
    // ==================== 手势监听器 ====================
    
    interface GestureListener {
        fun onTap(count: Int) {}
        fun onDoubleTap(position: Offset) {}
        fun onLongPress(position: Offset) {}
        fun onScale(scaleFactor: Float, focus: Offset) {}
        fun onPan(translation: Offset) {}
        fun onSwipe(direction: SwipeDirection) {}
        fun onShake() {}
        fun onTwoFingerTap() {}
    }
    
    enum class SwipeDirection {
        UP, DOWN, LEFT, RIGHT
    }
    
    // ==================== 手势检测器 ====================
    
    private var scaleFactor = 1f
    private var lastFocus = Offset.Zero
    private var translation = Offset.Zero
    private var isScaling = false
    private var isPanning = false
    private var lastTapTime = 0L
    private var tapCount = 0
    
    /**
     * 处理触摸事件
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tapCount = 1
                lastTapTime = System.currentTimeMillis()
                translation = Offset.Zero
                isPanning = false
            }
            
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    // 双指按下
                    isScaling = true
                    listener.onTwoFingerTap()
                }
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 2) {
                    // 双指缩放
                    val newScale = calculateScale(event)
                    val focus = calculateFocus(event)
                    
                    if (newScale > 0) {
                        scaleFactor *= newScale
                        scaleFactor = scaleFactor.coerceIn(0.5f, 5f)
                        listener.onScale(scaleFactor, focus)
                    }
                } else if (isPanning) {
                    // 单指拖拽
                    val dx = event.x - lastFocus.x
                    val dy = event.y - lastFocus.y
                    translation = Offset(translation.x + dx, translation.y + dy)
                    listener.onPan(translation)
                }
                
                lastFocus = Offset(event.x, event.y)
            }
            
            MotionEvent.ACTION_UP -> {
                val elapsed = System.currentTimeMillis() - lastTapTime
                
                if (elapsed < DOUBLE_TAP_TIMEOUT && tapCount == 1) {
                    tapCount++
                    // 等待可能的第二次点击
                    return true
                } else if (tapCount == 2 && elapsed < DOUBLE_TAP_TIMEOUT * 2) {
                    listener.onDoubleTap(Offset(event.x, event.y))
                    tapCount = 0
                } else if (event.pointerCount == 1 && !isScaling) {
                    if (elapsed > LONG_PRESS_TIMEOUT) {
                        listener.onLongPress(Offset(event.x, event.y))
                    } else {
                        listener.onTap(tapCount)
                    }
                }
                
                isScaling = false
                isPanning = false
                scaleFactor = 1f
            }
            
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 2) {
                    isScaling = false
                }
            }
        }
        
        return true
    }
    
    private fun calculateScale(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 1f
        
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        val distance = sqrt(dx * dx + dy * dy)
        
        return distance / 200f // 归一化
    }
    
    private fun calculateFocus(event: MotionEvent): Offset {
        if (event.pointerCount < 2) return Offset.Zero
        
        return Offset(
            (event.getX(0) + event.getX(1)) / 2f,
            (event.getY(0) + event.getY(1)) / 2f
        )
    }
    
    /**
     * 检测滑动手势
     */
    fun detectSwipe(startX: Float, startY: Float, endX: Float, endY: Float) {
        val dx = endX - startX
        val dy = endY - startY
        
        if (abs(dx) > abs(dy)) {
            // 水平滑动
            if (abs(dx) > SWIPE_THRESHOLD) {
                listener.onSwipe(if (dx > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT)
            }
        } else {
            // 垂直滑动
            if (abs(dy) > SWIPE_THRESHOLD) {
                listener.onSwipe(if (dy > 0) SwipeDirection.DOWN else SwipeDirection.UP)
            }
        }
    }
}

/**
 * 可组合手势操作
 */
@Composable
fun Modifier.gestureHandler(
    onTap: (Int) -> Unit = {},
    onDoubleTap: (Offset) -> Unit = {},
    onLongPress: (Offset) -> Unit = {},
    onScale: (Float, Offset) -> Unit = { _, _ -> },
    onPan: (Offset) -> Unit = {},
    onSwipe: (GestureHelper.SwipeDirection) -> Unit = {}
): Modifier {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var lastTapPosition by remember { mutableStateOf(Offset.Zero) }
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    
    return this
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { position ->
                    val now = System.currentTimeMillis()
                    if (now - lastTapTime < 300) {
                        tapCount++
                    } else {
                        tapCount = 1
                    }
                    lastTapTime = now
                    lastTapPosition = position
                    
                    if (tapCount == 1) {
                        // 等待可能的第二次点击
                    } else if (tapCount == 2) {
                        onDoubleTap(lastTapPosition)
                        tapCount = 0
                    }
                },
                onDoubleTap = { position ->
                    onDoubleTap(position)
                },
                onLongPress = { position ->
                    onLongPress(position)
                }
            )
        }
        .pointerInput(Unit) {
            detectTransformGestures { centroid, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(0.5f, 5f)
                offset += pan
                
                onScale(scale, centroid)
                onPan(offset)
            }
        }
        .pointerInput(Unit) {
            detectHorizontalDragGestures { _, dragAmount ->
                if (abs(dragAmount) > 100) {
                    onSwipe(if (dragAmount > 0) GestureHelper.SwipeDirection.RIGHT else GestureHelper.SwipeDirection.LEFT)
                }
            }
        }
}

/**
 * 可缩放图片组件
 */
@Composable
fun ZoomableImage(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )
    
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale > 1f) {
                        Offset(
                            (offset.x + pan.x).coerceIn(-500f, 500f),
                            (offset.y + pan.y).coerceIn(-500f, 500f)
                        )
                    } else {
                        Offset.Zero
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2f
                        offset = Offset.Zero
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                translationX = offset.x
                translationY = offset.y
            }
        ) {
            content()
        }
    }
}

/**
 * 摇一摇检测
 */
class ShakeDetector(
    private val context: Context,
    private val onShake: () -> Unit
) {
    companion object {
        private const val SHAKE_THRESHOLD = 12.0f
        private const val SHAKE_TIME_LAPSE = 500L
    }
    
    private var lastShakeTime = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    
    fun onSensorChanged(x: Float, y: Float, z: Float) {
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastShakeTime > SHAKE_TIME_LAPSE) {
            val diffTime = currentTime - lastShakeTime
            lastShakeTime = currentTime
            
            val speed = (x + y + z - lastX - lastY - lastZ) / diffTime * 10000
            
            if (speed > SHAKE_THRESHOLD) {
                onShake()
            }
        }
        
        lastX = x
        lastY = y
        lastZ = z
    }
}

/**
 * 快速操作菜单状态
 */
enum class QuickActionType {
    REGENERATE,      // 重新生成
    SAVE,           // 保存
    SHARE,          // 分享
    FAVORITE,       // 收藏
    DELETE,         // 删除
    DETAIL,         // 详情
    UPSCALE,        // 超分
    STYLE_TRANSFER  // 风格迁移
}

/**
 * 长按弹出菜单数据
 */
data class QuickActionMenuItem(
    val type: QuickActionType,
    val label: String,
    val icon: String,
    val shortcut: String = ""
)

val defaultQuickActions = listOf(
    QuickActionMenuItem(QuickActionType.REGENERATE, "重新生成", "🔄", "长按"),
    QuickActionMenuItem(QuickActionType.SAVE, "保存", "💾", "长按"),
    QuickActionMenuItem(QuickActionType.SHARE, "分享", "📤", "长按"),
    QuickActionMenuItem(QuickActionType.FAVORITE, "收藏", "⭐", "长按"),
    QuickActionMenuItem(QuickActionType.UPSCALE, "超分辨率", "🔍", "长按"),
    QuickActionMenuItem(QuickActionType.STYLE_TRANSFER, "风格迁移", "🎨", "长按")
)
