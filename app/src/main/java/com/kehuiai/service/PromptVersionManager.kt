package com.kehuiai.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * 可绘AI v3.6 - 提示词版本历史管理器
 *
 * 核心功能：
 * 1. 提示词版本追踪（每次修改都有历史）
 * 2. 版本对比（diff 视图）
 * 3. 版本分支管理
 * 4. 版本标签和备注
 * 5. 快速恢复
 * 6. 常用提示词收藏
 */
class PromptVersionManager(private val context: Context) {

    companion object {
        private const val TAG = "PromptVersionManager"
        private const val PREFS_NAME = "prompt_version_prefs"
        private const val HISTORY_FILE = "prompt_history.json"
        private const val FAVORITES_FILE = "prompt_favorites.json"
        private const val MAX_HISTORY_PER_PROMPT = 50

        @Volatile
        private var INSTANCE: PromptVersionManager? = null

        fun getInstance(context: Context): PromptVersionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PromptVersionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // ===== 版本条目 =====
    data class VersionEntry(
        val id: String,
        val promptId: String,         // 关联的 prompt 标识
        val version: Int,              // 版本号
        val content: String,          // 提示词内容
        val negativePrompt: String = "",
        val timestamp: Long,
        val label: String = "",       // 标签（如 "初版", "优化版"）
        val notes: String = "",        // 备注
        val parentVersion: Int = 0,   // 父版本
        val tags: List<String> = emptyList(),
        val isFavorite: Boolean = false,
        val isCurrent: Boolean = true
    )

    // ===== 提示词记录 =====
    data class PromptRecord(
        val promptId: String,
        val name: String,
        val firstCreated: Long,
        val lastModified: Long,
        val versionCount: Int,
        val currentContent: String,
        val tags: List<String>
    )

    // ===== 版本对比 =====
    data class DiffResult(
        val additions: List<String>,   // 新增的行
        val deletions: List<String>,  // 删除的行
        val modifications: List<Pair<String, String>>,  // 修改的行 (old, new)
        val unchanged: Int,
        val similarity: Float  // 相似度 0-1
    )

    // ===== Diff 行 =====
    enum class DiffType { ADDED, REMOVED, UNCHANGED, MODIFIED }

    data class DiffLine(
        val type: DiffType,
        val content: String,
        val lineNumber: Int
    )

    // ===== 状态 =====
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val historyFile = File(context.filesDir, HISTORY_FILE).apply {
        if (!exists()) createNewFile()
    }
    private val favoritesFile = File(context.filesDir, FAVORITES_FILE).apply {
        if (!exists()) createNewFile()
    }

    private val _versions = MutableStateFlow<Map<String, List<VersionEntry>>>(emptyMap())
    val versions: StateFlow<Map<String, List<VersionEntry>>> = _versions.asStateFlow()

    private val _favorites = MutableStateFlow<List<VersionEntry>>(emptyList())
    val favorites: StateFlow<List<VersionEntry>> = _favorites.asStateFlow()

    private val _records = MutableStateFlow<List<PromptRecord>>(emptyList())
    val records: StateFlow<List<PromptRecord>> = _records.asStateFlow()

    init {
        loadHistory()
        loadFavorites()
        buildRecords()
    }

    // ===== 主要接口 =====

    /**
     * 记录提示词版本（每次生成或手动保存）
     */
    suspend fun recordVersion(
        prompt: String,
        negativePrompt: String = "",
        promptId: String? = null,
        label: String = "",
        notes: String = "",
        tags: List<String> = emptyList()
    ): VersionEntry = withContext(Dispatchers.IO) {
        // 确定 promptId
        val id = promptId ?: generatePromptId(prompt)

        // 获取当前版本号
        val existing = _versions.value[id] ?: emptyList()
        val nextVersion = (existing.maxOfOrNull { it.version } ?: 0) + 1
        val parentVersion = existing.maxOfOrNull { it.version } ?: 0

        // 取消之前的 current 标记
        val updated = existing.map { it.copy(isCurrent = false) }

        val entry = VersionEntry(
            id = generateVersionId(id, nextVersion),
            promptId = id,
            version = nextVersion,
            content = prompt,
            negativePrompt = negativePrompt,
            timestamp = System.currentTimeMillis(),
            label = if (label.isBlank()) "v$nextVersion" else label,
            notes = notes,
            parentVersion = parentVersion,
            tags = tags
        )

        val newList = (updated + entry).takeLast(MAX_HISTORY_PER_PROMPT).toMutableList()
        val allVersions = _versions.value.toMutableMap()
        allVersions[id] = newList
        _versions.value = allVersions

        saveHistory()
        buildRecords()

        Log.i(TAG, "记录版本: $id v$nextVersion")
        entry
    }

    /**
     * 获取提示词的所有版本
     */
    fun getVersions(promptId: String): List<VersionEntry> {
        return _versions.value[promptId]?.sortedByDescending { it.version } ?: emptyList()
    }

    /**
     * 获取特定版本
     */
    fun getVersion(promptId: String, version: Int): VersionEntry? {
        return _versions.value[promptId]?.find { it.version == version }
    }

    /**
     * 获取当前版本
     */
    fun getCurrentVersion(promptId: String): VersionEntry? {
        return _versions.value[promptId]?.find { it.isCurrent }
    }

    /**
     * 恢复指定版本
     */
    suspend fun restoreVersion(promptId: String, version: Int): VersionEntry? {
        val entry = getVersion(promptId, version) ?: return null
        // 创建一个新版本，内容与指定版本相同
        return recordVersion(
            prompt = entry.content,
            negativePrompt = entry.negativePrompt,
            promptId = promptId,
            label = "从 v$version 恢复",
            notes = "恢复自 ${entry.label}",
            tags = entry.tags
        )
    }

    /**
     * 对比两个版本
     */
    fun compareVersions(promptId: String, v1: Int, v2: Int): DiffResult {
        val entry1 = getVersion(promptId, v1)
        val entry2 = getVersion(promptId, v2)
        if (entry1 == null || entry2 == null) {
            return DiffResult(emptyList(), emptyList(), emptyList(), 0, 0f)
        }
        return computeDiff(entry1.content, entry2.content)
    }

    /**
     * 获取版本对比的逐行差异
     */
    fun getLineDiff(promptId: String, v1: Int, v2: Int): List<DiffLine> {
        val entry1 = getVersion(promptId, v1)
        val entry2 = getVersion(promptId, v2)
        if (entry1 == null || entry2 == null) return emptyList()

        val lines1 = entry1.content.split("\n")
        val lines2 = entry2.content.split("\n")

        // 简单的行级 diff（使用 LCS 算法）
        return computeLineDiff(lines1, lines2)
    }

    /**
     * 收藏提示词
     */
    fun favoriteVersion(promptId: String, version: Int): Boolean {
        val entry = getVersion(promptId, version) ?: return false

        // 更新版本
        _versions.value = _versions.value.mapValues { (_, list) ->
            list.map { if (it.id == entry.id) it.copy(isFavorite = !it.isFavorite) else it }
        }

        // 更新收藏列表
        _favorites.value = _versions.value.values.flatten().filter { it.isFavorite }

        saveHistory()
        saveFavorites()

        return !entry.isFavorite
    }

    /**
     * 更新标签
     */
    fun updateTags(promptId: String, version: Int, tags: List<String>) {
        _versions.value = _versions.value.mapValues { (_, list) ->
            list.map { v ->
                if (v.promptId == promptId && v.version == version) v.copy(tags = tags)
                else v
            }
        }
        saveHistory()
        buildRecords()
    }

    /**
     * 更新备注
     */
    fun updateNotes(promptId: String, version: Int, notes: String) {
        _versions.value = _versions.value.mapValues { (_, list) ->
            list.map { v ->
                if (v.promptId == promptId && v.version == version) v.copy(notes = notes)
                else v
            }
        }
        saveHistory()
    }

    /**
     * 重命名提示词记录
     */
    fun renamePrompt(promptId: String, newName: String) {
        prefs.edit().putString("name_$promptId", newName).apply()
        buildRecords()
    }

    /**
     * 搜索提示词
     */
    fun search(query: String): List<VersionEntry> {
        val lowerQuery = query.lowercase()
        return _versions.value.values.flatten()
            .filter { entry ->
                entry.content.lowercase().contains(lowerQuery) ||
                entry.negativePrompt.lowercase().contains(lowerQuery) ||
                entry.label.lowercase().contains(lowerQuery) ||
                entry.notes.lowercase().contains(lowerQuery) ||
                entry.tags.any { it.lowercase().contains(lowerQuery) }
            }
            .sortedByDescending { it.timestamp }
    }

    /**
     * 导出版本历史
     */
    fun exportHistory(promptId: String): String {
        val entries = getVersions(promptId)
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("version", entry.version)
                put("content", entry.content)
                put("negativePrompt", entry.negativePrompt)
                put("timestamp", entry.timestamp)
                put("label", entry.label)
                put("notes", entry.notes)
                put("tags", JSONArray(entry.tags))
            })
        }
        return array.toString(2)
    }

    /**
     * 删除提示词历史
     */
    fun deletePromptHistory(promptId: String) {
        val all = _versions.value.toMutableMap()
        all.remove(promptId)
        _versions.value = all
        saveHistory()
        buildRecords()
    }

    /**
     * 删除特定版本
     */
    fun deleteVersion(promptId: String, version: Int) {
        val list = _versions.value[promptId]?.filterNot { it.version == version && it.isCurrent }?.toMutableList()
        if (list != null) {
            // 如果删除的是 current 版本，将最新的设为 current
            if (list.all { !it.isCurrent }) {
                list.maxByOrNull { it.version }?.let { latest ->
                    val idx = list.indexOf(latest)
                    list[idx] = latest.copy(isCurrent = true)
                }
            }
            val all = _versions.value.toMutableMap()
            all[promptId] = list
            _versions.value = all
            saveHistory()
            buildRecords()
        }
    }

    /**
     * 获取常用提示词
     */
    fun getMostUsed(count: Int = 10): List<VersionEntry> {
        val byPrompt: Map<String, List<VersionEntry>> = _versions.value.values.flatten().groupBy(VersionEntry::promptId)
        val byCount: List<Pair<String, Int>> = byPrompt.mapValues { (_, entries) -> entries.size }.toList()
        val sorted: List<Pair<String, Int>> = byCount.sortedByDescending { it.second }
        val topPromptIds: List<String> = sorted.take(count).map { it.first }
        return topPromptIds.mapNotNull { pid -> getCurrentVersion(pid) }
    }

    // ===== 私有方法 =====

    private fun computeDiff(text1: String, text2: String): DiffResult {
        val lines1 = text1.split("\n").filter { it.isNotBlank() }
        val lines2 = text2.split("\n").filter { it.isNotBlank() }

        val additions = lines2.filter { !lines1.contains(it) }
        val deletions = lines1.filter { !lines2.contains(it) }

        val modifications = mutableListOf<Pair<String, String>>()
        val unchanged = lines1.count { lines2.contains(it) }

        for (line in lines1) {
            if (!lines2.contains(line)) {
                // 找最相似的
                val similar = lines2.find { computeSimilarity(line, it) > 0.6f }
                if (similar != null) {
                    modifications.add(line to similar)
                }
            }
        }

        val similarity = if (lines1.isEmpty() && lines2.isEmpty()) 1f
        else if (lines1.isEmpty() || lines2.isEmpty()) 0f
        else {
            val common = lines1.count { lines2.contains(it) }
            common.toFloat() / maxOf(lines1.size, lines2.size)
        }

        return DiffResult(
            additions = additions,
            deletions = deletions,
            modifications = modifications,
            unchanged = unchanged,
            similarity = similarity
        )
    }

    private fun computeLineDiff(lines1: List<String>, lines2: List<String>): List<DiffLine> {
        val result = mutableListOf<DiffLine>()
        val lcs = computeLCS(lines1, lines2)

        var i = 0
        var j = 0
        var lineNum = 1

        while (i < lines1.size || j < lines2.size) {
            when {
                i < lines1.size && j < lines2.size && lines1[i] == lines2[j] -> {
                    result.add(DiffLine(DiffType.UNCHANGED, lines1[i], lineNum++))
                    i++
                    j++
                }
                j < lines2.size && (i >= lines1.size || !lcs.contains(Pair(i, j))) -> {
                    result.add(DiffLine(DiffType.ADDED, lines2[j], 0))
                    j++
                }
                i < lines1.size && (j >= lines2.size || !lcs.contains(Pair(i, j))) -> {
                    result.add(DiffLine(DiffType.REMOVED, lines1[i], lineNum++))
                    i++
                }
                else -> {
                    i++
                    j++
                }
            }
        }

        return result
    }

    private fun computeLCS(a: List<String>, b: List<String>): Set<Pair<Int, Int>> {
        val m = a.size
        val n = b.size
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1] + 1
                           else maxOf(dp[i - 1][j], dp[i][j - 1])
            }
        }

        val lcs = mutableSetOf<Pair<Int, Int>>()
        var i = m
        var j = n
        while (i > 0 && j > 0) {
            when {
                a[i - 1] == b[j - 1] -> {
                    lcs.add(Pair(i - 1, j - 1))
                    i--
                    j--
                }
                dp[i - 1][j] > dp[i][j - 1] -> i--
                else -> j--
            }
        }

        return lcs
    }

    private fun computeSimilarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1f
        if (s1.isBlank() || s2.isBlank()) return 0f

        val words1 = s1.lowercase().split(Regex("[\\s,，、]")).filter { it.length > 2 }.toSet()
        val words2 = s2.lowercase().split(Regex("[\\s,，、]")).filter { it.length > 2 }.toSet()

        if (words1.isEmpty() && words2.isEmpty()) return 1f
        if (words1.isEmpty() || words2.isEmpty()) return 0f

        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size

        return intersection.toFloat() / union
    }

    private fun generatePromptId(prompt: String): String {
        val data = prompt.take(100) + System.currentTimeMillis().toString()
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(data.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun generateVersionId(promptId: String, version: Int): String {
        return "$promptId-v$version-${System.currentTimeMillis()}"
    }

    private fun loadHistory() {
        try {
            if (!historyFile.exists()) return
            val content = historyFile.readText()
            if (content.isBlank()) return

            val json = JSONObject(content)
            val map = mutableMapOf<String, List<VersionEntry>>()

            json.keys().forEach { promptId ->
                val array = json.getJSONArray(promptId)
                val entries = mutableListOf<VersionEntry>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    entries.add(VersionEntry(
                        id = obj.getString("id"),
                        promptId = obj.getString("promptId"),
                        version = obj.getInt("version"),
                        content = obj.getString("content"),
                        negativePrompt = obj.optString("negativePrompt", ""),
                        timestamp = obj.getLong("timestamp"),
                        label = obj.optString("label", ""),
                        notes = obj.optString("notes", ""),
                        parentVersion = obj.optInt("parentVersion", 0),
                        tags = obj.optJSONArray("tags")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
                        isFavorite = obj.optBoolean("isFavorite", false),
                        isCurrent = obj.optBoolean("isCurrent", true)
                    ))
                }
                map[promptId] = entries
            }

            _versions.value = map
            _favorites.value = map.values.flatten().filter { it.isFavorite }
        } catch (e: Exception) {
            Log.e(TAG, "加载历史失败: ${e.message}")
        }
    }

    private fun saveHistory() {
        try {
            val json = JSONObject()
            _versions.value.forEach { (promptId, entries) ->
                val array = JSONArray()
                entries.forEach { entry ->
                    array.put(JSONObject().apply {
                        put("id", entry.id)
                        put("promptId", entry.promptId)
                        put("version", entry.version)
                        put("content", entry.content)
                        put("negativePrompt", entry.negativePrompt)
                        put("timestamp", entry.timestamp)
                        put("label", entry.label)
                        put("notes", entry.notes)
                        put("parentVersion", entry.parentVersion)
                        put("tags", JSONArray(entry.tags))
                        put("isFavorite", entry.isFavorite)
                        put("isCurrent", entry.isCurrent)
                    })
                }
                json.put(promptId, array)
            }
            historyFile.writeText(json.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "保存历史失败: ${e.message}")
        }
    }

    private fun saveFavorites() {
        try {
            val array = JSONArray()
            _favorites.value.forEach { entry ->
                array.put(JSONObject().apply {
                    put("id", entry.id)
                    put("promptId", entry.promptId)
                    put("version", entry.version)
                    put("content", entry.content)
                    put("negativePrompt", entry.negativePrompt)
                    put("timestamp", entry.timestamp)
                    put("label", entry.label)
                    put("tags", JSONArray(entry.tags))
                })
            }
            favoritesFile.writeText(array.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "保存收藏失败: ${e.message}")
        }
    }

    private fun loadFavorites() {
        try {
            if (!favoritesFile.exists()) return
            val content = favoritesFile.readText()
            if (content.isBlank()) return

            val array = JSONArray(content)
            val list = mutableListOf<VersionEntry>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(VersionEntry(
                    id = obj.getString("id"),
                    promptId = obj.getString("promptId"),
                    version = obj.getInt("version"),
                    content = obj.getString("content"),
                    negativePrompt = obj.optString("negativePrompt", ""),
                    timestamp = obj.getLong("timestamp"),
                    label = obj.optString("label", ""),
                    tags = obj.optJSONArray("tags")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
                    isFavorite = true
                ))
            }
            _favorites.value = list
        } catch (e: Exception) {
            Log.e(TAG, "加载收藏失败: ${e.message}")
        }
    }

    private fun buildRecords() {
        val records = _versions.value.map { (promptId, entries) ->
            val latest = entries.maxByOrNull { it.version }!!
            PromptRecord(
                promptId = promptId,
                name = prefs.getString("name_$promptId", latest.content.take(30)) ?: latest.content.take(30),
                firstCreated = entries.minOfOrNull { it.timestamp } ?: latest.timestamp,
                lastModified = latest.timestamp,
                versionCount = entries.size,
                currentContent = latest.content,
                tags = latest.tags
            )
        }.sortedByDescending { it.lastModified }
        _records.value = records
    }

    fun release() {
        INSTANCE = null
    }
}
