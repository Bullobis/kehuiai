package com.kehuiai.data

import android.content.Context
import androidx.core.content.edit

/**
 * App Preferences - stores various app settings
 */
class AppPreferences(private val context: Context) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    
    // Network settings
    var listenOnAllAddresses: Boolean
        get() = prefs.getBoolean("listen_on_all_addresses", false)
        set(value) = prefs.edit { putBoolean("listen_on_all_addresses", value) }
    
    // Download source (huggingface, civitai, etc.)
    var downloadSource: String
        get() = prefs.getString("download_source", "huggingface") ?: "huggingface"
        set(value) = prefs.edit { putString("download_source", value) }
    
    // Base URL for downloads
    var baseUrl: String
        get() = prefs.getString("base_url", "https://huggingface.co/") ?: "https://huggingface.co/"
        set(value) = prefs.edit { putString("base_url", value) }
    
    // SDXL Low RAM mode
    var sdxlLowRam: Boolean
        get() = prefs.getBoolean("sdxl_lowram", true)
        set(value) = prefs.edit { putBoolean("sdxl_lowram", value) }
    
    // Log collection enabled
    var logCollectionEnabled: Boolean
        get() = prefs.getBoolean("log_collection_enabled", false)
        set(value) = prefs.edit { putBoolean("log_collection_enabled", value) }
}
