package com.kehuiai.service.advanced

import android.content.Context
import android.util.Log
import com.kehuiai.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 快绘AI v3.5.0 超级模型管理引擎
 * 多源下载 + 智能选择 + 国内/国外镜像
 */
class SuperModelManager(private val context: Context) {

    companion object {
        private const val TAG = "SuperModelManager"
        
        // 下载超时 (秒)
        const val DOWNLOAD_TIMEOUT = 300
        
        // 最大并发下载
        const val MAX_CONCURRENT_DOWNLOADS = 3
        
        // 分块大小 (MB)
        const val CHUNK_SIZE = 10 * 1024 * 1024L
    }

    // ========== 模型节点类型 ==========
    
    enum class NodeType(
        val displayName: String,
        val description: String
    ) {
        // 国内节点
        HUGGINGFACE_CN("HuggingFace 镜像", "国内加速访问"),
        MODELSCOPE("ModelScope", "阿里魔搭"),
        WISPAI("WispAI", "AI 模型市场"),
        JIQUAN("极速光年", "高速下载"),
        CHEEREGO("奇怪云", "免费镜像"),
        HuggingFace("HF官方", "HuggingFace 官方"),
        CIVITAI("CivitAI", "AI 模型社区"),
        
        // AI 处理
        OPENAI_CN("国内AI", "国内访问"),
        OPENAI("OpenAI", "官方API"),
        
        // 加速服务
        ACCELERATE("加速通道", "高速通道")
    }
    
    // ========== 下载源配置 ==========
    
    data class DownloadSource(
        val id: String,
        val name: String,
        val node: NodeType,
        val baseUrl: String,
        val priority: Int,  // 优先级 1-10
        val speed: DownloadSpeed,
        val isCN: Boolean,  // 是否国内
        val requiresProxy: Boolean = false,
        val requiresAuth: Boolean = false,
        val authToken: String? = null,
        val mirrorOf: String? = null,  // 镜像源
        val testUrl: String? = null,
        val testTimeout: Int = 5000
    )
    
    enum class DownloadSpeed {
        VERY_FAST, FAST, NORMAL, SLOW
    }
    
    // ========== 模型信息 ==========
    
    data class SuperModel(
        val id: String,
        val name: String,
        val description: String,
        val type: ModelType,
        val baseModel: BaseModelType,
        val version: String,
        val tags: List<String>,
        val rating: Float,
        val downloads: Long,
        val isNSFW: Boolean,
        val minRAM: Int,  // MB
        val recommendedRAM: Int,  // MB
        val fileSize: Long,  // bytes
        val hash: String,
        val baseModelRequired: String,
        val recommendedParams: RecommendedParams,
        val sources: List<ModelSource2>,
        val previewImages: List<String>,
        val author: String,
        val createdAt: Long,
        val updatedAt: Long
    )
    
    data class ModelSource2(
        val sourceId: String,
        val node: NodeType,
        val downloadUrl: String,
        val filename: String,
        val fileSize: Long,
        val hash: String?,
        val priority: Int,
        val isCN: Boolean,
        val requiresProxy: Boolean,
        val avgSpeed: DownloadSpeed,
        val successRate: Float,  // 成功率
        val lastTested: Long,
        val directDownload: Boolean = true
    )
    
    data class RecommendedParams(
        val width: Int,
        val height: Int,
        val steps: Int,
        val guidance: Float,
        val scheduler: SchedulerType,
        val strength: Float? = null,
        val clipSkip: Int? = null,
        val loraLinks: List<LoraLink> = emptyList(),
        val negativePrompt: String = ""
    )
    
    data class LoraLink(
        val modelId: String,
        val name: String,
        val weight: Float,
        val downloadUrl: String
    )
    
    // ========== 下载任务 ==========
    
    data class DownloadTask(
        val id: String,
        val modelId: String,
        val modelName: String,
        val source: ModelSource2,
        val destinationPath: String,
        val totalBytes: Long,
        val downloadedBytes: Long,
        val status: DownloadStatus,
        val progress: Float,
        val speed: Long,  // bytes per second
        val eta: Long,  // seconds remaining
        val error: String?,
        val startedAt: Long,
        val completedAt: Long?,
        val retries: Int,
        val resumeSupported: Boolean
    )
    
    enum class DownloadStatus {
        QUEUED,      // 排队中
        TESTING,     // 测试中
        DOWNLOADING, // 下载中
        PAUSED,      // 暂停
        COMPLETED,   // 完成
        FAILED,      // 失败
        CANCELLED    // 取消
    }
    
    // ========== 下载策略 ==========
    
    enum class DownloadStrategy(
        val displayName: String,
        val description: String
    ) {
        AUTO("自动选择", "自动选择最优源"),
        SPEED_FIRST("速度优先", "优先选择最快的源"),
        STABILITY_FIRST("稳定优先", "优先选择最稳定的源"),
        CN_FIRST("国内优先", "优先选择国内镜像"),
        HF_FIRST("HF优先", "优先选择 HuggingFace"),
        MANUAL("手动选择", "手动选择下载地址")
    }
    
    // ========== 模型分类 ==========
    
    enum class ModelCategory(
        val displayName: String,
        val emoji: String,
        val description: String,
        val icon: String
    ) {
        // 风格
        REALISTIC("真实摄影", "📷", "逼真摄影风格", "photo_camera"),
        ANIME("动漫插画", "🎨", "精美动漫风格", "brush"),
        SEMIREALISTIC("半真实", "🖼️", "介于真实与动漫", "image"),
        FANTASY("奇幻魔法", "✨", "魔法奇幻元素", "auto_awesome"),
        COMIC("美式漫画", "📚", "美式漫画风格", "menu_book"),
        ABSTRACT("抽象艺术", "🎭", "抽象艺术风格", "palette"),
        
        // 用途
        PORTRAIT("人像写真", "👤", "人物肖像写真", "person"),
        FASHION("时尚穿搭", "👗", "服装时尚设计", "checkroom"),
        LANDSCAPE("自然风景", "🏔️", "自然风景", "landscape"),
        ARCHITECTURE("建筑室内", "🏛️", "建筑设计", "apartment"),
        PRODUCT("产品展示", "📦", "商业产品", "inventory_2"),
        GAME_ASSET("游戏资产", "🎮", "游戏角色道具", "sports_esports"),
        
        // 技术
        LORA("LoRA模型", "🔧", "LoRA 微调模型", "tune"),
        CONTROLNET("ControlNet", "🎛️", "控制网络模型", "settings_input_component"),
        VAE("VAE模型", "🎬", "变分自编码器", "movie"),
        UPSCALE("超分模型", "🔍", "图像超分辨率", "zoom_in"),
        EMBEDDING("词嵌入", "💾", "Textual Inversion", "text_fields"),
        
        // 基础
        CHECKPOINT("检查点", "📦", "完整检查点模型", "archive"),
        SDXL("SDXL", "🔷", "SDXL 系列模型", "filter_9_plus")
    }
    
