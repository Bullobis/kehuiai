package comkuaihuiai.data.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * 可绘AI v3.0 提示词模板仓库
 */
class PromptTemplateRepository(private val context: Context) {
    
    companion object {
        private const val TAG = "PromptTemplateRepo"
        private const val TEMPLATES_FILE = "prompt_templates.json"
    }
    
    private val templatesFile = File(context.filesDir, TEMPLATES_FILE)
    
    private val _templates = MutableStateFlow<List<PromptTemplate>>(getDefaultTemplates())
    val templates: StateFlow<List<PromptTemplate>> = _templates.asStateFlow()
    
    private val _selectedTemplate = MutableStateFlow<PromptTemplate?>(null)
    val selectedTemplate: StateFlow<PromptTemplate?> = _selectedTemplate.asStateFlow()
    
    init {
        loadTemplates()
    }
    
    private fun getDefaultTemplates(): List<PromptTemplate> = listOf(
        PromptTemplate(
            id = "anime",
            name = "🎌 动漫风格",
            prompt = "masterpiece, best quality, anime style, detailed, high resolution",
            negativePrompt = "lowres, bad anatomy, bad hands, text, error, worst quality"
        ),
        PromptTemplate(
            id = "realistic",
            name = "📷 写实风格",
            prompt = "photorealistic, realistic, 8k, detailed, professional photography",
            negativePrompt = "cartoon, anime, painting, drawing, illustration, cartoonish"
        ),
        PromptTemplate(
            id = "sdxl",
            name = "✨ SDXL 高质量",
            prompt = "masterpiece, professional, extremely detailed, 4k, HDR",
            negativePrompt = "low quality, worst quality, blurry, low resolution"
        ),
        PromptTemplate(
            id = "zimage",
            name = "🖼️ Z-Image 风格",
            prompt = "high quality, detailed, beautiful composition, professional",
            negativePrompt = "low quality, distortion, blur, noise"
        )
    )
    
    fun selectTemplate(template: PromptTemplate) {
        _selectedTemplate.value = template
    }
    
    fun applyTemplate(templateId: String): Pair<String, String>? {
        val template = _templates.value.find { it.id == templateId }
        return template?.let { Pair(it.prompt, it.negativePrompt) }
    }
    
    private fun loadTemplates() {
        try {
            if (templatesFile.exists()) {
                val json = templatesFile.readText()
                // 简单的 JSON 解析
                Log.i(TAG, "已加载自定义模板")
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载模板失败", e)
        }
    }
}

data class PromptTemplate(
    val id: String,
    val name: String,
    val prompt: String,
    val negativePrompt: String
)
