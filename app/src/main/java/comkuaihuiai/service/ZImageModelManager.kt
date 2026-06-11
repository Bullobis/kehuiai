package comkuaihuiai.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * 可绘AI v3.0 - Z-Image 模型管理器
 * 
 * Z-Image 是阿里巴巴通义实验室 Tongyi-MAI 推出的开源图像生成模型
 * GitHub: https://github.com/Tongyi-MAI/Z-Image
 */
class ZImageModelManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ZImageModel"
        
        // 模型来源
        const val HUGGINGFACE_REPO = "Tongyi-MAI/Z-Image"
        const val MODEL_DIR = "models/zimage"
        
        @Volatile
        private var INSTANCE: ZImageModelManager? = null
        
        fun getInstance(context: Context): ZImageModelManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ZImageModelManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Z-Image 模型类型
     */
    enum class ZImageModelType(val displayName: String, val size: Long, val description: String) {
        Z_IMAGE_STANDARD("Z-Image 标准版", 4L * 1024 * 1024 * 1024, "基础图像生成，约4GB"),
        Z_IMAGE_PRO("Z-Image 专业版", 7L * 1024 * 1024 * 1024, "高质量图像生成，约7GB"),
        Z_IMAGE_HD("Z-Image HD", 8L * 1024 * 1024 * 1024, "2K高清图像生成，约8GB")
    }
    
    sealed class ModelState {
        object NotDownloaded : ModelState()
        data class Downloading(val progress: Float, val downloaded: Long, val total: Long) : ModelState()
        object Downloaded : ModelState()
        data class Error(val message: String) : ModelState()
    }
    
    data class ZImageModelInfo(
        val type: ZImageModelType,
        val localPath: String,
        val state: ModelState
    )
    
    private val _models = MutableStateFlow<Map<ZImageModelType, ZImageModelInfo>>(emptyMap())
    val models: StateFlow<Map<ZImageModelType, ZImageModelInfo>> = _models.asStateFlow()
    
    private val _selectedModel = MutableStateFlow(ZImageModelType.Z_IMAGE_STANDARD)
    val selectedModel: StateFlow<ZImageModelType> = _selectedModel.asStateFlow()
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    
    init {
        // 初始化模型状态
        ZImageModelType.entries.forEach { type ->
            val localPath = getModelPath(type)
            val state = if (localPath.exists()) ModelState.Downloaded else ModelState.NotDownloaded
            _models.value = _models.value + (type to ZImageModelInfo(type, localPath.absolutePath, state))
        }
    }
    
    fun getModelPath(type: ZImageModelType): File {
        return File(context.filesDir, "$MODEL_DIR/${type.name.lowercase()}")
    }
    
    fun selectModel(type: ZImageModelType) {
        _selectedModel.value = type
    }
    
    fun downloadModel(type: ZImageModelType) {
        downloadJob?.cancel()
        downloadJob = scope.launch {
            try {
                val path = getModelPath(type)
                path.mkdirs()
                
                updateModelState(type, ModelState.Downloading(0f, 0, type.size))
                
                // 模拟下载进度
                for (i in 0..100) {
                    delay(100)
                    val progress = i / 100f
                    updateModelState(type, ModelState.Downloading(progress, (type.size * progress).toLong(), type.size))
                }
                
                File(path, "model.bin").createNewFile()
                updateModelState(type, ModelState.Downloaded)
                Log.i(TAG, "Z-Image ${type.displayName} 下载完成")
                
            } catch (e: CancellationException) {
                updateModelState(type, ModelState.NotDownloaded)
            } catch (e: Exception) {
                Log.e(TAG, "下载失败", e)
                updateModelState(type, ModelState.Error(e.message ?: "下载失败"))
            }
        }
    }
    
    fun deleteModel(type: ZImageModelType) {
        val path = getModelPath(type)
        if (path.exists()) {
            path.deleteRecursively()
        }
        updateModelState(type, ModelState.NotDownloaded)
    }
    
    fun cancelDownload(type: ZImageModelType) {
        if (_models.value[type]?.state is ModelState.Downloading) {
            downloadJob?.cancel()
            updateModelState(type, ModelState.NotDownloaded)
        }
    }
    
    fun getDownloadedModels(): List<ZImageModelType> {
        return _models.value.filter { it.value.state == ModelState.Downloaded }.keys.toList()
    }
    
    fun isModelDownloaded(type: ZImageModelType): Boolean {
        return _models.value[type]?.state == ModelState.Downloaded
    }
    
    fun getModelInfo(type: ZImageModelType): ZImageModelInfo? {
        return _models.value[type]
    }
    
    private fun updateModelState(type: ZImageModelType, state: ModelState) {
        val info = _models.value[type] ?: return
        _models.value = _models.value + (type to info.copy(state = state))
    }
}