    // ========== 核心组件 ==========
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloadSources = ConcurrentHashMap<String, DownloadSource>()
    private val modelDatabase = ConcurrentHashMap<String, SuperModel>()
    private val downloadQueue = ConcurrentHashMap<String, DownloadTask>()
    private val downloadHistory = ConcurrentHashMap<String, List<DownloadTask>>()
    
    private val _downloadProgress = MutableSharedFlow<DownloadProgress>()
    val downloadProgress: SharedFlow<DownloadProgress> = _downloadProgress.asSharedFlow()
    
    private var currentStrategy = DownloadStrategy.AUTO
    private var useProxy = false
    private var preferredNode: NodeType? = null
    
    // ========== 初始化 ==========
    
    init {
        initializeSources()
        initializeModelDatabase()
    }
    
    // ========== 初始化下载源 ==========
    
    private fun initializeSources() {
        Log.i(TAG, "🌐 初始化下载源...")
        
        // ========== 国内镜像源 ==========
        
        addSource(DownloadSource(
            id = "modelscope",
            name = "ModelScope 魔搭",
            node = NodeType.MODELSCOPE,
            baseUrl = "https://modelscope.cn/models",
            priority = 9,
            speed = DownloadSpeed.VERY_FAST,
            isCN = true,
            requiresProxy = false,
            mirrorOf = "huggingface"
        ))
        
        addSource(DownloadSource(
            id = "wispai",
            name = "WispAI",
            node = NodeType.WISPAI,
            baseUrl = "https://model.wispai.top",
            priority = 8,
            speed = DownloadSpeed.FAST,
            isCN = true,
            requiresProxy = false,
            mirrorOf = "huggingface"
        ))
        
        addSource(DownloadSource(
            id = "jiquan",
            name = "极速光年",
            node = NodeType.JIQUAN,
            baseUrl = "https://hf-mirror.com",
            priority = 8,
            speed = DownloadSpeed.VERY_FAST,
            isCN = true,
            requiresProxy = false,
            mirrorOf = "huggingface"
        ))
        
        addSource(DownloadSource(
            id = "cheerego",
            name = "奇怪云",
            node = NodeType.CHEEREGO,
            baseUrl = "https://hf.co",  // 使用 CDN
            priority = 7,
            speed = DownloadSpeed.FAST,
            isCN = true,
            requiresProxy = false,
            mirrorOf = "huggingface"
        ))
        
        addSource(DownloadSource(
            id = "huggingface_cn",
            name = "HF 国内镜像",
            node = NodeType.HUGGINGFACE_CN,
            baseUrl = "https://hf-mirror.com",
            priority = 9,
            speed = DownloadSpeed.VERY_FAST,
            isCN = true,
            requiresProxy = false,
            mirrorOf = "huggingface"
        ))
        
        // ========== 官方源 ==========
        
        addSource(DownloadSource(
            id = "huggingface",
            name = "HuggingFace 官方",
            node = NodeType.HuggingFace,
            baseUrl = "https://huggingface.co",
            priority = 7,
            speed = DownloadSpeed.NORMAL,
            isCN = false,
            requiresProxy = true,
            requiresAuth = false
        ))
        
        addSource(DownloadSource(
            id = "civitai",
            name = "CivitAI",
            node = NodeType.CIVITAI,
            baseUrl = "https://civitai.com",
            priority = 8,
            speed = DownloadSpeed.FAST,
            isCN = false,
            requiresProxy = true,
            requiresAuth = false
        ))
        
        // ========== 加速源 ==========
        
        addSource(DownloadSource(
            id = "accelerate",
            name = "AI 加速通道",
            node = NodeType.ACCELERATE,
            baseUrl = "https://加速服务.com",
            priority = 6,
            speed = DownloadSpeed.VERY_FAST,
            isCN = true,
            requiresProxy = false
        ))
        
        Log.i(TAG, "✅ 已初始化 ${downloadSources.size} 个下载源")
    }
    
    private fun addSource(source: DownloadSource) {
        downloadSources[source.id] = source
    }
    
    // ========== 初始化模型数据库 ==========
    
