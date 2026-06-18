package com.kehuiai.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 云端 API 调用服务
 * 支持多种云端 AI API
 */
class CloudAPIService(private val context: Context) {

    companion object {
        private const val TAG = "CloudAPIService"
        
        // API 提供商
        const val PROVIDER_STABLE_DIFFUSION = "stable_diffusion"
        const val PROVIDER_COMFYUI = "comfyui"
        const val PROVIDER_OPENAI = "openai"
        const val PROVIDER_MIDJOURNEY = "midjourney"
    }

    private var currentProvider = PROVIDER_STABLE_DIFFUSION
    private var apiUrl = ""
    private var apiKey = ""
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // ========== 配置 ==========

    fun setProvider(provider: String) {
        currentProvider = provider
    }

    fun setApiUrl(url: String) {
        apiUrl = url.trimEnd('/')
    }

    fun setApiKey(key: String) {
        apiKey = key
    }

    // ========== 图像生成 ==========

    /**
     * 文生图
     */
    fun textToImage(
        prompt: String,
        negativePrompt: String = "",
        width: Int = 512,
        height: Int = 512,
        steps: Int = 20,
        cfgScale: Float = 7.0f,
        seed: Long = -1L,
        sampler: String = "Euler"
    ): Flow<ImageResponse> = flow {
        emit(ImageResponse.Status("正在生成图像..."))

        try {
            when (currentProvider) {
                PROVIDER_STABLE_DIFFUSION -> {
                    emit(generateStableDiffusion(prompt, negativePrompt, width, height, steps, cfgScale, seed, sampler))
                }
                PROVIDER_COMFYUI -> {
                    emit(generateComfyUI(prompt, width, height, steps))
                }
                else -> {
                    emit(ImageResponse.Error("不支持的提供商"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Generation error: ${e.message}")
            emit(ImageResponse.Error(e.message ?: "生成失败"))
        }
    }

    /**
     * 图生图
     */
    fun imageToImage(
        baseImage: String,  // Base64
        prompt: String,
        negativePrompt: String = "",
        strength: Float = 0.75f,
        width: Int = 512,
        height: Int = 512,
        steps: Int = 20,
        cfgScale: Float = 7.0f,
        seed: Long = -1L
    ): Flow<ImageResponse> = flow {
        emit(ImageResponse.Status("正在处理图像..."))

        try {
            val payload = JSONObject().apply {
                put("init_images", org.json.JSONArray().put(baseImage))
                put("prompt", prompt)
                put("negative_prompt", negativePrompt)
                put("denoising_strength", strength)
                put("width", width)
                put("height", height)
                put("steps", steps)
                put("cfg_scale", cfgScale)
                if (seed >= 0) put("seed", seed)
            }

            val result = postJSON("$apiUrl/sdapi/v1/img2img", payload)
            
            val images = result.getJSONArray("images")
            val outputImages = mutableListOf<String>()
            for (i in 0 until images.length()) {
                outputImages.add(images.getString(i))
            }

            emit(ImageResponse.Completed(outputImages))
        } catch (e: Exception) {
            emit(ImageResponse.Error(e.message ?: "处理失败"))
        }
    }

    // ========== 内部方法 ==========

    private suspend fun generateStableDiffusion(
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        cfgScale: Float,
        seed: Long,
        sampler: String
    ): ImageResponse {
        val payload = JSONObject().apply {
            put("prompt", prompt)
            put("negative_prompt", negativePrompt)
            put("width", width)
            put("height", height)
            put("steps", steps)
            put("cfg_scale", cfgScale)
            put("sampler_name", sampler)
            if (seed >= 0) put("seed", seed)
            else put("seed", (Math.random() * Long.MAX_VALUE).toLong())
        }

        val result = postJSON("$apiUrl/sdapi/v1/txt2img", payload)
        
        val images = result.getJSONArray("images")
        val outputImages = mutableListOf<String>()
        for (i in 0 until images.length()) {
            outputImages.add(images.getString(i))
        }

        return ImageResponse.Completed(outputImages)
    }

    private suspend fun generateComfyUI(
        prompt: String,
        width: Int,
        height: Int,
        steps: Int
    ): ImageResponse {
        // ComfyUI 工作流
        val workflow = JSONObject().apply {
            put("prompt", prompt)
            put("width", width)
            put("height", height)
            put("steps", steps)
        }

        // 先获取历史ID
        val promptResponse = postJSON("$apiUrl/prompt", workflow)
        val promptId = promptResponse.getString("prompt_id")

        // 轮询进度
        var completed = false
        while (!completed) {
            kotlinx.coroutines.delay(2000)
            val historyResponse = getJSON("$apiUrl/history/$promptId")
            
            if (historyResponse.has(promptId)) {
                val history = historyResponse.getJSONObject(promptId)
                if (history.has("outputs")) {
                    val outputs = history.getJSONObject("outputs")
                    // 提取图像
                    val images = mutableListOf<String>()
                    outputs.keys().forEach { nodeId ->
                        val nodeOutput = outputs.getJSONObject(nodeId)
                        if (nodeOutput.has("images")) {
                            val imageList = nodeOutput.getJSONArray("images")
                            for (i in 0 until imageList.length()) {
                                val img = imageList.getJSONObject(i)
                                images.add(img.getString("filename"))
                            }
                        }
                    }
                    if (images.isNotEmpty()) {
                        return ImageResponse.Completed(images)
                    }
                }
            }
        }

        return ImageResponse.Error("生成超时")
    }

    private suspend fun getJSON(url: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("空响应")
        JSONObject(body)
    }

    private suspend fun postJSON(url: String, payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .apply {
                if (apiKey.isNotBlank()) {
                    addHeader("Authorization", "Bearer $apiKey")
                }
            }
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("空响应")
        
        try {
            JSONObject(body)
        } catch (e: Exception) {
            throw Exception("响应格式错误: $body")
        }
    }

    // ========== 状态检查 ==========

    suspend fun checkConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = getJSON("$apiUrl/system-stats")
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getSystemInfo(): SystemInfo? = withContext(Dispatchers.IO) {
        try {
            val response = getJSON("$apiUrl/system-stats")
            
            SystemInfo(
                ramUsed = response.optLong("ram_used", 0),
                ramTotal = response.optLong("ram_total", 0),
                cudaUsed = response.optLong("cuda_used", 0),
                cudaTotal = response.optLong("cuda_total", 0),
                model = response.optString("sd_model_checkpoint", "未加载")
            )
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * 图像响应
 */
sealed class ImageResponse {
    data class Status(val message: String) : ImageResponse()
    data class Completed(val images: List<String>) : ImageResponse()
    data class Error(val message: String) : ImageResponse()
}

/**
 * 系统信息
 */
data class SystemInfo(
    val ramUsed: Long,
    val ramTotal: Long,
    val cudaUsed: Long,
    val cudaTotal: Long,
    val model: String
)
