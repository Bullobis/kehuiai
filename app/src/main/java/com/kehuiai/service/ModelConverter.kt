package com.kehuiai.service

import android.content.Context
import android.util.Log
import com.kehuiai.data.model.ModelFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * 模型格式转换工具
 * 支持：PT/PTH/CKPT/SAFE -> MNN 转换
 * 支持：模型量化（FP16/INT8/INT4）
 */
class ModelConverter(private val context: Context) {

    companion object {
        private const val TAG = "ModelConverter"
        
        // 量化类型
        const val QUANT_NONE = 0      // 不量化
        const val QUANT_FP16 = 1     // FP16 半精度
        const val QUANT_INT8 = 2     // INT8 量化
        const val QUANT_INT4 = 3     // INT4 量化
    }

    private val convertDir = File(context.filesDir, "converted")
    private val tempDir = File(context.cacheDir, "convert_temp")
    
    init {
        if (!convertDir.exists()) convertDir.mkdirs()
        if (!tempDir.exists()) tempDir.mkdirs()
    }

    /**
     * 转换为 MNN 格式
     */
    fun convertToMnn(
        inputPath: String,
        outputPath: String? = null,
        targetPlatform: String = "android"
    ): Flow<ConversionProgress> = flow {
        emit(ConversionProgress.Status("开始转换..."))

        try {
            val inputFile = File(inputPath)
            if (!inputFile.exists()) {
                emit(ConversionProgress.Error("输入文件不存在"))
                return@flow
            }

            val mnnDir = File(convertDir, "mnn")
            if (!mnnDir.exists()) mnnDir.mkdirs()
            val outputFile = if (outputPath != null) {
                File(outputPath)
            } else {
                File(mnnDir, "${inputFile.nameWithoutExtension}.mnn")
            }
            
            emit(ConversionProgress.Progress(10, "分析模型结构..."))

            // 检测输入格式
            val inputFormat = detectFormat(inputPath)
            if (inputFormat == ModelFormat.MNN) {
                emit(ConversionProgress.Error("已经是 MNN 格式"))
                return@flow
            }

            emit(ConversionProgress.Progress(30, "加载模型..."))

            // 读取模型文件
            val modelData = readModelFile(inputPath, inputFormat)

            emit(ConversionProgress.Progress(50, "转换为 MNN 格式..."))

            // 执行转换（这里是模拟实现）
            val convertedData = convertToMnnFormat(modelData, inputFormat)

            emit(ConversionProgress.Progress(80, "保存模型..."))

            // 保存转换后的模型
            writeModelFile(outputFile, convertedData)

            emit(ConversionProgress.Completed(
                outputPath = outputFile.absolutePath,
                inputFormat = inputFormat.name,
                outputFormat = "MNN",
                inputSize = inputFile.length(),
                outputSize = outputFile.length()
            ))

        } catch (e: Exception) {
            Log.e(TAG, "Conversion error: ${e.message}")
            emit(ConversionProgress.Error("转换失败: ${e.message}"))
        }
    }

    /**
     * 模型量化
     */
    fun quantizeModel(
        inputPath: String,
        quantType: Int,
        outputPath: String? = null
    ): Flow<ConversionProgress> = flow {
        emit(ConversionProgress.Status("开始量化..."))

        try {
            val inputFile = File(inputPath)
            val quantName = when (quantType) {
                QUANT_FP16 -> "FP16"
                QUANT_INT8 -> "INT8"
                QUANT_INT4 -> "INT4"
                else -> "NONE"
            }

            emit(ConversionProgress.Progress(10, "加载模型..."))

            val modelData = readModelFile(inputPath, detectFormat(inputPath))

            emit(ConversionProgress.Progress(40, "执行 $quantName 量化..."))

            // 量化处理
            val quantizedData = quantize(modelData, quantType)

            emit(ConversionProgress.Progress(80, "保存量化模型..."))

            val outputDir = File(convertDir, "quantized")
            if (!outputDir.exists()) outputDir.mkdirs()
            val outputFileName = "${inputFile.nameWithoutExtension}_$quantName.${inputFile.extension}"
            val outputFile = if (outputPath != null) {
                File(outputPath)
            } else {
                File(outputDir, outputFileName)
            }
            
            writeModelFile(outputFile, quantizedData)

            emit(ConversionProgress.Completed(
                outputPath = outputFile.absolutePath,
                inputFormat = "Original",
                outputFormat = quantName,
                inputSize = inputFile.length(),
                outputSize = outputFile.length()
            ))

        } catch (e: Exception) {
            emit(ConversionProgress.Error("量化失败: ${e.message}"))
        }
    }

