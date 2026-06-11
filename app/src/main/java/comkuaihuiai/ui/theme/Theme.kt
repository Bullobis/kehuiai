package comkuaihuiai.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// 可绘AI 主题色
val KeHuiPrimary = Color(0xFF6366F1)      // 主色 - 靛蓝
val KeHuiSecondary = Color(0xFF10B981)    // 次色 - 翠绿
val KeHuiTertiary = Color(0xFFF59E0B)     // 强调色 - 琥珀

// 深色主题
val KeHuiDarkBackground = Color(0xFF0F172A)
val KeHuiDarkSurface = Color(0xFF1E293B)
val KeHuiDarkOnSurface = Color(0xFFE2E8F0)

// 浅色主题
val KeHuiLightBackground = Color(0xFFF8FAFC)
val KeHuiLightSurface = Color(0xFFFFFFFF)
val KeHuiLightOnSurface = Color(0xFF1E293B)

// 状态色
val KeHuiSuccess = Color(0xFF22C55E)
val KeHuiWarning = Color(0xFFF59E0B)
val KeHuiError = Color(0xFFEF4444)

private val DarkColorScheme = darkColorScheme(
    primary = KeHuiPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4F46E5),
    onPrimaryContainer = Color.White,
    secondary = KeHuiSecondary,
    onSecondary = Color.White,
    tertiary = KeHuiTertiary,
    background = KeHuiDarkBackground,
    onBackground = KeHuiDarkOnSurface,
    surface = KeHuiDarkSurface,
    onSurface = KeHuiDarkOnSurface,
    error = KeHuiError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = KeHuiPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = KeHuiSecondary,
    onSecondary = Color.White,
    tertiary = KeHuiTertiary,
    background = KeHuiLightBackground,
    onBackground = KeHuiLightOnSurface,
    surface = KeHuiLightSurface,
    onSurface = KeHuiLightOnSurface,
    error = KeHuiError,
    onError = Color.White
)

@Composable
fun KehuiAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
