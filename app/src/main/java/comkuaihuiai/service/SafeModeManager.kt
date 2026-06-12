package comkuaihuiai.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * 快绘AI v3.0.0 安全模式管理器
 * 
 * 支持两种版本：
 * - Safe 版：启用内容过滤和安全检查
 * - Unsafe 版：关闭所有安全限制，允许完全创作自由
 * 
 * 版本检测：
 * - 通过包名判断：com.kehui.ai = Safe, com.kehui.ai.unsafe = Unsafe
 * - 也可以通过 buildConfig 判断
 */
object SafeModeManager {
    
    private const val TAG = "SafeModeManager"
    
    // 内容过滤关键词
    private val blockedKeywords = mutableListOf<String>()
    private val nsfwKeywords = mutableListOf<String>()
    
    private var sharedPrefs: SharedPreferences? = null
    
    // 模式状态
    private var _isSafeModeEnabled: Boolean = false
    private var _isUnsafeVersion: Boolean = false
    private var _isInitialized: Boolean = false
    
    /**
     * 是否启用安全模式
     * Safe 版本默认启用，Unsafe 版本默认关闭
     */
    var isSafeModeEnabled: Boolean
        get() = _isSafeModeEnabled
        private set(value) {
            _isSafeModeEnabled = value
            Log.i(TAG, if (value) "🛡️ 安全模式已启用" else "⚡ 无限制创作模式")
        }
    
    /**
     * 是否为无限制版本
     */
    val isUnsafeVersion: Boolean
        get() = _isUnsafeVersion
    
    /**
     * 是否已初始化
     */
    val isInitialized: Boolean
        get() = _isInitialized
    
    /**
     * 初始化
     * @param context 应用上下文
     * @param forceSafeMode 强制设置安全模式（用于调试）
     */
    fun initialize(context: Context, forceSafeMode: Boolean? = null) {
        if (_isInitialized) {
            Log.d(TAG, "SafeModeManager 已初始化")
            return
        }
        
        sharedPrefs = context.getSharedPreferences("safe_mode_prefs", Context.MODE_PRIVATE)
        
        // 检测版本类型
        val packageName = context.packageName
        _isUnsafeVersion = packageName.endsWith(".unsafe") || packageName.contains("unsafe")
        
        Log.i(TAG, "📦 版本检测: package=$packageName, isUnsafe=$_isUnsafeVersion")
        
        // 确定安全模式状态
        if (forceSafeMode != null) {
            // 强制设置
            isSafeModeEnabled = forceSafeMode
        } else if (_isUnsafeVersion) {
            // Unsafe 版本默认关闭
            isSafeModeEnabled = false
        } else {
            // Safe 版本默认启用
            isSafeModeEnabled = sharedPrefs?.getBoolean("safe_mode_enabled", true) ?: true
        }
        
        _isInitialized = true
    }
    
    /**
     * 启用安全模式
     */
    fun enable() {
        if (_isUnsafeVersion) {
            Log.w(TAG, "⚠️ 无限制版本无法启用安全模式")
            return
        }
        isSafeModeEnabled = true
        savePreference()
    }
    
    /**
     * 禁用安全模式（无限制）
     */
    fun disable() {
        isSafeModeEnabled = false
        savePreference()
    }
    
    /**
     * 切换安全模式
     */
    fun toggle() {
        if (_isUnsafeVersion) {
            Log.w(TAG, "⚠️ 无限制版本无法切换安全模式")
            return
        }
        isSafeModeEnabled = !isSafeModeEnabled
        savePreference()
    }
    
