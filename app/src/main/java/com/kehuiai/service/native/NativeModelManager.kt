package com.kehuiai.service.native

import android.util.Log

/**
 * 本地模型管理 JNI 封装
 */
object NativeModelManager {

    private const val TAG = "NativeModel"

    // 格式检测
    fun getModelFormat(modelPath: String): Int {
        return nativeGetModelFormat(modelPath)
    }

    fun getFormatName(format: Int): String {
        return nativeGetFormatName(format)
    }

    // 文件大小
    fun getFileSize(modelPath: String): Long {
        return nativeGetFileSize(modelPath)
    }

    fun formatFileSize(bytes: Long): String {
        return nativeFormatFileSize(bytes)
    }

    // 验证
    fun isModelFile(modelPath: String): Boolean {
        return nativeIsModelFile(modelPath)
    }

    /**
     * 验证 .pt 文件安全性
     * @return true 如果安全，false 如果可能包含恶意代码
     */
    fun validatePtFile(modelPath: String): Boolean {
        return nativeValidatePtFile(modelPath)
    }

    /**
     * 转换为 MNN 格式
     */
    fun convertToMnn(inputPath: String, outputPath: String): Boolean {
        return nativeConvertToMnn(inputPath, outputPath)
    }

    // 格式名称映射
    fun getFormatDisplayName(format: Int): String {
        return when (format) {
            FORMAT_PT -> "PyTorch (.pt)"
            FORMAT_PTH -> "PyTorch (.pth)"
            FORMAT_CKPT -> "Checkpoint (.ckpt)"
            FORMAT_SAFETENSORS -> "SafeTensors (.safetensors)"
            FORMAT_MNN -> "MNN (.mnn)"
            else -> "Unknown"
        }
    }

    fun isSafeFormat(format: Int): Boolean {
        return format == FORMAT_SAFETENSORS || format == FORMAT_MNN
    }

    // 常量
    const val FORMAT_UNKNOWN = 0
    const val FORMAT_PT = 1
    const val FORMAT_PTH = 2
    const val FORMAT_CKPT = 3
    const val FORMAT_SAFETENSORS = 4
    const val FORMAT_MNN = 5

    // Native 方法
    private external fun nativeGetModelFormat(modelPath: String): Int
    private external fun nativeGetFormatName(format: Int): String
    private external fun nativeGetFileSize(modelPath: String): Long
    private external fun nativeFormatFileSize(bytes: Long): String
    private external fun nativeIsModelFile(modelPath: String): Boolean
    private external fun nativeValidatePtFile(modelPath: String): Boolean
    private external fun nativeConvertToMnn(inputPath: String, outputPath: String): Boolean

    init {
        try {
            System.loadLibrary("kuaihui_native")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library: ${e.message}")
        }
    }
}
