package comkuaihuiai.service.advanced

import android.content.Context
import android.util.Log
import comkuaihuiai.data.model.*
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 快绘AI v3.4.0 内置模型管理
 * 支持 CivitAI / HuggingFace 模型
 */
class BuiltInModelManager(private val context: Context) {

    companion object {
        private const val TAG = "BuiltInModelManager"
        
        // CivitAI API 基础地址
        const val CIVITAI_API_BASE = "https://civitai.com/api/v1"
        
        // 默认下载目录
        const val DEFAULT_MODEL_PATH = "models"
    }

    // ========== 模型源配置 ==========
    
    data class ModelSourceConfig(
        val source: ModelSource,
        val apiBase: String,
        val websiteBase: String,
        val requiresApiKey: Boolean,
        val apiKeyEnvVar: String? = null
    )
    
    // ========== 内置模型数据库 ==========
    
    data class BuiltInModel(
        val id: String,
        val name: String,
        val description: String,
        val type: ModelType,
        val baseModel: BaseModelType,
        val source: ModelSource,
        val sourceId: Long,  // CivitAI/HuggingFace ID
        val sourceUrl: String,
        val downloadUrl: String?,
        val previewImages: List<String>,
        val tags: List<String>,
        val rating: Float,
        val downloads: Long,
        val isNSFW: Boolean,
        val recommendedParams: RecommendedParams,
        val compatibility: List<String>
    )
    
    data class RecommendedParams(
        val width: Int = 512,
        val height: Int = 768,
        val steps: Int = 25,
        val guidance: Float = 7.5f,
        val scheduler: SchedulerType = SchedulerType.DPMSOLVER_PLUS_PLUS_2M_KARRAS,
        val strength: Float? = null,
        val clipSkip: Int? = null,
        val loraLinks: List<LoraLink> = emptyList()
    )
    
    data class LoraLink(
        val name: String,
        val sourceId: Long,
        val weight: Float = 1.0f,
        val downloadUrl: String?
    )
    
    // ========== 模型数据库 ==========
    
    private val modelDatabase = ConcurrentHashMap<String, BuiltInModel>()
    private val categoryIndex = ConcurrentHashMap<ModelCategory, MutableList<BuiltInModel>>()
    private val popularModels = ConcurrentHashMap<String, BuiltInModel>()
    
    // ========== 模型类别 ==========
    
    enum class ModelCategory(
        val displayName: String,
        val emoji: String,
        val description: String
    ) {
        // 按风格
        REALISTIC("真实人像", "📷", "逼真摄影风格"),
        ANIME("动漫风格", "🎨", "精美动漫插画"),
        SEMIREALISTIC("半真实", "🖼️", "介于真实与动漫之间"),
        FANTASY("奇幻风格", "✨", "魔法、奇幻元素"),
        COMIC("漫画风格", "📚", "美式漫画风格"),
        
        // 按用途
        PORTRAIT("人像", "👤", "人物肖像"),
        FASHION("时尚", "👗", "服装、时尚"),
        LANDSCAPE("风景", "🏔️", "自然风景"),
        ARCHITECTURE("建筑", "🏛️", "建筑室内"),
        GAME_ASSET("游戏资产", "🎮", "游戏角色、道具"),
        PRODUCT("产品", "📦", "商业产品展示"),
        
        // 特殊
        LORA("LoRA 合集", "🔧", "精选 LoRA 模型"),
        CHECKPOINT("检查点", "📦", "完整检查点模型")
    }
    
    // ========== 初始化数据库 ==========
    
    init {
        loadBuiltInModels()
    }
    
