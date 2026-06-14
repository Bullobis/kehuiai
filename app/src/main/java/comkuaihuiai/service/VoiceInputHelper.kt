package comkuaihuiai.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 可绘AI v3.6.0 - 语音输入助手
 * 
 * 功能：
 * - 语音转文字
 * - 语音命令控制
 * - 文字转语音播报
 * - 多语言支持
 */
class VoiceInputHelper(private val context: Context) {

    companion object {
        private const val TAG = "VoiceInput"
        
        // 静默阈值
        private const val SILENCE_THRESHOLD_MS = 2000
        private const val MIN_AUDIO_LEVEL = 100
    }
    
    /**
     * 语音状态
     */
    enum class VoiceState {
        IDLE,           // 空闲
        LISTENING,      // 监听中
        PROCESSING,     // 处理中
        SPEAKING,       // 播报中
        ERROR           // 错误
    }
    
    /**
     * 识别结果
     */
    data class RecognitionResult(
        val text: String,
        val confidence: Float,
        val language: String,
        val alternatives: List<String> = emptyList(),
        val isFinal: Boolean = false
    )
    
    /**
     * 命令
     */
    data class VoiceCommand(
        val action: CommandAction,
        val parameters: Map<String, Any> = emptyMap(),
        val confidence: Float
    )
    
    enum class CommandAction {
        GENERATE,      // 生成
        STOP,          // 停止
        UNDO,          // 撤销
        REDO,          // 重做
        SAVE,          // 保存
        SHARE,         // 分享
        SET_STYLE,     // 设置风格
        SET_MODEL,     // 设置模型
        HELP,          // 帮助
        UNKNOWN        // 未知
    }
    
    /**
     * TTS 状态
     */
    data class TTSState(
        val isReady: Boolean = false,
        val isSpeaking: Boolean = false,
        val language: String = "en",
        val speechRate: Float = 1.0f,
        val pitch: Float = 1.0f
    )
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 语音识别
    private var speechRecognizer: SpeechRecognizer? = null
    
    // TTS
    private var textToSpeech: TextToSpeech? = null
    private var ttsState = MutableStateFlow(TTSState())
    
    // 状态
    private val voiceState = MutableStateFlow(VoiceState.IDLE)
    private val lastResult = MutableStateFlow<RecognitionResult?>(null)
    private val audioLevel = MutableStateFlow(0)
    
    private val isListening = AtomicBoolean(false)
    private val isTtsReady = AtomicBoolean(false)
    
    // 监听器
    private var onResultListener: ((RecognitionResult) -> Unit)? = null
    private var onCommandListener: ((VoiceCommand) -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null
    
    // Flow
    private val _recognitionResults = MutableSharedFlow<RecognitionResult>(extraBufferCapacity = 64)
    val recognitionResults: SharedFlow<RecognitionResult> = _recognitionResults.asSharedFlow()
    
    private val _voiceCommands = MutableSharedFlow<VoiceCommand>(extraBufferCapacity = 16)
    val voiceCommands: SharedFlow<VoiceCommand> = _voiceCommands.asSharedFlow()
    
    /**
     * 初始化
     */
    fun initialize() {
        initSpeechRecognizer()
        initTextToSpeech()
        Log.i(TAG, "VoiceInputHelper 已初始化")
    }
    
    /**
     * 设置结果监听器
     */
    fun setOnResultListener(listener: (RecognitionResult) -> Unit) {
        onResultListener = listener
    }
    
    /**
     * 设置命令监听器
     */
    fun setOnCommandListener(listener: (VoiceCommand) -> Unit) {
        onCommandListener = listener
    }
    
    /**
     * 设置错误监听器
     */
    fun setOnErrorListener(listener: (String) -> Unit) {
        onErrorListener = listener
    }
    
    /**
     * 开始监听
     */
    fun startListening(language: String = "zh-CN") {
        if (isListening.get()) {
            Log.w(TAG, "已经在监听中")
            return
        }
        
        if (speechRecognizer == null) {
            initSpeechRecognizer()
        }
        
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_THRESHOLD_MS)
            }
            
            speechRecognizer?.startListening(intent)
            isListening.set(true)
            voiceState.value = VoiceState.LISTENING
            