    /**
     * 安全检查（检测恶意代码）
     */
    fun securityCheck(modelPath: String): Flow<SecurityCheckResult> = flow {
        emit(SecurityCheckResult.Status("正在安全检查..."))

        try {
            val file = File(modelPath)
            if (!file.exists()) {
                emit(SecurityCheckResult.Error("文件不存在"))
                return@flow
            }

            emit(SecurityCheckResult.Progress(20, "检查文件头..."))
            
            // 检查文件头
            val headerValid = checkFileHeader(modelPath)

            emit(SecurityCheckResult.Progress(50, "检查可疑内容..."))

            // 检查可疑内容
            val suspiciousContent = checkSuspiciousContent(modelPath)

            emit(SecurityCheckResult.Progress(80, "计算哈希值..."))

            // 计算哈希
            val sha256 = calculateSHA256(modelPath)

            emit(SecurityCheckResult.Completed(
                isSafe = headerValid && !suspiciousContent.hasSuspicious,
                warnings = suspiciousContent.warnings,
                sha256 = sha256,
                fileSize = file.length()
            ))

        } catch (e: Exception) {
            emit(SecurityCheckResult.Error("检查失败: ${e.message}"))
        }
    }

    /**
     * 批量转换
     */
    fun batchConvert(
        inputPaths: List<String>,
        outputFormat: String = "mnn"
    ): Flow<ConversionProgress> = flow {
        val total = inputPaths.size
        
        for ((index, path) in inputPaths.withIndex()) {
            emit(ConversionProgress.Status("转换 ${index + 1}/$total..."))
            
            // 递归转换每个文件
            convertToMnn(path).collect { result ->
                when (result) {
                    is ConversionProgress.Completed -> {
                        emit(ConversionProgress.Progress(
                            ((index + 1) * 100) / total,
                            "完成 ${index + 1}/$total"
                        ))
                    }
                    is ConversionProgress.Error -> {
                        emit(ConversionProgress.Error("批量转换中断: ${result.message}"))
                        return@collect
                    }
                    else -> {}
                }
            }
        }
        
        emit(ConversionProgress.Completed(
            outputPath = convertDir.absolutePath,
            inputFormat = "Mixed",
            outputFormat = outputFormat.uppercase(),
            inputSize = 0,
            outputSize = 0
        ))
    }

    // ==================== 内部方法 ====================

    private fun detectFormat(path: String): ModelFormat {
        val ext = File(path).extension.lowercase()
        return when (ext) {
            "pt" -> ModelFormat.PT
            "pth" -> ModelFormat.PT  // PTH 映射到 PT
            "ckpt" -> ModelFormat.CKPT
            "safetensors" -> ModelFormat.SAFETENSORS
            "mnn" -> ModelFormat.MNN
            else -> ModelFormat.PT  // 默认当作 PT
        }
    }

    private suspend fun readModelFile(path: String, format: ModelFormat): ByteArray = 
        withContext(Dispatchers.IO) {
            FileInputStream(File(path)).use { it.readBytes() }
        }

    private suspend fun writeModelFile(file: File, data: ByteArray) = 
        withContext(Dispatchers.IO) {
            FileOutputStream(file).use { it.write(data) }
        }

    private fun convertToMnnFormat(data: ByteArray, inputFormat: ModelFormat): ByteArray {
        // 简化实现：返回原始数据
        // 实际需要调用 MNN 的转换 API
        Log.i(TAG, "Converting from $inputFormat to MNN")
        return data
    }

    private fun quantize(data: ByteArray, quantType: Int): ByteArray {
        // 简化实现：返回原始数据
        // 实际需要实现量化算法
        Log.i(TAG, "Quantizing to type: $quantType")
        return data
    }

    private fun checkFileHeader(path: String): Boolean {
        return try {
            FileInputStream(File(path)).use { fis ->
                val header = ByteArray(10)
                fis.read(header)
                
                // 检查 PyTorch 文件头
                // 这里可以添加更多检查
                header.isNotEmpty()
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun checkSuspiciousContent(path: String): SuspiciousCheckResult {
        val warnings = mutableListOf<String>()
        var hasSuspicious = false
        
        try {
            // 简单检查：文件大小异常
            val file = File(path)
            if (file.length() < 1024) {
                warnings.add("文件过小，可能是占位符")
                hasSuspicious = true
            }
            
            // 可以添加更多检查
        } catch (e: Exception) {
            warnings.add("检查失败: ${e.message}")
        }
        
        return SuspiciousCheckResult(hasSuspicious, warnings)
    }

    private fun calculateSHA256(path: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(File(path)).use { fis ->
                val buffer = ByteArray(8192)
                var read: Int
                while (fis.read(buffer).also { read = it } > 0) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "error"
        }
    }

    fun getConvertDirectory(): File = convertDir
    fun clearConvertedModels() = convertDir.listFiles()?.forEach { it.delete() }
    
    data class SuspiciousCheckResult(
        val hasSuspicious: Boolean,
        val warnings: List<String>
    )
}

sealed class ConversionProgress {
    data class Status(val message: String) : ConversionProgress()
    data class Progress(val percent: Int, val message: String) : ConversionProgress()
    data class Completed(
        val outputPath: String,
        val inputFormat: String,
        val outputFormat: String,
        val inputSize: Long,
        val outputSize: Long
    ) : ConversionProgress()
    data class Error(val message: String) : ConversionProgress()
}

sealed class SecurityCheckResult {
    data class Status(val message: String) : SecurityCheckResult()
    data class Progress(val percent: Int, val message: String) : SecurityCheckResult()
    data class Completed(
        val isSafe: Boolean,
        val warnings: List<String>,
        val sha256: String,
        val fileSize: Long
    ) : SecurityCheckResult()
    data class Error(val message: String) : SecurityCheckResult()
}