    private fun loadBuiltInModels() {
        Log.i(TAG, "📦 加载内置模型数据库...")
        
        // ========== MajicMix 系列 ==========
        addModel(BuiltInModel(
            id = "majicmix_realistic_v7",
            name = "MajicMix Realistic v7",
            description = "高质量真实人像模型，细节丰富，光影自然。适合写真、产品展示等。",
            type = ModelType.CHECKPOINT,
            baseModel = BaseModelType.SD_1_5,
            source = ModelSources.huggingface,
            sourceId = 43331,
            sourceUrl = "https://civitai.com/models/43331/majicmix-realistic",
            downloadUrl = "https://civitai.com/api/download/models/43331",
            previewImages = listOf(
                "https://image.civitai.com/43331/init1.jpeg",
                "https://image.civitai.com/43331/init2.jpeg"
            ),
            tags = listOf("realistic", "photorealistic", "portrait", "asian", "female"),
            rating = 4.8f,
            downloads = 2800000,
            isNSFW = false,
            recommendedParams = RecommendedParams(
                width = 512,
                height = 768,
                steps = 25,
                guidance = 7.5f,
                scheduler = SchedulerType.DPMSOLVER_PLUS_PLUS_2M_KARRAS,
                clipSkip = 2
            ),
            compatibility = listOf("SD 1.5", "ComfyUI", "Forge", "Forge Plus")
        ))
        
        addModel(BuiltInModel(
            id = "majicmix_sombras_v6",
            name = "MajicMix Sombras v6",
            description = "光影效果出色的真实人像模型，适合戏剧性光照场景。",
            type = ModelType.CHECKPOINT,
            baseModel = BaseModelType.SD_1_5,
            source = ModelSources.huggingface,
            sourceId = 46646,
            sourceUrl = "https://civitai.com/models/46646/majicmix-sombras",
            downloadUrl = "https://civitai.com/api/download/models/46646",
            previewImages = emptyList(),
            tags = listOf("realistic", "portrait", "dramatic", "lighting", "shadows"),
            rating = 4.7f,
            downloads = 1500000,
            isNSFW = false,
            recommendedParams = RecommendedParams(
                width = 512,
                height = 768,
                steps = 28,
                guidance = 8.0f,
                scheduler = SchedulerType.DDIM
            ),
            compatibility = listOf("SD 1.5")
        ))
        
        addModel(BuiltInModel(
            id = "majicmix_beverly_v1",
            name = "MajicMix Beverly v1",
            description = "甜美风格真实人像，适合时尚摄影。",
            type = ModelType.CHECKPOINT,
            baseModel = BaseModelType.SD_1_5,
            source = ModelSources.huggingface,
            sourceId = 61324,
            sourceUrl = "https://civitai.com/models/61324/majicmix-beverly",
            downloadUrl = "https://civitai.com/api/download/models/61324",
            previewImages = emptyList(),
            tags = listOf("realistic", "portrait", "fashion", "sweet", "beauty"),
            rating = 4.6f,
            downloads = 800000,
            isNSFW = false,
            recommendedParams = RecommendedParams(
                width = 512,
                height = 768,
                steps = 25,
                guidance = 7.0f,
                scheduler = SchedulerType.EULER_ANCESTRAL
            ),
            compatibility = listOf("SD 1.5")
        ))
        
        // ========== DreamShaper 系列 ==========
        addModel(BuiltInModel(
            id = "dreamshaper_v8",
            name = "DreamShaper v8",
            description = "通用型模型，真实与创意兼顾。社区热门首选。",
            type = ModelType.CHECKPOINT,
            baseModel = BaseModelType.SD_1_5,
            source = ModelSources.huggingface,
            sourceId = 4384,
            sourceUrl = "https://civitai.com/models/4384/dreamshaper",
            downloadUrl = "https://civitai.com/api/download/models/4384",
            previewImages = emptyList(),
            tags = listOf("general", "realistic", "illustration", "fantasy", "versatile"),
            rating = 4.7f,
            downloads = 3500000,
            isNSFW = false,
            recommendedParams = RecommendedParams(
                width = 512,
                height = 512,
                steps = 25,
                guidance = 7.0f,
                scheduler = SchedulerType.DPMSOLVER_PLUS_PLUS_2M_KARRAS
            ),
            compatibility = listOf("SD 1.5", "ComfyUI", "Forge")
        ))
        
        addModel(BuiltInModel(
            id = "dreamshaper_xl",
            name = "DreamShaper XL",
            description = "DreamShaper 的 SDXL 版本，更高质量。",
            type = ModelType.CHECKPOINT,
            baseModel = BaseModelType.SD_XL,
            source = ModelSources.huggingface,
            sourceId = 128713,
            sourceUrl = "https://civitai.com/models/128713/dreamshaper-xl",
            downloadUrl = "https://civitai.com/api/download/models/128713",
            previewImages = emptyList(),
            tags = listOf("xl", "realistic", "illustration", "fantasy", "versatile"),
            rating = 4.6f,
            downloads = 1200000,
            isNSFW = false,
            recommendedParams = RecommendedParams(
                width = 1024,
                height = 1024,
                steps = 30,
                guidance = 7.0f,
                scheduler = SchedulerType.DPMSOLVER_PLUS_PLUS_2M_KARRAS
            ),
            compatibility = listOf("SDXL", "ComfyUI")
        ))
        
        // ========== Realistic Vision ==========
        addModel(BuiltInModel(
            id = "realistic_vision_v5",
            name = "Realistic Vision v5.1",
            description = "专注于真实摄影风格，人物和物体都非常逼真。",
            type = ModelType.CHECKPOINT,
            baseModel = BaseModelType.SD_1_5,
            source = ModelSources.huggingface,
            sourceId = 5746,
            sourceUrl = "https://civitai.com/models/5746/realistic-vision",
            downloadUrl = "https://civitai.com/api/download/models/5746",
            previewImages = emptyList(),
            tags = listOf("realistic", "photorealistic", "portrait", "product", "photography"),
            rating = 4.8f,
            downloads = 3200000,
            isNSFW = false,
            recommendedParams = RecommendedParams(
                width = 512,
                height = 768,
                steps = 25,
                guidance = 7.5f,
                scheduler = SchedulerType.DPMSOLVER_PLUS_PLUS_2M_KARRAS
            ),
            compatibility = listOf("SD 1.5", "Forge", "ComfyUI")
        ))
        
        addModel(BuiltInModel(
            id = "realistic_vision_xl",
            name = "Realistic Vision XL",
            description = "SDXL 版本的真实摄影模型。",
            type = ModelType.CHECKPOINT,
            baseModel = BaseModelType.SD_XL,
            source = ModelSources.huggingface,
            sourceId = 257749,
            sourceUrl = "https://civitai.com/models/257749/realistic-vision-xl",
            downloadUrl = "https://civitai.com/api/download/models/257749",
            previewImages = emptyList(),
            tags = listOf("xl", "realistic", "photorealistic", "portrait"),
            rating = 4.7f,
            downloads = 600000,
            isNSFW = false,
            recommendedParams = RecommendedParams(
                width = 1024,
                height = 1024,
                steps = 30,
                guidance = 6.0f,
                scheduler = SchedulerType.DPMSOLVER_PLUS_PLUS_2M_KARRAS
            ),
            compatibility = listOf("SDXL")
        ))
        
        // ========== SDXL 基础模型 ==========
        addModel(BuiltInModel(
            id = "sdxl_base_1.0",
            name = "SDXL 1.0 Base",
            description = "Stable Diffusion XL 官方基础模型，支持 1024x1024 高分辨率。",
            type = ModelType.CHECKPOINT,
            baseModel = BaseModelType.SD_XL,
            source = ModelSources.huggingface,
            sourceId = 0,
            sourceUrl = "https://stability.ai/stablediffusion",
            downloadUrl = null,
            previewImages = emptyList(),
            tags = listOf("xl", "base", "official", "foundation"),
            rating = 4.5f,
            downloads = 0,
            isNSFW = false,
            recommendedParams = RecommendedParams(
                width = 1024,
                height = 1024,
                steps = 30,
                guidance = 7.5f,
                scheduler = SchedulerType.DPMSOLVER_PLUS_PLUS_2M_KARRAS
            ),
            compatibility = listOf("SDXL")
        ))
        
        addModel(BuiltInModel(
            id = "sdxl_lightning",
            name = "SDXL Lightning",
            description = "SDXL 高速模型，4步出图，质量优秀。",
            type = ModelType.CHECKPOINT,
            baseModel = BaseModelType.SD_XL_LIGHTNING,
            source = ModelSources.huggingface,
            sourceId = 118901,
            sourceUrl = "https://civitai.com/models/118901/sdxl-lightning",
            downloadUrl = "https://civitai.com/api/download/models/118901",
            previewImages = emptyList(),
            tags = listOf("xl", "lightning", "fast", "quality"),
            rating = 4.6f,
            downloads = 950000,
            isNSFW = false,
            recommendedParams = RecommendedParams(
                width = 1024,
                height = 1024,
                steps = 4,
                guidance = 1.0f,
                scheduler = SchedulerType.LCM
            ),
            compatibility = listOf("SDXL", "LCM")
        ))
        
        addModel(BuiltInModel(
            id = "sdxl_turbo",
            name = "SDXL Turbo",
            description = "Adversarial Diffusion Distillation 技术，1-2步出图。",
            type = ModelType.CHECKPOINT,
            baseModel = BaseModelType.SD_XL_TURBO,
            source = ModelSources.huggingface,
            sourceId = 103055,
            sourceUrl = "https://civitai.com/models/103055/sdxl-turbo",
            downloadUrl = "https://civitai.com/api/download/models/103055",
            previewImages = emptyList(),
            tags = listOf("xl", "turbo", "fast", "1-step"),
            rating = 4.4f,
            downloads = 800000,
            isNSFW = false,
            recommendedParams = RecommendedParams(
                width = 512,
                height = 512,
                steps = 1,
                guidance = 1.0f,
                scheduler = SchedulerType.TURBO
            ),
            compatibility = listOf("SDXL")
        ))
        
        // ========== 动漫模型 ==========
        addModel(BuiltInModel(
            id = "counterfeit_v30",
            name = "Counterfeit v3.0",
            description = "高质量动漫插画模型，色彩鲜艳，线条清晰。",
            type = ModelType.CHECKPOINT,
            baseModel = BaseModelType.SD_1_5,
            source = ModelSources.huggingface,
            sourceId = 5765,
            sourceUrl = "https://civitai.com/models/5765/counterfeit",
            downloadUrl = "https://civitai.com/api/download/models/5765",
            previewImages = emptyList(),
            tags = listOf("anime", "illustration", "colorful", "detailed"),
            rating = 4.7f,
            downloads = 2100000,
            isNSFW = false,
            recommendedParams = RecommendedParams(
                width = 512,
                height = 768,
                steps = 28,
                guidance = 8.0f,
                scheduler = SchedulerType.EULER_ANCESTRAL
            ),
            compatibility = listOf("SD 1.5")
        ))
        
        addModel(BuiltInModel(
            id = "anything_v5",
            name = "Anything v5",
            description = "经典动漫模型，兼容性极好，社区广泛使用。",
            type = ModelType.CHECKPOINT,
            baseModel = BaseModelType.SD_1_5,
            source = ModelSources.huggingface,
            sourceId = 405,
            sourceUrl = "https://civitai.com/models/405/anything-v5",
            downloadUrl = "https://civitai.com/api/download/models/405",
            previewImages = emptyList(),
            tags = listOf("anime", "general", "versatile", "popular"),
            rating = 4.5f,
            downloads = 4800000,
            isNSFW = false,
            recommendedParams = RecommendedParams(
                width = 512,
                height = 768,
                steps = 25,
                guidance = 7.0f,
                scheduler = SchedulerType.EULER
            ),
            compatibility = listOf("SD 1.5")
        ))
        
        addModel(BuiltInModel(
            id = "meinamix_v11",
            name = "MeinaMix v11",
            description = "Meina 混音版动漫模型，画面精美，构图优秀。",
            type = ModelType.CHECKPOINT,
            baseModel = BaseModelType.SD_1_5,
            source = ModelSources.huggingface,
            sourceId = 9797,
            sourceUrl = "https://civitai.com/models/9797/meinamix",
            downloadUrl = "https://civitai.com/api/download/models/9797",
            previewImages = emptyList(),
            tags = listOf("anime", "meina", "illustration", "beautiful"),
            rating = 4.6f,
            downloads = 1900000,
            isNSFW = false,
            recommendedParams = RecommendedParams(
                width = 512,
                height = 768,
                steps = 25,
                guidance = 7.5f,
                scheduler = SchedulerType.EULER_ANCESTRAL
            ),
            compatibility = listOf("SD 1.5")
        ))
        
        addModel(BuiltInModel(
            id = "animagine_xl",
            name = "Animagine XL 3.1",
            description = "SDXL 动漫模型，质量优秀，社区活跃更新。",
            type = ModelType.CHECKPOINT,
            baseModel = BaseModelType.SD_XL,
            source = ModelSources.huggingface,
            sourceId = 26056,
            sourceUrl = "https://civitai.com/models/26056/animagine-xl",
            downloadUrl = "https://civitai.com/api/download/models/26056",
            previewImages = emptyList(),
            tags = listOf("xl", "anime", "illustration", "quality"),
            rating = 4.8f,
            downloads = 1400000,
            isNSFW = false,
            recommendedParams = RecommendedParams(
                width = 1024,
                height = 1024,
                steps = 30,
                guidance = 8.0f,
                scheduler = SchedulerType.EULER_ANCESTRAL,
                clipSkip = 2
            ),
            compatibility = listOf("SDXL")
        ))
        
        // ========== 精选 LoRA ==========
        addModel(BuiltInModel(
            id = "lora_detail_enhancer",
            name = "Detail Enhancer",
            description = "增强图像细节和纹理，让画面更加清晰锐利。",
            type = ModelType.LORA,
            baseModel = BaseModelType.SD_1_5,
            source = ModelSources.huggingface,
            sourceId = 56266,
            sourceUrl = "https://civitai.com/models/56266/detail-enhancer",
            downloadUrl = "https://civitai.com/api/download/models/56266",
            previewImages = emptyList(),
            tags = listOf("lora", "detail", "enhance", "texture", "sharp"),
            rating = 4.5f,
            downloads = 650000,
            isNSFW = false,
            recommendedParams = RecommendedParams(),
            compatibility = listOf("SD 1.5", "SDXL")
        ))
        
        addModel(BuiltInModel(
            id = "lora_add_brightness",
            name = "Add More Details",
            description = "增加细节和亮度，让图像更加明亮清晰。",
            type = ModelType.LORA,
            baseModel = BaseModelType.SD_1_5,
            source = ModelSources.huggingface,
            sourceId = 62886,
            sourceUrl = "https://civitai.com/models/62886/add-more-details",
            downloadUrl = "https://civitai.com/api/download/models/62886",
            previewImages = emptyList(),
            tags = listOf("lora", "detail", "brightness", "enhance"),
            rating = 4.4f,
            downloads = 580000,
            isNSFW = false,
            recommendedParams = RecommendedParams(),
            compatibility = listOf("SD 1.5")
        ))
        
        addModel(BuiltInModel(
            id = "lora_face_detail",
            name = "Face Detail",
            description = "增强面部细节，让皮肤质感更真实。",
            type = ModelType.LORA,
            baseModel = BaseModelType.SD_1_5,
            source = ModelSources.huggingface,
            sourceId = 173836,
            sourceUrl = "https://civitai.com/models/173836/face-detail",
            downloadUrl = "https://civitai.com/api/download/models/173836",
            previewImages = emptyList(),
            tags = listOf("lora", "face", "portrait", "skin", "detail"),
            rating = 4.6f,
            downloads = 920000,
            isNSFW = false,
            recommendedParams = RecommendedParams(),
            compatibility = listOf("SD 1.5", "SDXL")
        ))
        
        addModel(BuiltInModel(
            id = "lora_ Closer",
            name = "Closer",
            description = "让人物更近，构图更紧凑，突出主体。",
            type = ModelType.LORA,
            baseModel = BaseModelType.SD_1_5,
            source = ModelSources.huggingface,
            sourceId = 55277,
            sourceUrl = "https://civitai.com/models/55277/-closer",
            downloadUrl = "https://civitai.com/api/download/models/55277",
            previewImages = emptyList(),
            tags = listOf("lora", "camera", "closer", "composition"),
            rating = 4.3f,
            downloads = 420000,
            isNSFW = false,
            recommendedParams = RecommendedParams(),
            compatibility = listOf("SD 1.5")
        ))
        
        addModel(BuiltInModel(
            id = "lora_ulzzang",
            name = "Ulzzang",
            description = "韩式审美风格，让人物更符合韩国流行美学。",
            type = ModelType.LORA,
            baseModel = BaseModelType.SD_1_5,
            source = ModelSources.huggingface,
            sourceId = 33864,
            sourceUrl = "https://civitai.com/models/33864/-ulzzang-",
            downloadUrl = "https://civitai.com/api/download/models/33864",
            previewImages = emptyList(),
            tags = listOf("lora", "asian", "korean", "beauty", "fashion"),
            rating = 4.4f,
            downloads = 780000,
            isNSFW = false,
            recommendedParams = RecommendedParams(),
            compatibility = listOf("SD 1.5")
        ))
        
        addModel(BuiltInModel(
            id = "lora_0D_anime",
            name = "0D Anime",
            description = "动漫风格 LoRA，让真实图像转换为动漫风格。",
            type = ModelType.LORA,
            baseModel = BaseModelType.SD_1_5,
            source = ModelSources.huggingface,
            sourceId = 19838,
            sourceUrl = "https://civitai.com/models/19838/0d-anime",
            downloadUrl = "https://civitai.com/api/download/models/19838",
            previewImages = emptyList(),
            tags = listOf("lora", "anime", "style", "transfer"),
            rating = 4.5f,
            downloads = 560000,
            isNSFW = false,
            recommendedParams = RecommendedParams(),
            compatibility = listOf("SD 1.5")
        ))
        
        // ========== ControlNet 模型 ==========
        addModel(BuiltInModel(
            id = "controlnet_canny",
            name = "ControlNet Canny",
            description = "边缘检测 ControlNet，精准控制图像结构。",
            type = ModelType.CONTROLNET,
            baseModel = BaseModelType.SD_1_5,
            source = ModelSources.huggingface,
            sourceId = 0,
            sourceUrl = "https://huggingface.co/lllyasviel/ControlNet",
            downloadUrl = "https://huggingface.co/lllyasviel/ControlNet/resolve/main/models/control_canny-fp16.safetensors",
            previewImages = emptyList(),
            tags = listOf("controlnet", "canny", "edge", "structure"),
            rating = 4.8f,
            downloads = 0,
            isNSFW = false,
            recommendedParams = RecommendedParams(),
            compatibility = listOf("SD 1.5", "ControlNet")
        ))
        
        addModel(BuiltInModel(
            id = "controlnet_depth",
            name = "ControlNet Depth",
            description = "深度图 ControlNet，控制图像深度和空间感。",
            type = ModelType.CONTROLNET,
            baseModel = BaseModelType.SD_1_5,
            source = ModelSources.huggingface,
            sourceId = 0,
            sourceUrl = "https://huggingface.co/lllyasviel/ControlNet",
            downloadUrl = "https://huggingface.co/lllyasviel/ControlNet/resolve/main/models/control_depth-fp16.safetensors",
            previewImages = emptyList(),
            tags = listOf("controlnet", "depth", "midas", "spatial"),
            rating = 4.8f,
            downloads = 0,
            isNSFW = false,
            recommendedParams = RecommendedParams(),
            compatibility = listOf("SD 1.5", "ControlNet")
        ))
        
        addModel(BuiltInModel(
            id = "controlnet_openpose",
            name = "ControlNet OpenPose",
            description = "姿态检测 ControlNet，精确控制人物姿势。",
            type = ModelType.CONTROLNET,
            baseModel = BaseModelType.SD_1_5,
            source = ModelSources.huggingface,
            sourceId = 0,
            sourceUrl = "https://huggingface.co/lllyasviel/ControlNet",
            downloadUrl = "https://huggingface.co/lllyasviel/ControlNet/resolve/main/models/control_openpose-fp16.safetensors",
            previewImages = emptyList(),
            tags = listOf("controlnet", "openpose", "pose", "human"),
            rating = 4.9f,
            downloads = 0,
            isNSFW = false,
            recommendedParams = RecommendedParams(),
            compatibility = listOf("SD 1.5", "ControlNet")
        ))
        
        // ========== VAE 模型 ==========
        addModel(BuiltInModel(
            id = "vae_vae",
            name = "Stability AI VAE",
            description = "官方 VAE 模型，用于图像编解码。",
            type = ModelType.VAE,
            baseModel = BaseModelType.SD_1_5,
            source = ModelSources.huggingface,
            sourceId = 0,
            sourceUrl = "https://huggingface.co/stabilityai/sd-vae-ft-mse",
            downloadUrl = "https://huggingface.co/stabilityai/sd-vae-ft-mse/resolve/main/vae-ft-mse-840000-ema-pruned.safetensors",
            previewImages = emptyList(),
            tags = listOf("vae", "decoder", "encoder", "official"),
            rating = 4.7f,
            downloads = 0,
            isNSFW = false,
            recommendedParams = RecommendedParams(),
            compatibility = listOf("SD 1.5")
        ))
        
        addModel(BuiltInModel(
            id = "vae_vae_ft_mse",
            name = "VAE FT-MSE",
            description = "高保真 VAE，细节更丰富。",
            type = ModelType.VAE,
            baseModel = BaseModelType.SD_1_5,
            source = ModelSources.huggingface,
            sourceId = 0,
            sourceUrl = "https://huggingface.co/stabilityai/sd-vae-ft-mse",
            downloadUrl = "https://huggingface.co/stabilityai/sd-vae-ft-mse/resolve/main/vae-ft-mse-840000-ema-pruned.safetensors",
            previewImages = emptyList(),
            tags = listOf("vae", "high-fidelity", "detail", "official"),
            rating = 4.8f,
            downloads = 0,
            isNSFW = false,
            recommendedParams = RecommendedParams(),
            compatibility = listOf("SD 1.5")
        ))
        
        // ========== 放大模型 ==========
        addModel(BuiltInModel(
            id = "upscale_4xUltraSharp",
            name = "4x-UltraSharp",
            description = "高质量图像放大模型，放大4倍仍保持清晰。",
            type = ModelType.UPSCALER,
            baseModel = BaseModelType.SD_1_5,
            source = ModelSources.huggingface,
            sourceId = 8460,
            sourceUrl = "https://civitai.com/models/8460/4x-upscaler",
            downloadUrl = "https://civitai.com/api/download/models/8460",
            previewImages = emptyList(),
            tags = listOf("upscale", "4x", "sharp", "quality"),
            rating = 4.8f,
            downloads = 1200000,
            isNSFW = false,
            recommendedParams = RecommendedParams(),
            compatibility = listOf("ESRGAN", "RealESRGAN")
        ))
        
        addModel(BuiltInModel(
            id = "upscale_real_esrgan_x4",
            name = "Real-ESRGAN x4",
            description = "Real-ESRGAN 官方放大模型，通用性极佳。",
            type = ModelType.UPSCALER,
            baseModel = BaseModelType.SD_1_5,
            source = ModelSources.huggingface,
            sourceId = 0,
            sourceUrl = "https://github.com/xinntao/Real-ESRGAN",
            downloadUrl = "https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.5.0/realesrgan-x4.pth",
            previewImages = emptyList(),
            tags = listOf("upscale", "real-esrgan", "4x", "universal"),
            rating = 4.6f,
            downloads = 0,
            isNSFW = false,
            recommendedParams = RecommendedParams(),
            compatibility = listOf("Real-ESRGAN")
        ))
        
        Log.i(TAG, "✅ 已加载 ${modelDatabase.size} 个内置模型")
    }
    
