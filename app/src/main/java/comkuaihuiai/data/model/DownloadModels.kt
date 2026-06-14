package comkuaihuiai.data.model

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 模型类别
 */
enum class ModelCategory {
    ANIME,      // 动漫风格
    REALISTIC,  // 写实风格
    GENERAL,    // 通用
    SDXL        // SDXL模型
}

/**
 * Stable Diffusion 模型定义
 */
@Immutable
data class SDModel(
    val id: String,
    val name: String,
    val description: String,
    val baseUrl: String = "https://huggingface.co/Bullobis/kehuiai-models/resolve/main",
    val fileUri: String,
    val approximateSize: String = "1GB",
    val isDownloaded: Boolean = false,
    val textEmbeddingSize: Int = 768,
    val runOnCpu: Boolean = false,
    val useCpuClip: Boolean = false,
    val isSdxl: Boolean = false,
    val defaultPrompt: String = "",
    val defaultNegativePrompt: String = "",
    val category: ModelCategory = ModelCategory.ANIME
)

/**
 * 模型下载状态
 */
sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : DownloadState()
    object Completed : DownloadState()
    data class Error(val message: String) : DownloadState()
}

/**
 * 默认SD模型列表
 */
object DefaultModels {
    
    // 动漫风格模型 (NPU)
    val animeModels = listOf(
        SDModel(
            id = "anythingv5",
            name = "Anything V5",
            description = "高质量动漫风格模型，支持多种姿态和服装",
            fileUri = "anything-v5/models/anything-v5-pruned.onnx",
            approximateSize = "3.5GB",
            textEmbeddingSize = 768,
            category = ModelCategory.ANIME
        ),
        SDModel(
            id = "qteamix",
            name = "QteaMix V2",
            description = "可爱的动漫风格插画模型",
            fileUri = "qteamix/QteaMixV2_fp16.onnx",
            approximateSize = "3.2GB",
            textEmbeddingSize = 768,
            category = ModelCategory.ANIME
        ),
        SDModel(
            id = "cuteyukimix",
            name = "CuteYukimix",
            description = "甜美可爱的动漫风格",
            fileUri = "cuteyukimix/cuteyukimix_momo_f16.onnx",
            approximateSize = "2.8GB",
            textEmbeddingSize = 768,
            category = ModelCategory.ANIME
        )
    )
    
    // 写实风格模型 (NPU)
    val realisticModels = listOf(
        SDModel(
            id = "absolutereality",
            name = "Absolute Reality",
            description = "高度逼真的写实风格模型",
            fileUri = "absolutereality/absolutereality_v16.safetensors",
            approximateSize = "4GB",
            textEmbeddingSize = 768,
            category = ModelCategory.REALISTIC
        ),
        SDModel(
            id = "chilloutmix",
            name = "ChilloutMix",
            description = "亚洲人物写实风格",
            fileUri = "chilloutmix/chilloutmix_NiPruned_fp32.safetensors",
            approximateSize = "3.8GB",
            textEmbeddingSize = 768,
            category = ModelCategory.REALISTIC
        )
    )
    
    // CPU运行模型
    val cpuModels = listOf(
        SDModel(
            id = "anythingv5cpu",
            name = "Anything V5 (CPU)",
            description = "CPU优化版动漫模型",
            fileUri = "cpu/anything-v5-cpu.onnx",
            approximateSize = "2.5GB",
            runOnCpu = true,
            textEmbeddingSize = 768,
            category = ModelCategory.ANIME
        ),
        SDModel(
            id = "chilloutmixcpu",
            name = "ChilloutMix (CPU)",
            description = "CPU优化版写实模型",
            fileUri = "cpu/chilloutmix-cpu.onnx",
            approximateSize = "2.8GB",
            runOnCpu = true,
            textEmbeddingSize = 768,
            category = ModelCategory.REALISTIC
        )
    )
    
    // SDXL模型
    val sdxlModels = listOf(
        SDModel(
            id = "sdxl_base",
            name = "SDXL 1.0 Base",
            description = "Stable Diffusion XL基础模型",
            fileUri = "sdxl/sd_xl_base_1.0.safetensors",
            approximateSize = "6.5GB",
            isSdxl = true,
            textEmbeddingSize = 768,
            category = ModelCategory.SDXL
        ),
        SDModel(
            id = "sdxl_refiner",
            name = "SDXL 1.0 Refiner",
            description = "SDXL精细化模型",
            fileUri = "sdxl/sd_xl_refiner_1.0.safetensors",
            approximateSize = "6GB",
            isSdxl = true,
            textEmbeddingSize = 768,
            category = ModelCategory.SDXL
        )
    )
    
    // 获取所有模型
    fun getAllModels(): List<SDModel> {
        return animeModels + realisticModels + cpuModels + sdxlModels
    }
    
    // 获取NPU模型
    fun getNpuModelsList(): List<SDModel> {
        return animeModels + realisticModels + sdxlModels
    }
    
    // 获取CPU模型
    fun getCpuModelsList(): List<SDModel> {
        return cpuModels
    }
}
