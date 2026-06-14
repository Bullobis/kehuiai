package comkuaihuiai.service

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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 可绘AI v3.6 - 模型版本管理系统
 *
 * 核心功能：
 * 1. 模型版本追踪（下载/更新/回滚）
 * 2. 模型完整性校验（SHA256/MD5）
 * 3. 模型对比分析（参数数量、尺寸、精度）
 * 4. 版本历史记录
 * 5. 模型标签和分类管理
 * 6. 自动更新检查
 */
class ModelVersionManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelVersionManager"
        private const val PREFS_NAME = "model_version_prefs"
        private const val VERSIONS_FILE = "model_versions.json"
        private const val MODEL_DIR = "models"

        @Volatile
        private var INSTANCE: ModelVersionManager? = null

        fun getInstance(context: Context): ModelVersionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModelVersionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // ===== 模型版本 =====
    data class ModelVersion(
        val id: String,
        val modelId: String,         // 模型标识（如 SD_1_5）
        val version: String,          // 版本号（如 v1.0, v2.0）
        val downloadUrl: String,
        val filePath: String,
        val fileSize: Long,           // 字节
        val checksum: String,         // SHA256
        val checksumType: ChecksumType = ChecksumType.SHA256,
        val downloadDate: Long,
        val lastUsedDate: Long = 0,
        val useCount: Int = 0,
        val tags: List<String> = emptyList(),
        val notes: String = "",
        val isCurrent: Boolean = false,
        val metadata: Map<String, String> = emptyMap()
    )

    enum class ChecksumType {
        SHA256, MD5, SHA1
    }

    // ===== 模型信息 =====
    data class ModelInfo(
        val id: String,
        val name: String,
        val description: String,
        val baseModel: String,
        val format: ModelFormat,
        val size: Long,
        val paramCount: Long,         // 参数量
        val precision: String,       // fp16, fp32, int8 等
        val recommendedSteps: IntRange,
        val recommendedCFG: ClosedFloatingPointRange<Float>,
        val tags: List<String>,
        val versions: List<ModelVersion>
    )

    enum class ModelFormat {
        SAFE_TENSOR,   // .safetensors
        PICKLE,        // .ckpt
        ONNX,          // .onnx
        GGML,          // .ggml/.gguf
        UNKNOWN
    }

    // ===== 模型对比结果 =====
    data class ComparisonResult(
        val modelA: ModelInfo,
        val modelB: ModelInfo,
        val sizeDiff: Long,          // 大小差异（字节）
        val paramDiff: Long,          // 参数量差异
        val recommendation: String,
        val pros: List<String>,
        val cons: List<String>
    )

    // ===== 版本检查结果 =====
    data class UpdateCheckResult(
        val hasUpdate: Boolean,
        val currentVersion: String,
        val latestVersion: String,
        val changelog: String = "",
        val downloadUrl: String = "",
        val fileSize: Long = 0
    )

    // ===== 状态 =====
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val modelDir = File(context.filesDir, MODEL_DIR).apply { if (!exists()) mkdirs() }
    private val versionsFile = File(context.filesDir, VERSIONS_FILE)

    private val _versions = MutableStateFlow<List<ModelVersion>>(emptyList())
    val versions: StateFlow<List<ModelVersion>> = _versions.asStateFlow()

    private val _modelInfos = MutableStateFlow<List<ModelInfo>>(emptyList())
    val modelInfos: StateFlow<List<ModelInfo>> = _modelInfos.asStateFlow()

    init {
        loadVersions()
        buildModelInfos()
    }

    // ===== 主要接口 =====

    /**
     * 注册模型版本
     */
    suspend fun registerVersion(
        modelId: String,
        version: String,
        filePath: String,
        checksum: String,
        checksumType: ChecksumType = ChecksumType.SHA256,
        tags: List<String> = emptyList(),
        notes: String = ""
    ) = withContext(Dispatchers.IO) {
        val file = File(filePath)
        val existing = _versions.value.filter { it.modelId == modelId }

        // 取消当前版本标记
        val updatedExisting = existing.map { it.copy(isCurrent = false) }
        updatedExisting.forEach { updateVersionInList(it) }

        val newVersion = ModelVersion(
            id = generateId(modelId, version),
            modelId = modelId,
            version = version,
            downloadUrl = "",
            filePath = filePath,
            fileSize = if (file.exists()) file.length() else 0,
            checksum = checksum,
            checksumType = checksumType,
            downloadDate = System.currentTimeMillis(),
            isCurrent = true,
            tags = tags,
            notes = notes
        )

        val current = _versions.value.toMutableList()
        current.add(newVersion)
        _versions.value = current

        saveVersions()
        buildModelInfos()

        Log.i(TAG, "注册模型版本: $modelId $version")
        newVersion
    }

    /**
     * 标记为当前使用版本
     */
    fun setCurrentVersion(versionId: String) {
        val version = _versions.value.find { it.id == versionId } ?: return

        // 取消同模型的其他版本
        _versions.value = _versions.value.map { v ->
            if (v.modelId == version.modelId) {
                v.copy(isCurrent = v.id == versionId)
            } else v
        }

        // 更新使用统计
        _versions.value = _versions.value.map { v ->
            if (v.id == versionId) {
                v.copy(
                    lastUsedDate = System.currentTimeMillis(),
                    useCount = v.useCount + 1
                )
            } else v
        }

        saveVersions()
        buildModelInfos()
    }

    /**
     * 获取模型的当前版本
     */
    fun getCurrentVersion(modelId: String): ModelVersion? {
        return _versions.value.find { it.modelId == modelId && it.isCurrent }
    }

    /**
     * 获取模型的所有版本
     */
    fun getVersions(modelId: String): List<ModelVersion> {
        return _versions.value.filter { it.modelId == modelId }
            .sortedByDescending { it.downloadDate }
    }

    /**
     * 校验模型完整性
     */
    suspend fun verifyIntegrity(versionId: String): IntegrityResult = withContext(Dispatchers.IO) {
        val version = _versions.value.find { it.id == versionId }
            ?: return@withContext IntegrityResult(versionId, false, "版本不存在")

        val file = File(version.filePath)
        if (!file.exists()) {
            return@withContext IntegrityResult(versionId, false, "文件不存在: ${version.filePath}")
        }

        if (file.length() != version.fileSize && version.fileSize > 0) {
            return@withContext IntegrityResult(versionId, false, "文件大小不匹配: 期望 ${version.fileSize}, 实际 ${file.length()}")
        }

        // 计算校验和
        val computed = computeChecksum(file, version.checksumType)
        val matches = computed.equals(version.checksum, ignoreCase = true)

        if (!matches) {
            Log.w(TAG, "校验和不匹配: $versionId, expected=${version.checksum}, actual=$computed")
        }

        IntegrityResult(
            versionId = versionId,
            isValid = matches,
            message = if (matches) "完整性校验通过" else "校验和不匹配",
            expectedChecksum = version.checksum,
            actualChecksum = computed,
            fileSize = file.length(),
            expectedSize = version.fileSize
        )
    }

    data class IntegrityResult(
        val versionId: String,
        val isValid: Boolean,
        val message: String,
        val expectedChecksum: String = "",
        val actualChecksum: String = "",
        val fileSize: Long = 0,
        val expectedSize: Long = 0
    )

    /**
     * 比较两个模型版本
     */
    fun compareVersions(versionIdA: String, versionIdB: String): ComparisonResult? {
        val vA = _versions.value.find { it.id == versionIdA } ?: return null
        val vB = _versions.value.find { it.id == versionIdB } ?: return null

        val modelA = buildModelInfo(vA)
        val modelB = buildModelInfo(vB)

        val prosA = mutableListOf<String>()
        val consA = mutableListOf<String>()
        val prosB = mutableListOf<String>()
        val consB = mutableListOf<String>()

        // 大小比较
        if (vA.fileSize < vB.fileSize) {
            prosA.add("体积更小（${formatSize(vA.fileSize)}）")
            consB.add("体积较大（${formatSize(vB.fileSize)}）")
        } else {
            prosB.add("体积更小（${formatSize(vB.fileSize)}）")
            consA.add("体积较大（${formatSize(vA.fileSize)}）")
        }

        // 版本新旧
        if (vA.downloadDate > vB.downloadDate) {
            prosA.add("版本更新")
            consB.add("版本较旧")
        } else {
            prosB.add("版本更新")
            consA.add("版本较旧")
        }

        // 使用次数
        if (vA.useCount > vB.useCount) {
            prosA.add("使用更多（${vA.useCount}次）")
        } else {
            prosB.add("使用更多（${vB.useCount}次）")
        }

        val recommendation = when {
            vA.downloadDate > vB.downloadDate -> "推荐使用 ${vA.version}（最新版本）"
            vA.useCount > vB.useCount -> "推荐使用 ${vA.version}（使用经验丰富）"
            vA.fileSize < vB.fileSize -> "推荐使用 ${vA.version}（体积更小）"
            else -> "根据场景选择"
        }

        return ComparisonResult(
            modelA = modelA,
            modelB = modelB,
            sizeDiff = vA.fileSize - vB.fileSize,
            paramDiff = 0,
            recommendation = recommendation,
            pros = prosA,
            cons = consA
        )
    }

    /**
     * 删除版本
     */
    suspend fun deleteVersion(versionId: String) = withContext(Dispatchers.IO) {
        val version = _versions.value.find { it.id == versionId } ?: return@withContext
        val file = File(version.filePath)

        // 删除文件
        if (file.exists()) {
            file.delete()
        }

        // 从列表移除
        _versions.value = _versions.value.filter { it.id != versionId }

        // 如果删除的是当前版本，选择最新的作为当前
        val remaining = _versions.value.filter { it.modelId == version.modelId }
        if (remaining.isNotEmpty() && !remaining.any { it.isCurrent }) {
            val latest = remaining.maxByOrNull { it.downloadDate }
            if (latest != null) {
                setCurrentVersion(latest.id)
            }
        }

        saveVersions()
        buildModelInfos()

        Log.i(TAG, "删除版本: $versionId")
    }

    /**
     * 获取使用统计
     */
    fun getUsageStats(): Map<String, Any> {
        val versions = _versions.value
        val mostUsedEntry = versions.maxByOrNull { it.useCount }
        val mostUsedMap: Map<String, Any> = mostUsedEntry?.let {
            mapOf("model" to it.modelId, "version" to it.version, "uses" to it.useCount)
        } ?: emptyMap()
        val recentlyUsed: List<Map<String, Any>> = versions
            .filter { it.lastUsedDate > 0 }
            .sortedByDescending { it.lastUsedDate }
            .take(5)
            .map { mapOf("model" to it.modelId, "version" to it.version, "lastUsed" to it.lastUsedDate) }
        val storageByModel: Map<String, Long> = versions
            .groupBy { it.modelId }
            .mapValues { it.value.sumOf { v -> v.fileSize } }
        return mapOf(
            "total_versions" to versions.size as Any,
            "total_models" to versions.map { it.modelId }.distinct().size as Any,
            "total_size" to versions.sumOf { it.fileSize } as Any,
            "total_uses" to versions.sumOf { it.useCount } as Any,
            "most_used" to mostUsedMap as Any,
            "recently_used" to recentlyUsed as Any,
            "storage_by_model" to storageByModel as Any
        )
    }

    /**
     * 添加标签
     */
    fun addTag(versionId: String, tag: String) {
        _versions.value = _versions.value.map { v ->
            if (v.id == versionId && !v.tags.contains(tag)) {
                v.copy(tags = v.tags + tag)
            } else v
        }
        saveVersions()
    }

    /**
     * 设置备注
     */
    fun setNotes(versionId: String, notes: String) {
        _versions.value = _versions.value.map { v ->
            if (v.id == versionId) v.copy(notes = notes) else v
        }
        saveVersions()
    }

    /**
     * 导出版本信息
     */
    fun exportVersionInfo(): String {
        val array = JSONArray()
        _versions.value.forEach { v ->
            array.put(JSONObject().apply {
                put("id", v.id)
                put("modelId", v.modelId)
                put("version", v.version)
                put("filePath", v.filePath)
                put("fileSize", v.fileSize)
                put("checksum", v.checksum)
                put("checksumType", v.checksumType.name)
                put("downloadDate", v.downloadDate)
                put("lastUsedDate", v.lastUsedDate)
                put("useCount", v.useCount)
                put("tags", JSONArray(v.tags))
                put("notes", v.notes)
                put("isCurrent", v.isCurrent)
            })
        }
        return array.toString(2)
    }

    // ===== 私有方法 =====

    private fun loadVersions() {
        try {
            if (!versionsFile.exists()) return
            val content = versionsFile.readText()
            val array = JSONArray(content)
            val list = mutableListOf<ModelVersion>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(ModelVersion(
                    id = obj.getString("id"),
                    modelId = obj.getString("modelId"),
                    version = obj.getString("version"),
                    downloadUrl = obj.optString("downloadUrl", ""),
                    filePath = obj.optString("filePath", ""),
                    fileSize = obj.optLong("fileSize", 0) ?: 0L,
                    checksum = obj.optString("checksum", ""),
                    checksumType = try { ChecksumType.valueOf(obj.optString("checksumType", "SHA256")) } catch (e: Exception) { ChecksumType.SHA256 },
                    downloadDate = obj.optLong("downloadDate", 0) ?: 0L,
                    lastUsedDate = obj.optLong("lastUsedDate", 0) ?: 0L,
                    useCount = obj.optInt("useCount", 0) ?: 0,
                    tags = obj.optJSONArray("tags")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
                    notes = obj.optString("notes", ""),
                    isCurrent = obj.optBoolean("isCurrent", false)
                ))
            }
            _versions.value = list
        } catch (e: Exception) {
            Log.e(TAG, "加载版本失败: ${e.message}")
        }
    }

    private fun saveVersions() {
        try {
            val array = JSONArray()
            _versions.value.forEach { v ->
                array.put(JSONObject().apply {
                    put("id", v.id)
                    put("modelId", v.modelId)
                    put("version", v.version)
                    put("downloadUrl", v.downloadUrl)
                    put("filePath", v.filePath)
                    put("fileSize", v.fileSize)
                    put("checksum", v.checksum)
                    put("checksumType", v.checksumType.name)
                    put("downloadDate", v.downloadDate)
                    put("lastUsedDate", v.lastUsedDate)
                    put("useCount", v.useCount)
                    put("tags", JSONArray(v.tags))
                    put("notes", v.notes)
                    put("isCurrent", v.isCurrent)
                })
            }
            versionsFile.writeText(array.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "保存版本失败: ${e.message}")
        }
    }

    private fun updateVersionInList(version: ModelVersion) {
        _versions.value = _versions.value.map { v ->
            if (v.id == version.id) version else v
        }
    }

    private fun buildModelInfos() {
        val infos = _versions.value
            .groupBy { it.modelId }
            .map { (modelId, versions) ->
                val latest = versions.maxByOrNull { it.downloadDate }!!
                buildModelInfo(latest).copy(versions = versions.sortedByDescending { it.downloadDate })
            }
        _modelInfos.value = infos
    }

    private fun buildModelInfo(version: ModelVersion): ModelInfo {
        val format = when {
            version.filePath.endsWith(".safetensors") -> ModelFormat.SAFE_TENSOR
            version.filePath.endsWith(".ckpt") || version.filePath.endsWith(".pt") -> ModelFormat.PICKLE
            version.filePath.endsWith(".onnx") -> ModelFormat.ONNX
            version.filePath.endsWith(".gguf") || version.filePath.endsWith(".ggml") -> ModelFormat.GGML
            else -> ModelFormat.UNKNOWN
        }

        val paramCount = estimateParamCount(version.fileSize, format)
        val precision = guessPrecision(format, version.fileSize, paramCount)

        return ModelInfo(
            id = version.modelId,
            name = version.modelId.replace("_", " ").replace("-", " ").replaceFirstChar { it.uppercase() },
            description = "本地模型 ${version.version}",
            baseModel = version.modelId,
            format = format,
            size = version.fileSize,
            paramCount = paramCount,
            precision = precision,
            recommendedSteps = when {
                paramCount > 5_000_000_000 -> 20..30
                paramCount > 2_000_000_000 -> 25..35
                else -> 25..30
            },
            recommendedCFG = 5f..12f,
            tags = version.tags,
            versions = emptyList()
        )
    }

    private fun estimateParamCount(fileSize: Long, format: ModelFormat): Long {
        // 估算：safetensors(fp16) 每参数量约 2 字节, fp32 约 4 字节
        val bytesPerParam = when (format) {
            ModelFormat.SAFE_TENSOR -> 2.0  // 假设 fp16
            ModelFormat.PICKLE -> 2.5
            ModelFormat.ONNX -> 2.0
            ModelFormat.GGML -> 2.0
            ModelFormat.UNKNOWN -> 2.0
        }
        return (fileSize / bytesPerParam).toLong()
    }

    private fun guessPrecision(format: ModelFormat, fileSize: Long, paramCount: Long): String {
        if (paramCount <= 0) return "Unknown"

        val bytesPerParam = fileSize.toDouble() / paramCount
        return when {
            bytesPerParam > 3.5 -> "FP32"
            bytesPerParam > 1.75 -> "FP16"
            bytesPerParam > 0.875 -> "INT8"
            bytesPerParam > 0.5 -> "INT4"
            else -> "Quantized"
        }
    }

    private fun generateId(modelId: String, version: String): String {
        val data = "$modelId-$version-${System.currentTimeMillis()}"
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(data.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun computeChecksum(file: File, type: ChecksumType): String {
        val md = when (type) {
            ChecksumType.SHA256 -> MessageDigest.getInstance("SHA-256")
            ChecksumType.MD5 -> MessageDigest.getInstance("MD5")
            ChecksumType.SHA1 -> MessageDigest.getInstance("SHA-1")
        }

        val fis = java.io.FileInputStream(file)
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (fis.read(buffer).also { bytesRead = it } != -1) {
            md.update(buffer, 0, bytesRead)
        }
        fis.close()
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1_000_000.0)
            bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
            else -> "$bytes B"
        }
    }

    fun release() {
        INSTANCE = null
    }
}