    // ========== 添加模型 ==========
    
    private fun addModel(model: BuiltInModel) {
        modelDatabase[model.id] = model
        
        // 按类别索引
        val category = categorizeModel(model)
        categoryIndex.getOrPut(category) { mutableListOf() }.add(model)
        
        // 热门模型
        if (model.downloads > 500000) {
            popularModels[model.id] = model
        }
    }
    
    // ========== 模型分类 ==========
    
    private fun categorizeModel(model: BuiltInModel): ModelCategory {
        val tags = model.tags
        
        return when {
            model.type == ModelType.LORA -> ModelCategory.LORA
            model.type == ModelType.CONTROLNET -> ModelCategory.PORTRAIT
            model.type == ModelType.VAE -> ModelCategory.CHECKPOINT
            model.type == ModelType.UPSCALER -> ModelCategory.CHECKPOINT
            tags.contains("anime") || tags.contains("illustration") -> {
                if (tags.contains("realistic") || tags.contains("photorealistic")) {
                    ModelCategory.SEMIREALISTIC
                } else {
                    ModelCategory.ANIME
                }
            }
            tags.contains("realistic") || tags.contains("photorealistic") || tags.contains("portrait") -> {
                if (tags.contains("fantasy") || tags.contains("magic")) {
                    ModelCategory.FANTASY
                } else {
                    ModelCategory.REALISTIC
                }
            }
            tags.contains("landscape") || tags.contains("nature") -> ModelCategory.LANDSCAPE
            tags.contains("architecture") || tags.contains("interior") -> ModelCategory.ARCHITECTURE
            tags.contains("game") || tags.contains("asset") -> ModelCategory.GAME_ASSET
            tags.contains("product") || tags.contains("commercial") -> ModelCategory.PRODUCT
            tags.contains("fashion") || tags.contains("clothing") -> ModelCategory.FASHION
            tags.contains("comic") || tags.contains("cartoon") -> ModelCategory.COMIC
            else -> ModelCategory.CHECKPOINT
        }
    }
    
