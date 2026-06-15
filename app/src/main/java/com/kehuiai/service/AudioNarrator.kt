package com.kehuiai.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

/**
 * 可绘AI v3.5.0 - 语音解说员
 */
class AudioNarrator(private val context: Context) {

    companion object {
        private const val TAG = "AudioNarrator"
    }
    
    enum class VoiceSpeed { SLOW, NORMAL, FAST }
    enum class VoiceGender { MALE, FEMALE }
    
    data class NarrationConfig(
        val language: String = "zh-CN",
        val speed: VoiceSpeed = VoiceSpeed.NORMAL,
        val gender: VoiceGender = VoiceGender.FEMALE
    )
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val _status = MutableStateFlow(NarrationStatus.IDLE)
    val status: StateFlow<NarrationStatus> = _status.asStateFlow()
    
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()
    
    enum class NarrationStatus { IDLE, INITIALIZING, READY, SPEAKING, ERROR }
    
    init { initialize() }
    
    private fun initialize() {
        _status.value = NarrationStatus.INITIALIZING
        tts = TextToSpeech(context) { code ->
            if (code == TextToSpeech.SUCCESS) {
                tts?.language = Locale.CHINA
                isInitialized = true
                _status.value = NarrationStatus.READY
                Log.i(TAG, "TTS 初始化成功")
            } else {
                _status.value = NarrationStatus.ERROR
                Log.e(TAG, "TTS 初始化失败")
            }
        }
        
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utt: String?) { _status.value = NarrationStatus.SPEAKING }
            override fun onDone(utt: String?) { 
                _status.value = NarrationStatus.READY
                _progress.value = 1f
            }
            override fun onError(utt: String?) { _status.value = NarrationStatus.ERROR }
        })
    }
    
    fun speak(text: String, config: NarrationConfig = NarrationConfig()) {
        if (!isInitialized) {
            Log.w(TAG, "TTS 未初始化")
            return
        }
        
        tts?.apply {
            language = Locale(config.language)
            setSpeechRate(when (config.speed) {
                VoiceSpeed.SLOW -> 0.8f
                VoiceSpeed.NORMAL -> 1.0f
                VoiceSpeed.FAST -> 1.3f
            })
        }
        
        _progress.value = 0f
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "narration_${System.currentTimeMillis()}")
    }
    
    fun stop() {
        tts?.stop()
        _status.value = NarrationStatus.READY
    }
    
    fun describeImage(prompt: String, config: NarrationConfig = NarrationConfig()): String {
        val description = generateDescription(prompt)
        speak(description, config)
        return description
    }
    
    private fun generateDescription(prompt: String): String {
        return buildString {
            append("这是一张")
            if (prompt.contains("girl", true) || prompt.contains("女孩", true)) append("女孩")
            else if (prompt.contains("boy", true) || prompt.contains("男孩", true)) append("男孩")
            else if (prompt.contains("landscape", true) || prompt.contains("风景", true)) append("风景")
            else append("图像")
            append("的AI生成作品。")
            append("描述为：").append(prompt.take(50))
        }
    }
    
    fun release() {
        scope.cancel()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
