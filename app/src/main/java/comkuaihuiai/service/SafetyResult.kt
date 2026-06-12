package comkuaihuiai.service

/**
 * 安全性检查结果
 */
sealed class SafetyResult {
    /**
     * 内容安全
     */
    data object SAFE : SafetyResult()
    
    /**
     * 不安全内容
     */
    data class Unsafe(
        val reason: String,
        val matchedKeyword: String
    ) : SafetyResult()
    
    /**
     * 错误
     */
    data class Error(
        val message: String
    ) : SafetyResult()
}
