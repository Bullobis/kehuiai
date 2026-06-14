package comkuaihuiai.service

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.*

/**
 * 可绘AI v3.6.0 - 智能批处理引擎
 * 
 * 功能：
 * - 自动合并相似任务
 * - 智能任务调度
 * - 动态批处理大小
 * - 优先级队列管理
 */
class SmartBatchProcessor {

    companion object {
        private const val TAG = "SmartBatch"
        
        // 批处理配置
        const val MAX_BATCH_SIZE = 8
        const val MIN_BATCH_SIZE = 1
        const val DEFAULT_BATCH_SIZE = 4
        
        // 时间窗口
        private const val MERGE_WINDOW_MS = 500L
        private const val MAX_WAIT_MS = 2000L
        
        // 相似度阈值
        private const val SIMILARITY_THRESHOLD = 0.75f
    }
    
    /**
     * 批处理任务
     */
    data class BatchTask(
        val id: String,
        val prompt: String,
        val negativePrompt: String = "",
        val params: TaskParams,
        val priority: Int = 0,
        val timestamp: Long = System.currentTimeMillis(),
        val continuation: CompletableDeferred<BatchResult> = CompletableDeferred()
    )
    
    /**
     * 任务参数
     */
    data class TaskParams(
        val width: Int = 512,
        val height: Int = 512,
        val steps: Int = 20,
        val seed: Long = -1,
        val guidanceScale: Float = 7.5f,
        val modelId: String = "",
        val controlNet: String? = null
    )
    
    /**
     * 批处理结果
     */
    data class BatchResult(
        val taskId: String,
        val success: Boolean,
        val outputs: List<Any> = emptyList(),
        val error: String? = null,
        val processingTimeMs: Long = 0
    )
    
    /**
     * 批次
     */
    data class Batch(
        val id: String = "${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}",
        val tasks: MutableList<BatchTask> = mutableListOf(),
        val createdAt: Long = System.currentTimeMillis(),
        var size: Int = 0
    )
    
    /**
     * 统计信息
     */
    data class BatchStats(
        val totalTasks: Int,
        val totalBatches: Int,
        val averageBatchSize: Float,
        val tasksMerged: Int,
        val averageWaitTimeMs: Long,
        val efficiency: Float  // 0-1, 越高表示合并效果越好
    )
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // 任务队列
    private val pendingTasks = PriorityBlockingQueue<BatchTask>(100)
    private val runningTasks = ConcurrentHashMap<String, BatchTask>()
    
    // 批次缓存 (用于合并相似任务)
    private val batchCache = ConcurrentHashMap<String, Batch>()
    
    // 控制
    private val isRunning = AtomicBoolean(false)
    private val shouldStop = AtomicBoolean(false)
    
    // 统计
    private val totalTasks = AtomicInteger(0)
    private val totalBatches = AtomicInteger(0)
    private val tasksMerged = AtomicInteger(0)
    private val waitTimes = ArrayDeque<Long>(1000)
    
    // Flow
    private val _batchStats = MutableStateFlow(BatchStats(0, 0, 0f, 0, 0, 0f))
    val stats: StateFlow<BatchStats> = _batchStats.asStateFlow()
    
    private val _batchProgress = MutableSharedFlow<Pair<String, Int>>(extraBufferCapacity = 64)
    val progress: SharedFlow<Pair<String, Int>> = _batchProgress.asSharedFlow()
    
    /**
     * 启动批处理器
     */
    fun start() {
        if (isRunning.get()) return
        
        isRunning.set(true)
        shouldStop.set(false)
        
        scope.launch {
            processLoop()
        }
        
        Log.i(TAG, "SmartBatchProcessor 已启动")
    }
    
    /**
     * 停止批处理器
     */
    fun stop() {
        shouldStop.set(true)
        isRunning.set(false)
        pendingTasks.clear()
        batchCache.clear()
        Log.i(TAG, "SmartBatchProcessor 已停止")
    }
    
