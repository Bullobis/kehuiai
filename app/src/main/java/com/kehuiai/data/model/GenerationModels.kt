package com.kehuiai.data.model

import kotlin.random.Random

/**
 * 快绘AI v2.3.0 数据模型 - 五大方向全面增强
 */

// ============================================================
// 方向一：更高质量生成
// ============================================================

/**
 * 基础模型类型
 */
enum class BaseModelType(
    val displayName: String,
    val maxResolution: Int,
    val supportsSDXL: Boolean = false,
    val supportsFlux: Boolean = false,
    val memoryRequirement: String
) {
    // SD 1.5 系列
    SD_1_5("SD 1.5", 512, memoryRequirement = "4GB"),
    SD_1_5_INPAINTING("SD 1.5 Inpainting", 512, memoryRequirement = "4GB"),
    
    // SD 2.x 系列
    SD_2_1("SD 2.1", 768, memoryRequirement = "6GB"),
    SD_2_1_INPAINTING("SD 2.1 Inpainting", 768, memoryRequirement = "6GB"),
    
    // SDXL 系列 (支持 4K)
    SD_XL("SDXL 1.0", 1024, supportsSDXL = true, memoryRequirement = "8GB"),
    SD_XL_INPAINTING("SDXL 1.0 Inpainting", 1024, supportsSDXL = true, memoryRequirement = "8GB"),
    SD_XL_LIGHTNING("SDXL Lightning", 1024, supportsSDXL = true, memoryRequirement = "6GB"),
    SD_XL_TURBO("SDXL Turbo", 1024, supportsSDXL = true, memoryRequirement = "4GB"),
    
    // SD 3 系列
    SD_3_MEDIUM("SD 3 Medium", 1024, supportsSDXL = true, memoryRequirement = "8GB"),
    
    // Flux 系列
    FLUX_1_DEV("Flux 1 Dev", 1024, supportsFlux = true, memoryRequirement = "12GB"),
    FLUX_1_SCHNELL("Flux 1 Schnell", 1024, supportsFlux = true, memoryRequirement = "8GB"),
}

/**
 * 调度器类型 - 包括更快的调度器
 */
enum class SchedulerType(
    val displayName: String,
    val speed: Float, // 1.0 = 基准速度
    val quality: Float // 1.0 = 基准质量
) {
    // 标准调度器
    EULER("Euler", 1.0f, 1.0f),
    EULER_ANCESTRAL("Euler A", 0.9f, 1.1f),
    DPM_SOLVER("DPM-Solver", 1.1f, 1.0f),
    DPM_SOLVER_PP("DPM-Solver++", 1.2f, 1.0f),
    DPMSOLVER_PLUS_PLUS("DPM-Solver++", 1.3f, 1.0f),
    DPMSOLVER_PLUS_PLUS_2M("DPM-Solver++ 2M", 1.5f, 0.95f),
    DPMSOLVER_PLUS_PLUS_2M_KARRAS("DPM-Solver++ 2M Karras", 1.6f, 1.05f),
    DPMSOLVER_SDE("DPM-Solver++ SDE", 1.0f, 1.2f),
    DPMSOLVER_SDE_KARRAS("DPM-Solver++ SDE Karras", 1.1f, 1.25f),
    DDIM("DDIM", 0.8f, 1.0f),
    PNDM("PNDM", 1.0f, 1.0f),
    UNIPC("UniPC", 1.2f, 0.95f),
    
    // 快速调度器
    LCM("LCM", 3.0f, 0.85f),
    LCM_FAST("LCM Fast", 4.0f, 0.80f),
    TURBO("Turbo", 3.5f, 0.82f),
    
    // 高质量调度器
    TCD("TCD", 0.7f, 1.3f),
    TCD_LIGHTNING("TCD Lightning", 2.5f, 0.90f),
    
    // 动漫优化
    DDPM("DDPM", 0.5f, 1.2f),
    LMS("LMS", 0.9f, 1.0f),
    LMS_KARRAS("LMS Karras", 0.95f, 1.1f);
    
    val isFast: Boolean get() = speed > 2.0f
    val isHighQuality: Boolean get() = quality > 1.1f
}

