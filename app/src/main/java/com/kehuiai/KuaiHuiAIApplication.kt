package com.kehuiai

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

/**
 * KuaiHuiAI Application class
 */
class KuaiHuiAIApplication : Application() {
    
    companion object {
        const val DOWNLOAD_CHANNEL_ID = "download_channel"
        const val GENERATION_CHANNEL_ID = "generation_channel"
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            
            // Download channel
            val downloadChannel = NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                "模型下载",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "模型下载进度通知"
            }
            notificationManager.createNotificationChannel(downloadChannel)
            
            // Generation channel
            val generationChannel = NotificationChannel(
                GENERATION_CHANNEL_ID,
                "图像生成",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "图像生成进度通知"
            }
            notificationManager.createNotificationChannel(generationChannel)
        }
    }
}
