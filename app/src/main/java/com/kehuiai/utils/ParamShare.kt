package com.kehuiai.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * 参数分享工具
 */
object ParamShare {
    
    private const val AUTHORITY = "com.kehuiai.kuaihuiAI.fileprovider"
    
    /**
     * 分享图像
     */
    fun shareImage(context: Context, imagePath: String) {
        val file = File(imagePath)
        if (!file.exists()) return
        
        val uri = FileProvider.getUriForFile(context, AUTHORITY, file)
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(intent, "分享图像"))
    }
    
    /**
     * 保存图像到相册
     */
    fun saveToGallery(context: Context, imagePath: String, name: String? = null): Uri? {
        val file = File(imagePath)
        if (!file.exists()) return null
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = name ?: "KeHuiAI_$timestamp.jpg"
        
        return try {
            val destFile = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), fileName)
            file.copyTo(destFile, overwrite = true)
            Uri.fromFile(destFile)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 加载缩略图
     */
    fun loadThumbnail(imagePath: String, width: Int, height: Int): Bitmap? {
        return try {
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                BitmapFactory.decodeFile(imagePath, this)
                
                inSampleSize = calculateInSampleSize(this, width, height)
                inJustDecodeBounds = false
            }.let { options ->
                BitmapFactory.decodeFile(imagePath, options)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
}
