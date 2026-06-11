package comkuaihuiai

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log

/**
 * 可绘AI v3.0 Application
 */
class KeHuiAIApplication : Application() {
    
    companion object {
        private const val TAG = "KeHuiAI"
        const val VERSION_NAME = "3.0.0"
        const val VERSION_CODE = 300
        
        const val DOWNLOAD_CHANNEL_ID = "kehui_download_channel"
        const val GENERATION_CHANNEL_ID = "kehui_generation_channel"
        const val BACKEND_CHANNEL_ID = "kehui_backend_channel"
        const val VIDEO_CHANNEL_ID = "kehui_video_channel"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        Log.i(TAG, """
            ╔═══════════════════════════════════════════╗
            ║     🌟 可绘AI v$VERSION_NAME 启动中 🌟           ║
            ║     AI图像与视频生成专家                    ║
            ╚═══════════════════════════════════════════╝
        """.trimIndent())
        
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            
            listOf(
                NotificationChannel(DOWNLOAD_CHANNEL_ID, "模型下载", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "模型下载进度"; setShowBadge(false)
                },
                NotificationChannel(GENERATION_CHANNEL_ID, "图像生成", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "图像生成进度"; setShowBadge(true)
                },
                NotificationChannel(BACKEND_CHANNEL_ID, "后端服务", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "AI后端服务状态"; setShowBadge(false)
                },
                NotificationChannel(VIDEO_CHANNEL_ID, "视频生成", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "视频生成进度"; setShowBadge(true); enableVibration(true)
                }
            ).forEach { nm.createNotificationChannel(it) }
        }
    }
}
