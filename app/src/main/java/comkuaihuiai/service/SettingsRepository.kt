package comkuaihuiai.service

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.*

/**
 * 可绘AI v3.5.0 - 设置存储库
 */
class SettingsRepository(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "kehuiai_settings"
    }
    
    data class Settings(
        // 生成设置
        var defaultSteps: Int = 20,
        var defaultGuidance: Float = 7f,
        var defaultWidth: Int = 512,
        var defaultHeight: Int = 512,
        var defaultModel: String = "sd15",
        
        // UI设置
        var themeMode: String = "SYSTEM",
        var showPreview: Boolean = true,
        var animationEnabled: Boolean = true,
        var hapticFeedback: Boolean = true,
        
        // 性能设置
        var batchSize: Int = 1,
        var maxCacheSize: Int = 500,
        var autoPreload: Boolean = true,
        
        // 社区设置
        var saveToHistory: Boolean = true,
        var autoSaveFavorites: Boolean = false,
        var showTips: Boolean = true
    )
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    
    private fun loadSettings(): Settings = Settings().apply {
        defaultSteps = prefs.getInt("default_steps", 20)
        defaultGuidance = prefs.getFloat("default_guidance", 7f)
        defaultWidth = prefs.getInt("default_width", 512)
        defaultHeight = prefs.getInt("default_height", 512)
        defaultModel = prefs.getString("default_model", "sd15") ?: "sd15"
        themeMode = prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM"
        showPreview = prefs.getBoolean("show_preview", true)
        animationEnabled = prefs.getBoolean("animation_enabled", true)
        hapticFeedback = prefs.getBoolean("haptic_feedback", true)
        batchSize = prefs.getInt("batch_size", 1)
        maxCacheSize = prefs.getInt("max_cache_size", 500)
        autoPreload = prefs.getBoolean("auto_preload", true)
        saveToHistory = prefs.getBoolean("save_to_history", true)
        autoSaveFavorites = prefs.getBoolean("auto_save_favorites", false)
        showTips = prefs.getBoolean("show_tips", true)
    }
    
    fun updateSettings(update: Settings.() -> Unit) {
        val current = _settings.value
        val newSettings = Settings().also { s ->
            s.defaultSteps = current.defaultSteps
            s.defaultGuidance = current.defaultGuidance
            s.defaultWidth = current.defaultWidth
            s.defaultHeight = current.defaultHeight
            s.defaultModel = current.defaultModel
            s.themeMode = current.themeMode
            s.showPreview = current.showPreview
            s.animationEnabled = current.animationEnabled
            s.hapticFeedback = current.hapticFeedback
            s.batchSize = current.batchSize
            s.maxCacheSize = current.maxCacheSize
            s.autoPreload = current.autoPreload
            s.saveToHistory = current.saveToHistory
            s.autoSaveFavorites = current.autoSaveFavorites
            s.showTips = current.showTips
            update(s)
        }
        _settings.value = newSettings
        saveSettings(newSettings)
    }
    
    private fun saveSettings(settings: Settings) {
        prefs.edit().apply {
            putInt("default_steps", settings.defaultSteps)
            putFloat("default_guidance", settings.defaultGuidance)
            putInt("default_width", settings.defaultWidth)
            putInt("default_height", settings.defaultHeight)
            putString("default_model", settings.defaultModel)
            putString("theme_mode", settings.themeMode)
            putBoolean("show_preview", settings.showPreview)
            putBoolean("animation_enabled", settings.animationEnabled)
            putBoolean("haptic_feedback", settings.hapticFeedback)
            putInt("batch_size", settings.batchSize)
            putInt("max_cache_size", settings.maxCacheSize)
            putBoolean("auto_preload", settings.autoPreload)
            putBoolean("save_to_history", settings.saveToHistory)
            putBoolean("auto_save_favorites", settings.autoSaveFavorites)
            putBoolean("show_tips", settings.showTips)
            apply()
        }
    }
    
    fun exportSettings(): String {
        val s = _settings.value
        return """
            {
                "defaultSteps": ${s.defaultSteps},
                "defaultGuidance": ${s.defaultGuidance},
                "defaultWidth": ${s.defaultWidth},
                "defaultHeight": ${s.defaultHeight},
                "defaultModel": "${s.defaultModel}",
                "themeMode": "${s.themeMode}",
                "showPreview": ${s.showPreview},
                "animationEnabled": ${s.animationEnabled},
                "hapticFeedback": ${s.hapticFeedback},
                "batchSize": ${s.batchSize},
                "maxCacheSize": ${s.maxCacheSize},
                "autoPreload": ${s.autoPreload},
                "saveToHistory": ${s.saveToHistory},
                "autoSaveFavorites": ${s.autoSaveFavorites},
                "showTips": ${s.showTips}
            }
        """.trimIndent()
    }
    
    fun importSettings(json: String) {
        try {
            val obj = org.json.JSONObject(json)
            updateSettings {
                defaultSteps = obj.optInt("defaultSteps", 20)
                defaultGuidance = obj.optDouble("defaultGuidance", 7.0).toFloat()
                defaultWidth = obj.optInt("defaultWidth", 512)
                defaultHeight = obj.optInt("defaultHeight", 512)
                defaultModel = obj.optString("defaultModel", "sd15")
                themeMode = obj.optString("themeMode", "SYSTEM")
                showPreview = obj.optBoolean("showPreview", true)
                animationEnabled = obj.optBoolean("animationEnabled", true)
                hapticFeedback = obj.optBoolean("hapticFeedback", true)
                batchSize = obj.optInt("batchSize", 1)
                maxCacheSize = obj.optInt("maxCacheSize", 500)
                autoPreload = obj.optBoolean("autoPreload", true)
                saveToHistory = obj.optBoolean("saveToHistory", true)
                autoSaveFavorites = obj.optBoolean("autoSaveFavorites", false)
                showTips = obj.optBoolean("showTips", true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun resetToDefaults() {
        _settings.value = Settings()
        saveSettings(_settings.value)
    }
}
