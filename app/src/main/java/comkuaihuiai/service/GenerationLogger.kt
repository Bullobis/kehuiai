package comkuaihuiai.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * 可绘AI v3.6 - 详细生成日志系统
 *
 * 核心功能：
 * 1. 记录每次生成的完整参数和结果
 * 2. 生成性能统计（耗时、内存、成功率）
 * 3. 提示词版本历史追踪
 * 4. 收藏和标记系统
 * 5. 导出/导入功能
 * 6. 自动清理旧日志
 */
class GenerationLogger(private val context: Context) {

    companion object {
        private const val TAG = "GenerationLogger"
        private const val LOG_DIR = "logs"
        private const val LOG_FILE = "generation_log.json"
        private const val STATS_FILE = "stats.json"
        private const val MAX_LOG_ENTRIES = 5000  // 最多保留5000条记录
        private const val MAX_STATS_ENTRIES = 100  // 性能统计保留条数

        @Volatile
        private var INSTANCE: GenerationLogger? = null

        fun getInstance(context: Context): GenerationLogger {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GenerationLogger(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // ===== 日志条目 =====
    data class LogEntry(
        val id: String,
        val timestamp: Long,
        val params: GenerationParamsLog,
        val result: ResultLog,
        val performance: PerformanceLog,
        val tags: List<String> = emptyList(),
        val isFavorite: Boolean = false,
        val notes: String = ""
    )

    data class GenerationParamsLog(
        val positivePrompt: String,
        val negativePrompt: String,
        val width: Int,
        val height: Int,
        val steps: Int,
        val guidanceScale: Float,
        val seed: Long,
        val scheduler: String,
        val modelId: String,
        val modelName: String,
        val baseModel: String,
        val loras: List<String> = emptyList(),
        val embeddings: List<String> = emptyList(),
        val enableHiresFix: Boolean,
        val hiresScale: Float,
        val enableControlNet: Boolean,
        val controlNetType: String,
        val batchSize: Int,
        val totalImages: Int
    )

    data class ResultLog(
        val success: Boolean,
        val outputPaths: List<String>,
        val outputHashes: List<String>,
        val errorMessage: String? = null,
        val isPartialSuccess: Boolean = false
    )

    data class PerformanceLog(
        val totalTimeMs: Long,
        val inferenceTimeMs: Long,
        val preprocessTimeMs: Long,
        val postprocessTimeMs: Long,
        val memoryUsedMb: Long,
        val peakMemoryMb: Long,
        val gpuUtilization: Float,
        val npuUtilization: Float,
        val temperature: Float,
        val thermalTier: String,
        val deviceModel: String,
        val androidVersion: String
    )

    // ===== 统计摘要 =====
    data class StatsSummary(
        val totalGenerations: Int = 0,
        val successfulGenerations: Int = 0,
        val failedGenerations: Int = 0,
        val averageTimeMs: Long = 0,
        val averageMemoryMb: Long = 0,
        val averageSteps: Float = 0f,
        val modelUsageCount: Map<String, Int> = emptyMap(),
        val schedulerUsageCount: Map<String, Int> = emptyMap(),
        val baseModelUsageCount: Map<String, Int> = emptyMap(),
        val hourlyDistribution: Map<Int, Int> = emptyMap(),  // 小时 -> 数量
        val dailyDistribution: Map<String, Int> = emptyMap(),  // 日期 -> 数量
        val successRate: Float = 0f,
        val totalImagesGenerated: Long = 0,
        val totalTimeSpentMs: Long = 0
    )

    // ===== 状态 =====
    private val logDir = File(context.filesDir, LOG_DIR).apply { if (!exists()) mkdirs() }
    private val logFile = File(logDir, LOG_FILE)
    private val statsFile = File(logDir, STATS_FILE)

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val _stats = MutableStateFlow(StatsSummary())
    val stats: StateFlow<StatsSummary> = _stats.asStateFlow()

    private val _recentEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    val recentEntries: StateFlow<List<LogEntry>> = _recentEntries.asStateFlow()

    private val _favorites = MutableStateFlow<List<LogEntry>>(emptyList())
    val favorites: StateFlow<List<LogEntry>> = _favorites.asStateFlow()

    private val logsCache = ConcurrentHashMap<String, LogEntry>()

    init {
        loadLogs()
        loadStats()
    }

    // ===== 主要接口 =====

    /**
     * 记录一次生成
     */
    suspend fun logGeneration(
        params: comkuaihuiai.data.model.GenerationParams,
        success: Boolean,
        outputPaths: List<String>,
        performance: PerformanceMetrics,
        error: String? = null
    ) = withContext(Dispatchers.IO) {
        val entry = buildLogEntry(params, success, outputPaths, performance, error)
        logsCache[entry.id] = entry

        // 保存到文件
        appendToLogFile(entry)

        // 更新内存缓存
        val current = _entries.value.toMutableList()
        current.add(0, entry)
        if (current.size > MAX_LOG_ENTRIES) {
            current.removeAt(current.lastIndex)
        }
        _entries.value = current

        // 更新最近记录
        _recentEntries.value = current.take(50)

        // 更新收藏
        _favorites.value = current.filter { it.isFavorite }

        // 更新统计
        updateStats(entry)

        Log.i(TAG, "记录生成: ${entry.id}, success=$success, time=${performance.totalTimeMs}ms")
    }

    /**
     * 记录推理性能指标
     */
    data class PerformanceMetrics(
        val totalTimeMs: Long,
        val inferenceTimeMs: Long,
        val preprocessTimeMs: Long,
        val postprocessTimeMs: Long,
        val memoryUsedMb: Long,
        val peakMemoryMb: Long,
        val gpuUtilization: Float,
        val npuUtilization: Float,
        val temperature: Float
    )

    /**
     * 标记/取消标记收藏
     */
    fun toggleFavorite(entryId: String): Boolean {
        val entry = logsCache[entryId] ?: return false
        val updated = entry.copy(isFavorite = !entry.isFavorite)
        logsCache[entryId] = updated

        // 更新列表
        _entries.value = _entries.value.map {
            if (it.id == entryId) updated else it
        }
        _recentEntries.value = _entries.value.take(50)
        _favorites.value = _entries.value.filter { it.isFavorite }

        // 保存更新
        saveAllLogs()

        return updated.isFavorite
    }

    /**
     * 添加标签
     */
    fun addTag(entryId: String, tag: String) {
        val entry = logsCache[entryId] ?: return
        if (entry.tags.contains(tag)) return
        val updated = entry.copy(tags = entry.tags + tag)
        logsCache[entryId] = updated

        _entries.value = _entries.value.map {
            if (it.id == entryId) updated else it
        }
        _recentEntries.value = _entries.value.take(50)
        _favorites.value = _entries.value.filter { it.isFavorite }

        saveAllLogs()
    }

    /**
     * 添加备注
     */
    fun setNotes(entryId: String, notes: String) {
        val entry = logsCache[entryId] ?: return
        val updated = entry.copy(notes = notes)
        logsCache[entryId] = updated

        _entries.value = _entries.value.map {
            if (it.id == entryId) updated else it
        }

        saveAllLogs()
    }

    /**
     * 搜索日志
     */
    fun search(query: String): List<LogEntry> {
        val lowerQuery = query.lowercase()
        return _entries.value.filter { entry ->
            entry.params.positivePrompt.lowercase().contains(lowerQuery) ||
            entry.params.negativePrompt.lowercase().contains(lowerQuery) ||
            entry.params.modelName.lowercase().contains(lowerQuery) ||
            entry.tags.any { it.lowercase().contains(lowerQuery) } ||
            entry.notes.lowercase().contains(lowerQuery) ||
            entry.params.seed.toString().contains(query)
        }
    }

    /**
     * 按模型筛选
     */
    fun filterByModel(modelId: String): List<LogEntry> {
        return _entries.value.filter { it.params.modelId == modelId }
    }

    /**
     * 按日期筛选
     */
    fun filterByDateRange(startTime: Long, endTime: Long): List<LogEntry> {
        return _entries.value.filter { it.timestamp in startTime..endTime }
    }

    /**
     * 按标签筛选
     */
    fun filterByTags(tags: List<String>): List<LogEntry> {
        return _entries.value.filter { entry ->
            tags.all { tag -> entry.tags.contains(tag) }
        }
    }

    /**
     * 获取成功率的趋势
     */
    fun getSuccessRateTrend(days: Int = 7): List<Pair<String, Float>> {
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L
        val result = mutableListOf<Pair<String, Float>>()
        val dateFormat = SimpleDateFormat("MM-dd", Locale.getDefault())

        for (i in days - 1 downTo 0) {
            val dayStart = now - (i * dayMs)
            val dayEnd = dayStart + dayMs
            val dayEntries = _entries.value.filter { it.timestamp in dayStart until dayEnd }

            val successRate = if (dayEntries.isNotEmpty()) {
                dayEntries.count { it.result.success }.toFloat() / dayEntries.size
            } else 0f

            result.add(dateFormat.format(Date(dayStart)) to successRate)
        }

        return result
    }

    /**
     * 获取平均生成时间趋势
     */
    fun getTimeTrend(days: Int = 7): List<Pair<String, Long>> {
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L
        val result = mutableListOf<Pair<String, Long>>()
        val dateFormat = SimpleDateFormat("MM-dd", Locale.getDefault())

        for (i in days - 1 downTo 0) {
            val dayStart = now - (i * dayMs)
            val dayEnd = dayStart + dayMs
            val dayEntries = _entries.value.filter { it.timestamp in dayStart until dayEnd && it.result.success }

            val avgTime = if (dayEntries.isNotEmpty()) {
                dayEntries.map { it.performance.totalTimeMs }.average().toLong()
            } else 0L

            result.add(dateFormat.format(Date(dayStart)) to avgTime)
        }

        return result
    }

    /**
     * 获取提示词历史
     */
    fun getPromptHistory(prompt: String, limit: Int = 10): List<LogEntry> {
        val lowerPrompt = prompt.lowercase()
        return _entries.value.filter {
            it.params.positivePrompt.lowercase().contains(lowerPrompt) ||
            lowerPrompt.contains(it.params.positivePrompt.lowercase())
        }.take(limit)
    }

    /**
     * 获取相似生成（用于参考）
     */
    fun findSimilarGenerations(prompt: String, limit: Int = 5): List<LogEntry> {
        val words = prompt.lowercase().split(Regex("[\\s,.，、]")).filter { it.length > 3 }
        if (words.isEmpty()) return emptyList()

        return _entries.value
            .map { entry ->
                val entryWords = entry.params.positivePrompt.lowercase().split(Regex("[\\s,.，、]"))
                val similarity = words.count { word -> entryWords.any { it.contains(word) || word.contains(it) } }.toFloat() / words.size
                entry to similarity
            }
            .filter { it.second > 0.3f }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    /**
     * 导出日志（JSON格式）
     */
    fun exportLogs(): String {
        val array = JSONArray()
        _entries.value.forEach { entry ->
            array.put(entryToJson(entry))
        }
        return array.toString(2)
    }

    /**
     * 导入日志
     */
    suspend fun importLogs(json: String): Int = withContext(Dispatchers.IO) {
        try {
            val array = JSONArray(json)
            var count = 0
            for (i in 0 until array.length()) {
                val entry = jsonToEntry(array.getJSONObject(i))
                if (entry != null && !logsCache.containsKey(entry.id)) {
                    logsCache[entry.id] = entry
                    count++
                }
            }
            saveAllLogs()
            reloadLogs()
            count
        } catch (e: Exception) {
            Log.e(TAG, "导入失败: ${e.message}")
            0
        }
    }

    /**
     * 清理旧日志
     */
    suspend fun cleanup(keepDays: Int = 30) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - (keepDays * 24L * 60L * 60L * 1000L)
        val current = _entries.value.filter { it.timestamp >= cutoff || it.isFavorite }
        _entries.value = current
        _recentEntries.value = current.take(50)
        _favorites.value = current.filter { it.isFavorite }
        logsCache.clear()
        current.forEach { logsCache[it.id] = it }
        saveAllLogs()
        Log.i(TAG, "清理完成，保留 ${current.size} 条记录")
    }

    /**
     * 获取日志文件大小
     */
    fun getLogFileSize(): Long {
        return logFile.length()
    }

    /**
     * 删除单条日志
     */
    fun deleteEntry(entryId: String) {
        logsCache.remove(entryId)
        _entries.value = _entries.value.filter { it.id != entryId }
        _recentEntries.value = _entries.value.take(50)
        _favorites.value = _entries.value.filter { it.isFavorite }
        saveAllLogs()
    }

    /**
     * 获取按模型分组的统计
     */
    fun getModelStats(): Map<String, Any> {
        val entries = _entries.value
        val modelStats = mutableMapOf<String, MutableMap<String, Any>>()

        entries.groupBy { it.params.modelId }.forEach { (modelId, modelEntries) ->
            val successCount = modelEntries.count { it.result.success }
            val totalTime = modelEntries.filter { it.result.success }.sumOf { it.performance.totalTimeMs }
            val avgTime = if (successCount > 0) totalTime / successCount else 0L

            modelStats[modelId] = mutableMapOf<String, Any>(
                "modelName" to modelEntries.first().params.modelName as Any,
                "totalCount" to modelEntries.size as Any,
                "successCount" to successCount as Any,
                "successRate" to (if (modelEntries.isNotEmpty()) successCount.toFloat() / modelEntries.size else 0f) as Any,
                "averageTimeMs" to avgTime as Any,
                "averageSteps" to modelEntries.map { it.params.steps }.average() as Any,
                "lastUsed" to (modelEntries.maxOfOrNull { it.timestamp } ?: 0L) as Any
            )
        }

        return modelStats
    }

    // ===== 私有方法 =====

    private fun buildLogEntry(
        params: comkuaihuiai.data.model.GenerationParams,
        success: Boolean,
        outputPaths: List<String>,
        performance: PerformanceMetrics,
        error: String?
    ): LogEntry {
        val timestamp = System.currentTimeMillis()
        val id = generateId(timestamp, params.seed)

        return LogEntry(
            id = id,
            timestamp = timestamp,
            params = GenerationParamsLog(
                positivePrompt = params.positivePrompt,
                negativePrompt = params.negativePrompt,
                width = params.width,
                height = params.height,
                steps = params.steps,
                guidanceScale = params.guidanceScale,
                seed = params.seed,
                scheduler = params.scheduler.name,
                modelId = params.baseModel.name,
                modelName = params.baseModel.name,
                baseModel = params.baseModel.name,
                loras = params.selectedLoras.map { it.name },
                embeddings = params.selectedEmbeddings,
                enableHiresFix = params.enableHiresFix,
                hiresScale = params.hiresScale,
                enableControlNet = params.enableControlNet,
                controlNetType = params.controlNetType.name,
                batchSize = params.batchSize,
                totalImages = params.batchSize
            ),
            result = ResultLog(
                success = success,
                outputPaths = outputPaths,
                outputHashes = outputPaths.map { computeFileHash(it) },
                errorMessage = error,
                isPartialSuccess = success && outputPaths.isNotEmpty() && !success
            ),
            performance = PerformanceLog(
                totalTimeMs = performance.totalTimeMs,
                inferenceTimeMs = performance.inferenceTimeMs,
                preprocessTimeMs = performance.preprocessTimeMs,
                postprocessTimeMs = performance.postprocessTimeMs,
                memoryUsedMb = performance.memoryUsedMb,
                peakMemoryMb = performance.peakMemoryMb,
                gpuUtilization = performance.gpuUtilization,
                npuUtilization = performance.npuUtilization,
                temperature = performance.temperature,
                thermalTier = "NORMAL",
                deviceModel = android.os.Build.MODEL,
                androidVersion = android.os.Build.VERSION.RELEASE
            )
        )
    }

    private fun generateId(timestamp: Long, seed: Long): String {
        val data = "$timestamp-$seed-${(Math.random() * 10000).toInt()}"
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(data.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(12)
    }

    private fun computeFileHash(path: String): String {
        return try {
            val file = File(path)
            if (file.exists()) {
                val md = MessageDigest.getInstance("MD5")
                val fis = java.io.FileInputStream(file)
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    md.update(buffer, 0, bytesRead)
                }
                fis.close()
                md.digest().joinToString("") { "%02x".format(it) }
            } else ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun appendToLogFile(entry: LogEntry) {
        try {
            val json = entryToJson(entry).toString()
            logFile.appendText(json + "\n")
        } catch (e: Exception) {
            Log.e(TAG, "写入日志失败: ${e.message}")
        }
    }

    private fun loadLogs() {
        try {
            if (!logFile.exists()) return
            val lines = logFile.readLines().takeLast(MAX_LOG_ENTRIES)
            val entries = lines.mapNotNull { line ->
                try {
                    JSONObject(line).let { jsonToEntry(it) }
                } catch (e: Exception) {
                    null
                }
            }.reversed()

            logsCache.clear()
            entries.forEach { logsCache[it.id] = it }

            _entries.value = entries
            _recentEntries.value = entries.take(50)
            _favorites.value = entries.filter { it.isFavorite }
        } catch (e: Exception) {
            Log.e(TAG, "加载日志失败: ${e.message}")
        }
    }

    private fun saveAllLogs() {
        try {
            logFile.writeText(_entries.value.joinToString("\n") { entryToJson(it).toString() })
        } catch (e: Exception) {
            Log.e(TAG, "保存日志失败: ${e.message}")
        }
    }

    private fun reloadLogs() {
        logsCache.clear()
        _entries.value.forEach { logsCache[it.id] = it }
        _recentEntries.value = _entries.value.take(50)
        _favorites.value = _entries.value.filter { it.isFavorite }
    }

    private fun updateStats(entry: LogEntry) {
        val current = _stats.value
        val allEntries = _entries.value

        // 计算模型使用次数
        val modelCount = mutableMapOf<String, Int>()
        val schedulerCount = mutableMapOf<String, Int>()
        val baseModelCount = mutableMapOf<String, Int>()
        val hourlyDist = mutableMapOf<Int, Int>()
        val dailyDist = mutableMapOf<String, Int>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        allEntries.forEach { e ->
            modelCount[e.params.modelId] = (modelCount[e.params.modelId] ?: 0) + 1
            schedulerCount[e.params.scheduler] = (schedulerCount[e.params.scheduler] ?: 0) + 1
            baseModelCount[e.params.baseModel] = (baseModelCount[e.params.baseModel] ?: 0) + 1

            val hour = java.util.Calendar.getInstance().apply { timeInMillis = e.timestamp }.get(java.util.Calendar.HOUR_OF_DAY)
            hourlyDist[hour] = (hourlyDist[hour] ?: 0) + 1

            val date = dateFormat.format(Date(e.timestamp))
            dailyDist[date] = (dailyDist[date] ?: 0) + 1
        }

        val successEntries = allEntries.filter { it.result.success }
        val avgTime = if (successEntries.isNotEmpty()) {
            successEntries.map { it.performance.totalTimeMs }.average().toLong()
        } else 0L
        val avgMem = if (allEntries.isNotEmpty()) {
            allEntries.map { it.performance.memoryUsedMb }.average().toLong()
        } else 0L
        val avgSteps = if (allEntries.isNotEmpty()) {
            allEntries.map { it.params.steps }.average().toFloat()
        } else 0f

        _stats.value = StatsSummary(
            totalGenerations = allEntries.size,
            successfulGenerations = allEntries.count { it.result.success },
            failedGenerations = allEntries.count { !it.result.success },
            averageTimeMs = avgTime,
            averageMemoryMb = avgMem,
            averageSteps = avgSteps,
            modelUsageCount = modelCount,
            schedulerUsageCount = schedulerCount,
            baseModelUsageCount = baseModelCount,
            hourlyDistribution = hourlyDist,
            dailyDistribution = dailyDist,
            successRate = if (allEntries.isNotEmpty()) allEntries.count { it.result.success }.toFloat() / allEntries.size else 0f,
            totalImagesGenerated = allEntries.sumOf { it.result.outputPaths.size }.toLong(),
            totalTimeSpentMs = allEntries.sumOf { it.performance.totalTimeMs }
        )
    }

    private fun loadStats() {
        try {
            if (!statsFile.exists()) return
            // 统计从 entries 实时计算
            if (_entries.value.isNotEmpty()) {
                updateStats(_entries.value.last())
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载统计失败: ${e.message}")
        }
    }

    private fun entryToJson(entry: LogEntry): JSONObject {
        return JSONObject().apply {
            put("id", entry.id)
            put("timestamp", entry.timestamp)
            put("isFavorite", entry.isFavorite)
            put("tags", JSONArray(entry.tags))
            put("notes", entry.notes)
            put("params", JSONObject().apply {
                put("positivePrompt", entry.params.positivePrompt)
                put("negativePrompt", entry.params.negativePrompt)
                put("width", entry.params.width)
                put("height", entry.params.height)
                put("steps", entry.params.steps)
                put("guidanceScale", entry.params.guidanceScale)
                put("seed", entry.params.seed)
                put("scheduler", entry.params.scheduler)
                put("modelId", entry.params.modelId)
                put("modelName", entry.params.modelName)
                put("baseModel", entry.params.baseModel)
                put("loras", JSONArray(entry.params.loras))
                put("embeddings", JSONArray(entry.params.embeddings))
                put("enableHiresFix", entry.params.enableHiresFix)
                put("hiresScale", entry.params.hiresScale)
                put("enableControlNet", entry.params.enableControlNet)
                put("controlNetType", entry.params.controlNetType)
                put("batchSize", entry.params.batchSize)
                put("totalImages", entry.params.totalImages)
            })
            put("result", JSONObject().apply {
                put("success", entry.result.success)
                put("outputPaths", JSONArray(entry.result.outputPaths))
                put("outputHashes", JSONArray(entry.result.outputHashes))
                put("errorMessage", entry.result.errorMessage ?: JSONObject.NULL)
                put("isPartialSuccess", entry.result.isPartialSuccess)
            })
            put("performance", JSONObject().apply {
                put("totalTimeMs", entry.performance.totalTimeMs)
                put("inferenceTimeMs", entry.performance.inferenceTimeMs)
                put("preprocessTimeMs", entry.performance.preprocessTimeMs)
                put("postprocessTimeMs", entry.performance.postprocessTimeMs)
                put("memoryUsedMb", entry.performance.memoryUsedMb)
                put("peakMemoryMb", entry.performance.peakMemoryMb)
                put("gpuUtilization", entry.performance.gpuUtilization)
                put("npuUtilization", entry.performance.npuUtilization)
                put("temperature", entry.performance.temperature)
                put("thermalTier", entry.performance.thermalTier)
                put("deviceModel", entry.performance.deviceModel)
                put("androidVersion", entry.performance.androidVersion)
            })
        }
    }

    private fun jsonToEntry(json: JSONObject): LogEntry? {
        return try {
            LogEntry(
                id = json.getString("id"),
                timestamp = json.getLong("timestamp"),
                isFavorite = json.optBoolean("isFavorite", false),
                tags = json.optJSONArray("tags")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
                notes = json.optString("notes", ""),
                params = json.getJSONObject("params").let { p ->
                    GenerationParamsLog(
                        positivePrompt = p.getString("positivePrompt"),
                        negativePrompt = p.optString("negativePrompt", ""),
                        width = p.optInt("width", 512) ?: 512,
                        height = p.optInt("height", 512) ?: 512,
                        steps = p.optInt("steps", 25) ?: 25,
                        guidanceScale = (p.optDouble("guidanceScale", 7.5) ?: 7.5).toFloat(),
                        seed = p.optLong("seed", -1),
                        scheduler = p.optString("scheduler", "DPMSOLVER_PLUS_PLUS_2M_KARRAS"),
                        modelId = p.optString("modelId", ""),
                        modelName = p.optString("modelName", ""),
                        baseModel = p.optString("baseModel", ""),
                        loras = p.optJSONArray("loras")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
                        embeddings = p.optJSONArray("embeddings")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
                        enableHiresFix = p.optBoolean("enableHiresFix", false),
                        hiresScale = (p.optDouble("hiresScale", 1.5) ?: 1.5).toFloat(),
                        enableControlNet = p.optBoolean("enableControlNet", false),
                        controlNetType = p.optString("controlNetType", "NONE"),
                        batchSize = p.optInt("batchSize", 1) ?: 1,
                        totalImages = p.optInt("totalImages", 1) ?: 1
                    )
                },
                result = json.getJSONObject("result").let { r ->
                    ResultLog(
                        success = r.getBoolean("success"),
                        outputPaths = r.optJSONArray("outputPaths")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
                        outputHashes = r.optJSONArray("outputHashes")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
                        errorMessage = if (r.has("errorMessage") && !r.isNull("errorMessage")) r.getString("errorMessage") else null,
                        isPartialSuccess = r.optBoolean("isPartialSuccess", false)
                    )
                },
                performance = json.getJSONObject("performance").let { pe ->
                    PerformanceLog(
                        totalTimeMs = pe.optLong("totalTimeMs", 0),
                        inferenceTimeMs = pe.optLong("inferenceTimeMs", 0),
                        preprocessTimeMs = pe.optLong("preprocessTimeMs", 0),
                        postprocessTimeMs = pe.optLong("postprocessTimeMs", 0),
                        memoryUsedMb = pe.optLong("memoryUsedMb", 0),
                        peakMemoryMb = pe.optLong("peakMemoryMb", 0),
                        gpuUtilization = pe.optDouble("gpuUtilization", 0.0).toFloat(),
                        npuUtilization = pe.optDouble("npuUtilization", 0.0).toFloat(),
                        temperature = pe.optDouble("temperature", 0.0).toFloat(),
                        thermalTier = pe.optString("thermalTier", "NORMAL"),
                        deviceModel = pe.optString("deviceModel", ""),
                        androidVersion = pe.optString("androidVersion", "")
                    )
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "解析日志条目失败: ${e.message}")
            null
        }
    }

    fun release() {
        INSTANCE = null
    }
}
