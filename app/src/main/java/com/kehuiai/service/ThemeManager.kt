@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST", "DEPRECATION", "USELESS_ELVIS")
package com.kehuiai.service

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

/**
 * 可绘AI v3.5.0 - 主题管理器
 */
class ThemeManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "theme_prefs"
    }
    
    data class ThemeColors(
        val primary: Int = 0xFF6366F1.toInt(),
        val secondary: Int = 0xFF10B981.toInt(),
        val tertiary: Int = 0xFFF59E0B.toInt()
    )
    
    data class Theme(
        val id: String,
        val name: String,
        val colors: ThemeColors,
        val isDark: Boolean = false,
        val isOLED: Boolean = false
    )
    
    enum class AppThemeMode { LIGHT, DARK, OLED, SYSTEM }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private var _currentTheme: Theme = createDefaultTheme()
    private var _appThemeMode: AppThemeMode = loadThemeMode()
    private var _customColors: ThemeColors = loadCustomColors()
    
    fun getCurrentTheme() = _currentTheme
    fun getAppThemeMode() = _appThemeMode
    fun getAppCustomColors() = _customColors
    
    fun setThemeMode(mode: AppThemeMode) {
        _appThemeMode = mode
        prefs.edit().putString("app_theme_mode", mode.name).apply()
        refreshCurrentTheme()
    }
    
    fun setCustomColors(colors: ThemeColors) {
        _customColors = colors
        prefs.edit().apply {
            putInt("custom_primary", colors.primary)
            putInt("custom_secondary", colors.secondary)
            putInt("custom_tertiary", colors.tertiary)
            apply()
        }
        refreshCurrentTheme()
    }
    
    fun getPresetThemes(): List<Pair<String, ThemeColors>> = listOf(
        "默认靛蓝" to ThemeColors(),
        "活力橙" to ThemeColors(0xFFF97316.toInt(), 0xFF22C55E.toInt(), 0xFF3B82F6.toInt()),
        "神秘紫" to ThemeColors(0xFF8B5CF6.toInt(), 0xFFEC4899.toInt(), 0xFF14B8A6.toInt()),
        "海洋蓝" to ThemeColors(0xFF0EA5E9.toInt(), 0xFF06B6D4.toInt(), 0xFFF59E0B.toInt()),
        "森林绿" to ThemeColors(0xFF22C55E.toInt(), 0xFF10B981.toInt(), 0xFFF59E0B.toInt()),
        "玫瑰红" to ThemeColors(0xFFEF4444.toInt(), 0xFFF472B6.toInt(), 0xFFFBBF24.toInt())
    )
    
    fun getAllThemes(): List<Theme> = listOf(
        Theme("light_default", "默认浅色", ThemeColors()),
        Theme("dark_default", "默认深色", ThemeColors(
            adjustBrightness(0xFF6366F1.toInt(), 0.8f),
            adjustBrightness(0xFF10B981.toInt(), 0.8f),
            adjustBrightness(0xFFF59E0B.toInt(), 0.8f)
        ), isDark = true),
        Theme("dark_oled", "OLED纯黑", ThemeColors(), isDark = true, isOLED = true),
        Theme("custom", "自定义", _customColors)
    )
    
    private fun refreshCurrentTheme() {
        _currentTheme = when (_appThemeMode) {
            AppThemeMode.LIGHT -> Theme("light", "浅色模式", _customColors)
            AppThemeMode.DARK -> Theme("dark", "深色模式", ThemeColors(
                adjustBrightness(_customColors.primary, 0.8f),
                adjustBrightness(_customColors.secondary, 0.8f),
                adjustBrightness(_customColors.tertiary, 0.8f)
            ), isDark = true)
            AppThemeMode.OLED -> Theme("oled", "OLED模式", _customColors, isDark = true, isOLED = true)
            AppThemeMode.SYSTEM -> createDefaultTheme()
        }
    }
    
    private fun createDefaultTheme() = Theme("light", "默认", ThemeColors())
    
    private fun loadThemeMode(): AppThemeMode {
        val name = prefs.getString("app_theme_mode", AppThemeMode.SYSTEM.name)
        return try { AppThemeMode.valueOf(name ?: "SYSTEM") } catch (e: Exception) { AppThemeMode.SYSTEM }
    }
    
    private fun loadCustomColors(): ThemeColors {
        val primary = prefs.getInt("custom_primary", 0xFF6366F1.toInt())
        val secondary = prefs.getInt("custom_secondary", 0xFF10B981.toInt())
        val tertiary = prefs.getInt("custom_tertiary", 0xFFF59E0B.toInt())
        return ThemeColors(primary, secondary, tertiary)
    }
    
    private fun adjustBrightness(color: Int, factor: Float): Int {
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}
