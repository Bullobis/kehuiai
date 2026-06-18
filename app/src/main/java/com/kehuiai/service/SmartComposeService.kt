package com.kehuiai.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * 智能组合服务
 * AI 驱动的图像生成和增强
 */
class SmartComposeService(private val context: Context) {

    companion object {
        private const val TAG = "SmartCompose"
    }
    
    // 单例
    @Volatile
    private var instance: SmartComposeService? = null
    
    fun getInstance(): SmartComposeService {
        return instance ?: synchronized(this) {
            instance ?: SmartComposeService(context).also { instance = it }
        }
    }
    
    // API 配置
    private var apiUrl = ""
    private var apiKey = ""
    private var selectedBackend = Backend.STABLE_DIFFUSION
    
    enum class Backend {
        STABLE_DIFFUSION,
        COMFYUI,
        FLUX,
        DALLE,
        MIDJOURNEY
    }
    
    // HTTP 客户端
    private val client = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    // ========== 配置 ==========
    
    fun configure(url: String, key: String, backend: Backend = Backend.STABLE_DIFFUSION) {
        apiUrl = url.trimEnd('/')
        apiKey = key
        selectedBackend = backend
    }
    
    // ========== 生成 ==========
    
    data class GenerationParams(
        val prompt: String,
        val negativePrompt: String = "",
        val width: Int = 512,
        val height: Int = 512,
        val steps: Int = 25,
        val cfgScale: Float = 7.0f,
        val seed: Long = -1L,
        val sampler: String = "Euler",
        val model: String = "sd15",
        val style: String = "none"
    )
    
    data class GenerationResult(
        val success: Boolean,
        val imagePath: String? = null,
        val error: String? = null,
        val metadata: Map<String, Any> = emptyMap()
    )
    
    /**
     * 文生图
     */
    suspend fun textToImage(params: GenerationParams): GenerationResult = withContext(Dispatchers.IO) {
        try {
            when (selectedBackend) {
                Backend.STABLE_DIFFUSION -> generateWithStableDiffusion(params)
                Backend.COMFYUI -> generateWithComfyUI(params)
                Backend.FLUX -> generateWithFlux(params)
                Backend.DALLE -> generateWithDALLE(params)
                Backend.MIDJOURNEY -> generateWithMidjourney(params)
            }
        } catch (e: Exception) {
            Log.e(TAG, "生成失败", e)
            GenerationResult(false, error = e.message)
        }
    }
    
    /**
     * 流式生成
     */
    fun textToImageStream(params: GenerationParams): Flow<GenerationResult> = flow {
        emit(GenerationResult(true, metadata = mapOf("status" to "正在生成...")))
        
        try {
            val result = textToImage(params)
            emit(result)
        } catch (e: Exception) {
            emit(GenerationResult(false, error = e.message))
        }
    }
    
    /**
     * 图生图
     */
    suspend fun imageToImage(
        sourceImage: Bitmap,
        params: GenerationParams,
        strength: Float = 0.7f
    ): GenerationResult = withContext(Dispatchers.IO) {
        try {
            // 保存源图
            val sourceFile = File(context.cacheDir, "source_${System.currentTimeMillis()}.png")
            FileOutputStream(sourceFile).use { out ->
                sourceImage.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            // 调用 API
            when (selectedBackend) {
                Backend.STABLE_DIFFUSION -> {
                    val requestBody = buildImageToImageRequest(sourceFile, params, strength)
                    val result = callAPI(requestBody)
                    sourceFile.delete()
                    return@withContext result
                }
                else -> {
                    sourceFile.delete()
                    GenerationResult(false, error = "该后端不支持图生图")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "图生图失败", e)
            GenerationResult(false, error = e.message)
        }
    }
    
    // ========== 后端实现 ==========
    
    private suspend fun generateWithStableDiffusion(params: GenerationParams): GenerationResult {
        val requestBody = JSONObject().apply {
            put("prompt", params.prompt)
            put("negative_prompt", params.negativePrompt)
            put("width", params.width)
            put("height", params.height)
            put("steps", params.steps)
            put("cfg_scale", params.cfgScale)
            put("sampler_name", params.sampler)
            if (params.seed >= 0) {
                put("seed", params.seed)
            }
            put("n_iter", 1)
            put("save_images", false)
        }.toString()
        
        return callAPI(requestBody)
    }
    
    private suspend fun generateWithComfyUI(params: GenerationParams): GenerationResult {
        // ComfyUI 工作流
        val workflow = JSONObject().apply {
            put("prompt", params.prompt)
            put("negative_prompt", params.negativePrompt)
            put("width", params.width)
            put("height", params.height)
            put("steps", params.steps)
            put("cfg", params.cfgScale)
        }
        
        return callAPI(workflow.toString())
    }
    
    private suspend fun generateWithFlux(params: GenerationParams): GenerationResult {
        val requestBody = JSONObject().apply {
            put("prompt", params.prompt)
            put("negative_prompt", params.negativePrompt)
            put("width", params.width)
            put("height", params.height)
            put("num_steps", params.steps)
            put("guidance", params.cfgScale)
            if (params.seed >= 0) {
                put("seed", params.seed)
            }
        }.toString()
        
        return callAPI(requestBody)
    }
    
    private suspend fun generateWithDALLE(params: GenerationParams): GenerationResult {
        val requestBody = JSONObject().apply {
            put("prompt", params.prompt)
            put("n", 1)
            put("size", "${params.width}x${params.height}")
            put("model", "dall-e-3")
        }.toString()
        
        return callAPI(requestBody)
    }
    
    private suspend fun generateWithMidjourney(params: GenerationParams): GenerationResult {
        val requestBody = JSONObject().apply {
            put("prompt", params.prompt)
            put("negative_prompt", params.negativePrompt)
            put("aspect_ratio", "${params.width}:${params.height}")
            put("quality", "standard")
        }.toString()
        
        return callAPI(requestBody)
    }
    
    private suspend fun callAPI(requestBody: String): GenerationResult {
        if (apiUrl.isEmpty()) {
            return GenerationResult(false, error = "请先配置 API 地址")
        }
        
        val request = Request.Builder()
            .url("$apiUrl/generate")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return GenerationResult(false, error = "请求失败: ${response.code}")
            }
            
            val body = response.body?.string() ?: ""
            
            // 解析响应
            try {
                val json = JSONObject(body)
                if (json.has("image")) {
                    // Base64 图像
                    val imageData = json.getString("image")
                    val imagePath = saveBase64Image(imageData)
                    return GenerationResult(true, imagePath = imagePath)
                } else if (json.has("images")) {
                    // 图像数组
                    val images = json.getJSONArray("images")
                    if (images.length() > 0) {
                        val imageData = images.getString(0)
                        val imagePath = saveBase64Image(imageData)
                        return GenerationResult(true, imagePath = imagePath)
                    }
                } else if (json.has("data")) {
                    // OpenAI 格式
                    val data = json.getJSONArray("data")
                    if (data.length() > 0) {
                        val url = data.getJSONObject(0).getString("url")
                        return downloadImage(url)
                    }
                }
                
                return GenerationResult(false, error = "未找到图像数据")
            } catch (e: Exception) {
                Log.e(TAG, "解析响应失败: $body", e)
                return GenerationResult(false, error = "解析失败: ${e.message}")
            }
        }
    }
    
