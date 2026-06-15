package com.kehuiai.service.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

/**
 * API 服务管理器
 * 支持本地 API 和远程 API 调用
 */
class APIServiceManager(private val context: Context) {

    companion object {
        private const val TAG = "APIServiceManager"
        
        // API 状态
        const val STATE_IDLE = 0
        const val STATE_STARTING = 1
        const val STATE_READY = 2
        const val STATE_ERROR = 3
        const val STATE_STOPPED = 4
    }

    private var localServerPort = 7860
    private var baseUrl = "http://127.0.0.1:$localServerPort"
    private var apiState = STATE_IDLE
    private var currentTask: String? = null

    // ==================== 远程 API ====================

    /**
     * 文本生成图像 (txt2img)
     */
    fun txt2img(
        prompt: String,
        negativePrompt: String = "",
        width: Int = 512,
        height: Int = 512,
        steps: Int = 20,
        sampler: String = "Euler a",
        cfgScale: Float = 7f,
        seed: Long = -1,
        batchSize: Int = 1,
        restoreFaces: Boolean = false,
        tiling: Boolean = false,
        n_iter: Int = 1
    ): Flow<APIProgress> = flow {
        emit(APIProgress.Status("正在生成图像..."))

        try {
            val payload = JSONObject().apply {
                put("prompt", prompt)
                put("negative_prompt", negativePrompt)
                put("width", width)
                put("height", height)
                put("steps", steps)
                put("sampler_name", sampler)
                put("cfg_scale", cfgScale)
                put("seed", if (seed == -1L) (Math.random() * Long.MAX_VALUE).toLong() else seed)
                put("batch_size", batchSize)
                put("restore_faces", restoreFaces)
                put("tiling", tiling)
                put("n_iter", n_iter)
            }

            emit(APIProgress.Progress(10, "准备请求..."))

            val result = postJSON("/sdapi/v1/txt2img", payload)

            emit(APIProgress.Progress(80, "处理结果..."))

            // 解析结果
            val images = result.getJSONArray("images")
            val info = result.optString("info", "")
            
            val outputImages = mutableListOf<String>()
            for (i in 0 until images.length()) {
                outputImages.add(images.getString(i))
            }

            emit(APIProgress.Completed(
                images = outputImages,
                info = info,
                seed = parseSeed(info),
                width = width,
                height = height
            ))

        } catch (e: Exception) {
            Log.e(TAG, "txt2img error: ${e.message}")
            emit(APIProgress.Error("生成失败: ${e.message}"))
        }
    }

    /**
     * 图像生成图像 (img2img)
     */
    fun img2img(
        images: List<String>,  // base64 编码的图像
        prompt: String,
        negativePrompt: String = "",
        width: Int = 512,
        height: Int = 50,
        steps: Int = 20,
        sampler: String = "Euler a",
        cfgScale: Float = 7f,
        denoisingStrength: Float = 0.75f,
        seed: Long = -1,
        mask: String? = null  // base64 编码的蒙版
    ): Flow<APIProgress> = flow {
        emit(APIProgress.Status("正在处理图像..."))

        try {
            val payload = JSONObject().apply {
                put("prompt", prompt)
                put("negative_prompt", negativePrompt)
                put("width", width)
                put("height", height)
                put("steps", steps)
                put("sampler_name", sampler)
                put("cfg_scale", cfgScale)
                put("denoising_strength", denoisingStrength)
                put("seed", if (seed == -1L) (Math.random() * Long.MAX_VALUE).toLong() else seed)
                put("images", JSONArray(images))
                mask?.let { put("mask", it) }
            }

            val result = postJSON("/sdapi/v1/img2img", payload)

            val imagesResult = result.getJSONArray("images")
            val outputImages = mutableListOf<String>()
            for (i in 0 until imagesResult.length()) {
                outputImages.add(imagesResult.getString(i))
            }

            emit(APIProgress.Completed(
                images = outputImages,
                info = result.optString("info", ""),
                seed = seed,
                width = width,
                height = height
            ))

        } catch (e: Exception) {
            emit(APIProgress.Error("处理失败: ${e.message}"))
        }
    }

    /**
     * 获取模型列表
     */
    suspend fun getModels(): List<ModelInfo> = withContext(Dispatchers.IO) {
        try {
            val result = getJSONArray("/sdapi/v1/sd-models")
            val models = mutableListOf<ModelInfo>()
            
            for (i in 0 until result.length()) {
                val model = result.getJSONObject(i)
                models.add(ModelInfo(
                    title = model.getString("title"),
                    modelName = model.getString("model_name"),
                    hash = model.optString("hash", ""),
                    sha256 = model.optString("sha256", ""),
                    filename = model.getString("filename"),
                    config = model.optString("config", ""),
                    image = model.optString("image", ""),
                    localPath = model.optString("local_path", ""),
                    locked = model.optBoolean("locked", false)
                ))
            }
            
            models
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get models: ${e.message}")
            emptyList()
        }
    }

