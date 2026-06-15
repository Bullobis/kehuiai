package com.kehuiai.service.native

import android.content.Context
import android.graphics.Bitmap
import android.util.Log

/**
 * 快绘AI v2.0 本地推理引擎 JNI 封装
 * 支持 MNN/QNN/NNAPI 真实推理
 */
class NativeInferenceEngine private constructor() {

    private var nativePtr: Long = 0
    private var isInitialized: Boolean = false

    companion object {
        private const val TAG = "NativeInference"

        // 引擎类型
        const val ENGINE_CPU = 0
        const val ENGINE_GPU_OPENCL = 1
        const val ENGINE_NPU_QNN = 2
        const val ENGINE_ANDROID_NN = 3
        const val ENGINE_MNN = 4

        // 模型格式
        const val FORMAT_UNKNOWN = 0
        const val FORMAT_PT = 1           // PyTorch .pt
        const val FORMAT_PTH = 2          // PyTorch .pth
        const val FORMAT_CKPT = 3         // Checkpoint .ckpt
        const val FORMAT_SAFETENSORS = 4  // SafeTensors .safetensors
        const val FORMAT_MNN = 5          // MNN format .mnn
        const val FORMAT_ONNX = 6         // ONNX format .onnx

        @Volatile
        private var instance: NativeInferenceEngine? = null

        fun getInstance(): NativeInferenceEngine {
            return instance ?: synchronized(this) {
                instance ?: NativeInferenceEngine().also { instance = it }
            }
        }

        /**
         * 检测设备支持的引擎
         */
        fun detectBestEngine(): Int {
            val engine = getInstance()
            if (!engine.create()) {
                Log.e(TAG, "Failed to create native engine")
                return ENGINE_CPU
            }

            val availableEngines = engine.getAvailableEngines()
            
            // 优先级: QNN NPU > NNAPI > OpenCL > CPU
            return when {
                availableEngines.contains(ENGINE_NPU_QNN) -> ENGINE_NPU_QNN
                availableEngines.contains(ENGINE_MNN) -> ENGINE_MNN
                availableEngines.contains(ENGINE_ANDROID_NN) -> ENGINE_ANDROID_NN
                availableEngines.contains(ENGINE_GPU_OPENCL) -> ENGINE_GPU_OPENCL
                else -> ENGINE_CPU
            }
        }
    }

    /**
     * 创建本地引擎
     */
    fun create(): Boolean {
        if (nativePtr != 0L) {
            return true
        }
        nativePtr = nativeCreate()
        if (nativePtr != 0L) {
            Log.i(TAG, "Native engine created successfully")
        }
        return nativePtr != 0L
    }

    /**
     * 销毁引擎
     */
    fun destroy() {
        if (nativePtr != 0L) {
            nativeDestroy(nativePtr)
            nativePtr = 0
        }
        isInitialized = false
        Log.i(TAG, "Native engine destroyed")
    }

    /**
     * 初始化引擎
     */
    fun init(engineType: Int): Boolean {
        if (nativePtr == 0L) {
            if (!create()) {
                Log.e(TAG, "Failed to create native engine")
                return false
            }
        }

        val result = nativeInit(nativePtr, engineType)
        isInitialized = result
        
        if (result) {
            Log.i(TAG, "Engine initialized: $engineType")
            Log.d(TAG, "Device Info:\n${getDeviceInfo()}")
        } else {
            Log.e(TAG, "Failed to initialize engine: $engineType")
        }
        
        return result
    }

    /**
     * 初始化引擎（自动选择最佳引擎）
     */
    fun initAuto(): Boolean {
        val bestEngine = detectBestEngine()
        Log.i(TAG, "Auto-detected best engine: $bestEngine")
        return init(bestEngine)
    }

    /**
     * 获取可用的引擎列表
     */
    fun getAvailableEngines(): List<Int> {
        if (nativePtr == 0L) return listOf(ENGINE_CPU)

        val flags = nativeGetAvailableEngines(nativePtr)
        val engines = mutableListOf<Int>()

        if (flags and (1 shl ENGINE_CPU) != 0) engines.add(ENGINE_CPU)
        if (flags and (1 shl ENGINE_GPU_OPENCL) != 0) engines.add(ENGINE_GPU_OPENCL)
        if (flags and (1 shl ENGINE_NPU_QNN) != 0) engines.add(ENGINE_NPU_QNN)
        if (flags and (1 shl ENGINE_ANDROID_NN) != 0) engines.add(ENGINE_ANDROID_NN)
        if (flags and (1 shl ENGINE_MNN) != 0) engines.add(ENGINE_MNN)

        return engines
    }

    /**
     * 设置引擎类型
     */
    fun setEngine(engineType: Int) {
        if (nativePtr != 0L) {
            nativeSetEngine(nativePtr, engineType)
            Log.i(TAG, "Engine switched to: $engineType")
        }
    }

