@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST", "DEPRECATION", "USELESS_ELVIS")
package com.kehuiai.service

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 提示词模板管理器
 * 提供丰富的提示词模板
 */
class PromptTemplateManager(private val context: Context) {

    companion object {
        private const val TAG = "PromptTemplate"
    }
    
    // ========== 模板分类 ==========
    
    data class TemplateCategory(
        val id: String,
        val name: String,
        val emoji: String,
        val description: String,
        val templates: List<PromptTemplate>
    )
    
    data class PromptTemplate(
        val id: String,
        val name: String,
        val description: String,
        val prompt: String,
        val negativePrompt: String = "",
        val tags: List<String> = emptyList(),
        val isPremium: Boolean = false
    )
    
    // ========== 获取所有模板 ==========
    
    fun getAllCategories(): List<TemplateCategory> = listOf(
        // 人物
        TemplateCategory(
            id = "portrait",
            name = "人像摄影",
            emoji = "👤",
            description = "各种人像风格",
            templates = listOf(
                PromptTemplate(
                    id = "portrait_natural",
                    name = "自然人像",
                    description = "自然光线下的真实人像",
                    prompt = "photorealistic portrait, natural lighting, soft shadows, candid expression, shallow depth of field, 85mm lens, f/1.8, professional photography",
                    negativePrompt = "anime, cartoon, painting, oversaturated,过度美化",
                    tags = listOf("写真", "自然", "光影")
                ),
                PromptTemplate(
                    id = "portrait_glamour",
                    name = "时尚杂志",
                    description = "高端时尚杂志风格",
                    prompt = "fashion magazine cover, editorial photography, professional studio lighting, retouched, high-end fashion, Vogue style, 85mm lens",
                    negativePrompt = "amateur, low quality, blurry, oversaturated滤镜",
                    tags = listOf("时尚", "杂志", "商业")
                ),
                PromptTemplate(
                    id = "portrait_headshot",
                    name = "专业头像",
                    description = "商务/LinkedIn头像",
                    prompt = "professional headshot, solid background, even lighting, business attire, corporate portrait, passport photo style but artistic",
                    negativePrompt = "casual, distracting background, harsh shadows",
                    tags = listOf("商务", "头像", "专业")
                ),
                PromptTemplate(
                    id = "portrait_golden",
                    name = "黄金时刻",
                    description = "日出日落时的人像",
                    prompt = "golden hour portrait, warm sunset lighting, sun flare, bokeh background, ethereal glow, romantic atmosphere",
                    negativePrompt = "harsh lighting, cold tones, overexposed",
                    tags = listOf("黄昏", "暖色", "氛围")
                ),
                PromptTemplate(
                    id = "portrait_cyberpunk",
                    name = "赛博朋克",
                    description = "未来科技感人像",
                    prompt = "cyberpunk portrait, neon lights, futuristic city background, LED implants, holographic elements, rain reflections, dramatic lighting",
                    negativePrompt = "natural, vintage, medieval, fantasy",
                    tags = listOf("科幻", "霓虹", "未来")
                )
            )
        ),
        
        // 风景
        TemplateCategory(
            id = "landscape",
            name = "风景摄影",
            emoji = "🏔️",
            description = "壮丽的自然风景",
            templates = listOf(
                PromptTemplate(
                    id = "landscape_sunset",
                    name = "壮丽日落",
                    description = "震撼的日落场景",
                    prompt = "breathtaking sunset landscape, dramatic clouds, golden hour, reflection on water, wide angle, national geographic, 8K, highly detailed",
                    negativePrompt = "underexposed, blurry, low quality, amateur",
                    tags = listOf("日落", "金色", "壮丽")
                ),
                PromptTemplate(
                    id = "landscape_mountain",
                    name = "雪山峰",
                    description = "高海拔雪山风景",
                    prompt = "majestic mountain landscape, snow-capped peaks, crystal clear lake, morning mist, epic vista, ultra wide angle, 16mm",
                    negativePrompt = "urban, buildings, crowded, summer only",
                    tags = listOf("雪山", "日出", "云海")
                ),
                PromptTemplate(
                    id = "landscape_forest",
                    name = "神秘森林",
                    description = "迷雾森林",
                    prompt = "mystical forest, foggy atmosphere, sun rays through trees, enchanted woodland, fairy tale style, dreamy, ethereal lighting",
                    negativePrompt = "bright daylight, clear sky, urban, modern",
                    tags = listOf("森林", "迷雾", "神秘")
                ),
                PromptTemplate(
                    id = "landscape_ocean",
                    name = "海岸线",
                    description = "海景风光",
                    prompt = "dramatic ocean coastline, crashing waves, golden hour, rocky cliffs, long exposure, misty atmosphere, ultra detailed",
                    negativePrompt = "calm sea, winter, inland",
                    tags = listOf("大海", "礁石", "浪花")
                ),
                PromptTemplate(
                    id = "landscape_urban",
                    name = "城市夜景",
                    description = "繁华都市夜景",
                    prompt = "cityscape at night, illuminated skyscrapers, light trails, urban photography, reflection on wet streets, cyberpunk atmosphere",
                    negativePrompt = "rural, daytime, plain",
                    tags = listOf("夜景", "灯光", "繁华")
                )
            )
        ),
        
        // 艺术
        TemplateCategory(
            id = "art",
            name = "艺术创作",
            emoji = "🎨",
            description = "各种艺术风格",
            templates = listOf(
                PromptTemplate(
                    id = "art_oil_painting",
                    name = "油画风格",
                    description = "古典油画效果",
                    prompt = "oil painting style, classical art, Renaissance influence, museum quality, dramatic lighting, impasto technique, rich colors",
                    negativePrompt = "digital art, photograph, cartoon, minimalist",
                    tags = listOf("油画", "古典", "博物馆")
                ),
                PromptTemplate(
                    id = "art_watercolor",
                    name = "水彩画",
                    description = "水彩艺术风格",
                    prompt = "watercolor painting, soft gradients, delicate brushstrokes, artistic, flowing colors, paper texture visible",
                    negativePrompt = "sharp edges, digital, photograph, hyperrealistic",
                    tags = listOf("水彩", "柔和", "艺术")
                ),
                PromptTemplate(
                    id = "art_anime",
                    name = "动漫风格",
                    description = "日式动漫插画",
                    prompt = "anime style illustration, vibrant colors, cel shading, detailed background, Studio Ghibli inspired, beautiful scenery",
                    negativePrompt = "photorealistic, western cartoon, dark, gritty",
                    tags = listOf("动漫", "插画", "日系")
                ),
                PromptTemplate(
                    id = "art_digital",
                    name = "数字艺术",
                    description = "现代数字艺术",
                    prompt = "digital art, concept art, detailed illustration, vibrant colors, epic composition, trending on artstation, 8K",
                    negativePrompt = "traditional art, photograph, amateur",
                    tags = listOf("数字", "概念", "现代")
                ),
                PromptTemplate(
                    id = "art_pencil",
                    name = "铅笔素描",
                    description = "细腻铅笔画",
                    prompt = "pencil sketch, detailed drawing, cross-hatching, realistic shading, black and white, hand-drawn texture",
                    negativePrompt = "color, digital, painting, cartoon",
                    tags = listOf("素描", "黑白", "手绘")
                )
            )
        ),
        
        // 商业
        TemplateCategory(
            id = "commercial",
            name = "商业设计",
            emoji = "💼",
            description = "商业应用场景",
            templates = listOf(
                PromptTemplate(
                    id = "commercial_product",
                    name = "产品展示",
                    description = "电商产品图",
                    prompt = "product photography, white background, professional lighting, commercial quality, clean and minimal, e-commerce ready",
                    negativePrompt = "cluttered, messy, dark, amateur",
                    tags = listOf("产品", "电商", "商业")
                ),
                PromptTemplate(
                    id = "commercial_food",
                    name = "美食摄影",
                    description = "诱人的美食照",
                    prompt = "food photography, appetizing presentation, restaurant style, soft lighting, shallow depth, top-down angle, professional",
                    negativePrompt = "raw, messy, unprofessional, dark",
                    tags = listOf("美食", "餐饮", "诱人")
                ),
                PromptTemplate(
                    id = "commercial_3d",
                    name = "3D渲染",
                    description = "3D产品展示",
                    prompt = "3D render, product visualization, studio lighting, clean background, octane render, blender, unreal engine, hyperrealistic",
                    negativePrompt = "2D, flat design, photograph, sketch",
                    tags = listOf("3D", "渲染", "CGI")
                ),
                PromptTemplate(
                    id = "commercial_poster",
                    name = "海报设计",
                    description = "商业海报",
                    prompt = "poster design, graphic design, typography, bold colors, modern layout, commercial art, print ready, A3 size",
                    negativePrompt = "photograph only, messy, unprofessional",
                    tags = listOf("海报", "平面", "印刷")
                )
            )
        ),
        
        // 创意
        TemplateCategory(
            id = "creative",
            name = "创意灵感",
            emoji = "💡",
            description = "天马行空的想法",
            templates = listOf(
                PromptTemplate(
                    id = "creative_surreal",
                    name = "超现实主义",
                    description = "梦幻超现实场景",
                    prompt = "surrealist art, dreamlike atmosphere, floating islands, impossible architecture, Salvador Dali inspired, magical realism",
                    negativePrompt = "realistic, normal, boring, plain",
                    tags = listOf("超现实", "梦幻", "艺术")
                ),
                PromptTemplate(
                    id = "creative_fantasy",
                    name = "奇幻世界",
                    description = "魔幻题材",
                    prompt = "fantasy world, epic scene, magical creatures, ancient ruins, dramatic lighting, Lord of the Rings inspired, highly detailed",
                    negativePrompt = "modern, sci-fi, urban, realistic",
                    tags = listOf("魔幻", "史诗", "史诗")
                ),
                PromptTemplate(
                    id = "creative_space",
                    name = "太空宇宙",
                    description = "星际题材",
                    prompt = "space scene, nebula, galaxies, stars, planet, cosmic dust, Hubble telescope view, awe-inspiring, 8K resolution",
                    negativePrompt = "earth, ground, ocean, urban",
                    tags = listOf("太空", "宇宙", "星际")
                ),
                PromptTemplate(
                    id = "creative_steampunk",
                    name = "蒸汽朋克",
                    description = "复古机械风",
                    prompt = "steampunk scene, Victorian era, brass machinery, gears, airships, steam power, detailed, atmospheric",
                    negativePrompt = "modern, futuristic, digital, minimal",
                    tags = listOf("蒸汽朋克", "复古", "机械")
                )
            )
        ),
        
        // 特定风格
        TemplateCategory(
            id = "specific",
            name = "特定风格",
            emoji = "✨",
            description = "特定的艺术风格",
            templates = listOf(
                PromptTemplate(
                    id = "style_dual_realism",
                    name = "双重曝光人像",
                    description = "人像与风景结合",
                    prompt = "double exposure portrait, face merged with nature scene, dramatic, artistic, silhouette, ethereal effect, black and white",
                    negativePrompt = "single exposure, plain, simple",
                    tags = listOf("双重曝光", "创意", "艺术")
                ),
                PromptTemplate(
                    id = "style_minimalist",
                    name = "极简主义",
                    description = "简洁设计风格",
                    prompt = "minimalist art, clean composition, simple shapes, limited color palette, negative space, modern design, elegant",
                    negativePrompt = "busy, cluttered, detailed, ornate",
                    tags = listOf("极简", "简洁", "现代")
                ),
                PromptTemplate(
                    id = "style_retro",
                    name = "复古怀旧",
                    description = "复古色调风格",
                    prompt = "retro photography style, 1970s aesthetic, warm tones, film grain, vintage colors, nostalgic mood, Kodak film",
                    negativePrompt = "modern, digital colors, clean, sharp",
                    tags = listOf("复古", "怀旧", "胶片")
                ),
                PromptTemplate(
                    id = "style_macro",
                    name = "微距摄影",
                    description = "微观世界",
                    prompt = "macro photography, extreme close-up, intricate details, shallow depth of field, insect or flower, nature photography, 100mm macro",
                    negativePrompt = "wide angle, landscape, human",
                    tags = listOf("微距", "细节", "特写")
                ),
                PromptTemplate(
                    id = "style_long_exposure",
                    name = "长曝光",
                    description = "丝绸般的流水",
                    prompt = "long exposure photography, silky water, cloud trails, light painting, dreamy, ethereal, professional technique",
                    negativePrompt = "sharp water, static, daytime without filter",
                    tags = listOf("长曝光", "慢门", "梦幻")
                )
            )
        )
    )
    
    // ========== 搜索功能 ==========
    
    fun searchTemplates(query: String): List<PromptTemplate> {
        val lowerQuery = query.lowercase()
        return getAllCategories()
            .flatMap { it.templates }
            .filter { template ->
                template.name.lowercase().contains(lowerQuery) ||
                template.description.lowercase().contains(lowerQuery) ||
                template.tags.any { it.lowercase().contains(lowerQuery) }
            }
    }
    
    // ========== 获取推荐 ==========
    
    fun getRecommendedTemplates(): List<PromptTemplate> {
        return getAllCategories()
            .flatMap { it.templates }
            .filter { !it.isPremium }
            .shuffled()
            .take(10)
    }
}