/**
 * 宽高比
 */
enum class AspectRatio(val displayName: String, val widthRatio: Int, val heightRatio: Int) {
    SQUARE("1:1", 1, 1),
    PORTRAIT_3_4("3:4", 3, 4),
    PORTRAIT_2_3("2:3", 2, 3),
    LANDSCAPE_4_3("4:3", 4, 3),
    LANDSCAPE_3_2("3:2", 3, 2),
    WIDE_16_9("16:9", 16, 9),
    CINEMATIC_21_9("21:9", 21, 9);
    
    val width: Int get() = widthRatio * 512
    val height: Int get() = heightRatio * 512
}

/**
 * 常用分辨率
 */
enum class Resolution(
    val displayName: String,
    val width: Int,
    val height: Int,
    val is4K: Boolean = false,
    val isSDXL: Boolean = false
) {
    // SD 1.5 分辨率
    SQUARE_512("512×512", 512, 512),
    PORTRAIT_512_768("512×768", 512, 768),
    LANDSCAPE_768_512("768×512", 768, 512),
    SQUARE_768("768×768", 768, 768),
    
    // SDXL 分辨率 (支持 4K)
    SDXL_PORTRAIT("SDXL 竖版", 896, 1152, isSDXL = true),
    SDXL_LANDSCAPE("SDXL 横版", 1152, 896, isSDXL = true),
    SDXL_SQUARE("SDXL 方图", 1024, 1024, isSDXL = true),
    
    // 4K 分辨率
    HD_1920_1080("1920×1080 (Full HD)", 1920, 1080, is4K = true),
    QHD_2560_1440("2560×1440 (2K)", 2560, 1440, is4K = true),
    UHD_3840_2160("3840×2160 (4K)", 3840, 2160, is4K = true);
}

/**
 * Hires.fix 超分算法
 */
enum class HiresUpscaler(
    val displayName: String,
    val description: String,
    val speed: Float,
    val quality: Float
) {
    LATENT("Latent", "潜在空间上采样 - 快速但可能有模糊", 1.0f, 0.8f),
    LATENT_PLUS_PLUS("Latent++", "潜在空间++上采样 - 平衡速度和质量", 1.2f, 0.9f),
    NEAREST_EXACT("Nearest Exact", "最近邻精确 - 最快但可能有锯齿", 0.5f, 0.7f),
    BILINEAR("Bilinear", "双线性插值 - 柔和平滑", 0.8f, 0.85f),
    BICUBIC("Bicubic", "双三次插值 - 较好质量", 1.0f, 0.9f),
    LANCZOS("Lanczos", "Lanczos 重采样 - 高质量", 1.5f, 0.95f),
    R_ESRGAN_4X("R-ESRGAN 4x", "R-ESRGAN 4倍放大 - 优秀细节恢复", 2.0f, 1.2f),
    R_ESRGAN_4X_PLUS("R-ESRGAN 4x+", "R-ESRGAN 4x+ 增强版", 2.5f, 1.3f),
    R_ESRGAN_4X_ANIME("R-ESRGAN 4x Anime", "动漫专用 - 锐利边缘", 2.0f, 1.15f),
    SWINIR_4X("SwinIR 4x", "SwinIR 4倍放大 - 最新算法", 3.0f, 1.25f),
    GFPGAN("GFPGAN", "人脸修复专用", 1.5f, 1.1f),
    CODEFORMER("CodeFormer", "CodeFormer 人脸修复", 2.0f, 1.15f);
    
    val isAIUpscaler: Boolean get() = quality > 1.0f
    val isFast: Boolean get() = speed > 2.0f
}

// ============================================================
// 方向二：更强大控制
// ============================================================

/**
 * ControlNet 控制类型
 */