    private fun initializeModelDatabase() {
        Log.i(TAG, "📦 初始化模型数据库...")
        
        // ========== MajicMix 系列 ==========
        addSuperModel(SuperModel(
            id = "majicmix_realistic_v7",
            name = "MajicMix Realistic v7",
            description = "高质量真实人像模型，细节丰富，光影自然。适合写真、产品展示等。亚洲审美优化。",
            type = ModelType.CHECKPOINT,
            baseModel = BaseModelType.SD_1_5,
            version = "v7.0",
            tags = listOf("realistic", "photorealistic", "portrait", "asian", "female", "beauty"),
            rating = 4.8f,
            downloads = 2800000,
            isNSFW = false,
            minRAM = 4096,
            recommendedRAM = 8192,
            fileSize = 2_000_000_000,  // 2GB
            hash = "a8fa4d3c",
            baseModelRequired = "SD 1.5",
            recommendedParams = RecommendedParams(
                width = 512,
                height = 768,
                steps = 25,
                guidance = 7.5f,
                scheduler = SchedulerType.DPMSOLVER_PLUS_PLUS_2M_KARRAS,
                clipSkip = 2,
                negativePrompt = "anime, cartoon, 3d render, painting, drawing, illustration, blurry, low quality"
            ),
            sources = listOf(
                ModelSource2("ms_majicmix_v7", NodeType.MODELSCOPE, 
                    "https://modelscope.cn/models/AI-ModelScope/MajicMixRealistic/summary",
                    "majicmix_realistic_v7.safetensors", 2_000_000_000, 
                    "a8fa4d3c", 9, true, false, DownloadSpeed.VERY_FAST, 0.98f, System.currentTimeMillis()),
                ModelSource2("hf_majicmix_v7", NodeType.HuggingFace,
                    "https://huggingface.co/Albedo-base/Albedo-base/resolve/main/majicmix_realistic_v7.safetensors",
                    "majicmix_realistic_v7.safetensors", 2_000_000_000,
                    "a8fa4d3c", 7, false, true, DownloadSpeed.NORMAL, 0.95f, System.currentTimeMillis()),
                ModelSource2("civitai_majicmix_v7", NodeType.CIVITAI,
                    "https://civitai.com/models/43331/majicmix-realistic",
                    "majicmix_realistic_v7.safetensors", 2_000_000_000,
                    "a8fa4d3c", 8, false, true, DownloadSpeed.FAST, 0.92f, System.currentTimeMillis()),
                ModelSource2("jiquan_majicmix_v7", NodeType.JIQUAN,
                    "https://hf-mirror.com/Albedo-base/Albedo-base/resolve/main/majicmix_realistic_v7.safetensors",
                    "majicmix_realistic_v7.safetensors", 2_000_000_000,
                    "a8fa4d3c", 9, true, false, DownloadSpeed.VERY_FAST, 0.99f, System.currentTimeMillis())
            ),
            previewImages = listOf(
                "https://image.civitai.com/43331/init1.jpeg",
                "https://image.civitai.com/43331/init2.jpeg"
            ),
            author = "Mick",
            createdAt = 1700000000000,
            updatedAt = 1710000000000
        ))
        
        // ========== Realistic Vision ==========
        addSuperModel(SuperModel(
            id = "realistic_vision_v51",
            name = "Realistic Vision v5.1",
            description = "专注于真实摄影风格，人物和物体都非常逼真。适合产品摄影、人像写真。",
            type = ModelType.CHECKPOINT,
            baseModel = BaseModelType.SD_1_5,
            version = "v5.1",
            tags = listOf("realistic", "photorealistic", "portrait", "product", "photography", "cinematic"),
            rating = 4.8f,
            downloads = 3200000,
            isNSFW = false,
            minRAM = 4096,
            recommendedRAM = 8192,
            fileSize = 2_000_000_000,
            hash = "b8c2a6f0",
            baseModelRequired = "SD 1.5",
            recommendedParams = RecommendedParams(
                width = 512,
                height = 768,
                steps = 25,
                guidance = 7.5f,
                scheduler = SchedulerType.DPMSOLVER_PLUS_PLUS_2M_KARRAS,
                negativePrompt = "anime, cartoon, painting, drawing, illustration, 3d render, blurry"
            ),
            sources = listOf(
                ModelSource2("ms_rv51", NodeType.MODELSCOPE,
                    "https://modelscope.cn/models/AI-ModelScope/Realistic_Vision_V5.1_noVAE/summary",
                    "Realistic_Vision_V5.1.safetensors", 2_000_000_000,
                    "b8c2a6f0", 9, true, false, DownloadSpeed.VERY_FAST, 0.98f, System.currentTimeMillis()),
                ModelSource2("hf_rv51", NodeType.HuggingFace,
                    "https://huggingface.co/stablediffusionapi/realistic-vision-v51/resolve/main/realisticVisionV51_v51VAE.safetensors",
                    "Realistic_Vision_V5.1.safetensors", 2_000_000_000,
                    "b8c2a6f0", 7, false, true, DownloadSpeed.NORMAL, 0.95f, System.currentTimeMillis()),
                ModelSource2("civitai_rv51", NodeType.CIVITAI,
                    "https://civitai.com/models/5746/realistic-vision",
                    "Realistic_Vision_V5.1.safetensors", 2_000_000_000,
                    "b8c2a6f0", 8, false, true, DownloadSpeed.FAST, 0.93f, System.currentTimeMillis()),
                ModelSource2("jiquan_rv51", NodeType.JIQUAN,
                    "https://hf-mirror.com/stablediffusionapi/realistic-vision-v51/resolve/main/realisticVisionV51_v51VAE.safetensors",
                    "Realistic_Vision_V5.1.safetensors", 2_000_000_000,
                    "b8c2a6f0", 9, true, false, DownloadSpeed.VERY_FAST, 0.99f, System.currentTimeMillis())
            ),
            previewImages = emptyList(),
            author = "SG_161222",
            createdAt = 1680000000000,
            updatedAt = 1715000000000
        ))
        
        // ========== DreamShaper ==========
        addSuperModel(SuperModel(
            id = "dreamshaper_v8",
            name = "DreamShaper v8",
            description = "通用型模型，真实与创意兼顾。社区热门首选，适合各种场景。",
            type = ModelType.CHECKPOINT,
            baseModel = BaseModelType.SD_1_5,
            version = "v8.0",
            tags = listOf("general", "realistic", "illustration", "fantasy", "versatile", "popular"),
            rating = 4.7f,
            downloads = 3500000,
            isNSFW = false,
            minRAM = 4096,
            recommendedRAM = 8192,
            fileSize = 2_000_000_000,
            hash = "c4d3f8e1",
            baseModelRequired = "SD 1.5",
            recommendedParams = RecommendedParams(
                width = 512,
                height = 512,
                steps = 25,
                guidance = 7.0f,
                scheduler = SchedulerType.DPMSOLVER_PLUS_PLUS_2M_KARRAS
            ),
            sources = listOf(
                ModelSource2("ms_ds8", NodeType.MODELSCOPE,
                    "https://modelscope.cn/models/AI-ModelScope/DreamShaper_8/summary",
                    "dreamshaper_v8.safetensors", 2_000_000_000,
                    "c4d3f8e1", 9, true, false, DownloadSpeed.VERY_FAST, 0.98f, System.currentTimeMillis()),
                ModelSource2("civitai_ds8", NodeType.CIVITAI,
                    "https://civitai.com/models/4384/dreamshaper",
                    "dreamshaper_v8.safetensors", 2_000_000_000,
                    "c4d3f8e1", 8, false, true, DownloadSpeed.FAST, 0.94f, System.currentTimeMillis()),
                ModelSource2("hf_ds8", NodeType.HuggingFace,
                    "https://huggingface.co/Lykon/DreamShaper/resolve/main/DreamShaper_8.safetensors",
                    "dreamshaper_v8.safetensors", 2_000_000_000,
                    "c4d3f8e1", 7, false, true, DownloadSpeed.NORMAL, 0.95f, System.currentTimeMillis()),
                ModelSource2("jiquan_ds8", NodeType.JIQUAN,
                    "https://hf-mirror.com/Lykon/DreamShaper/resolve/main/DreamShaper_8.safetensors",
                    "dreamshaper_v8.safetensors", 2_000_000_000,
                    "c4d3f8e1", 9, true, false, DownloadSpeed.VERY_FAST, 0.99f, System.currentTimeMillis())
            ),
            previewImages = emptyList(),
            author = "Lykon",
            createdAt = 1670000000000,
            updatedAt = 1700000000000
        ))
        
        // ========== SDXL Lightning ==========
        addSuperModel(SuperModel(
            id = "sdxl_lightning",
            name = "SDXL Lightning",
            description = "SDXL 高速模型，4步出图，质量优秀。适合快速预览。",
            type = ModelType.CHECKPOINT,
            baseModel = BaseModelType.SD_XL_LIGHTNING,
            version = "1.0",
            tags = listOf("xl", "lightning", "fast", "quality", "8-step", "4-step"),
            rating = 4.6f,
            downloads = 950000,
            isNSFW = false,
            minRAM = 8192,
            recommendedRAM = 16384,
            fileSize = 6_500_000_000,  // 6.5GB
            hash = "d5e2a9c7",
            baseModelRequired = "SDXL",
            recommendedParams = RecommendedParams(
                width = 1024,
                height = 1024,
                steps = 4,
                guidance = 1.0f,
                scheduler = SchedulerType.LCM
            ),
            sources = listOf(
                ModelSource2("ms_sdxl_l", NodeType.MODELSCOPE,
                    "https://modelscope.cn/models/AI-ModelScope/SDXL-Lightning/summary",
                    "sdxl_lightning.safetensors", 6_500_000_000,
                    "d5e2a9c7", 9, true, false, DownloadSpeed.VERY_FAST, 0.98f, System.currentTimeMillis()),
                ModelSource2("civitai_sdxl_l", NodeType.CIVITAI,
                    "https://civitai.com/models/118901/sdxl-lightning",
                    "sdxl_lightning.safetensors", 6_500_000_000,
                    "d5e2a9c7", 8, false, true, DownloadSpeed.FAST, 0.93f, System.currentTimeMillis()),
                ModelSource2("hf_sdxl_l", NodeType.HuggingFace,
                    "https://huggingface.co/ByteDance/SDXL-Lightning/resolve/main/sdxl_lightning_4step.safetensors",
                    "sdxl_lightning_4step.safetensors", 6_500_000_000,
                    "d5e2a9c7", 7, false, true, DownloadSpeed.NORMAL, 0.96f, System.currentTimeMillis()),
                ModelSource2("jiquan_sdxl_l", NodeType.JIQUAN,
                    "https://hf-mirror.com/ByteDance/SDXL-Lightning/resolve/main/sdxl_lightning_4step.safetensors",
                    "sdxl_lightning_4step.safetensors", 6_500_000_000,
                    "d5e2a9c7", 9, true, false, DownloadSpeed.VERY_FAST, 0.99f, System.currentTimeMillis())
            ),
            previewImages = emptyList(),
            author = "ByteDance",
            createdAt = 1690000000000,
            updatedAt = 1705000000000
        ))
        
        // ========== Animagine XL ==========
        addSuperModel(SuperModel(
            id = "animagine_xl_31",
            name = "Animagine XL 3.1",
            description = "SDXL 动漫模型，质量优秀，社区活跃更新。标签优化，生成稳定。",
            type = ModelType.CHECKPOINT,
            baseModel = BaseModelType.SD_XL,
            version = "3.1",
            tags = listOf("xl", "anime", "illustration", "quality", "stable", "tag-optimized"),
            rating = 4.8f,
            downloads = 1400000,
            isNSFW = false,
            minRAM = 8192,
            recommendedRAM = 16384,
            fileSize = 6_500_000_000,
            hash = "e6f1b8d4",
            baseModelRequired = "SDXL",
            recommendedParams = RecommendedParams(
                width = 1024,
                height = 1024,
                steps = 30,
                guidance = 8.0f,
                scheduler = SchedulerType.EULER_ANCESTRAL,
                clipSkip = 2
            ),
            sources = listOf(
                ModelSource2("ms_animagine", NodeType.MODELSCOPE,
                    "https://modelscope.cn/models/AI-ModelScope/Animagine-XL-3.1/summary",
                    "animagine_xl_3.1.safetensors", 6_500_000_000,
                    "e6f1b8d4", 9, true, false, DownloadSpeed.VERY_FAST, 0.98f, System.currentTimeMillis()),
                ModelSource2("civitai_animagine", NodeType.CIVITAI,
                    "https://civitai.com/models/26056/animagine-xl",
                    "animagine_xl_3.1.safetensors", 6_500_000_000,
                    "e6f1b8d4", 8, false, true, DownloadSpeed.FAST, 0.94f, System.currentTimeMillis()),
                ModelSource2("hf_animagine", NodeType.HuggingFace,
                    "https://huggingface.co/cagliostrolab/animagine-xl-3.1/resolve/main/animagine-xl-3.1.safetensors",
                    "animagine_xl_3.1.safetensors", 6_500_000_000,
                    "e6f1b8d4", 7, false, true, DownloadSpeed.NORMAL, 0.96f, System.currentTimeMillis()),
                ModelSource2("jiquan_animagine", NodeType.JIQUAN,
                    "https://hf-mirror.com/cagliostrolab/animagine-xl-3.1/resolve/main/animagine-xl-3.1.safetensors",
                    "animagine_xl_3.1.safetensors", 6_500_000_000,
                    "e6f1b8d4", 9, true, false, DownloadSpeed.VERY_FAST, 0.99f, System.currentTimeMillis())
            ),
            previewImages = emptyList(),
            author = "Cagliostrolab",
            createdAt = 1685000000000,
            updatedAt = 1710000000000
        ))
        
        // ========== Counterfeit ==========
        addSuperModel(SuperModel(
            id = "counterfeit_v30",
            name = "Counterfeit v3.0",
            description = "高质量动漫插画模型，色彩鲜艳，线条清晰。画面精美度极高。",
            type = ModelType.CHECKPOINT,
            baseModel = BaseModelType.SD_1_5,
            version = "v3.0",
            tags = listOf("anime", "illustration", "colorful", "detailed", "2.5d"),
            rating = 4.7f,
            downloads = 2100000,
            isNSFW = false,
            minRAM = 4096,
            recommendedRAM = 8192,
            fileSize = 2_000_000_000,
            hash = "f7a2c9e5",
            baseModelRequired = "SD 1.5",
            recommendedParams = RecommendedParams(
                width = 512,
                height = 768,
                steps = 28,
                guidance = 8.0f,
                scheduler = SchedulerType.EULER_ANCESTRAL
            ),
            sources = listOf(
                ModelSource2("ms_counterfeit", NodeType.MODELSCOPE,
                    "https://modelscope.cn/models/AI-ModelScope/Counterfeit_v3.0/summary",
                    "counterfeit_v3.0.safetensors", 2_000_000_000,
                    "f7a2c9e5", 9, true, false, DownloadSpeed.VERY_FAST, 0.98f, System.currentTimeMillis()),
                ModelSource2("civitai_counterfeit", NodeType.CIVITAI,
                    "https://civitai.com/models/5765/counterfeit",
                    "counterfeit_v3.0.safetensors", 2_000_000_000,
                    "f7a2c9e5", 8, false, true, DownloadSpeed.FAST, 0.94f, System.currentTimeMillis()),
                ModelSource2("hf_counterfeit", NodeType.HuggingFace,
                    "https://huggingface.co/gsdf/Counterfeit-V3.0/resolve/main/Counterfeit-V3.0.safetensors",
                    "counterfeit_v3.0.safetensors", 2_000_000_000,
                    "f7a2c9e5", 7, false, true, DownloadSpeed.NORMAL, 0.96f, System.currentTimeMillis()),
                ModelSource2("jiquan_counterfeit", NodeType.JIQUAN,
                    "https://hf-mirror.com/gsdf/Counterfeit-V3.0/resolve/main/Counterfeit-V3.0.safetensors",
                    "counterfeit_v3.0.safetensors", 2_000_000_000,
                    "f7a2c9e5", 9, true, false, DownloadSpeed.VERY_FAST, 0.99f, System.currentTimeMillis())
            ),
            previewImages = emptyList(),
            author = "gsdf",
            createdAt = 1675000000000,
            updatedAt = 1695000000000
        ))
        
        // ========== LoRA: Detail Enhancer ==========
        addSuperModel(SuperModel(
            id = "lora_detail_enhancer",
            name = "Detail Enhancer LoRA",
            description = "增强图像细节和纹理，让画面更加清晰锐利。适用于所有基础模型。",
            type = ModelType.LORA,
            baseModel = BaseModelType.SD_1_5,
            version = "1.0",
            tags = listOf("lora", "detail", "enhance", "texture", "sharp", "quality"),
            rating = 4.5f,
            downloads = 650000,
            isNSFW = false,
            minRAM = 2048,
            recommendedRAM = 4096,
            fileSize = 150_000_000,  // 150MB
            hash = "a1b2c3d4",
            baseModelRequired = "SD 1.5 / SDXL",
            recommendedParams = RecommendedParams(
                width = 512,
                height = 512,
                steps = 25,
                guidance = 7.5f,
                scheduler = SchedulerType.DPMSOLVER_PLUS_PLUS_2M_KARRAS
            ),
            sources = listOf(
                ModelSource2("ms_detail", NodeType.MODELSCOPE,
                    "https://modelscope.cn/models/AI-ModelScope/detail_enhancer/summary",
                    "detail_enhancer.safetensors", 150_000_000,
                    "a1b2c3d4", 9, true, false, DownloadSpeed.VERY_FAST, 0.99f, System.currentTimeMillis()),
                ModelSource2("civitai_detail", NodeType.CIVITAI,
                    "https://civitai.com/models/56266/detail-enhancer",
                    "detail_enhancer.safetensors", 150_000_000,
                    "a1b2c3d4", 8, false, true, DownloadSpeed.FAST, 0.95f, System.currentTimeMillis()),
                ModelSource2("jiquan_detail", NodeType.JIQUAN,
                    "https://hf-mirror.com/h94/Detail_Enhancer/resolve/main/Detail_Enhancer.safetensors",
                    "detail_enhancer.safetensors", 150_000_000,
                    "a1b2c3d4", 9, true, false, DownloadSpeed.VERY_FAST, 0.99f, System.currentTimeMillis())
            ),
            previewImages = emptyList(),
            author = "h94",
            createdAt = 1680000000000,
            updatedAt = 1700000000000
        ))
        
        // ========== LoRA: Face Detail ==========
        addSuperModel(SuperModel(
            id = "lora_face_detail",
            name = "Face Detail LoRA",
            description = "增强面部细节，让皮肤质感更真实。适合人像摄影风格。",
            type = ModelType.LORA,
            baseModel = BaseModelType.SD_1_5,
            version = "1.0",
            tags = listOf("lora", "face", "portrait", "skin", "detail", "beauty"),
            rating = 4.6f,
            downloads = 920000,
            isNSFW = false,
            minRAM = 2048,
            recommendedRAM = 4096,
            fileSize = 140_000_000,
            hash = "b2c3d4e5",
            baseModelRequired = "SD 1.5 / SDXL",
            recommendedParams = RecommendedParams(
                width = 512,
                height = 512,
                steps = 25,
                guidance = 7.5f,
                scheduler = SchedulerType.DPMSOLVER_PLUS_PLUS_2M_KARRAS
            ),
            sources = listOf(
                ModelSource2("ms_face", NodeType.MODELSCOPE,
                    "https://modelscope.cn/models/AI-ModelScope/face_detail/summary",
                    "face_detail.safetensors", 140_000_000,
                    "b2c3d4e5", 9, true, false, DownloadSpeed.VERY_FAST, 0.99f, System.currentTimeMillis()),
                ModelSource2("civitai_face", NodeType.CIVITAI,
                    "https://civitai.com/models/173836/face-detail",
                    "face_detail.safetensors", 140_000_000,
                    "b2c3d4e5", 8, false, true, DownloadSpeed.FAST, 0.95f, System.currentTimeMillis()),
                ModelSource2("jiquan_face", NodeType.JIQUAN,
                    "https://hf-mirror.com/Binexis/face-detail/resolve/main/face_detail.safetensors",
                    "face_detail.safetensors", 140_000_000,
                    "b2c3d4e5", 9, true, false, DownloadSpeed.VERY_FAST, 0.99f, System.currentTimeMillis())
            ),
            previewImages = emptyList(),
            author = "Binexis",
            createdAt = 1690000000000,
            updatedAt = 1705000000000
        ))
        
        // ========== ControlNet Canny ==========
        addSuperModel(SuperModel(
            id = "controlnet_canny",
            name = "ControlNet Canny",
            description = "边缘检测 ControlNet，精准控制图像结构。基础控制网络模型。",
            type = ModelType.CONTROLNET,
            baseModel = BaseModelType.SD_1_5,
            version = "1.1",
            tags = listOf("controlnet", "canny", "edge", "structure", "control"),
            rating = 4.8f,
            downloads = 0,
            isNSFW = false,
            minRAM = 4096,
            recommendedRAM = 8192,
            fileSize = 1_200_000_000,  // 1.2GB
            hash = "c3d4e5f6",
            baseModelRequired = "SD 1.5 + ControlNet",
            recommendedParams = RecommendedParams(
                width = 512,
                height = 512,
                steps = 25,
                guidance = 7.5f,
                scheduler = SchedulerType.DPMSOLVER_PLUS_PLUS_2M_KARRAS
            ),
            sources = listOf(
                ModelSource2("ms_canny", NodeType.MODELSCOPE,
                    "https://modelscope.cn/models/AI-ModelScope/ControlNet/resolve/main/canny.pth",
                    "controlnet_canny.pth", 1_200_000_000,
                    "c3d4e5f6", 9, true, false, DownloadSpeed.VERY_FAST, 0.99f, System.currentTimeMillis()),
                ModelSource2("hf_canny", NodeType.HuggingFace,
                    "https://huggingface.co/lllyasviel/ControlNet-v1-1/resolve/main/control_v11p_sd15_canny.pth",
                    "controlnet_canny.pth", 1_200_000_000,
                    "c3d4e5f6", 7, false, true, DownloadSpeed.NORMAL, 0.96f, System.currentTimeMillis()),
                ModelSource2("jiquan_canny", NodeType.JIQUAN,
                    "https://hf-mirror.com/lllyasviel/ControlNet-v1-1/resolve/main/control_v11p_sd15_canny.pth",
                    "controlnet_canny.pth", 1_200_000_000,
                    "c3d4e5f6", 9, true, false, DownloadSpeed.VERY_FAST, 0.99f, System.currentTimeMillis())
            ),
            previewImages = emptyList(),
            author = "lllyasviel",
            createdAt = 1670000000000,
            updatedAt = 1690000000000
        ))
        
        // ========== ControlNet Depth ==========
        addSuperModel(SuperModel(
            id = "controlnet_depth",
            name = "ControlNet Depth",
            description = "深度图 ControlNet，控制图像深度和空间感。适合建筑和风景。",
            type = ModelType.CONTROLNET,
            baseModel = BaseModelType.SD_1_5,
            version = "1.1",
            tags = listOf("controlnet", "depth", "midas", "spatial", "control"),
            rating = 4.8f,
            downloads = 0,
            isNSFW = false,
            minRAM = 4096,
            recommendedRAM = 8192,
            fileSize = 1_200_000_000,
            hash = "d4e5f6a7",
            baseModelRequired = "SD 1.5 + ControlNet",
            recommendedParams = RecommendedParams(
                width = 512,
                height = 512,
                steps = 25,
                guidance = 7.5f,
                scheduler = SchedulerType.DPMSOLVER_PLUS_PLUS_2M_KARRAS
            ),
            sources = listOf(
                ModelSource2("ms_depth", NodeType.MODELSCOPE,
                    "https://modelscope.cn/models/AI-ModelScope/ControlNet/resolve/main/depth.pth",
                    "controlnet_depth.pth", 1_200_000_000,
                    "d4e5f6a7", 9, true, false, DownloadSpeed.VERY_FAST, 0.99f, System.currentTimeMillis()),
                ModelSource2("hf_depth", NodeType.HuggingFace,
                    "https://huggingface.co/lllyasviel/ControlNet-v1-1/resolve/main/control_v11f1p_sd15_depth.pth",
                    "controlnet_depth.pth", 1_200_000_000,
                    "d4e5f6a7", 7, false, true, DownloadSpeed.NORMAL, 0.96f, System.currentTimeMillis()),
                ModelSource2("jiquan_depth", NodeType.JIQUAN,
                    "https://hf-mirror.com/lllyasviel/ControlNet-v1-1/resolve/main/control_v11f1p_sd15_depth.pth",
                    "controlnet_depth.pth", 1_200_000_000,
                    "d4e5f6a7", 9, true, false, DownloadSpeed.VERY_FAST, 0.99f, System.currentTimeMillis())
            ),
            previewImages = emptyList(),
            author = "lllyasviel",
            createdAt = 1670000000000,
            updatedAt = 1690000000000
        ))
        
        // ========== VAE ==========
        addSuperModel(SuperModel(
            id = "vae_ft_mse",
            name = "VAE FT-MSE",
            description = "高保真 VAE 模型，用于图像编解码。细节更丰富，色彩更准确。",
            type = ModelType.VAE,
            baseModel = BaseModelType.SD_1_5,
            version = "1.0",
            tags = listOf("vae", "decoder", "encoder", "high-fidelity", "official"),
            rating = 4.7f,
            downloads = 0,
            isNSFW = false,
            minRAM = 2048,
            recommendedRAM = 4096,
            fileSize = 320_000_000,  // 320MB
            hash = "e5f6a7b8",
            baseModelRequired = "SD 1.5",
            recommendedParams = RecommendedParams(
                width = 512,
                height = 512,
                steps = 25,
                guidance = 7.5f,
                scheduler = SchedulerType.DPMSOLVER_PLUS_PLUS_2M_KARRAS
            ),
            sources = listOf(
                ModelSource2("ms_vae", NodeType.MODELSCOPE,
                    "https://modelscope.cn/models/AI-ModelScope/vae/resolve/main/vae-ft-mse-840000-ema-pruned.safetensors",
                    "vae_ft_mse.safetensors", 320_000_000,
                    "e5f6a7b8", 9, true, false, DownloadSpeed.VERY_FAST, 0.99f, System.currentTimeMillis()),
                ModelSource2("hf_vae", NodeType.HuggingFace,
                    "https://huggingface.co/stabilityai/sd-vae-ft-mse/resolve/main/vae-ft-mse-840000-ema-pruned.safetensors",
                    "vae_ft_mse.safetensors", 320_000_000,
                    "e5f6a7b8", 7, false, true, DownloadSpeed.NORMAL, 0.97f, System.currentTimeMillis()),
                ModelSource2("jiquan_vae", NodeType.JIQUAN,
                    "https://hf-mirror.com/stabilityai/sd-vae-ft-mse/resolve/main/vae-ft-mse-840000-ema-pruned.safetensors",
                    "vae_ft_mse.safetensors", 320_000_000,
                    "e5f6a7b8", 9, true, false, DownloadSpeed.VERY_FAST, 0.99f, System.currentTimeMillis())
            ),
            previewImages = emptyList(),
            author = "Stability AI",
            createdAt = 1660000000000,
            updatedAt = 1680000000000
        ))
        
        Log.i(TAG, "✅ 已加载 ${modelDatabase.size} 个超级模型")
    }
    
