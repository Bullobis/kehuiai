@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST", "DEPRECATION", "USELESS_ELVIS")
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
 * AI 对话服务
 * 支持多种 API 提供商
 */
class AIChatService(private val context: Context) {

    companion object {
        private const val TAG = "AIChatService"
        
        // API 提供商
        const val PROVIDER_OPENAI = "openai"
        const val PROVIDER_ANTHROPIC = "anthropic"
        const val PROVIDER_GEMINI = "gemini"
        const val PROVIDER_QWEN = "qwen"
        const val PROVIDER_DEEPSEEK = "deepseek"
        const val PROVIDER_CUSTOM = "custom"
    }
    
    // 当前配置
    private var currentProvider = PROVIDER_QWEN
    private var apiKey = ""
    private var baseUrl = ""
    private var modelName = "qwen2.5-7b-instruct"
    
    // HTTP 客户端
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    // 对话历史
    private val conversationHistory = mutableListOf<ChatMessage>()
    
    // ========== 配置方法 ==========
    
    fun setProvider(provider: String) {
        currentProvider = provider
        updateEndpoint()
    }
    
    fun setApiKey(key: String) {
        apiKey = key
    }
    
    fun setBaseUrl(url: String) {
        baseUrl = url
    }
    
    fun setModel(model: String) {
        modelName = model
    }
    
    private fun updateEndpoint() {
        baseUrl = when (currentProvider) {
            PROVIDER_OPENAI -> "https://api.openai.com/v1"
            PROVIDER_ANTHROPIC -> "https://api.anthropic.com/v1"
            PROVIDER_GEMINI -> "https://generativelanguage.googleapis.com/v1beta"
            PROVIDER_QWEN -> "https://dashscope.aliyuncs.com/compatible-mode/v1"
            PROVIDER_DEEPSEEK -> "https://api.deepseek.com/v1"
            PROVIDER_CUSTOM -> baseUrl
            else -> baseUrl
        }
        
        modelName = when (currentProvider) {
            PROVIDER_OPENAI -> "gpt-4o-mini"
            PROVIDER_ANTHROPIC -> "claude-3-haiku-20240307"
            PROVIDER_GEMINI -> "gemini-1.5-flash"
            PROVIDER_QWEN -> "qwen2.5-7b-instruct"
            PROVIDER_DEEPSEEK -> "deepseek-chat"
            PROVIDER_CUSTOM -> modelName
            else -> modelName
        }
    }
    
    // ========== 对话方法 ==========
    
    /**
     * 发送消息并获取回复
     */
    fun sendMessage(message: String): Flow<ChatResponse> = flow {
        try {
            emit(ChatResponse.Status("正在思考..."))
            
            // 添加用户消息到历史
            conversationHistory.add(ChatMessage(role = "user", content = message))
            
            val response = when (currentProvider) {
                PROVIDER_OPENAI -> sendOpenAIRequest(message)
                PROVIDER_ANTHROPIC -> sendAnthropicRequest(message)
                PROVIDER_GEMINI -> sendGeminiRequest(message)
                PROVIDER_QWEN -> sendQwenRequest(message)
                PROVIDER_DEEPSEEK -> sendDeepSeekRequest(message)
                PROVIDER_CUSTOM -> sendCustomRequest(message)
                else -> throw Exception("未知提供商: $currentProvider")
            }
            
            // 添加助手消息到历史
            conversationHistory.add(ChatMessage(role = "assistant", content = response))
            
            emit(ChatResponse.Completed(response))
            
        } catch (e: Exception) {
            Log.e(TAG, "Chat error: ${e.message}")
            emit(ChatResponse.Error(e.message ?: "请求失败"))
        }
    }
    
    /**
     * 清空对话历史
     */
    fun clearHistory() {
        conversationHistory.clear()
    }
    
    /**
     * 获取对话历史
     */
    fun getHistory(): List<ChatMessage> = conversationHistory.toList()
    
    // ========== API 实现 ==========
    
    private suspend fun sendOpenAIRequest(message: String): String = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("model", modelName)
            put("messages", JSONArray().apply {
                conversationHistory.forEach { msg ->
                    put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
            })
            put("temperature", 0.7)
            put("max_tokens", 2048)
        }
        
        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()
        
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("空响应")
        
        val jsonResponse = JSONObject(body)
        if (jsonResponse.has("error")) {
            throw Exception(jsonResponse.getJSONObject("error").getString("message"))
        }
        