    /**
     * 提交任务
     */
    suspend fun submitTask(task: BatchTask): BatchResult {
        totalTasks.incrementAndGet()
        
        // 尝试合并相似任务
        val merged = tryMergeTask(task)
        if (merged != null) {
            tasksMerged.incrementAndGet()
            return merged
        }
        
        // 加入等待队列
        pendingTasks.put(task)
        
        // 等待结果
        val startWait = System.currentTimeMillis()
        val result = task.continuation.await()
        waitTimes.addLast(System.currentTimeMillis() - startWait)
        
        updateStats()
        return result
    }
    
    /**
     * 批量提交任务
     */
    suspend fun submitBatch(tasks: List<BatchTask>): List<BatchResult> {
        return tasks.map { submitTask(it) }
    }
    
    /**
     * 取消任务
     */
    fun cancelTask(taskId: String): Boolean {
        // 检查是否在队列中
        val iterator = pendingTasks.iterator()
        while (iterator.hasNext()) {
            val task = iterator.next()
            if (task.id == taskId) {
                iterator.remove()
                task.continuation.complete(
                    BatchResult(taskId, false, error = "任务已取消")
                )
                return true
            }
        }
        
        // 检查是否在运行中
        runningTasks[taskId]?.let { task ->
            runningTasks.remove(taskId)
            task.continuation.complete(
                BatchResult(taskId, false, error = "任务已在运行中被取消")
            )
            return true
        }
        
        return false
    }
    
    /**
     * 获取统计信息
     */
    fun getStats(): BatchStats = _batchStats.value
    
    /**
     * 释放资源
     */
    fun release() {
        stop()
        scope.cancel()
        Log.i(TAG, "SmartBatchProcessor 已释放")
    }
    
    // ==================== 私有方法 ====================
    
    private suspend fun processLoop() {
        while (!shouldStop.get()) {
            // 收集可以合并的任务
            val batch = collectBatch()
            
            if (batch.tasks.isNotEmpty()) {
                totalBatches.incrementAndGet()
                executeBatch(batch)
            } else {
                delay(100) // 避免空转
            }
        }
    }
    
    private fun collectBatch(): Batch {
        val batch = Batch()
        val deadline = System.currentTimeMillis() + MAX_WAIT_MS
        
        while (batch.size < MAX_BATCH_SIZE && System.currentTimeMillis() < deadline) {
            val task = pendingTasks.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                ?: continue
            
            // 检查是否可以合并到现有批次
            val canMerge = batch.tasks.any { isSimilar(it, task) }
            
            if (canMerge && batch.size < MAX_BATCH_SIZE) {
                batch.tasks.add(task)
                batch.size = batch.tasks.size
            } else if (!canMerge && batch.tasks.isEmpty()) {
                // 第一个任务总是加入
                batch.tasks.add(task)
                batch.size = 1
                
                // 尝试收集更多相似任务
                while (batch.size < MAX_BATCH_SIZE) {
                    val similarTask = pendingTasks.poll(50, java.util.concurrent.TimeUnit.MILLISECONDS)
                        ?: break
                    
                    if (isSimilar(batch.tasks.first(), similarTask)) {
                        batch.tasks.add(similarTask)
                        batch.size = batch.tasks.size
                    } else {
                        // 不相似，放回队列
                        pendingTasks.put(similarTask)
                        break
                    }
                }
                
                break
            } else {
                // 不能合并，放回队列
                pendingTasks.put(task)
                break
            }
        }
        
        return batch
    }
    
    private suspend fun executeBatch(batch: Batch) {
        if (batch.tasks.isEmpty()) return
        
        Log.i(TAG, "执行批次: ${batch.id}, 任务数: ${batch.tasks.size}")
        
        val startTime = System.currentTimeMillis()
        val progressStep = 100 / batch.tasks.size
        
        batch.tasks.forEachIndexed { index, task ->
            runningTasks[task.id] = task
            
            try {
                scope.launch {
                    val result = processTask(task)
                    task.continuation.complete(result)
                    runningTasks.remove(task.id)
                    
                    _batchProgress.emit(task.id to (index + 1) * progressStep)
                }
            } catch (e: Exception) {
                task.continuation.complete(
                    BatchResult(task.id, false, error = e.message)
                )
                runningTasks.remove(task.id)
            }
        }
        
        // 更新批次统计
        updateStats()
        return
    }

