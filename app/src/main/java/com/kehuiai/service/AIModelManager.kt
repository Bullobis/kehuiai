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
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * AI 模型管理器
 * 统一管理各种 AI 模型和 API
 */
class AIModelManager(private val context: Context) {

    companion object {
        private const val TAG = "AIModelManager"
        
        // 模型提供商
        const val PROVIDER_QWEN = "qwen"
        const val PROVIDER_DEEPSEEK = "deepseek"
        const val PROVIDER_OPENAI = "openai"
        const val PROVIDER_ANTHROPIC = "anthropic"
        const val PROVIDER_GEMINI = "gemini"
        const val PROVIDER_ZHIPU = "zhipu"
        const val PROVIDER_MINIMAX = "minimax"
        const val PROVIDER_BAICHUAN = "baichuan"
        const val PROVIDER_LAMBDA = "lambda"
        const val PROVIDER_TONGYI = "tongyi"
        const val PROVIDER_CUSTOM = "custom"
    }
    
    // 单例
    @Volatile
    private var instance: AIModelManager? = null
    
    fun getInstance(): AIModelManager {
        return instance ?: synchronized(this) {
            instance ?: AIModelManager(context).also { instance = it }
        }
    }
    
    // HTTP 客户端
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    // 当前配置
    private var currentProvider = PROVIDER_QWEN
    private var apiKey = ""
    private var baseUrl = ""
    private var modelName = ""
    
    // 对话历史
    private val conversationHistory = mutableListOf<ChatMessage>()
    
    // ========== 配置方法 ==========
    
    fun configure(provider: String, apiKey: String, baseUrl: String = "", model: String = "") {
        this.currentProvider = provider
        this.apiKey = apiKey
        this.baseUrl = baseUrl
        this.modelName = model
    }
    
    fun setApiKey(key: String) {
        apiKey = key
    }
    
    fun clearHistory() {
        conversationHistory.clear()
    }
    
    // ========== 模型信息 ==========
    
    data class ModelInfo(
        val id: String,
        val name: String,
        val provider: String,
        val description: String,
        val maxTokens: Int,
        val supportsVision: Boolean = false,
        val supportsFunction: Boolean = false
    )
    
    data class ChatMessage(
        val role: String,
        val content: String,
        val imageUrls: List<String> = emptyList()
    )
    
    data class ChatResponse(
        val content: String,
        val usage: UsageInfo? = null,
        val model: String,
        val finishReason: String = "stop"
    ) {
        data class UsageInfo(
            val promptTokens: Int,
            val completionTokens: Int,
            val totalTokens: Int
        )
    }
    