        jsonResponse.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }
    
    private suspend fun sendAnthropicRequest(message: String): String = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("model", modelName)
            put("messages", JSONArray().apply {
                conversationHistory.forEach { msg ->
                    put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
            })
            put("max_tokens", 2048)
        }
        
        val request = Request.Builder()
            .url("$baseUrl/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()
        
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("空响应")
        
        val jsonResponse = JSONObject(body)
        if (jsonResponse.has("error")) {
            throw Exception(jsonResponse.getJSONObject("error").getString("message"))
        }
        
        jsonResponse.getJSONObject("content").getString("text")
    }
    
    private suspend fun sendGeminiRequest(message: String): String = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", message)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 2048)
            })
        }
        
        val request = Request.Builder()
            .url("$baseUrl/models/$modelName:generateContent?key=$apiKey")
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()
        
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("空响应")
        
        val jsonResponse = JSONObject(body)
        if (jsonResponse.has("error")) {
            throw Exception(jsonResponse.getJSONObject("error").getString("message"))
        }
        
        jsonResponse.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
    }
    
    private suspend fun sendQwenRequest(message: String): String = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("model", modelName)
            put("input", JSONObject().apply {
                put("messages", JSONArray().apply {
                    conversationHistory.forEach { msg ->
                        put(JSONObject().apply {
                            put("role", msg.role)
                            put("content", msg.content)
                        })
                    }
                })
            })
            put("parameters", JSONObject().apply {
                put("temperature", 0.7)
                put("max_tokens", 2048)
                put("result_format", "message")
            })
        }
        
        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()
        
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("空响应")
        
        val jsonResponse = JSONObject(body)
        if (jsonResponse.has("error")) {
            throw Exception(jsonResponse.getJSONObject("error").getString("message"))
        }
        
        jsonResponse.getJSONObject("output").getJSONObject("choices")
            .getJSONArray("text")
            .getJSONObject(0)
            .getString("content")
    }
    
    private suspend fun sendDeepSeekRequest(message: String): String = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("model", modelName)
            put("messages", JSONArray().apply {
                conversationHistory.forEach { msg ->
                    put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
            })
            put("temperature", 0.7)
            put("max_tokens", 2048)
        }
        
        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()
        
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("空响应")
        
        val jsonResponse = JSONObject(body)
        if (jsonResponse.has("error")) {
            throw Exception(jsonResponse.getJSONObject("error").getString("message"))
        }
        
        jsonResponse.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }
    
    private suspend fun sendCustomRequest(message: String): String = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("model", modelName)
            put("messages", JSONArray().apply {
                conversationHistory.forEach { msg ->
                    put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
            })
            put("temperature", 0.7)
            put("max_tokens", 2048)
        }
        
        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()
        
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("空响应")
        
        val jsonResponse = JSONObject(body)
        if (jsonResponse.has("error")) {
            throw Exception(jsonResponse.getJSONObject("error").getString("message"))
        }
        
        jsonResponse.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }
    
    // ========== 模型信息 ==========
    
    fun getAvailableModels(): List<ModelInfo> {
        return when (currentProvider) {
            PROVIDER_OPENAI -> listOf(
                ModelInfo("gpt-4o", "最新最强模型"),
                ModelInfo("gpt-4o-mini", "快速轻量模型"),
                ModelInfo("gpt-4-turbo", "高性能模型"),
                ModelInfo("gpt-3.5-turbo", "经济实惠")
            )
            PROVIDER_ANTHROPIC -> listOf(
                ModelInfo("claude-3-5-sonnet-20241022", "最新旗舰"),
                ModelInfo("claude-3-opus-20240229", "最强能力"),
                ModelInfo("claude-3-sonnet-20240229", "平衡之选"),
                ModelInfo("claude-3-haiku-20240307", "快速响应")
            )
            PROVIDER_GEMINI -> listOf(
                ModelInfo("gemini-1.5-pro", "最强大模型"),
                ModelInfo("gemini-1.5-flash", "快速响应"),
                ModelInfo("gemini-1.5-flash-8b", "极速轻量")
            )
            PROVIDER_QWEN -> listOf(
                ModelInfo("qwen2.5-72b-instruct", "最强中文"),
                ModelInfo("qwen2.5-7b-instruct", "轻量快速"),
                ModelInfo("qwen2.5-1.5b-instruct", "超轻量"),
                ModelInfo("qwen-plus", "Plus 版本")
            )
            PROVIDER_DEEPSEEK -> listOf(
                ModelInfo("deepseek-chat", "通用对话"),
                ModelInfo("deepseek-coder", "代码专家")
            )
            PROVIDER_CUSTOM -> listOf(
                ModelInfo(modelName, "自定义模型")
            )
            else -> emptyList()
        }
    }
}

/**
 * 聊天消息
 */
data class ChatMessage(
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * AI 响应
 */
sealed class ChatResponse {
    data class Status(val message: String) : ChatResponse()
    data class Completed(val content: String, val usage: TokenUsage? = null) : ChatResponse()
    data class Error(val message: String) : ChatResponse()
}

/**
 * Token 使用统计
 */
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

/**
 * 模型信息
 */
data class ModelInfo(
    val name: String,
    val description: String
)