    // ========== 查询 API ==========
    
    fun getAllModels(): List<BuiltInModel> = modelDatabase.values.toList()
    
    fun getModel(id: String): BuiltInModel? = modelDatabase[id]
    
    fun getPopularModels(): List<BuiltInModel> = popularModels.values.sortedByDescending { it.downloads }
    
    fun getModelsByCategory(category: ModelCategory): List<BuiltInModel> = 
        categoryIndex[category] ?: emptyList()
    
    fun getModelsByType(type: ModelType): List<BuiltInModel> =
        modelDatabase.values.filter { it.type == type }
    
    fun getModelsByBaseModel(baseModel: BaseModelType): List<BuiltInModel> =
        modelDatabase.values.filter { it.baseModel == baseModel }
    
    fun searchModels(
        query: String,
        category: ModelCategory? = null,
        type: ModelType? = null,
        baseModel: BaseModelType? = null,
        nsfw: Boolean? = null
    ): List<BuiltInModel> {
        val q = query.lowercase()
        return modelDatabase.values.filter { model ->
            val matchQuery = q.isEmpty() || 
                model.name.lowercase().contains(q) ||
                model.description.lowercase().contains(q) ||
                model.tags.any { it.lowercase().contains(q) }
            val matchCategory = category == null || categorizeModel(model) == category
            val matchType = type == null || model.type == type
            val matchBase = baseModel == null || model.baseModel == baseModel
            val matchNsfw = nsfw == null || model.isNSFW == nsfw
            
            matchQuery && matchCategory && matchType && matchBase && matchNsfw
        }.sortedByDescending { it.downloads }
    }
    