    // 获取可用模型列表
    fun getAvailableModels(): List<ModelInfo> {
        return listOf(
            // 通义千问
            ModelInfo("qwen2.5-72b-instruct", "Qwen2.5-72B", PROVIDER_QWEN, "最强大的通义千问模型", 32000),
            ModelInfo("qwen2.5-32b-instruct", "Qwen2.5-32B", PROVIDER_QWEN, "高性能通义千问模型", 32000),
            ModelInfo("qwen2.5-14b-instruct", "Qwen2.5-14B", PROVIDER_QWEN, "均衡通义千问模型", 32000),
            ModelInfo("qwen2.5-7b-instruct", "Qwen2.5-7B", PROVIDER_QWEN, "轻量通义千问模型", 32000),
            ModelInfo("qwen2.5-1.5b-instruct", "Qwen2.5-1.5B", PROVIDER_QWEN, "超轻量通义千问模型", 8000),
            ModelInfo("qwen2.5-coder-32b-instruct", "Qwen2.5-Coder-32B", PROVIDER_QWEN, "代码专用模型", 32000),
            ModelInfo("qwen-vl-plus", "Qwen-VL-Plus", PROVIDER_QWEN, "视觉理解模型", 8000, supportsVision = true),
            ModelInfo("qwen-vl-max", "Qwen-VL-Max", PROVIDER_QWEN, "高级视觉理解模型", 8000, supportsVision = true),
            
            // DeepSeek
            ModelInfo("deepseek-chat", "DeepSeek V3", PROVIDER_DEEPSEEK, "深度求索最新模型", 64000),
            ModelInfo("deepseek-coder", "DeepSeek Coder", PROVIDER_DEEPSEEK, "代码专用模型", 16000),
            ModelInfo("deepseek-reasoner", "DeepSeek R1", PROVIDER_DEEPSEEK, "推理模型", 64000),
            
            // OpenAI
            ModelInfo("gpt-4o", "GPT-4o", PROVIDER_OPENAI, "最新 GPT-4 模型", 128000, supportsVision = true),
            ModelInfo("gpt-4o-mini", "GPT-4o Mini", PROVIDER_OPENAI, "轻量 GPT-4 模型", 128000, supportsVision = true),
            ModelInfo("gpt-4-turbo", "GPT-4 Turbo", PROVIDER_OPENAI, "高性能 GPT-4", 128000, supportsVision = true),
            ModelInfo("gpt-3.5-turbo", "GPT-3.5 Turbo", PROVIDER_OPENAI, "经济实惠模型", 16000),
            
            // Anthropic
            ModelInfo("claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet", PROVIDER_ANTHROPIC, "最新 Claude 模型", 200000, supportsVision = true),
            ModelInfo("claude-3-5-haiku-20241022", "Claude 3.5 Haiku", PROVIDER_ANTHROPIC, "快速 Claude 模型", 200000, supportsVision = true),
            ModelInfo("claude-3-opus-20240229", "Claude 3 Opus", PROVIDER_ANTHROPIC, "最强大 Claude 模型", 200000, supportsVision = true),
            
            // Gemini
            ModelInfo("gemini-2.0-flash-exp", "Gemini 2.0 Flash", PROVIDER_GEMINI, "最新 Gemini 模型", 1000000, supportsVision = true),
            ModelInfo("gemini-1.5-pro", "Gemini 1.5 Pro", PROVIDER_GEMINI, "高级 Gemini 模型", 1000000, supportsVision = true),
            ModelInfo("gemini-1.5-flash", "Gemini 1.5 Flash", PROVIDER_GEMINI, "快速 Gemini 模型", 1000000, supportsVision = true),
            
            // 智谱 AI
            ModelInfo("glm-4-plus", "GLM-4 Plus", PROVIDER_ZHIPU, "智谱最新模型", 128000, supportsVision = true),
            ModelInfo("glm-4v-plus", "GLM-4V Plus", PROVIDER_ZHIPU, "视觉模型", 4000, supportsVision = true),
            ModelInfo("glm-z1-flash", "GLM-Z1 Flash", PROVIDER_ZHIPU, "推理模型", 4000),
            
            // 阶跃星辰
            ModelInfo("step-1v-32k", "Step-1V", PROVIDER_MINIMAX, "视觉模型", 32000, supportsVision = true),
            ModelInfo("step-1-32k", "Step-1", PROVIDER_MINIMAX, "对话模型", 32000),
            
            // 百川
            ModelInfo("baichuan4", "百川 4", PROVIDER_BAICHUAN, "百川最新模型", 32000),
            ModelInfo("baichuan3-turbo", "百川 3 Turbo", PROVIDER_BAICHUAN, "高性能模型", 32000)
        )
    }
    
    // ========== 对话方法 ==========
    