    /**
     * 检查提示词安全性
     * @return SafetyResult.SAFE 或 SafetyResult.UNSAFE
     */
    fun checkPrompt(prompt: String): SafetyResult {
        if (!isSafeModeEnabled) {
            return SafetyResult.SAFE
        }
        
        if (prompt.isBlank()) {
            return SafetyResult.Error("提示词不能为空")
        }
        
        val lowerPrompt = prompt.lowercase()
        
        // 检查阻止关键词
        for (keyword in blockedKeywords) {
            if (lowerPrompt.contains(keyword.lowercase())) {
                Log.w(TAG, "🛡️ 检测到阻止关键词: $keyword")
                return SafetyResult.Unsafe(
                    reason = "内容包含不允许的关键词",
                    matchedKeyword = keyword
                )
            }
        }
        
        // 检查 NSFW 关键词
        for (keyword in nsfwKeywords) {
            if (lowerPrompt.contains(keyword.lowercase())) {
                Log.w(TAG, "🛡️ 检测到 NSFW 关键词: $keyword")
                return SafetyResult.Unsafe(
                    reason = "内容可能不适合工作场所",
                    matchedKeyword = keyword
                )
            }
        }
        
        return SafetyResult.SAFE
    }
    
    /**
     * 检查正负提示词
     */
    fun checkPrompts(positivePrompt: String, negativePrompt: String): SafetyResult {
        val positiveCheck = checkPrompt(positivePrompt)
        if (positiveCheck is SafetyResult.Unsafe) {
            return positiveCheck
        }
        
        val negativeCheck = checkPrompt(negativePrompt)
        if (negativeCheck is SafetyResult.Unsafe) {
            return negativeCheck
        }
        
        return SafetyResult.SAFE
    }
    
    /**
     * 获取安全模式状态描述
     */
    fun getStatusDescription(): String {
        return when {
            _isUnsafeVersion -> "⚡ 无限制创作模式"
            isSafeModeEnabled -> "🛡️ 安全模式已启用"
            else -> "⚡ 无限制创作模式"
        }
    }
    
    /**
     * 获取版本信息
     */
    fun getVersionInfo(): VersionInfo {
        return VersionInfo(
            isUnsafeVersion = _isUnsafeVersion,
            safeModeEnabled = isSafeModeEnabled,
            versionName = if (_isUnsafeVersion) "无限制版" else "安全版",
            versionCode = 300
        )
    }
    
    /**
     * 添加自定义过滤词
     */
    fun addBlockedKeyword(keyword: String) {
        if (!blockedKeywords.contains(keyword)) {
            blockedKeywords.add(keyword)
            Log.i(TAG, "➕ 添加过滤词: $keyword")
        }
    }
    
    /**
     * 获取过滤统计
     */
    fun getFilterStats(): FilterStats {
        return FilterStats(
            totalKeywords = blockedKeywords.size + nsfwKeywords.size,
            safeModeActive = isSafeModeEnabled,
            isUnsafeVersion = _isUnsafeVersion
        )
    }
    
    /**
     * 保存偏好设置
     */
    private fun savePreference() {
        sharedPrefs?.edit()?.putBoolean("safe_mode_enabled", isSafeModeEnabled)?.apply()
    }
    
    /**
     * 重置为默认设置
     */
    fun reset() {
        if (_isUnsafeVersion) {
            isSafeModeEnabled = false
        } else {
            isSafeModeEnabled = true
        }
        savePreference()
        Log.i(TAG, "🔄 安全模式设置已重置")
    }
    
    /**
     * 释放资源
     */
    fun release() {
        sharedPrefs = null
        _isInitialized = false
    }
}

// ==================== 数据类 ====================

/**
 * 安全性检查结果
 */

/**
 * 版本信息
 */
data class VersionInfo(
    val isUnsafeVersion: Boolean,
    val safeModeEnabled: Boolean,
    val versionName: String,
    val versionCode: Int
)

/**
 * 过滤统计
 */
data class FilterStats(
    val totalKeywords: Int,
    val safeModeActive: Boolean,
    val isUnsafeVersion: Boolean
)

/**
 * SafeMode 配置（用于依赖注入）
 */
data class SafeModeConfig(
    val enabled: Boolean,
    val isUnsafeVersion: Boolean,
    val blockedKeywords: List<String> = emptyList()
)