    /**
     * 加载模型
     */
    fun loadModel(modelPath: String, format: Int): Boolean {
        if (nativePtr == 0L) return false
        
        Log.i(TAG, "Loading model: $modelPath (format: $format)")
        val result = nativeLoadModel(nativePtr, modelPath, format)
        
        if (result) {
            Log.i(TAG, "Model loaded successfully")
        } else {
            Log.e(TAG, "Failed to load model")
        }
        
        return result
    }

    /**
     * 加载模型包（包含UNet/VAE/TextEncoder）
     */
    fun loadModelBundle(basePath: String): Boolean {
        if (nativePtr == 0L) return false
        
        Log.i(TAG, "Loading model bundle from: $basePath")
        return nativeLoadModelBundle(nativePtr, basePath)
    }

    /**
     * 卸载模型
     */
    fun unloadModel() {
        if (nativePtr != 0L) {
            nativeUnloadModel(nativePtr)
            Log.i(TAG, "Model unloaded")
        }
    }

    /**
     * 检查模型是否已加载
     */
    fun isModelLoaded(): Boolean {
        return nativePtr != 0L && nativeIsModelLoaded(nativePtr)
    }

    /**
     * 加载 LoRA
     */
    fun loadLora(loraPath: String, strength: Float = 1.0f): Boolean {
        if (nativePtr == 0L) return false
        return nativeLoadLora(nativePtr, loraPath, strength)
    }

    /**
     * 卸载 LoRA
     */
    fun unloadLora(loraPath: String) {
        if (nativePtr != 0L) {
            nativeUnloadLora(nativePtr, loraPath)
        }
    }

    /**
     * 检测模型格式
     */
    fun detectModelFormat(modelPath: String): Int {
        if (nativePtr == 0L) return FORMAT_UNKNOWN
        return nativeDetectModelFormat(nativePtr, modelPath)
    }

    /**
     * 文生图
     */
    fun generateText2Image(
        prompt: String,
        negativePrompt: String = "",
        width: Int = 512,
        height: Int = 512,
        steps: Int = 20,
        cfgScale: Float = 7.5f,
        seed: Long = -1,
        scheduler: String = "euler"
    ): Bitmap? {
        if (nativePtr == 0L) {
            Log.e(TAG, "Native engine not initialized")
            return null
        }

        if (!isModelLoaded()) {
            Log.w(TAG, "Model not loaded, using standalone mode")
        }

        Log.i(TAG, "Generating image: ${width}x$height, steps=$steps, cfg=$cfgScale")
        Log.i(TAG, "Prompt: $prompt")

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        val startTime = System.currentTimeMillis()

        val result = nativeGenerateText2Image(
            nativePtr,
            prompt,
            negativePrompt,
            width,
            height,
            steps,
            cfgScale,
            seed,
            scheduler,
            pixels
        )

        val elapsed = System.currentTimeMillis() - startTime

        if (result) {
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            Log.i(TAG, "Generation completed in ${elapsed}ms")
            return bitmap
        }

        Log.e(TAG, "Generation failed")
        return null
    }

    /**
     * 图生图
     */
    fun generateImage2Image(
        inputBitmap: Bitmap,
        prompt: String,
        negativePrompt: String = "",
        steps: Int = 20,
        cfgScale: Float = 7.5f,
        strength: Float = 0.75f,
        seed: Long = -1,
        scheduler: String = "euler"
    ): Bitmap? {
        if (nativePtr == 0L) return null

        val width = inputBitmap.width
        val height = inputBitmap.height

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val inputPixels = IntArray(width * height)
        val outputPixels = IntArray(width * height)
        
        inputBitmap.getPixels(inputPixels, 0, width, 0, 0, width, height)

        val result = nativeGenerateImage2Image(
            nativePtr,
            inputPixels,
            prompt,
            negativePrompt,
            width,
            height,
            steps,
            cfgScale,
            seed,
            scheduler,
            strength,
            outputPixels
        )

        if (result) {
            bitmap.setPixels(outputPixels, 0, width, 0, 0, width, height)
            return bitmap
        }

        return null
    }

    /**
     * 局部重绘 (Inpainting)
     */
    fun generateInpaint(
        inputBitmap: Bitmap,
        maskBitmap: Bitmap,
        prompt: String,
        negativePrompt: String = "",
        steps: Int = 20,
        cfgScale: Float = 7.5f,
        seed: Long = -1,
        scheduler: String = "euler"
    ): Bitmap? {
        if (nativePtr == 0L) return null

        val width = inputBitmap.width
        val height = inputBitmap.height

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val inputPixels = IntArray(width * height)
        val maskPixels = IntArray(width * height)
        val outputPixels = IntArray(width * height)
        
        inputBitmap.getPixels(inputPixels, 0, width, 0, 0, width, height)
        maskBitmap.getPixels(maskPixels, 0, width, 0, 0, width, height)

        val result = nativeGenerateInpaint(
            nativePtr,
            inputPixels,
            maskPixels,
            prompt,
            negativePrompt,
            width,
            height,
            steps,
            cfgScale,
            seed,
            scheduler,
            outputPixels
        )

        if (result) {
            bitmap.setPixels(outputPixels, 0, width, 0, 0, width, height)
            return bitmap
        }

        return null
    }