    /**
     * 获取采样器列表
     */
    suspend fun getSamplers(): List<SamplerInfo> = withContext(Dispatchers.IO) {
        try {
            val result = getJSONArray("/sdapi/v1/samplers")
            val samplers = mutableListOf<SamplerInfo>()
            
            for (i in 0 until result.length()) {
                val sampler = result.getJSONObject(i)
                samplers.add(SamplerInfo(
                    name = sampler.getString("name"),
                    aliases = sampler.getString("aliases").split(","),
                    options = sampler.optString("options", "")
                ))
            }
            
            samplers
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 获取 VAE 列表
     */
    suspend fun getVAEs(): List<VAEFileInfo> = withContext(Dispatchers.IO) {
        try {
            val result = getJSONArray("/sdapi/v1/vae")
            val vaes = mutableListOf<VAEFileInfo>()
            
            for (i in 0 until result.length()) {
                val vae = result.getJSONObject(i)
                vaes.add(VAEFileInfo(
                    name = vae.getString("name"),
                    filename = vae.getString("filename")
                ))
            }
            
            vaes
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 切换模型
     */
    suspend fun switchModel(modelName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("sd_model_checkpoint", modelName)
            }
            
            postJSON("/sdapi/v1/options", payload)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to switch model: ${e.message}")
            false
        }
    }

    /**
     * 获取系统状态
     */
    suspend fun getSystemStatus(): SystemStatus? = withContext(Dispatchers.IO) {
        try {
            val result = getJSON("/system_stats")
            val options = getJSON("/sdapi/v1/options")
            
            SystemStatus(
                ramUsed = result.optLong("ram_used", 0),
                ramTotal = result.optLong("ram_total", 0),
                torchUsed = result.optLong("torch_used", 0),
                torchTotal = result.optLong("torch_total", 0),
                cudaUsed = result.optLong("cuda_used", 0),
                cudaTotal = result.optLong("cuda_total", 0),
                currentModel = options.optString("sd_model_checkpoint", ""),
                sdVersion = result.optString("sd_version", ""),
                gpuUsed = result.optBoolean("gpu_used", false)
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取任务队列状态
     */
    suspend fun getQueueStatus(): QueueStatus = withContext(Dispatchers.IO) {
        try {
            val result = getJSON("/queue/status")
            
            QueueStatus(
                queueLength = result.optInt("queue_length", 0),
                isProcessing = result.optBoolean("is_processing", false),
                currentJob = result.optString("current_job", ""),
                currentJobStep = result.optInt("current_job_step", 0),
                currentJobSteps = result.optInt("current_job_steps", 0)
            )
        } catch (e: Exception) {
            QueueStatus()
        }
    }

    /**
     * 终止当前任务
     */
    suspend fun interrupt(): Boolean = withContext(Dispatchers.IO) {
        try {
            postEmpty("/sdapi/v1/interrupt")
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取选项
     */
    suspend fun getOptions(): Map<String, Any> = withContext(Dispatchers.IO) {
        try {
            val result = getJSON("/sdapi/v1/options")
            val map = mutableMapOf<String, Any>()
            
            result.keys().forEach { key ->
                map[key] = result.get(key)
            }
            
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * 设置选项
     */
    suspend fun setOption(key: String, value: Any): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put(key, value)
            }
            postJSON("/sdapi/v1/options", payload)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==================== 内部方法 ====================

    private fun getJSON(path: String): JSONObject {
        val url = URL("$baseUrl$path")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 30000

        return try {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.readText()
            reader.close()
            JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    private fun getJSONArray(path: String): JSONArray {
        val url = URL("$baseUrl$path")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 30000

        return try {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.readText()
            reader.close()
            JSONArray(response)
        } finally {
            connection.disconnect()
        }
    }

    private fun postJSON(path: String, payload: JSONObject): JSONObject {
        val url = URL("$baseUrl$path")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")

        return try {
            val os = DataOutputStream(connection.outputStream)
            os.writeBytes(payload.toString())
            os.flush()
            os.close()

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.readText()
            reader.close()
            JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    private fun postEmpty(path: String) {
        val url = URL("$baseUrl$path")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.outputStream.use { it.write(ByteArray(0)) }
        connection.inputStream.close()
    }

    private fun parseSeed(info: String): Long {
        return try {
            val json = JSONObject(info)
            json.getLong("seed")
        } catch (e: Exception) {
            -1L
        }
    }

    fun setBaseUrl(url: String) {
        baseUrl = url
    }

    fun getBaseUrl(): String = baseUrl
}

/**
 * API 进度
 */
sealed class APIProgress {
    data class Status(val message: String) : APIProgress()
    data class Progress(val percent: Int, val message: String) : APIProgress()
    data class Completed(
        val images: List<String>,
        val info: String,
        val seed: Long,
        val width: Int,
        val height: Int
    ) : APIProgress()
    data class Error(val message: String) : APIProgress()
}

/**
 * 模型信息
 */
data class ModelInfo(
    val title: String,
    val modelName: String,
    val hash: String,
    val sha256: String,
    val filename: String,
    val config: String,
    val image: String,
    val localPath: String,
    val locked: Boolean
)

/**
 * 采样器信息
 */
data class SamplerInfo(
    val name: String,
    val aliases: List<String>,
    val options: String
)

/**
 * VAE 文件信息
 */
data class VAEFileInfo(
    val name: String,
    val filename: String
)

/**
 * 系统状态
 */
data class SystemStatus(
    val ramUsed: Long,
    val ramTotal: Long,
    val torchUsed: Long,
    val torchTotal: Long,
    val cudaUsed: Long,
    val cudaTotal: Long,
    val currentModel: String,
    val sdVersion: String,
    val gpuUsed: Boolean
)

/**
 * 队列状态
 */
data class QueueStatus(
    val queueLength: Int = 0,
    val isProcessing: Boolean = false,
    val currentJob: String = "",
    val currentJobStep: Int = 0,
    val currentJobSteps: Int = 0
)