    private fun buildImageToImageRequest(sourceFile: File, params: GenerationParams, strength: Float): String {
        return JSONObject().apply {
            put("prompt", params.prompt)
            put("negative_prompt", params.negativePrompt)
            put("init_image", android.util.Base64.encodeToString(
                sourceFile.readBytes(),
                android.util.Base64.NO_WRAP
            ))
            put("strength", strength)
            put("width", params.width)
            put("height", params.height)
            put("steps", params.steps)
            put("cfg_scale", params.cfgScale)
            put("sampler_name", params.sampler)
        }.toString()
    }
    
    private suspend fun downloadImage(url: String): GenerationResult {
        val request = Request.Builder()
            .url(url)
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return GenerationResult(false, error = "下载失败")
            }
            
            val bytes = response.body?.bytes() ?: return GenerationResult(false, error = "空响应")
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return GenerationResult(false, error = "解码失败")
            
            val file = File(context.cacheDir, "generated_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            return GenerationResult(true, imagePath = file.absolutePath)
        }
    }
    
    private fun saveBase64Image(base64: String): String {
        val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
        val file = File(context.cacheDir, "generated_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            out.write(bytes)
        }
        return file.absolutePath
    }
    
    // ========== 批量生成 ==========
    
    data class BatchGenerationConfig(
        val prompts: List<String>,
        val negativePrompt: String = "",
        val count: Int = 1,
        val params: GenerationParams.() -> Unit = {}
    )
    
    suspend fun batchGenerate(config: BatchGenerationConfig): List<GenerationResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<GenerationResult>()
        
        for (prompt in config.prompts) {
            val defaultParams = GenerationParams(prompt = prompt, negativePrompt = config.negativePrompt)
            config.params(defaultParams)
            
            repeat(config.count) {
                val result = textToImage(defaultParams)
                results.add(result)
            }
        }
        
        results
    }
    
    // ========== 提示词优化 ==========
    
    data class PromptAnalysis(
        val keywords: List<String>,
        val style: String?,
        val quality: String?,
        val suggestions: List<String>
    )
    
    suspend fun analyzePrompt(prompt: String): PromptAnalysis = withContext(Dispatchers.Default) {
        val keywords = prompt.split(" ")
            .filter { it.length > 3 }
            .filterNot { it.startsWith(",") || it.startsWith(".") }
        
        val styleKeywords = listOf("realistic", "anime", "painting", "digital", "photo")
        val detectedStyle = styleKeywords.find { prompt.contains(it, ignoreCase = true) }
        
        val qualityKeywords = listOf("masterpiece", "detailed", "high quality", "4k", "8k")
        val detectedQuality = qualityKeywords.find { prompt.contains(it, ignoreCase = true) }
        
        val suggestions = mutableListOf<String>()
        
        if (detectedStyle == null) {
            suggestions.add("建议添加风格描述，如: realistic, anime style, digital art")
        }
        
        if (detectedQuality == null) {
            suggestions.add("建议添加质量描述，如: masterpiece, detailed, 8K")
        }
        
        if (!prompt.contains("lighting", ignoreCase = true)) {
            suggestions.add("建议添加光线描述，如: natural lighting, dramatic lighting")
        }
        
        PromptAnalysis(
            keywords = keywords,
            style = detectedStyle,
            quality = detectedQuality,
            suggestions = suggestions
        )
    }
    
    suspend fun enhancePrompt(prompt: String, style: String = "photorealistic"): String = withContext(Dispatchers.Default) {
        val enhanced = StringBuilder(prompt)
        
        // 添加质量标签
        if (!prompt.contains("masterpiece", ignoreCase = true)) {
            enhanced.append(", masterpiece")
        }
        if (!prompt.contains("detailed", ignoreCase = true)) {
            enhanced.append(", highly detailed")
        }
        if (!prompt.contains("quality", ignoreCase = true)) {
            enhanced.append(", 8K")
        }
        
        // 添加风格
        if (!prompt.contains(style, ignoreCase = true)) {
            enhanced.append(", $style")
        }
        
        // 添加通用增强
        enhanced.append(", professional photography")
        
        enhanced.toString()
    }
}
