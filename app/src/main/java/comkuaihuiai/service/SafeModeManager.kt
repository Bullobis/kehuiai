package comkuaihuiai.service

/**
 * 快绘AI v2.4.0 安全模式管理器
 * 内容过滤和安全检查
 */
object SafeModeManager {
    
    // 安全模式开关（可通过设置修改）
    var isSafeModeEnabled: Boolean = true
        private set
    
    /**
     * 启用安全模式
     */
    fun enable() {
        isSafeModeEnabled = true
    }
    
    /**
     * 禁用安全模式
     */
    fun disable() {
        isSafeModeEnabled = false
    }
    
    /**
     * 切换安全模式
     */
    fun toggle() {
        isSafeModeEnabled = !isSafeModeEnabled
    }
    
    /**
     * v2.4.0 安全词库
     */
    private val unsafeKeywords = listOf(
        // 暴力相关
        "gore", "blood", "violence", "weapon", "gun", "knife", "murder",
        "血腥", "暴力", "武器", "枪", "刀", "谋杀",
        
        // 不当内容
        "nude", "naked", "nsfw", "explicit", "porn",
        "色情", "裸体", "成人",
        
        // 歧视相关
        "racist", "discrimination", "hate",
        "歧视", "仇恨"
    )
    
    /**
     * v2.4.0 检查提示词是否安全
     */
    fun checkPrompt(prompt: String): SafetyResult {
        if (!isSafeModeEnabled) {
            return SafetyResult.SAFE
        }
        
        val lowerPrompt = prompt.lowercase()
        
        for (keyword in unsafeKeywords) {
            if (lowerPrompt.contains(keyword)) {
                return SafetyResult.UNSAFE(
                    reason = "检测到不当内容关键词",
                    matchedKeyword = keyword
                )
            }
        }
        
        return SafetyResult.SAFE
    }
    
    /**
     * v2.4.0 批量检查多个提示词
     */
    fun checkPrompts(positivePrompt: String, negativePrompt: String): SafetyResult {
        val positiveCheck = checkPrompt(positivePrompt)
        if (positiveCheck is SafetyResult.UNSAFE) {
            return positiveCheck
        }
        
        return checkPrompt(negativePrompt)
    }
    
    /**
     * v2.4.0 安全检查结果
     */
    sealed class SafetyResult {
        data object SAFE : SafetyResult()
        data class UNSAFE(
            val reason: String,
            val matchedKeyword: String
        ) : SafetyResult()
    }
    
    /**
     * v2.4.0 获取安全模式状态描述
     */
    fun getStatusDescription(): String {
        return if (isSafeModeEnabled) {
            "🛡️ 安全模式已启用"
        } else {
            "⚠️ 安全模式已关闭"
        }
    }
}
