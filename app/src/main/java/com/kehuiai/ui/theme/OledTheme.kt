package com.kehuiai.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

// ============================================================
// 可绘AI v3.5.0 - 完整主题系统
// ============================================================

// 主品牌色
val KeHuiPrimaryNew = Color(0xFF6366F1)       // 靛蓝
val KeHuiSecondaryNew = Color(0xFF10B981)     // 翠绿
val KeHuiTertiaryNew = Color(0xFFF59E0B)       // 琥珀

// OLED 纯黑专用颜色
val OLEDPureBlack = Color(0xFF000000)
val OLEDSurface = Color(0xFF0A0A0A)
val OLEDSurfaceVariant = Color(0xFF121212)
val OLEDOnSurface = Color(0xFFFAFAFA)
val OLEDPrimary = Color(0xFF6366F1)
val OLEDPrimaryContainer = Color(0xFF1E1B4B)
val OLEDSecondary = Color(0xFF10B981)
val OLEDSecondaryContainer = Color(0xFF064E3B)
val OLEDTertiary = Color(0xFFF59E0B)
val OLEDTertiaryContainer = Color(0xFF78350F)
val OLEDError = Color(0xFFEF4444)
val OLEDErrorContainer = Color(0xFF7F1D1D)
val OLEDBackground = Color(0xFF000000)
val OLEDOnBackground = Color(0xFFFAFAFA)
val OLEDCard = Color(0xFF0D0D0D)

// 渐变色预设
val GradientPrimary = listOf(
    Color(0xFF6366F1),
    Color(0xFF8B5CF6),
    Color(0xFFA855F7)
)

val GradientSecondary = listOf(
    Color(0xFF10B981),
    Color(0xFF14B8A6)
)

val GradientSunset = listOf(
    Color(0xFFF59E0B),
    Color(0xFFEF4444),
    Color(0xFFEC4899)
)

val GradientOcean = listOf(
    Color(0xFF06B6D4),
    Color(0xFF3B82F6)
)

// 主题类型
enum class ThemeMode {
    LIGHT,              // 浅色模式
    DARK,               // 深色模式
    OLED,               // OLED 纯黑模式
    SYSTEM              // 跟随系统
}

// 浅色配色方案
private val LightColorScheme = lightColorScheme(
    primary = KeHuiPrimaryNew,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = KeHuiSecondaryNew,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = KeHuiTertiaryNew,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    error = Color(0xFFEF4444),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF1E293B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFE2E8F0)
)

// 深色配色方案
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF4F46E5),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF064E3B),
    secondaryContainer = Color(0xFF059669),
    onSecondaryContainer = Color(0xFFD1FAE5),
    tertiary = Color(0xFFF59E0B),
    onTertiary = Color(0xFF78350F),
    tertiaryContainer = Color(0xFF92400E),
    onTertiaryContainer = Color(0xFFFEF3C7),
    error = Color(0xFFF87171),
    onError = Color(0xFF7F1D1D),
    errorContainer = Color(0xFF991B1B),
    onErrorContainer = Color(0xFFFEE2E2),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155)
)

// OLED 纯黑配色方案
private val OLEDColorScheme = darkColorScheme(
    primary = OLEDPrimary,
    onPrimary = Color.White,
    primaryContainer = OLEDPrimaryContainer,
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = OLEDSecondary,
    onSecondary = Color.White,
    secondaryContainer = OLEDSecondaryContainer,
    onSecondaryContainer = Color(0xFFD1FAE5),
    tertiary = OLEDTertiary,
    onTertiary = Color.White,
    tertiaryContainer = OLEDTertiaryContainer,
    onTertiaryContainer = Color(0xFFFEF3C7),
    error = OLEDError,
    onError = Color.White,
    errorContainer = OLEDErrorContainer,
    onErrorContainer = Color(0xFFFEE2E2),
    background = OLEDBackground,
    onBackground = OLEDOnBackground,
    surface = OLEDSurface,
    onSurface = OLEDOnSurface,
    surfaceVariant = OLEDSurfaceVariant,
    onSurfaceVariant = Color(0xFFB0B0B0),
    outline = Color(0xFF2A2A2A)
)

// 字体
private val KeHuiTypography = Typography()

@Composable
fun KeHuiAITheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.OLED -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    
    val colorScheme = when {
        themeMode == ThemeMode.OLED -> OLEDColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            
            when (themeMode) {
                ThemeMode.OLED -> {
                    window.statusBarColor = OLEDPureBlack.toArgb()
                    window.navigationBarColor = OLEDPureBlack.toArgb()
                }
                ThemeMode.DARK -> {
                    window.statusBarColor = Color(0xFF0F172A).toArgb()
                    window.navigationBarColor = Color(0xFF0F172A).toArgb()
                }
                else -> {
                    window.statusBarColor = Color(0xFFF8FAFC).toArgb()
                    window.navigationBarColor = Color(0xFFF8FAFC).toArgb()
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = KeHuiTypography,
        content = content
    )
}