    private fun addSuperModel(model: SuperModel) {
        modelDatabase[model.id] = model
    }
    
    // ========== 查询 API ==========
    
    fun getAllModels(): List<SuperModel> = modelDatabase.values.toList()
    
    fun getModel(id: String): SuperModel? = modelDatabase[id]
    
    fun getModelsByCategory(category: ModelCategory): List<SuperModel> {
        return modelDatabase.values.filter { model ->
            when (category) {
                ModelCategory.REALISTIC -> model.tags.any { it in listOf("realistic", "photorealistic") }
                ModelCategory.ANIME -> model.tags.any { it == "anime" }
                ModelCategory.PORTRAIT -> model.tags.any { it == "portrait" }
                ModelCategory.LANDSCAPE -> model.tags.any { it == "landscape" }
                ModelCategory.LORA -> model.type == ModelType.LORA
                ModelCategory.CONTROLNET -> model.type == ModelType.CONTROLNET
                ModelCategory.VAE -> model.type == ModelType.VAE
                ModelCategory.UPSCALE -> model.type == ModelType.UPSCALER
                ModelCategory.CHECKPOINT -> model.type == ModelType.CHECKPOINT
                ModelCategory.SDXL -> model.baseModel == BaseModelType.SD_XL || 
                                       model.baseModel == BaseModelType.SD_XL_LIGHTNING ||
                                       model.baseModel == BaseModelType.SD_XL_TURBO
                else -> true
            }
        }
    }
    
