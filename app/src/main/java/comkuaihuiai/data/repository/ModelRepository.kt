package comkuaihuiai.data.repository

import android.content.Context
import comkuaihuiai.data.model.DefaultModels
import comkuaihuiai.data.model.SDModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Model Repository - Manages SD models
 */
class ModelRepository(private val context: Context) {
    
    private val _models = MutableStateFlow(DefaultModels.getAllModels())
    val models: StateFlow<List<SDModel>> = _models
    
    /**
     * Check if device supports NPU (Qualcomm)
     */
    fun isNpuSupported(): Boolean {
        val soc = android.os.Build.SOC_MODEL
        return soc.startsWith("SM") || soc.startsWith("QCS") || soc.startsWith("MSM")
    }
    
    /**
     * Get models directory
     */
    fun getModelsDir(): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
    
    /**
     * Check if a model is downloaded
     */
    fun isModelDownloaded(modelId: String): Boolean {
        val modelDir = File(getModelsDir(), modelId)
        return modelDir.exists() && modelDir.list()?.isNotEmpty() == true
    }
    
    /**
     * Get download URL for a model
     */
    fun getDownloadUrl(model: SDModel): String {
        return "https://huggingface.co/Bullobis/kehuiai-models/resolve/main/${model.fileUri}"
    }
    
    /**
     * Delete a model
     */
    fun deleteModel(modelId: String): Boolean {
        return try {
            val modelDir = File(getModelsDir(), modelId)
            if (modelDir.exists()) {
                modelDir.deleteRecursively()
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Refresh model download states
     */
    fun refreshModels() {
        _models.value = _models.value.map { model ->
            model.copy(isDownloaded = isModelDownloaded(model.id))
        }
    }
}