    fun getModelStats(): ModelStats {
        val models = modelDatabase.values.toList()
        return ModelStats(
            totalModels = models.size,
            byType = models.groupBy { it.type }.mapValues { it.value.size },
            byCategory = models.groupBy { categorizeModel(it) }.mapValues { it.value.size },
            byBaseModel = models.groupBy { it.baseModel }.mapValues { it.value.size },
            totalDownloads = models.sumOf { it.downloads },
            avgRating = models.map { it.rating }.average().toFloat()
        )
    }
    
    data class ModelStats(
        val totalModels: Int,
        val byType: Map<ModelType, Int>,
        val byCategory: Map<ModelCategory, Int>,
        val byBaseModel: Map<BaseModelType, Int>,
        val totalDownloads: Long,
        val avgRating: Float
    )
    
    // ========== 下载管理 ==========
    
    data class DownloadInfo(
        val model: BuiltInModel,
        val localPath: String,
        val fileSize: Long,
        val downloadedSize: Long,
        val status: DownloadStatus,
        val progress: Float,
        val error: String?
    )
    
    enum class DownloadStatus {
        PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED
    }
    
    private val downloadQueue = ConcurrentHashMap<String, DownloadInfo>()
    
    fun getDownloadStatus(modelId: String): DownloadInfo? = downloadQueue[modelId]
    