enum class ControlNetType(
    val displayName: String,
    val icon: String,
    val description: String,
    val modelSuffix: String
) {
    NONE("无", "❌", "不使用 ControlNet", ""),
    CANNY("Canny 边缘", "🔲", "使用 Canny 边缘检测控制构图", "canny"),
    DEPTH("Depth 深度", "🗺️", "使用深度图控制空间结构", "depth"),
    DEPTH_ZOE("Depth ZoE", "🌍", "更准确的深度估计", "depth_zoe"),
    NORMAL("Normal Map", "🧊", "使用法线图控制表面细节", "normal"),
    POSE("OpenPose 姿态", "🧍", "使用姿态检测控制人物姿势", "pose"),
    SCRIBBLE("Scribble 涂鸦", "✏️", "使用手绘涂鸦控制轮廓", "scribble"),
    SOFTEDGE("SoftEdge", "🌫️", "柔和边缘检测", "softedge"),
    LINEART("LineArt 线稿", "📐", "线条艺术提取", "lineart"),
    LINEART_COARSE("LineArt Coarse", "📏", "粗线条稿", "lineart_coarse"),
    SEG("Segmentation", "🟦", "语义分割控制", "seg"),
    SHUFFLE("Shuffle 风格迁移", "🎭", "风格迁移参考", "shuffle"),
    INPAINT("Inpaint 局部修复", "🖌️", "局部重绘控制", "inpaint"),
    IP2P("Image-to-Image", "🖼️", "图生图转换", "ip2p"),
    REFERENCE("Reference 参考", "📸", "风格参考", "reference"),
    RECOLOR("ReColor 着色", "🎨", "图像着色控制", "recolor"),
    BLUR("Blur 模糊", "💧", "模糊引导", "blur"),
    MIP("MIP 遮罩", "🔳", "MIP 遮罩控制", "mip"),
    
    // Tile 相关
    TILE("Tile 分块", "🧩", "分块控制保持细节", "tile"),
    TILE_COLORFIX("Tile ColorFix", "🎨", "分块色彩修复", "tile_colorfix"),
    TILE_COLORFIX_SHARP("Tile ColorFix Sharp", "🔪", "分块色彩锐化", "tile_colorfix_sharp");
    
    val isAvailable: Boolean get() = this != NONE
    val isAdvanced: Boolean get() = this in listOf(TILE, SHUFFLE, REFERENCE, RECOLOR)
}

/**
 * ControlNet 预处理类型
 */
enum class ControlNetPreprocessor(
    val displayName: String,
    val outputType: String,
    val description: String
) {
    CANNY_EDGE("Canny Edge", "Edge", "经典边缘检测"),
    CANNY_THRESHOLD_LOW("Canny (Low)", "Edge", "低阈值边缘"),
    CANNY_THRESHOLD_MEDIUM("Canny (Medium)", "Edge", "中等阈值边缘"),
    CANNY_THRESHOLD_HIGH("Canny (High)", "Edge", "高阈值边缘"),
    
    DEPTH_MIDAS("Depth Midas", "Depth", "Midas 深度估计"),
    DEPTH_ZOE("Depth Zoe", "Depth", "ZoeDepth 深度估计"),
    DEPTH_LERF("Depth LERF", "Depth", "LERF 深度估计"),
    
    NORMAL_MIDAS("Normal Midas", "Normal", "Midas 法线图"),
    NORMAL_BAE("Normal BAE", "Normal", "BAE 法线估计"),
    
    POSE_OPENPOSE_FULL("OpenPose Full", "Pose", "完整姿态检测"),
    POSE_OPENPOSE_BODY("OpenPose Body", "Pose", "身体姿态检测"),
    POSE_OPENPOSE_FACE("OpenPose Face", "Pose", "面部关键点"),
    POSE_OPENPOSE_HAND("OpenPose Hand", "Pose", "手部姿态"),
    POSE_OPENPOSE_ALL("OpenPose All", "Pose", "全身姿态"),
    
    SCRIBBLE_HOG("Scribble HOG", "Scribble", "HOG 涂鸦"),
    SCRIBBLE_PIDINET("Scribble PIDINet", "Scribble", "PIDINet 涂鸦"),
    
    SEGMENTATION_UNIVNET("Segmentation UnivNet", "Seg", "UNet 分割"),
    SEGMENTATION_ONEFormer("Segmentation OneFormer", "Seg", "OneFormer 分割"),
    
    LINEART_REALISTIC("LineArt Realistic", "LineArt", "写实线条"),
    LINEART_ANIME("LineArt Anime", "LineArt", "动漫线条"),
    
    SHUFFLE("Shuffle", "Image", "风格洗牌"),
    REFERENCE("Reference", "Image", "参考图像"),
    IP2P("Img2Img Pipeline", "Image", "图生图管道");
}

