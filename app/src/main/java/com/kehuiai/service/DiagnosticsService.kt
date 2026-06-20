package com.kehuiai.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 可绘AI v3.6.6 诊断日志系统
 * 用于捕获和诊断应用问题
 */
class DiagnosticsService private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "Diagnostics"
        private const val LOG_FILE = "diagnostics.log"
        private const val MAX_LOG_ENTRIES = 500
        
        @Volatile
        private var INSTANCE: DiagnosticsService? = null
        
        fun getInstance(context: Context): DiagnosticsService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DiagnosticsService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val level: LogLevel,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null
    )
    
    enum class LogLevel {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }
    
    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()
    
    private val logFile: File by lazy {
        File(context.filesDir, LOG_FILE)
    }
    
    init {
        loadLogs()
    }
    
    /**
     * 记录日志
     */
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(
            level = level,
            tag = tag,
            message = message,
            throwable = throwable
        )
        
        logQueue.offer(entry)
        
        // 保持日志数量在限制内
        while (logQueue.size > MAX_LOG_ENTRIES) {
            logQueue.poll()
        }
        
        updateState()
        writeToFile(entry)
        
        // 同时输出到 Android Log
        when (level) {
            LogLevel.VERBOSE -> Log.v(tag, message)
            LogLevel.DEBUG -> Log.d(tag, message)
            LogLevel.INFO -> Log.i(tag, message)
            LogLevel.WARN -> Log.w(tag, message)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
        }
    }
    
    fun v(tag: String, message: String) = log(LogLevel.VERBOSE, tag, message)
    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String) = log(LogLevel.WARN, tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.ERROR, tag, message, throwable)
    
    /**
     * 记录异常
     */
    fun logException(tag: String, message: String, exception: Throwable) {
        log(LogLevel.ERROR, tag, message, exception)
    }
    
    /**
     * 生成诊断报告
     */
    fun generateReport(): String {
        val sb = StringBuilder()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        
        sb.appendLine("═══════════════════════════════════════════════════════")
        sb.appendLine("  可绘AI 诊断报告")
        sb.appendLine("  生成时间: ${dateFormat.format(Date())}")
        sb.appendLine("═══════════════════════════════════════════════════════")
        sb.appendLine()
        
        // 系统信息
        sb.appendLine("【系统信息】")
        sb.appendLine("  Android 版本: ${android.os.Build.VERSION.RELEASE}")
        sb.appendLine("  SDK 版本: ${android.os.Build.VERSION.SDK_INT}")
        sb.appendLine("  设备: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        sb.appendLine("  应用版本: ${getAppVersion()}")
        sb.appendLine()
        
        // 存储信息
        sb.appendLine("【存储信息】")
        val filesDir = context.filesDir
        sb.appendLine("  应用数据目录: ${filesDir.absolutePath}")
        sb.appendLine("  可用空间: ${getAvailableSpace()}")
        sb.appendLine()
        
        // 最近日志
        sb.appendLine("【最近日志】(共 ${logQueue.size} 条)")
        val recentLogs = logQueue.toList().takeLast(50)
        recentLogs.forEach { entry ->
            val time = dateFormat.format(Date(entry.timestamp))
            val level = entry.level
            val levelStr = when {
                level == DiagnosticsService.LogLevel.VERBOSE -> "V"
                level == DiagnosticsService.LogLevel.DEBUG -> "D"
                level == DiagnosticsService.LogLevel.INFO -> "I"
                level == DiagnosticsService.LogLevel.WARN -> "W"
                level == DiagnosticsService.LogLevel.ERROR -> "E"
                else -> "?"
            }
            sb.appendLine("  [$time] $levelStr/${entry.tag}: ${entry.message}")
            entry.throwable?.let { t ->
                sb.appendLine("    ${getStackTrace(t)}")
            }
        }
        sb.appendLine()
        
        // 错误统计
        val errorCount = logQueue.count { it.level == DiagnosticsService.LogLevel.ERROR }
        val warnCount = logQueue.count { it.level == DiagnosticsService.LogLevel.WARN }
        sb.appendLine("【错误统计】")
        sb.appendLine("  错误: $errorCount")
        sb.appendLine("  警告: $warnCount")
        sb.appendLine()
        
        sb.appendLine("═══════════════════════════════════════════════════════")
        sb.appendLine("  报告结束")
        sb.appendLine("═══════════════════════════════════════════════════════")
        
        return sb.toString()
    }
    
    /**
     * 导出日志
     */
    fun exportLogs(): File {
        val exportFile = File(context.getExternalFilesDir(null), "kehuiai_logs_${System.currentTimeMillis()}.txt")
        exportFile.writeText(generateReport())
        return exportFile
    }
    
    /**
     * 清除日志
     */
    fun clearLogs() {
        logQueue.clear()
        logFile.delete()
        updateState()
    }
    
    /**
     * 获取错误列表
     */
    fun getErrors(): List<LogEntry> {
        return logQueue.filter { it.level == DiagnosticsService.LogLevel.ERROR }.toList()
    }
    
    /**
     * 获取警告列表
     */
    fun getWarnings(): List<LogEntry> {
        return logQueue.filter { it.level == DiagnosticsService.LogLevel.WARN }.toList()
    }
    
    // 私有方法
    
    private fun updateState() {
        _logs.value = logQueue.toList()
    }
    
    private fun writeToFile(entry: LogEntry) {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val time = dateFormat.format(Date(entry.timestamp))
            val levelStr = when (entry.level) {
                LogLevel.VERBOSE -> "V"
                LogLevel.DEBUG -> "D"
                LogLevel.INFO -> "I"
                LogLevel.WARN -> "W"
                LogLevel.ERROR -> "E"
            }
            val logLine = "[$time] $levelStr/${entry.tag}: ${entry.message}\n"
            
            logFile.appendText(logLine)
            
            // 限制文件大小
            if (logFile.length() > 1024 * 1024) { // 1MB
                trimLogFile()
            }
        } catch (e: Exception) {
            Log.e(TAG, "写入日志失败", e)
        }
    }
    
    private fun trimLogFile() {
        try {
            val lines = logFile.readLines()
            if (lines.size > 1000) {
                logFile.writeText(lines.takeLast(500).joinToString("\n"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "裁剪日志失败", e)
        }
    }
    
    private fun loadLogs() {
        try {
            if (logFile.exists()) {
                logFile.readLines().forEach { line ->
                    parseLogLine(line)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载日志失败", e)
        }
    }
    
    private fun parseLogLine(line: String) {
        // 简单的日志解析
        val regex = """\[(.*?)\] (V|D|I|W|E)/([^:]+): (.*)""".toRegex()
        regex.find(line)?.let { match ->
            val (time, level, tag, message) = match.destructured
            val logLevel = when (level) {
                "V" -> LogLevel.VERBOSE
                "D" -> LogLevel.DEBUG
                "I" -> LogLevel.INFO
                "W" -> LogLevel.WARN
                "E" -> LogLevel.ERROR
                else -> LogLevel.INFO
            }
            logQueue.offer(LogEntry(level = logLevel, tag = tag, message = message))
        }
    }
    
    private fun getStackTrace(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString().split("\n").take(3).joinToString(" | ")
    }
    
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    private fun getAvailableSpace(): String {
        return try {
            val freeBytes = context.filesDir.freeSpace
            when {
                freeBytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", freeBytes / (1024.0 * 1024 * 1024))
                freeBytes >= 1024 * 1024 -> String.format("%.2f MB", freeBytes / (1024.0 * 1024))
                else -> String.format("%.2f KB", freeBytes / 1024.0)
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }
}

// 全局日志记录器
object Logger {
    private var diagnostics: DiagnosticsService? = null
    
    fun init(context: Context) {
        diagnostics = DiagnosticsService.getInstance(context)
    }
    
    fun v(tag: String, message: String) = diagnostics?.v(tag, message) ?: Log.v(tag, message)
    fun d(tag: String, message: String) = diagnostics?.d(tag, message) ?: Log.d(tag, message)
    fun i(tag: String, message: String) = diagnostics?.i(tag, message) ?: Log.i(tag, message)
    fun w(tag: String, message: String) = diagnostics?.w(tag, message) ?: Log.w(tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        diagnostics?.e(tag, message, throwable) ?: Log.e(tag, message, throwable)
    }
    
    fun logException(tag: String, message: String, exception: Throwable) {
        diagnostics?.logException(tag, message, exception) ?: Log.e(tag, message, exception)
    }
}