    fun getModelsByType(type: ModelType): List<SuperModel> =
        modelDatabase.values.filter { it.type == type }
    
    fun searchModels(
        query: String,
        category: ModelCategory? = null,
        type: ModelType? = null,
        baseModel: BaseModelType? = null
    ): List<SuperModel> {
        val q = query.lowercase()
        return modelDatabase.values.filter { model ->
            val matchQuery = q.isEmpty() || 
                model.name.lowercase().contains(q) ||
                model.description.lowercase().contains(q) ||
                model.tags.any { it.lowercase().contains(q) } ||
                model.author.lowercase().contains(q)
            val matchCategory = category == null || getModelsByCategory(category).contains(model)
            val matchType = type == null || model.type == type
            val matchBase = baseModel == null || model.baseModel == baseModel
            
            matchQuery && matchType && matchBase
        }.sortedByDescending { it.downloads }
    }
    
    fun getPopularModels(limit: Int = 10): List<SuperModel> =
        modelDatabase.values.sortedByDescending { it.downloads }.take(limit)
    
    fun getRecentModels(): List<SuperModel> =
        modelDatabase.values.sortedByDescending { it.updatedAt }.take(10)
    
    fun getRecommendedModels(purpose: String): List<SuperModel> {
        return when (purpose.lowercase()) {
            "portrait", "人像", "写真" -> getModelsByCategory(ModelCategory.REALISTIC)
            "anime", "动漫" -> getModelsByCategory(ModelCategory.ANIME)
            "fast", "快速", "预览" -> modelDatabase.values.filter { 
                it.recommendedParams.steps <= 10 
            }
            "quality", "质量", "高清" -> modelDatabase.values.filter { 
                it.recommendedParams.steps >= 30 
            }
            "sdxl", "xl" -> getModelsByCategory(ModelCategory.SDXL)
            "lora" -> getModelsByCategory(ModelCategory.LORA)
            "controlnet", "控制" -> getModelsByCategory(ModelCategory.CONTROLNET)
            else -> getPopularModels()
        }
    }
    
