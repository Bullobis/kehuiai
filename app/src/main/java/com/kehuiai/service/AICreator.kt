package com.kehuiai.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * 可绘AI v3.5.0 - 智能创作者
 */
class AICreator(private val context: Context) {

    companion object {
        private const val TAG = "AICreator"
    }
    
    data class PromptSuggestion(
        val prompt: String,
        val negativePrompt: String = "",
        val style: String = "",
        val confidence: Float = 0f,
        val reason: String = ""
    )
    
    data class GenerationAdvice(
        val model: String,
        val steps: Int,
        val guidance: Float,
        val tips: List<String>
    )
    
    data class ImageScore(
        val overall: Float,
        val composition: Float,
        val color: Float,
        val detail: Float,
        val suggestions: List<String>
    )
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val _currentSuggestion = MutableSharedFlow<PromptSuggestion>()
    val currentSuggestion: SharedFlow<PromptSuggestion> = _currentSuggestion.asSharedFlow()
    
    /**
     * 根据关键词生成最佳提示词
     */
    suspend fun generatePrompt(keywords: String, targetStyle: String? = null): PromptSuggestion = 
        withContext(Dispatchers.Default) {
            Log.i(TAG, "生成提示词: $keywords")
            
            val basePrompt = buildPrompt(keywords, targetStyle)
            val negative = "low quality, worst quality, blurry, distorted, watermark, signature"
            val style = targetStyle ?: detectStyle(keywords)
            
            val suggestion = PromptSuggestion(
                prompt = basePrompt,
                negativePrompt = negative,
                style = style,
                confidence = 0.85f,
                reason = "基于关键词「$keywords」自动生成"
            )
            
            _currentSuggestion.emit(suggestion)
            suggestion
        }
    
    /**
     * 获取生成建议
     */
    suspend fun getAdvice(prompt: String): GenerationAdvice = withContext(Dispatchers.Default) {
        val hasAnime = prompt.contains("anime", true) || prompt.contains("动漫", true)
        val hasPortrait = prompt.contains("girl", true) || prompt.contains("boy", true) || prompt.contains("人", true)
        val hasLandscape = prompt.contains("landscape", true) || prompt.contains("风景", true)
        
        val model = when {
            hasAnime -> "sdxl_anime"
            hasPortrait -> "majicmix"
            hasLandscape -> "realistic"
            else -> "sd15"
        }
        
        val steps = when {
            hasAnime -> 25
            hasPortrait -> 30
            else -> 20
        }
        
        val guidance = when {
            hasAnime -> 6f
            hasPortrait -> 8f
            else -> 7f
        }
        
        val tips = mutableListOf<String>()
        if (hasAnime) tips.add("推荐使用动漫优化模型")
        if (hasPortrait) tips.add("可开启面部修复增强效果")
        tips.add("适当降低guidance可增加创意性")
        
        GenerationAdvice(model, steps, guidance, tips)
    }
    
    /**
     * 评分作品
     */
    suspend fun scoreImage(imagePath: String): ImageScore = withContext(Dispatchers.Default) {
        delay(500) // 模拟分析
        ImageScore(
            overall = 8.5f,
            composition = 8.0f,
            color = 9.0f,
            detail = 8.5f,
            suggestions = listOf("构图可以更平衡", "色彩搭配很出色", "细节处理到位")
        )
    }
    
    /**
     * 推荐提示词
     */
    fun getRecommendedPrompts(category: String): List<String> {
        return when (category) {
            "portrait" -> listOf(
                "masterpiece, best quality, 1girl, anime style, detailed face",
                "masterpiece, realistic portrait, professional lighting",
                "beautiful woman, fashion photography, studio lighting"
            )
            "landscape" -> listOf(
                "masterpiece, beautiful landscape, golden hour, cinematic",
                "fantasy world, floating islands, magical atmosphere",
                "peaceful nature, mountain stream, zen garden"
            )
            "art" -> listOf(
                "digital art, concept art, detailed illustration",
                "oil painting style, renaissance, classical art",
                "watercolor painting, soft colors, dreamy atmosphere"
            )
            else -> listOf(
                "masterpiece, best quality, detailed",
                "high quality, 8k, professional",
                "amazing art, trending on artstation"
            )
        }
    }
    
    /**
     * 优化现有提示词
     */
    suspend fun optimizePrompt(currentPrompt: String): PromptSuggestion = withContext(Dispatchers.Default) {
        val optimized = buildString {
            append("masterpiece, best quality, ")
            append(currentPrompt)
            append(", highly detailed, sharp focus")
        }
        
        PromptSuggestion(
            prompt = optimized,
            negativePrompt = "low quality, worst quality, blurry, bad anatomy",
            style = detectStyle(currentPrompt),
            confidence = 0.9f,
            reason = "已添加质量修饰词进行优化"
        )
    }
    
    private fun buildPrompt(keywords: String, style: String?): String {
        return buildString {
            append("masterpiece, best quality, ")
            append(keywords)
            style?.let { append(", $it style") }
            append(", highly detailed, sharp focus, professional")
        }
    }
    
    private fun detectStyle(keywords: String): String {
        return when {
            keywords.contains("anime", true) || keywords.contains("动漫", true) -> "anime"
            keywords.contains("realistic", true) || keywords.contains("写实", true) -> "realistic"
            keywords.contains("oil painting", true) || keywords.contains("油画", true) -> "oil painting"
            keywords.contains("watercolor", true) || keywords.contains("水彩", true) -> "watercolor"
            keywords.contains("ink", true) || keywords.contains("水墨", true) -> "chinese ink"
            else -> "general"
        }
    }
    
    fun release() = scope.cancel()
}
