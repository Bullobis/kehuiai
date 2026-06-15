package com.kehuiai.service.native

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.ByteArrayOutputStream

/**
 * KehuiAI 原生引擎 JNI 包装器
 * 整合 KehuiAI 2.6.0 的完整 C++ 推理功能
 */
class NativeKehuiAIEngine private constructor(
    private val _context: Context
) {
    
    companion object {
        private const val TAG = "NativeKehuiAIEngine"
        
        // 引擎类型
        const val ENGINE_CPU = 0
        const val ENGINE_GPU_OPENCL = 1
        const val ENGINE_NPU_QNN = 2
        const val ENGINE_ANDROID_NNAPI = 3
        const val ENGINE_MNN = 4
        
        // 模型类型
        const val MODEL_CLIP = 0
        const val MODEL_CLIP2 = 1
        const val MODEL_UNET = 2
        const val MODEL_VAE_DECODER = 3
        const val MODEL_VAE_ENCODER = 4
        const val MODEL_UPSCALE = 5
        const val MODEL_SAFETY = 6
        
        // 采样器类型
        const val SAMPLER_EULER = 0
        const val SAMPLER_EULER_A = 1
        const val SAMPLER_DPM_2M = 2
        const val SAMPLER_DPM_2M_KARRAS = 3
        const val SAMPLER_DPM_2M_SDE = 4
        const val SAMPLER_DPM_2M_SDE_KARRAS = 5
        const val SAMPLER_DPMPP_2M = 6
        const val SAMPLER_DPMPP_2M_KARRAS = 7
        const val SAMPLER_DPMPP_SDE = 8
        const val SAMPLER_DPMPP_SDE_KARRAS = 9
        const val SAMPLER_UNI_PC = 10
        const val SAMPLER_UNI_PC_BH2 = 11
        const val SAMPLER_LCM = 12
        const val SAMPLER_DDIM = 13
        const val SAMPLER_PNDM = 14
        
        // 采样器名称
        val SAMPLER_NAMES = listOf(
            "euler", "euler_ancestral", "dpm_2m", "dpm_2m_karras",
            "dpm_2m_sde", "dpm_2m_sde_karras", "dpmpp_2m", "dpmpp_2m_karras",
            "dpmpp_sde", "dpmpp_sde_karras", "uni_pc", "uni_pc_bh2",
            "lcm", "ddim", "pndm"
        )
        
        // ControlNet 类型
        const val CN_CANNY = 0
        const val CN_DEPTH = 1
        const val CN_POSE = 2
        const val CN_SCRIBBLE = 3
        const val CN_SEG = 4
        const val CN_NORMAL = 5
        const val CN_LINEART = 6
        const val CN_REDHRAW = 7
        const val CN_BLUR = 8
        
        @Volatile
        private var instance: NativeKehuiAIEngine? = null
        
        fun getInstance(context: Context): NativeKehuiAIEngine {
            return instance ?: synchronized(this) {
                instance ?: NativeKehuiAIEngine(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private var isInitialized = false
    
    // 加载本地库
    init {
        try {
            System.loadLibrary("kuaihui_native")
            Log.i(TAG, "Native library loaded")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library: ${e.message}")
        }
    }
    
    /**
     * 初始化引擎
     */
    fun initialize(modelDir: String, engineType: Int = ENGINE_MNN): Boolean {
        if (isInitialized) return true
        
        try {
            isInitialized = nativeInitialize(modelDir, engineType)
            Log.i(TAG, "Engine initialized: $isInitialized")
            return isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "Init error: ${e.message}")
            return false
        }
    }
    
    /**
     * 销毁引擎
     */
    fun destroy() {
        try {
            nativeDestroy()
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Destroy error: ${e.message}")
        }
    }
    
    /**
     * 文生图生成
     */
    fun generate(
        prompt: String,
        negativePrompt: String = "",
        width: Int = 512,
        height: Int = 512,
        steps: Int = 20,
        cfgScale: Float = 7.5f,
        seed: Int = -1,
        samplerType: Int = SAMPLER_EULER,
        strength: Float = 0.8f,
        enablePromptCache: Boolean = true
    ): Flow<LDGenerationProgress> = flow {
        emit(LDGenerationProgress.Status("Generating..."))
        
        if (!isInitialized) {
            emit(LDGenerationProgress.Error("Engine not initialized"))
            return@flow
        }
        
        try {
            val imageData = nativeGenerate(
                prompt, negativePrompt, width, height,
                steps, cfgScale, seed, samplerType, strength, enablePromptCache
            )
            
            if (imageData != null && imageData.isNotEmpty()) {
                val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
                if (bitmap != null) {
                    emit(LDGenerationProgress.Completed(bitmap))
                } else {
                    emit(LDGenerationProgress.Error("Failed to decode image"))
                }
            } else {
                emit(LDGenerationProgress.Error("Generation failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Generation error: ${e.message}", e)
            emit(LDGenerationProgress.Error(e.message ?: "Unknown error"))
        }
    }
    
    /**
     * 图生图 (Img2Img)
     */
    fun generateImg2Img(
        inputImage: Bitmap,
        prompt: String,
        negativePrompt: String = "",
        width: Int = 512,
        height: Int = 512,
        steps: Int = 20,
        cfgScale: Float = 7.5f,
        seed: Int = -1,
        samplerType: Int = SAMPLER_EULER,
        strength: Float = 0.8f
    ): Flow<LDGenerationProgress> = flow {
        emit(LDGenerationProgress.Status("Processing img2img..."))
        
        if (!isInitialized) {
            emit(LDGenerationProgress.Error("Engine not initialized"))
            return@flow
        }
        
        try {
            val imageBytes = bitmapToBytes(inputImage)
            val resultBytes = nativeGenerateImg2Img(
                imageBytes, prompt, negativePrompt, width, height,
                steps, cfgScale, seed, samplerType, strength, true
            )
            
            if (resultBytes != null) {
                val bitmap = BitmapFactory.decodeByteArray(resultBytes, 0, resultBytes.size)
                if (bitmap != null) {
                    emit(LDGenerationProgress.Completed(bitmap))
                }
            }
        } catch (e: Exception) {
            emit(LDGenerationProgress.Error(e.message ?: "Error"))
        }
    }
    
    /**
     * 局部重绘 (Inpainting)
     */
    fun generateInpaint(
        inputImage: Bitmap,
        maskImage: Bitmap,
        prompt: String,
        negativePrompt: String = "",
        width: Int = 512,
        height: Int = 512,
        steps: Int = 20,
        cfgScale: Float = 7.5f,
        seed: Int = -1,
        samplerType: Int = SAMPLER_EULER,
        strength: Float = 0.8f
    ): Flow<LDGenerationProgress> = flow {
        emit(LDGenerationProgress.Status("Inpainting..."))
        
        if (!isInitialized) {
            emit(LDGenerationProgress.Error("Engine not initialized"))
            return@flow
        }
        
        try {
            val imageBytes = bitmapToBytes(inputImage)
            val maskBytes = bitmapToBytes(maskImage)
            
            val resultBytes = nativeGenerateInpaint(
                imageBytes, maskBytes, prompt, negativePrompt, width, height,
                steps, cfgScale, seed, samplerType, strength, true
            )
            
            if (resultBytes != null) {
                val bitmap = BitmapFactory.decodeByteArray(resultBytes, 0, resultBytes.size)
                if (bitmap != null) {
                    emit(LDGenerationProgress.Completed(bitmap))
                }
            }
        } catch (e: Exception) {
            emit(LDGenerationProgress.Error(e.message ?: "Error"))
        }
    }
    
    /**
     * 超分辨率放大
     */
    fun upscale(
        inputImage: Bitmap,
        scale: Int = 2,
        method: Int = 0
    ): Flow<LDGenerationProgress> = flow {
        emit(LDGenerationProgress.Status("Upscaling..."))
        
        if (!isInitialized) {
            emit(LDGenerationProgress.Error("Engine not initialized"))
            return@flow
        }
        
        try {
            val imageBytes = bitmapToBytes(inputImage)
            val resultBytes = nativeUpscale(imageBytes, scale, method)
            
            if (resultBytes != null) {
                val newWidth = inputImage.width * scale
                val newHeight = inputImage.height * scale
                val bitmap = BitmapFactory.decodeByteArray(resultBytes, 0, resultBytes.size)
                if (bitmap != null) {
                    emit(LDGenerationProgress.Completed(bitmap))
                }
            }
        } catch (e: Exception) {
            emit(LDGenerationProgress.Error(e.message ?: "Error"))
        }
    }
    
    // ============== LoRA 管理 ==============
    
    fun loadLoRA(path: String, weight: Float = 1.0f): Boolean {
        return try {
            nativeLoadLoRA(path, weight)
        } catch (e: Exception) {
            Log.e(TAG, "Load LoRA error: ${e.message}")
            false
        }
    }
    
    fun unloadLoRA(name: String) {
        try {
            nativeUnloadLoRA(name)
        } catch (e: Exception) {
            Log.e(TAG, "Unload LoRA error: ${e.message}")
        }
    }
    
    fun unloadAllLoRA() {
        try {
            nativeUnloadAllLoRA()
        } catch (e: Exception) {
            Log.e(TAG, "Unload all LoRA error: ${e.message}")
        }
    }
    
    // ============== Embeddings ==============
    
    fun loadEmbeddings(path: String): Boolean {
        return try {
            nativeLoadEmbeddings(path)
        } catch (e: Exception) {
            Log.e(TAG, "Load embeddings error: ${e.message}")
            false
        }
    }
    
    fun unloadEmbeddings() {
        try {
            nativeUnloadEmbeddings()
        } catch (e: Exception) {
            Log.e(TAG, "Unload embeddings error: ${e.message}")
        }
    }
    
    // ============== VAE ==============
    
    fun loadVAE(path: String): Boolean {
        return try {
            nativeLoadVAE(path)
        } catch (e: Exception) {
            Log.e(TAG, "Load VAE error: ${e.message}")
            false
        }
    }
    
    fun setVAE(name: String) {
        try {
            nativeSetVAE(name)
        } catch (e: Exception) {
            Log.e(TAG, "Set VAE error: ${e.message}")
        }
    }
    
    // ============== ControlNet ==============
    
    fun loadControlNet(path: String, controlType: Int): Boolean {
        return try {
            nativeLoadControlNet(path, controlType)
        } catch (e: Exception) {
            Log.e(TAG, "Load ControlNet error: ${e.message}")
            false
        }
    }
    
    fun unloadControlNet(controlType: Int) {
        try {
            nativeUnloadControlNet(controlType)
        } catch (e: Exception) {
            Log.e(TAG, "Unload ControlNet error: ${e.message}")
        }
    }
    
    fun applyControlNet(
        inputImage: Bitmap,
        controlType: Int,
        guidanceScale: Float = 1.0f
    ): Flow<LDGenerationProgress> = flow {
        emit(LDGenerationProgress.Status("Applying ControlNet..."))
        
        if (!isInitialized) {
            emit(LDGenerationProgress.Error("Engine not initialized"))
            return@flow
        }
        
        try {
            val imageBytes = bitmapToBytes(inputImage)
            val resultBytes = nativeApplyControlNet(imageBytes, controlType, guidanceScale)
            
            if (resultBytes != null) {
                val bitmap = BitmapFactory.decodeByteArray(resultBytes, 0, resultBytes.size)
                if (bitmap != null) {
                    emit(LDGenerationProgress.Completed(bitmap))
                }
            }
        } catch (e: Exception) {
            emit(LDGenerationProgress.Error(e.message ?: "Error"))
        }
    }
    
    // ============== 信息查询 ==============
    
    fun getDeviceInfo(): String {
        return try {
            nativeGetDeviceInfo()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
    
    fun getModelInfo(): String {
        return try {
            nativeGetModelInfo()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
    
    // ============== 调度器 ==============
    
    fun setScheduler(samplerType: Int) {
        try {
            nativeSetScheduler(samplerType)
        } catch (e: Exception) {
            Log.e(TAG, "Set scheduler error: ${e.message}")
        }
    }
    
    fun getSchedulerNames(): List<String> = SAMPLER_NAMES
    
    // ============== 缓存 ==============
    
    fun clearPromptCache() {
        try {
            nativeClearPromptCache()
        } catch (e: Exception) {
            Log.e(TAG, "Clear prompt cache error: ${e.message}")
        }
    }
    
    fun clearModelCache() {
        try {
            nativeClearModelCache()
        } catch (e: Exception) {
            Log.e(TAG, "Clear model cache error: ${e.message}")
        }
    }
    
    // ============== Safety Checker ==============
    
    fun checkSafety(imageData: ByteArray): Boolean {
        return try {
            nativeCheckSafety(imageData)
        } catch (e: Exception) {
            true // 默认认为安全
        }
    }
    
    fun setSafetyChecker(path: String) {
        try {
            nativeSetSafetyChecker(path)
        } catch (e: Exception) {
            Log.e(TAG, "Set safety checker error: ${e.message}")
        }
    }
    
    // ============== 工具函数 ==============
    
    private fun bitmapToBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
    
    // ============== Native 方法 ==============
    
    private external fun nativeInitialize(modelDir: String, engineType: Int): Boolean
    private external fun nativeDestroy()
    
    private external fun nativeGenerate(
        prompt: String, negativePrompt: String,
        width: Int, height: Int, steps: Int, cfgScale: Float, seed: Int,
        samplerType: Int, strength: Float, enablePromptCache: Boolean
    ): ByteArray?
    
    private external fun nativeGenerateImg2Img(
        inputImage: ByteArray, prompt: String, negativePrompt: String,
        width: Int, height: Int, steps: Int, cfgScale: Float, seed: Int,
        samplerType: Int, strength: Float, enablePromptCache: Boolean
    ): ByteArray?
    
    private external fun nativeGenerateInpaint(
        inputImage: ByteArray, maskImage: ByteArray,
        prompt: String, negativePrompt: String,
        width: Int, height: Int, steps: Int, cfgScale: Float, seed: Int,
        samplerType: Int, strength: Float, enablePromptCache: Boolean
    ): ByteArray?
    
    private external fun nativeUpscale(inputImage: ByteArray, scale: Int, method: Int): ByteArray?
    
    private external fun nativeLoadLoRA(path: String, weight: Float): Boolean
    private external fun nativeUnloadLoRA(name: String)
    private external fun nativeUnloadAllLoRA()
    
    private external fun nativeLoadEmbeddings(path: String): Boolean
    private external fun nativeUnloadEmbeddings()
    
    private external fun nativeLoadVAE(path: String): Boolean
    private external fun nativeSetVAE(name: String)
    
    private external fun nativeLoadControlNet(path: String, controlType: Int): Boolean
    private external fun nativeUnloadControlNet(controlType: Int)
    private external fun nativeApplyControlNet(inputImage: ByteArray, controlType: Int, guidanceScale: Float): ByteArray?
    
    private external fun nativeGetDeviceInfo(): String
    private external fun nativeGetModelInfo(): String
    
    private external fun nativeSetScheduler(samplerType: Int)
    private external fun nativeGetSchedulerNames(): IntArray
    
    private external fun nativeClearPromptCache()
    private external fun nativeClearModelCache()
    
    private external fun nativeCheckSafety(imageData: ByteArray): Boolean
    private external fun nativeSetSafetyChecker(path: String)
}

// 进度回调 sealed class
sealed class LDGenerationProgress {
    data class Status(val message: String) : LDGenerationProgress()
    data class Progress(val percent: Int, val message: String) : LDGenerationProgress()
    data class Completed(val image: Bitmap) : LDGenerationProgress()
    data class Error(val message: String) : LDGenerationProgress()
}
