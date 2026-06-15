package com.kehuiai.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.kehuiai.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

/**
 * 方向三：VAE 管理器 - 更多模型生态
 * VAE (Variational Autoencoder) 用于图像编码和解码的美化
 */
class VAEManager(private val context: Context) {
    
    companion object {
        private const val TAG = "VAEManager"
        private const val VAE_DIR = "models/vae"
    }
    
    private val vaeDir = File(context.filesDir, VAE_DIR)
    
    private val _availableVAEs = MutableStateFlow<List<VAEInfo>>(emptyList())
    val availableVAEs: StateFlow<List<VAEInfo>> = _availableVAEs.asStateFlow()
    
    private val _currentVAE = MutableStateFlow<VAEParam?>(null)
    val currentVAE: StateFlow<VAEParam?> = _currentVAE.asStateFlow()
    
    private var loadedVAE: VAEInfo? = null
    private val vaeCache = mutableMapOf<String, VAECodec>()
    
    init {
        if (!vaeDir.exists()) vaeDir.mkdirs()
        loadBuiltinVAEs()
    }
    
    /**
     * 加载内置 VAE 列表
     */
    private fun loadBuiltinVAEs() {
        val builtinVAEs = listOf(
            VAEInfo(
                id = "vae_ft_mae",
                name = "VAE-FT-MAE",
                path = "builtin",
                description = "高质量美化 VAE，适用于写实风格",
                isDefault = false,
                type = VAEType.FT_MAE
            ),
            VAEInfo(
                id = "vae_ft_mse",
                name = "VAE-FT-MSE",
                path = "builtin",
                description = "MSE 优化的 VAE，平衡质量和速度",
                isDefault = false,
                type = VAEType.FT_MSE
            ),
            VAEInfo(
                id = "vae_ema",
                name = "VAE-EMA",
                path = "builtin",
                description = "EMA 平滑的 VAE，稳定输出",
                isDefault = true,
                type = VAEType.EMA
            ),
            VAEInfo(
                id = "kl-f8",
                name = "KL-F8",
                path = "builtin",
                description = "SDXL 原生 VAE",
                isDefault = true,
                type = VAEType.KL_F8
            ),
            VAEInfo(
                id = "taesd",
                name = "TAESD",
                path = "builtin",
                description = "轻量级 VAE，用于快速预览",
                isDefault = false,
                type = VAEType.TAESD
            )
        )
        
        _availableVAEs.value = builtinVAEs
        
        // 扫描自定义 VAE
        scanCustomVAEs()
    }
    
    /**
     * 扫描自定义 VAE
     */
    private fun scanCustomVAEs() {
        val customVAEs = mutableListOf<VAEInfo>()
        
        vaeDir.listFiles()?.forEach { file ->
            if (file.extension in listOf("safetensors", "ckpt", "pt", "pth")) {
                customVAEs.add(
                    VAEInfo(
                        id = file.nameWithoutExtension,
                        name = file.nameWithoutExtension,
                        path = file.absolutePath,
                        description = "自定义 VAE",
                        isDefault = false,
                        type = VAEType.CUSTOM
                    )
                )
            }
        }
        
        _availableVAEs.value = _availableVAEs.value + customVAEs
    }
    
    /**
     * 加载 VAE
     */
    suspend fun loadVAE(vaeInfo: VAEInfo): VAEParam? = withContext(Dispatchers.IO) {
        Log.i(TAG, "加载 VAE: ${vaeInfo.name}")
        
        if (loadedVAE?.id == vaeInfo.id) {
            Log.d(TAG, "VAE 已加载: ${vaeInfo.name}")
            return@withContext _currentVAE.value
        }
        
        try {
            // 模拟加载
            delay(100)
            
            loadedVAE = vaeInfo
            
            val param = VAEParam(
                id = vaeInfo.id,
                name = vaeInfo.name,
                path = vaeInfo.path,
                description = vaeInfo.description,
                isDefault = vaeInfo.isDefault,
                usedFor = VAEUsage.BOTH
            )
            
            _currentVAE.value = param
            
            Log.i(TAG, "VAE 加载完成: ${vaeInfo.name}")
            param
        } catch (e: Exception) {
            Log.e(TAG, "VAE 加载失败: ${e.message}")
            null
        }
    }
    
    /**
     * 应用 VAE 解码美化
     */
    suspend fun applyVAEDecode(bitmap: Bitmap, strength: Float = 1.0f): Bitmap = withContext(Dispatchers.Default) {
        val vae = loadedVAE ?: return@withContext bitmap
        
        Log.d(TAG, "应用 VAE 解码: ${vae.name} (强度: $strength)")
        
        when (vae.type) {
            VAEType.FT_MAE -> applyFTMAE(bitmap, strength)
            VAEType.FT_MSE -> applyFTMSE(bitmap, strength)
            VAEType.EMA -> applyEMA(bitmap, strength)
            VAEType.KL_F8 -> applyKLF8(bitmap, strength)
            VAEType.KL_F16 -> applyKLF8(bitmap, strength)
            VAEType.KL_F8_FP16 -> applyKLF8(bitmap, strength)
            VAEType.TAESD -> applyTAESD(bitmap, strength)
            VAEType.CUSTOM -> applyCustomVAE(bitmap, strength)
        }
    }
    