/**
 * 生成模式
 */
enum class GenerationMode {
    TXT2IMG,      // 文生图
    IMG2IMG,      // 图生图
    INPAINT,      // 局部重绘
    UPSCALE,      // 超分辨率
    PIX2PIX,      // 图像转换
    CONTROLLED    // 控制生成
}

// ============================================================
// 方向三：更多模型生态
// ============================================================

/**
 * LoRA 参数
 */
data class LoraParam(
    val id: String,
    val name: String,
    val path: String,
    val weight: Float = 1.0f,           // LoRA 权重
    val clipWeight: Float = 1.0f,       // CLIP 权重
    val isEnabled: Boolean = true,
    val category: LoraCategory = LoraCategory.STYLE,
    val triggerWords: List<String> = emptyList()
) {
    companion object {
        fun default(name: String, weight: Float = 1.0f) = LoraParam(
            id = name.lowercase().replace(" ", "_"),
            name = name,
            path = "models/lora/$name.safetensors",
            weight = weight
        )
    }
}

/**
 * LoRA 分类
 */
enum class LoraCategory(val displayName: String, val icon: String) {
    STYLE("风格", "🎨"),
    CHARACTER("角色", "👤"),
    CONCEPT("概念", "💡"),
    POSE("姿势", "🧍"),
    HAIR("发型", "💇"),
    CLOTHING("服装", "👗"),
    BACKGROUND("背景", "🏞️"),
    LIGHTING("光照", "💡"),
    CAMERA("相机", "📷"),
    OTHER("其他", "📦")
}

/**
 * VAE 模型
 */
data class VAEParam(
    val id: String,
    val name: String,
    val path: String,
    val description: String = "",
    val isDefault: Boolean = false,
    val usedFor: VAEUsage = VAEUsage.BOTH
)

enum class VAEUsage(val displayName: String) {
    ENCODE("编码"),
    DECODE("解码"),
    BOTH("编码/解码")
}

/**
 * Embedding 嵌入
 */
data class EmbeddingParam(
    val id: String,
    val name: String,
    val path: String,
    val tokens: Int = 1,
    val isEnabled: Boolean = true,
    val activationText: String = ""  // 触发词
)

// ============================================================
// 方向四：性能优化
// ============================================================

/**
 * ONNX Provider 类型
 */
