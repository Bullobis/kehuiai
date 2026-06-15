package com.kehuiai.service

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile

/**
 * PNG Info 解析器
 * 从 PNG/图片文件中提取生成参数
 */
class PNGInfoParser(private val context: Context) {

    companion object {
        private const val TAG = "PNGInfoParser"
        
        // PNG 关键块
        private const val CHUNK_IEND = 0x49454E44
        private const val CHUNK_tEXt = 0x74455874
        private const val CHUNK_iTXt = 0x69547874
        private const val CHUNK_zTXt = 0x7A545874
    }

    /**
     * 解析 PNG 文件
     */
    fun parsePNGInfo(imagePath: String): PNGInfo? {
        return try {
            val file = File(imagePath)
            if (!file.exists()) {
                Log.e(TAG, "File not found: $imagePath")
                return null
            }

            val extension = file.extension.lowercase()
            
            when (extension) {
                "png" -> parsePNG(file)
                "jpg", "jpeg" -> parseJPEG(file)
                "webp" -> parseWebP(file)
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
            null
        }
    }

    /**
     * 从 PNG 提取信息
     */
    private fun parsePNG(file: File): PNGInfo? {
        var prompt = ""
        var negativePrompt = ""
        var parameters = mutableMapOf<String, String>()
        
        try {
            RandomAccessFile(file, "r").use { raf ->
                // 读取 PNG 签名
                val signature = ByteArray(8)
                raf.read(signature)
                
                // 读取每个块
                while (true) {
                    val length = readInt32(raf)
                    val chunkType = readInt32(raf)
                    
                    if (chunkType == CHUNK_IEND) {
                        break
                    }
                    
                    val data = ByteArray(length)
                    raf.read(data)
                    
                    // 跳过 CRC
                    raf.seek(raf.filePointer + 4)
                    
                    // 解析文本块
                    if (chunkType == CHUNK_tEXt || chunkType == CHUNK_zTXt) {
                        val text = String(data, Charsets.ISO_8859_1)
                        val separator = text.indexOf('\u0000')
                        if (separator > 0) {
                            val key = text.substring(0, separator)
                            val value = text.substring(separator + 1)
                            
                            if (key == "parameters") {
                                parseParameters(value, parameters)
                            } else if (key == "Comment") {
                                parseParameters(value, parameters)
                            }
                        }
                    }
                }
            }
            
            prompt = parameters["Positive prompt"] ?: parameters["prompt"] ?: ""
            negativePrompt = parameters["Negative prompt"] ?: parameters["negative_prompt"] ?: ""
            
            return PNGInfo(
                filePath = file.absolutePath,
                prompt = prompt,
                negativePrompt = negativePrompt,
                width = parameters["Size"]?.split("x")?.getOrNull(0)?.toIntOrNull() ?: 0,
                height = parameters["Size"]?.split("x")?.getOrNull(1)?.toIntOrNull() ?: 0,
                steps = parameters["Steps"]?.toIntOrNull(),
                sampler = parameters["Sampler"],
                CFGScale = parameters["CFG scale"]?.toFloatOrNull(),
                seed = parameters["Seed"]?.toLongOrNull(),
                modelHash = parameters["Model hash"],
                model = parameters["Model"],
                clipSkip = parameters["Clip skip"]?.toIntOrNull(),
                denoisingStrength = parameters["Denoising strength"]?.toFloatOrNull(),
                ENSD = parameters["ENSD"]?.toIntOrNull(),
                tileWidth = parameters["tile_width"]?.toIntOrNull(),
                tileHeight = parameters["tile_height"]?.toIntOrNull(),
                tiles = parameters["tiles"]?.toIntOrNull(),
                extraData = parameters.toMutableMap()
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "PNG parse error: ${e.message}")
            return null
        }
    }

    /**
     * 从 JPEG 提取信息
     */
    private fun parseJPEG(file: File): PNGInfo? {
        var prompt = ""
        var negativePrompt = ""
        var parameters = mutableMapOf<String, String>()
        
        try {
            FileInputStream(file).use { fis ->
                // 跳过 SOI
                val soi = ByteArray(2)
                fis.read(soi)
                
                while (true) {
                    val marker = ByteArray(2)
                    if (fis.read(marker) != 2) break
                    
                    val markerInt = ((marker[0].toInt() and 0xFF) shl 8) or (marker[1].toInt() and 0xFF)
                    
                    // APP13 (Photoshop) 或 COM (Comment)
                    if (markerInt == 0xFFDA || markerInt == 0xFFD9) {
                        break
                    }
                    
                    if (markerInt == 0xFFFE) {
                        // Comment
                        val len = ByteArray(2)
                        fis.read(len)
                        val length = ((len[0].toInt() and 0xFF) shl 8) or (len[1].toInt() and 0xFF)
                        
                        val comment = ByteArray(length - 2)
                        fis.read(comment)
                        
                        val text = String(comment, Charsets.ISO_8859_1)
                        parseParameters(text, parameters)
                    } else if ((markerInt and 0xFFF0) == 0xFFE0) {
                        // APPn
                        val len = ByteArray(2)
                        fis.read(len)
                        val length = ((len[0].toInt() and 0xFF) shl 8) or (len[1].toInt() and 0xFF)
                        
                        fis.skip((length - 2).toLong())
                    } else {
                        break
                    }
                }
            }
            
            prompt = parameters["Positive prompt"] ?: parameters["prompt"] ?: ""
            negativePrompt = parameters["Negative prompt"] ?: parameters["negative_prompt"] ?: ""
            
            return PNGInfo(
                filePath = file.absolutePath,
                prompt = prompt,
                negativePrompt = negativePrompt,
                width = parameters["Size"]?.split("x")?.getOrNull(0)?.toIntOrNull() ?: 0,
                height = parameters["Size"]?.split("x")?.getOrNull(1)?.toIntOrNull() ?: 0,
                steps = parameters["Steps"]?.toIntOrNull(),
                sampler = parameters["Sampler"],
                CFGScale = parameters["CFG scale"]?.toFloatOrNull(),
                seed = parameters["Seed"]?.toLongOrNull(),
                modelHash = parameters["Model hash"],
                model = parameters["Model"],
                extraData = parameters
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "JPEG parse error: ${e.message}")
            return null
        }
    }

    /**
     * 从 WebP 提取信息（简化）
     */
    private fun parseWebP(file: File): PNGInfo? {
        // WebP 格式更复杂，这里简化处理
        return PNGInfo(
            filePath = file.absolutePath,
            prompt = "",
            negativePrompt = "",
            width = 0,
            height = 0
        )
    }

    /**
     * 解析参数字符串
     */
    private fun parseParameters(text: String, params: MutableMap<String, String>) {
        val lines = text.split("\n")
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            
            // 检查是否为键值对
            val separator = trimmed.indexOf(":")
            if (separator > 0) {
                val key = trimmed.substring(0, separator).trim()
                val value = trimmed.substring(separator + 1).trim()
                
                when {
                    key.contains("prompt", ignoreCase = true) && !key.contains("negative", ignoreCase = true) -> {
                        params["Positive prompt"] = value
                    }
                    key.contains("negative", ignoreCase = true) -> {
                        params["Negative prompt"] = value
                    }
                    else -> {
                        params[key] = value
                    }
                }
            } else {
                // 可能是纯 prompt
                if (params["Positive prompt"].isNullOrEmpty()) {
                    params["Positive prompt"] = trimmed
                }
            }
        }
    }