    /**
     * 发送消息
     */
    fun sendMessage(
        message: String,
        images: List<String> = emptyList(),
        systemPrompt: String = ""
    ): Flow<Result<ChatResponse>> = flow {
        try {
            emit(Result.success(ChatResponse("正在思考...", model = modelName)))
            
            // 构建请求
            val requestBody = buildRequestBody(message, images, systemPrompt)
            
            val request = Request.Builder()
                .url(getEndpoint())
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        emit(Result.failure(Exception("请求失败: ${response.code}")))
                        return@withContext
                    }
                    
                    val body = response.body?.string() ?: ""
                    val chatResponse = parseResponse(body)
                    emit(Result.success(chatResponse))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "发送消息失败", e)
            emit(Result.failure(e))
        }
    }
    
    /**
     * 流式发送消息
     */
    fun sendMessageStream(
        message: String,
        images: List<String> = emptyList(),
        systemPrompt: String = "",
        onChunk: (String) -> Unit
    ): Flow<Result<ChatResponse>> = flow {
        try {
            val fullResponse = StringBuilder()
            
            // 构建请求
            val requestBody = buildRequestBody(message, images, systemPrompt, stream = true)
            
            val request = Request.Builder()
                .url(getEndpoint())
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        emit(Result.failure(Exception("请求失败: ${response.code}")))
                        return@withContext
                    }
                    
                    response.body?.byteStream()?.bufferedReader()?.use { reader ->
                        var line: String?
                        while ((reader.readLine().also { line = it }) != null) {
                            if (line!!.startsWith("data:")) {
                                val data = line!!.substringAfter("data:").trim()
                                if (data.isNotEmpty() && data != "[DONE]") {
                                    val chunk = parseStreamChunk(data)
                                    if (chunk.isNotEmpty()) {
                                        fullResponse.append(chunk)
                                        onChunk(chunk)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            emit(Result.success(ChatResponse(fullResponse.toString(), model = modelName)))
        } catch (e: Exception) {
            Log.e(TAG, "流式发送消息失败", e)
            emit(Result.failure(e))
        }
    }
    
    // ========== 辅助方法 ==========
    
    private fun getEndpoint(): String {
        return when (currentProvider) {
            PROVIDER_QWEN -> "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
            PROVIDER_DEEPSEEK -> "https://api.deepseek.com/v1/chat/completions"
            PROVIDER_OPENAI -> "https://api.openai.com/v1/chat/completions"
            PROVIDER_ANTHROPIC -> "https://api.anthropic.com/v1/messages"
            PROVIDER_GEMINI -> "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"
            PROVIDER_ZHIPU -> "https://open.bigmodel.cn/api/paas/v4/chat/completions"
            PROVIDER_MINIMAX -> "https://api.minimax.chat/v1/text/chatcompletion_v2"
            PROVIDER_BAICHUAN -> "https://api.baichuan-ai.com/v1/chat/completions"
            PROVIDER_LAMBDA -> "https://api.lambdai.com/v1/chat/completions"
            PROVIDER_TONGYI -> "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
            PROVIDER_CUSTOM -> baseUrl
            else -> baseUrl
        }
    }
    
    private fun buildRequestBody(
        message: String,
        images: List<String>,
        systemPrompt: String,
        stream: Boolean = false
    ): String {
        // 添加到历史
        conversationHistory.add(ChatMessage("user", message, images))
        
        return when (currentProvider) {
            PROVIDER_QWEN, PROVIDER_DEEPSEEK, PROVIDER_OPENAI, PROVIDER_LAMBDA, PROVIDER_TONGYI -> {
                buildOpenAICompatibleBody(message, images, systemPrompt, stream)
            }
            PROVIDER_ANTHROPIC -> {
                buildAnthropicBody(message, images, systemPrompt)
            }
            PROVIDER_GEMINI -> {
                buildGeminiBody(message, images)
            }
            PROVIDER_ZHIPU -> {
                buildZhipuBody(message, images, systemPrompt, stream)
            }
            else -> buildOpenAICompatibleBody(message, images, systemPrompt, stream)
        }
    }
    
    private fun buildOpenAICompatibleBody(
        message: String,
        images: List<String>,
        systemPrompt: String,
        stream: Boolean
    ): String {
        val messages = mutableListOf<JSONObject>()
        
        // 系统提示词
        if (systemPrompt.isNotEmpty()) {
            messages.add(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
        }
        
        // 用户消息
        val userContent = if (images.isEmpty()) {
            message
        } else {
            val contentArray = JSONArray()
            contentArray.put(JSONObject().apply {
                put("type", "text")
                put("text", message)
            })
            for (imageUrl in images) {
                contentArray.put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", imageUrl)
                    })
                })
            }
            contentArray.toString()
        }
        
        messages.add(JSONObject().apply {
            put("role", "user")
            put("content", userContent)
        })
        
        return JSONObject().apply {
            put("model", modelName)
            put("messages", messages)
            put("stream", stream)
        }.toString()
    }
    
    private fun buildAnthropicBody(
        message: String,
        images: List<String>,
        systemPrompt: String
    ): String {
        val messages = JSONArray()
        messages.put(JSONObject().apply {
            put("role", "user")
            if (images.isEmpty()) {
                put("content", message)
            } else {
                val contentArray = JSONArray()
                contentArray.put(JSONObject().apply {
                    put("type", "text")
                    put("text", message)
                })
                for (imageUrl in images) {
                    contentArray.put(JSONObject().apply {
                        put("type", "image")
                        put("source", JSONObject().apply {
                            put("type", "url")
                            put("media_type", "image/jpeg")
                            put("data", imageUrl)
                        })
                    })
                }
                put("content", contentArray)
            }
        })
        
        return JSONObject().apply {
            put("model", modelName)
            put("messages", messages)
            put("max_tokens", 4096)
            if (systemPrompt.isNotEmpty()) {
                put("system", systemPrompt)
            }
        }.toString()
    }
    
    @Suppress("UNUSED_PARAMETER")
    private fun buildGeminiBody(message: String, images: List<String>): String {
        val contents = JSONArray()
        val parts = JSONArray()
        parts.put(JSONObject().apply {
            put("text", message)
        })
        
        contents.put(JSONObject().apply {
            put("parts", parts)
        })
        
        return JSONObject().apply {
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 8192)
                put("temperature", 0.9)
                put("topP", 1.0)
            })
        }.toString()
    }
    
    private fun buildZhipuBody(
        message: String,
        images: List<String>,
        systemPrompt: String,
        stream: Boolean
    ): String {
        val messages = mutableListOf<JSONObject>()
        
        if (systemPrompt.isNotEmpty()) {
            messages.add(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
        }
        
        val userContent = if (images.isEmpty()) {
            message
        } else {
            val contentArray = JSONArray()
            contentArray.put(JSONObject().apply {
                put("type", "text")
                put("text", message)
            })
            for (imageUrl in images) {
                contentArray.put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", imageUrl)
                    })
                })
            }
            contentArray.toString()
        }
        
        messages.add(JSONObject().apply {
            put("role", "user")
            put("content", userContent)
        })
        
        return JSONObject().apply {
            put("model", modelName)
            put("messages", messages)
            put("stream", stream)
        }.toString()
    }
    
    private fun parseResponse(body: String): ChatResponse {
        return try {
            val json = JSONObject(body)
            
            when (currentProvider) {
                PROVIDER_ANTHROPIC -> {
                    val content = json.getJSONArray("content").getJSONObject(0).getString("text")
                    val usage = json.optJSONObject("usage")
                    ChatResponse(
                        content = content,
                        usage = usage?.let {
                            ChatResponse.UsageInfo(
                                promptTokens = it.optInt("input_tokens"),
                                completionTokens = it.optInt("output_tokens"),
                                totalTokens = it.optInt("total_tokens")
                            )
                        },
                        model = modelName,
                        finishReason = "stop"
                    )
                }
                else -> {
                    val choices = json.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val message = choices.getJSONObject(0).getJSONObject("message")
                        val content = message.optString("content", "")
                        val usage = json.optJSONObject("usage")
                        val finishReason = choices.getJSONObject(0).optString("finish_reason", "stop")
                        
                        conversationHistory.add(ChatMessage("assistant", content))
                        
                        ChatResponse(
                            content = content,
                            usage = usage?.let {
                                ChatResponse.UsageInfo(
                                    promptTokens = it.optInt("prompt_tokens"),
                                    completionTokens = it.optInt("completion_tokens"),
                                    totalTokens = it.optInt("total_tokens")
                                )
                            },
                            model = modelName,
                            finishReason = finishReason
                        )
                    } else {
                        ChatResponse(content = "响应为空", model = modelName)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析响应失败: $body", e)
            ChatResponse(content = "解析响应失败: ${e.message}", model = modelName)
        }
    }
    
    private fun parseStreamChunk(data: String): String {
        return try {
            val json = JSONObject(data)
            
            when (currentProvider) {
                PROVIDER_ANTHROPIC -> {
                    json.optJSONArray("content_block_delta")
                        ?.optJSONObject(0)
                        ?.optString("text", "") ?: ""
                }
                else -> {
                    json.optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("delta")
                        ?.optString("content", "") ?: ""
                }
            }
        } catch (e: Exception) {
            ""
        }
    }
}