enum class ONNXProvider(
    val displayName: String,
    val priority: Int,
    val description: String
) {
    CPU("CPU", 0, "通用 CPU 推理"),
    CUDA("CUDA", 100, "NVIDIA GPU 加速"),
    DIRECTML("DirectML", 90, "Windows GPU 加速"),
    COREML("CoreML", 95, "Apple Silicon 加速"),
    QNN("QNN", 110, "Qualcomm NPU 加速"),
    OPENCL("OpenCL", 80, "通用 GPU 加速"),
    VULKAN("Vulkan", 85, "移动 GPU 加速"),
    NNAPI("NNAPI", 70, "Android Neural Networks API"),
    OPENVINO("OpenVINO", 75, "Intel 硬件加速"),
    XNNPACK("XNNPACK", 60, "优化的 CPU 神经网络"),
    
    // 组合 Provider
    AUTO_GPU("Auto GPU", 150, "自动选择最佳 GPU"),
    AUTO_ALL("Auto All", 200, "最佳加速方案");
    
    val isFast: Boolean get() = priority > 80
    val isHardwareAccelerated: Boolean get() = priority > 70
    
    companion object {
        fun getBestAvailable(): ONNXProvider {
            return when {
                isAndroid() && hasNPU() -> QNN
                isAndroid() && hasGPU() -> VULKAN
                isAppleSilicon() -> COREML
                hasNvidiaGPU() -> CUDA
                isWindows() && hasGPU() -> DIRECTML
                else -> CPU
            }
        }
        
        private fun isAndroid() = android.os.Build.VERSION.SDK_INT >= 24
        private fun isAppleSilicon() = System.getProperty("os.arch")?.contains("aarch64") == true
        private fun isWindows() = System.getProperty("os.name")?.contains("indows") == true
        private fun hasNPU() = true // 简化检测
        private fun hasGPU() = true
        private fun hasNvidiaGPU() = false
    }
}

/**
 * 优化级别
 */
enum class OptimizationLevel(
    val displayName: String,
    val speedMultiplier: Float,
    val qualityRetention: Float
) {
    NONE("无优化", 1.0f, 1.0f),
    BASIC("基础优化", 1.2f, 0.98f),
    AGGRESSIVE("激进优化", 1.5f, 0.95f),
    MAXIMUM("最大加速", 2.0f, 0.90f);
}

/**
 * 内存优化策略
 */
enum class MemoryStrategy(
    val displayName: String,
    val memoryReduction: Float,
    val speedImpact: Float
) {
    BALANCED("均衡", 1.0f, 1.0f),
    LOW_MEMORY("低显存", 0.5f, 0.9f),
    ULTRA_LOW("极低显存", 0.3f, 0.7f),
    HIGH_QUALITY("高质量", 1.0f, 1.1f);
}

// ============================================================
// 方向五：UI 升级 - 预设模板
// ============================================================

/**
 * 预设模板
 */
data class PromptTemplate(
    val id: String,
    val name: String,
    val icon: String,
    val category: TemplateCategory,
    val positivePrompt: String,
    val negativePrompt: String = "",
    val defaultSteps: Int = 25,
    val defaultGuidance: Float = 7.5f,
    val defaultBaseModel: BaseModelType = BaseModelType.SD_1_5,
    val defaultWidth: Int = 512,
    val defaultHeight: Int = 512,
    val isBuiltIn: Boolean = true,
    val isFavorite: Boolean = false,
    val usageCount: Int = 0
)

/**
 * 模板分类
 */
enum class TemplateCategory(val displayName: String, val emoji: String) {
    PORTRAIT("人像", "👤"),
    LANDSCAPE("风景", "🏞️"),
    ANIME("动漫", "🎌"),
    ABSTRACT("抽象", "🎨"),
    CONCEPT("概念", "💡"),
    SCENE("场景", "🎬"),
    CHARACTER("角色", "🧑"),
    OBJECT("物体", "📦"),
    ARCHITECTURE("建筑", "🏛️"),
    CUSTOM("自定义", "⭐")
}

/**
 * 预设模板集合
 */
