package comkuaihuiai.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 可绘AI v3.0 模型下载服务
 */
class ModelDownloadService private constructor(private val context: Context) {
    
    companion object {
        const val ACTION_START = "com.kehui.ai.ACTION_DOWNLOAD_START"
        const val ACTION_PROGRESS = "com.kehui.ai.ACTION_DOWNLOAD_PROGRESS"
        const val ACTION_COMPLETE = "com.kehui.ai.ACTION_DOWNLOAD_COMPLETE"
        const val EXTRA_MODEL_ID = "model_id"
        const val EXTRA_MODEL_NAME = "model_name"
        const val EXTRA_DOWNLOAD_URL = "download_url"
        const val EXTRA_IS_ZIP = "is_zip"
        
        @Volatile
        private var INSTANCE: ModelDownloadService? = null
        
        fun getInstance(context: Context): ModelDownloadService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModelDownloadService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()
    
    /**
     * 下载模型
     */
    fun downloadModel(
        modelId: String,
        url: String,
        onProgress: (Float) -> Unit,
        onComplete: (Boolean, String?) -> Unit
    ) {
        // TODO: 实现真实的下载逻辑
        Thread {
            for (i in 1..100) {
                Thread.sleep(50)
                onProgress(i / 100f)
                updateProgress(modelId, i / 100f)
            }
            onComplete(true, null)
        }.start()
    }
    
    private fun updateProgress(modelId: String, progress: Float) {
        val current = _downloadProgress.value.toMutableMap()
        current[modelId] = progress
        _downloadProgress.value = current
    }
    
    /**
     * 取消下载
     */
    fun cancelDownload(modelId: String) {
        val current = _downloadProgress.value.toMutableMap()
        current.remove(modelId)
        _downloadProgress.value = current
    }
    
    /**
     * 获取下载状态
     */
    fun isDownloading(modelId: String): Boolean {
        return _downloadProgress.value.containsKey(modelId)
    }
}