    // ========== 下载源管理 ==========
    
    fun getAllSources(): List<DownloadSource> = downloadSources.values.sortedByDescending { it.priority }
    
    fun getSource(id: String): DownloadSource? = downloadSources[id]
    
    fun getCNSources(): List<DownloadSource> = downloadSources.values.filter { it.isCN }
    
    fun getInternationalSources(): List<DownloadSource> = downloadSources.values.filter { !it.isCN }
    
    fun testSource(sourceId: String): Flow<SourceTestResult> = flow {
        val source = downloadSources[sourceId] ?: run {
            emit(SourceTestResult(sourceId, false, "源不存在", 0))
            return@flow
        }
        
        emit(SourceTestResult(sourceId, true, "测试中...", 0))
        
        // 模拟测试延迟
        delay(500)
        
        // 根据节点返回模拟结果
        val speed = when (source.node) {
            NodeType.MODELSCOPE -> 50_000_000L  // 50 MB/s
            NodeType.HUGGINGFACE_CN -> 40_000_000L
            NodeType.JIQUAN -> 45_000_000L
            NodeType.WISPAI -> 30_000_000L
            NodeType.CHEEREGO -> 25_000_000L
            NodeType.HuggingFace -> 5_000_000L  // 慢
            NodeType.CIVITAI -> 10_000_000L
            else -> 10_000_000L
        }
        
        emit(SourceTestResult(sourceId, true, "成功", speed))
    }.flowOn(Dispatchers.IO)
    