object PresetTemplates {
    val builtInTemplates = listOf(
        // 人像类
        PromptTemplate(
            id = "portrait_photo",
            name = "写真照片",
            icon = "📸",
            category = TemplateCategory.PORTRAIT,
            positivePrompt = "portrait photo of a person, detailed face, natural lighting, professional photography, 8k, highly detailed",
            negativePrompt = "anime, cartoon, painting, drawing, illustration, animated, cgi, 3d render",
            defaultSteps = 25,
            defaultGuidance = 7.5f,
            defaultWidth = 512,
            defaultHeight = 768
        ),
        PromptTemplate(
            id = "anime_girl",
            name = "动漫少女",
            icon = "🎌",
            category = TemplateCategory.ANIME,
            positivePrompt = "1girl, anime style, beautiful detailed eyes, detailed hair, school uniform, soft lighting, best quality",
            negativePrompt = "realistic, photorealistic, 3d render, man, male, multiple heads, extra limbs",
            defaultSteps = 30,
            defaultGuidance = 8.0f,
            defaultBaseModel = BaseModelType.SD_1_5,
            defaultWidth = 512,
            defaultHeight = 768
        ),
        PromptTemplate(
            id = "anime_style_sdxl",
            name = "SDXL 动漫",
            icon = "⚡",
            category = TemplateCategory.ANIME,
            positivePrompt = "1girl, anime style illustration, detailed face, beautiful hair, vibrant colors, high quality",
            negativePrompt = "realistic, photorealistic, photograph, 3d render, low quality",
            defaultSteps = 20,
            defaultGuidance = 7.0f,
            defaultBaseModel = BaseModelType.SD_XL,
            defaultWidth = 1024,
            defaultHeight = 1024
        ),
        
        // 风景类
        PromptTemplate(
            id = "landscape_nature",
            name = "自然风景",
            icon = "🏔️",
            category = TemplateCategory.LANDSCAPE,
            positivePrompt = "landscape, mountain, forest, lake, sunset, golden hour, nature photography, highly detailed, 8k",
            negativePrompt = "person, people, building, house, text, watermark, blurry",
            defaultSteps = 30,
            defaultGuidance = 7.5f,
            defaultWidth = 768,
            defaultHeight = 512
        ),
        PromptTemplate(
            id = "cityscape_night",
            name = "城市夜景",
            icon = "🌃",
            category = TemplateCategory.LANDSCAPE,
            positivePrompt = "cityscape at night, neon lights, cyberpunk, rainy streets, reflections, detailed buildings",
            negativePrompt = "daytime, sunny, cartoon, anime, low quality",
            defaultSteps = 25,
            defaultGuidance = 7.0f,
            defaultWidth = 768,
            defaultHeight = 512
        ),
        
        // 概念类
        PromptTemplate(
            id = "concept_art",
            name = "概念设计",
            icon = "💡",
            category = TemplateCategory.CONCEPT,
            positivePrompt = "concept art, character design, detailed, highly detailed, digital art, trending on artstation",
            negativePrompt = "photograph, photo-realistic, low quality, blurry",
            defaultSteps = 35,
            defaultGuidance = 8.0f,
            defaultWidth = 512,
            defaultHeight = 768
        ),
        
        // 快速生成
        PromptTemplate(
            id = "quick_sketch",
            name = "快速草图",
            icon = "⚡",
            category = TemplateCategory.ABSTRACT,
            positivePrompt = "detailed illustration, clean lines, artistic",
            negativePrompt = "blurry, low quality, distorted",
            defaultSteps = 10,
            defaultGuidance = 6.0f,
            defaultBaseModel = BaseModelType.SD_XL_TURBO,
            defaultWidth = 512,
            defaultHeight = 512
        ),
        
        // 4K 高质量
        PromptTemplate(
            id = "4k_detailed",
            name = "4K 高清",
            icon = "🖼️",
            category = TemplateCategory.CONCEPT,
            positivePrompt = "highly detailed, 4k, 8k, ultra sharp, intricate details, masterpiece",
            negativePrompt = "low quality, blurry, pixelated, watermark",
            defaultSteps = 40,
            defaultGuidance = 7.5f,
            defaultBaseModel = BaseModelType.SD_XL,
            defaultWidth = 1024,
            defaultHeight = 1024
        ),
        
        // 建筑类
        PromptTemplate(
            id = "architecture",
            name = "建筑设计",
            icon = "🏛️",
            category = TemplateCategory.ARCHITECTURE,
            positivePrompt = "architectural rendering, modern building, clean design, detailed, professional, 3d visualization",
            negativePrompt = "person, people, cartoon, low quality",
            defaultSteps = 30,
            defaultGuidance = 7.5f,
            defaultWidth = 768,
            defaultHeight = 768
        ),
        
        // 物体类
        PromptTemplate(
            id = "product_shot",
            name = "产品展示",
            icon = "📦",
            category = TemplateCategory.OBJECT,
            positivePrompt = "product photography, studio lighting, clean background, professional, commercial, high detail",
            negativePrompt = "dirty, messy, background clutter, text, watermark",
            defaultSteps = 25,
            defaultGuidance = 8.0f,
            defaultWidth = 512,
            defaultHeight = 512
        )
    )
    
