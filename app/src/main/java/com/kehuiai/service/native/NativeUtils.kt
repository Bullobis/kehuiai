package com.kehuiai.service.native

import android.os.Build
import android.util.Log

/**
 * 本地工具函数
 */
object NativeUtils {

    private const val TAG = "NativeUtils"

    /**
     * 获取当前时间戳（秒）
     */
    fun getTimestamp(): Long {
        return nativeGetTimestamp()
    }

    /**
     * 生成随机种子
     */
    fun generateSeed(): Long {
        return nativeGenerateSeed()
    }

    /**
     * 字符串哈希
     */
    fun hashString(str: String): Int {
        return nativeHashString(str)
    }

    /**
     * 拷贝像素数据
     */
    fun copyPixels(srcPixels: IntArray, dstPixels: IntArray, count: Int) {
        nativeCopyPixels(srcPixels, dstPixels, count)
    }

    /**
     * 获取设备型号
     */
    fun getDeviceModel(): String {
        return nativeGetDeviceModel()
    }

    /**
     * 获取设备制造商
     */
    fun getDeviceManufacturer(): String {
        return nativeGetDeviceManufacturer()
    }

    /**
     * 获取可用内存
     */
    fun getAvailableMemory(): Long {
        return nativeGetAvailableMemory()
    }

    /**
     * 获取完整的设备信息
     */
    fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            model = getDeviceModel(),
            manufacturer = getDeviceManufacturer(),
            androidVersion = Build.VERSION.SDK_INT,
            availableMemory = getAvailableMemory()
        )
    }

    data class DeviceInfo(
        val model: String,
        val manufacturer: String,
        val androidVersion: Int,
        val availableMemory: Long
    ) {
        fun getAvailableMemoryMB(): Long = availableMemory / (1024 * 1024)
    }

    // Native 方法
    private external fun nativeGetTimestamp(): Long
    private external fun nativeGenerateSeed(): Long
    private external fun nativeHashString(str: String): Int
    private external fun nativeCopyPixels(srcPixels: IntArray, dstPixels: IntArray, count: Int)
    private external fun nativeGetDeviceModel(): String
    private external fun nativeGetDeviceManufacturer(): String
    private external fun nativeGetAvailableMemory(): Long

    init {
        try {
            System.loadLibrary("kuaihui_native")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library: ${e.message}")
        }
    }
}