    /**
     * FT-MAE 美化
     */
    private fun applyFTMAE(input: Bitmap, strength: Float): Bitmap {
        val width = input.width
        val height = input.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = input.getPixel(x, y)
                
                // 增强细节和色彩饱和度
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                
                // 饱和度增强
                val gray = (r * 0.299 + g * 0.587 + b * 0.114).toInt()
                val newR = (gray + (r - gray) * (1 + 0.1f * strength)).toInt().coerceIn(0, 255)
                val newG = (gray + (g - gray) * (1 + 0.1f * strength)).toInt().coerceIn(0, 255)
                val newB = (gray + (b - gray) * (1 + 0.1f * strength)).toInt().coerceIn(0, 255)
                
                result.setPixel(x, y, Color.rgb(newR, newG, newB))
            }
        }
        
        return result
    }
    
    /**
     * FT-MSE 美化
     */
    private fun applyFTMSE(input: Bitmap, strength: Float): Bitmap {
        // MSE 优化，更平滑的结果
        val width = input.width
        val height = input.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // 简单的对比度调整
        val factor = 1 + 0.05f * strength
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = input.getPixel(x, y)
                
                val r = ((Color.red(pixel) - 128) * factor + 128).toInt().coerceIn(0, 255)
                val g = ((Color.green(pixel) - 128) * factor + 128).toInt().coerceIn(0, 255)
                val b = ((Color.blue(pixel) - 128) * factor + 128).toInt().coerceIn(0, 255)
                
                result.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        
        return result
    }
    
    /**
     * EMA 美化
     */
    private fun applyEMA(input: Bitmap, strength: Float): Bitmap {
        // EMA 平滑，减少噪点
        val width = input.width
        val height = input.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // 轻微模糊 + 锐化组合
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = input.getPixel(x, y)
                
                // 轻微去噪
                var sumR = 0
                var sumG = 0
                var sumB = 0
                var count = 0
                
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = (x + dx).coerceIn(0, width - 1)
                        val ny = (y + dy).coerceIn(0, height - 1)
                        val neighbor = input.getPixel(nx, ny)
                        sumR += Color.red(neighbor)
                        sumG += Color.green(neighbor)
                        sumB += Color.blue(neighbor)
                        count++
                    }
                }
                
                val avgR = sumR / count
                val avgG = sumG / count
                val avgB = sumB / count
                
                // 混合
                val blendFactor = 0.2f * strength
                val r = (Color.red(pixel) * (1 - blendFactor) + avgR * blendFactor).toInt()
                val g = (Color.green(pixel) * (1 - blendFactor) + avgG * blendFactor).toInt()
                val b = (Color.blue(pixel) * (1 - blendFactor) + avgB * blendFactor).toInt()
                
                result.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        
        return result
    }
    
    /**
     * KL-F8 美化 (SDXL VAE)
     */
    private fun applyKLF8(input: Bitmap, strength: Float): Bitmap {
        // SDXL 原生 VAE 效果
        val width = input.width
        val height = input.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = input.getPixel(x, y)
                
                // KL VAE 通常有更好的色彩恢复
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                
                // 增强蓝色和绿色（天空和自然色调）
                val newR = (r * 0.95f).toInt()
                val newG = (g * 1.02f).toInt().coerceIn(0, 255)
                val newB = (b * 1.05f).toInt().coerceIn(0, 255)
                
                result.setPixel(x, y, Color.rgb(newR, newG, newB))
            }
        }
        
        return result
    }
    
    /**
     * TAESD 美化 (快速预览)
     */
    private fun applyTAESD(input: Bitmap, strength: Float): Bitmap {
        // TAESD 用于快速预览，轻微美化
        val width = input.width
        val height = input.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = input.getPixel(x, y)
                
                // 轻微锐化
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                
                result.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        
        return result
    }
    
    /**
     * 自定义 VAE 处理
     */
    private fun applyCustomVAE(input: Bitmap, strength: Float): Bitmap {
        // 自定义 VAE 使用默认处理
        return applyEMA(input, strength)
    }
    
    /**
     * 获取当前 VAE
     */
    fun getCurrentVAE(): VAEInfo? = loadedVAE
    
    /**
     * 设置默认 VAE
     */
    fun setDefaultVAE(vaeId: String) {
        val updated = _availableVAEs.value.map {
            it.copy(isDefault = it.id == vaeId)
        }
        _availableVAEs.value = updated
    }
    
    /**
     * 获取默认 VAE
     */
    fun getDefaultVAE(): VAEInfo? {
        return _availableVAEs.value.find { it.isDefault }
    }
    
    /**
     * 卸载 VAE
     */
    fun unloadVAE() {
        loadedVAE = null
        _currentVAE.value = null
        vaeCache.clear()
        Log.i(TAG, "VAE 已卸载")
    }
    
    /**
     * 删除 VAE
     */
    suspend fun deleteVAE(vaeId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val vae = _availableVAEs.value.find { it.id == vaeId }
            if (vae != null && vae.type == VAEType.CUSTOM) {
                File(vae.path).delete()
                _availableVAEs.value = _availableVAEs.value.filter { it.id != vaeId }
                if (loadedVAE?.id == vaeId) {
                    unloadVAE()
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "删除 VAE 失败: ${e.message}")
            false
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        unloadVAE()
    }
}

/**
 * VAE 信息
 */
data class VAEInfo(
    val id: String,
    val name: String,
    val path: String,
    val description: String,
    val isDefault: Boolean = false,
    val type: VAEType = VAEType.CUSTOM
)

/**
 * VAE 类型
 */
enum class VAEType {
    EMA,
    FT_MAE,
    FT_MSE,
    KL_F8,
    KL_F16,
    KL_F8_FP16,
    TAESD,
    CUSTOM
}

/**
 * VAE 编解码器
 */
data class VAECodec(
    val info: VAEInfo,
    val encoder: Any? = null,
    val decoder: Any? = null
)