    fun getByCategory(category: TemplateCategory): List<PromptTemplate> {
        return builtInTemplates.filter { it.category == category }
    }
    
    fun search(query: String): List<PromptTemplate> {
        val lowerQuery = query.lowercase()
        return builtInTemplates.filter {
            it.name.lowercase().contains(lowerQuery) ||
            it.positivePrompt.lowercase().contains(lowerQuery) ||
            it.category.displayName.contains(query)
        }
    }
}

// ============================================================
// 生成参数与进度
// ============================================================

/**
 * 生成参数
 */
data class GenerationParams(
    // 基础参数
    val positivePrompt: String,
    val negativePrompt: String = "",
    val width: Int = 512,
    val height: Int = 512,
    val steps: Int = 25,
    val guidanceScale: Float = 7.5f,
    val seed: Long = -1,
    val scheduler: SchedulerType = SchedulerType.DPMSOLVER_PLUS_PLUS_2M_KARRAS,
    val batchSize: Int = 1,
    val clipSkip: Int = 0,
    val baseModel: BaseModelType = BaseModelType.SD_1_5,
    
    // 方向一：高质量生成
    val enableHiresFix: Boolean = false,
    val hiresScale: Float = 1.5f,
    val hiresSteps: Int = 15,
    val hiresDenoise: Float = 0.4f,
    val hiresUpscaler: HiresUpscaler = HiresUpscaler.LANCZOS,
    val enableRefiner: Boolean = false,
    val refinerStart: Float = 0.8f,
    
    // 方向二：控制
    val enableControlNet: Boolean = false,
    val controlNetType: ControlNetType = ControlNetType.NONE,
    val controlNetWeight: Float = 1.0f,
    val controlNetGuidanceStart: Float = 0.0f,
    val controlNetGuidanceEnd: Float = 1.0f,
    val preprocessor: ControlNetPreprocessor? = null,
    
    // 方向三：模型生态
    val selectedLoras: List<LoraParam> = emptyList(),
    val vaeModel: String? = null,
    val selectedEmbeddings: List<String> = emptyList(),
    
    // 方向四：性能
    val enableONNX: Boolean = false,
    val onnxProvider: ONNXProvider = ONNXProvider.CPU,
    val enableFP16: Boolean = true,
    val cpuThreads: Int = 4,
    val optimizationLevel: OptimizationLevel = OptimizationLevel.BASIC,
    
    // 图生图
    val strength: Float = 0.7f,
    val inputImage: String? = null,
    val maskImage: String? = null,
    
    // 模式
    val mode: GenerationMode = GenerationMode.TXT2IMG
) {
    val is4K: Boolean get() = width >= 1024 || height >= 1024
    val isSDXL: Boolean get() = baseModel.supportsSDXL
    val totalSteps: Int get() = steps + (if (enableHiresFix) hiresSteps else 0)
    val estimatedTimeMs: Long get() = (steps * guidanceScale * (1000 / scheduler.speed)).toLong()
}

/**
 * 生成进度
 */
