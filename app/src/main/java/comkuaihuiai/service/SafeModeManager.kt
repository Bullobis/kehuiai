package comkuaihuiai.service

/**
 * 快绘AI v2.4.0 安全模式管理器
 * 内容过滤和安全检查
 * 
 * 无限制版 - 默认关闭安全过滤
 */
object SafeModeManager {
    
    // 安全模式开关 - 无限制版默认关闭
    var isSafeModeEnabled: Boolean = false
        private set
    
    fun enable() { isSafeModeEnabled = true }
    fun disable() { isSafeModeEnabled = false }
    fun toggle() { isSafeModeEnabled = !isSafeModeEnabled }
    
    fun checkPrompt(prompt: String): SafetyResult {
        // 无限制版不做检查
        return SafetyResult.SAFE
    }
    
    fun checkPrompts(positivePrompt: String, negativePrompt: String): SafetyResult {
        // 无限制版不做检查
        return SafetyResult.SAFE
    }
    
    sealed class SafetyResult {
        data object SAFE : SafetyResult()
        data class UNSAFE(val reason: String, val matchedKeyword: String) : SafetyResult()
    }
    
    fun getStatusDescription(): String {
        return "⚡ 无限制创作模式"
    }
}