    private suspend fun processTask(task: BatchTask): BatchResult {
        val startTime = System.currentTimeMillis()
        
        try {
            // 模拟推理处理
            delay(100L * task.params.steps / 20) // 根据步数调整
            
            // 返回模拟结果
            BatchResult(
                taskId = task.id,
                success = true,
                outputs = listOf("generated_image_${task.id}"),
                processingTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            BatchResult(
                taskId = task.id,
                success = false,
                error = e.message,
                processingTimeMs = System.currentTimeMillis() - startTime
            )
        }
        return BatchResult(taskId = task.id, success = false, error = "未知错误")
    }

    private fun tryMergeTask(task: BatchTask): BatchResult? {
        val currentTime = System.currentTimeMillis()
        
        // 检查批次缓存中是否有可合并的任务
        for ((_, batch) in batchCache) {
            // 检查时间窗口
            if (currentTime - batch.createdAt > MERGE_WINDOW_MS) {
                batchCache.remove(batch.id)
                continue
            }
            
            // 检查相似度
            val firstTask = batch.tasks.firstOrNull() ?: continue
            if (isSimilar(firstTask, task)) {
                // 合并到现有批次
                batch.tasks.add(task)
                batch.size = batch.tasks.size
                tasksMerged.incrementAndGet()
                
                Log.i(TAG, "任务 ${task.id} 合并到批次 ${batch.id}")
                return null // 不立即返回，让它进入队列等待执行
            }
        }
        
        // 创建新批次
        val newBatch = Batch(tasks = mutableListOf(task))
        batchCache[newBatch.id] = newBatch
        
        return null
    }
    
    private fun isSimilar(task1: BatchTask, task2: BatchTask): Boolean {
        // 检查参数相似度
        if (task1.params.width != task2.params.width) return false
        if (task1.params.height != task2.params.height) return false
        if (task1.params.steps != task2.params.steps) return false
        if (task1.params.guidanceScale != task2.params.guidanceScale) return false
        if (task1.params.modelId != task2.params.modelId) return false
        
        // 检查 Prompt 相似度
        val similarity = calculatePromptSimilarity(task1.prompt, task2.prompt)
        if (similarity < SIMILARITY_THRESHOLD) return false
        
        // 检查时间窗口
        if (kotlin.math.abs(task1.timestamp - task2.timestamp) > MERGE_WINDOW_MS) return false
        
        return true
    }
    
    private fun calculatePromptSimilarity(prompt1: String, prompt2: String): Float {
        if (prompt1 == prompt2) return 1f
        if (prompt1.isEmpty() || prompt2.isEmpty()) return 0f
        
        // 简单的词袋相似度
        val words1 = prompt1.lowercase().split(Regex("[\\s,.!?]+")).toSet()
        val words2 = prompt2.lowercase().split(Regex("[\\s,.!?]+")).toSet()
        
        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        
        return if (union > 0) intersection.toFloat() / union else 0f
    }
    
    private fun updateStats() {
        val avgBatchSize = if (totalBatches.get() > 0) {
            totalTasks.get().toFloat() / totalBatches.get()
        } else 0f
        
        val avgWaitTime = if (waitTimes.isNotEmpty()) {
            waitTimes.average().toLong()
        } else 0L
        
        // 计算效率 (基于合并率)
        val mergeRate = if (totalTasks.get() > 0) {
            tasksMerged.get().toFloat() / totalTasks.get()
        } else 0f
        
        _batchStats.value = BatchStats(
            totalTasks = totalTasks.get(),
            totalBatches = totalBatches.get(),
            averageBatchSize = avgBatchSize,
            tasksMerged = tasksMerged.get(),
            averageWaitTimeMs = avgWaitTime,
            efficiency = mergeRate
        )
    }
}