sealed class GenerationProgress {
    data class Status(val message: String) : GenerationProgress()
    data class Progress(
        val currentStep: Int,
        val totalSteps: Int,
        val percent: Float,
        val elapsedMs: Long = 0,
        val etaMs: Long = 0
    ) : GenerationProgress()
    data class Completed(val paths: List<String>) : GenerationProgress()
    data class Error(val message: String) : GenerationProgress()
    
    // 方向二：ControlNet 进度
    data class ControlNetProgress(
        val type: ControlNetType,
        val percent: Float
    ) : GenerationProgress()
    
    // 方向一：Hires.fix 进度
    data class HiresFixProgress(
        val phase: String,
        val currentPhase: Int,
        val totalPhases: Int,
        val percent: Float
    ) : GenerationProgress()
    
    // 批量生成进度
    data class BatchProgress(
        val currentBatch: Int,
        val totalBatch: Int,
        val currentImage: Int,
        val percent: Float
    ) : GenerationProgress()
    
    // Refiner 进度
    data class RefinerProgress(
        val percent: Float
    ) : GenerationProgress()
}

/**
 * 历史记录项
 */
data class HistoryItem(
    val id: String,
    val timestamp: Long,
    val params: GenerationParams,
    val outputPaths: List<String>,
    val thumbnailPath: String? = null,
    val status: HistoryStatus = HistoryStatus.COMPLETED,
    val errorMessage: String? = null,
    val generationTimeMs: Long = 0
)

enum class HistoryStatus {
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * 模型信息
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val path: String,
    val type: ModelType,
    val size: Long,
    val baseModel: BaseModelType? = null,
    val isDownloaded: Boolean = false,
    val isBuiltIn: Boolean = false,
    val downloadProgress: Float = 0f,
    val hash: String? = null
)

enum class ModelType {
    CHECKPOINT,
    LORA,
    VAE,
    EMBEDDING,
    CONTROLNET,
    UPSCALER,
    REFINER,
    OMNI
}

/**
 * 模型格式
 */
enum class ModelFormat(val extensions: List<String>) {
    PT(listOf("pt", "pth")),
    CKPT(listOf("ckpt")),
    SAFETENSORS(listOf("safetensors")),
    MNN(listOf("mnn")),
    ONNX(listOf("onnx"))
}

/**
 * 模型下载源
 */
data class ModelSource(
    val name: String,
    val url: String,
    val mirrors: List<String> = emptyList()
)

/**
 * 预设下载源
 */
object ModelSources {
    val huggingface = ModelSource(
        name = "HuggingFace",
        url = "https://huggingface.co"
    )
    
    val civitai = ModelSource(
        name = "Civitai",
        url = "https://civitai.com"
    )
    
    val modelscope = ModelSource(
        name = "ModelScope",
        url = "https://modelscope.cn"
    )
}

/**
 * 内存模式
 */
enum class MemoryMode(
    val displayName: String,
    val memoryReduction: Float,
    val speedImpact: Float
) {
    LOW("低显存模式", 0.5f, 0.8f),
    BALANCED("均衡模式", 0.7f, 0.9f),
    HIGH("高质量模式", 1.0f, 1.0f)
}

/**
 * 应用设置
 */
data class AppSettings(
    val engine: String = "CPU",
    val fp16Enabled: Boolean = true,
    val onnxEnabled: Boolean = false,
    val onnxProvider: String = "CPU",
    val memoryMode: MemoryMode = MemoryMode.BALANCED,
    val defaultSteps: Int = 25,
    val defaultGuidance: Float = 7.5f,
    val defaultWidth: Int = 512,
    val defaultHeight: Int = 512,
    val autoSaveHistory: Boolean = true,
    val maxHistoryItems: Int = 100
)

/**
 * 基准测试结果
 */
data class BenchmarkResult(
    val timestamp: Long = System.currentTimeMillis(),
    val engine: String,
    val steps: Int,
    val width: Int,
    val height: Int,
    val totalTimeMs: Long,
    val avgStepTimeMs: Float,
    val memoryUsedMb: Float,
    val iterationsPerSecond: Float
)