    /**
     * 超分辨率放大
     */
    fun generateUpscale(
        inputBitmap: Bitmap,
        scale: Int = 2
    ): Bitmap? {
        if (nativePtr == 0L) return null

        val width = inputBitmap.width
        val height = inputBitmap.height
        val newWidth = width * scale
        val newHeight = height * scale

        val bitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
        val inputPixels = IntArray(width * height)
        val outputPixels = IntArray(newWidth * newHeight)
        
        inputBitmap.getPixels(inputPixels, 0, width, 0, 0, width, height)

        val result = nativeGenerateUpscale(
            nativePtr,
            inputPixels,
            width,
            height,
            scale,
            outputPixels
        )

        if (result) {
            bitmap.setPixels(outputPixels, 0, newWidth, 0, 0, newWidth, newHeight)
            return bitmap
        }

        return null
    }

    /**
     * 转换模型格式
     */
    fun convertModel(
        inputPath: String,
        outputPath: String,
        targetFormat: Int
    ): Boolean {
        if (nativePtr == 0L) return false
        return nativeConvertModel(nativePtr, inputPath, outputPath, targetFormat)
    }

    /**
     * 获取设备信息
     */
    fun getDeviceInfo(): String {
        return if (nativePtr != 0L) {
            nativeGetDeviceInfo(nativePtr)
        } else {
            "Native engine not initialized"
        }
    }

    /**
     * 是否为骁龙芯片
     */
    fun isQualcommSnapdragon(): Boolean {
        return nativePtr != 0L && nativeIsQualcommSnapdragon()
    }

    /**
     * 是否支持 NNAPI
     */
    fun hasNNAPI(): Boolean {
        return nativePtr != 0L && nativeHasNNAPI()
    }

    /**
     * NNAPI 是否为快速模式 (NPU)
     */
    fun isNNAPIFast(): Boolean {
        return nativePtr != 0L && nativeIsNNAPIFast()
    }

    /**
     * 启用/禁用性能模式
     */
    fun enablePerformanceMode(enable: Boolean) {
        if (nativePtr != 0L) {
            nativeEnablePerformanceMode(nativePtr, enable)
        }
    }

    // ==================== Native 方法 ====================

    private external fun nativeCreate(): Long
    private external fun nativeDestroy(ptr: Long)
    private external fun nativeInit(ptr: Long, engineType: Int): Boolean
    private external fun nativeGetAvailableEngines(ptr: Long): Int
    private external fun nativeSetEngine(ptr: Long, engineType: Int)
    private external fun nativeDetectModelFormat(ptr: Long, modelPath: String): Int
    private external fun nativeLoadModel(ptr: Long, modelPath: String, format: Int): Boolean
    private external fun nativeLoadModelBundle(ptr: Long, basePath: String): Boolean
    private external fun nativeUnloadModel(ptr: Long)
    private external fun nativeIsModelLoaded(ptr: Long): Boolean
    private external fun nativeLoadLora(ptr: Long, loraPath: String, strength: Float): Boolean
    private external fun nativeUnloadLora(ptr: Long, loraPath: String)
    private external fun nativeConvertModel(ptr: Long, inputPath: String, outputPath: String, targetFormat: Int): Boolean

    private external fun nativeGenerateText2Image(
        ptr: Long,
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        cfgScale: Float,
        seed: Long,
        scheduler: String,
        outputPixels: IntArray
    ): Boolean

    private external fun nativeGenerateImage2Image(
        ptr: Long,
        inputPixels: IntArray,
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        cfgScale: Float,
        seed: Long,
        scheduler: String,
        strength: Float,
        outputPixels: IntArray
    ): Boolean

    private external fun nativeGenerateInpaint(
        ptr: Long,
        inputPixels: IntArray,
        maskPixels: IntArray,
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        cfgScale: Float,
        seed: Long,
        scheduler: String,
        outputPixels: IntArray
    ): Boolean

    private external fun nativeGenerateUpscale(
        ptr: Long,
        inputPixels: IntArray,
        width: Int,
        height: Int,
        scale: Int,
        outputPixels: IntArray
    ): Boolean

    private external fun nativeGetDeviceInfo(ptr: Long): String
    private external fun nativeIsQualcommSnapdragon(): Boolean
    private external fun nativeHasNNAPI(): Boolean
    private external fun nativeIsNNAPIFast(): Boolean
    private external fun nativeEnablePerformanceMode(ptr: Long, enable: Boolean)

    // 加载本地库
    init {
        try {
            System.loadLibrary("kuaihui_native")
            Log.i(TAG, "Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library: ${e.message}")
        }
    }
}