    fun getAllDownloads(): List<DownloadInfo> = downloadQueue.values.toList()
    
    // ========== 推荐模型 ==========
    
    fun getRecommendedModels(purpose: String): List<BuiltInModel> {
        return when (purpose.lowercase()) {
            "portrait", "人像", "写真" -> getModelsByCategory(ModelCategory.REALISTIC).take(5)
            "anime", "动漫" -> getModelsByCategory(ModelCategory.ANIME).take(5)
            "landscape", "风景" -> getModelsByCategory(ModelCategory.LANDSCAPE).take(5)
            "product", "产品" -> getModelsByCategory(ModelCategory.PRODUCT).take(5)
            "fast", "快速", "预览" -> modelDatabase.values.filter { 
                it.recommendedParams.steps <= 10 
            }.take(5)
            "quality", "质量", "高清" -> modelDatabase.values.filter { 
                it.recommendedParams.steps >= 30 
            }.take(5)
            else -> getPopularModels().take(10)
        }
    }
    
    // ========== 获取下载链接 ==========
    
    fun getDownloadCommand(model: BuiltInModel): String {
        return when (model.source.name) {
            "Civitai" -> "curl -L -o ${model.id}.safetensors \"${model.downloadUrl}\""
            "HuggingFace" -> "wget -O ${model.id}.safetensors \"${model.downloadUrl}\""
            else -> "echo 'Download from: ${model.sourceUrl}'"
        }
    }
}
