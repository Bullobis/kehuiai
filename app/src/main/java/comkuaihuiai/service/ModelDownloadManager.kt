package comkuaihuiai.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * 可绘AI v3.5.0 - 模型下载管理器
 */
class ModelDownloadManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelDownloadManager"
        private const val CONNECT_TIMEOUT = 30000
        private const val READ_TIMEOUT = 60000
    }
    
    enum class DownloadStatus {
        PENDING, QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED
    }
    
    data class DownloadTask(
        val id: String,
        val url: String,
        val fileName: String,
        val saveDir: String,
        var totalSize: Long = 0,
        var downloadedSize: Long = 0,
        var status: DownloadStatus = DownloadStatus.PENDING,
        var progress: Float = 0f,
        var speed: Long = 0,
        var startTime: Long = 0
    )
    
    data class ModelInfo(
        val id: String,
        val name: String,
        val url: String,
        val size: Long,
        val category: String = "checkpoint"
    )
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tasks = ConcurrentHashMap<String, DownloadTask>()
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val queue = ArrayDeque<String>()
    private var maxConcurrent = 2
    
    private val _tasksFlow = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasksFlow: StateFlow<List<DownloadTask>> = _tasksFlow.asStateFlow()
    
    fun addTask(modelInfo: ModelInfo, saveDir: String): String {
        val taskId = "dl_${System.currentTimeMillis()}"
        val task = DownloadTask(
            id = taskId,
            url = modelInfo.url,
            fileName = "${modelInfo.id}.safetensors",
            saveDir = saveDir,
            totalSize = modelInfo.size
        )
        tasks[taskId] = task
        queue.addLast(taskId)
        processQueue()
        return taskId
    }
    
    fun startDownload(taskId: String) {
        tasks[taskId]?.let { task ->
            if (task.status == DownloadStatus.PENDING || task.status == DownloadStatus.PAUSED) {
                task.status = DownloadStatus.QUEUED
                if (!queue.contains(taskId)) queue.addLast(taskId)
                updateTask(task)
                processQueue()
            }
        }
    }
    
    fun pauseDownload(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        tasks[taskId]?.apply {
            status = DownloadStatus.PAUSED
            updateTask(this)
        }
    }
    
    fun cancelDownload(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        queue.remove(taskId)
        tasks[taskId]?.apply {
            status = DownloadStatus.CANCELLED
            File(saveDir, fileName).delete()
            updateTask(this)
        }
    }
    
    fun pauseAll() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        tasks.values.filter { it.status == DownloadStatus.DOWNLOADING }.forEach { task ->
            task.status = DownloadStatus.PAUSED
            updateTask(task)
        }
    }
    
    fun resumeAll() {
        tasks.values.filter { it.status == DownloadStatus.PAUSED }.forEach { startDownload(it.id) }
    }
    
    fun getTask(taskId: String): DownloadTask? = tasks[taskId]
    fun getAllTasks(): List<DownloadTask> = tasks.values.toList()
    fun clearCompleted() {
        tasks.entries.removeIf { it.value.status == DownloadStatus.COMPLETED }
        updateTaskList()
    }
    
    fun release() {
        scope.cancel()
        pauseAll()
    }
    
    private fun processQueue() {
        scope.launch {
            while (queue.isNotEmpty() && activeJobs.size < maxConcurrent) {
                val taskId = if (queue.isNotEmpty()) queue.removeFirst() else null
                if (taskId == null) break
                val task = tasks[taskId] ?: continue
                if (task.status == DownloadStatus.COMPLETED || task.status == DownloadStatus.CANCELLED) continue
                
                val job = scope.launch { downloadFile(task) }
                activeJobs[taskId] = job
                task.status = DownloadStatus.DOWNLOADING
                updateTask(task)
            }
        }
    }
    
    private suspend fun downloadFile(task: DownloadTask) = withContext(Dispatchers.IO) {
        Log.i(TAG, "下载: ${task.fileName}")
        task.startTime = System.currentTimeMillis()
        
        try {
            val url = URL(task.url)
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
            }
            
            if (task.totalSize == 0L) task.totalSize = conn.contentLengthLong.toLong()
            
            val dir = File(task.saveDir)
            if (!dir.exists()) dir.mkdirs()
            
            val file = File(task.saveDir, task.fileName)
            FileOutputStream(file).use { output ->
                conn.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    var lastUpdate = System.currentTimeMillis()
                    var bytes: Int
                    
                    while (input.read(buffer).also { bytes = it } != -1) {
                        if (!isActive) return@withContext
                        output.write(buffer, 0, bytes)
                        task.downloadedSize += bytes
                        
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate >= 1000) {
                            val elapsed = (now - task.startTime) / 1000.0
                            task.speed = (task.downloadedSize / elapsed).toLong()
                            task.progress = if (task.totalSize > 0) task.downloadedSize.toFloat() / task.totalSize else 0f
                            lastUpdate = now
                            updateTask(task)
                        }
                    }
                }
            }
            
            task.status = DownloadStatus.COMPLETED
            task.progress = 1f
            activeJobs.remove(task.id)
            Log.i(TAG, "完成: ${task.fileName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "失败: ${task.fileName}, ${e.message}")
            task.status = DownloadStatus.FAILED
        }
        
        updateTask(task)
        processQueue()
    }
    
    private fun updateTask(task: DownloadTask) {
        _tasksFlow.value = tasks.values.toList()
    }
    
    private fun updateTaskList() {
        _tasksFlow.value = tasks.values.toList()
    }
}