    private fun readInt32(raf: RandomAccessFile): Int {
        val bytes = ByteArray(4)
        raf.read(bytes)
        return ((bytes[0].toInt() and 0xFF) shl 24) or
               ((bytes[1].toInt() and 0xFF) shl 16) or
               ((bytes[2].toInt() and 0xFF) shl 8) or
               (bytes[3].toInt() and 0xFF)
    }

    /**
     * 从文件路径生成占位信息
     */
    fun createPlaceholderInfo(imagePath: String): PNGInfo {
        val file = File(imagePath)
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(imagePath, options)
        
        return PNGInfo(
            filePath = file.absolutePath,
            prompt = "",
            negativePrompt = "",
            width = options.outWidth,
            height = options.outHeight
        )
    }
}

/**
 * PNG Info 数据类
 */
data class PNGInfo(
    val filePath: String,
    val prompt: String,
    val negativePrompt: String,
    val width: Int = 0,
    val height: Int = 0,
    val steps: Int? = null,
    val sampler: String? = null,
    val CFGScale: Float? = null,
    val seed: Long? = null,
    val modelHash: String? = null,
    val model: String? = null,
    val clipSkip: Int? = null,
    val denoisingStrength: Float? = null,
    val ENSD: Int? = null,
    val tileWidth: Int? = null,
    val tileHeight: Int? = null,
    val tiles: Int? = null,
    val extraData: Map<String, String> = emptyMap()
) {
    fun getResolution(): String = "${width}x${height}"
    
    fun getSeedString(): String = seed?.toString() ?: "Random"
    
    fun getParameters(): String {
        val sb = StringBuilder()
        sb.append("Prompt: $prompt\n")
        sb.append("Negative: $negativePrompt\n")
        sb.append("Size: ${getResolution()}\n")
        steps?.let { sb.append("Steps: $it\n") }
        sampler?.let { sb.append("Sampler: $it\n") }
        CFGScale?.let { sb.append("CFG: $it\n") }
        seed?.let { sb.append("Seed: $it\n") }
        model?.let { sb.append("Model: $it\n") }
        return sb.toString()
    }
}