            Log.i(TAG, "开始监听: $language")
        } catch (e: Exception) {
            Log.e(TAG, "启动监听失败: ${e.message}")
            onErrorListener?.invoke("无法启动语音识别: ${e.message}")
        }
    }
    
    /**
     * 停止监听
     */
    fun stopListening() {
        if (!isListening.get()) return
        
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "停止监听失败: ${e.message}")
        }
        
        isListening.set(false)
        voiceState.value = VoiceState.IDLE
        Log.i(TAG, "停止监听")
    }
    
    /**
     * 取消监听
     */
    fun cancelListening() {
        try {
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "取消监听失败: ${e.message}")
        }
        
        isListening.set(false)
        voiceState.value = VoiceState.IDLE
    }
    
    /**
     * 语音播报
     */
    fun speak(
        text: String,
        language: String = "en",
        speechRate: Float = 1.0f,
        pitch: Float = 1.0f
    ) {
        if (!isTtsReady.get()) {
            Log.w(TAG, "TTS 未就绪")
            return
        }
        
        voiceState.value = VoiceState.SPEAKING
        
        textToSpeech?.apply {
            setSpeechRate(speechRate)
            setPitch(pitch)
            setLanguage(Locale.forLanguageTag(language))
            
            speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance_${System.currentTimeMillis()}")
        }
    }
    
    /**
     * 停止播报
     */
    fun stopSpeaking() {
        textToSpeech?.stop()
        voiceState.value = VoiceState.IDLE
    }
    
    /**
     * 设置 TTS 语速
     */
    fun setSpeechRate(rate: Float) {
        textToSpeech?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
        ttsState.update { it.copy(speechRate = rate) }
    }
    
    /**
     * 设置 TTS 音调
     */
    fun setPitch(pitch: Float) {
        textToSpeech?.setPitch(pitch.coerceIn(0.5f, 2.0f))
        ttsState.update { it.copy(pitch = pitch) }
    }
    
    /**
     * 获取 TTS 状态
     */
    fun getTtsState(): TTSState = ttsState.value
    
    /**
     * 获取语音状态
     */
    fun getVoiceState(): VoiceState = voiceState.value
    
    /**
     * 是否正在监听
     */
    fun isCurrentlyListening(): Boolean = isListening.get()
    
    /**
     * 获取最后识别结果
     */
    fun getLastResult(): RecognitionResult? = lastResult.value
    
    /**
     * 释放资源
     */
    fun release() {
        stopListening()
        stopSpeaking()
        
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "销毁语音识别器失败: ${e.message}")
        }
        
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "销毁TTS失败: ${e.message}")
        }
        
        scope.cancel()
        Log.i(TAG, "VoiceInputHelper 已释放")
    }
    
    // ==================== 私有方法 ====================
    
    private fun initSpeechRecognizer() {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer?.setRecognitionListener(createRecognitionListener())
                Log.i(TAG, "语音识别器已初始化")
            } else {
                Log.w(TAG, "设备不支持语音识别")
            }
        } catch (e: Exception) {
            Log.e(TAG, "初始化语音识别器失败: ${e.message}")
        }
    }
    
    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady.set(true)
                ttsState.update { it.copy(isReady = true) }
                
                textToSpeech?.apply {
                    setLanguage(Locale.US)
                    setSpeechRate(1.0f)
                    setPitch(1.0f)
                }
                
                Log.i(TAG, "TTS 已初始化")
            } else {
                Log.e(TAG, "TTS 初始化失败: $status")
            }
        }
        
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                voiceState.value = VoiceState.SPEAKING
            }
            
            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    voiceState.value = VoiceState.IDLE
                }
            }
            
            override fun onError(utteranceId: String?) {
                mainHandler.post {
                    voiceState.value = VoiceState.ERROR
                }
            }
        })
    }
    
    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "准备接收语音")
            voiceState.value = VoiceState.LISTENING
        }
        
        override fun onBeginningOfSpeech() {
            Log.d(TAG, "开始说话")
        }
        
        override fun onRmsChanged(rmsdB: Float) {
            // 更新音频级别
            val level = (rmsdB * 10).toInt().coerceIn(0, 100)
            audioLevel.value = level
        }
        
        override fun onBufferReceived(buffer: ByteArray?) {
            // 音频缓冲
        }
        
        override fun onEndOfSpeech() {
            Log.d(TAG, "结束说话")
            voiceState.value = VoiceState.PROCESSING
        }
        
        override fun onError(error: Int) {
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "音频录制错误"
                SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足"
                SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                SpeechRecognizer.ERROR_NO_MATCH -> "没有匹配结果"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙"
                SpeechRecognizer.ERROR_SERVER -> "服务器错误"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有语音输入"
                else -> "未知错误"
            }
            
            Log.e(TAG, "识别错误: $errorMessage (code: $error)")
            isListening.set(false)
            voiceState.value = VoiceState.IDLE
            
            if (error != SpeechRecognizer.ERROR_NO_MATCH && 
                error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                onErrorListener?.invoke(errorMessage)
            }
        }
        
        override fun onResults(results: Bundle?) {
            Log.d(TAG, "收到识别结果")
            
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val result = RecognitionResult(
                    text = matches[0],
                    confidence = 0.9f,
                    language = "zh-CN",
                    alternatives = matches.drop(1),
                    isFinal = true
                )
                
                lastResult.value = result
                isListening.set(false)
                voiceState.value = VoiceState.IDLE
                
                // 通知监听器
                onResultListener?.invoke(result)
                scope.launch {
                    _recognitionResults.emit(result)
                }
                
                // 解析命令
                val command = parseCommand(result.text)
                if (command.action != CommandAction.UNKNOWN) {
                    onCommandListener?.invoke(command)
                    _voiceCommands.tryEmit(command)
                }
            }
        }
        
        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val result = RecognitionResult(
                    text = matches[0],
                    confidence = 0.7f,
                    language = "zh-CN",
                    isFinal = false
                )
                
                // 临时更新状态
                lastResult.value = result
                onResultListener?.invoke(result)
            }
        }
        
        override fun onEvent(eventType: Int, params: Bundle?) {
            // 事件处理
        }
    }
    
    private fun parseCommand(text: String): VoiceCommand {
        val lowerText = text.lowercase()
        
        return when {
            // 生成命令
            lowerText.contains("生成") || lowerText.contains("create") ||
            lowerText.contains("生成图片") -> {
                VoiceCommand(CommandAction.GENERATE, emptyMap(), 0.9f)
            }
            
            // 停止命令
            lowerText.contains("停止") || lowerText.contains("stop") ||
            lowerText.contains("取消") -> {
                VoiceCommand(CommandAction.STOP, emptyMap(), 0.9f)
            }
            
            // 撤销命令
            lowerText.contains("撤销") || lowerText.contains("undo") ||
            lowerText.contains("上一步") -> {
                VoiceCommand(CommandAction.UNDO, emptyMap(), 0.85f)
            }
            
            // 重做命令
            lowerText.contains("重做") || lowerText.contains("redo") ||
            lowerText.contains("下一步") -> {
                VoiceCommand(CommandAction.REDO, emptyMap(), 0.85f)
            }
            
            // 保存命令
            lowerText.contains("保存") || lowerText.contains("save") ||
            lowerText.contains("存储") -> {
                VoiceCommand(CommandAction.SAVE, emptyMap(), 0.85f)
            }
            
            // 分享命令
            lowerText.contains("分享") || lowerText.contains("share") ||
            lowerText.contains("发送") -> {
                VoiceCommand(CommandAction.SHARE, emptyMap(), 0.8f)
            }
            
            // 设置风格
            lowerText.contains("风格") || lowerText.contains("style") -> {
                val style = extractStyle(text)
                VoiceCommand(CommandAction.SET_STYLE, mapOf("style" to style), 0.8f)
            }
            
            // 设置模型
            lowerText.contains("模型") || lowerText.contains("model") -> {
                val model = extractModel(text)
                VoiceCommand(CommandAction.SET_MODEL, mapOf("model" to model), 0.8f)
            }
            
            // 帮助
            lowerText.contains("帮助") || lowerText.contains("help") ||
            lowerText.contains("怎么用") -> {
                VoiceCommand(CommandAction.HELP, emptyMap(), 0.9f)
            }
            
            else -> VoiceCommand(CommandAction.UNKNOWN, emptyMap(), 0f)
        }
    }
    
    private fun extractStyle(text: String): String {
        val styles = listOf("动漫", "anime", "写实", "realistic", "油画", "水彩", 
                          "赛博朋克", "奇幻", "像素", "素描")
        
        return styles.find { text.contains(it) } ?: "默认"
    }
    
    private fun extractModel(text: String): String {
        val models = listOf("SDXL", "SD1.5", "anything", "illustrious")
        
        return models.find { text.contains(it) } ?: "默认"
    }
}
