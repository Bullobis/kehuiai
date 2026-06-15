package com.kehuiai.service.native

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * KehuiAI MNN 模型管理器
 * 整合 KehuiAI 2.6.0 的预编译 MNN 模型
 */
class KehuiAIModelManager(private val context: Context) {

    companion object {
        private const val TAG = "KehuiAIModel"
        
        // 模型路径（assets 目录）
        const val MODEL_DIR = "models/mnn"
        
        // 模型文件名
        const val CLIP_MODEL_1 = "clip_skip_1.mnn"
        const val CLIP_MODEL_2 = "clip_skip_2.mnn"
        const val UNET_MODEL = "unet.mnn"
        const val VAE_DECODER = "vae_decoder.mnn"
        const val VAE_ENCODER = "vae_encoder.mnn"
        const val TOKENIZER_JSON = "tokenizer.json"
        
        @Volatile
        private var instance: KehuiAIModelManager? = null
        
        fun getInstance(context: Context): KehuiAIModelManager {
            return instance ?: synchronized(this) {
                instance ?: KehuiAIModelManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // 模型是否已加载
    private var modelsLoaded = false
    
    // 模型文件路径
    private var clipModelPath: String = ""
    private var clip2ModelPath: String = ""
    private var unetModelPath: String = ""
    private var vaeDecoderPath: String = ""
    private var vaeEncoderPath: String = ""
    private var tokenizerPath: String = ""
    
    /**
     * 初始化模型
     * 从 assets 复制到内部存储
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Initializing KehuiAI models...")
            
            val modelDir = File(context.filesDir, MODEL_DIR)
            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }
            
            // 复制模型文件
            copyAssetToFile(CLIP_MODEL_1, File(modelDir, CLIP_MODEL_1))
            copyAssetToFile(CLIP_MODEL_2, File(modelDir, CLIP_MODEL_2))
            copyAssetToFile(UNET_MODEL, File(modelDir, UNET_MODEL))
            copyAssetToFile(VAE_DECODER, File(modelDir, VAE_DECODER))
            copyAssetToFile(VAE_ENCODER, File(modelDir, VAE_ENCODER))
            copyAssetToFile(TOKENIZER_JSON, File(modelDir, TOKENIZER_JSON))
            
            // 设置路径
            clipModelPath = File(modelDir, CLIP_MODEL_1).absolutePath
            clip2ModelPath = File(modelDir, CLIP_MODEL_2).absolutePath
            unetModelPath = File(modelDir, UNET_MODEL).absolutePath
            vaeDecoderPath = File(modelDir, VAE_DECODER).absolutePath
            vaeEncoderPath = File(modelDir, VAE_ENCODER).absolutePath
            tokenizerPath = File(modelDir, TOKENIZER_JSON).absolutePath
            
            modelsLoaded = true
            
            Log.i(TAG, "Models initialized successfully")
            Log.i(TAG, "CLIP: $clipModelPath")
            Log.i(TAG, "UNet: $unetModelPath")
            Log.i(TAG, "VAE Decoder: $vaeDecoderPath")
            
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize models: ${e.message}")
            return@withContext false
        }
    }
    
    /**
     * 复制 asset 文件到内部存储
     */
    private fun copyAssetToFile(assetName: String, destFile: File) {
        if (destFile.exists()) {
            Log.d(TAG, "Model already exists: ${destFile.name}")
            return
        }
        
        try {
            context.assets.open("$MODEL_DIR/$assetName").use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "Copied model: $assetName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy $assetName: ${e.message}")
        }
    }
    
    /**
     * 检查模型是否已加载
     */
    fun isModelsLoaded(): Boolean = modelsLoaded
    
    /**
     * 获取模型路径
     */
    fun getModelPaths(): ModelPaths {
        return ModelPaths(
            clipPath = clipModelPath,
            clip2Path = clip2ModelPath,
            unetPath = unetModelPath,
            vaeDecoderPath = vaeDecoderPath,
            vaeEncoderPath = vaeEncoderPath,
            tokenizerPath = tokenizerPath
        )
    }
    
    /**
     * 检查模型文件是否存在
     */
    fun checkModelsExist(): List<String> {
        val missing = mutableListOf<String>()
        
        if (clipModelPath.isEmpty() || !File(clipModelPath).exists()) {
            missing.add(CLIP_MODEL_1)
        }
        if (unetModelPath.isEmpty() || !File(unetModelPath).exists()) {
            missing.add(UNET_MODEL)
        }
        if (vaeDecoderPath.isEmpty() || !File(vaeDecoderPath).exists()) {
            missing.add(VAE_DECODER)
        }
        if (vaeEncoderPath.isEmpty() || !File(vaeEncoderPath).exists()) {
            missing.add(VAE_ENCODER)
        }
        if (tokenizerPath.isEmpty() || !File(tokenizerPath).exists()) {
            missing.add(TOKENIZER_JSON)
        }
        
        return missing
    }
    
    /**
     * 获取模型大小信息
     */
    fun getModelInfo(): Map<String, Long> {
        val info = mutableMapOf<String, Long>()
        
        listOf(
            CLIP_MODEL_1 to clipModelPath,
            CLIP_MODEL_2 to clip2ModelPath,
            UNET_MODEL to unetModelPath,
            VAE_DECODER to vaeDecoderPath,
            VAE_ENCODER to vaeEncoderPath,
            TOKENIZER_JSON to tokenizerPath
        ).forEach { (name, path) ->
            val file = File(path)
            if (file.exists()) {
                info[name] = file.length()
            }
        }
        
        return info
    }
    
    data class ModelPaths(
        val clipPath: String,
        val clip2Path: String,
        val unetPath: String,
        val vaeDecoderPath: String,
        val vaeEncoderPath: String,
        val tokenizerPath: String
    )
}
