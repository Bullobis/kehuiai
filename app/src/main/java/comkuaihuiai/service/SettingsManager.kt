package comkuaihuiai.service

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 可绘AI v3.0 设置管理器
 */
class SettingsManager private constructor(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "kehuiai_settings"
        
        @Volatile
        private var INSTANCE: SettingsManager? = null
        
        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // 主题设置
    private val _darkMode = MutableStateFlow(prefs.getInt("dark_mode", 0))
    val darkMode: StateFlow<Int> = _darkMode.asStateFlow()
    
    private val _dynamicColor = MutableStateFlow(prefs.getBoolean("dynamic_color", true))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()
    
    // 安全模式
    private val _safeMode = MutableStateFlow(prefs.getBoolean("safe_mode", true))
    val safeMode: StateFlow<Boolean> = _safeMode.asStateFlow()
    
    // 网络设置
    private val _listenOnAllAddresses = MutableStateFlow(prefs.getBoolean("listen_all", false))
    val listenOnAllAddresses: StateFlow<Boolean> = _listenOnAllAddresses.asStateFlow()
    
    // Tag 设置
    private val _tagAutocomplete = MutableStateFlow(prefs.getBoolean("tag_autocomplete", true))
    val tagAutocomplete: StateFlow<Boolean> = _tagAutocomplete.asStateFlow()
    
    // SDXL 低内存模式
    private val _sdxlLowRam = MutableStateFlow(prefs.getBoolean("sdxl_lowram", false))
    val sdxlLowRam: StateFlow<Boolean> = _sdxlLowRam.asStateFlow()
    
    // ONNX 提供者
    private val _onnxProvider = MutableStateFlow(prefs.getString("onnx_provider", "CPU") ?: "CPU")
    val onnxProvider: StateFlow<String> = _onnxProvider.asStateFlow()
    
    fun setDarkMode(mode: Int) {
        prefs.edit { putInt("dark_mode", mode) }
        _darkMode.value = mode
    }
    
    fun setDynamicColor(enabled: Boolean) {
        prefs.edit { putBoolean("dynamic_color", enabled) }
        _dynamicColor.value = enabled
    }
    
    fun setSafeMode(enabled: Boolean) {
        prefs.edit { putBoolean("safe_mode", enabled) }
        _safeMode.value = enabled
    }
    
    fun setListenOnAllAddresses(enabled: Boolean) {
        prefs.edit { putBoolean("listen_all", enabled) }
        _listenOnAllAddresses.value = enabled
    }
    
    fun setTagAutocomplete(enabled: Boolean) {
        prefs.edit { putBoolean("tag_autocomplete", enabled) }
        _tagAutocomplete.value = enabled
    }
    
    fun setSdxlLowRam(enabled: Boolean) {
        prefs.edit { putBoolean("sdxl_lowram", enabled) }
        _sdxlLowRam.value = enabled
    }
    
    fun setOnnxProvider(provider: String) {
        prefs.edit { putString("onnx_provider", provider) }
        _onnxProvider.value = provider
    }
    
    /**
     * 获取存储大小
     */
    fun getStorageSize(context: Context): Long {
        return context.filesDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }
    
    /**
     * 获取缓存大小
     */
    fun getCacheSize(context: Context): Long {
        return context.cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }
    
    /**
     * 清除缓存
     */
    fun clearCache(context: Context) {
        context.cacheDir.deleteRecursively()
        context.cacheDir.mkdirs()
    }
}