    data class SourceTestResult(
        val sourceId: String,
        val success: Boolean,
        val message: String,
        val speed: Long  // bytes per second
    )
    
    // ========== 智能选源 ==========
    
    fun selectBestSource(modelId: String, strategy: DownloadStrategy = currentStrategy): ModelSource2? {
        val model = modelDatabase[modelId] ?: return null
        return selectBestSourceFromList(model.sources, strategy)
    }
    
    fun selectBestSourceFromList(sources: List<ModelSource2>, strategy: DownloadStrategy): ModelSource2? {
        if (sources.isEmpty()) return null
        if (sources.size == 1) return sources.first()
        
        return when (strategy) {
            DownloadStrategy.AUTO -> {
                // 综合评分: 速度 * 0.4 + 成功率 * 0.3 + 优先级 * 0.2 + 国内加成 0.1
                sources.maxByOrNull { source ->
                    val speedScore = when (source.avgSpeed) {
                        DownloadSpeed.VERY_FAST -> 100
                        DownloadSpeed.FAST -> 75
                        DownloadSpeed.NORMAL -> 50
                        DownloadSpeed.SLOW -> 25
                    }
                    val successScore = source.successRate * 100
                    val priorityScore = source.priority * 10
                    val cnBonus = if (source.isCN) 10 else 0
                    
                    speedScore * 0.4 + successScore * 0.3 + priorityScore * 0.2 + cnBonus * 0.1
                }
            }
            DownloadStrategy.SPEED_FIRST -> {
                sources.maxByOrNull { source ->
                    when (source.avgSpeed) {
                        DownloadSpeed.VERY_FAST -> 4
                        DownloadSpeed.FAST -> 3
                        DownloadSpeed.NORMAL -> 2
                        DownloadSpeed.SLOW -> 1
                    }
                }
            }
            DownloadStrategy.STABILITY_FIRST -> {
                sources.maxByOrNull { it.successRate }
            }
            DownloadStrategy.CN_FIRST -> {
                sources.filter { it.isCN }.maxByOrNull { it.avgSpeed }
                    ?: sources.maxByOrNull { it.avgSpeed }
            }
            DownloadStrategy.HF_FIRST -> {
                sources.filter { it.node == NodeType.HuggingFace || it.node == NodeType.MODELSCOPE }
                    .maxByOrNull { it.priority }
                    ?: sources.maxByOrNull { it.priority }
            }
            DownloadStrategy.MANUAL -> {
                // 返回最快但需要用户确认的源
                sources.maxByOrNull { it.priority }
            }
        }
    }
    
    // ========== 下载管理 ==========
    
    fun setStrategy(strategy: DownloadStrategy) {
        currentStrategy = strategy
    }
    
    fun setPreferredNode(node: NodeType?) {
        preferredNode = node
    }
    
    fun setUseProxy(use: Boolean) {
        useProxy = use
    }
    
    suspend fun downloadModel(
        modelId: String,
        sourceId: String? = null,
        destinationPath: String? = null
    ): Flow<DownloadProgress> = flow {
        val model = modelDatabase[modelId] ?: run {
            emit(DownloadProgress.Error("模型不存在: $modelId"))
            return@flow
        }
        
        // 选择源
        val selectedSource = if (sourceId != null) {
            model.sources.find { it.sourceId == sourceId }
        } else {
            selectBestSource(modelId, currentStrategy)
        }
        
        if (selectedSource == null) {
            emit(DownloadProgress.Error("没有可用的下载源"))
            return@flow
        }
        
        val taskId = "task_${System.currentTimeMillis()}"
        val task = DownloadTask(
            id = taskId,
            modelId = modelId,
            modelName = model.name,
            source = selectedSource,
            destinationPath = destinationPath ?: "models/${selectedSource.filename}",
            totalBytes = model.fileSize,
            downloadedBytes = 0,
            status = DownloadStatus.QUEUED,
            progress = 0f,
            speed = 0,
            eta = 0,
            error = null,
            startedAt = System.currentTimeMillis(),
            completedAt = null,
            retries = 0,
            resumeSupported = true
        )
        
        downloadQueue[taskId] = task
        
        emit(DownloadProgress.Queued(task.toProgress()))
        
        // 开始下载
        downloadQueue[taskId] = task.copy(status = DownloadStatus.DOWNLOADING)
        emit(DownloadProgress.Started(task.toProgress()))
        
        // 模拟下载进度
        var downloaded = 0L
        val total = model.fileSize
        
        while (downloaded < total) {
            val chunk = minOf(CHUNK_SIZE, total - downloaded)
            downloaded += chunk
            
            val progress = downloaded.toFloat() / total
            val speed = selectedSource.avgSpeed.let { speed ->
                when (speed) {
                    DownloadSpeed.VERY_FAST -> 50_000_000L
                    DownloadSpeed.FAST -> 30_000_000L
                    DownloadSpeed.NORMAL -> 10_000_000L
                    DownloadSpeed.SLOW -> 5_000_000L
                }
            }
            val eta = if (speed > 0) ((total - downloaded) / speed) else 0
            
            val currentTask = downloadQueue[taskId] ?: return@flow
            downloadQueue[taskId] = currentTask.copy(
                downloadedBytes = downloaded,
                progress = progress,
                speed = speed,
                eta = eta
            )
            
            emit(DownloadProgress.Update(task.copy(
                downloadedBytes = downloaded,
                progress = progress,
                speed = speed,
                eta = eta
            ).toProgress()))
            
            delay(100) // 模拟进度更新
        }
        
        // 下载完成
        downloadQueue[taskId] = task.copy(
            status = DownloadStatus.COMPLETED,
            downloadedBytes = total,
            progress = 1f,
            completedAt = System.currentTimeMillis()
        )
        
        emit(DownloadProgress.Completed(task.copy(
            status = DownloadStatus.COMPLETED,
            downloadedBytes = total,
            progress = 1f,
            completedAt = System.currentTimeMillis()
        ).toProgress()))
        
    }.flowOn(Dispatchers.IO)
    
    fun pauseDownload(taskId: String) {
        downloadQueue[taskId]?.let { task ->
            downloadQueue[taskId] = task.copy(status = DownloadStatus.PAUSED)
        }
    }
    
    fun resumeDownload(taskId: String) {
        downloadQueue[taskId]?.let { task ->
            if (task.status == DownloadStatus.PAUSED) {
                downloadQueue[taskId] = task.copy(status = DownloadStatus.DOWNLOADING)
            }
        }
    }
    
    fun cancelDownload(taskId: String) {
        downloadQueue[taskId]?.let { task ->
            downloadQueue[taskId] = task.copy(status = DownloadStatus.CANCELLED)
        }
    }
    
    fun getDownloadTask(taskId: String): DownloadTask? = downloadQueue[taskId]
    
    fun getAllDownloads(): List<DownloadTask> = downloadQueue.values.toList()
    
    fun getActiveDownloads(): List<DownloadTask> = 
        downloadQueue.values.filter { it.status == DownloadStatus.DOWNLOADING }
    
    fun getCompletedDownloads(): List<DownloadTask> = 
        downloadQueue.values.filter { it.status == DownloadStatus.COMPLETED }
    
    // ========== 统计 ==========
    
    fun getStats(): ModelStats {
        val models = modelDatabase.values.toList()
        return ModelStats(
            totalModels = models.size,
            totalCheckpoints = models.count { it.type == ModelType.CHECKPOINT },
            totalLoras = models.count { it.type == ModelType.LORA },
            totalControlNets = models.count { it.type == ModelType.CONTROLNET },
            totalVAEs = models.count { it.type == ModelType.VAE },
            totalDownloads = models.sumOf { it.downloads },
            totalSize = models.sumOf { it.fileSize },
            avgRating = models.map { it.rating }.average().toFloat(),
            totalSources = downloadSources.size,
            activeDownloads = getActiveDownloads().size
        )
    }
    
    data class ModelStats(
        val totalModels: Int,
        val totalCheckpoints: Int,
        val totalLoras: Int,
        val totalControlNets: Int,
        val totalVAEs: Int,
        val totalDownloads: Long,
        val totalSize: Long,
        val avgRating: Float,
        val totalSources: Int,
        val activeDownloads: Int
    )
    
    // ========== 下载进度 ==========
    
    sealed class DownloadProgress {
        data class Queued(val task: TaskProgress) : DownloadProgress()
        data class Started(val task: TaskProgress) : DownloadProgress()
        data class Update(val task: TaskProgress) : DownloadProgress()
        data class Completed(val task: TaskProgress) : DownloadProgress()
        data class Error(val message: String) : DownloadProgress()
    }
    
    data class TaskProgress(
        val taskId: String,
        val modelId: String,
        val modelName: String,
        val sourceName: String,
        val sourceNode: NodeType,
        val isCN: Boolean,
        val totalBytes: Long,
        val downloadedBytes: Long,
        val progress: Float,
        val speed: Long,
        val eta: Long,
        val status: DownloadStatus,
        val error: String?
    )
    
    private fun DownloadTask.toProgress() = TaskProgress(
        taskId = id,
        modelId = modelId,
        modelName = modelName,
        sourceName = downloadSources[source.sourceId]?.name ?: source.sourceId,
        sourceNode = source.node,
        isCN = source.isCN,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        progress = progress,
        speed = speed,
        eta = eta,
        status = status,
        error = error
    )
    
    // ========== 清理 ==========
    
    fun clearHistory() {
        downloadHistory.clear()
    }
    
    fun release() {
        scope.cancel()
        Log.i(TAG, "♻️ SuperModelManager 已释放")
    }
}
